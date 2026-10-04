/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.xmlbeans.impl.store;

import org.apache.xmlbeans.SystemProperties;
import org.apache.xmlbeans.XmlDocumentProperties;
import org.apache.xmlbeans.XmlOptionCharEscapeMap;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.common.EncodingMap;
import org.apache.xmlbeans.impl.common.QNameHelper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.namespace.QName;
import java.io.*;
import java.util.*;

abstract class Saver {
    static final int ROOT = Cur.ROOT;
    static final int ELEM = Cur.ELEM;
    static final int ATTR = Cur.ATTR;
    static final int COMMENT = Cur.COMMENT;
    static final int PROCINST = Cur.PROCINST;
    static final int TEXT = Cur.TEXT;

    // this is based on what is used in the core Java classes
    static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private final Locale _locale;
    private final long _version;

    private SaveCur _cur;

    private List<String> _ancestorNamespaces;
    private final Map<String, String> _suggestedPrefixes;
    protected XmlOptionCharEscapeMap _replaceChar;
    private final boolean _useDefaultNamespace;
    private Map<String, String> _preComputedNamespaces;
    private final boolean _saveNamespacesFirst;
    private final boolean _escapeAttrWhitespace;

    private final ArrayList<QName> _attrNames = new ArrayList<>();
    private final ArrayList<String> _attrValues = new ArrayList<>();

    private final ArrayList<String> _namespaceStack = new ArrayList<>();
    private int _currentMapping;
    private final HashMap<String, String> _uriMap = new HashMap<>();
    private final HashMap<String, String> _prefixMap = new HashMap<>();
    private String _initialDefaultUri;

    static final String _newLine = SystemProperties.getProperty("line.separator", "\n");


    protected abstract boolean emitElement(SaveCur c, List<QName> attrNames, List<String> attrValues);

    protected abstract void emitFinish(SaveCur c);

    protected abstract void emitText(SaveCur c);

    protected abstract void emitComment(SaveCur c);

    protected abstract void emitProcinst(SaveCur c);

    protected abstract void emitDocType(String docTypeName, String publicId, String systemId);

    protected abstract void emitStartDoc(SaveCur c);

    protected abstract void emitEndDoc(SaveCur c);

    protected void syntheticNamespace(String prefix, String uri, boolean considerDefault) {
    }

    Saver(Cur c, XmlOptions options) {
        assert c._locale.entered();

        options = XmlOptions.maskNull(options);

        _cur = createSaveCur(c, options);

        _locale = c._locale;
        _version = _locale.version();

        // Define implicit xml prefixed namespace

        addMapping("xml", Locale._xml1998Uri);

        Map<String, String> m = options.getSaveImplicitNamespaces();
        if (m != null) {
            for (String prefix : m.keySet()) {
                addMapping(prefix, m.get(prefix));
            }
        }

        // define character map for escaped replacements
        _replaceChar = options.getSaveSubstituteCharacters();

        // If the default prefix has not been mapped, do so now

        if (getNamespaceForPrefix("") == null) {
            _initialDefaultUri = "";
            addMapping("", _initialDefaultUri);
        }

        if (options.isSaveAggressiveNamespaces() && !(this instanceof SynthNamespaceSaver)) {
            SynthNamespaceSaver saver = new SynthNamespaceSaver(c, options);

            //noinspection StatementWithEmptyBody
            while (saver.process()) {
            }

            if (!saver._synthNamespaces.isEmpty()) {
                _preComputedNamespaces = saver._synthNamespaces;
            }
        }

        _useDefaultNamespace = options.isUseDefaultNamespace();

        _saveNamespacesFirst = options.isSaveNamespacesFirst();

        _escapeAttrWhitespace = !options.isSaveNoAttributeWhitespaceEscape();


        _suggestedPrefixes = options.getSaveSuggestedPrefixes();

        _ancestorNamespaces = _cur.getAncestorNamespaces();
    }

    private static SaveCur createSaveCur(Cur c, XmlOptions options) {
        QName synthName = options.getSaveSyntheticDocumentElement();

        QName fragName = synthName;

        if (fragName == null) {
            fragName = options.isSaveUseOpenFrag()
                ? Locale._openuriFragment
                : Locale._xmlFragment;
        }

        boolean saveInner = options.isSaveInner() && !options.isSaveOuter();

        Cur start = c.tempCur();
        Cur end = c.tempCur();

        SaveCur cur = null;

        int k = c.kind();

        switch (k) {
            case ROOT: {
                positionToInner(c, start, end);

                if (Locale.isFragment(start, end)) {
                    cur = new FragSaveCur(start, end, fragName);
                } else if (synthName != null) {
                    cur = new FragSaveCur(start, end, synthName);
                } else {
                    cur = new DocSaveCur(c);
                }

                break;
            }

            case ELEM: {
                if (saveInner) {
                    positionToInner(c, start, end);

                    cur =
                        new FragSaveCur(
                            start, end, Locale.isFragment(start, end) ? fragName : synthName);
                } else if (synthName != null) {
                    positionToInner(c, start, end);

                    cur = new FragSaveCur(start, end, synthName);
                } else {
                    start.moveToCur(c);
                    end.moveToCur(c);
                    end.skip();

                    cur = new FragSaveCur(start, end, null);
                }

                break;
            }
        }

        if (cur == null) {
            assert k < 0 || k == ATTR || k == COMMENT || k == PROCINST || k == TEXT;

            if (k < 0) {
                // Save out ""
                start.moveToCur(c);
                end.moveToCur(c);
            } else if (k == TEXT) {
                start.moveToCur(c);
                end.moveToCur(c);
                end.next();
            } else if (saveInner) {
                start.moveToCur(c);
                start.next();

                end.moveToCur(c);
                end.toEnd();
            } else if (k == ATTR) {
                start.moveToCur(c);
                end.moveToCur(c);
            } else {
                start.moveToCur(c);
                end.moveToCur(c);
                end.skip();
            }

            cur = new FragSaveCur(start, end, fragName);
        }

        String filterPI = options.getSaveFilterProcinst();

        if (filterPI != null) {
            cur = new FilterPiSaveCur(cur, filterPI);
        }

        if (options.isSavePrettyPrint()) {
            cur = new PrettySaveCur(cur, options);
        }

        start.release();
        end.release();

        return cur;
    }

    private static void positionToInner(Cur c, Cur start, Cur end) {
        assert c.isContainer();

        start.moveToCur(c);

        if (!start.toFirstAttr()) {
            start.next();
        }

        end.moveToCur(c);
        end.toEnd();
    }

    /**
     * Test if a character is valid in xml character content. See
     * http://www.w3.org/TR/REC-xml#NT-Char
     */
    static boolean isBadChar(char ch) {
        return !(
            Character.isHighSurrogate(ch) ||
            Character.isLowSurrogate(ch) ||
            (ch >= 0x20 && ch <= 0xD7FF) ||
            (ch >= 0xE000 && ch <= 0xFFFD) ||
            // TODO: ch >= 0x10000 && ch <= 0x10FFFF is always false for a char, use codepoints/ints
            // (ch >= 0x10000 && ch <= 0x10FFFF) ||
            (ch == 0x9) || (ch == 0xA) || (ch == 0xD)
        );
    }

    protected boolean saveNamespacesFirst() {
        return _saveNamespacesFirst;
    }

    protected boolean escapeAttrWhitespace() {
        return _escapeAttrWhitespace;
    }

    protected final boolean process() {
        assert _locale.entered();

        if (_cur == null) {
            return false;
        }

        if (_version != _locale.version()) {
            throw new ConcurrentModificationException("Document changed during save");
        }

        switch (_cur.kind()) {
            case ROOT: {
                processRoot();
                break;
            }
            case ELEM: {
                processElement();
                break;
            }
            case -ELEM: {
                processFinish();
                break;
            }
            case TEXT: {
                emitText(_cur);
                break;
            }

            case COMMENT: {
                emitComment(_cur);
                _cur.toEnd();
                break;
            }
            case PROCINST: {
                emitProcinst(_cur);
                _cur.toEnd();
                break;
            }

            case -ROOT: {
                emitEndDoc(_cur);
                _cur.release();
                _cur = null;

                return true;
            }

            default:
                throw new RuntimeException("Unexpected kind");
        }

        _cur.next();

        return true;
    }

    private void processFinish() {
        emitFinish(_cur);
        popMappings();
    }

    private void processRoot() {
        assert _cur.isRoot();

        XmlDocumentProperties props = _cur.getDocProps();
        String systemId = null;
        String docTypeName = null;
        if (props != null) {
            systemId = props.getDoctypeSystemId();
            docTypeName = props.getDoctypeName();
        }

        if (systemId != null || docTypeName != null) {
            if (docTypeName == null) {
                _cur.push();
                //noinspection StatementWithEmptyBody
                while (!_cur.isElem() && _cur.next()) {
                }
                if (_cur.isElem()) {
                    docTypeName = _cur.getName().getLocalPart();
                }
                _cur.pop();
            }

            String publicId = props.getDoctypePublicId();

            if (docTypeName != null) {
                QName rootElemName = _cur.getName();

                if (rootElemName == null) {
                    _cur.push();
                    while (!_cur.isFinish()) {
                        if (_cur.isElem()) {
                            rootElemName = _cur.getName();
                            break;
                        }
                        _cur.next();
                    }
                    _cur.pop();
                }

                if (rootElemName != null && docTypeName.equals(rootElemName.getLocalPart())) {
                    emitDocType(docTypeName, publicId, systemId);
                    return;
                }
            }
        }

        emitStartDoc(_cur);
    }

    private void processElement() {
        assert _cur.isElem() && _cur.getName() != null;

        QName name = _cur.getName();

        // Add a new entry to the frontier.  If this element has a name
        // which has no namespace, then we must make sure that pushing
        // the mappings causes the default namespace to be empty

        boolean ensureDefaultEmpty = name.getNamespaceURI().isEmpty();

        pushMappings(_cur, ensureDefaultEmpty);

        //
        // There are four things which use mappings:
        //
        //   1) The element name
        //   2) The element value (qname based)
        //   3) Attribute names
        //   4) The attribute values (qname based)
        //

        // 1) The element name (not for starts)

        ensureMapping(name.getNamespaceURI(), name.getPrefix(), !ensureDefaultEmpty, false);

        //
        //
        //

        _attrNames.clear();
        _attrValues.clear();

        _cur.push();

        attrs:
        for (boolean A = _cur.toFirstAttr(); A; A = _cur.toNextAttr()) {
            if (_cur.isNormalAttr()) {
                QName attrName = _cur.getName();

                _attrNames.add(attrName);

                for (int i = _attrNames.size() - 2; i >= 0; i--) {
                    if (_attrNames.get(i).equals(attrName)) {
                        _attrNames.remove(_attrNames.size() - 1);
                        continue attrs;
                    }
                }

                _attrValues.add(_cur.getAttrValue());

                ensureMapping(attrName.getNamespaceURI(), attrName.getPrefix(), false, true);
            }
        }

        _cur.pop();

        // If I am doing aggressive namespaces and we're emitting a
        // container which can contain content, add the namespaces
        // we've computed.  Basically, I'm making sure the pre-computed
        // namespaces are mapped on the first container which has a name.

        if (_preComputedNamespaces != null) {
            for (Map.Entry<String, String> entry : _preComputedNamespaces.entrySet()) {
                String uri = entry.getKey();
                String prefix = entry.getValue();
                boolean considerDefault = prefix.isEmpty() && !ensureDefaultEmpty;

                ensureMapping(uri, prefix, considerDefault, false);
            }

            // Set to null so we do this once at the top
            _preComputedNamespaces = null;
        }

        if (emitElement(_cur, _attrNames, _attrValues)) {
            popMappings();
            _cur.toEnd();
        }
    }

