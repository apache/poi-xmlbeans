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

import org.w3c.dom.CharacterData;
import org.w3c.dom.*;

import javax.xml.namespace.QName;
import java.io.PrintStream;

abstract class CharNode implements DomImpl.Dom, Node, CharacterData {

    public CharNode(Locale l) {
        assert l != null;

        _locale = l;
    }

    public QName getQName() {
        return null;
    }

    public Locale locale() {
        assert isValid();

        return _locale == null ? ((DomImpl.Dom) _src).locale() : _locale;
    }

    public void setChars(Object src, int off, int cch) {
        assert CharUtil.isValid(src, off, cch);
        assert (_locale != null || _src instanceof DomImpl.Dom);

        if (_locale == null) {
            _locale = ((DomImpl.Dom) _src).locale();
        }

        _src = src;
        _off = off;
        _cch = cch;
    }

    public DomImpl.Dom getDom() {
        assert isValid();

        if (_src instanceof DomImpl.Dom dom)
            return dom;

        return null;
    }

    public void setDom(DomImpl.Dom d) {
        assert d != null;

        _src = d;
        _locale = null;
    }

    public Cur tempCur() {
        assert isValid();

        if (!(_src instanceof DomImpl.Dom))
            return null;

        Cur c = locale().tempCur();
        c.moveToCharNode(this);

        return c;
    }

    private boolean isValid() {
        return (_src instanceof DomImpl.Dom) == (_locale == null);

    }

    public static boolean isOnList(CharNode nodes, CharNode node) {
        assert node != null;

        for (CharNode cn = nodes; cn != null; cn = cn._next) {
            if (cn == node) {
                return true;
            }
        }

        return false;
    }

    public static CharNode remove(CharNode nodes, CharNode node) {
        assert isOnList(nodes, node);

        if (nodes == node) {
            nodes = node._next;
        } else {
            node._prev._next = node._next;
        }

        if (node._next != null) {
            node._next._prev = node._prev;
        }

        node._prev = node._next = null;

        return nodes;
    }

    public static CharNode insertNode(CharNode nodes, CharNode newNode, CharNode before) {
        assert !isOnList(nodes, newNode);
        assert before == null || isOnList(nodes, before);
        assert newNode != null;
        assert newNode._prev == null && newNode._next == null;

        if (nodes == null) {
            assert before == null;
            nodes = newNode;
        } else if (nodes == before) {
            nodes._prev = newNode;
            newNode._next = nodes;
            nodes = newNode;
        } else {
            CharNode n = nodes;

            while (n._next != before)
                n = n._next;

            if ((newNode._next = n._next) != null)
                n._next._prev = newNode;

            newNode._prev = n;
            n._next = newNode;
        }

        return nodes;
    }

    public static CharNode appendNode(CharNode nodes, CharNode newNode) {
        return insertNode(nodes, newNode, null);
    }

    public static CharNode appendNodes(CharNode nodes, CharNode newNodes) {
        assert newNodes != null;
        assert newNodes._prev == null;

        if (nodes == null)
            return newNodes;

        CharNode n = nodes;

        while (n._next != null)
            n = n._next;

        n._next = newNodes;
        newNodes._prev = n;

        return nodes;
    }

    public static CharNode copyNodes(CharNode nodes, Object newSrc) {
        CharNode newNodes = null;

        for (CharNode n = null; nodes != null; nodes = nodes._next) {
            CharNode newNode;

            if (nodes instanceof TextNode)
                newNode = nodes.locale().createTextNode();
            else
                newNode = nodes.locale().createCdataNode();

            // How to deal with entity refs??

            newNode.setChars(newSrc, nodes._off, nodes._cch);

            if (newNodes == null)
                newNodes = newNode;

            if (n != null) {
                n._next = newNode;
                newNode._prev = n;
            }

            n = newNode;
        }

        return newNodes;
    }

    public boolean nodeCanHavePrefixUri() {
        return false;
    }

    public boolean isNodeAftertext() {
        assert _src instanceof Xobj :
            "this method is to only be used for nodes backed up by Xobjs";
        Xobj src = (Xobj) _src;
        return src._charNodesValue == null ? true :
            src._charNodesAfter == null ? false :
                CharNode.isOnList(src._charNodesAfter, this);
    }

    public void dump(PrintStream o, Object ref) {
        if (_src instanceof DomImpl.Dom dom)
            dom.dump(o, ref);
        else
            o.println("Lonely CharNode: \"" + CharUtil.getString(_src, _off, _cch) + "\"");
    }

    public void dump(PrintStream o) {
        dump(o, (Object) this);
    }

    public void dump() {
        dump(System.out);
    }

