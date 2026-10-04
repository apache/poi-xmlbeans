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

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlDocumentProperties;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.common.GlobalLock;
import org.apache.xmlbeans.impl.common.XMLChar;
import org.apache.xmlbeans.impl.store.Locale.ChangeListener;
import org.apache.xmlbeans.impl.store.Saver.TextSaver;
import org.apache.xmlbeans.impl.xpath.XPathEngine;
import org.apache.xmlbeans.impl.xpath.XPathFactory;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

public final class Cursor implements XmlCursor, ChangeListener {
    static final int ROOT = Cur.ROOT;
    static final int ELEM = Cur.ELEM;
    static final int ATTR = Cur.ATTR;
    static final int COMMENT = Cur.COMMENT;
    static final int PROCINST = Cur.PROCINST;
    static final int TEXT = Cur.TEXT;

    private Cur _cur;
    private XPathEngine _pathEngine;
    private int _currentSelection;

    private ChangeListener _nextChangeListener;

    Cursor(Xobj x, int p) {
        _cur = x._locale.weakCur(this);
        _cur.moveTo(x, p);
        _currentSelection = -1;
    }

    public Cursor(Cur c) {
        this(c._xobj, c._pos);
    }

    private static boolean isValid(Cur c) {
        if (c.kind() <= 0) {
            c.push();

            if (c.toParentRaw()) {
                int pk = c.kind();

                if (pk == COMMENT || pk == PROCINST || pk == ATTR) {
                    return false;
                }
            }

            c.pop();
        }

        return true;
    }

    private boolean isValid() {
        return isValid(_cur);
    }

    Locale locale() {
        return _cur._locale;
    }

    Cur tempCur() {
        return _cur.tempCur();
    }

    public void dump(PrintStream o) {
        _cur.dump(o);
    }

    static void validateLocalName(QName name) {
        if (name == null) {
            throw new IllegalArgumentException("QName is null");
        }

        validateLocalName(name.getLocalPart());
    }