    //
    // Layout of namespace stack:
    //
    //    URI Undo
    //    URI Rename
    //    Prefix Undo
    //    Mapping
    //

    void iterateMappings() {
        _currentMapping = _namespaceStack.size();

        while (_currentMapping > 0 && _namespaceStack.get(_currentMapping - 1) != null) {
            _currentMapping -= 8;
        }
    }

    boolean hasMapping() {
        return _currentMapping < _namespaceStack.size();
    }

    void nextMapping() {
        _currentMapping += 8;
    }

    String mappingPrefix() {
        assert hasMapping();
        return _namespaceStack.get(_currentMapping + 6);
    }

    String mappingUri() {
        assert hasMapping();
        return _namespaceStack.get(_currentMapping + 7);
    }

    private void pushMappings(SaveCur c, boolean ensureDefaultEmpty) {
        assert c.isContainer();

        _namespaceStack.add(null);

        c.push();

        for (boolean A = c.toFirstAttr(); A; A = c.toNextAttr()) {
            if (c.isXmlns()) {
                addNewFrameMapping(c.getXmlnsPrefix(), c.getXmlnsUri(), ensureDefaultEmpty);
            }
        }

        c.pop();

        if (_ancestorNamespaces != null) {
            for (int i = 0; i < _ancestorNamespaces.size(); i += 2) {
                String prefix = _ancestorNamespaces.get(i);
                String uri = _ancestorNamespaces.get(i + 1);

                addNewFrameMapping(prefix, uri, ensureDefaultEmpty);
            }

            _ancestorNamespaces = null;
        }

        if (ensureDefaultEmpty) {
            String defaultUri = _prefixMap.get("");

            // I map the default to "" at the very beginning
            assert defaultUri != null;

            if (!defaultUri.isEmpty()) {
                addMapping("", "");
            }
        }
    }

    private void addNewFrameMapping(String prefix, String uri, boolean ensureDefaultEmpty) {
        // If the prefix maps to "", then don't include this mapping 'cause it's not well-formed.
        // Also, if we want to make sure that the default namespace is always "", then check that
        // here as well.

        if ((prefix.isEmpty() || !uri.isEmpty()) &&
            (!ensureDefaultEmpty || !prefix.isEmpty() || uri.isEmpty())) {
            // Make sure the prefix is not already mapped in this frame

            for (iterateMappings(); hasMapping(); nextMapping()) {
                if (mappingPrefix().equals(prefix)) {
                    return;
                }
            }

            // Also make sure that the prefix declaration is not redundant
            // This has the side effect of making it impossible to set a
            // redundant prefix declaration, but seems that it's better
            // to just never issue a duplicate prefix declaration.
            if (uri.equals(getNamespaceForPrefix(prefix))) {
                return;
            }

            addMapping(prefix, uri);
        }
    }

    private void addMapping(String prefix, String uri) {
        assert uri != null;
        assert prefix != null;

        // If the prefix being mapped here is already mapped to a uri,
        // that uri will either go out of scope or be mapped to another
        // prefix.

        String renameUri = _prefixMap.get(prefix);
        String renamePrefix = null;

        if (renameUri != null) {
            // See if this prefix is already mapped to this uri.  If
            // so, then add to the stack, but there is nothing to rename

            if (renameUri.equals(uri)) {
                renameUri = null;
            } else {
                int i = _namespaceStack.size();

                while (i > 0) {
                    if (_namespaceStack.get(i - 1) == null) {
                        i--;
                        continue;
                    }

                    if (_namespaceStack.get(i - 7).equals(renameUri)) {
                        renamePrefix = _namespaceStack.get(i - 8);

                        if (renamePrefix == null || !renamePrefix.equals(prefix)) {
                            break;
                        }
                    }

                    i -= 8;
                }

                assert i > 0;
            }
        }

        _namespaceStack.add(_uriMap.get(uri));
        _namespaceStack.add(uri);

        if (renameUri != null) {
            _namespaceStack.add(_uriMap.get(renameUri));
            _namespaceStack.add(renameUri);
        } else {
            _namespaceStack.add(null);
            _namespaceStack.add(null);
        }

        _namespaceStack.add(prefix);
        _namespaceStack.add(_prefixMap.get(prefix));

        _namespaceStack.add(prefix);
        _namespaceStack.add(uri);

        _uriMap.put(uri, prefix);
        _prefixMap.put(prefix, uri);

        if (renameUri != null) {
            _uriMap.put(renameUri, renamePrefix);
        }
    }

    private void popMappings() {
        for (; ; ) {
            int i = _namespaceStack.size();

            if (i == 0) {
                break;
            }

            if (_namespaceStack.get(i - 1) == null) {
                _namespaceStack.remove(i - 1);
                break;
            }

            String oldUri = _namespaceStack.get(i - 7);
            String oldPrefix = _namespaceStack.get(i - 8);

            if (oldPrefix == null) {
                _uriMap.remove(oldUri);
            } else {
                _uriMap.put(oldUri, oldPrefix);
            }

            oldPrefix = _namespaceStack.get(i - 4);
            oldUri = _namespaceStack.get(i - 3);

            if (oldUri == null) {
                _prefixMap.remove(oldPrefix);
            } else {
                _prefixMap.put(oldPrefix, oldUri);
            }

            String uri = _namespaceStack.get(i - 5);

            if (uri != null) {
                _uriMap.put(uri, _namespaceStack.get(i - 6));
            }

            // Hahahahahaha -- :-(
            _namespaceStack.remove(i - 1);
            _namespaceStack.remove(i - 2);
            _namespaceStack.remove(i - 3);
            _namespaceStack.remove(i - 4);
            _namespaceStack.remove(i - 5);
            _namespaceStack.remove(i - 6);
            _namespaceStack.remove(i - 7);
            _namespaceStack.remove(i - 8);
        }
    }

    private void ensureMapping(
        String uri, String candidatePrefix,
        boolean considerCreatingDefault, boolean mustHavePrefix) {
        assert uri != null;

        // Can be called for no-namespaced things

        if (uri.isEmpty()) {
            return;
        }

        String prefix = _uriMap.get(uri);

        if (prefix != null && (!prefix.isEmpty() || !mustHavePrefix)) {
            return;
        }

        //
        // I try prefixes from a number of places, in order:
        //
        //  1) What was passed in
        //  2) The optional suggestions (for uri's)
        //  3) The default mapping is allowed
        //  4) ns#++
        //

        if (candidatePrefix != null && candidatePrefix.isEmpty()) {
            candidatePrefix = null;
        }

        if (!tryPrefix(candidatePrefix)) {
            if (_suggestedPrefixes != null &&
                _suggestedPrefixes.containsKey(uri) &&
                tryPrefix(_suggestedPrefixes.get(uri))) {
                candidatePrefix = _suggestedPrefixes.get(uri);
            } else if (considerCreatingDefault && _useDefaultNamespace && tryPrefix("")) {
                candidatePrefix = "";
            } else {
                String basePrefix = QNameHelper.suggestPrefix(uri);
                candidatePrefix = basePrefix;

                for (int i = 1; ; i++) {
                    if (tryPrefix(candidatePrefix)) {
                        break;
                    }

                    candidatePrefix = basePrefix + i;
                }
            }
        }

        assert candidatePrefix != null;

        syntheticNamespace(candidatePrefix, uri, considerCreatingDefault);

        addMapping(candidatePrefix, uri);
    }

    protected final String getUriMapping(String uri) {
        assert _uriMap.containsKey(uri);
        return _uriMap.get(uri);
    }

    String getNonDefaultUriMapping(String uri) {
        String prefix = _uriMap.get(uri);

        if (prefix != null && !prefix.isEmpty()) {
            return prefix;
        }

        for (String s : _prefixMap.keySet()) {
            prefix = s;

            if (!prefix.isEmpty() && _prefixMap.get(prefix).equals(uri)) {
                return prefix;
            }
        }

        assert false : "Could not find non-default mapping";

        return null;
    }

    private boolean tryPrefix(String prefix) {
        if (prefix == null || Locale.beginsWithXml(prefix)) {
            return false;
        }

        String existingUri = _prefixMap.get(prefix);

        // If the prefix is currently mapped, then try another prefix.  A
        // special case is that of trying to map the default prefix ("").
        // Here, there always exists a default mapping.  If this is the
        // mapping we found, then remap it anyways. I use != to compare
        // strings because I want to test for the specific initial default
        // uri I added when I initialized the saver.

        return existingUri == null || (prefix.length() <= 0 && Objects.equals(existingUri, _initialDefaultUri));
    }

    public final String getNamespaceForPrefix(String prefix) {
        assert !prefix.equals("xml") || _prefixMap.get(prefix).equals(Locale._xml1998Uri);

        return _prefixMap.get(prefix);
    }


    static final class SynthNamespaceSaver extends Saver {
        LinkedHashMap<String, String> _synthNamespaces = new LinkedHashMap<>();

        SynthNamespaceSaver(Cur c, XmlOptions options) {
            super(c, options);
        }

        protected void syntheticNamespace(
            String prefix, String uri, boolean considerCreatingDefault) {
            _synthNamespaces.put(uri, considerCreatingDefault ? "" : prefix);
        }

        protected boolean emitElement(
            SaveCur c, List<QName> attrNames, List<String> attrValues) {
            return false;
        }

        protected void emitFinish(SaveCur c) {
        }

        protected void emitText(SaveCur c) {
        }

        protected void emitComment(SaveCur c) {
        }

        protected void emitProcinst(SaveCur c) {
        }

        protected void emitDocType(String docTypeName, String publicId, String systemId) {
        }

        protected void emitStartDoc(SaveCur c) {
        }

        protected void emitEndDoc(SaveCur c) {
        }
    }

    //
    //
    //

