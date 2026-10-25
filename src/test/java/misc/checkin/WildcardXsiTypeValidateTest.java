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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WildcardXsiTypeValidateTest {

    // u has no global declaration, so only its xsi:type can be used to assess it
    private static XmlObject parse(String processContents, String child) throws Exception {
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
            "xmlns:t='urn:t' targetNamespace='urn:t'>" +
            "  <xs:element name='root'><xs:complexType><xs:sequence>" +
            "    <xs:any namespace='##any' processContents='" + processContents + "'/>" +
            "  </xs:sequence></xs:complexType></xs:element>" +
            "  <xs:simpleType name='small'>" +
            "    <xs:restriction base='xs:int'><xs:maxInclusive value='5'/></xs:restriction>" +
            "  </xs:simpleType>" +
            "  <xs:complexType name='ct'><xs:sequence>" +
            "    <xs:element name='z' type='xs:int'/>" +
            "  </xs:sequence></xs:complexType>" +
            "</xs:schema>";

        SchemaTypeLoader loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});
        return loader.parse(
            "<t:root xmlns:t='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>" +
            child + "</t:root>", null, null);
    }

    private static List<XmlError> validate(XmlObject doc) {
        List<XmlError> errors = new ArrayList<>();
        doc.validate(new XmlOptions().setErrorListener(errors));
        return errors;
    }

    @ParameterizedTest
    @ValueSource(strings = {"lax", "strict"})
    void validXsiTypeContent(String processContents) throws Exception {
        assertTrue(validate(parse(processContents, "<u xsi:type='t:small'>3</u>")).isEmpty());
        assertTrue(validate(parse(processContents, "<u xsi:type='t:ct'><z>1</z></u>")).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lax", "strict"})
    void invalidXsiTypeContent(String processContents) throws Exception {
        List<XmlError> errors = validate(parse(processContents, "<u xsi:type='t:small'>9</u>"));
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("cvc-maxInclusive-valid", errors.get(0).getErrorCode());

        errors = validate(parse(processContents, "<u xsi:type='t:ct'><z>x</z></u>"));
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("decimal", errors.get(0).getErrorCode());

        errors = validate(parse(processContents, "<u xsi:type='t:missing'/>"));
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("cvc-elt.4.2", errors.get(0).getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lax", "strict"})
    void undeclaredWithoutXsiType(String processContents) throws Exception {
        XmlObject doc = parse(processContents, "<u>x</u>");
        if ("lax".equals(processContents)) {
            assertTrue(doc.validate());
        } else {
            List<XmlError> errors = validate(doc);
            assertFalse(errors.isEmpty());
            assertEquals("cvc-assess-elt.1.1.1.3.2", errors.get(0).getErrorCode());
        }
    }
}
