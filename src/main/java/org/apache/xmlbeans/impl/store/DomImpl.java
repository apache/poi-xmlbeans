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
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlRuntimeException;
import org.apache.xmlbeans.impl.common.XMLChar;
import org.apache.xmlbeans.impl.soap.*;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.w3c.dom.*;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import java.io.PrintStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.apache.xmlbeans.impl.util.StringUtil.equalsIgnoreCase;

// DOM Level 3

public final class DomImpl {
    static final int ELEMENT = Node.ELEMENT_NODE;
    static final int ATTR = Node.ATTRIBUTE_NODE;
    static final int TEXT = Node.TEXT_NODE;
    static final int CDATA = Node.CDATA_SECTION_NODE;
    static final int ENTITYREF = Node.ENTITY_REFERENCE_NODE;
    static final int ENTITY = Node.ENTITY_NODE;
    static final int PROCINST = Node.PROCESSING_INSTRUCTION_NODE;
    static final int COMMENT = Node.COMMENT_NODE;
    static final int DOCUMENT = Node.DOCUMENT_NODE;
    static final int DOCTYPE = Node.DOCUMENT_TYPE_NODE;
    static final int DOCFRAG = Node.DOCUMENT_FRAGMENT_NODE;
    static final int NOTATION = Node.NOTATION_NODE;

    public interface Dom {
        Locale locale();

        int nodeType();

        Cur tempCur();

        QName getQName();

        boolean nodeCanHavePrefixUri();

        void dump();

        void dump(PrintStream o);

        void dump(PrintStream o, Object ref);
    }

    static Node parent(Dom d) {
        return node_getParentNode(d);
    }

    static Node firstChild(Dom d) {
        return node_getFirstChild(d);
    }

    static Node nextSibling(Dom d) {
        return node_getNextSibling(d);
    }

    static Node prevSibling(Dom d) {
        return node_getPreviousSibling(d);
    }

    public static Node append(Dom n, Dom p) {
        return node_insertBefore(p, n, null);
    }

    public static Node insert(Dom n, Dom b) {
        assert b != null;
        return node_insertBefore((Dom) parent(b), n, b);
    }

    public static Node remove(Dom n) {
        Node p = parent(n);

        if (p != null) {
            node_removeChild((Dom) p, n);
        }

        return (Node) n;
    }

    //
    // Handy dandy Dom exceptions
    //

    static class HierarchyRequestErr extends DOMException {
        HierarchyRequestErr(String message) {
            super(HIERARCHY_REQUEST_ERR, message);
        }
    }

    static class WrongDocumentErr extends DOMException {
        WrongDocumentErr(String message) {
            super(WRONG_DOCUMENT_ERR, message);
        }
    }

    static class NotFoundErr extends DOMException {
        NotFoundErr(String message) {
            super(NOT_FOUND_ERR, message);
        }
    }

    static class NamespaceErr extends DOMException {
        NamespaceErr(String message) {
            super(NAMESPACE_ERR, message);
        }
    }

    static class NoModificationAllowedErr extends DOMException {
        NoModificationAllowedErr(String message) {
            super(NO_MODIFICATION_ALLOWED_ERR, message);
        }
    }

    static class InuseAttributeError extends DOMException {
        InuseAttributeError() {
            this("Attribute currently in use error");
        }

        InuseAttributeError(String message) {
            super(INUSE_ATTRIBUTE_ERR, message);
        }
    }

    static class IndexSizeError extends DOMException {
        IndexSizeError() {
            this("Index Size Error");
        }

        IndexSizeError(String message) {
            super(INDEX_SIZE_ERR, message);
        }
    }

    static class NotSupportedError extends DOMException {
        NotSupportedError(String message) {
            super(NOT_SUPPORTED_ERR, message);
        }
    }

    static class InvalidCharacterError extends DOMException {
        InvalidCharacterError() {
            this("The name contains an invalid character");
        }

        InvalidCharacterError(String message) {
            super(INVALID_CHARACTER_ERR, message);
        }
    }

    //
    // Helper fcns
    //

    private static final class EmptyNodeList implements NodeList {
        public int getLength() {
            return 0;
        }

        public Node item(int i) {
            return null;
        }
    }

    public static final NodeList _emptyNodeList = new EmptyNodeList();

    static String nodeKindName(int t) {
        switch (t) {
            case ATTR:
                return "attribute";
            case CDATA:
                return "cdata section";
            case COMMENT:
                return "comment";
            case DOCFRAG:
                return "document fragment";
            case DOCUMENT:
                return "document";
            case DOCTYPE:
                return "document type";
            case ELEMENT:
                return "element";
            case ENTITY:
                return "entity";
            case ENTITYREF:
                return "entity reference";
            case NOTATION:
                return "notation";
            case PROCINST:
                return "processing instruction";
            case TEXT:
                return "text";

            default:
                throw new RuntimeException("Unknown node type");
        }
    }

    private static String isValidChild(Dom parent, Dom child) {
        int pk = parent.nodeType();
        int ck = child.nodeType();

        switch (pk) {
            case DOCUMENT: {
                switch (ck) {
                    case ELEMENT: {
                        if (document_getDocumentElement(parent) != null) {
                            return "Documents may only have a maximum of one document element";
                        }

                        return null;
                    }
                    case DOCTYPE: {
                        if (document_getDoctype(parent) != null) {
                            return "Documents may only have a maximum of one document type node";
                        }

                        return null;
                    }
                    case PROCINST:
                    case COMMENT:
                        return null;
                }

                break;
            }

            case ATTR: {
                if (ck == TEXT || ck == ENTITYREF) {
                    return null;
                }

                // TODO -- traverse the entity tree, making sure that there are
                // only entity refs and text nodes in it.

                break;
            }

            case DOCFRAG:
            case ELEMENT:
            case ENTITY:
            case ENTITYREF: {
                switch (ck) {
                    case ELEMENT:
                    case ENTITYREF:
                    case CDATA:
                    case TEXT:
                    case COMMENT:
                    case PROCINST:
                        return null;
                }

                break;
            }

            case CDATA:
            case TEXT:
            case COMMENT:
            case PROCINST:
            case DOCTYPE:
            case NOTATION:
                return nodeKindName(pk) + " nodes may not have any children";
        }

        return
            nodeKindName(pk) + " nodes may not have " +
            nodeKindName(ck) + " nodes as children";
    }

    private static void validateNewChild(final Dom parent, Dom child) {
        String msg = isValidChild(parent, child);

        if (msg != null) {
            throw new HierarchyRequestErr(msg);
        }

        if (parent == child) {
            throw new HierarchyRequestErr("New child and parent are the same node");
        }

        for (Node p = (Node) parent; (p = parent((Dom) p)) != null; ) {
            // TODO - use read only state on a node to know if it is under an
            // entity ref

            if (child.nodeType() == ENTITYREF) {
                throw new NoModificationAllowedErr("Entity reference trees may not be modified");
            }

            if (child == p) {
                throw new HierarchyRequestErr("New child is an ancestor node of the parent node");
            }
        }
    }

    private static String validatePrefix(
        String prefix, String uri, String local, boolean isAttr) {

        if (prefix != null && prefix.contains(":")) {
            throw new NamespaceErr("Invalid prefix - contains ':' character");
        }

        validateNcName(prefix);

        if (prefix == null) {
            prefix = "";
        }

        if (uri == null) {
            uri = "";
        }

        if (!prefix.isEmpty() && uri.isEmpty()) {
            throw new NamespaceErr("Attempt to give a prefix for no namespace");
        }

        if (prefix.equals("xml") && !uri.equals(Locale._xml1998Uri)) {
            throw new NamespaceErr("Invalid prefix - begins with 'xml'");
        }

        if (isAttr) {
            if (prefix.length() > 0) {
                if (local.equals("xmlns")) {
                    throw new NamespaceErr("Invalid namespace - attr is default namespace already");
                }

                if (Locale.beginsWithXml(local)) {
                    throw new NamespaceErr("Invalid namespace - attr prefix begins with 'xml'");
                }

                if (prefix.equals("xmlns") && !uri.equals(Locale._xmlnsUri)) {
                    throw new NamespaceErr("Invalid namespace - uri is not '" + Locale._xmlnsUri + ";");
                }
            } else {
                if (local.equals("xmlns") && !uri.equals(Locale._xmlnsUri)) {
                    throw new NamespaceErr("Invalid namespace - uri is not '" + Locale._xmlnsUri + ";");
                }
            }
        } else if (Locale.beginsWithXml(prefix)) {
            throw new NamespaceErr("Invalid prefix - begins with 'xml'");
        }

        return prefix;
    }