    public Node appendChild(Node newChild) {
        return DomImpl._node_appendChild(this, newChild);
    }

    public Node cloneNode(boolean deep) {
        return DomImpl._node_cloneNode(this, deep);
    }

    public NamedNodeMap getAttributes() {
        return null;
    }

    public NodeList getChildNodes() {
        return DomImpl._emptyNodeList;
    }

    public Node getParentNode() {
        return DomImpl._node_getParentNode(this);
    }

    public Node removeChild(Node oldChild) {
        return DomImpl._node_removeChild(this, oldChild);
    }

    public Node getFirstChild() {
        return null;
    }

    public Node getLastChild() {
        return null;
    }

    public String getLocalName() {
        return DomImpl._node_getLocalName(this);
    }

    public String getNamespaceURI() {
        return DomImpl._node_getNamespaceURI(this);
    }

    public Node getNextSibling() {
        return DomImpl._node_getNextSibling(this);
    }

    public String getNodeName() {
        return DomImpl._node_getNodeName(this);
    }

    public short getNodeType() {
        return DomImpl._node_getNodeType(this);
    }

    public String getNodeValue() {
        return DomImpl._node_getNodeValue(this);
    }

    public Document getOwnerDocument() {
        return DomImpl._node_getOwnerDocument(this);
    }

    public String getPrefix() {
        return DomImpl._node_getPrefix(this);
    }

    public Node getPreviousSibling() {
        return DomImpl._node_getPreviousSibling(this);
    }

    public boolean hasAttributes() {
        return false;
    }

    public boolean hasChildNodes() {
        return false;
    }

    public Node insertBefore(Node newChild, Node refChild) {
        return DomImpl._node_insertBefore(this, newChild, refChild);
    }

    public boolean isSupported(String feature, String version) {
        return DomImpl._node_isSupported(this, feature, version);
    }

    public void normalize() {
        DomImpl._node_normalize(this);
    }

    public Node replaceChild(Node newChild, Node oldChild) {
        return DomImpl._node_replaceChild(this, newChild, oldChild);
    }

    public void setNodeValue(String nodeValue) {
        DomImpl._node_setNodeValue(this, nodeValue);
    }

    public void setPrefix(String prefix) {
        DomImpl._node_setPrefix(this, prefix);
    }

    // DOM Level 3
    public Object getUserData(String key) {
        return DomImpl._node_getUserData(this, key);
    }

    public Object setUserData(String key, Object data, UserDataHandler handler) {
        return DomImpl._node_setUserData(this, key, data, handler);
    }

    public Object getFeature(String feature, String version) {
        return DomImpl._node_getFeature(this, feature, version);
    }

    public boolean isEqualNode(Node arg) {
        return DomImpl._node_isEqualNode(this, arg);
    }

    public boolean isSameNode(Node arg) {
        return DomImpl._node_isSameNode(this, arg);
    }

    public String lookupNamespaceURI(String prefix) {
        return DomImpl._node_lookupNamespaceURI(this, prefix);
    }

    public String lookupPrefix(String namespaceURI) {
        return DomImpl._node_lookupPrefix(this, namespaceURI);
    }

    public boolean isDefaultNamespace(String namespaceURI) {
        return DomImpl._node_isDefaultNamespace(this, namespaceURI);
    }

    public void setTextContent(String textContent) {
        DomImpl._node_setTextContent(this, textContent);
    }

    public String getTextContent() {
        return DomImpl._node_getTextContent(this);
    }

    public short compareDocumentPosition(Node other) {
        return DomImpl._node_compareDocumentPosition(this, other);
    }

    public String getBaseURI() {
        return DomImpl._node_getBaseURI(this);
    }

    public void appendData(String arg) {
        DomImpl._characterData_appendData(this, arg);
    }

    public void deleteData(int offset, int count) {
        DomImpl._characterData_deleteData(this, offset, count);
    }

    public String getData() {
        return DomImpl._characterData_getData(this);
    }

    public int getLength() {
        return DomImpl._characterData_getLength(this);
    }

    public void insertData(int offset, String arg) {
        DomImpl._characterData_insertData(this, offset, arg);
    }

    public void replaceData(int offset, int count, String arg) {
        DomImpl._characterData_replaceData(this, offset, count, arg);
    }

    public void setData(String data) {
        DomImpl._characterData_setData(this, data);
    }

    public String substringData(int offset, int count) {
        return DomImpl._characterData_substringData(this, offset, count);
    }

    Object getObject() {
        return _src;
    }

    private Locale _locale;

    CharNode _next;
    CharNode _prev;

    private Object _src;

    int _off;
    int _cch;
}