    static void validateLocalName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name is null");
        }

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name is empty");
        }

        if (!XMLChar.isValidNCName(name)) {
            throw new IllegalArgumentException("Name is not valid");
        }
    }

    static void validatePrefix(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Prefix is null");
        }

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Prefix is empty");
        }

        if (Locale.beginsWithXml(name)) {
            throw new IllegalArgumentException("Prefix begins with 'xml'");
        }

        if (!XMLChar.isValidNCName(name)) {
            throw new IllegalArgumentException("Prefix is not valid");
        }
    }

    private static void complain(String msg) {
        throw new IllegalArgumentException(msg);
    }

    private void checkInsertionValidity(Cur that) {
        int thatKind = that.kind();

        if (thatKind < 0) {
            complain("Can't move/copy/insert an end token.");
        }

        if (thatKind == ROOT) {
            complain("Can't move/copy/insert a whole document.");
        }

        int thisKind = _cur.kind();

        if (thisKind == ROOT) {
            complain("Can't insert before the start of the document.");
        }

        if (thatKind == ATTR) {
            _cur.push();
            _cur.prevWithAttrs();
            int pk = _cur.kind();
            _cur.pop();

            if (pk != ELEM && pk != ROOT && pk != -ATTR) {
                complain("Can only insert attributes before other attributes or after containers.");
            }
        }

        if (thisKind == ATTR && thatKind != ATTR) {
            complain("Can only insert attributes before other attributes or after containers.");
        }
    }

    private void insertNode(Cur that, String text) {
        assert !that.isRoot();
        assert that.isNode();
        assert isValid(that);
        assert isValid();

        if (text != null && !text.isEmpty()) {
            that.next();
            that.insertString(text);
            that.toParent();
        }

        checkInsertionValidity(that);

        that.moveNode(_cur);

        _cur.toEnd();
        _cur.nextWithAttrs();
    }

    //
    //
    //

    // TODO - deal with cursors moving to other documents upon release?
    // Can I move the ref from one q to another?  If not I will have to
    // change from a phantom ref to a soft/weak ref so I can know what
    // to do when I dequeue from the old q.

    public void _dispose() {
        _cur.release();
        _cur = null;
    }

    public XmlCursor _newCursor() {
        return new Cursor(_cur);
    }

    public QName _getName() {
        // TODO - consider taking this out of the gateway

        switch (_cur.kind()) {
            case ATTR:

                if (_cur.isXmlns()) {
                    return
                        _cur._locale.makeQNameNoCheck(_cur.getXmlnsUri(), _cur.getXmlnsPrefix());
                }

                // Fall thru

            case ELEM:
            case PROCINST:
                return _cur.getName();
        }

        return null;
    }

    public void _setName(QName name) {
        if (name == null) {
            throw new IllegalArgumentException("Name is null");
        }

        switch (_cur.kind()) {
            case ELEM:
            case ATTR: {
                validateLocalName(name.getLocalPart());
                break;
            }

            case PROCINST: {
                validatePrefix(name.getLocalPart());

                if (!name.getNamespaceURI().isEmpty()) {
                    throw new IllegalArgumentException("Procinst name must have no URI");
                }

                if (!name.getPrefix().isEmpty()) {
                    throw new IllegalArgumentException("Procinst name must have no prefix");
                }

                break;
            }

            default:
                throw
                    new IllegalStateException("Can set name on element, atrtribute and procinst only");
        }

        _cur.setName(name);
    }

    public TokenType _currentTokenType() {
        assert isValid();

        switch (_cur.kind()) {
            case ROOT:
                return TokenType.STARTDOC;
            case -ROOT:
                return TokenType.ENDDOC;
            case ELEM:
                return TokenType.START;
            case -ELEM:
                return TokenType.END;
            case TEXT:
                return TokenType.TEXT;
            case ATTR:
                return _cur.isXmlns() ? TokenType.NAMESPACE : TokenType.ATTR;
            case COMMENT:
                return TokenType.COMMENT;
            case PROCINST:
                return TokenType.PROCINST;

            default:
                throw new IllegalStateException();
        }
    }

    public boolean _isStartdoc() {
        //return _currentTokenType().isStartdoc();
        assert isValid();
        return _cur.isRoot();
    }

    public boolean _isEnddoc() {
        //return _currentTokenType().isEnddoc();
        assert isValid();
        return _cur.isEndRoot();
    }

    public boolean _isStart() {
        //return _currentTokenType().isStart();
        assert isValid();
        return _cur.isElem();
    }

    public boolean _isEnd() {
        //return _currentTokenType().isEnd();
        assert isValid();
        return _cur.isEnd();
    }

    public boolean _isText() {
        //return _currentTokenType().isText();
        assert isValid();
        return _cur.isText();
    }

    public boolean _isAttr() {
        //return _currentTokenType().isAttr();
        assert isValid();
        return _cur.isNormalAttr();
    }

    public boolean _isNamespace() {
        //return _currentTokenType().isNamespace();
        assert isValid();
        return _cur.isXmlns();
    }

    public boolean _isComment() {
        //return _currentTokenType().isComment();
        assert isValid();
        return _cur.isComment();
    }

    public boolean _isProcinst() {
        //return _currentTokenType().isProcinst();
        assert isValid();
        return _cur.isProcinst();
    }

    public boolean _isContainer() {
        //return _currentTokenType().isContainer();
        assert isValid();
        return _cur.isContainer();
    }

    public boolean _isFinish() {
        //return _currentTokenType().isFinish();
        assert isValid();
        return _cur.isFinish();
    }

    public boolean _isAnyAttr() {
        //return _currentTokenType().isAnyAttr();
        assert isValid();
        return _cur.isAttr();
    }

    public TokenType _toNextToken() {
        assert isValid();

        switch (_cur.kind()) {
            case ROOT:
            case ELEM: {
                if (!_cur.toFirstAttr()) {
                    _cur.next();
                }

                break;
            }

            case ATTR: {
                if (!_cur.toNextSibling()) {
                    _cur.toParent();
                    _cur.next();
                }

                break;
            }

            case COMMENT:
            case PROCINST: {
                _cur.skip();
                break;
            }

            default: {
                if (!_cur.next()) {
                    return TokenType.NONE;
                }

                break;
            }
        }

        return _currentTokenType();
    }

    public TokenType _toPrevToken() {
        assert isValid();

        // This method is different than the Cur version of prev in a few ways.  First,
        // Cursor iterates over attrs inline with all the other content.  Cur will skip attrs, or
        // if the Cur in in attrs, it will not jump out of attrs.  Also, if moving backwards and
        // text is to the left and right, Cur will move to the beginning of that text, while
        // Cursor will move further so that the token type to the right is not text.

        boolean wasText = _cur.isText();

        if (!_cur.prev()) {
            assert _cur.isRoot() || _cur.isAttr();

            if (_cur.isRoot()) {
                return TokenType.NONE;
            }

            _cur.toParent();
        } else {
            int k = _cur.kind();

            if (k == -COMMENT || k == -PROCINST || k == -ATTR) {
                _cur.toParent();
            } else if (_cur.isContainer()) {
                _cur.toLastAttr();
            } else if (wasText && _cur.isText()) {
                return _toPrevToken();
            }
        }

        return _currentTokenType();
    }

    public Object _monitor() {
        // TODO - some of these methods need not be protected by a
        //  gatway.  This is one of them.  Inline this.

        return _cur._locale;
    }

    public boolean _toParent() {
        Cur c = _cur.tempCur();

        if (!c.toParent()) {
            return false;
        }

        _cur.moveToCur(c);

        c.release();

        return true;
    }

    private static final class ChangeStampImpl implements ChangeStamp {
        ChangeStampImpl(Locale l) {
            _locale = l;
            _versionStamp = _locale.version();
        }

        public boolean hasChanged() {
            return _versionStamp != _locale.version();
        }

        private final Locale _locale;
        private final long _versionStamp;
    }

    public ChangeStamp _getDocChangeStamp() {
        return new ChangeStampImpl(_cur._locale);
    }

    //
    // These simply delegate to the version of the method which takes XmlOptions
    //


    public XMLStreamReader _newXMLStreamReader() {
        return _newXMLStreamReader(null);
    }

    public Node _newDomNode() {
        return _newDomNode(null);
    }

    public InputStream _newInputStream() {
        return _newInputStream(null);
    }

    public String _xmlText() {
        return _xmlText(null);
    }

    public Reader _newReader() {
        return _newReader(null);
    }

    public void _save(File file) throws IOException {
        _save(file, null);
    }

    public void _save(OutputStream os) throws IOException {
        _save(os, null);
    }

    public void _save(Writer w) throws IOException {
        _save(w, null);
    }

    public void _save(ContentHandler ch, LexicalHandler lh) throws SAXException {
        _save(ch, lh, null);
    }

    //
    //
    //

    public XmlDocumentProperties _documentProperties() {
        return Locale.getDocProps(_cur, true);
    }

    public XMLStreamReader _newXMLStreamReader(XmlOptions options) {
        return Jsr173.newXmlStreamReader(_cur, options);
    }

    public String _xmlText(XmlOptions options) {
        assert isValid();

        return new TextSaver(_cur, options, null).saveToString();
    }

    public InputStream _newInputStream(XmlOptions options) {
        return new Saver.InputStreamSaver(_cur, options);
    }

    public Reader _newReader(XmlOptions options) {
        return new Saver.TextReader(_cur, options);
    }

    public void _save(ContentHandler ch, LexicalHandler lh, XmlOptions options)
        throws SAXException {
        new Saver.SaxSaver(_cur, options, ch, lh);
    }

    public void _save(File file, XmlOptions options) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Null file specified");
        }

        try (OutputStream os = Files.newOutputStream(file.toPath())) {
            _save(os, options);
        }
    }

    public void _save(OutputStream os, XmlOptions options) throws IOException {
        if (os == null) {
            throw new IllegalArgumentException("Null OutputStream specified");
        }

        try (InputStream is = _newInputStream(options)) {
            byte[] bytes = new byte[8192];

            for (; ; ) {
                int n = is.read(bytes);

                if (n < 0) {
                    break;
                }

                os.write(bytes, 0, n);
            }
        }
    }

    public void _save(Writer w, XmlOptions options) throws IOException {
        if (w == null) {
            throw new IllegalArgumentException("Null Writer specified");
        }

        if (options != null && options.isSaveOptimizeForSpeed()) {
            Saver.OptimizedForSpeedSaver.save(_cur, w, options); //ignore all other options bar attribute whitespace escaping
            return;
        }

        try (Reader r = _newReader(options)) {
            char[] chars = new char[8192];

            for (; ; ) {
                int n = r.read(chars);

                if (n < 0) {
                    break;
                }

                w.write(chars, 0, n);
            }
        }
    }

    public Node _getDomNode() {
        return (Node) _cur.getDom();
    }

    private boolean isDomFragment() {
        if (!isStartdoc()) {
            return true;
        }

        boolean seenElement = false;

        try (XmlCursor c = newCursor()) {
            int token = c.toNextToken().intValue();

            LOOP:
            for (; ; ) {
                switch (token) {
                    case TokenType.INT_START:
                        if (seenElement) {
                            return true;
                        }
                        seenElement = true;
                        token = c.toEndToken().intValue();
                        break;

                    case TokenType.INT_TEXT:
                        if (!Locale.isWhiteSpace(c.getChars())) {
                            return true;
                        }
                        token = c.toNextToken().intValue();
                        break;

                    case TokenType.INT_NONE:
                    case TokenType.INT_ENDDOC:
                        break LOOP;

                    case TokenType.INT_ATTR:
                    case TokenType.INT_NAMESPACE:
                        return true;

                    case TokenType.INT_END:
                    case TokenType.INT_COMMENT:
                    case TokenType.INT_PROCINST:
                        token = c.toNextToken().intValue();
                        break;

                    case TokenType.INT_STARTDOC:
                        assert false;
                        break LOOP;
                }
            }
        }

        return !seenElement;
    }

    public Node _newDomNode(XmlOptions options) {
        // Must ignore inner options for compat with v1.

        if (options != null && options.isSaveInner()) {
            options = new XmlOptions(options);
            options.setSaveInner(false);
        }

        return new DomSaver(_cur, isDomFragment(), options).saveDom();
    }

    public boolean _toCursor(Cursor other) {
        assert _cur._locale == other._cur._locale;

        _cur.moveToCur(other._cur);

        return true;
    }

    public void _push() {
        _cur.push();
    }

    public boolean _pop() {
        return _cur.pop();
    }

    public void notifyChange() {
        // Force any path to get exausted, cursor may be disposed, but still be on the notification
        // list.

        if (_cur != null) {
            _getSelectionCount();
        }
    }

    public void setNextChangeListener(ChangeListener listener) {
        _nextChangeListener = listener;
    }

    public ChangeListener getNextChangeListener() {
        return _nextChangeListener;
    }

    public void _selectPath(String path) {
        _selectPath(path, null);
    }

    public void _selectPath(String pathExpr, XmlOptions options) {
        _clearSelections();

        assert _pathEngine == null;

        _pathEngine = XPathFactory.getCompiledPath(pathExpr, options).execute(_cur, options);

        _cur._locale.registerForChange(this);
    }

    public boolean _hasNextSelection() {
        int curr = _currentSelection;
        push();

        try {
            return _toNextSelection();
        } finally {
            _currentSelection = curr;
            pop();
        }
    }

    public boolean _toNextSelection() {
        return _toSelection(_currentSelection + 1);
    }

    public boolean _toSelection(int i) {
        if (i < 0) {
            return false;
        }

        while (i >= _cur.selectionCount()) {
            if (_pathEngine == null) {
                return false;
            }

            if (!_pathEngine.next(_cur)) {
                _pathEngine.release();
                _pathEngine = null;

                return false;
            }
        }

        _cur.moveToSelection(_currentSelection = i);

        return true;
    }

    public int _getSelectionCount() {
        // Should never get to MAX_VALUE selection index, so, state should not change
        _toSelection(Integer.MAX_VALUE);

        return _cur.selectionCount();
    }

    public void _addToSelection() {
        _toSelection(Integer.MAX_VALUE);

        _cur.addToSelection();
    }

    public void _clearSelections() {
        if (_pathEngine != null) {
            _pathEngine.release();
            _pathEngine = null;
        }

        _cur.clearSelection();

        _currentSelection = -1;
    }

    public String _namespaceForPrefix(String prefix) {
        if (!_cur.isContainer()) {
            throw new IllegalStateException("Not on a container");
        }

        return _cur.namespaceForPrefix(prefix, true);
    }

    public String _prefixForNamespace(String ns) {
        if (ns == null || ns.isEmpty()) {
            throw new IllegalArgumentException("Must specify a namespace");
        }

// Note: I loosen this requirement in v2, can call this from anywhere
//        if (!_cur.isContainer())
//            throw new IllegalStateException( "Not on a container" );

        return _cur.prefixForNamespace(ns, null, true);
    }

    public void _getAllNamespaces(Map<String, String> addToThis) {
        if (!_cur.isContainer()) {
            throw new IllegalStateException("Not on a container");
        }

        if (addToThis != null) {
            Locale.getAllNamespaces(_cur, addToThis);
        }
    }

    public XmlObject _getObject() {
        return _cur.getObject();
    }

    public TokenType _prevTokenType() {
        _cur.push();

        TokenType tt = _toPrevToken();

        _cur.pop();

        return tt;
    }

    public boolean _hasNextToken() {
        //return _cur.kind() != -ROOT;
        return _cur._pos != Cur.END_POS || _cur._xobj.kind() != ROOT;
    }

    public boolean _hasPrevToken() {
        return _cur.kind() != ROOT;
    }

    public TokenType _toFirstContentToken() {
        if (!_cur.isContainer()) {
            return TokenType.NONE;
        }

        _cur.next();

        return currentTokenType();
    }

    public TokenType _toEndToken() {
        if (!_cur.isContainer()) {
            return TokenType.NONE;
        }

        _cur.toEnd();

        return currentTokenType();
    }

    public boolean _toChild(String local) {
        return _toChild(null, local);
    }

    public boolean _toChild(QName name) {
        return _toChild(name, 0);
    }

    public boolean _toChild(int index) {
        return _toChild(null, index);
    }

    public boolean _toChild(String uri, String local) {
        validateLocalName(local);

        return _toChild(_cur._locale.makeQName(uri, local), 0);
    }

    public boolean _toChild(QName name, int index) {
        return Locale.toChild(_cur, name, index);
    }

    public int _toNextChar(int maxCharacterCount) {
        return _cur.nextChars(maxCharacterCount);
    }

    public int _toPrevChar(int maxCharacterCount) {
        return _cur.prevChars(maxCharacterCount);
    }

    public boolean _toPrevSibling() {
        return Locale.toPrevSiblingElement(_cur);
    }

    public boolean _toLastChild() {
        return Locale.toLastChildElement(_cur);
    }

    public boolean _toFirstChild() {
        return Locale.toFirstChildElement(_cur);
    }

    public boolean _toNextSibling(String name) {
        return _toNextSibling(new QName(name));
    }

    public boolean _toNextSibling(String uri, String local) {
        validateLocalName(local);

        return _toNextSibling(_cur._locale._qnameFactory.getQName(uri, local));
    }

    public boolean _toNextSibling(QName name) {
        _cur.push();

        while (___toNextSibling()) {
            if (_cur.getName().equals(name)) {
                _cur.popButStay();
                return true;
            }
        }

        _cur.pop();

        return false;
    }

    public boolean _toFirstAttribute() {
        return _cur.isContainer() && Locale.toFirstNormalAttr(_cur);
    }

    public boolean _toLastAttribute() {
        if (_cur.isContainer()) {
            _cur.push();
            _cur.push();

            boolean foundAttr = false;

            while (_cur.toNextAttr()) {
                if (_cur.isNormalAttr()) {
                    _cur.popButStay();
                    _cur.push();
                    foundAttr = true;
                }
            }

            _cur.pop();

            if (foundAttr) {
                _cur.popButStay();
                return true;
            }

            _cur.pop();
        }

        return false;
    }

    public boolean _toNextAttribute() {
        return _cur.isAttr() && Locale.toNextNormalAttr(_cur);
    }

    public boolean _toPrevAttribute() {
        return _cur.isAttr() && Locale.toPrevNormalAttr(_cur);
    }

    public String _getAttributeText(QName attrName) {
        if (attrName == null) {
            throw new IllegalArgumentException("Attr name is null");
        }

        if (!_cur.isContainer()) {
            return null;
        }

        return _cur.getAttrValue(attrName);
    }

    public boolean _setAttributeText(QName attrName, String value) {
        if (attrName == null) {
            throw new IllegalArgumentException("Attr name is null");
        }

        validateLocalName(attrName.getLocalPart());

        if (!_cur.isContainer()) {
            return false;
        }

        _cur.setAttrValue(attrName, value);

        return true;
    }

    public boolean _removeAttribute(QName attrName) {
        if (attrName == null) {
            throw new IllegalArgumentException("Attr name is null");
        }

        if (!_cur.isContainer()) {
            return false;
        }

        return _cur.removeAttr(attrName);
    }

    public String _getTextValue() {
        if (_cur.isText()) {
            return _getChars();
        }

        if (!_cur.isNode()) {
            throw new IllegalStateException("Can't get text value, current token can have no text value");
        }

        return _cur.hasChildren() ? Locale.getTextValue(_cur) : _cur.getValueAsString();
    }

    public int _getTextValue(char[] chars, int offset, int max) {
        if (_cur.isText()) {
            return _getChars(chars, offset, max);
        }

        if (chars == null) {
            throw new IllegalArgumentException("char buffer is null");
        }

        if (offset < 0) {
            throw new IllegalArgumentException("offset < 0");
        }

        if (offset >= chars.length) {
            throw new IllegalArgumentException("offset off end");
        }

        if (max < 0) {
            max = Integer.MAX_VALUE;
        }

        if (offset + max > chars.length) {
            max = chars.length - offset;
        }

        if (!_cur.isNode()) {
            throw new IllegalStateException("Can't get text value, current token can have no text value");
        }

        // If there are no children (hopefully the common case), I can get the text faster.

        if (_cur.hasChildren()) {
            return Locale.getTextValue(_cur, chars, offset, max);
        }

        // Fast way

        Object src = _cur.getFirstChars();

        if (_cur._cchSrc > max) {
            _cur._cchSrc = max;
        }

        if (_cur._cchSrc <= 0) {
            return 0;
        }

        CharUtil.getChars(chars, offset, src, _cur._offSrc, _cur._cchSrc);

        return _cur._cchSrc;
    }

    private void setTextValue(Object src, int off, int cch) {
        if (!_cur.isNode()) {
            throw new IllegalStateException("Can't set text value, current token can have no text value");
        }

        _cur.moveNodeContents(null, false);
        _cur.next();
        _cur.insertChars(src, off, cch);
        _cur.toParent();
    }

    public void _setTextValue(String text) {
        if (text == null) {
            text = "";
        }

        setTextValue(text, 0, text.length());
    }

    public void _setTextValue(char[] sourceChars, int offset, int length) {
        if (length < 0) {
            throw new IndexOutOfBoundsException("setTextValue: length < 0");
        }

        if (sourceChars == null) {
            if (length > 0) {
                throw new IllegalArgumentException("setTextValue: sourceChars == null");
            }

            setTextValue(null, 0, 0);

            return;
        }

        if (offset < 0 || offset >= sourceChars.length) {
            throw new IndexOutOfBoundsException("setTextValue: offset out of bounds");
        }

        if (offset + length > sourceChars.length) {
            length = sourceChars.length - offset;
        }

        CharUtil cu = _cur._locale.getCharUtil();

        setTextValue(cu.saveChars(sourceChars, offset, length), cu._offSrc, cu._cchSrc);
    }

    public String _getChars() {
        return _cur.getCharsAsString();
    }

    public int _getChars(char[] buf, int off, int cch) {
        int cchRight = _cur.cchRight();

        if (cch < 0 || cch > cchRight) {
            cch = cchRight;
        }

        if (buf == null || off >= buf.length) {
            return 0;
        }

        if (buf.length - off < cch) {
            cch = buf.length - off;
        }

        Object src = _cur.getChars(cch);

        CharUtil.getChars(buf, off, src, _cur._offSrc, _cur._cchSrc);

        return _cur._cchSrc;
    }

    public void _toStartDoc() {
        _cur.toRoot();
    }

    public void _toEndDoc() {
        _toStartDoc();
        _cur.toEnd();
    }

    public int _comparePosition(Cursor other) {
        int s = _cur.comparePosition(other._cur);

        if (s == 2) {
            throw new IllegalArgumentException("Cursors not in same document");
        }

        assert s >= -1 && s <= 1;

        return s;
    }

    public boolean _isLeftOf(Cursor other) {
        return _comparePosition(other) < 0;
    }

    public boolean _isAtSamePositionAs(Cursor other) {
        return _cur.isSamePos(other._cur);
    }

    public boolean _isRightOf(Cursor other) {
        return _comparePosition(other) > 0;
    }

    public XmlCursor _execQuery(String query) {
        return _execQuery(query, null);
    }

    public XmlCursor _execQuery(String query, XmlOptions options) {
        checkThisCursor();
        return XPathFactory.cursorExecQuery(_cur, query, options);
    }


    public boolean _toBookmark(XmlBookmark bookmark) {
        if (bookmark == null || !(bookmark._currentMark instanceof Bookmark)) {
            return false;
        }

        Bookmark m = (Bookmark) bookmark._currentMark;

        if (m._xobj == null || m._xobj._locale != _cur._locale) {
            return false;
        }

        _cur.moveTo(m._xobj, m._pos);

        return true;
    }

    public XmlBookmark _toNextBookmark(Object key) {
        if (key == null) {
            return null;
        }

        int cch;

        _cur.push();

        for (; ; ) {
            // Move a minimal amount.  If at text, move to a potential bookmark in the text.

            if ((cch = _cur.cchRight()) > 1) {
                _cur.nextChars(1);
                _cur.nextChars((cch = _cur.firstBookmarkInChars(key, cch - 1)) >= 0 ? cch : -1);
            } else if (_toNextToken().isNone()) {
                _cur.pop();
                return null;
            }

            XmlBookmark bm = getBookmark(key, _cur);

            if (bm != null) {
                _cur.popButStay();
                return bm;
            }

            if (_cur.kind() == -ROOT) {
                _cur.pop();
                return null;
            }
        }
    }

    public XmlBookmark _toPrevBookmark(Object key) {
        if (key == null) {
            return null;
        }

        int cch;

        _cur.push();

        for (; ; ) {
            // Move a minimal amount.  If at text, move to a potential bookmark in the text.

            if ((cch = _cur.cchLeft()) > 1) {
                _cur.prevChars(1);

                _cur.prevChars((cch = _cur.firstBookmarkInCharsLeft(key, cch - 1)) >= 0 ? cch : -1);
            } else if (cch == 1) {
                // _toPrevToken will not skip to the beginning of the text, it will go further
                // so that the token to the right is not text.  I need to simply skip to
                // the beginning of the text ...

                _cur.prevChars(1);
            } else if (_toPrevToken().isNone()) {
                _cur.pop();
                return null;
            }

            XmlBookmark bm = getBookmark(key, _cur);

            if (bm != null) {
                _cur.popButStay();
                return bm;
            }

            if (_cur.kind() == ROOT) {
                _cur.pop();
                return null;
            }
        }
    }

    public void _setBookmark(XmlBookmark bookmark) {
        if (bookmark != null) {
            if (bookmark.getKey() == null) {
                throw new IllegalArgumentException("Annotation key is null");
            }

            // TODO - I Don't do weak bookmarks yet ... perhaps I'll never do them ....

            bookmark._currentMark = _cur.setBookmark(bookmark.getKey(), bookmark);
        }
    }

    static XmlBookmark getBookmark(Object key, Cur c) {
        // TODO - I Don't do weak bookmarks yet ...

        if (key == null) {
            return null;
        }

        Object bm = c.getBookmark(key);

        return bm instanceof XmlBookmark xb ? xb : null;
    }

    public XmlBookmark _getBookmark(Object key) {
        return key == null ? null : getBookmark(key, _cur);
    }

    public void _clearBookmark(Object key) {
        if (key != null) {
            _cur.setBookmark(key, null);
        }
    }

    public void _getAllBookmarkRefs(Collection<Object> listToFill) {
        if (listToFill != null) {
            for (Bookmark b = _cur._xobj._bookmarks; b != null; b = b._next) {
                if (b._value instanceof XmlBookmark) {
                    listToFill.add(b._value);
                }
            }
        }
    }

    public boolean _removeXml() {
        if (_cur.isRoot()) {
            throw new IllegalStateException("Can't remove a whole document.");
        }

        if (_cur.isFinish()) {
            return false;
        }

        assert _cur.isText() || _cur.isNode();

        if (_cur.isText()) {
            _cur.moveChars(null, -1);
        } else {
            _cur.moveNode(null);
        }

        return true;
    }

    public boolean _moveXml(Cursor to) {
        to.checkInsertionValidity(_cur);

        // Check for a no-op

        if (_cur.isText()) {
            int cchRight = _cur.cchRight();

            assert cchRight > 0;

            if (_cur.inChars(to._cur, cchRight, true)) {
                return false;
            }

            _cur.moveChars(to._cur, cchRight);

            to._cur.nextChars(cchRight);

            return true;
        }

        if (_cur.contains(to._cur)) {
            return false;
        }

        // Make a cur which will float to the right of the insertion

        Cur c = to.tempCur();

        _cur.moveNode(to._cur);

        to._cur.moveToCur(c);

        c.release();

        return true;
    }

    public boolean _copyXml(Cursor to) {
        to.checkInsertionValidity(_cur);

        assert _cur.isText() || _cur.isNode();

        Cur c = to.tempCur();

        if (_cur.isText()) {
            to._cur.insertChars(_cur.getChars(-1), _cur._offSrc, _cur._cchSrc);
        } else {
            _cur.copyNode(to._cur);
        }

        to._cur.moveToCur(c);

        c.release();

        return true;
    }

    public boolean _removeXmlContents() {
        if (!_cur.isContainer()) {
            return false;
        }

        _cur.moveNodeContents(null, false);

        return true;
    }

    private boolean checkContentInsertionValidity(Cursor to) {
        _cur.push();

        _cur.next();

        if (_cur.isFinish()) {
            _cur.pop();
            return false;
        }

        try {
            to.checkInsertionValidity(_cur);
        } catch (IllegalArgumentException e) {
            _cur.pop();
            throw e;
        }

        _cur.pop();

        return true;
    }

    public boolean _moveXmlContents(Cursor to) {
        if (!_cur.isContainer() || _cur.contains(to._cur)) {
            return false;
        }

        if (!checkContentInsertionValidity(to)) {
            return false;
        }

        Cur c = to.tempCur();

        _cur.moveNodeContents(to._cur, false);

        to._cur.moveToCur(c);

        c.release();

        return true;
    }

    public boolean _copyXmlContents(Cursor to) {
        if (!_cur.isContainer() || _cur.contains(to._cur)) {
            return false;
        }

        if (!checkContentInsertionValidity(to)) {
            return false;
        }

        // I don't have a primitive to copy contents, make a copy of the node and them move the
        // contents

        Cur c = _cur._locale.tempCur();

        _cur.copyNode(c);

        Cur c2 = to._cur.tempCur();

        c.moveNodeContents(to._cur, false);

        c.release();

        to._cur.moveToCur(c2);

        c2.release();

        return true;
    }

    public int _removeChars(int cch) {
        int cchRight = _cur.cchRight();

        if (cchRight == 0 || cch == 0) {
            return 0;
        }

        if (cch < 0 || cch > cchRight) {
            cch = cchRight;
        }

        _cur.moveChars(null, cch);

        return _cur._cchSrc;
    }

    public int _moveChars(int cch, Cursor to) {
        int cchRight = _cur.cchRight();

        if (cchRight <= 0 || cch == 0) {
            return 0;
        }

        if (cch < 0 || cch > cchRight) {
            cch = cchRight;
        }

        to.checkInsertionValidity(_cur);

        _cur.moveChars(to._cur, cch);

        to._cur.nextChars(_cur._cchSrc);

        return _cur._cchSrc;
    }

    public int _copyChars(int cch, Cursor to) {
        int cchRight = _cur.cchRight();

        if (cchRight <= 0 || cch == 0) {
            return 0;
        }

        if (cch < 0 || cch > cchRight) {
            cch = cchRight;
        }

        to.checkInsertionValidity(_cur);

        to._cur.insertChars(_cur.getChars(cch), _cur._offSrc, _cur._cchSrc);

        to._cur.nextChars(_cur._cchSrc);

        return _cur._cchSrc;
    }

    public void _insertChars(String text) {
        int l = text == null ? 0 : text.length();

        if (l > 0) {
            if (_cur.isRoot() || _cur.isAttr()) {
                throw
                    new IllegalStateException("Can't insert before the document or an attribute.");
            }

            _cur.insertChars(text, 0, l);
            _cur.nextChars(l);
        }
    }

    //
    // Inserting elements
    //

    public void _beginElement(String localName) {
        _insertElementWithText(localName, null, null);
        _toPrevToken();
    }

    public void _beginElement(String localName, String uri) {
        _insertElementWithText(localName, uri, null);
        _toPrevToken();
    }

    public void _beginElement(QName name) {
        _insertElementWithText(name, null);
        _toPrevToken();
    }

    public void _insertElement(String localName) {
        _insertElementWithText(localName, null, null);
    }

    public void _insertElement(String localName, String uri) {
        _insertElementWithText(localName, uri, null);
    }

    public void _insertElement(QName name) {
        _insertElementWithText(name, null);
    }

    public void _insertElementWithText(String localName, String text) {
        _insertElementWithText(localName, null, text);
    }

    public void _insertElementWithText(String localName, String uri, String text) {
        validateLocalName(localName);

        _insertElementWithText(_cur._locale.makeQName(uri, localName), text);
    }

    public void _insertElementWithText(QName name, String text) {
        validateLocalName(name.getLocalPart());

        Cur c = _cur._locale.tempCur();

        c.createElement(name);

        insertNode(c, text);

        c.release();
    }

    //
    //
    //

    public void _insertAttribute(String localName) {
        _insertAttributeWithValue(localName, null);
    }

    public void _insertAttribute(String localName, String uri) {
        _insertAttributeWithValue(localName, uri, null);
    }

    public void _insertAttribute(QName name) {
        _insertAttributeWithValue(name, null);
    }

    public void _insertAttributeWithValue(String localName, String value) {
        _insertAttributeWithValue(localName, null, value);
    }

    public void _insertAttributeWithValue(String localName, String uri, String value) {
        validateLocalName(localName);

        _insertAttributeWithValue(_cur._locale.makeQName(uri, localName), value);
    }

    public void _insertAttributeWithValue(QName name, String text) {
        if (name == null) {
            throw new IllegalArgumentException("QName must not be null");
        }

        validateLocalName(name.getLocalPart());

        Cur c = _cur._locale.tempCur();

        c.createAttr(name);

        insertNode(c, text);

        c.release();
    }

    //
    //
    //

    public void _insertNamespace(String prefix, String namespace) {
        _insertAttributeWithValue(_cur._locale.createXmlns(prefix), namespace);
    }

    public void _insertComment(String text) {
        Cur c = _cur._locale.tempCur();

        c.createComment();

        insertNode(c, text);

        c.release();
    }

    public void _insertProcInst(String target, String text) {
        validateLocalName(target);

        if (Locale.beginsWithXml(target) && target.length() == 3) {
            throw new IllegalArgumentException("Target is 'xml'");
        }

        Cur c = _cur._locale.tempCur();

        c.createProcinst(target);

        insertNode(c, text);

        c.release();
    }

    public void _dump() {
        _cur.dump();
    }

    //
    //
    //
    //
    //
    //
    //

    private void checkThisCursor() {
        if (_cur == null) {
            throw new IllegalStateException("This cursor has been disposed");
        }
    }

    private Cursor checkCursors(XmlCursor xOther) {
        checkThisCursor();

        if (xOther == null) {
            throw new IllegalArgumentException("Other cursor is <null>");
        }

        if (!(xOther instanceof Cursor)) {
            throw new IllegalArgumentException("Incompatible cursors: " + xOther);
        }

        Cursor other = (Cursor) xOther;

        if (other._cur == null) {
            throw new IllegalStateException("Other cursor has been disposed");
        }

        return other;
    }

    //
    // The following operations have two cursors, and can be in different documents
    //

    private static final int MOVE_XML = 0;
    private static final int COPY_XML = 1;
    private static final int MOVE_XML_CONTENTS = 2;
    private static final int COPY_XML_CONTENTS = 3;
    private static final int MOVE_CHARS = 4;
    private static final int COPY_CHARS = 5;

    private int twoLocaleOp(XmlCursor xOther, int op, int arg) {
        Cursor other = checkCursors(xOther);

        Locale locale = _cur._locale;
        Locale otherLocale = other._cur._locale;

        if (locale == otherLocale) {
            return syncWrapNoEnter(() -> twoLocaleOp(other, op, arg));
        }

        if (locale.noSync()) {
            if (otherLocale.noSync()) {
                return twoLocaleOp(other, op, arg);
            } else {
                synchronized (otherLocale) {
                    return twoLocaleOp(other, op, arg);
                }
            }
        } else if (otherLocale.noSync()) {
            synchronized (locale) {
                return twoLocaleOp(other, op, arg);
            }
        }

        boolean acquired = false;

        try {
            GlobalLock.acquire();
            acquired = true;

            synchronized (locale) {
                synchronized (otherLocale) {
                    GlobalLock.release();
                    acquired = false;

                    return twoLocaleOp(other, op, arg);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (acquired) {
                GlobalLock.release();
            }
        }
    }

    private int twoLocaleOp(Cursor other, int op, int arg) {
        Locale locale = _cur._locale;
        Locale otherLocale = other._cur._locale;

        locale.enter(otherLocale);

        try {
            switch (op) {
                case MOVE_XML:
                    return _moveXml(other) ? 1 : 0;
                case COPY_XML:
                    return _copyXml(other) ? 1 : 0;
                case MOVE_XML_CONTENTS:
                    return _moveXmlContents(other) ? 1 : 0;
                case COPY_XML_CONTENTS:
                    return _copyXmlContents(other) ? 1 : 0;
                case MOVE_CHARS:
                    return _moveChars(arg, other);
                case COPY_CHARS:
                    return _copyChars(arg, other);

                default:
                    throw new RuntimeException("Unknown operation: " + op);
            }
        } finally {
            locale.exit(otherLocale);
        }
    }

    public boolean moveXml(XmlCursor xTo) {
        return twoLocaleOp(xTo, MOVE_XML, 0) == 1;
    }

    public boolean copyXml(XmlCursor xTo) {
        return twoLocaleOp(xTo, COPY_XML, 0) == 1;
    }

    public boolean moveXmlContents(XmlCursor xTo) {
        return twoLocaleOp(xTo, MOVE_XML_CONTENTS, 0) == 1;
    }

    public boolean copyXmlContents(XmlCursor xTo) {
        return twoLocaleOp(xTo, COPY_XML_CONTENTS, 0) == 1;
    }

    public int moveChars(int cch, XmlCursor xTo) {
        return twoLocaleOp(xTo, MOVE_CHARS, cch);
    }

    public int copyChars(int cch, XmlCursor xTo) {
        return twoLocaleOp(xTo, COPY_CHARS, cch);
    }


    //
    // Special methods involving multiple cursors which can be in different locales, but do not
    // require sync on both locales.
    //

    public boolean toCursor(XmlCursor xOther) {
        // One may only move cursors within the same locale

        Cursor other = checkCursors(xOther);

        return _cur._locale == other._cur._locale &&
               syncWrap(() -> _toCursor(other));
    }

    public boolean isInSameDocument(XmlCursor xOther) {
        return xOther != null && _cur.isInSameTree(checkCursors(xOther)._cur);
    }

    //
    // The following operations have two cursors, but they must be in the same document
    //

    private Cursor preCheck(XmlCursor xOther) {
        Cursor other = checkCursors(xOther);

        if (_cur._locale != other._cur._locale) {
            throw new IllegalArgumentException("Cursors not in same document");
        }

        return other;
    }

    public int comparePosition(XmlCursor xOther) {
        Cursor other = preCheck(xOther);
        return syncWrap(() -> _comparePosition(other));
    }

    public boolean isLeftOf(XmlCursor xOther) {
        Cursor other = preCheck(xOther);
        return syncWrap(() -> _isLeftOf(other));
    }

    public boolean isAtSamePositionAs(XmlCursor xOther) {
        Cursor other = preCheck(xOther);
        return syncWrap(() -> _isAtSamePositionAs(other));
    }

    public boolean isRightOf(XmlCursor xOther) {
        Cursor other = preCheck(xOther);
        return syncWrap(() -> _isRightOf(other));
    }

    //
    // Create a cursor from an Xobj -- used for XmlBookmark.createCursor
    //

    public static XmlCursor newCursor(Xobj x, int p) {
        Locale l = x._locale;
        if (l.noSync()) {
            l.enter();
            try {
                return new Cursor(x, p);
            } finally {
                l.exit();
            }
        } else {
            synchronized (l) {
                l.enter();
                try {
                    return new Cursor(x, p);
                } finally {
                    l.exit();
                }
            }
        }
    }

    //
    // The following operations involve only one cursor
    //

    private boolean preCheck() {
        checkThisCursor();
        return _cur._locale.noSync();
    }

    @Override
    public void close() {
        if (_cur != null) {
            syncWrap(this::_dispose);
        }
    }

    @Override
    @Deprecated
    public void dispose() {
        close();
    }

    public Object monitor() {
        return syncWrap(this::_monitor);
    }

    public XmlDocumentProperties documentProperties() {
        return syncWrap(this::_documentProperties);
    }

    public XmlCursor newCursor() {
        return syncWrap(this::_newCursor);
    }

    public XMLStreamReader newXMLStreamReader() {
        return syncWrap((Supplier<XMLStreamReader>) this::_newXMLStreamReader);
    }

    public XMLStreamReader newXMLStreamReader(XmlOptions options) {
        return syncWrap(() -> _newXMLStreamReader(options));
    }

    public String xmlText() {
        return syncWrap((Supplier<String>) this::_xmlText);
    }

    public InputStream newInputStream() {
        return syncWrap((Supplier<InputStream>) this::_newInputStream);
    }

    public Reader newReader() {
        return syncWrap((Supplier<Reader>) this::_newReader);
    }

    public Node newDomNode() {
        return syncWrap((Supplier<Node>) this::_newDomNode);
    }

    public Node getDomNode() {
        return syncWrap(this::_getDomNode);
    }

    public void save(ContentHandler ch, LexicalHandler lh) throws SAXException {
        syncWrapSAXEx(() -> _save(ch, lh));
    }

    public void save(File file) throws IOException {
        syncWrapIOEx(() -> _save(file));
    }

    public void save(OutputStream os) throws IOException {
        syncWrapIOEx(() -> _save(os));
    }

    public void save(Writer w) throws IOException {
        syncWrapIOEx(() -> _save(w));
    }

    public String xmlText(XmlOptions options) {
        return syncWrap(() -> _xmlText(options));
    }

    public InputStream newInputStream(XmlOptions options) {
        return syncWrap(() -> _newInputStream(options));
    }

    public Reader newReader(XmlOptions options) {
        return syncWrap(() -> _newReader(options));
    }

    public Node newDomNode(XmlOptions options) {
        return syncWrap(() -> _newDomNode(options));
    }

    public void save(ContentHandler ch, LexicalHandler lh, XmlOptions options) throws SAXException {
        syncWrapSAXEx(() -> _save(ch, lh, options));
    }

    public void save(File file, XmlOptions options) throws IOException {
        syncWrapIOEx(() -> _save(file, options));
    }

    public void save(OutputStream os, XmlOptions options) throws IOException {
        syncWrapIOEx(() -> _save(os, options));
    }

    public void save(Writer w, XmlOptions options) throws IOException {
        syncWrapIOEx(() -> _save(w, options));
    }

    public void push() {
        syncWrap(this::_push);
    }

    public boolean pop() {
        return syncWrap(this::_pop);
    }

    public void selectPath(String path) {
        syncWrap(() -> _selectPath(path));
    }

    public void selectPath(String path, XmlOptions options) {
        syncWrap(() -> _selectPath(path, options));
    }

    public boolean hasNextSelection() {
        return syncWrap(this::_hasNextSelection);
    }

    public boolean toNextSelection() {
        return syncWrap(this::_toNextSelection);
    }

    public boolean toSelection(int i) {
        return syncWrap(() -> _toSelection(i));
    }

    public int getSelectionCount() {
        return syncWrap(this::_getSelectionCount);
    }

    public void addToSelection() {
        syncWrap(this::_addToSelection);
    }

    public void clearSelections() {
        syncWrap(this::_clearSelections);
    }

    public boolean toBookmark(XmlBookmark bookmark) {
        return syncWrap(() -> _toBookmark(bookmark));
    }

    public XmlBookmark toNextBookmark(Object key) {
        return syncWrap(() -> _toNextBookmark(key));
    }

    public XmlBookmark toPrevBookmark(Object key) {
        return syncWrap(() -> _toPrevBookmark(key));
    }

    public QName getName() {
        return syncWrap(this::_getName);
    }

    public void setName(QName name) {
        syncWrap(() -> _setName(name));
    }

    public String namespaceForPrefix(String prefix) {
        return syncWrap(() -> _namespaceForPrefix(prefix));
    }

    public String prefixForNamespace(String namespaceURI) {
        return syncWrap(() -> _prefixForNamespace(namespaceURI));
    }

    public void getAllNamespaces(Map<String, String> addToThis) {
        syncWrap(() -> _getAllNamespaces(addToThis));
    }

    public XmlObject getObject() {
        return syncWrap(this::_getObject);
    }

    public TokenType currentTokenType() {
        return syncWrapNoEnter(this::_currentTokenType);
    }

    public boolean isStartdoc() {
        return syncWrapNoEnter(this::_isStartdoc);
    }

    public boolean isEnddoc() {
        return syncWrapNoEnter(this::_isEnddoc);
    }

    public boolean isStart() {
        return syncWrapNoEnter(this::_isStart);
    }

    public boolean isEnd() {
        return syncWrapNoEnter(this::_isEnd);
    }

    public boolean isText() {
        return syncWrapNoEnter(this::_isText);
    }

    public boolean isAttr() {
        return syncWrapNoEnter(this::_isAttr);
    }

    public boolean isNamespace() {
        return syncWrapNoEnter(this::_isNamespace);
    }

    public boolean isComment() {
        return syncWrapNoEnter(this::_isComment);
    }

    public boolean isProcinst() {
        return syncWrapNoEnter(this::_isProcinst);
    }

    public boolean isContainer() {
        return syncWrapNoEnter(this::_isContainer);
    }

    public boolean isFinish() {
        return syncWrapNoEnter(this::_isFinish);
    }

    public boolean isAnyAttr() {
        return syncWrapNoEnter(this::_isAnyAttr);
    }

    public TokenType prevTokenType() {
        return syncWrap(this::_prevTokenType);
    }

    public boolean hasNextToken() {
        return syncWrapNoEnter(this::_hasNextToken);
    }

    public boolean hasPrevToken() {
        return syncWrap(this::_hasPrevToken);
    }

    public TokenType toNextToken() {
        return syncWrap(this::_toNextToken);
    }

    public TokenType toPrevToken() {
        return syncWrap(this::_toPrevToken);
    }

    public TokenType toFirstContentToken() {
        return syncWrap(this::_toFirstContentToken);
    }

    public TokenType toEndToken() {
        return syncWrap(this::_toEndToken);
    }

    public int toNextChar(int cch) {
        return syncWrap(() -> _toNextChar(cch));
    }

    public int toPrevChar(int cch) {
        return syncWrap(() -> _toPrevChar(cch));
    }

//    public boolean _toNextSibling()
//    {
//        return Locale.toNextSiblingElement(_cur);
//    }

    public boolean ___toNextSibling() {
        if (!_cur.hasParent()) {
            return false;
        }

        Xobj parent = _cur.getParentNoRoot();

        if (parent == null) {
            _cur._locale.enter();
            try {
                parent = _cur.getParent();
            } finally {
                _cur._locale.exit();
            }
        }

        return Locale.toNextSiblingElement(_cur, parent);
    }

    public boolean toNextSibling() {
        return syncWrapNoEnter(this::___toNextSibling);
    }

    public boolean toPrevSibling() {
        return syncWrap(this::_toPrevSibling);
    }

    public boolean toParent() {
        return syncWrap(this::_toParent);
    }

    public boolean toFirstChild() {
        return syncWrapNoEnter(this::_toFirstChild);
    }

    public boolean toLastChild() {
        return syncWrap(this::_toLastChild);
    }

    public boolean toChild(String name) {
        return syncWrap(() -> _toChild(name));
    }

    public boolean toChild(String namespace, String name) {
        return syncWrap(() -> _toChild(namespace, name));
    }

    public boolean toChild(QName name) {
        return syncWrap(() -> _toChild(name));
    }

    public boolean toChild(int index) {
        return syncWrap(() -> _toChild(index));
    }

    public boolean toChild(QName name, int index) {
        return syncWrap(() -> _toChild(name, index));
    }

    public boolean toNextSibling(String name) {
        return syncWrap(() -> _toNextSibling(name));
    }

    public boolean toNextSibling(String namespace, String name) {
        return syncWrap(() -> _toNextSibling(namespace, name));
    }

    public boolean toNextSibling(QName name) {
        return syncWrap(() -> _toNextSibling(name));
    }

    public boolean toFirstAttribute() {
        return syncWrapNoEnter(this::_toFirstAttribute);
    }

    public boolean toLastAttribute() {
        return syncWrap(this::_toLastAttribute);
    }

    public boolean toNextAttribute() {
        return syncWrap(this::_toNextAttribute);
    }

    public boolean toPrevAttribute() {
        return syncWrap(this::_toPrevAttribute);
    }

    public String getAttributeText(QName attrName) {
        return syncWrap(() -> _getAttributeText(attrName));
    }

    public boolean setAttributeText(QName attrName, String value) {
        return syncWrap(() -> _setAttributeText(attrName, value));
    }

    public boolean removeAttribute(QName attrName) {
        return syncWrap(() -> _removeAttribute(attrName));
    }

    public String getTextValue() {
        return syncWrap((Supplier<String>) this::_getTextValue);
    }

    public int getTextValue(char[] chars, int offset, int cch) {
        return syncWrap(() -> _getTextValue(chars, offset, cch));
    }

    public void setTextValue(String text) {
        syncWrap(() -> _setTextValue(text));
    }

    public void setTextValue(char[] sourceChars, int offset, int length) {
        syncWrap(() -> _setTextValue(sourceChars, offset, length));
    }

    public String getChars() {
        return syncWrap((Supplier<String>) this::_getChars);
    }

    public int getChars(char[] chars, int offset, int cch) {
        return syncWrap(() -> _getChars(chars, offset, cch));
    }

    public void toStartDoc() {
        syncWrapNoEnter(this::_toStartDoc);
    }

    public void toEndDoc() {
        syncWrapNoEnter(this::_toEndDoc);
    }

    public XmlCursor execQuery(String query) {
        return syncWrap(() -> _execQuery(query));
    }

    public XmlCursor execQuery(String query, XmlOptions options) {
        return syncWrap(() -> _execQuery(query, options));
    }

    public ChangeStamp getDocChangeStamp() {
        return syncWrap(this::_getDocChangeStamp);
    }

    public void setBookmark(XmlBookmark bookmark) {
        syncWrap(() -> _setBookmark(bookmark));
    }

    public XmlBookmark getBookmark(Object key) {
        return syncWrap(() -> _getBookmark(key));
    }

    public void clearBookmark(Object key) {
        syncWrap(() -> _clearBookmark(key));
    }

    @Override
    public void getAllBookmarkRefs(Collection<Object> listToFill) {
        syncWrap(() -> _getAllBookmarkRefs(listToFill));
    }

    public boolean removeXml() {
        return syncWrap(this::_removeXml);
    }

    public boolean removeXmlContents() {
        return syncWrap(this::_removeXmlContents);
    }

    public int removeChars(int cch) {
        return syncWrap(() -> _removeChars(cch));
    }

    public void insertChars(String text) {
        syncWrap(() -> _insertChars(text));
    }

    public void insertElement(QName name) {
        syncWrap(() -> _insertElement(name));
    }

    public void insertElement(String localName) {
        syncWrap(() -> _insertElement(localName));
    }

    public void insertElement(String localName, String uri) {
        syncWrap(() -> _insertElement(localName, uri));
    }

    public void beginElement(QName name) {
        syncWrap(() -> _beginElement(name));
    }

    public void beginElement(String localName) {
        syncWrap(() -> _beginElement(localName));
    }

    public void beginElement(String localName, String uri) {
        syncWrap(() -> _beginElement(localName, uri));
    }

    public void insertElementWithText(QName name, String text) {
        syncWrap(() -> _insertElementWithText(name, text));
    }

    public void insertElementWithText(String localName, String text) {
        syncWrap(() -> _insertElementWithText(localName, text));
    }

    public void insertElementWithText(String localName, String uri, String text) {
        syncWrap(() -> _insertElementWithText(localName, uri, text));
    }

    public void insertAttribute(String localName) {
        syncWrap(() -> _insertAttribute(localName));
    }

    public void insertAttribute(String localName, String uri) {
        syncWrap(() -> _insertAttribute(localName, uri));
    }

    public void insertAttribute(QName name) {
        syncWrap(() -> _insertAttribute(name));
    }

    public void insertAttributeWithValue(String name, String value) {
        syncWrap(() -> _insertAttributeWithValue(name, value));
    }

    public void insertAttributeWithValue(String name, String uri, String value) {
        syncWrap(() -> _insertAttributeWithValue(name, uri, value));
    }

    public void insertAttributeWithValue(QName name, String value) {
        syncWrap(() -> _insertAttributeWithValue(name, value));
    }

    public void insertNamespace(String prefix, String namespace) {
        syncWrap(() -> _insertNamespace(prefix, namespace));
    }

    public void insertComment(String text) {
        syncWrap(() -> _insertComment(text));
    }

    public void insertProcInst(String target, String text) {
        syncWrap(() -> _insertProcInst(target, text));
    }

    public void dump() {
        syncWrap(this::_dump);
    }

    private interface WrapSAXEx {
        void run() throws SAXException;
    }

    private interface WrapIOEx {
        void run() throws IOException;
    }


    @SuppressWarnings("SynchronizeOnNonFinalField")
    private void syncWrap(Runnable inner) {
        if (preCheck()) {
            syncWrapHelper(inner, true);
        } else {
            synchronized (_cur._locale) {
                syncWrapHelper(inner, true);
            }
        }
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    private <T> T syncWrap(Supplier<T> inner) {
        if (preCheck()) {
            return syncWrapHelper(inner, true);
        } else {
            synchronized (_cur._locale) {
                return syncWrapHelper(inner, true);
            }
        }
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    private <T> T syncWrapNoEnter(Supplier<T> inner) {
        if (preCheck()) {
            return syncWrapHelper(inner, false);
        } else {
            synchronized (_cur._locale) {
                return syncWrapHelper(inner, false);
            }
        }
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    private void syncWrapNoEnter(Runnable inner) {
        if (preCheck()) {
            syncWrapHelper(inner, false);
        } else {
            synchronized (_cur._locale) {
                syncWrapHelper(inner, false);
            }
        }
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    private void syncWrapSAXEx(WrapSAXEx inner) throws SAXException {
        if (preCheck()) {
            syncWrapHelper(inner);
        } else {
            synchronized (_cur._locale) {
                syncWrapHelper(inner);
            }
        }
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    private void syncWrapIOEx(WrapIOEx inner) throws IOException {
        if (preCheck()) {
            syncWrapHelper(inner);
        } else {
            synchronized (_cur._locale) {
                syncWrapHelper(inner);
            }
        }
    }

    private void syncWrapHelper(Runnable inner, final boolean enterLocale) {
        final Locale l = _cur._locale;
        if (enterLocale) {
            l.enter();
        }
        try {
            inner.run();
        } finally {
            if (enterLocale) {
                l.exit();
            }
        }
    }

    private <T> T syncWrapHelper(Supplier<T> inner, final boolean enterLocale) {
        final Locale l = _cur._locale;
        if (enterLocale) {
            l.enter();
        }
        try {
            return inner.get();
        } finally {
            if (enterLocale) {
                l.exit();
            }
        }
    }

    private void syncWrapHelper(WrapSAXEx inner) throws SAXException {
        final Locale l = _cur._locale;
        l.enter();
        try {
            inner.run();
        } finally {
            l.exit();
        }
    }

    private void syncWrapHelper(WrapIOEx inner) throws IOException {
        final Locale l = _cur._locale;
        l.enter();
        try {
            inner.run();
        } finally {
            l.exit();
        }
    }
}