    private static void validateName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name is null");
        }

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name is empty");
        }

        if (!XMLChar.isValidName(name)) {
            throw new InvalidCharacterError("Name has an invalid character");
        }
    }

    private static void validateNcName(String name) {
        if (name != null && name.length() > 0 && !XMLChar.isValidNCName(name)) {
            throw new InvalidCharacterError();
        }
    }

    private static void validateQualifiedName(String name, String uri, boolean isAttr) {
        assert name != null;

        if (uri == null) {
            uri = "";
        }

        int i = name.indexOf(':');

        String local;

        if (i < 0) {
            validateNcName(local = name);

            if (isAttr && local.equals("xmlns") && !uri.equals(Locale._xmlnsUri)) {
                throw
                    new NamespaceErr(
                        "Default xmlns attribute does not have namespace: " + Locale._xmlnsUri);
            }
        } else {
            if (i == 0) {
                throw new NamespaceErr("Invalid qualified name, no prefix specified");
            }

            String prefix = name.substring(0, i);

            validateNcName(prefix);

            if (uri.isEmpty()) {
                throw new NamespaceErr("Attempt to give a prefix for no namespace");
            }

            local = name.substring(i + 1);

            if (local.indexOf(':') >= 0) {
                throw new NamespaceErr("Invalid qualified name, more than one colon");
            }

            validateNcName(local);

            if (prefix.equals("xml") && !uri.equals(Locale._xml1998Uri)) {
                throw new NamespaceErr("Invalid prefix - begins with 'xml'");
            }
        }

        if (local.isEmpty()) {
            throw new NamespaceErr("Invalid qualified name, no local part specified");
        }
    }

    private static void removeNode(Dom n) {
        assert n.nodeType() != TEXT && n.nodeType() != CDATA;

        Cur cFrom = n.tempCur();

        cFrom.toEnd();

        // Move any char nodes which ater after the node to remove to be before it.  The call to
        // Next here does two things, it tells me if I can get after the move to remove (all nodes
        // but the root) and it positions me at the place where there are char nodes after.

        if (cFrom.next()) {
            CharNode fromNodes = cFrom.getCharNodes();

            if (fromNodes != null) {
                cFrom.setCharNodes(null);
                Cur cTo = n.tempCur();
                cTo.setCharNodes(CharNode.appendNodes(cTo.getCharNodes(), fromNodes));
                cTo.release();
            }
        }

        cFrom.release();

        Cur.moveNode((Xobj) n, null);
    }

    private abstract static class ElementsNodeList implements NodeList {
        ElementsNodeList(Dom root) {
            assert root.nodeType() == DOCUMENT || root.nodeType() == ELEMENT;

            _root = root;
            _locale = _root.locale();
            _version = 0;
        }

        public int getLength() {
            ensureElements();

            return _elements.size();
        }

        public Node item(int i) {
            ensureElements();
            return i < 0 || i >= _elements.size() ? null : (Node) _elements.get(i);
        }

        private void ensureElements() {
            if (_version == _locale.version()) {
                return;
            }

            _version = _locale.version();
            _elements = new ArrayList<>();

            syncWrapHelper(_locale, true, () -> {
                addElements(_root);
                return null;
            });
        }

        private void addElements(Dom node) {
            for (Node c = firstChild(node); c != null; c = nextSibling((Dom) c)) {
                if (((Dom) c).nodeType() == ELEMENT) {
                    if (match((Dom) c)) {
                        _elements.add((Dom) c);
                    }

                    addElements((Dom) c);
                }
            }
        }

        protected abstract boolean match(Dom element);

        private final Dom _root;
        private final Locale _locale;
        private long _version;
        private ArrayList<Dom> _elements;
    }

    private static class ElementsByTagNameNodeList extends ElementsNodeList {
        ElementsByTagNameNodeList(Dom root, String name) {
            super(root);
            _name = name;
            assert (_name != null);
        }

        protected boolean match(Dom element) {
            return _name.equals("*") || _name.equals(_node_getNodeName(element));
        }

        private final String _name;
    }

    private static class ElementsByTagNameNSNodeList extends ElementsNodeList {
        ElementsByTagNameNSNodeList(Dom root, String uri, String local) {
            super(root);

            _uri = uri == null ? "" : uri;
            _local = local;
            assert (local != null);
        }

        protected boolean match(Dom element) {
            if (!(_uri.equals("*") || _uri.equals(_node_getNamespaceURI(element)))) {
                return false;
            }

            return _local.equals("*") || _local.equals(_node_getLocalName(element));
        }

        private final String _uri;
        private final String _local;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Document _domImplementation_createDocument(Locale l, String u, String n, DocumentType t) {
        return syncWrapHelper(l, true, () -> domImplementation_createDocument(l, u, n, t));
    }

    public static Document domImplementation_createDocument(
        Locale l, String namespaceURI, String qualifiedName, DocumentType doctype) {
        validateQualifiedName(qualifiedName, namespaceURI, false);

        Cur c = l.tempCur();
        c.createDomDocumentRoot();
        Document doc = (Document) c.getDom();
        c.next();
        c.createElement(l.makeQualifiedQName(namespaceURI, qualifiedName));

        if (doctype != null) {
            throw new RuntimeException("Not impl");
        }

        c.toParent();

        try {
            Locale.autoTypeDocument(c, null, null);
        } catch (XmlException e) {
            throw new XmlRuntimeException(e);
        }

        c.release();

        return doc;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static boolean _domImplementation_hasFeature(Locale l, String feature, String version) {
        if (feature == null) {
            return false;
        }

        if (version != null && !version.isEmpty() &&
            !version.equals("1.0") && !version.equals("2.0")) {
            return false;
        }

        if (equalsIgnoreCase(feature, "core")) {
            return true;
        }

        return equalsIgnoreCase(feature, "xml");
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Element _document_getDocumentElement(Dom d) {
        return syncWrap(d, DomImpl::document_getDocumentElement);
    }

    public static Element document_getDocumentElement(final Dom d) {
        for (Node n = firstChild(d); n != null; n = nextSibling((Dom) n)) {
            if (((Dom) n).nodeType() == ELEMENT) {
                return (Element) n;
            }
        }

        return null;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static DocumentFragment _document_createDocumentFragment(Dom d) {
        return syncWrap(d, DomImpl::document_createDocumentFragment);
    }

    public static DocumentFragment document_createDocumentFragment(Dom d) {
        Cur c = d.locale().tempCur();
        c.createDomDocFragRoot();
        Dom f = c.getDom();
        c.release();
        return (DocumentFragment) f;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Element _document_createElement(Dom d, String name) {
        return syncWrap(d, p -> document_createElement(p, name));
    }

    public static Element document_createElement(Dom d, String name) {
        validateName(name);
        Locale l = d.locale();
        Cur c = l.tempCur();
        c.createElement(l.makeQualifiedQName("", name));
        ElementXobj e = (ElementXobj) c.getDom();
        c.release();
        e.clearBit(Xobj.CAN_HAVE_PREFIX_URI);
        return e;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Element _document_createElementNS(Dom d, String uri, String qname) {
        return syncWrap(d, p -> document_createElementNS(p, uri, qname));
    }

    public static Element document_createElementNS(Dom d, String uri, String qname) {
        validateQualifiedName(qname, uri, false);
        Locale l = d.locale();
        Cur c = l.tempCur();
        c.createElement(l.makeQualifiedQName(uri, qname));
        Dom e = c.getDom();
        c.release();
        return (Element) e;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Attr _document_createAttribute(Dom d, String name) {
        return syncWrap(d, p -> document_createAttribute(p, name));
    }

    public static Attr document_createAttribute(Dom d, String name) {
        validateName(name);
        Locale l = d.locale();
        Cur c = l.tempCur();
        c.createAttr(l.makeQualifiedQName("", name));
        AttrXobj e = (AttrXobj) c.getDom();
        c.release();
        e.clearBit(Xobj.CAN_HAVE_PREFIX_URI);
        return e;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Attr _document_createAttributeNS(Dom d, String uri, String qname) {
        return syncWrap(d, p -> document_createAttributeNS(p, uri, qname));
    }

    public static Attr document_createAttributeNS(Dom d, String uri, String qname) {
        validateQualifiedName(qname, uri, true);
        Locale l = d.locale();
        Cur c = l.tempCur();
        c.createAttr(l.makeQualifiedQName(uri, qname));
        Dom e = c.getDom();
        c.release();
        return (Attr) e;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Comment _document_createComment(Dom d, String data) {
        return syncWrap(d, p -> document_createComment(p, data));
    }

    public static Comment document_createComment(Dom d, String data) {
        Locale l = d.locale();
        Cur c = l.tempCur();
        c.createComment();
        Dom comment = c.getDom();
        if (data != null) {
            c.next();
            c.insertString(data);
        }
        c.release();
        return (Comment) comment;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static ProcessingInstruction _document_createProcessingInstruction(Dom d, String target, String data) {
        return syncWrap(d, p -> document_createProcessingInstruction(p, target, data));
    }

    public static ProcessingInstruction document_createProcessingInstruction(Dom d, String target, String data) {
        if (target == null) {
            throw new IllegalArgumentException("Target is null");
        }

        if (target.isEmpty()) {
            throw new IllegalArgumentException("Target is empty");
        }

        if (!XMLChar.isValidName(target)) {
            throw new InvalidCharacterError("Target has an invalid character");
        }

        if (Locale.beginsWithXml(target) && target.length() == 3) {
            throw new InvalidCharacterError("Invalid target - is 'xml'");
        }

        Locale l = d.locale();
        Cur c = l.tempCur();
        c.createProcinst(target);
        Dom pi = c.getDom();
        if (data != null) {
            c.next();
            c.insertString(data);
        }
        c.release();
        return (ProcessingInstruction) pi;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static CDATASection _document_createCDATASection(Dom d, String data) {
        return document_createCDATASection(d, data);
    }

    public static CDATASection document_createCDATASection(Dom d, String data) {
        TextNode t = d.locale().createCdataNode();

        if (data == null) {
            data = "";
        }

        t.setChars(data, 0, data.length());

        return (CDATASection) t;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Text _document_createTextNode(Dom d, String data) {
        return document_createTextNode(d, data);
    }

    public static Text document_createTextNode(Dom d, String data) {
        TextNode t = d.locale().createTextNode();

        if (data == null) {
            data = "";
        }

        t.setChars(data, 0, data.length());

        return t;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static EntityReference _document_createEntityReference(Dom d, String name) {
        throw new RuntimeException("Not implemented");
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static Element _document_getElementById(Dom d, String elementId) {
        throw new RuntimeException("Not implemented");
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static NodeList _document_getElementsByTagName(Dom d, String name) {
        return syncWrap(d, p -> document_getElementsByTagName(p, name));
    }

    public static NodeList document_getElementsByTagName(Dom d, String name) {
        return new ElementsByTagNameNodeList(d, name);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static NodeList _document_getElementsByTagNameNS(Dom d, String uri, String local) {
        return syncWrap(d, p -> document_getElementsByTagNameNS(p, uri, local));
    }

    public static NodeList document_getElementsByTagNameNS(Dom d, String uri, String local) {
        return new ElementsByTagNameNSNodeList(d, uri, local);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static DOMImplementation _document_getImplementation(Dom d) {
        return d.locale();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _document_importNode(Dom d, Node n, boolean deep) {
        // TODO -- I'm importing my own nodes through DOM methods!  -- make this faster
        return syncWrap(d, p -> document_importNode(p, n, deep));
    }

    public static Node document_importNode(Dom d, Node n, boolean deep) {
        if (n == null) {
            return null;
        }

        Node i;

        boolean copyChildren = false;

        switch (n.getNodeType()) {
            case DOCUMENT:
                throw new NotSupportedError("Document nodes may not be imported");

            case DOCTYPE:
                throw new NotSupportedError("Document type nodes may not be imported");

            case ELEMENT: {
                String local = n.getLocalName();

                if (local == null || local.isEmpty()) {
                    i = document_createElement(d, n.getNodeName());
                } else {
                    String prefix = n.getPrefix();
                    String name = prefix == null || prefix.isEmpty() ? local : prefix + ":" + local;
                    String uri = n.getNamespaceURI();

                    if (uri == null || uri.isEmpty()) {
                        i = document_createElement(d, name);
                    } else {
                        i = document_createElementNS(d, uri, name);
                    }
                }

                NamedNodeMap attrs = n.getAttributes();

                for (int a = 0; a < attrs.getLength(); a++) {
                    attributes_setNamedItem((Dom) i, (Dom) document_importNode(d, attrs.item(a), true));
                }

                copyChildren = deep;

                break;
            }

            case ATTR: {
                String local = n.getLocalName();

                if (local == null || local.isEmpty()) {
                    i = document_createAttribute(d, n.getNodeName());
                } else {
                    String prefix = n.getPrefix();
                    String name = prefix == null || prefix.isEmpty() ? local : prefix + ":" + local;
                    String uri = n.getNamespaceURI();

                    if (uri == null || uri.isEmpty()) {
                        i = document_createAttribute(d, name);
                    } else {
                        i = document_createAttributeNS(d, uri, name);
                    }
                }

                copyChildren = true;

                break;
            }

            case DOCFRAG: {
                i = document_createDocumentFragment(d);

                copyChildren = deep;

                break;
            }

            case PROCINST: {
                i = document_createProcessingInstruction(d, n.getNodeName(), n.getNodeValue());
                break;
            }

            case COMMENT: {
                i = document_createComment(d, n.getNodeValue());
                break;
            }

            case TEXT: {
                i = document_createTextNode(d, n.getNodeValue());
                break;
            }

            case CDATA: {
                i = document_createCDATASection(d, n.getNodeValue());
                break;
            }

            case ENTITYREF:
            case ENTITY:
            case NOTATION:
                throw new RuntimeException("Not impl");

            default:
                throw new RuntimeException("Unknown kind");
        }

        if (copyChildren) {
            NodeList children = n.getChildNodes();

            for (int c = 0; c < children.getLength(); c++) {
                node_insertBefore((Dom) i, (Dom) document_importNode(d, children.item(c), true), null);
            }
        }

        return i;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static DocumentType _document_getDoctype(Dom d) {
        return syncWrap(d, DomImpl::document_getDoctype);
    }

    public static DocumentType document_getDoctype(Dom d) {
        return null;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Document _node_getOwnerDocument(Dom d) {
        return syncWrap(d, DomImpl::node_getOwnerDocument);
    }

    public static Document node_getOwnerDocument(Dom n) {
        if (n.nodeType() == DOCUMENT) {
            return null;
        }

        Locale l = n.locale();

        if (l._ownerDoc == null) {
            Cur c = l.tempCur();
            c.createDomDocumentRoot();
            l._ownerDoc = c.getDom();
            c.release();
        }

        return (Document) l._ownerDoc;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _node_getParentNode(Dom d) {
        return syncWrap(d, DomImpl::node_getParentNode);
    }

    public static Node node_getParentNode(Dom n) {
        Cur c = null;

        switch (n.nodeType()) {
            case DOCUMENT:
            case DOCFRAG:
            case ATTR:
                break;

            case PROCINST:
            case COMMENT:
            case ELEMENT: {
                if (!(c = n.tempCur()).toParentRaw()) {
                    c.release();
                    c = null;
                }

                break;
            }

            case TEXT:
            case CDATA: {
                if ((c = n.tempCur()) != null) {
                    c.toParent();
                }

                break;
            }

            case ENTITYREF:
                throw new RuntimeException("Not impl");

            case ENTITY:
            case DOCTYPE:
            case NOTATION:
                throw new RuntimeException("Not impl");

            default:
                throw new RuntimeException("Unknown kind");
        }

        if (c == null) {
            return null;
        }

        Dom d = c.getDom();

        c.release();

        return (Node) d;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _node_getFirstChild(Dom n) {
        assert n instanceof Xobj;
        Xobj node = (Xobj) n;
        if (!node.isVacant()) {
            if (node.isFirstChildPtrDomUsable()) {
                return (Node) node._firstChild;
            }
            Xobj lastAttr = node.lastAttr();
            if (lastAttr != null &&
                lastAttr.isNextSiblingPtrDomUsable()) {
                return (NodeXobj) lastAttr._nextSibling;
            }
            if (node.isExistingCharNodesValueUsable()) {
                return node._charNodesValue;
            }
        }

        return syncWrapNoEnter(n, DomImpl::node_getFirstChild);
    }

    public static Node node_getFirstChild(Dom n) {
        Dom fc = null;

        switch (n.nodeType()) {
            case TEXT:
            case CDATA:
            case PROCINST:
            case COMMENT:
                break;

            case ENTITYREF:
                throw new RuntimeException("Not impl");

            case ENTITY:
            case DOCTYPE:
            case NOTATION:
                throw new RuntimeException("Not impl");

            case ELEMENT:
            case DOCUMENT:
            case DOCFRAG:
            case ATTR: {

                Xobj node = (Xobj) n;
                node.ensureOccupancy();
                if (node.isFirstChildPtrDomUsable()) {
                    return (NodeXobj) node._firstChild;
                }
                Xobj lastAttr = node.lastAttr();
                if (lastAttr != null) {
                    if (lastAttr.isNextSiblingPtrDomUsable()) {
                        return (NodeXobj) lastAttr._nextSibling;
                    } else if (lastAttr.isCharNodesAfterUsable()) {
                        return lastAttr._charNodesAfter;
                    }
                }
                if (node.isCharNodesValueUsable()) {
                    return node._charNodesValue;
                }


                break;
            }
        }

        // TODO - handle entity refs here ...

        return (Node) fc;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _node_getLastChild(Dom n) {
        return syncWrap(n, DomImpl::node_getLastChild);
    }

    public static Node node_getLastChild(Dom n) {
        switch (n.nodeType()) {
            case TEXT:
            case CDATA:
            case PROCINST:
            case COMMENT:
                return null;

            case ENTITYREF:
                throw new RuntimeException("Not impl");

            case ENTITY:
            case DOCTYPE:
            case NOTATION:
                throw new RuntimeException("Not impl");

            case ELEMENT:
            case DOCUMENT:
            case DOCFRAG:
            case ATTR:
                break;
        }

        Dom lc = null;
        CharNode nodes;

        Cur c = n.tempCur();

        if (c.toLastChild()) {
            lc = c.getDom();

            c.skip();

            if ((nodes = c.getCharNodes()) != null) {
                lc = null;
            }
        } else {
            c.next();
            nodes = c.getCharNodes();
        }

        if (lc == null && nodes != null) {
            while (nodes._next != null) {
                nodes = nodes._next;
            }

            lc = nodes;
        }

        c.release();

        // TODO - handle entity refs here ...

        return (Node) lc;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _node_getNextSibling(Dom n) {
        return syncWrapNoEnter(n, DomImpl::node_getNextSibling);
    }

    public static Node node_getNextSibling(Dom n) {
        Dom ns = null;

        switch (n.nodeType()) {
            case DOCUMENT:
            case DOCFRAG:
            case ATTR:
                break;

            case TEXT:
            case CDATA: {
                CharNode cn = (CharNode) n;
                //if src is attr & next is null , ret null;
                //if src is container and
                // a) this node is aftertext && src._nextSib = null; ret null
                // b) this node is value && src._fc = null; ret null


                if (!(cn.getObject() instanceof Xobj)) {
                    return null;
                }
                Xobj src = (Xobj) cn.getObject();
                //if src is attr this node is always value and
                // next is always the next ptr of the attr
                src._charNodesAfter =
                    Cur.updateCharNodes(src._locale, src, src._charNodesAfter, src._cchAfter);

                src._charNodesValue =
                    Cur.updateCharNodes(src._locale, src, src._charNodesValue, src._cchValue);

                if (cn._next != null) {
                    ns = cn._next;
                    break;
                }
                boolean isThisNodeAfterText = cn.isNodeAftertext();

                if (isThisNodeAfterText) {
                    ns = (NodeXobj) src._nextSibling;
                } else     //srcValue or attribute source
                {
                    ns = (NodeXobj) src._firstChild;
                }
                break;

            }

            case PROCINST:
            case COMMENT:
            case ELEMENT: {
                assert n instanceof Xobj : "PI, Comments and Elements always backed up by Xobj";
                Xobj node = (Xobj) n;
                node.ensureOccupancy();
                if (node.isNextSiblingPtrDomUsable()) {
                    return
                        (NodeXobj) node._nextSibling;
                }
                if (node.isCharNodesAfterUsable()) {
                    return node._charNodesAfter;
                }
                break;
            }

            case ENTITY:
            case NOTATION:
            case ENTITYREF:
            case DOCTYPE:
                throw new RuntimeException("Not implemented");
        }

        // TODO - handle entity refs here ...

        return (Node) ns;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _node_getPreviousSibling(Dom n) {
        return syncWrapNoEnter(n, DomImpl::node_getPreviousSibling);
    }

    public static Node node_getPreviousSibling(Dom n) {
        Node prev;
        switch (n.nodeType()) {
            case TEXT:
            case CDATA: {
                assert n instanceof CharNode : "Text/CData should be a CharNode";
                CharNode node = (CharNode) n;
                if (!(node.getObject() instanceof Xobj)) {
                    return null;
                }
                NodeXobj src = (NodeXobj) node.getObject();
                src.ensureOccupancy();
                boolean isThisNodeAfterText = node.isNodeAftertext();
                prev = node._prev;
                if (prev == null) {
                    prev = isThisNodeAfterText ? src : src._charNodesValue;
                }
                break;
            }
            default: {
                assert n instanceof NodeXobj;
                NodeXobj node = (NodeXobj) n;
                prev = (NodeXobj) node._prevSibling;
                if ((prev == null || !(node instanceof AttrXobj) && prev instanceof AttrXobj) &&
                    node._parent != null) {
                    prev = node_getFirstChild((Dom) node._parent);
                }
            }
        }
        Node temp = prev;
        while (temp != null && (temp = node_getNextSibling((Dom) temp)) != n) {
            prev = temp;
        }
        return prev;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static boolean _node_hasAttributes(Dom n) {
        return syncWrap(n, DomImpl::node_hasAttributes);
    }

    public static boolean node_hasAttributes(Dom n) {
        boolean hasAttrs = false;

        if (n.nodeType() == ELEMENT) {
            Cur c = n.tempCur();
            hasAttrs = c.hasAttrs();
            c.release();
        }

        return hasAttrs;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static boolean _node_isSupported(Dom n, String feature, String version) {
        return _domImplementation_hasFeature(n.locale(), feature, version);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _node_normalize(Dom n) {
        syncWrapVoid(n, DomImpl::node_normalize);
    }

    public static void node_normalize(Dom n) {
        switch (n.nodeType()) {
            case TEXT:
            case CDATA:
            case PROCINST:
            case COMMENT:
                return;

            case ENTITYREF:
                throw new RuntimeException("Not impl");

            case ENTITY:
            case DOCTYPE:
            case NOTATION:
                throw new RuntimeException("Not impl");

            case ELEMENT:
            case DOCUMENT:
            case DOCFRAG:
            case ATTR:
                break;
        }

        Cur c = n.tempCur();

        c.push();

        do {
            c.nextWithAttrs();

            CharNode cn = c.getCharNodes();

            if (cn != null) {
                if (!c.isText()) {
                    while (cn != null) {
                        cn.setChars(null, 0, 0);
                        cn = CharNode.remove(cn, cn);
                    }
                } else if (cn._next != null) {
                    while (cn._next != null) {
                        cn.setChars(null, 0, 0);
                        cn = CharNode.remove(cn, cn._next);
                    }

                    cn._cch = Integer.MAX_VALUE;
                }

                c.setCharNodes(cn);
            }
        }
        while (!c.isAtEndOfLastPush());

        c.release();

        n.locale().invalidateDomCaches(n);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static boolean _node_hasChildNodes(Dom n) {
        // TODO - make this faster
        return n instanceof Xobj && _node_getFirstChild(n) != null;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _node_appendChild(Dom p, Node newChild) {
        return _node_insertBefore(p, newChild, null);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _node_replaceChild(Dom p, Node newChild, Node oldChild) {
        Locale l = p.locale();

        if (newChild == null) {
            throw new IllegalArgumentException("Child to add is null");
        }

        if (oldChild == null) {
            throw new NotFoundErr("Child to replace is null");
        }

        Dom nc;

        if (!(newChild instanceof Dom) || (nc = (Dom) newChild).locale() != l) {
            throw new WrongDocumentErr("Child to add is from another document");
        }

        Dom oc;

        if (!(oldChild instanceof Dom) || (oc = (Dom) oldChild).locale() != l) {
            throw new WrongDocumentErr("Child to replace is from another document");
        }

        Dom oc2 = oc;
        return syncWrap(p, x -> node_replaceChild(x, nc, oc2));
    }

    public static Node node_replaceChild(Dom p, Dom newChild, Dom oldChild) {
        // Remove the old child firest to avoid a dom exception raised
        // when inserting two document elements

        Node nextNode = node_getNextSibling(oldChild);

        node_removeChild(p, oldChild);

        try {
            node_insertBefore(p, newChild, (Dom) nextNode);
        } catch (DOMException e) {
            node_insertBefore(p, oldChild, (Dom) nextNode);

            throw e;
        }

        return (Node) oldChild;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _node_insertBefore(Dom p, Node newChild, Node refChild) {
        Locale l = p.locale();

        if (newChild == null) {
            throw new IllegalArgumentException("Child to add is null");
        }

        Dom nc;

        if (!(newChild instanceof Dom) || (nc = (Dom) newChild).locale() != l) {
            throw new WrongDocumentErr("Child to add is from another document");
        }

        Dom rc = null;

        if (refChild != null) {
            if (!(refChild instanceof Dom) || (rc = (Dom) refChild).locale() != l) {
                throw new WrongDocumentErr("Reference child is from another document");
            }
        }
        Dom rc2 = rc;

        return syncWrap(p, x -> node_insertBefore(x, nc, rc2));
    }

    public static Node node_insertBefore(Dom p, Dom nc, final Dom rc) {
        assert nc != null;

        // Inserting self before self is a no-op

        if (nc == rc) {
            return (Node) nc;
        }

        if (rc != null && parent(rc) != p) {
            throw new NotFoundErr("RefChild is not a child of this node");
        }

        // TODO - obey readonly status of a substree

        int nck = nc.nodeType();

        if (nck == DOCFRAG) {
            for (Node c = firstChild(nc); c != null; c = nextSibling((Dom) c)) {
                validateNewChild(p, (Dom) c);
            }

            for (Node c = firstChild(nc); c != null; ) {
                Node n = nextSibling((Dom) c);

                if (rc == null) {
                    append((Dom) c, p);
                } else {
                    insert((Dom) c, rc);
                }

                c = n;
            }

            return (Node) nc;
        }

        //
        // Make sure the new child is allowed here
        //

        validateNewChild(p, nc);

        //
        // Orphan the child before establishing a new parent
        //

        remove(nc);

        int pk = p.nodeType();

        // Only these nodes can be modifiable parents
        assert pk == ATTR || pk == DOCFRAG || pk == DOCUMENT || pk == ELEMENT;

        switch (nck) {
            case ELEMENT:
            case COMMENT:
            case PROCINST: {
                if (rc == null) {
                    Cur cTo = p.tempCur();
                    cTo.toEnd();
                    Cur.moveNode((Xobj) nc, cTo);
                    cTo.release();
                } else {
                    int rck = rc.nodeType();

                    if (rck == TEXT || rck == CDATA) {
                        // Quick and dirty impl....

                        List<Dom> charNodes = new ArrayList<>();

                        Dom rc2 = rc;

                        while (rc2 != null && (rc2.nodeType() == TEXT || rc2.nodeType() == CDATA)) {
                            Node next = nextSibling(rc2);
                            charNodes.add((Dom) remove(rc2));
                            rc2 = (Dom) next;
                        }

                        if (rc2 == null) {
                            append(nc, p);
                        } else {
                            insert(nc, rc2);
                        }

                        rc2 = (Dom) nextSibling(nc);

                        for (Object charNode : charNodes) {
                            Dom n = (Dom) charNode;

                            if (rc2 == null) {
                                append(n, p);
                            } else {
                                insert(n, rc2);
                            }
                        }
                    } else if (rck == ENTITYREF) {
                        throw new RuntimeException("Not implemented");
                    } else {
                        assert rck == ELEMENT || rck == PROCINST || rck == COMMENT;
                        Cur cTo = rc.tempCur();
                        Cur.moveNode((Xobj) nc, cTo);
                        cTo.release();
                    }
                }

                break;
            }

            case TEXT:
            case CDATA: {
                CharNode n = (CharNode) nc;

                assert n._prev == null && n._next == null;

                CharNode refCharNode = null;
                Cur c = p.tempCur();

                if (rc == null) {
                    c.toEnd();
                } else {
                    int rck = rc.nodeType();

                    if (rck == TEXT || rck == CDATA) {
                        c.moveToCharNode(refCharNode = (CharNode) rc);
                    } else if (rck == ENTITYREF) {
                        throw new RuntimeException("Not implemented");
                    } else {
                        c.moveToDom(rc);
                    }
                }

                CharNode nodes = c.getCharNodes();

                nodes = CharNode.insertNode(nodes, n, refCharNode);

                c.insertChars(n.getObject(), n._off, n._cch);

                c.setCharNodes(nodes);

                c.release();

                break;
            }

            case ENTITYREF: {
                throw new RuntimeException("Not implemented");
            }

            case DOCTYPE: {
                // TODO - don't actually insert this here, associate it with the
                // doc??  Hmm .. Perhaps I should disallow insertion into the tree
                // at all.

                throw new RuntimeException("Not implemented");
            }

            default:
                throw new RuntimeException("Unexpected child node type");
        }

        return (Node) nc;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _node_removeChild(Dom p, Node child) {
        Locale l = p.locale();

        if (child == null) {
            throw new NotFoundErr("Child to remove is null");
        }

        Dom c;

        if (!(child instanceof Dom) || (c = (Dom) child).locale() != l) {
            throw new WrongDocumentErr("Child to remove is from another document");
        }

        return syncWrap(p, x -> node_removeChild(x, c));
    }

    public static Node node_removeChild(Dom parent, Dom child) {
        if (parent(child) != parent) {
            throw new NotFoundErr("Child to remove is not a child of given parent");
        }

        switch (child.nodeType()) {
            case DOCUMENT:
            case DOCFRAG:
            case ATTR:
                throw new IllegalStateException();

            case ELEMENT:
            case PROCINST:
            case COMMENT:
                removeNode(child);
                break;

            case TEXT:
            case CDATA: {
                Cur c = child.tempCur();
                CharNode nodes = c.getCharNodes();
                CharNode cn = (CharNode) child;
                assert (cn.getDom() != null);

                cn.setChars(c.moveChars(null, cn._cch), c._offSrc, c._cchSrc);
                c.setCharNodes(CharNode.remove(nodes, cn));
                c.release();
                break;
            }

            case ENTITYREF:
                throw new RuntimeException("Not impl");

            case ENTITY:
            case DOCTYPE:
            case NOTATION:
                throw new RuntimeException("Not impl");

            default:
                throw new RuntimeException("Unknown kind");
        }

        return (Node) child;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _node_cloneNode(Dom n, boolean deep) {
        return syncWrap(n, p -> node_cloneNode(p, deep));
    }

    public static Node node_cloneNode(Dom n, boolean deep) {
        Locale l = n.locale();

        Dom clone = null;

        if (!deep) {
            Cur shallow = null;

            switch (n.nodeType()) {
                case DOCUMENT:
                    shallow = l.tempCur();
                    shallow.createDomDocumentRoot();
                    break;

                case DOCFRAG:
                    shallow = l.tempCur();
                    shallow.createDomDocFragRoot();
                    break;

                case ELEMENT: {
                    shallow = l.tempCur();
                    shallow.createElement(n.getQName());

                    Element elem = (Element) shallow.getDom();
                    NamedNodeMap attrs = ((Element) n).getAttributes();

                    for (int i = 0; i < attrs.getLength(); i++) {
                        elem.setAttributeNodeNS((Attr) attrs.item(i).cloneNode(true));
                    }

                    break;
                }

                case ATTR:
                    shallow = l.tempCur();
                    shallow.createAttr(n.getQName());
                    break;

                case PROCINST:
                case COMMENT:
                case TEXT:
                case CDATA:
                case ENTITYREF:
                case ENTITY:
                case DOCTYPE:
                case NOTATION:
                    break;
            }

            if (shallow != null) {
                clone = shallow.getDom();
                shallow.release();
            }
        }

        if (clone == null) {
            switch (n.nodeType()) {
                case DOCUMENT:
                case DOCFRAG:
                case ATTR:
                case ELEMENT:
                case PROCINST:
                case COMMENT: {
                    Cur cClone = l.tempCur();
                    Cur cSrc = n.tempCur();
                    cSrc.copyNode(cClone);
                    clone = cClone.getDom();
                    cClone.release();
                    cSrc.release();

                    break;
                }

                case TEXT:
                case CDATA: {
                    Cur c = n.tempCur();
                    CharNode cn = n.nodeType() == TEXT ? l.createTextNode() : l.createCdataNode();
                    cn.setChars(c.getChars(((CharNode) n)._cch), c._offSrc, c._cchSrc);
                    clone = cn;
                    c.release();
                    break;
                }

                case ENTITYREF:
                case ENTITY:
                case DOCTYPE:
                case NOTATION:
                    throw new RuntimeException("Not impl");

                default:
                    throw new RuntimeException("Unknown kind");
            }
        }

        return (Node) clone;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _node_getLocalName(Dom n) {
        if (!n.nodeCanHavePrefixUri()) {
            return null;
        }
        QName name = n.getQName();
        return name == null ? "" : name.getLocalPart();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _node_getNamespaceURI(Dom n) {
        if (!n.nodeCanHavePrefixUri()) {
            return null;
        }
        QName name = n.getQName();
        // TODO - should return the correct namespace for xmlns ...
        //name.getNamespaceURI().equals("")? null:
        return name == null ? "" : name.getNamespaceURI();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _node_setPrefix(Dom n, String prefix) {
        syncWrapVoid(n, p -> node_setPrefix(p, prefix));
    }

    public static void node_setPrefix(Dom n, String prefix) {
        // TODO - make it possible to set the prefix of an xmlns
        // TODO - test to make use prefix: xml maps to the predefined namespace
        // if set???? hmmm ... perhaps I should not allow the setting of any
        // prefixes which start with xml unless the namespace is the predefined
        // one and the prefix is 'xml' all other prefixes which start with
        // 'xml' should fail.

        if (n.nodeType() == ELEMENT || n.nodeType() == ATTR) {
            Cur c = n.tempCur();
            QName name = c.getName();
            String uri = name.getNamespaceURI();
            String local = name.getLocalPart();

            prefix = validatePrefix(prefix, uri, local, n.nodeType() == ATTR);

            c.setName(n.locale().makeQName(uri, local, prefix));
            c.release();
        } else {
            validatePrefix(prefix, "", "", false);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _node_getPrefix(Dom n) {
        if (!n.nodeCanHavePrefixUri()) {
            return null;
        }
        QName name = n.getQName();
        return name == null ? "" : name.getPrefix();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _node_getNodeName(Dom n) {
        switch (n.nodeType()) {
            case CDATA:
                return "#cdata-section";
            case COMMENT:
                return "#comment";
            case DOCFRAG:
                return "#document-fragment";
            case DOCUMENT:
                return "#document";
            case PROCINST:
                return n.getQName().getLocalPart();
            case TEXT:
                return "#text";

            case ATTR:
            case ELEMENT: {
                QName name = n.getQName();
                String prefix = name.getPrefix();
                return prefix.isEmpty() ? name.getLocalPart() : prefix + ":" + name.getLocalPart();
            }

            case DOCTYPE:
            case ENTITY:
            case ENTITYREF:
            case NOTATION:
                throw new RuntimeException("Not impl");

            default:
                throw new RuntimeException("Unknown node type");
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static short _node_getNodeType(Dom n) {
        return (short) n.nodeType();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _node_setNodeValue(Dom n, String nodeValue) {
        syncWrapVoid(n, p -> node_setNodeValue(p, nodeValue));
    }

    public static void node_setNodeValue(Dom n, String nodeValue) {
        if (nodeValue == null) {
            nodeValue = "";
        }

        switch (n.nodeType()) {
            case TEXT:
            case CDATA: {
                CharNode cn = (CharNode) n;

                Cur c;

                if ((c = cn.tempCur()) != null) {
                    c.moveChars(null, cn._cch);
                    cn._cch = nodeValue.length();
                    c.insertString(nodeValue);
                    c.release();
                } else {
                    cn.setChars(nodeValue, 0, nodeValue.length());
                }

                break;
            }

            case ATTR: {
                // Try to set an exisiting text node to contain the new value

                NodeList children = ((Node) n).getChildNodes();

                while (children.getLength() > 1) {
                    node_removeChild(n, (Dom) children.item(1));
                }

                if (children.getLength() == 0) {
                    TextNode tn = n.locale().createTextNode();
                    tn.setChars(nodeValue, 0, nodeValue.length());
                    node_insertBefore(n, tn, null);
                } else {
                    assert children.getLength() == 1;
                    children.item(0).setNodeValue(nodeValue);
                }
                if (((AttrXobj) n).isId()) {
                    Document d = DomImpl.node_getOwnerDocument(n);
                    String val = node_getNodeValue(n);
                    if (d instanceof DocumentXobj dox) {
                        dox.removeIdElement(val);
                        dox.addIdElement(nodeValue, (Dom) attr_getOwnerElement(n));
                    }
                }

                break;
            }

            case PROCINST:
            case COMMENT: {
                Cur c = n.tempCur();
                c.next();

                c.getChars(-1);
                c.moveChars(null, c._cchSrc);
                c.insertString(nodeValue);

                c.release();

                break;
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _node_getNodeValue(Dom n) {
        return syncWrapNoEnter(n, DomImpl::node_getNodeValue);
    }

    public static String node_getNodeValue(Dom n) {
        String s = null;

        switch (n.nodeType()) {
            case ATTR:
            case PROCINST:
            case COMMENT: {
                s = ((Xobj) n).getValueAsString();
                break;
            }

            case TEXT:
            case CDATA: {
                assert n instanceof CharNode : "Text/CData should be a CharNode";
                CharNode node = (CharNode) n;
                if (!(node.getObject() instanceof Xobj)) {
                    s = CharUtil.getString(node.getObject(), node._off, node._cch);
                } else {
                    Xobj src = (Xobj) node.getObject();
                    src.ensureOccupancy();
                    boolean isThisNodeAfterText = node.isNodeAftertext();
                    if (isThisNodeAfterText) {
                        src._charNodesAfter =
                            Cur.updateCharNodes(src._locale, src, src._charNodesAfter, src._cchAfter);
                        s = src.getCharsAfterAsString(node._off, node._cch);
                    } else {
                        src._charNodesValue =
                            Cur.updateCharNodes(src._locale, src, src._charNodesValue, src._cchValue);
                        s = src.getCharsValueAsString(node._off, node._cch);
                    }

                }
                break;
            }
        }

        return s;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static Object _node_getUserData(Dom n, String key) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static Object _node_setUserData(Dom n, String key, Object data, UserDataHandler handler) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static Object _node_getFeature(Dom n, String feature, String version) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static boolean _node_isEqualNode(Dom n, Node arg) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static boolean _node_isSameNode(Dom n, Node arg) {
        // TODO: check if relying on object identity is ok
        boolean ret;
        if (n instanceof CharNode) {
//            ret = ((CharNode)n).getDom().equals(arg);
            ret = n.equals(arg);
        } else if (n instanceof NodeXobj xobj) {
            ret = xobj.getDom().equals(arg);
        } else {
            throw new DomLevel3NotImplemented();
        }
        return ret;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static String _node_lookupNamespaceURI(Dom n, String prefix) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static boolean _node_isDefaultNamespace(Dom n, String namespaceURI) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static String _node_lookupPrefix(Dom n, String namespaceURI) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static void _node_setTextContent(Dom n, String textContent) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static String _node_getTextContent(Dom n) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static short _node_compareDocumentPosition(Dom n, Node other) {
        // TODO: find a faster way to compare, may be based on the locale / cursor elements inside the nodes
        if (!(n instanceof Node)) {
            return Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC;
        }
        Iterator<Node> nAncIter = ancestorAndSelf((Node) n).iterator();
        Iterator<Node> oAncIter = ancestorAndSelf(other).iterator();

        Node nAnc, oAnc;
        boolean isFirst = true, isEqual;
        do {
            nAnc = nAncIter.next();
            oAnc = oAncIter.next();
            isEqual = Objects.equals(nAnc, oAnc);
            if (isFirst && !isEqual) {
                // if root node differ, the elements are from different documents
                return Node.DOCUMENT_POSITION_DISCONNECTED;
            }
            isFirst = false;
        } while (isEqual && nAncIter.hasNext() && oAncIter.hasNext());

        if (isEqual) {
            return nAncIter.hasNext()
                ? Node.DOCUMENT_POSITION_CONTAINS | Node.DOCUMENT_POSITION_PRECEDING
                : (oAncIter.hasNext()
                ? Node.DOCUMENT_POSITION_CONTAINED_BY | Node.DOCUMENT_POSITION_FOLLOWING
                : Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC
            );
        } else {
            Node prevSib = nAnc;
            while ((prevSib = prevSib.getPreviousSibling()) != null) {
                if (Objects.equals(prevSib, oAnc)) {
                    return Node.DOCUMENT_POSITION_PRECEDING;
                }
            }
            return Node.DOCUMENT_POSITION_FOLLOWING;
        }
    }

    private static List<Node> ancestorAndSelf(Node node) {
        LinkedList<Node> nodes = new LinkedList<>();
        Node n = node;
        do {
            nodes.addFirst(n);
            n = n.getParentNode();
        } while (n != null);
        return nodes;
    }


    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static String _node_getBaseURI(Dom n) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _childNodes_item(Dom n, int i) {
        return i == 0
            ? _node_getFirstChild(n)
            : syncWrapNoEnter(n, p -> childNodes_item(p, i));
    }

    public static Node childNodes_item(Dom n, int i) {
        if (i < 0) {
            return null;
        }

        switch (n.nodeType()) {
            case TEXT:
            case CDATA:
            case PROCINST:
            case COMMENT:
                return null;

            case ENTITYREF:
                throw new RuntimeException("Not impl");

            case ENTITY:
            case DOCTYPE:
            case NOTATION:
                throw new RuntimeException("Not impl");

            case ELEMENT:
            case DOCUMENT:
            case DOCFRAG:
            case ATTR:
                break;
        }
        if (i == 0) {
            return node_getFirstChild(n);
        }
        return (Node) n.locale().findDomNthChild(n, i);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static int _childNodes_getLength(Dom n) {
        assert n instanceof Xobj;
        int count;
        Xobj node = (Xobj) n;
        if (!node.isVacant() &&
            (count = node.getDomZeroOneChildren()) < 2) {
            return count;
        }

        return syncWrapNoEnter(n, DomImpl::childNodes_getLength);
    }

    public static int childNodes_getLength(Dom n) {
        switch (n.nodeType()) {
            case TEXT:
            case CDATA:
            case PROCINST:
            case COMMENT:
                return 0;

            case ENTITYREF:
                throw new RuntimeException("Not impl");

            case ENTITY:
            case DOCTYPE:
            case NOTATION:
                throw new RuntimeException("Not impl");

            case ELEMENT:
            case DOCUMENT:
            case DOCFRAG:
            case ATTR:
                break;
        }

        int count;
        assert n instanceof Xobj;
        Xobj node = (Xobj) n;
        node.ensureOccupancy();
        if ((count = node.getDomZeroOneChildren()) < 2) {
            return count;
        }
        return n.locale().domLength(n);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _element_getTagName(Dom e) {
        return _node_getNodeName(e);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Attr _element_getAttributeNode(Dom e, String name) {
        return (Attr) _attributes_getNamedItem(e, name);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Attr _element_getAttributeNodeNS(Dom e, String uri, String local) {
        return (Attr) _attributes_getNamedItemNS(e, uri, local);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Attr _element_setAttributeNode(Dom e, Attr newAttr) {
        return (Attr) _attributes_setNamedItem(e, newAttr);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Attr _element_setAttributeNodeNS(Dom e, Attr newAttr) {
        return (Attr) _attributes_setNamedItemNS(e, newAttr);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _element_getAttribute(Dom e, String name) {
        Node a = _attributes_getNamedItem(e, name);
        return a == null ? "" : a.getNodeValue();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _element_getAttributeNS(Dom e, String uri, String local) {
        Node a = _attributes_getNamedItemNS(e, uri, local);
        return a == null ? "" : a.getNodeValue();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static boolean _element_hasAttribute(Dom e, String name) {
        return _attributes_getNamedItem(e, name) != null;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static boolean _element_hasAttributeNS(Dom e, String uri, String local) {
        return _attributes_getNamedItemNS(e, uri, local) != null;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _element_removeAttribute(Dom e, String name) {
        try {
            _attributes_removeNamedItem(e, name);
        } catch (NotFoundErr ignored) {
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _element_removeAttributeNS(Dom e, String uri, String local) {
        try {
            _attributes_removeNamedItemNS(e, uri, local);
        } catch (NotFoundErr ignored) {
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Attr _element_removeAttributeNode(Dom e, Attr oldAttr) {
        if (oldAttr == null) {
            throw new NotFoundErr("Attribute to remove is null");
        }

        if (oldAttr.getOwnerElement() != e) {
            throw new NotFoundErr("Attribute to remove does not belong to this element");
        }

        return (Attr) _attributes_removeNamedItem(e, oldAttr.getNodeName());
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _element_setAttribute(Dom e, String name, String value) {
        // TODO - validate all attr/element names in all appropriate methods
        syncWrapVoid(e, p -> element_setAttribute(p, name, value));
    }

    public static void element_setAttribute(Dom e, String name, String value) {
        Node a = attributes_getNamedItem(e, name);

        if (a == null) {
            Dom e2 = (Dom) node_getOwnerDocument(e);
            if (e2 == null) {
                throw new NotFoundErr("Document element can't be determined.");
            }

            a = document_createAttribute(e2, name);
            attributes_setNamedItem(e, (Dom) a);
        }

        node_setNodeValue((Dom) a, value);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _element_setAttributeNS(Dom e, String uri, String qname, String value) {
        syncWrapVoid(e, p -> element_setAttributeNS(p, uri, qname, value));
    }

    public static void element_setAttributeNS(Dom e, String uri, String qname, String value) {
        validateQualifiedName(qname, uri, true);

        QName name = e.locale().makeQualifiedQName(uri, qname);
        String local = name.getLocalPart();
        String prefix = validatePrefix(name.getPrefix(), uri, local, true);

        Node a = attributes_getNamedItemNS(e, uri, local);

        if (a == null) {
            a = document_createAttributeNS((Dom) node_getOwnerDocument(e), uri, local);
            attributes_setNamedItemNS(e, (Dom) a);
        }

        node_setPrefix((Dom) a, prefix);
        node_setNodeValue((Dom) a, value);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static NodeList _element_getElementsByTagName(Dom e, String name) {
        return syncWrap(e, p -> element_getElementsByTagName(p, name));
    }

    public static NodeList element_getElementsByTagName(Dom e, String name) {
        return new ElementsByTagNameNodeList(e, name);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static NodeList _element_getElementsByTagNameNS(Dom e, String uri, String local) {
        return syncWrap(e, p -> element_getElementsByTagNameNS(p, uri, local));
    }

    public static NodeList element_getElementsByTagNameNS(Dom e, String uri, String local) {
        return new ElementsByTagNameNSNodeList(e, uri, local);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static int _attributes_getLength(Dom e) {
        return syncWrap(e, DomImpl::attributes_getLength);
    }

    public static int attributes_getLength(Dom e) {
        int n = 0;
        Cur c = e.tempCur();

        while (c.toNextAttr()) {
            n++;
        }

        c.release();
        return n;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _attributes_setNamedItem(Dom e, Node attr) {
        Locale l = e.locale();

        if (attr == null) {
            throw new IllegalArgumentException("Attr to set is null");
        }

        Dom a;

        if (!(attr instanceof Dom) || (a = (Dom) attr).locale() != l) {
            throw new WrongDocumentErr("Attr to set is from another document");
        }

        return syncWrap(e, p -> attributes_setNamedItem(p, a));
    }

    public static Node attributes_setNamedItem(Dom e, Dom a) {
        if (attr_getOwnerElement(a) != null) {
            throw new InuseAttributeError();
        }

        if (a.nodeType() != ATTR) {
            throw new HierarchyRequestErr("Node is not an attribute");
        }

        String name = _node_getNodeName(a);
        Dom oldAttr = null;

        Cur c = e.tempCur();

        while (c.toNextAttr()) {
            Dom aa = c.getDom();

            if (_node_getNodeName(aa).equals(name)) {
                if (oldAttr == null) {
                    oldAttr = aa;
                } else {
                    removeNode(aa);
                    c.toPrevAttr();
                }
            }
        }

        if (oldAttr == null) {
            c.moveToDom(e);
            c.next();
            Cur.moveNode((Xobj) a, c);
        } else {
            c.moveToDom(oldAttr);
            Cur.moveNode((Xobj) a, c);
            removeNode(oldAttr);
        }

        c.release();

        return (Node) oldAttr;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _attributes_getNamedItem(Dom e, String name) {
        return syncWrap(e, p -> attributes_getNamedItem(e, name));
    }

    public static Node attributes_getNamedItem(Dom e, String name) {
        Dom a = null;
        Cur c = e.tempCur();

        while (c.toNextAttr()) {
            Dom d = c.getDom();
            if (_node_getNodeName(d).equals(name)) {
                a = d;
                break;
            }
        }

        c.release();
        return (Node) a;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _attributes_getNamedItemNS(Dom e, String uri, String local) {
        return syncWrap(e, p -> attributes_getNamedItemNS(p, uri, local));
    }

    public static Node attributes_getNamedItemNS(Dom e, String uri, String local) {
        if (uri == null) {
            uri = "";
        }

        Dom a = null;
        Cur c = e.tempCur();

        while (c.toNextAttr()) {
            Dom d = c.getDom();
            QName n = d.getQName();

            if (n.getNamespaceURI().equals(uri) && n.getLocalPart().equals(local)) {
                a = d;
                break;
            }
        }

        c.release();
        return (Node) a;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _attributes_removeNamedItem(Dom e, String name) {
        return syncWrap(e, p -> attributes_removeNamedItem(p, name));
    }

    public static Node attributes_removeNamedItem(Dom e, String name) {
        Dom oldAttr = null;

        Cur c = e.tempCur();

        while (c.toNextAttr()) {
            Dom aa = c.getDom();

            if (_node_getNodeName(aa).equals(name)) {
                if (oldAttr == null) {
                    oldAttr = aa;
                }

                if (((AttrXobj) aa).isId()) {
                    Node d = DomImpl.node_getOwnerDocument(aa);
                    String val = node_getNodeValue(aa);
                    if (d instanceof DocumentXobj xobj) {
                        xobj.removeIdElement(val);
                    }
                }
                removeNode(aa);
                c.toPrevAttr();
            }
        }

        c.release();

        if (oldAttr == null) {
            throw new NotFoundErr("Named item not found: " + name);
        }

        return (Node) oldAttr;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _attributes_removeNamedItemNS(Dom e, String uri, String local) {
        return syncWrap(e, p -> attributes_removeNamedItemNS(p, uri, local));
    }

    public static Node attributes_removeNamedItemNS(Dom e, String uri, String local) {
        if (uri == null) {
            uri = "";
        }

        Dom oldAttr = null;

        Cur c = e.tempCur();

        while (c.toNextAttr()) {
            Dom aa = c.getDom();

            QName qn = aa.getQName();

            if (qn.getNamespaceURI().equals(uri) && qn.getLocalPart().equals(local)) {
                if (oldAttr == null) {
                    oldAttr = aa;
                }
                if (((AttrXobj) aa).isId()) {
                    Node d = DomImpl.node_getOwnerDocument(aa);
                    String val = node_getNodeValue(aa);
                    if (d instanceof DocumentXobj xobj) {
                        xobj.removeIdElement(val);
                    }
                }
                removeNode(aa);

                c.toPrevAttr();
            }
        }

        c.release();

        if (oldAttr == null) {
            throw new NotFoundErr("Named item not found: uri=" + uri + ", local=" + local);
        }

        return (Node) oldAttr;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _attributes_setNamedItemNS(Dom e, Node attr) {
        Locale l = e.locale();

        if (attr == null) {
            throw new IllegalArgumentException("Attr to set is null");
        }

        Dom a;

        if (!(attr instanceof Dom) || (a = (Dom) attr).locale() != l) {
            throw new WrongDocumentErr("Attr to set is from another document");
        }

        return syncWrap(e, p -> attributes_setNamedItemNS(p, a));
    }

    public static Node attributes_setNamedItemNS(Dom e, Dom a) {
        Node owner = attr_getOwnerElement(a);

        if (owner == e) {
            return (Node) a;
        }

        if (owner != null) {
            throw new InuseAttributeError();
        }

        if (a.nodeType() != ATTR) {
            throw new HierarchyRequestErr("Node is not an attribute");
        }

        QName name = a.getQName();
        Dom oldAttr = null;

        Cur c = e.tempCur();

        while (c.toNextAttr()) {
            Dom aa = c.getDom();

            if (aa.getQName().equals(name)) {
                if (oldAttr == null) {
                    oldAttr = aa;
                } else {
                    removeNode(aa);
                    c.toPrevAttr();
                }
            }
        }

        if (oldAttr == null) {
            c.moveToDom(e);
            c.next();
            Cur.moveNode((Xobj) a, c);
        } else {
            c.moveToDom(oldAttr);
            Cur.moveNode((Xobj) a, c);
            removeNode(oldAttr);
        }

        c.release();

        return (Node) oldAttr;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Node _attributes_item(Dom e, int index) {
        return syncWrap(e, p -> attributes_item(p, index));
    }

    public static Node attributes_item(Dom e, int index) {
        if (index < 0) {
            return null;
        }

        Cur c = e.tempCur();

        Dom a = null;

        while (c.toNextAttr()) {
            if (index-- == 0) {
                a = c.getDom();
                break;
            }
        }

        c.release();

        return (Node) a;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _processingInstruction_getData(Dom p) {
        return _node_getNodeValue(p);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _processingInstruction_getTarget(Dom p) {
        return _node_getNodeName(p);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _processingInstruction_setData(Dom p, String data) {
        _node_setNodeValue(p, data);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static boolean _attr_getSpecified(Dom a) {
        // Can't tell the difference
        return true;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Element _attr_getOwnerElement(Dom a) {
        return syncWrap(a, DomImpl::attr_getOwnerElement);
    }

    public static Element attr_getOwnerElement(Dom n) {
        Cur c = n.tempCur();

        if (!c.toParentRaw()) {
            c.release();
            return null;
        }

        Dom p = c.getDom();
        c.release();
        return (Element) p;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _characterData_appendData(Dom cd, String arg) {
        // TODO - fix this *really* cheesy/bad/lousy perf impl
        //        also fix all the funcitons which follow

        if (arg != null && !arg.isEmpty()) {
            _node_setNodeValue(cd, _node_getNodeValue(cd) + arg);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _characterData_deleteData(Dom c, int offset, int count) {
        String s = _characterData_getData(c);

        if (offset < 0 || offset > s.length() || count < 0) {
            throw new IndexSizeError();
        }

        if (offset + count > s.length()) {
            count = s.length() - offset;
        }

        if (count > 0) {
            _characterData_setData(c, s.substring(0, offset) + s.substring(offset + count));
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _characterData_getData(Dom c) {
        return _node_getNodeValue(c);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static int _characterData_getLength(Dom c) {
        return _characterData_getData(c).length();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _characterData_insertData(Dom c, int offset, String arg) {
        String s = _characterData_getData(c);

        if (offset < 0 || offset > s.length()) {
            throw new IndexSizeError();
        }

        if (arg != null && arg.length() > 0) {
            _characterData_setData(c, s.substring(0, offset) + arg + s.substring(offset));
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _characterData_replaceData(Dom c, int offset, int count, String arg) {
        String s = _characterData_getData(c);

        if (offset < 0 || offset > s.length() || count < 0) {
            throw new IndexSizeError();
        }

        if (offset + count > s.length()) {
            count = s.length() - offset;
        }

        if (count > 0) {
            _characterData_setData(
                c, s.substring(0, offset) + (arg == null ? "" : arg)
                   + s.substring(offset + count));
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static void _characterData_setData(Dom c, String data) {
        _node_setNodeValue(c, data);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static String _characterData_substringData(Dom c, int offset, int count) {
        String s = _characterData_getData(c);

        if (offset < 0 || offset > s.length() || count < 0) {
            throw new IndexSizeError();
        }

        if (offset + count > s.length()) {
            count = s.length() - offset;
        }

        return s.substring(offset, offset + count);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static Text _text_splitText(Dom t, int offset) {
        assert t.nodeType() == TEXT;

        String s = _characterData_getData(t);

        if (offset < 0 || offset > s.length()) {
            throw new IndexSizeError();
        }

        _characterData_deleteData(t, offset, s.length() - offset);

        // Don't need to pass a doc here, any node will do..

        Dom t2 = (Dom) _document_createTextNode(t, s.substring(offset));

        Dom p = (Dom) _node_getParentNode(t);

        if (p != null) {
            _node_insertBefore(p, (Text) t2, _node_getNextSibling(t));
            t.locale().invalidateDomCaches(p);
        }

        return (Text) t2;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static String _text_getWholeText(Dom t) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static boolean _text_isElementContentWhitespace(Dom t) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public static Text _text_replaceWholeText(Dom t, String content) {
        throw new DomLevel3NotImplemented();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static XMLStreamReader _getXmlStreamReader(Dom n) {
        return syncWrap(n, DomImpl::getXmlStreamReader);
    }

    public static XMLStreamReader getXmlStreamReader(Dom n) {
        XMLStreamReader xs;

        switch (n.nodeType()) {
            case DOCUMENT:
            case DOCFRAG:
            case ATTR:
            case ELEMENT:
            case PROCINST:
            case COMMENT: {
                Cur c = n.tempCur();
                xs = Jsr173.newXmlStreamReader(c, null);
                c.release();
                break;
            }

            case TEXT:
            case CDATA: {
                CharNode cn = (CharNode) n;

                Cur c;

                if ((c = cn.tempCur()) == null) {
                    c = n.locale().tempCur();

                    xs = Jsr173.newXmlStreamReader(c, cn.getObject(), cn._off, cn._cch);
                } else {
                    xs =
                        Jsr173.newXmlStreamReader(
                            c, c.getChars(cn._cch), c._offSrc, c._cchSrc);

                }

                c.release();

                break;
            }

            case ENTITYREF:
            case ENTITY:
            case DOCTYPE:
            case NOTATION:
                throw new RuntimeException("Not impl");

            default:
                throw new RuntimeException("Unknown kind");
        }

        return xs;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static XmlCursor _getXmlCursor(Dom n) {
        return syncWrap(n, DomImpl::getXmlCursor);
    }

    public static XmlCursor getXmlCursor(Dom n) {
        Cur c = n.tempCur();
        Cursor xc = new Cursor(c);
        c.release();
        return xc;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////

    public static XmlObject _getXmlObject(Dom n) {
        return syncWrap(n, DomImpl::getXmlObject);
    }

    public static XmlObject getXmlObject(Dom n) {
        Cur c = n.tempCur();
        XmlObject x = c.getObject();
        c.release();
        return x;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////


    //
    // Soap Text Node
    //

    public static boolean _soapText_isComment(Dom n) {
        org.apache.xmlbeans.impl.soap.Text text = (org.apache.xmlbeans.impl.soap.Text) n;
        return syncWrap(n, p -> p.locale()._saaj.soapText_isComment(text));
    }

    //
    // Soap Node
    //

    public static void _soapNode_detachNode(Dom n) {
        org.apache.xmlbeans.impl.soap.Node node = (org.apache.xmlbeans.impl.soap.Node) n;
        syncWrapVoid(n, p -> p.locale()._saaj.soapNode_detachNode(node));
    }

    public static void _soapNode_recycleNode(Dom n) {
        org.apache.xmlbeans.impl.soap.Node node = (org.apache.xmlbeans.impl.soap.Node) n;
        syncWrapVoid(n, p -> p.locale()._saaj.soapNode_recycleNode(node));
    }

    public static String _soapNode_getValue(Dom n) {
        org.apache.xmlbeans.impl.soap.Node node = (org.apache.xmlbeans.impl.soap.Node) n;
        return syncWrap(n, p -> p.locale()._saaj.soapNode_getValue(node));
    }

    public static void _soapNode_setValue(Dom n, String value) {
        org.apache.xmlbeans.impl.soap.Node node = (org.apache.xmlbeans.impl.soap.Node) n;
        syncWrapVoid(n, p -> p.locale()._saaj.soapNode_setValue(node, value));
    }

    public static SOAPElement _soapNode_getParentElement(Dom n) {
        org.apache.xmlbeans.impl.soap.Node node = (org.apache.xmlbeans.impl.soap.Node) n;
        return syncWrap(n, p -> p.locale()._saaj.soapNode_getParentElement(node));
    }

    public static void _soapNode_setParentElement(Dom n, SOAPElement p) {
        org.apache.xmlbeans.impl.soap.Node node = (org.apache.xmlbeans.impl.soap.Node) n;
        syncWrapVoid(n, x -> x.locale()._saaj.soapNode_setParentElement(node, p));
    }

    //
    // Soap Element
    //

    public static void _soapElement_removeContents(Dom d) {
        SOAPElement se = (SOAPElement) d;
        syncWrapVoid(d, x -> x.locale()._saaj.soapElement_removeContents(se));
    }

    public static String _soapElement_getEncodingStyle(Dom d) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_getEncodingStyle(se));
    }

    public static void _soapElement_setEncodingStyle(Dom d, String encodingStyle) {
        SOAPElement se = (SOAPElement) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapElement_setEncodingStyle(se, encodingStyle));
    }

    public static boolean _soapElement_removeNamespaceDeclaration(Dom d, String prefix) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_removeNamespaceDeclaration(se, prefix));
    }

    public static Iterator<Name> _soapElement_getAllAttributes(Dom d) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_getAllAttributes(se));
    }

    public static Iterator<SOAPElement> _soapElement_getChildElements(Dom d) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_getChildElements(se));
    }

    public static Iterator<String> _soapElement_getNamespacePrefixes(Dom d) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_getNamespacePrefixes(se));
    }

    public static SOAPElement _soapElement_addAttribute(Dom d, Name name, String value) throws SOAPException {
        SOAPElement se = (SOAPElement) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapElement_addAttribute(se, name, value));
    }

    public static SOAPElement _soapElement_addChildElement(Dom d, SOAPElement oldChild) throws SOAPException {
        SOAPElement se = (SOAPElement) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapElement_addChildElement(se, oldChild));
    }

    public static SOAPElement _soapElement_addChildElement(Dom d, Name name) throws SOAPException {
        SOAPElement se = (SOAPElement) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapElement_addChildElement(se, name));
    }

    public static SOAPElement _soapElement_addChildElement(Dom d, String localName) throws SOAPException {
        SOAPElement se = (SOAPElement) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapElement_addChildElement(se, localName));
    }

    public static SOAPElement _soapElement_addChildElement(Dom d, String localName, String prefix) throws SOAPException {
        SOAPElement se = (SOAPElement) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapElement_addChildElement(se, localName, prefix));
    }

    public static SOAPElement _soapElement_addChildElement(Dom d, String localName, String prefix, String uri) throws SOAPException {
        SOAPElement se = (SOAPElement) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapElement_addChildElement(se, localName, prefix, uri));
    }

    public static SOAPElement _soapElement_addNamespaceDeclaration(Dom d, String prefix, String uri) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_addNamespaceDeclaration(se, prefix, uri));
    }

    public static SOAPElement _soapElement_addTextNode(Dom d, String data) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_addTextNode(se, data));
    }

    public static String _soapElement_getAttributeValue(Dom d, Name name) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_getAttributeValue(se, name));
    }

    public static Iterator<SOAPElement> _soapElement_getChildElements(Dom d, Name name) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_getChildElements(se, name));
    }

    public static Name _soapElement_getElementName(Dom d) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_getElementName(se));
    }

    public static String _soapElement_getNamespaceURI(Dom d, String prefix) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_getNamespaceURI(se, prefix));
    }

    public static Iterator<String> _soapElement_getVisibleNamespacePrefixes(Dom d) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_getVisibleNamespacePrefixes(se));
    }

    public static boolean _soapElement_removeAttribute(Dom d, Name name) {
        SOAPElement se = (SOAPElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapElement_removeAttribute(se, name));
    }

    //
    // Soap Envelope
    //

    public static SOAPBody _soapEnvelope_addBody(Dom d) throws SOAPException {
        SOAPEnvelope se = (SOAPEnvelope) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapEnvelope_addBody(se));
    }

    public static SOAPBody _soapEnvelope_getBody(Dom d) throws SOAPException {
        SOAPEnvelope se = (SOAPEnvelope) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapEnvelope_getBody(se));
    }

    public static SOAPHeader _soapEnvelope_getHeader(Dom d) throws SOAPException {
        SOAPEnvelope se = (SOAPEnvelope) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapEnvelope_getHeader(se));
    }

    public static SOAPHeader _soapEnvelope_addHeader(Dom d) throws SOAPException {
        SOAPEnvelope se = (SOAPEnvelope) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapEnvelope_addHeader(se));
    }

    public static Name _soapEnvelope_createName(Dom d, String localName) {
        SOAPEnvelope se = (SOAPEnvelope) d;
        return syncWrap(d, p -> p.locale()._saaj.soapEnvelope_createName(se, localName));
    }

    public static Name _soapEnvelope_createName(Dom d, String localName, String prefix, String namespaceURI) {
        SOAPEnvelope se = (SOAPEnvelope) d;
        return syncWrap(d, p -> p.locale()._saaj.soapEnvelope_createName(se, localName, prefix, namespaceURI));
    }

    //
    // Soap Header
    //

    public static Iterator<SOAPHeaderElement> soapHeader_examineAllHeaderElements(Dom d) {
        SOAPHeader sh = (SOAPHeader) d;
        return syncWrap(d, p -> p.locale()._saaj.soapHeader_examineAllHeaderElements(sh));
    }

    public static Iterator<SOAPHeaderElement> soapHeader_extractAllHeaderElements(Dom d) {
        SOAPHeader sh = (SOAPHeader) d;
        return syncWrap(d, p -> p.locale()._saaj.soapHeader_extractAllHeaderElements(sh));
    }

    public static Iterator<SOAPHeaderElement> soapHeader_examineHeaderElements(Dom d, String actor) {
        SOAPHeader sh = (SOAPHeader) d;
        return syncWrap(d, p -> p.locale()._saaj.soapHeader_examineHeaderElements(sh, actor));
    }

    public static Iterator<SOAPHeaderElement> soapHeader_examineMustUnderstandHeaderElements(Dom d, String mustUnderstandString) {
        SOAPHeader sh = (SOAPHeader) d;
        return syncWrap(d, p -> p.locale()._saaj.soapHeader_examineMustUnderstandHeaderElements(sh, mustUnderstandString));
    }

    public static Iterator<SOAPHeaderElement> soapHeader_extractHeaderElements(Dom d, String actor) {
        SOAPHeader sh = (SOAPHeader) d;
        return syncWrap(d, p -> p.locale()._saaj.soapHeader_extractHeaderElements(sh, actor));
    }

    public static SOAPHeaderElement soapHeader_addHeaderElement(Dom d, Name name) {
        SOAPHeader sh = (SOAPHeader) d;
        return syncWrap(d, p -> p.locale()._saaj.soapHeader_addHeaderElement(sh, name));
    }

    //
    // Soap Body
    //

    public static boolean soapBody_hasFault(Dom d) {
        SOAPBody sb = (SOAPBody) d;
        return syncWrap(d, p -> p.locale()._saaj.soapBody_hasFault(sb));
    }

    public static SOAPFault soapBody_addFault(Dom d) throws SOAPException {
        SOAPBody sb = (SOAPBody) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapBody_addFault(sb));
    }

    public static SOAPFault soapBody_getFault(Dom d) {
        SOAPBody sb = (SOAPBody) d;
        return syncWrap(d, p -> p.locale()._saaj.soapBody_getFault(sb));
    }

    public static SOAPBodyElement soapBody_addBodyElement(Dom d, Name name) {
        SOAPBody sb = (SOAPBody) d;
        return syncWrap(d, p -> p.locale()._saaj.soapBody_addBodyElement(sb, name));
    }

    public static SOAPBodyElement soapBody_addDocument(Dom d, Document document) {
        SOAPBody sb = (SOAPBody) d;
        return syncWrap(d, p -> p.locale()._saaj.soapBody_addDocument(sb, document));
    }

    public static SOAPFault soapBody_addFault(Dom d, Name name, String s) throws SOAPException {
        SOAPBody sb = (SOAPBody) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapBody_addFault(sb, name, s));
    }

    public static SOAPFault soapBody_addFault(Dom d, Name faultCode, String faultString, java.util.Locale locale) throws SOAPException {
        SOAPBody sb = (SOAPBody) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapBody_addFault(sb, faultCode, faultString, locale));
    }

    //
    // Soap Fault
    //

    public static void soapFault_setFaultString(Dom d, String faultString) {
        SOAPFault sf = (SOAPFault) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapFault_setFaultString(sf, faultString));
    }

    public static void soapFault_setFaultString(Dom d, String faultString, java.util.Locale locale) {
        SOAPFault sf = (SOAPFault) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapFault_setFaultString(sf, faultString, locale));
    }

    public static void soapFault_setFaultCode(Dom d, Name faultCodeName) throws SOAPException {
        SOAPFault sf = (SOAPFault) d;
        syncWrapEx(d, () -> {
            d.locale()._saaj.soapFault_setFaultCode(sf, faultCodeName);
            return null;
        });
    }

    public static void soapFault_setFaultActor(Dom d, String faultActorString) {
        SOAPFault sf = (SOAPFault) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapFault_setFaultActor(sf, faultActorString));
    }

    public static String soapFault_getFaultActor(Dom d) {
        SOAPFault sf = (SOAPFault) d;
        return syncWrap(d, p -> p.locale()._saaj.soapFault_getFaultActor(sf));
    }

    public static String soapFault_getFaultCode(Dom d) {
        SOAPFault sf = (SOAPFault) d;
        return syncWrap(d, p -> p.locale()._saaj.soapFault_getFaultCode(sf));
    }

    public static void soapFault_setFaultCode(Dom d, String faultCode) throws SOAPException {
        SOAPFault sf = (SOAPFault) d;
        syncWrapEx(d, () -> {
            d.locale()._saaj.soapFault_setFaultCode(sf, faultCode);
            return null;
        });
    }

    public static java.util.Locale soapFault_getFaultStringLocale(Dom d) {
        SOAPFault sf = (SOAPFault) d;
        return syncWrap(d, p -> p.locale()._saaj.soapFault_getFaultStringLocale(sf));
    }

    public static Name soapFault_getFaultCodeAsName(Dom d) {
        SOAPFault sf = (SOAPFault) d;
        return syncWrap(d, p -> p.locale()._saaj.soapFault_getFaultCodeAsName(sf));
    }

    public static String soapFault_getFaultString(Dom d) {
        SOAPFault sf = (SOAPFault) d;
        return syncWrap(d, p -> p.locale()._saaj.soapFault_getFaultString(sf));
    }

    public static Detail soapFault_addDetail(Dom d) throws SOAPException {
        SOAPFault sf = (SOAPFault) d;
        return syncWrapEx(d, () -> d.locale()._saaj.soapFault_addDetail(sf));
    }

    public static Detail soapFault_getDetail(Dom d) {
        SOAPFault sf = (SOAPFault) d;
        return syncWrap(d, p -> p.locale()._saaj.soapFault_getDetail(sf));
    }

    //
    // Soap Header Element
    //

    public static void soapHeaderElement_setMustUnderstand(Dom d, boolean mustUnderstand) {
        SOAPHeaderElement she = (SOAPHeaderElement) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapHeaderElement_setMustUnderstand(she, mustUnderstand));
    }

    public static boolean soapHeaderElement_getMustUnderstand(Dom d) {
        SOAPHeaderElement she = (SOAPHeaderElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapHeaderElement_getMustUnderstand(she));
    }

    public static void soapHeaderElement_setActor(Dom d, String actor) {
        SOAPHeaderElement she = (SOAPHeaderElement) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapHeaderElement_setActor(she, actor));
    }

    public static String soapHeaderElement_getActor(Dom d) {
        SOAPHeaderElement she = (SOAPHeaderElement) d;
        return syncWrap(d, p -> p.locale()._saaj.soapHeaderElement_getActor(she));
    }

    //
    // Soap Header Element
    //

    public static DetailEntry detail_addDetailEntry(Dom d, Name name) {
        Detail detail = (Detail) d;
        return syncWrap(d, p -> p.locale()._saaj.detail_addDetailEntry(detail, name));
    }

    public static Iterator<DetailEntry> detail_getDetailEntries(Dom d) {
        Detail detail = (Detail) d;
        return syncWrap(d, p -> p.locale()._saaj.detail_getDetailEntries(detail));
    }

    //
    // Soap Header Element
    //

    public static void _soapPart_removeAllMimeHeaders(Dom d) {
        SOAPPart sp = (SOAPPart) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapPart_removeAllMimeHeaders(sp));
    }

    public static void _soapPart_removeMimeHeader(Dom d, String name) {
        SOAPPart sp = (SOAPPart) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapPart_removeMimeHeader(sp, name));
    }

    public static Iterator<MimeHeader> _soapPart_getAllMimeHeaders(Dom d) {
        SOAPPart sp = (SOAPPart) d;
        return syncWrap(d, p -> p.locale()._saaj.soapPart_getAllMimeHeaders(sp));
    }

    public static SOAPEnvelope _soapPart_getEnvelope(Dom d) {
        SOAPPart sp = (SOAPPart) d;
        return syncWrap(d, p -> p.locale()._saaj.soapPart_getEnvelope(sp));
    }

    public static Source _soapPart_getContent(Dom d) {
        SOAPPart sp = (SOAPPart) d;
        return syncWrap(d, p -> p.locale()._saaj.soapPart_getContent(sp));
    }

    public static void _soapPart_setContent(Dom d, Source source) {
        SOAPPart sp = (SOAPPart) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapPart_setContent(sp, source));
    }

    public static String[] _soapPart_getMimeHeader(Dom d, String name) {
        SOAPPart sp = (SOAPPart) d;
        return syncWrap(d, p -> p.locale()._saaj.soapPart_getMimeHeader(sp, name));
    }

    public static void _soapPart_addMimeHeader(Dom d, String name, String value) {
        SOAPPart sp = (SOAPPart) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapPart_addMimeHeader(sp, name, value));
    }

    public static void _soapPart_setMimeHeader(Dom d, String name, String value) {
        SOAPPart sp = (SOAPPart) d;
        syncWrapVoid(d, p -> p.locale()._saaj.soapPart_setMimeHeader(sp, name, value));
    }

    public static Iterator<MimeHeader> _soapPart_getMatchingMimeHeaders(Dom d, String[] names) {
        SOAPPart sp = (SOAPPart) d;
        return syncWrap(d, p -> p.locale()._saaj.soapPart_getMatchingMimeHeaders(sp, names));
    }

    public static Iterator<MimeHeader> _soapPart_getNonMatchingMimeHeaders(Dom d, String[] names) {
        SOAPPart sp = (SOAPPart) d;
        return syncWrap(d, p -> p.locale()._saaj.soapPart_getNonMatchingMimeHeaders(sp, names));
    }

    //
    // Saaj callback
    //

    private static class SaajData {
        Object _obj;
    }

    public static void saajCallback_setSaajData(Dom d, Object o) {
        syncWrapVoid(d, p -> impl_saajCallback_setSaajData(p, o));
    }

    public static void impl_saajCallback_setSaajData(Dom d, Object o) {
        Locale l = d.locale();

        Cur c = l.tempCur();

        c.moveToDom(d);

        SaajData sd = null;

        if (o != null) {
            sd = (SaajData) c.getBookmark(SaajData.class);

            if (sd == null) {
                sd = new SaajData();
            }

            sd._obj = o;
        }

        c.setBookmark(SaajData.class, sd);

        c.release();
    }

    public static Object saajCallback_getSaajData(Dom d) {
        return syncWrap(d, DomImpl::impl_saajCallback_getSaajData);
    }

    public static Object impl_saajCallback_getSaajData(Dom d) {
        Locale l = d.locale();

        Cur c = l.tempCur();

        c.moveToDom(d);

        SaajData sd = (SaajData) c.getBookmark(SaajData.class);

        Object o = sd == null ? null : sd._obj;

        c.release();

        return o;
    }

    public static Element saajCallback_createSoapElement(Dom d, QName name, QName parentName) {
        return syncWrap(d, p -> impl_saajCallback_createSoapElement(p, name, parentName));
    }

    public static Element impl_saajCallback_createSoapElement(Dom d, QName name, QName parentName) {
        Cur c = d.locale().tempCur();
        c.createElement(name, parentName);
        Dom e = c.getDom();
        c.release();
        return (Element) e;
    }

    public static Element saajCallback_importSoapElement(Dom d, Element elem, boolean deep, QName parentName) {
        return syncWrap(d, p -> impl_saajCallback_importSoapElement(p, elem, deep, parentName));
    }

    @SuppressWarnings("unused")
    public static Element impl_saajCallback_importSoapElement(Dom d, Element elem, boolean deep, QName parentName) {
        // TODO -- need to rewrite DomImpl.document_importNode to use an Xcur
        // to create the new tree.  Then, I can pass the parentName to the new
        // fcn and use it to create the correct root parent

        throw new RuntimeException("Not impl");
    }


    public static Text saajCallback_ensureSoapTextNode(Dom d) {
        return syncWrap(d, DomImpl::impl_saajCallback_ensureSoapTextNode);
    }

    public static Text impl_saajCallback_ensureSoapTextNode(Dom d) {
        // if (!(d instanceof Text)) {
        //     Xcur x = d.tempCur();
        //     x.moveTo
        //     x.release();
        // }
        // return (Text) d;

        return null;
    }

    public static class DomLevel3NotImplemented extends RuntimeException {
        DomLevel3NotImplemented() {
            super("DOM Level 3 Not implemented");
        }
    }


    private interface WrapSoapEx<T> {
        T get() throws SOAPException;
    }

    private static <T> T syncWrap(Dom d, Function<Dom, T> inner) {
        return syncWrapHelper(d.locale(), true, () -> inner.apply(d));
    }

    private static <T> T syncWrapNoEnter(Dom d, Function<Dom, T> inner) {
        return syncWrapHelper(d.locale(), false, () -> inner.apply(d));
    }

    private static void syncWrapVoid(Dom d, Consumer<Dom> inner) {
        syncWrapHelper(d.locale(), true, () -> {
            inner.accept(d);
            return null;
        });
    }

    private static <T> T syncWrapEx(Dom d, WrapSoapEx<T> inner) throws SOAPException {
        return syncWrapHelperEx(d.locale(), true, inner);
    }

    private static <T> T syncWrapHelper(Locale l, boolean enter, Supplier<T> inner) {
        if (l.noSync()) {
            return syncWrapHelper2(l, enter, inner);
        } else {
            synchronized (l) {
                return syncWrapHelper2(l, enter, inner);
            }
        }
    }

    private static <T> T syncWrapHelper2(Locale l, boolean enter, Supplier<T> inner) {
        if (enter) {
            l.enter();
        }
        try {
            return inner.get();
        } finally {
            if (enter) {
                l.exit();
            }
        }
    }

    private static <T> T syncWrapHelperEx(Locale l, boolean enter, WrapSoapEx<T> inner) throws SOAPException {
        if (l.noSync()) {
            return syncWrapHelperEx2(l, enter, inner);
        } else {
            synchronized (l) {
                return syncWrapHelperEx2(l, enter, inner);
            }
        }
    }

    private static <T> T syncWrapHelperEx2(Locale l, boolean enter, WrapSoapEx<T> inner) throws SOAPException {
        if (enter) {
            l.enter();
        }
        try {
            return inner.get();
        } finally {
            if (enter) {
                l.exit();
            }
        }
    }


}

