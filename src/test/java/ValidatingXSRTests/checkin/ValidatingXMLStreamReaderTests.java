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
package ValidatingXSRTests.checkin;

import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.validator.ValidatingXMLStreamReader;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openuri.testNumerals.DocDocument;
import tools.util.JarUtil;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidatingXMLStreamReaderTests {
    private final static String URI_NUMERALS = "http://openuri.org/testNumerals";

    @ParameterizedTest
    @ValueSource(strings = {
        "xbean/xmlobject/enumtest.xml",
        "xbean/xmlobject/nameworld.xml",
        "xbean/misc/xmldsig-core-schema.xsd"
        //"xbean/misc/signature-example.xml"
    })
    void testValidateDoc(String doc) throws FileNotFoundException, XMLStreamException, Exception {
        File file = JarUtil.getResourceFromJarasFile(doc);
        ValidatingXMLStreamReader valXsr = new ValidatingXMLStreamReader();
        Collection<XmlError> errors = new ArrayList<>();
        XMLStreamReader xsr = XmlObject.Factory.parse(new FileInputStream(file)).newXMLStreamReader();

        valXsr.init(xsr, true, null /* validate an entire document */,
            XmlBeans.typeLoaderForClassLoader(ValidatingXMLStreamReader.class.getClassLoader()),
            null,
            errors);

        while (valXsr.hasNext()) {
            valXsr.next();
        }

        assertTrue(valXsr.isValid(), "File '" + file.getName() + "' is invalid.");
    }

    @Test
    void testValidateGlobalAtt1() throws XMLStreamException {
        XmlObject xo = XmlObject.Factory.newInstance();
        try (XmlCursor xc = xo.newCursor()) {
            xc.toNextToken();
            xc.insertAttributeWithValue("price", URI_NUMERALS, "23.5");
        }

        XMLStreamReader xsr = xo.newXMLStreamReader(new XmlOptions().setSaveOuter());
        Collection<XmlError> errors = new ArrayList<>();

        ValidatingXMLStreamReader valXsr = new ValidatingXMLStreamReader();
        valXsr.init(xsr, false, null,
            XmlBeans.typeLoaderForClassLoader(ValidatingXMLStreamReader.class.getClassLoader()),
            null, errors);

        while (valXsr.hasNext()) {
            valXsr.next();
        }

        assertTrue(valXsr.isValid(), "Global attribute validation is broken.");

    }

    @Test
    void testValidateGlobalAtt2() throws XMLStreamException {

        XMLStreamReader xsr = new TestXSR();
        Collection<XmlError> errors = new ArrayList<>();

        ValidatingXMLStreamReader valXsr = new ValidatingXMLStreamReader();
        valXsr.init(xsr, false, null,
            XmlBeans.typeLoaderForClassLoader(ValidatingXMLStreamReader.class.getClassLoader()),
            null, errors);

        while (valXsr.hasNext()) {
            valXsr.next();
        }

        assertTrue(valXsr.isValid(), "Global attribute validation 2 is broken.");
    }

    private static class TestXSR implements XMLStreamReader {
        private int _state = 0;

        public Object getProperty(String name) throws IllegalArgumentException {
            return null;
        }

        public int next() throws XMLStreamException {
            int state = _state;
            _state++;

            switch (state) {
                case 0:
                    return XMLEvent.START_DOCUMENT;

                case 1:
                    return XMLEvent.ATTRIBUTE;
            }
            return XMLEvent.END_DOCUMENT;
        }

        public void require(int type, String namespaceURI, String localName) throws XMLStreamException {
        }

        public String getElementText() throws XMLStreamException {
            return null;
        }

        public int nextTag() throws XMLStreamException {
            return next();
        }

        public boolean hasNext() throws XMLStreamException {
            return _state < 3;
        }

        public void close() throws XMLStreamException {
        }

        public String getNamespaceURI(String prefix) {
            if ("".equals(prefix)) {
                return URI_NUMERALS;
            }
            return null;
        }

        public boolean isStartElement() {
            return false;
        }

        public boolean isEndElement() {
            return false;
        }

        public boolean isCharacters() {
            return false;
        }

        public boolean isWhiteSpace() {
            return false;
        }

        public String getAttributeValue(String namespaceURI, String localName) {
            if (URI_NUMERALS.equals(namespaceURI) && "price".equals(localName)) {
                return "5";
            }
            throw new IllegalStateException();
        }

        public int getAttributeCount() {
            return 1;
        }

        public QName getAttributeName(int index) {
            if (index == 0) {
                return new QName(URI_NUMERALS, "price");
            }
            throw new IllegalStateException();
        }

        public String getAttributeNamespace(int index) {
            if (index == 0) {
                return URI_NUMERALS;
            }
            throw new IllegalStateException();
        }

        public String getAttributeLocalName(int index) {
            if (index == 0) {
                return "price";
            }
            throw new IllegalStateException();
        }

        public String getAttributePrefix(int index) {
            if (index == 0) {
                return "";
            }
            throw new IllegalStateException();
        }

        public String getAttributeType(int index) {
            if (index == 0) {
                return "CDATA";
            }
            throw new IllegalStateException();
        }

        public String getAttributeValue(int index) {
            if (index == 0) {
                return "8.7654321";
            }
            throw new IllegalStateException();
        }

        public boolean isAttributeSpecified(int index) {
            if (index == 0) {
                return true;
            }
            throw new IllegalStateException();
        }

        public int getNamespaceCount() {
            return 0;
        }

        public String getNamespacePrefix(int index) {
            return null;
        }

        public String getNamespaceURI(int index) {
            return null;
        }

        public NamespaceContext getNamespaceContext() {
            return null;
        }

        public int getEventType() {
            return XMLEvent.ATTRIBUTE;
        }

        public String getText() {
            return null;
        }

        public char[] getTextCharacters() {
            return new char[0];
        }

        public int getTextCharacters(int sourceStart, char[] target, int targetStart, int length) throws XMLStreamException {
            return 0;
        }

        public int getTextStart() {
            return 0;
        }

        public int getTextLength() {
            return 0;
        }

        public String getEncoding() {
            return null;
        }

        public boolean hasText() {
            return false;
        }

        public Location getLocation() {
            return null;
        }

        public QName getName() {
            return null;
        }

        public String getLocalName() {
            return null;
        }

        public boolean hasName() {
            return false;
        }

        public String getNamespaceURI() {
            return null;
        }

        public String getPrefix() {
            return null;
        }

        public String getVersion() {
            return null;
        }

        public boolean isStandalone() {
            return false;
        }

        public boolean standaloneSet() {
            return false;
        }

        public String getCharacterEncodingScheme() {
            return null;
        }

        public String getPITarget() {
            return null;
        }

        public String getPIData() {
            return null;
        }

    }

    @Test
    void testValidateContent1() throws XMLStreamException {
        XmlObject xo = XmlObject.Factory.newInstance();
        XmlCursor xc = xo.newCursor();
        xc.toNextToken();

        xc.insertElementWithText("int", URI_NUMERALS, "5");
        xc.insertElementWithText("float", URI_NUMERALS, "7.654321");

        XMLStreamReader xsr = xo.newXMLStreamReader();
        Collection<XmlError> errors = new ArrayList<>();

        ValidatingXMLStreamReader valXsr = new ValidatingXMLStreamReader();
        valXsr.init(xsr, true, DocDocument.Doc.type,
            XmlBeans.typeLoaderForClassLoader(ValidatingXMLStreamReader.class.getClassLoader()),
            null, errors);

        int depth = 0;

        while (valXsr.hasNext()) {
            if (valXsr.getEventType() == XMLEvent.END_ELEMENT && ++depth >= 2) {
                break;
            }
            valXsr.next();
        }
        assertTrue(valXsr.isValid(), "Content1 validation is broken.");
    }

    @Test
    void testValidateContent2() throws XMLStreamException {
        String doc = "<doc xmlns='" + URI_NUMERALS + "'><int>5</int><float>7.654321</float></doc>";

        XMLStreamReader xsr = null;
        try {
            xsr = XmlObject.Factory.parse(doc).newXMLStreamReader();
        } catch (XmlException e) {
            throw new XMLStreamException(e);
        }
        xsr.nextTag();

        Collection<XmlError> errors = new ArrayList<>();

        ValidatingXMLStreamReader valXsr = new ValidatingXMLStreamReader();
        valXsr.init(xsr, false, DocDocument.Doc.type,
            XmlBeans.typeLoaderForClassLoader(ValidatingXMLStreamReader.class.getClassLoader()),
            null, errors);

        int depth = 0;

        while (valXsr.hasNext()) {
            if (valXsr.next() == XMLEvent.END_ELEMENT && ++depth >= 2) {
                break;
            }
        }

        assertTrue(valXsr.isValid(), "Content2 validation is broken.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<count xmlns='urn:t'>5<?pi x?>5</count>",
        "<count xmlns='urn:t'>5<?pi x?><!--c-->5</count>",
        "<count xmlns='urn:t'>5<!--c-->5</count>"
    })
    void testTextSplitByProcessingInstruction(String doc) throws Exception {
        // a comment or pi between two text chunks contributes no character data,
        // so the element value is 55 and must fail maxInclusive=9; the validator
        // only checks the first TEXT event, so the chunks have to arrive as one
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t'>" +
            "  <xs:element name='count'>" +
            "    <xs:simpleType><xs:restriction base='xs:int'><xs:maxInclusive value='9'/></xs:restriction></xs:simpleType>" +
            "  </xs:element>" +
            "</xs:schema>";
        SchemaTypeLoader stl = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});

        XMLStreamReader xsr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(doc));
        Collection<XmlError> errors = new ArrayList<>();

        ValidatingXMLStreamReader valXsr = new ValidatingXMLStreamReader();
        valXsr.init(xsr, false, null, stl, null, errors);

        while (valXsr.hasNext()) {
            valXsr.next();
        }

        assertFalse(valXsr.isValid(), "Text split by a pi was validated in pieces: " + doc);
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("cvc-maxInclusive-valid", errors.iterator().next().getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<h xmlns='urn:t' xmlns:t='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='t:ext'><b/></h>",
        "<i xmlns='urn:t' xmlns:t='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='t:small'>3</i>",
        "<n xmlns='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:nil='true'/>"
    })
    void testXsiAttributesOnRootElement(String doc) throws Exception {
        // xsi:type and xsi:nil on the root element apply to the root element,
        // not to the document type the validator is started with
        XMLStreamReader xsr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(doc));
        Collection<XmlError> errors = new ArrayList<>();

        ValidatingXMLStreamReader valXsr = new ValidatingXMLStreamReader();
        valXsr.init(xsr, false, null, rootXsiLoader(), null, errors);

        while (valXsr.hasNext()) {
            valXsr.next();
        }

        assertTrue(valXsr.isValid(), errors.toString());
    }

    @Test
    void testXsiTypeOnRootElementIsValidated() throws Exception {
        String doc = "<i xmlns='urn:t' xmlns:t='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='t:small'>9</i>";
        XMLStreamReader xsr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(doc));
        Collection<XmlError> errors = new ArrayList<>();

        ValidatingXMLStreamReader valXsr = new ValidatingXMLStreamReader();
        valXsr.init(xsr, false, null, rootXsiLoader(), null, errors);

        while (valXsr.hasNext()) {
            valXsr.next();
        }

        assertFalse(valXsr.isValid());
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("cvc-maxInclusive-valid", errors.iterator().next().getErrorCode());
    }

    private static SchemaTypeLoader rootXsiLoader() throws XmlException {
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t' xmlns:t='urn:t' elementFormDefault='qualified'>" +
            "  <xs:complexType name='base'><xs:sequence><xs:element name='a' minOccurs='0'/></xs:sequence></xs:complexType>" +
            "  <xs:complexType name='ext'><xs:complexContent><xs:extension base='t:base'>" +
            "    <xs:sequence><xs:element name='b'/></xs:sequence>" +
            "  </xs:extension></xs:complexContent></xs:complexType>" +
            "  <xs:simpleType name='small'><xs:restriction base='xs:int'><xs:maxInclusive value='5'/></xs:restriction></xs:simpleType>" +
            "  <xs:element name='h' type='t:base'/>" +
            "  <xs:element name='i' type='xs:int'/>" +
            "  <xs:element name='n' type='xs:int' nillable='true'/>" +
            "</xs:schema>";
        return XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});
    }

    @Test
    void testEventsSeenAroundProcessingInstruction() throws Exception {
        // the reader passes events through to its caller, so packing text also
        // decides what the caller sees: a comment or pi after text is consumed
        // into the merged CHARACTERS event, one before any text is passed on
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t'>" +
            "  <xs:element name='v' type='xs:string'/>" +
            "</xs:schema>";
        SchemaTypeLoader stl = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});
        String doc = "<v xmlns='urn:t'><?lead x?>a<?pi x?><!--c-->b<?tail x?></v>";

        XMLStreamReader xsr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(doc));
        Collection<XmlError> errors = new ArrayList<>();

        ValidatingXMLStreamReader valXsr = new ValidatingXMLStreamReader();
        valXsr.init(xsr, false, null, stl, null, errors);

        List<String> events = new ArrayList<>();
        while (valXsr.hasNext()) {
            int evType = valXsr.next();
            switch (evType) {
                case XMLEvent.START_ELEMENT:
                    events.add("start " + valXsr.getLocalName());
                    break;
                case XMLEvent.END_ELEMENT:
                    events.add("end " + valXsr.getLocalName());
                    break;
                case XMLEvent.PROCESSING_INSTRUCTION:
                    events.add("pi " + valXsr.getPITarget());
                    break;
                case XMLEvent.CHARACTERS:
                    events.add("text " + valXsr.getText());
                    break;
                default:
                    events.add("event " + evType);
            }
        }

        assertEquals(Arrays.asList("start v", "pi lead", "text ab", "end v", "event " + XMLEvent.END_DOCUMENT),
            events);
        assertTrue(valXsr.isValid(), errors.toString());
    }
}
