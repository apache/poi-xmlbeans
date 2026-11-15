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

public class SubstitutionGroupBlockValidateTest {

    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:t='urn:t' " +
        "targetNamespace='urn:t' elementFormDefault='qualified'>" +
        "  <xs:complexType name='base'><xs:sequence><xs:element name='a' type='xs:int'/></xs:sequence></xs:complexType>" +
        "  <xs:complexType name='ext'><xs:complexContent><xs:extension base='t:base'>" +
        "    <xs:sequence><xs:element name='b' type='xs:int'/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>" +
        "  <xs:complexType name='res'><xs:complexContent><xs:restriction base='t:base'>" +
        "    <xs:sequence><xs:element name='a' type='xs:byte'/></xs:sequence></xs:restriction></xs:complexContent></xs:complexType>" +
        "  <xs:complexType name='bbase' block='extension'><xs:sequence><xs:element name='a' type='xs:int'/></xs:sequence></xs:complexType>" +
        "  <xs:complexType name='bext'><xs:complexContent><xs:extension base='t:bbase'>" +
        "    <xs:sequence><xs:element name='b' type='xs:int'/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>" +
        "  <xs:element name='hE' type='t:base' block='extension'/>" +
        "  <xs:element name='mExt' type='t:ext' substitutionGroup='t:hE'/>" +
        "  <xs:element name='mRes' type='t:res' substitutionGroup='t:hE'/>" +
        "  <xs:element name='hR' type='t:base' block='restriction'/>" +
        "  <xs:element name='mR' type='t:res' substitutionGroup='t:hR'/>" +
        "  <xs:element name='hT' type='t:bbase'/>" +
        "  <xs:element name='mT' type='t:bext' substitutionGroup='t:hT'/>" +
        "  <xs:element name='root'><xs:complexType><xs:choice>" +
        "    <xs:element ref='t:hE'/><xs:element ref='t:hR'/><xs:element ref='t:hT'/>" +
        "  </xs:choice></xs:complexType></xs:element>" +
        "</xs:schema>";

    private static SchemaTypeLoader loader;

    @BeforeAll
    static void compile() throws Exception {
        loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)});
    }

    private static List<XmlError> validate(String content) throws Exception {
        XmlObject doc = loader.parse("<root xmlns='urn:t'>" + content + "</root>", null, null);
        List<XmlError> errors = new ArrayList<>();
        boolean valid = doc.validate(new XmlOptions().setErrorListener(errors));
        assertEquals(errors.isEmpty(), valid, errors.toString());
        return errors;
    }

    private static void assertBlocked(String content) throws Exception {
        List<XmlError> errors = validate(content);
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("cvc-particle.2.3.3b", errors.get(0).getErrorCode());
    }

    @Test
    void extensionMemberBlockedByHead() throws Exception {
        assertBlocked("<mExt><a>1</a><b>2</b></mExt>");
    }

    @Test
    void restrictionMemberBlockedByHead() throws Exception {
        assertBlocked("<mR><a>1</a></mR>");
    }

    @Test
    void extensionMemberBlockedByHeadType() throws Exception {
        assertBlocked("<mT><a>1</a><b>2</b></mT>");
    }

    @Test
    void unblockedMemberStillValid() throws Exception {
        // block='extension' does not exclude a member derived by restriction
        List<XmlError> errors = validate("<mRes><a>1</a></mRes>");
        assertTrue(errors.isEmpty(), errors.toString());
        assertFalse(validate("<mRes><a>1000</a></mRes>").isEmpty());
    }
}
