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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsiAttributeValidateTest {

    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
        "xmlns:t='urn:t' targetNamespace='urn:t'>" +
        "  <xs:element name='root'>" +
        "    <xs:complexType><xs:attribute name='a' type='xs:int'/></xs:complexType>" +
        "  </xs:element>" +
        "  <xs:element name='open'>" +
        "    <xs:complexType><xs:anyAttribute processContents='skip'/></xs:complexType>" +
        "  </xs:element>" +
        "</xs:schema>";

    private static boolean validate(String instance, List<XmlError> errors) throws Exception {
        SchemaTypeLoader loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)});
        XmlObject doc = loader.parse(instance, null, null);
        return doc.validate(new XmlOptions().setErrorListener(errors));
    }

    @Test
    void undeclaredXsiAttributeIsReported() throws Exception {
        List<XmlError> errors = new ArrayList<>();
        assertFalse(validate(
            "<t:root xmlns:t='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " +
            "a='1' xsi:foo='x'/>", errors));
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("cvc-complex-type.3.2.1", errors.get(0).getErrorCode());
    }

    @Test
    void specialXsiAttributesAreStillAllowed() throws Exception {
        List<XmlError> errors = new ArrayList<>();
        assertTrue(validate(
            "<t:root xmlns:t='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " +
            "xsi:schemaLocation='urn:t t.xsd' xsi:noNamespaceSchemaLocation='n.xsd' a='1'/>",
            errors), errors.toString());
    }

    @Test
    void xsiAttributeMatchesWildcard() throws Exception {
        List<XmlError> errors = new ArrayList<>();
        assertTrue(validate(
            "<t:open xmlns:t='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:foo='x'/>",
            errors), errors.toString());
    }
}
