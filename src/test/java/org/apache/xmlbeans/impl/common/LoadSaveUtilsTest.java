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

package org.apache.xmlbeans.impl.common;

import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadSaveUtilsTest {

    @Test
    void xmlText2GenericDom() throws Exception {
        String xml = "<r a='1'><c>text</c><!--x--></r>";
        Document doc = LoadSaveUtils.xmlText2GenericDom(
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), DocumentHelper.createDocument());
        assertEquals("r", doc.getDocumentElement().getNodeName());
        assertEquals("1", doc.getDocumentElement().getAttribute("a"));
        assertEquals("text", doc.getDocumentElement().getTextContent());
        // Sax2Dom is registered as the lexical handler, so comments are kept
        Node last = doc.getDocumentElement().getLastChild();
        assertEquals(Node.COMMENT_NODE, last.getNodeType());
        assertEquals("x", last.getNodeValue());
    }

    // xmlText2GenericDom must not resolve external entities, like every other
    // XmlBeans parse path (SAXHelper.newXMLReader / DocumentHelper).
    @Test
    void externalEntityIsNotResolved(@TempDir Path dir) throws Exception {
        Path secret = dir.resolve("secret.txt");
        Files.write(secret, "SECRET".getBytes(StandardCharsets.UTF_8));
        String xml = "<!DOCTYPE r [<!ENTITY x SYSTEM '" + secret.toUri() + "'>]><r>&x;</r>";
        Document doc = LoadSaveUtils.xmlText2GenericDom(
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), DocumentHelper.createDocument());
        assertEquals("", doc.getDocumentElement().getTextContent());
    }

    @Test
    void namespacesAreKept() throws Exception {
        Document doc = LoadSaveUtils.xmlText2GenericDom(
            stream("<p:r xmlns:p='urn:p' p:a='1'><c xmlns='urn:d'/></p:r>"), DocumentHelper.createDocument());
        Element r = doc.getDocumentElement();
        assertEquals("urn:p", r.getNamespaceURI());
        assertEquals("r", r.getLocalName());
        assertEquals("1", r.getAttributeNS("urn:p", "a"));
        assertEquals("urn:d", ((Element) r.getFirstChild()).getNamespaceURI());
    }

    @Test
    void internalEntityIsExpanded() throws Exception {
        Document doc = LoadSaveUtils.xmlText2GenericDom(
            stream("<!DOCTYPE r [<!ENTITY x 'expanded'>]><r>&x;</r>"), DocumentHelper.createDocument());
        assertEquals("expanded", doc.getDocumentElement().getTextContent());
    }

    @Test
    void malformedXmlThrows() {
        assertThrows(SAXParseException.class, () -> LoadSaveUtils.xmlText2GenericDom(
            stream("<r><c></r>"), DocumentHelper.createDocument()));
    }

    // the XmlOptions default limit (2048) applies, not the parser's own default
    @Test
    void defaultEntityExpansionLimitIsApplied() {
        String xml = "<!DOCTYPE r [<!ENTITY a 'aaaaaaaaaa'>"
            + "<!ENTITY b '&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;'>"
            + "<!ENTITY c '&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;'>"
            + "<!ENTITY d '&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;'>]><r>&d;&d;&d;</r>";
        assertThrows(SAXParseException.class, () -> LoadSaveUtils.xmlText2GenericDom(
            stream(xml), DocumentHelper.createDocument()));
    }

    @Test
    void externalDtdIsNotLoaded(@TempDir Path dir) throws Exception {
        Path dtd = dir.resolve("r.dtd");
        Files.write(dtd, "<!ATTLIST r a CDATA 'SECRET'>".getBytes(StandardCharsets.UTF_8));
        String xml = "<!DOCTYPE r SYSTEM '" + dtd.toUri() + "'><r/>";
        Document doc = LoadSaveUtils.xmlText2GenericDom(stream(xml), DocumentHelper.createDocument());
        assertFalse(doc.getDocumentElement().hasAttribute("a"));
    }

    @Test
    void externalParameterEntityIsNotResolved(@TempDir Path dir) throws Exception {
        Path ent = dir.resolve("p.ent");
        Files.write(ent, "<!ATTLIST r a CDATA 'SECRET'>".getBytes(StandardCharsets.UTF_8));
        String xml = "<!DOCTYPE r [<!ENTITY % p SYSTEM '" + ent.toUri() + "'> %p;]><r/>";
        Document doc = LoadSaveUtils.xmlText2GenericDom(stream(xml), DocumentHelper.createDocument());
        assertFalse(doc.getDocumentElement().hasAttribute("a"));
    }

    @Test
    void nullOptionsUsesDefaults() throws Exception {
        Document doc = LoadSaveUtils.xmlText2GenericDom(stream("<r>text</r>"), DocumentHelper.createDocument(), null);
        assertEquals("text", doc.getDocumentElement().getTextContent());
    }

    @Test
    void disallowDocTypeDeclaration() {
        XmlOptions options = new XmlOptions().setDisallowDocTypeDeclaration(true);
        assertThrows(SAXParseException.class, () -> LoadSaveUtils.xmlText2GenericDom(
            stream("<!DOCTYPE r [<!ENTITY x 'y'>]><r>&x;</r>"), DocumentHelper.createDocument(), options));
    }

    @Test
    void entityExpansionLimit() {
        String xml = "<!DOCTYPE r [<!ENTITY a 'aaaaaaaaaa'>"
            + "<!ENTITY b '&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;'>"
            + "<!ENTITY c '&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;'>]><r>&c;</r>";
        XmlOptions options = new XmlOptions().setEntityExpansionLimit(10);
        assertThrows(SAXParseException.class, () -> LoadSaveUtils.xmlText2GenericDom(
            stream(xml), DocumentHelper.createDocument(), options));
    }

    @Test
    void entityResolverFromOptionsIsUsed() throws Exception {
        String xml = "<!DOCTYPE r [<!ENTITY x SYSTEM 'urn:test:x'>]><r>&x;</r>";
        XmlOptions options = new XmlOptions().setEntityResolver((publicId, systemId) ->
            "urn:test:x".equals(systemId) ? new InputSource(new StringReader("resolved")) : null);
        Document doc = LoadSaveUtils.xmlText2GenericDom(stream(xml), DocumentHelper.createDocument(), options);
        assertEquals("resolved", doc.getDocumentElement().getTextContent());
    }

    private static ByteArrayInputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
