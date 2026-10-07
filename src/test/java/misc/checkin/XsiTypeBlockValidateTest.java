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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsiTypeBlockValidateTest {

    private static final String UNION_MEMBER_INSTANCE =
        "<t:root xmlns:t='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " +
        "xmlns:xs='http://www.w3.org/2001/XMLSchema' xsi:type='xs:int'>5</t:root>";

    private static XmlObject parse(String xsd, String instance) throws Exception {
        SchemaTypeLoader loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});
        return loader.parse(instance, null, null);
    }

    @Test
    void unionMemberSubstitutionUnderElementBlock() throws Exception {
        // xs:int is validly derived from the union, and block only excludes extension
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
            "xmlns:t='urn:t' targetNamespace='urn:t'>" +
            "  <xs:simpleType name='u'><xs:union memberTypes='xs:int xs:string'/></xs:simpleType>" +
            "  <xs:element name='root' type='t:u' block='extension'/>" +
            "</xs:schema>";

        XmlObject doc = parse(xsd, UNION_MEMBER_INSTANCE);
        List<XmlError> errors = new ArrayList<>();
        assertTrue(doc.validate(new XmlOptions().setErrorListener(errors)), errors.toString());
    }

    @Test
    void unionMemberSubstitutionUnderTypeBlock() throws Exception {
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
            "xmlns:t='urn:t' targetNamespace='urn:t'>" +
            "  <xs:simpleType name='u'><xs:union memberTypes='xs:int xs:string'/></xs:simpleType>" +
            "  <xs:complexType name='ct' block='extension'>" +
            "    <xs:simpleContent><xs:extension base='t:u'/></xs:simpleContent>" +
            "  </xs:complexType>" +
            "  <xs:element name='root' type='t:ct'/>" +
            "</xs:schema>";

        XmlObject doc = parse(xsd, UNION_MEMBER_INSTANCE);
        assertDoesNotThrow(() -> doc.validate(new XmlOptions().setErrorListener(new ArrayList<>())));
    }

    @Test
    void blockedExtensionIsStillReported() throws Exception {
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
            "xmlns:t='urn:t' targetNamespace='urn:t'>" +
            "  <xs:complexType name='base'><xs:sequence/></xs:complexType>" +
            "  <xs:complexType name='ext'>" +
            "    <xs:complexContent><xs:extension base='t:base'><xs:sequence/></xs:extension></xs:complexContent>" +
            "  </xs:complexType>" +
            "  <xs:element name='root' type='t:base' block='extension'/>" +
            "</xs:schema>";

        XmlObject doc = parse(xsd,
            "<t:root xmlns:t='urn:t' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " +
            "xsi:type='t:ext'/>");

        List<XmlError> errors = new ArrayList<>();
        assertFalse(doc.validate(new XmlOptions().setErrorListener(errors)));
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("cvc-elt.4.3d", errors.get(0).getErrorCode());
    }
}
