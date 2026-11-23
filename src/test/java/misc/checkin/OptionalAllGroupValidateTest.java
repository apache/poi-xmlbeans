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

package misc.checkin;

import org.apache.xmlbeans.SchemaTypeLoader;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptionalAllGroupValidateTest {

    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:t='urn:t' " +
        "targetNamespace='urn:t' elementFormDefault='qualified'>" +
        "  <xs:element name='root'><xs:complexType><xs:all minOccurs='0'>" +
        "    <xs:element name='a' type='xs:int'/>" +
        "    <xs:element name='b' type='xs:int'/>" +
        "    <xs:element name='c' type='xs:int' minOccurs='0'/>" +
        "  </xs:all></xs:complexType></xs:element>" +
        "  <xs:group name='g'><xs:all>" +
        "    <xs:element name='a' type='xs:int'/>" +
        "    <xs:element name='b' type='xs:int' minOccurs='0'/>" +
        "  </xs:all></xs:group>" +
        "  <xs:element name='groot'><xs:complexType><xs:group ref='t:g' minOccurs='0'/></xs:complexType></xs:element>" +
        "</xs:schema>";

    private static SchemaTypeLoader loader;

    @BeforeAll
    static void compile() throws Exception {
        loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)});
    }

    private static List<XmlError> validate(String xml) throws Exception {
        XmlObject doc = loader.parse(xml, null, null);
        List<XmlError> errors = new ArrayList<>();
        boolean valid = doc.validate(new XmlOptions().setErrorListener(errors));
        assertEquals(errors.isEmpty(), valid, errors.toString());
        return errors;
    }

    @Test
    void missingRequiredMember() throws Exception {
        assertFalse(validate("<root xmlns='urn:t'><a>1</a></root>").isEmpty());
        assertFalse(validate("<root xmlns='urn:t'><b>1</b><c>1</c></root>").isEmpty());
        assertFalse(validate("<root xmlns='urn:t'><c>1</c></root>").isEmpty());
        assertFalse(validate("<groot xmlns='urn:t'><b>1</b></groot>").isEmpty());
    }

    @Test
    void completeOrAbsentGroupStillValid() throws Exception {
        assertTrue(validate("<root xmlns='urn:t'/>").isEmpty());
        assertTrue(validate("<root xmlns='urn:t'><b>1</b><a>2</a></root>").isEmpty());
        assertTrue(validate("<root xmlns='urn:t'><c>1</c><b>1</b><a>2</a></root>").isEmpty());
        assertTrue(validate("<groot xmlns='urn:t'/>").isEmpty());
        assertTrue(validate("<groot xmlns='urn:t'><b>1</b><a>2</a></groot>").isEmpty());
    }
}