    static final class TextSaver extends Saver {
        TextSaver(Cur c, XmlOptions options, String encoding) {
            super(c, options);

            boolean noSaveDecl = options != null && options.isSaveNoXmlDecl();

            if (options != null && options.getSaveCDataLengthThreshold() != null) {
                _cdataLengthThreshold = options.getSaveCDataLengthThreshold();
            }

            if (options != null && options.getSaveCDataEntityCountThreshold() != null) {
                _cdataEntityCountThreshold = options.getSaveCDataEntityCountThreshold();
            }

            if (options != null && options.isUseCDataBookmarks()) {
                _useCDataBookmarks = true;
            }

            if (options != null && options.isSavePrettyPrint()) {
                _isPrettyPrint = true;
            }

            _in = _out = 0;
            _free = 0;

            //noinspection ConstantConditions
            assert _cbuf == null ||
                   (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                  // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||               // no data, all buffer free
                   (_out == _in && _free == 0)                             // buffer full
                : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            if (encoding != null && !noSaveDecl) {
                XmlDocumentProperties props = Locale.getDocProps(c, false);

                String version = props == null ? null : props.getVersion();

                if (version == null) {
                    version = "1.0";
                }

                Boolean standalone = null;
                if (props != null && props.get(XmlDocumentProperties.STANDALONE) != null) {
                    standalone = props.getStandalone();
                }

                emit("<?xml version=\"");
                emit(version);
                emit("\" encoding=\"" + encoding + "\"");
                if (standalone != null) {
                    emit(" standalone=\"" + (standalone ? "yes" : "no") + "\"");
                }
                emit("?>" + _newLine);
            }
        }

        @Override
        protected boolean emitElement(SaveCur c, List<QName> attrNames, List<String> attrValues) {
            assert c.isElem();

            emit('<');
            emitName(c.getName(), false);

            if (saveNamespacesFirst()) {
                emitNamespacesHelper();
            }

            for (int i = 0; i < attrNames.size(); i++) {
                emitAttrHelper(attrNames.get(i), attrValues.get(i));
            }

            if (!saveNamespacesFirst()) {
                emitNamespacesHelper();
            }

            if (!c.hasChildren() && !c.hasText()) {
                emit('/', '>');
                return true;
            } else {
                emit('>');
                return false;
            }
        }

        protected void emitFinish(SaveCur c) {
            emit('<', '/');
            emitName(c.getName(), false);
            emit('>');
        }

        protected void emitXmlns(String prefix, String uri) {
            assert prefix != null;
            assert uri != null;

            emit("xmlns");

            if (!prefix.isEmpty()) {
                emit(':');
                emit(prefix);
            }

            emit('=', '\"');

            // TODO - must encode uri properly

            emit(uri);
            entitizeAttrValue(false);

            emit('"');
        }

        private void emitNamespacesHelper() {
            LinkedHashMap<String, String> nsMap = new LinkedHashMap<>();
            for (iterateMappings(); hasMapping(); nextMapping()) {
                String prefix = mappingPrefix();
                String uri = mappingUri();
                if (nsMap.containsKey(prefix)) {
                    //only overwrite the nsMap entry for the prefix if the stored entry has prefix="" and uri=""
                    if (prefix.isEmpty() && nsMap.get(prefix).isEmpty()) {
                        nsMap.put(prefix, uri);
                    }
                } else {
                    nsMap.put(prefix, uri);
                }
            }
            for (Map.Entry<String, String> nsEntry : nsMap.entrySet()) {
                emit(' ');
                emitXmlns(nsEntry.getKey(), nsEntry.getValue());
            }
        }

        private void emitAttrHelper(QName attrName, String attrValue) {
            emit(' ');
            emitName(attrName, true);
            emit('=', '\"');
            emit(attrValue);
            entitizeAttrValue(true);
            emit('"');
        }

        protected void emitText(SaveCur c) {
            assert c.isText();

            // c.isTextCData() is expensive do it only if useCDataBookmarks option is enabled
            boolean forceCData = _useCDataBookmarks && c.isTextCData();

            emit(c);

            entitizeContent(forceCData);
        }

        protected void emitComment(SaveCur c) {
            assert c.isComment();

            emit("<!--");

            c.push();
            c.next();

            emit(c);

            c.pop();

            entitizeComment();
            emit("-->");
        }

        protected void emitProcinst(SaveCur c) {
            assert c.isProcinst();

            emit("<?");

            // TODO - encoding issues here?
            emit(c.getName().getLocalPart());

            c.push();

            c.next();

            if (c.isText()) {
                emit(" ");
                emit(c);
                entitizeProcinst();
            }

            c.pop();

            emit("?>");
        }

        private void emitLiteral(String literal) {
            // TODO: systemId production http://www.w3.org/TR/REC-xml/#NT-SystemLiteral
            // TODO: publicId production http://www.w3.org/TR/REC-xml/#NT-PubidLiteral
            if (!literal.contains("\"")) {
                emit('\"');
                emit(literal);
                emit('\"');
            } else {
                emit('\'');
                emit(literal);
                emit('\'');
            }
        }

        protected void emitDocType(String docTypeName, String publicId, String systemId) {
            assert docTypeName != null;

            emit("<!DOCTYPE ");
            emit(docTypeName);

            if (publicId == null && systemId != null) {
                emit(" SYSTEM ");
                emitLiteral(systemId);
            } else if (publicId != null) {
                emit(" PUBLIC ");
                emitLiteral(publicId);
                emit(" ");
                emitLiteral(systemId);
            }

            emit(">");
            emit(_newLine);
        }

        protected void emitStartDoc(SaveCur c) {
        }

        protected void emitEndDoc(SaveCur c) {
        }

        //
        //
        //

        private void emitName(QName name, boolean needsPrefix) {
            assert name != null;

            if (name != null && (name.getLocalPart() == null || name.getLocalPart().isEmpty())) {
                throw new IllegalArgumentException("emitName does not support names with empty local part");
            }

            String uri = name.getNamespaceURI();

            assert uri != null;

            if (!uri.isEmpty()) {
                String prefix = name.getPrefix();
                String mappedUri = getNamespaceForPrefix(prefix);

                if (mappedUri == null || !mappedUri.equals(uri)) {
                    prefix = getUriMapping(uri);
                }

                // Attrs need a prefix.  If I have not found one, then there must be a default
                // prefix obscuring the prefix needed for this attr.  Find it manually.

                // NOTE - Consider keeping the currently mapped default URI separate fromn the
                // _urpMap and _prefixMap.  This way, I would not have to look it up manually
                // here

                if (needsPrefix && prefix.isEmpty()) {
                    prefix = getNonDefaultUriMapping(uri);
                }

                if (!prefix.isEmpty()) {
                    emit(prefix);
                    emit(':');
                }
            }

            emit(name.getLocalPart());
        }

        private void emit(char ch) {
            assert _cbuf == null ||
                   (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                  // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||               // no data, all buffer free
                   (_out == _in && _free == 0)                             // buffer full
                : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            preEmit(1);

            _cbuf[_in] = ch;

            _in = (_in + 1) % _cbuf.length;

            assert (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                  // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||               // no data, all buffer free
                   (_out == _in && _free == 0)                             // buffer full
                : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;
        }

        private void emit(char ch1, char ch2) {
            if (preEmit(2)) {
                return;
            }

            _cbuf[_in] = ch1;
            _in = (_in + 1) % _cbuf.length;

            _cbuf[_in] = ch2;
            _in = (_in + 1) % _cbuf.length;

            assert (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                   // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||                  // no data, all buffer free
                   (_out == _in && _free == 0)                               // buffer full
                : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;
        }

        private void emit(String s) {
            assert _cbuf == null ||
                   (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                  // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||               // no data, all buffer free
                   (_out == _in && _free == 0)                             // buffer full
                : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            int cch = s == null ? 0 : s.length();

            if (preEmit(cch) || s == null) {
                return;
            }

            int chunk;

            if (_in <= _out || cch < (chunk = _cbuf.length - _in)) {
                s.getChars(0, cch, _cbuf, _in);
                _in += cch;
            } else {
                s.getChars(0, chunk, _cbuf, _in);
                s.getChars(chunk, cch, _cbuf, 0);
                _in = (_in + cch) % _cbuf.length;
            }

            assert (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                  // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||               // no data, all buffer free
                   (_out == _in && _free == 0)                             // buffer full
                : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;
        }

        private void emit(SaveCur c) {
            if (c.isText()) {
                Object src = c.getChars();
                int cch = c._cchSrc;

                if (preEmit(cch)) {
                    return;
                }

                int chunk;

                if (_in <= _out || cch < (chunk = _cbuf.length - _in)) {
                    CharUtil.getChars(_cbuf, _in, src, c._offSrc, cch);
                    _in += cch;
                } else {
                    CharUtil.getChars(_cbuf, _in, src, c._offSrc, chunk);
                    CharUtil.getChars(_cbuf, 0, src, c._offSrc + chunk, cch - chunk);
                    _in = (_in + cch) % _cbuf.length;
                }
            } else {
                preEmit(0);
            }
        }

        private boolean preEmit(int cch) {
            assert cch >= 0;
            assert _cbuf == null ||
                   (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                   // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||                  // no data, all buffer free
                   (_out == _in && _free == 0)                               // buffer full
                : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            _lastEmitCch = cch;

            if (cch == 0) {
                return true;
            }

            if (_free <= cch) {
                resize(cch, -1);
            }

            assert cch <= _free;

            int used = getAvailable();

            // if we are about to emit and there is noting in the buffer, reset
            // the buffer to be at the beginning so as to not grow it anymore
            // than needed.

            if (used == 0) {
                assert _in == _out;
                assert _cbuf == null || _free == _cbuf.length;
                _in = _out = 0;
            }

            _lastEmitIn = _in;

            _free -= cch;

            assert _cbuf == null || _free == (_in >= _out ? _cbuf.length - (_in - _out) : _out - _in) - cch : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;
            assert _cbuf == null ||
                   (_out < _in && _free == _cbuf.length - (_in - _out) - cch) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in - cch) ||                  // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length - cch) ||                 // no data, all buffer free
                   (_out == _in && _free == 0)                                    // buffer full
                : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            return false;
        }

        private void entitizeContent(boolean forceCData) {
            assert _free >= 0;

            if (_lastEmitCch == 0) {
                return;
            }

            int i = _lastEmitIn;
            final int n = _cbuf.length;

            boolean hasCharToBeReplaced = false;

            int count = 0;
            char prevChar = 0;
            char prevPrevChar = 0;
            for (int cch = _lastEmitCch; cch > 0; cch--) {
                char ch = _cbuf[i];

                if (ch == '<' || ch == '&') {
                    count++;
                } else if (prevPrevChar == ']' && prevChar == ']' && ch == '>') {
                    hasCharToBeReplaced = true;
                } else if (isBadChar(ch) || isEscapedChar(ch) || (!_isPrettyPrint && ch == '\r')) {
                    hasCharToBeReplaced = true;
                }

                if (++i == n) {
                    i = 0;
                }

                prevPrevChar = prevChar;
                prevChar = ch;
            }

            if (!forceCData && count == 0 && !hasCharToBeReplaced && count < _cdataEntityCountThreshold) {
                return;
            }

            i = _lastEmitIn;

            //
            // Heuristic for knowing when to save out stuff as a CDATA.
            //
            if (forceCData || (_lastEmitCch > _cdataLengthThreshold && count > _cdataEntityCountThreshold)) {
                boolean lastWasBracket = _cbuf[i] == ']';

                i = replace(i, "<![CDATA[" + _cbuf[i]);

                boolean secondToLastWasBracket = lastWasBracket;

                lastWasBracket = _cbuf[i] == ']';

                if (++i == _cbuf.length) {
                    i = 0;
                }

                for (int cch = _lastEmitCch - 2; cch > 0; cch--) {
                    char ch = _cbuf[i];

                    if (ch == '>' && secondToLastWasBracket && lastWasBracket) {
                        i = replace(i, "]]>><![CDATA[");
                    } else if (isBadChar(ch)) {
                        i = replace(i, "?");
                    } else {
                        i++;
                    }

                    secondToLastWasBracket = lastWasBracket;
                    lastWasBracket = ch == ']';

                    if (i == _cbuf.length) {
                        i = 0;
                    }
                }

                emit("]]>");
            } else {
                char ch = 0, ch_1 = 0, ch_2;
                for (int cch = _lastEmitCch; cch > 0; cch--) {
                    ch_2 = ch_1;
                    ch_1 = ch;
                    ch = _cbuf[i];

                    if (ch == '<') {
                        i = replace(i, "&lt;");
                    } else if (ch == '&') {
                        i = replace(i, "&amp;");
                    } else if (ch == '>' && ch_1 == ']' && ch_2 == ']') {
                        i = replace(i, "&gt;");
                    } else if (isBadChar(ch)) {
                        i = replace(i, "?");
                    } else if (!_isPrettyPrint && ch == '\r') {
                        i = replace(i, "&#13;");
                    } else if (isEscapedChar(ch)) {
                        i = replace(i, _replaceChar.getEscapedString(ch));
                    } else {
                        i++;
                    }

                    if (i == _cbuf.length) {
                        i = 0;
                    }
                }
            }
        }

        private void entitizeAttrValue(boolean replaceEscapedChar) {
            if (_lastEmitCch == 0) {
                return;
            }

            int i = _lastEmitIn;

            for (int cch = _lastEmitCch; cch > 0; cch--) {
                char ch = _cbuf[i];

                if (ch == '<') {
                    i = replace(i, "&lt;");
                } else if (ch == '&') {
                    i = replace(i, "&amp;");
                } else if (ch == '"') {
                    i = replace(i, "&quot;");
                } else if (ch == '\t' && escapeAttrWhitespace()) {
                    i = replace(i, "&#9;");
                } else if (ch == '\n' && escapeAttrWhitespace()) {
                    i = replace(i, "&#10;");
                } else if (ch == '\r' && escapeAttrWhitespace()) {
                    i = replace(i, "&#13;");
                } else if (isEscapedChar(ch)) {
                    if (replaceEscapedChar) {
                        i = replace(i, _replaceChar.getEscapedString(ch));
                    }
                } else {
                    i++;
                }

                if (i == _cbuf.length) {
                    i = 0;
                }
            }
        }

        private void entitizeComment() {
            if (_lastEmitCch == 0) {
                return;
            }

            int i = _lastEmitIn;

            boolean lastWasDash = false;

            for (int cch = _lastEmitCch; cch > 0; cch--) {
                char ch = _cbuf[i];

                if (isBadChar(ch)) {
                    i = replace(i, "?");
                } else if (ch == '-') {
                    if (lastWasDash) {
                        // Replace "--" with "- " to make well-formed
                        i = replace(i, " ");
                        lastWasDash = false;
                    } else {
                        lastWasDash = true;
                        i++;
                    }
                } else {
                    lastWasDash = false;
                    i++;
                }

                if (i == _cbuf.length) {
                    i = 0;
                }
            }

            // Because I have only replaced chars with single chars,
            // _lastEmitIn will still be ok

            int offset = (_lastEmitIn + _lastEmitCch - 1) % _cbuf.length;
            if (_cbuf[offset] == '-') {
                replace(offset, " ");
            }
        }

        private void entitizeProcinst() {
            if (_lastEmitCch == 0) {
                return;
            }

            int i = _lastEmitIn;

            boolean lastWasQuestion = false;

            for (int cch = _lastEmitCch; cch > 0; cch--) {
                char ch = _cbuf[i];

                if (isBadChar(ch)) {
                    replace(i, "?");
                    ch = '?';
                }

                if (ch == '>') {
                    // TODO - Had to convert to a space here ... implies not well-formed XML
                    if (lastWasQuestion) {
                        i = replace(i, " ");
                    } else {
                        i++;
                    }

                    lastWasQuestion = false;
                } else {
                    lastWasQuestion = ch == '?';
                    i++;
                }

                if (i == _cbuf.length) {
                    i = 0;
                }
            }
        }

        /**
         * Test if a character is to be replaced with an escaped value
         */
        private boolean isEscapedChar(char ch) {
            return (null != _replaceChar && _replaceChar.containsChar(ch));
        }

        private int replace(int i, String replacement) {
            assert !replacement.isEmpty();

            int dCch = replacement.length() - 1;

            if (dCch == 0) {
                _cbuf[i] = replacement.charAt(0);
                return i + 1;
            }

            assert _free >= 0;

            if (dCch > _free) {
                i = resize(dCch, i);
            }

            assert _free >= 0;

            assert _free >= dCch;
            assert getAvailable() > 0;

            int charsToCopy = dCch + 1;

            if (_out > _in && i >= _out) {
                System.arraycopy(_cbuf, _out, _cbuf, _out - dCch, i - _out);
                _out -= dCch;
                i -= dCch;
            } else {
                assert i < _in;
                int availableEndChunk = _cbuf.length - _in;
                if (dCch <= availableEndChunk) {
                    System.arraycopy(_cbuf, i, _cbuf, i + dCch, _in - i);
                    _in = (_in + dCch) % _cbuf.length;
                } else if (dCch <= availableEndChunk + _in - i - 1) {
                    int numToCopyToStart = dCch - availableEndChunk;
                    System.arraycopy(_cbuf, _in - numToCopyToStart, _cbuf, 0, numToCopyToStart);
                    System.arraycopy(_cbuf, i + 1, _cbuf, i + 1 + dCch, _in - i - 1 - numToCopyToStart);

                    _in = numToCopyToStart;
                } else {
                    int numToCopyToStart = _in - i - 1;
                    charsToCopy = availableEndChunk + _in - i;

                    System.arraycopy(_cbuf, _in - numToCopyToStart, _cbuf, dCch - charsToCopy + 1, numToCopyToStart);
                    replacement.getChars(charsToCopy, dCch + 1, _cbuf, 0);

                    _in = numToCopyToStart + dCch - charsToCopy + 1;
                }
            }

            replacement.getChars(0, charsToCopy, _cbuf, i);

            _free -= dCch;

            assert _free >= 0;
            assert (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                   // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||                  // no data, all buffer free
                   (_out == _in && _free == 0)                               // buffer full
                : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            return (i + dCch + 1) % _cbuf.length;
        }
        //
        //
        //

        private int ensure(int cch) {
            // Even if we're asked to ensure nothing, still try to ensure
            // at least one character so we can determine if we're at the
            // end of the stream.

            if (cch <= 0) {
                cch = 1;
            }

            int available = getAvailable();

            for (; available < cch; available = getAvailable()) {
                if (!process()) {
                    break;
                }
            }

            assert available == getAvailable();

//            if (available == 0)
//                return 0;

            return available;
        }

        int getAvailable() {
            return _cbuf == null ? 0 : _cbuf.length - _free;
        }

        private int resize(int cch, int i) {
            assert _free >= 0;
            assert cch > 0;
            assert cch >= _free;
            assert _cbuf == null ||
                   (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                   // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||                  // no data, all buffer free
                   (_out == _in && _free == 0)                               // buffer full
                : "_buf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            long newLen = _cbuf == null ? _initialBufSize : _cbuf.length * 2L;
            int used = getAvailable();

            while (newLen - used < cch) {
                newLen *= 2;
            }

            final int bufLen = (int) Math.min(newLen, MAX_ARRAY_SIZE);
            char[] newBuf = new char[bufLen];

            if (used > 0) {
                if (_in > _out) {
                    // Data in the middle (between _in and _out, i must be between those)
                    assert i == -1 || (i >= _out && i < _in);
                    System.arraycopy(_cbuf, _out, newBuf, 0, used);
                    i -= _out;
                } else {
                    // Data is on the edges: Oldest part from _in to end,
                    // newest part from 0 to _out.
                    // it must be in either of those regions
                    assert i == -1 || (i >= _out || i < _in);
                    int oldestSize = used - _in;
                    System.arraycopy(_cbuf, _out, newBuf, 0, oldestSize );
                    System.arraycopy(_cbuf, 0, newBuf, oldestSize, _in );
                    // newBuf now contains oldest data now at [0,oldestSize) and newest at [oldestSize,oldestSize+_in)
                    // Where was i? If past _out, i was in the oldest part, so adjust back by start of the oldest part
                    // Or: i was in the newest part, and should now be adjusted forward to where the newest part is now
                    i = (i >= _out) ? i - _out : i + oldestSize;
                }

                _out = 0;
                _in = used;
                _free += newBuf.length - _cbuf.length;
            } else {
                _free = newBuf.length;
                assert _in == 0 && _out == 0;
                assert i == -1;
            }

            _cbuf = newBuf;

            assert _free >= 0;
            //noinspection ConstantConditions
            assert _cbuf == null ||
                   (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                   // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||                 // no data, all buffer free
                   (_out == _in && _free == 0)                               // buffer full
                : "_cbuf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            return i;
        }

        public int read() {
            if (ensure(1) == 0) {
                return -1;
            }

            assert getAvailable() > 0;

            int ch = _cbuf[_out];

            _out = (_out + 1) % _cbuf.length;
            _free++;

            assert (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                   // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||                  // no data, all buffer free
                   (_out == _in && _free == 0)                               // buffer full
                : "_cbuf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            return ch;
        }

        public int read(char[] cbuf, int off, int len) {
            // Check for end of stream even if there is no way to return
            // characters because the Reader doc says to return -1 at end of
            // stream.

            int n;

            if ((n = ensure(len)) == 0) {
                return -1;
            }

            if (cbuf == null || len <= 0) {
                return 0;
            }

            if (n < len) {
                len = n;
            }

            if (_out < _in) {
                System.arraycopy(_cbuf, _out, cbuf, off, len);
            } else {
                int chunk = _cbuf.length - _out;

                if (chunk >= len) {
                    System.arraycopy(_cbuf, _out, cbuf, off, len);
                } else {
                    System.arraycopy(_cbuf, _out, cbuf, off, chunk);
                    System.arraycopy(_cbuf, 0, cbuf, off + chunk, len - chunk);
                }
            }

            _out = (_out + len) % _cbuf.length;
            _free += len;

            assert (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                   // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||                  // no data, all buffer free
                   (_out == _in && _free == 0)                               // buffer full
                : "_cbuf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            assert _free >= 0;

            return len;
        }

        public int write(Writer writer, int cchMin) {
            while (getAvailable() < cchMin) {
                if (!process()) {
                    break;
                }
            }

            int charsAvailable = getAvailable();

            if (charsAvailable > 0) {
                // I don't want to deal with the circular cases

                assert _out == 0;
                assert _in >= _out : "_in:" + _in + " < _out:" + _out;
                if ((_cbuf.length - _in) != _free) {
                    //https://issues.apache.org/jira/browse/XMLBEANS-604
                    _in = _cbuf.length;
                }
                assert _free == _cbuf.length - _in;

                try {
//System.out.println("-------------\nWriting in converter: TextSaver.write():1703  " + charsAvailable + " chars\n" + new String(_cbuf, 0, charsAvailable));
                    writer.write(_cbuf, 0, charsAvailable);
                    writer.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                _free += charsAvailable;

                assert _free >= 0;

                _in = 0;
            }
            assert _cbuf == null ||
                   (_out < _in && _free == _cbuf.length - (_in - _out)) || // data in the middle, free on the edges
                   (_out > _in && _free == _out - _in) ||                   // data on the edges, free in the middle
                   (_out == _in && _free == _cbuf.length) ||                  // no data, all buffer free
                   (_out == _in && _free == 0)                               // buffer full
                : "_cbuf.length:" + _cbuf.length + " _in:" + _in + " _out:" + _out + " _free:" + _free;

            return charsAvailable;
        }

        public String saveToString() {
            // We're gonna build a string.  Instead of using StringBuffer, may
            // as well use my buffer here.  Fill the whole sucker up and
            // create a String!

            //noinspection StatementWithEmptyBody
            while (process()) {
            }

            assert _out == 0;

            int available = getAvailable();

            return available == 0 ? "" : new String(_cbuf, _out, available);
        }

        //
        //
        //

        private static final int _initialBufSize = 4096;
        private int _cdataLengthThreshold = 32;
        private int _cdataEntityCountThreshold = 5;
        private boolean _useCDataBookmarks = false;
        private boolean _isPrettyPrint = false;

        private int _lastEmitIn;
        private int _lastEmitCch;

        private int _free;
        private int _in;
        private int _out;
        private char[] _cbuf;
        /*
        _cbuf is a circular buffer, useful data is before _in up to _out, there are 2 possible configurations:
        1: _in<=_out  |data|_in  empty  _out|data|
        2: _out<_in   |empty _out|data|_in  empty|
        _free is used to keep around the remaining empty space in the buffer so  assert _cbuf==null || _free == (_in>=_out ? _cbuf.length - (_in - _out) : _out - _in ) ;
         */
    }

    static final class OptimizedForSpeedSaver
        extends Saver {
        Writer _w;
        private final char[] _buf = new char[1024];


        static private class SaverIOException
            extends RuntimeException {
            SaverIOException(IOException e) {
                super(e);
            }
        }


        OptimizedForSpeedSaver(Cur cur, Writer writer, XmlOptions options) {
            super(cur, XmlOptions.maskNull(options));
            _w = writer;
        }

        static void save(Cur cur, Writer writer, XmlOptions options)
            throws IOException {
            try {
                Saver saver = new OptimizedForSpeedSaver(cur, writer, options);
                //noinspection StatementWithEmptyBody
                while (saver.process()) {
                }
            } catch (SaverIOException e) {
                throw (IOException) e.getCause();
            }
        }

        private void emit(String s) {
            try {
                _w.write(s);
            } catch (IOException e) {
                throw new SaverIOException(e);
            }
        }

        private void emit(char c) {
            try {
                _buf[0] = c;
                _w.write(_buf, 0, 1);
            } catch (IOException e) {
                throw new SaverIOException(e);
            }
        }

        private void emit(char c1, char c2) {
            try {
                _buf[0] = c1;
                _buf[1] = c2;
                _w.write(_buf, 0, 2);
            } catch (IOException e) {
                throw new SaverIOException(e);
            }
        }

        private void emit(char[] buf, int start, int len) {
            try {
                _w.write(buf, start, len);
            } catch (IOException e) {
                throw new SaverIOException(e);
            }
        }

        protected boolean emitElement(SaveCur c, List<QName> attrNames, List<String> attrValues) {
            assert c.isElem();

            emit('<');
            emitName(c.getName(), false);

            for (int i = 0; i < attrNames.size(); i++) {
                emitAttrHelper(attrNames.get(i), attrValues.get(i));
            }

            if (!saveNamespacesFirst()) {
                emitNamespacesHelper();
            }

            if (!c.hasChildren() && !c.hasText()) {
                emit('/', '>');
                return true;
            } else {
                emit('>');
                return false;
            }
        }

        protected void emitFinish(SaveCur c) {
            emit('<', '/');
            emitName(c.getName(), false);
            emit('>');
        }

        protected void emitXmlns(String prefix, String uri) {
            assert prefix != null;
            assert uri != null;

            emit("xmlns");

            if (!prefix.isEmpty()) {
                emit(':');
                emit(prefix);
            }

            emit('=', '\"');

            // TODO - must encode uri properly
            emitAttrValue(uri);

            emit('"');
        }

        private void emitNamespacesHelper() {
            for (iterateMappings(); hasMapping(); nextMapping()) {
                emit(' ');
                emitXmlns(mappingPrefix(), mappingUri());
            }
        }

        private void emitAttrHelper(QName attrName, String attrValue) {
            emit(' ');
            emitName(attrName, true);
            emit('=', '\"');
            emitAttrValue(attrValue);

            emit('"');
        }

        protected void emitComment(SaveCur c) {
            assert c.isComment();

            emit("<!--");

            c.push();
            c.next();

            emitCommentText(c);

            c.pop();

            emit("-->");
        }

        protected void emitProcinst(SaveCur c) {
            assert c.isProcinst();

            emit("<?");

            // TODO - encoding issues here?
            emit(c.getName().getLocalPart());

            c.push();

            c.next();

            if (c.isText()) {
                emit(' ');
                emitPiText(c);
            }

            c.pop();

            emit("?>");
        }

        protected void emitDocType(String docTypeName, String publicId, String systemId) {
            assert docTypeName != null;

            emit("<!DOCTYPE ");
            emit(docTypeName);

            if (publicId == null && systemId != null) {
                emit(" SYSTEM ");
                emitLiteral(systemId);
            } else if (publicId != null) {
                emit(" PUBLIC ");
                emitLiteral(publicId);
                emit(' ');
                emitLiteral(systemId);
            }

            emit('>');
            emit(_newLine);
        }

        protected void emitStartDoc(SaveCur c) {
        }

        protected void emitEndDoc(SaveCur c) {
        }

        //
        //
        //

        private void emitName(QName name, boolean needsPrefix) {
            assert name != null;

            String uri = name.getNamespaceURI();

            assert uri != null;

            if (!uri.isEmpty()) {
                String prefix = name.getPrefix();
                String mappedUri = getNamespaceForPrefix(prefix);

                if (mappedUri == null || !mappedUri.equals(uri)) {
                    prefix = getUriMapping(uri);
                }

                // Attrs need a prefix.  If I have not found one, then there must be a default
                // prefix obscuring the prefix needed for this attr.  Find it manually.

                // NOTE - Consider keeping the currently mapped default URI separate fromn the
                // _urpMap and _prefixMap.  This way, I would not have to look it up manually
                // here

                if (needsPrefix && prefix.isEmpty()) {
                    prefix = getNonDefaultUriMapping(uri);
                }

                if (!prefix.isEmpty()) {
                    emit(prefix);
                    emit(':');
                }
            }

            assert !name.getLocalPart().isEmpty();

            emit(name.getLocalPart());
        }

        private void emitAttrValue(CharSequence attVal) {
            int len = attVal.length();

            for (int i = 0; i < len; i++) {
                char ch = attVal.charAt(i);

                if (ch == '<') {
                    emit("&lt;");
                } else if (ch == '&') {
                    emit("&amp;");
                } else if (ch == '"') {
                    emit("&quot;");
                } else if (ch == '\t' && escapeAttrWhitespace()) {
                    emit("&#9;");
                } else if (ch == '\n' && escapeAttrWhitespace()) {
                    emit("&#10;");
                } else if (ch == '\r' && escapeAttrWhitespace()) {
                    emit("&#13;");
                } else {
                    emit(ch);
                }
            }
        }

        private void emitLiteral(String literal) {
            // TODO: systemId production http://www.w3.org/TR/REC-xml/#NT-SystemLiteral
            // TODO: publicId production http://www.w3.org/TR/REC-xml/#NT-PubidLiteral
            if (!literal.contains("\"")) {
                emit('\"');
                emit(literal);
                emit('\"');
            } else {
                emit('\'');
                emit(literal);
                emit('\'');
            }
        }

        protected void emitText(SaveCur c) {
            assert c.isText();

            Object src = c.getChars();
            int cch = c._cchSrc;
            int off = c._offSrc;
            int index = 0;
            int trailingBrackets = 0;
            while (index < cch) {
                int indexLimit = Math.min(index + 512, cch);
                CharUtil.getChars(_buf, 0, src, off + index, indexLimit - index);
                trailingBrackets = entitizeAndWriteText(indexLimit - index, trailingBrackets);
                index = indexLimit;
            }
        }

        protected void emitPiText(SaveCur c) {
            assert c.isText();

            Object src = c.getChars();
            int cch = c._cchSrc;
            int off = c._offSrc;
            int index = 0;
            boolean lastWasQuestion = false;
            while (index < cch) {
                int indexLimit = Math.min(index + 512, cch);
                CharUtil.getChars(_buf, 0, src, off + index, indexLimit - index);
                lastWasQuestion = entitizeAndWritePIText(indexLimit - index, lastWasQuestion);
                index = indexLimit;
            }
        }

        protected void emitCommentText(SaveCur c) {
            assert c.isText();

            Object src = c.getChars();
            int cch = c._cchSrc;
            int off = c._offSrc;
            int index = 0;
            boolean lastWasDash = false;
            while (index < cch) {
                int indexLimit = Math.min(index + 512, cch);
                CharUtil.getChars(_buf, 0, src, off + index, indexLimit - index);
                lastWasDash = entitizeAndWriteCommentText(indexLimit - index, lastWasDash, indexLimit == cch);
                index = indexLimit;
            }
        }

        private int entitizeAndWriteText(int bufLimit, int trailingBrackets) {
            int index = 0;
            for (int i = 0; i < bufLimit; i++) {
                char c = _buf[i];
                if (isBadChar(c)) {
                    _buf[i] = '?';
                    c = '?';
                }
                switch (c) {
                    case '<':
                        emit(_buf, index, i - index);
                        emit("&lt;");
                        index = i + 1;
                        trailingBrackets = 0;
                        break;
                    case '&':
                        emit(_buf, index, i - index);
                        emit("&amp;");
                        index = i + 1;
                        trailingBrackets = 0;
                        break;
                    case '>':
                        // ']]>' is not allowed in content, so escape the '>' that closes it
                        if (trailingBrackets >= 2) {
                            emit(_buf, index, i - index);
                            emit("&gt;");
                            index = i + 1;
                        }
                        trailingBrackets = 0;
                        break;
                    case ']':
                        trailingBrackets++;
                        break;
                    default:
                        trailingBrackets = 0;
                        break;
                }
            }
            emit(_buf, index, bufLimit - index);
            return trailingBrackets;
        }

        private boolean entitizeAndWriteCommentText(int bufLimit, boolean lastWasDash, boolean lastChunk) {
            for (int i = 0; i < bufLimit; i++) {
                char ch = _buf[i];

                if (isBadChar(ch)) {
                    _buf[i] = '?';
                } else if (ch == '-') {
                    if (lastWasDash) {
                        // Replace "--" with "- " to make well-formed
                        _buf[i] = ' ';
                        lastWasDash = false;
                    } else {
                        lastWasDash = true;
                    }
                } else {
                    lastWasDash = false;
                }

                if (i == _buf.length) {
                    i = 0;
                }
            }

            // A trailing '-' would form "--->" with the closing delimiter, so it
            // is escaped only at the real end of the comment, not on every chunk.
            if (lastChunk && _buf[bufLimit - 1] == '-') {
                _buf[bufLimit - 1] = ' ';
                lastWasDash = false;
            }

            emit(_buf, 0, bufLimit);
            return lastWasDash;
        }

        private boolean entitizeAndWritePIText(int bufLimit, boolean lastWasQuestion) {
            for (int i = 0; i < bufLimit; i++) {
                char ch = _buf[i];

                if (isBadChar(ch)) {
                    _buf[i] = '?';
                    ch = '?';
                }

                if (ch == '>') {
                    // Had to convert to a space here ... implies not well-formed XML
                    if (lastWasQuestion) {
                        _buf[i] = ' ';
                    }

                    lastWasQuestion = false;
                } else {
                    lastWasQuestion = ch == '?';
                }
            }
            emit(_buf, 0, bufLimit);
            return lastWasQuestion;
        }
    }

    private interface SyncWrapFun {
        int process() throws IOException;
    }

    private static int syncWrap(Locale l, SyncWrapFun fun) throws IOException {
        if (l.noSync()) {
            l.enter();
            try {
                return fun.process();
            } finally {
                l.exit();
            }
        } else {
            synchronized (l) {
                l.enter();
                try {
                    return fun.process();
                } finally {
                    l.exit();
                }
            }
        }
    }


    static final class TextReader extends Reader {
        TextReader(Cur c, XmlOptions options) {
            _textSaver = new TextSaver(c, options, null);
            _locale = c._locale;
            _closed = false;
        }

        public void close() {
            _closed = true;
        }

        public boolean ready() {
            return !_closed;
        }

        public int read() throws IOException {
            checkClosed();
            return syncWrap(_locale, _textSaver::read);
        }

        public int read(char[] cbuf) throws IOException {
            checkClosed();
            return syncWrap(_locale, () -> _textSaver.read(cbuf, 0, cbuf == null ? 0 : cbuf.length));
        }

        public int read(char[] cbuf, int off, int len) throws IOException {
            checkClosed();
            return syncWrap(_locale, () -> _textSaver.read(cbuf, off, len));
        }

        private void checkClosed() throws IOException {
            if (_closed) {
                throw new IOException("Reader has been closed");
            }
        }

        private final Locale _locale;
        private final TextSaver _textSaver;
        private boolean _closed;
    }

    static final class InputStreamSaver extends InputStream {

        InputStreamSaver(Cur c, XmlOptions options) {
            _locale = c._locale;

            _closed = false;

            assert _locale.entered();

            options = XmlOptions.maskNull(options);

            _outStreamImpl = new OutputStreamImpl();

            String encoding = null;

            XmlDocumentProperties props = Locale.getDocProps(c, false);

            if (props != null && props.getEncoding() != null) {
                encoding = EncodingMap.getIANA2JavaMapping(props.getEncoding());
            }

            String enc = options.getCharacterEncoding();
            if (enc != null) {
                encoding = enc;
            }

            if (encoding != null) {
                String ianaEncoding = EncodingMap.getJava2IANAMapping(encoding);

                if (ianaEncoding != null) {
                    encoding = ianaEncoding;
                }
            }

            if (encoding == null) {
                encoding = EncodingMap.getJava2IANAMapping("UTF8");
            }

            String javaEncoding = (encoding == null) ? null : EncodingMap.getIANA2JavaMapping(encoding);

            if (javaEncoding == null) {
                throw new IllegalStateException("Unknown encoding: " + encoding);
            }

            try {
                _converter = new OutputStreamWriter(_outStreamImpl, javaEncoding);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            _textSaver = new TextSaver(c, options, encoding);
        }

        public void close() {
            _closed = true;
        }

        private void checkClosed() throws IOException {
            if (_closed) {
                throw new IOException("Stream closed");
            }
        }

        // Having the gateway here is kinda slow for the single character case.  It may be possible
        // to only enter the gate when there are no chars in the buffer.

        public int read() throws IOException {
            checkClosed();

            if (_locale.noSync()) {
                _locale.enter();
                try {
                    return _outStreamImpl.read();
                } finally {
                    _locale.exit();
                }
            } else {
                synchronized (_locale) {
                    _locale.enter();
                    try {
                        return _outStreamImpl.read();
                    } finally {
                        _locale.exit();
                    }
                }
            }
        }

        public int read(byte[] bbuf, int off, int len) throws IOException {
            checkClosed();

            if (bbuf == null) {
                throw new NullPointerException("buf to read into is null");
            }

            if (off < 0 || off > bbuf.length) {
                throw new IndexOutOfBoundsException("Offset is not within buf");
            }

            return syncWrap(_locale, () -> _outStreamImpl.read(bbuf, off, len));
        }

        private int ensure(int cbyte) {
            // Even if we're asked to ensure nothing, still try to ensure
            // at least one byte so we can determine if we're at the
            // end of the stream.

            if (cbyte <= 0) {
                cbyte = 1;
            }

            int bytesAvailable = _outStreamImpl.getAvailable();

            for (; bytesAvailable < cbyte;
                 bytesAvailable = _outStreamImpl.getAvailable()) {
                if (_textSaver.write(_converter, 2048) < 2048) {
                    break;
                }
            }

            bytesAvailable = _outStreamImpl.getAvailable();

//            if (bytesAvailable == 0)
//                return 0;

            return bytesAvailable;
        }

        public int available() {
            try {
                return syncWrap(_locale, () -> ensure(1024));
            } catch (IOException e) {
                assert false : "ensure doesn't throw IOException and available() shouldn't throw either";
                throw new RuntimeException(e);
            }
        }

        private final class OutputStreamImpl extends OutputStream {
            int read() {
                if (InputStreamSaver.this.ensure(1) == 0) {
                    return -1;
                }

                assert getAvailable() > 0;

                int bite = _buf[_out];

                _out = (_out + 1) % _buf.length;
                _free++;

                return bite;
            }

            int read(byte[] bbuf, int off, int len) {
                // Check for end of stream even if there is no way to return
                // characters because the Reader doc says to return -1 at end of
                // stream.

                int n;

                if ((n = ensure(len)) == 0) {
                    return -1;
                }

                if (bbuf == null || len <= 0) {
                    return 0;
                }

                if (n < len) {
                    len = n;
                }

                if (_out < _in) {
                    System.arraycopy(_buf, _out, bbuf, off, len);
                } else {
                    int chunk = _buf.length - _out;

                    if (chunk >= len) {
                        System.arraycopy(_buf, _out, bbuf, off, len);
                    } else {
                        System.arraycopy(_buf, _out, bbuf, off, chunk);

                        System.arraycopy(
                            _buf, 0, bbuf, off + chunk, len - chunk);
                    }
                }
                _out = (_out + len) % _buf.length;
                _free += len;

//System.out.println("------------------------\nRead out of queue: Saver:2440 InputStreamSaver.read() bbuf   " + len + " bytes :\n" + new String(bbuf, off, len));
                return len;
            }

            int getAvailable() {
                return _buf == null ? 0 : _buf.length - _free;
            }

            public void write(int bite) {
                if (_free == 0) {
                    resize(1);
                }

                assert _free > 0;

                _buf[_in] = (byte) bite;

                _in = (_in + 1) % _buf.length;
                _free--;
            }

            public void write(byte[] buf, int off, int cbyte) {
                assert cbyte >= 0;
                if (cbyte == 0) {
                    return;
                }

                if (_free < cbyte) {
                    resize(cbyte);
                }

                if (_in == _out) {
                    assert getAvailable() == 0 && _buf != null && _free == _buf.length;
                    _in = _out = 0;
                }

                int chunk = _buf.length - _in;

                if (_in <= _out || cbyte < chunk) {
                    System.arraycopy(buf, off, _buf, _in, cbyte);
                    _in += cbyte;
                } else {
                    System.arraycopy(buf, off, _buf, _in, chunk);

                    System.arraycopy(
                        buf, off + chunk, _buf, 0, cbyte - chunk);

                    _in = (_in + cbyte) % _buf.length;
                }

                _free -= cbyte;
            }

            void resize(int cbyte) {
                assert cbyte > _free : cbyte + " !> " + _free;

                long newLen = _buf == null ? _initialBufSize : _buf.length * 2L;
                int used = getAvailable();

                while (newLen - used < cbyte) {
                    newLen *= 2;
                }

                final int bufLen = (int) Math.min(newLen, MAX_ARRAY_SIZE);

                final byte[] newBuf = new byte[bufLen];

                if (used > 0) {
                    if (_in > _out) {
                        System.arraycopy(_buf, _out, newBuf, 0, used);
                    } else {
                        System.arraycopy(
                            _buf, _out, newBuf, 0, used - _in);

                        System.arraycopy(
                            _buf, 0, newBuf, used - _in, _in);
                    }

                    _out = 0;
                    _in = used;
                    _free += newBuf.length - _buf.length;
                } else {
                    _free = newBuf.length;
                    assert _in == _out;
                }

                _buf = newBuf;
            }

            private static final int _initialBufSize = 4096;

            private int _free;
            private int _in;
            private int _out;
            private byte[] _buf;
        }

        private final Locale _locale;
        private boolean _closed;
        private final OutputStreamImpl _outStreamImpl;
        private final TextSaver _textSaver;
        private final OutputStreamWriter _converter;
    }

    static final class SaxSaver extends Saver {
        SaxSaver(Cur c, XmlOptions options, ContentHandler ch, LexicalHandler lh)
            throws SAXException {
            super(c, options);

            _contentHandler = ch;
            _lexicalHandler = lh;

            _attributes = new AttributesImpl();
            _nsAsAttrs = !options.isSaveSaxNoNSDeclsInAttributes();

            _contentHandler.startDocument();

            try {
                //noinspection StatementWithEmptyBody
                while (process()) {
                }
            } catch (SaverSAXException e) {
                throw e._saxException;
            }

            _contentHandler.endDocument();
        }

        private static class SaverSAXException extends RuntimeException {
            SaverSAXException(SAXException e) {
                _saxException = e;
            }

            SAXException _saxException;
        }

        private String getPrefixedName(QName name) {
            String uri = name.getNamespaceURI();
            String local = name.getLocalPart();

            if (uri.isEmpty()) {
                return local;
            }

            String prefix = getUriMapping(uri);

            if (prefix.isEmpty()) {
                return local;
            }

            return prefix + ":" + local;
        }

        private void emitNamespacesHelper() {
            for (iterateMappings(); hasMapping(); nextMapping()) {
                String prefix = mappingPrefix();
                String uri = mappingUri();

                try {
                    _contentHandler.startPrefixMapping(prefix, uri);
                } catch (SAXException e) {
                    throw new SaverSAXException(e);
                }

                if (_nsAsAttrs) {
                    if (prefix == null || prefix.isEmpty()) {
                        _attributes.addAttribute("http://www.w3.org/2000/xmlns/", "xmlns", "xmlns", "CDATA", uri);
                    } else {
                        _attributes.addAttribute("http://www.w3.org/2000/xmlns/", prefix, "xmlns:" + prefix, "CDATA", uri);
                    }
                }
            }
        }

        @Override
        protected boolean emitElement(SaveCur c, List<QName> attrNames, List<String> attrValues) {
            _attributes.clear();

            if (saveNamespacesFirst()) {
                emitNamespacesHelper();
            }

            for (int i = 0; i < attrNames.size(); i++) {
                QName name = attrNames.get(i);

                _attributes.addAttribute(
                    name.getNamespaceURI(), name.getLocalPart(), getPrefixedName(name),
                    "CDATA", attrValues.get(i));
            }

            if (!saveNamespacesFirst()) {
                emitNamespacesHelper();
            }

            QName elemName = c.getName();

            try {
                _contentHandler.startElement(
                    elemName.getNamespaceURI(), elemName.getLocalPart(),
                    getPrefixedName(elemName), _attributes);
            } catch (SAXException e) {
                throw new SaverSAXException(e);
            }

            return false;
        }

        protected void emitFinish(SaveCur c) {
            QName name = c.getName();

            try {
                _contentHandler.endElement(
                    name.getNamespaceURI(), name.getLocalPart(), getPrefixedName(name));

                for (iterateMappings(); hasMapping(); nextMapping()) {
                    _contentHandler.endPrefixMapping(mappingPrefix());
                }
            } catch (SAXException e) {
                throw new SaverSAXException(e);
            }
        }

        protected void emitText(SaveCur c) {
            assert c.isText();

            Object src = c.getChars();

            try {
                if (src instanceof char[] chars) {
                    // Pray the user does not modify the buffer ....
                    _contentHandler.characters(chars, c._offSrc, c._cchSrc);
                } else {
                    if (_buf == null) {
                        _buf = new char[1024];
                    }

                    while (c._cchSrc > 0) {
                        int cch = java.lang.Math.min(_buf.length, c._cchSrc);

                        CharUtil.getChars(_buf, 0, src, c._offSrc, cch);

                        _contentHandler.characters(_buf, 0, cch);

                        c._offSrc += cch;
                        c._cchSrc -= cch;
                    }
                }
            } catch (SAXException e) {
                throw new SaverSAXException(e);
            }
        }

        protected void emitComment(SaveCur c) {
            if (_lexicalHandler != null) {
                c.push();

                c.next();

                try {
                    if (!c.isText()) {
                        _lexicalHandler.comment(null, 0, 0);
                    } else {
                        Object src = c.getChars();

                        if (src instanceof char[] chars) {
                            // Pray the user does not modify the buffer ....
                            _lexicalHandler.comment(chars, c._offSrc, c._cchSrc);
                        } else {
                            if (_buf == null || _buf.length < c._cchSrc) {
                                _buf = new char[java.lang.Math.max(1024, c._cchSrc)];
                            }

                            CharUtil.getChars(_buf, 0, src, c._offSrc, c._cchSrc);

                            _lexicalHandler.comment(_buf, 0, c._cchSrc);
                        }
                    }
                } catch (SAXException e) {
                    throw new SaverSAXException(e);
                }

                c.pop();
            }
        }

        protected void emitProcinst(SaveCur c) {
            c.push();

            c.next();

            String value = CharUtil.getString(c.getChars(), c._offSrc, c._cchSrc);

            c.pop();

            try {
                _contentHandler.processingInstruction(c.getName().getLocalPart(), value);
            } catch (SAXException e) {
                throw new SaverSAXException(e);
            }
        }

        protected void emitDocType(String docTypeName, String publicId, String systemId) {
            if (_lexicalHandler != null) {
                try {
                    _lexicalHandler.startDTD(docTypeName, publicId, systemId);
                    _lexicalHandler.endDTD();
                } catch (SAXException e) {
                    throw new SaverSAXException(e);
                }
            }
        }

        protected void emitStartDoc(SaveCur c) {
        }

        protected void emitEndDoc(SaveCur c) {
        }

        private final ContentHandler _contentHandler;
        private final LexicalHandler _lexicalHandler;

        private final AttributesImpl _attributes;

        private char[] _buf;
        private final boolean _nsAsAttrs;
    }

    //
    //
    //

    static abstract class SaveCur {
        final boolean isRoot() {
            return kind() == ROOT;
        }

        final boolean isElem() {
            return kind() == ELEM;
        }

        final boolean isAttr() {
            return kind() == ATTR;
        }

        final boolean isText() {
            return kind() == TEXT;
        }

        final boolean isComment() {
            return kind() == COMMENT;
        }

        final boolean isProcinst() {
            return kind() == PROCINST;
        }

        final boolean isFinish() {
            return Cur.kindIsFinish(kind());
        }

        final boolean isContainer() {
            return Cur.kindIsContainer(kind());
        }

        final boolean isNormalAttr() {
            return kind() == ATTR && !isXmlns();
        }

        @SuppressWarnings("unused")
        final boolean skip() {
            toEnd();
            return next();
        }

        abstract void release();

        abstract int kind();

        abstract QName getName();

        abstract String getXmlnsPrefix();

        abstract String getXmlnsUri();

        abstract boolean isXmlns();

        abstract boolean hasChildren();

        abstract boolean hasText();

        abstract boolean isTextCData();

        abstract boolean toFirstAttr();

        abstract boolean toNextAttr();

        abstract String getAttrValue();

        abstract boolean next();

        abstract void toEnd();

        abstract void push();

        abstract void pop();

        abstract Object getChars();

        abstract List<String> getAncestorNamespaces();

        abstract XmlDocumentProperties getDocProps();

        int _offSrc;
        int _cchSrc;
    }

    // TODO - saving a fragment need to take namesapces from root and
    // reflect them on the document element

    private static final class DocSaveCur extends SaveCur {
        DocSaveCur(Cur c) {
            assert c.isRoot();
            _cur = c.weakCur(this);
        }

        void release() {
            _cur.release();
            _cur = null;
        }

        int kind() {
            return _cur.kind();
        }

        QName getName() {
            return _cur.getName();
        }

        String getXmlnsPrefix() {
            return _cur.getXmlnsPrefix();
        }

        String getXmlnsUri() {
            return _cur.getXmlnsUri();
        }

        boolean isXmlns() {
            return _cur.isXmlns();
        }

        boolean hasChildren() {
            return _cur.hasChildren();
        }

        boolean hasText() {
            return _cur.hasText();
        }

        boolean isTextCData() {
            return _cur.isTextCData();
        }

        boolean toFirstAttr() {
            return _cur.toFirstAttr();
        }

        boolean toNextAttr() {
            return _cur.toNextAttr();
        }

        String getAttrValue() {
            assert _cur.isAttr();
            return _cur.getValueAsString();
        }

        void toEnd() {
            _cur.toEnd();
        }

        boolean next() {
            return _cur.next();
        }

        void push() {
            _cur.push();
        }

        void pop() {
            _cur.pop();
        }

        List<String> getAncestorNamespaces() {
            return null;
        }

        Object getChars() {
            Object o = _cur.getChars(-1);

            _offSrc = _cur._offSrc;
            _cchSrc = _cur._cchSrc;

            return o;
        }

        XmlDocumentProperties getDocProps() {
            return Locale.getDocProps(_cur, false);
        }

        private Cur _cur;
    }

    private static abstract class FilterSaveCur extends SaveCur {
        FilterSaveCur(SaveCur c) {
            assert c.isRoot();
            _cur = c;
        }

        // Can filter anything by root and attributes and text
        protected abstract boolean filter();

        void release() {
            _cur.release();
            _cur = null;
        }

        int kind() {
            return _cur.kind();
        }

        QName getName() {
            return _cur.getName();
        }

        String getXmlnsPrefix() {
            return _cur.getXmlnsPrefix();
        }

        String getXmlnsUri() {
            return _cur.getXmlnsUri();
        }

        boolean isXmlns() {
            return _cur.isXmlns();
        }

        boolean hasChildren() {
            return _cur.hasChildren();
        }

        boolean hasText() {
            return _cur.hasText();
        }

        boolean isTextCData() {
            return _cur.isTextCData();
        }

        boolean toFirstAttr() {
            return _cur.toFirstAttr();
        }

        boolean toNextAttr() {
            return _cur.toNextAttr();
        }

        String getAttrValue() {
            return _cur.getAttrValue();
        }

        void toEnd() {
            _cur.toEnd();
        }

        boolean next() {
            if (!_cur.next()) {
                return false;
            }

            if (!filter()) {
                return true;
            }

            assert !isRoot() && !isText() && !isAttr();

            toEnd();

            return next();
        }

        void push() {
            _cur.push();
        }

        void pop() {
            _cur.pop();
        }

        List<String> getAncestorNamespaces() {
            return _cur.getAncestorNamespaces();
        }

        Object getChars() {
            Object o = _cur.getChars();

            _offSrc = _cur._offSrc;
            _cchSrc = _cur._cchSrc;

            return o;
        }

        XmlDocumentProperties getDocProps() {
            return _cur.getDocProps();
        }

        private SaveCur _cur;
    }

    private static final class FilterPiSaveCur extends FilterSaveCur {
        FilterPiSaveCur(SaveCur c, String target) {
            super(c);

            _piTarget = target;
        }

        protected boolean filter() {
            return kind() == PROCINST && getName().getLocalPart().equals(_piTarget);
        }

        private final String _piTarget;
    }

    private static final class FragSaveCur extends SaveCur {
        FragSaveCur(Cur start, Cur end, QName synthElem) {
            _saveAttr = start.isAttr() && start.isSamePos(end);

            _cur = start.weakCur(this);
            _end = end.weakCur(this);

            _elem = synthElem;

            _state = ROOT_START;

            _stateStack = new int[8];

            start.push();
            computeAncestorNamespaces(start);
            start.pop();
        }

        List<String> getAncestorNamespaces() {
            return _ancestorNamespaces;
        }

        private void computeAncestorNamespaces(Cur c) {
            _ancestorNamespaces = new ArrayList<>();

            while (c.toParentRaw()) {
                if (c.toFirstAttr()) {
                    do {
                        if (c.isXmlns()) {
                            String prefix = c.getXmlnsPrefix();
                            String uri = c.getXmlnsUri();

                            // Don't let xmlns:foo="" get used

                            if (!uri.isEmpty() || prefix.isEmpty()) {
                                _ancestorNamespaces.add(c.getXmlnsPrefix());
                                _ancestorNamespaces.add(c.getXmlnsUri());
                            }
                        }
                    }
                    while (c.toNextAttr());

                    c.toParent();
                }
            }
        }

        //
        //
        //

        void release() {
            _cur.release();
            _cur = null;

            _end.release();
            _end = null;
        }

        int kind() {
            switch (_state) {
                case ROOT_START:
                    return ROOT;
                case ELEM_START:
                    return ELEM;
                case ELEM_END:
                    return -ELEM;
                case ROOT_END:
                    return -ROOT;
            }

            assert _state == CUR;

            return _cur.kind();
        }

        QName getName() {
            switch (_state) {
                case ROOT_START:
                case ROOT_END:
                    return null;
                case ELEM_START:
                case ELEM_END:
                    return _elem;
            }

            assert _state == CUR;

            return _cur.getName();
        }

        String getXmlnsPrefix() {
            assert _state == CUR && _cur.isAttr();
            return _cur.getXmlnsPrefix();
        }

        String getXmlnsUri() {
            assert _state == CUR && _cur.isAttr();
            return _cur.getXmlnsUri();
        }

        boolean isXmlns() {
            assert _state == CUR && _cur.isAttr();
            return _cur.isXmlns();
        }

        boolean hasChildren() {
            boolean hasChildren = false;

            if (isContainer()) {   // is there a faster way to do this?
                push();
                next();

                if (!isText() && !isFinish()) {
                    hasChildren = true;
                }

                pop();
            }

            return hasChildren;
        }

        boolean hasText() {
            boolean hasText = false;

            if (isContainer()) {
                push();
                next();

                if (isText()) {
                    hasText = true;
                }

                pop();
            }

            return hasText;
        }

        boolean isTextCData() {
            return _cur.isTextCData();
        }

        Object getChars() {
            assert _state == CUR && _cur.isText();

            Object src = _cur.getChars(-1);

            _offSrc = _cur._offSrc;
            _cchSrc = _cur._cchSrc;

            return src;
        }

        boolean next() {
            switch (_state) {
                case ROOT_START: {
                    _state = _elem == null ? CUR : ELEM_START;
                    break;
                }

                case ELEM_START: {
                    if (_saveAttr) {
                        _state = ELEM_END;
                    } else {
                        if (_cur.isAttr()) {
                            _cur.toParent();
                            _cur.next();
                        }

                        if (_cur.isSamePos(_end)) {
                            _state = ELEM_END;
                        } else {
                            _state = CUR;
                        }
                    }

                    break;
                }

                case CUR: {
                    assert !_cur.isAttr();

                    _cur.next();

                    if (_cur.isSamePos(_end)) {
                        _state = _elem == null ? ROOT_END : ELEM_END;
                    }

                    break;
                }

                case ELEM_END: {
                    _state = ROOT_END;
                    break;
                }
                case ROOT_END:
                    return false;
            }

            return true;
        }

        void toEnd() {
            switch (_state) {
                case ROOT_START:
                    _state = ROOT_END;
                    return;
                case ELEM_START:
                    _state = ELEM_END;
                    return;
                case ROOT_END:
                case ELEM_END:
                    return;
            }

            assert _state == CUR && !_cur.isAttr() && !_cur.isText();

            _cur.toEnd();
        }

        boolean toFirstAttr() {
            switch (_state) {
                case ROOT_END:
                case ELEM_END:
                case ROOT_START:
                    return false;
                case CUR:
                    return _cur.toFirstAttr();
            }

            assert _state == ELEM_START;

            if (!_cur.isAttr()) {
                return false;
            }

            _state = CUR;

            return true;
        }

        boolean toNextAttr() {
            assert _state == CUR;
            return !_saveAttr && _cur.toNextAttr();
        }

        String getAttrValue() {
            assert _state == CUR && _cur.isAttr();
            return _cur.getValueAsString();
        }

        void push() {
            if (_stateStackSize == _stateStack.length) {
                int[] newStateStack = new int[_stateStackSize * 2];
                System.arraycopy(_stateStack, 0, newStateStack, 0, _stateStackSize);
                _stateStack = newStateStack;
            }

            _stateStack[_stateStackSize++] = _state;
            _cur.push();
        }

        void pop() {
            _cur.pop();
            _state = _stateStack[--_stateStackSize];
        }

        XmlDocumentProperties getDocProps() {
            return Locale.getDocProps(_cur, false);
        }

        //
        //
        //

        private Cur _cur;
        private Cur _end;

        private ArrayList<String> _ancestorNamespaces;

        private final QName _elem;

        private final boolean _saveAttr;

        private static final int ROOT_START = 1;
        private static final int ELEM_START = 2;
        private static final int ROOT_END = 3;
        private static final int ELEM_END = 4;
        private static final int CUR = 5;

        private int _state;

        private int[] _stateStack;
        private int _stateStackSize;
    }

    private static final class PrettySaveCur extends SaveCur {
        PrettySaveCur(SaveCur c, XmlOptions options) {
            _sb = new StringBuffer();
            _stack = new ArrayList<>();

            _cur = c;

            assert options != null;

            _prettyIndent = 2;

            if (options.getSavePrettyPrintIndent() != null) {
                _prettyIndent = options.getSavePrettyPrintIndent();
            }

            if (options.getSavePrettyPrintOffset() != null) {
                _prettyOffset = options.getSavePrettyPrintOffset();
            }

            _useCDataBookmarks = options.isUseCDataBookmarks();
        }

        List<String> getAncestorNamespaces() {
            return _cur.getAncestorNamespaces();
        }

        void release() {
            _cur.release();
        }

        int kind() {
            return _txt == null ? _cur.kind() : TEXT;
        }

        QName getName() {
            assert _txt == null;
            return _cur.getName();
        }

        String getXmlnsPrefix() {
            assert _txt == null;
            return _cur.getXmlnsPrefix();
        }

        String getXmlnsUri() {
            assert _txt == null;
            return _cur.getXmlnsUri();
        }

        boolean isXmlns() {
            return _txt == null && _cur.isXmlns();
        }

        boolean hasChildren() {
            return _txt == null && _cur.hasChildren();
        }

        boolean hasText() {
            return _txt == null && _cur.hasText();
        }

        // _cur.isTextCData() is expensive do it only if useCDataBookmarks option is enabled
        boolean isTextCData() {
            return _txt == null ? (_useCDataBookmarks && _cur.isTextCData())
                : _isTextCData;
        }

        boolean toFirstAttr() {
            assert _txt == null;
            return _cur.toFirstAttr();
        }

        boolean toNextAttr() {
            assert _txt == null;
            return _cur.toNextAttr();
        }

        String getAttrValue() {
            assert _txt == null;
            return _cur.getAttrValue();
        }

        void toEnd() {
            assert _txt == null;
            _cur.toEnd();

            if (_cur.kind() == -ELEM) {
                _depth--;
            }
        }

        boolean next() {
            int k;

            if (_txt != null) {
                assert !_txt.isEmpty();
                assert !_cur.isText();
                _txt = null;
                _isTextCData = false;
                k = _cur.kind();
            } else {
                int prevKind = _cur.kind();

                if (!_cur.next()) {
                    return false;
                }

                _sb.delete(0, _sb.length());

                assert _txt == null;

                // place any text encountered in the buffer
                if (_cur.isText()) {
                    // _cur.isTextCData() is expensive do it only if useCDataBookmarks option is enabled
                    _isTextCData = _useCDataBookmarks && _cur.isTextCData();
                    CharUtil.getString(_sb, _cur.getChars(), _cur._offSrc, _cur._cchSrc);
                    _cur.next();
                    k = _cur.kind();
                    if (prevKind != ELEM || k != -ELEM) {
                        trim(_sb);
                    }
                }

                k = _cur.kind();

                // Check for non leaf, _prettyIndent < 0 means that the save is all on one line

                if (_prettyIndent >= 0 &&
                    prevKind != COMMENT && prevKind != PROCINST && (prevKind != ELEM || k != -ELEM)) {
                    if (_sb.length() > 0) {
                        _sb.insert(0, _newLine);
                        spaces(_sb, _newLine.length(), _prettyOffset + _prettyIndent * _depth);
                    }

                    if (k != -ROOT) {
                        if (prevKind != ROOT) {
                            _sb.append(_newLine);
                        }

                        int d = k < 0 ? _depth - 1 : _depth;
                        spaces(_sb, _sb.length(), _prettyOffset + _prettyIndent * d);
                    }
                }

                if (_sb.length() > 0) {
                    _txt = _sb.toString();
                    k = TEXT;
                }
            }

            if (k == ELEM) {
                _depth++;
            } else if (k == -ELEM) {
                _depth--;
            }

            return true;
        }

        void push() {
            _cur.push();
            _stack.add(_txt);
            _stack.add(_depth);
            _isTextCData = false;
        }

        void pop() {
            _cur.pop();
            _depth = (Integer) _stack.remove(_stack.size() - 1);
            _txt = (String) _stack.remove(_stack.size() - 1);
            _isTextCData = false;
        }

        Object getChars() {
            if (_txt != null) {
                _offSrc = 0;
                _cchSrc = _txt.length();
                return _txt;
            }

            Object o = _cur.getChars();

            _offSrc = _cur._offSrc;
            _cchSrc = _cur._cchSrc;

            return o;
        }

        XmlDocumentProperties getDocProps() {
            return _cur.getDocProps();
        }

        static void spaces(StringBuffer sb, int offset, int count) {
            while (count-- > 0) {
                sb.insert(offset, ' ');
            }
        }

        static void trim(StringBuffer sb) {
            int i;

            for (i = 0; i < sb.length(); i++) {
                if (!CharUtil.isWhiteSpace(sb.charAt(i))) {
                    break;
                }
            }

            sb.delete(0, i);

            for (i = sb.length(); i > 0; i--) {
                if (!CharUtil.isWhiteSpace(sb.charAt(i - 1))) {
                    break;
                }
            }

            sb.delete(i, sb.length());
        }

        private final SaveCur _cur;

        private int _prettyIndent;
        private int _prettyOffset;

        private String _txt;
        private final StringBuffer _sb;

        private int _depth;

        private final ArrayList<Object> _stack;
        private boolean _isTextCData = false;
        private final boolean _useCDataBookmarks;
    }
}
