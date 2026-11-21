/*   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package misc.checkin;

import org.apache.xmlbeans.SchemaTypeLoader;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FixedQNameValidateTest {

    private static final String XSI = "xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'";

    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:t='urn:t' xmlns:p='urn:p' " +
        "targetNamespace='urn:t' elementFormDefault='qualified'>" +
        "  <xs:simpleType name='qname'><xs:restriction base='xs:QName'/></xs:simpleType>" +
        "  <xs:element name='qn' type='xs:QName' fixed='p:foo'/>" +
        "  <xs:element name='withAttr'>" +
        "    <xs:complexType><xs:attribute name='a' type='xs:QName' fixed='p:foo'/></xs:complexType>" +
        "  </xs:element>" +
        "</xs:schema>";

    private static SchemaTypeLoader loader;

    @BeforeAll
    static void compileSchema() throws Exception {
        loader = XmlBeans.loadXsd(SchemaDocument.Factory.parse(XSD));
    }

    private static boolean validates(String instance) throws Exception {
        return loader.parse(instance, null, null).validate();
    }

    @Test
    void fixedQNameElementValidates() throws Exception {
        // used to throw XmlValueOutOfRangeException out of validate(): the fixed
        // value comparison called newValue() with no namespace context pushed
        assertTrue(validates("<t:qn xmlns:t='urn:t' xmlns:p='urn:p'>p:foo</t:qn>"));
    }

    @Test
    void fixedQNameAttributeValidates() throws Exception {
        assertTrue(validates("<t:withAttr xmlns:t='urn:t' xmlns:p='urn:p' a='p:foo'/>"));
    }

    @Test
    void mismatchedFixedQNameIsReported() throws Exception {
        // a wrong value is reported as invalid, not raised as an exception
        assertFalse(validates("<t:qn xmlns:t='urn:t' xmlns:p='urn:p'>p:bar</t:qn>"));
        assertFalse(validates("<t:withAttr xmlns:t='urn:t' xmlns:p='urn:p' a='p:bar'/>"));
    }

    @Test
    void fixedQNameComparedByValueNotPrefix() throws Exception {
        // the schema's own prefix is never looked up in the instance: the fixed
        // namespace reached through a different instance prefix still matches,
        // with 'p' undeclared on the instance
        assertTrue(validates("<t:qn xmlns:t='urn:t' xmlns:z='urn:p'>z:foo</t:qn>"));
        assertTrue(validates("<t:withAttr xmlns:t='urn:t' xmlns:z='urn:p' a='z:foo'/>"));
        // rebinding the prefix to another namespace does not match the fixed value
        assertFalse(validates("<t:qn xmlns:t='urn:t' xmlns:p='urn:other'>p:foo</t:qn>"));
    }

    @Test
    void fixedQNameComparedAcrossXsiType() throws Exception {
        // xsi:type narrows the instance type, so the two sides of the comparison
        // come from different types: the compiled fixed value keeps the field's
        // declared xs:QName while the instance value is built from t:qname
        assertTrue(validates(
            "<t:qn xmlns:t='urn:t' " + XSI + " xmlns:z='urn:p' xsi:type='t:qname'>z:foo</t:qn>"));
        assertFalse(validates(
            "<t:qn xmlns:t='urn:t' " + XSI + " xmlns:z='urn:p' xsi:type='t:qname'>z:bar</t:qn>"));
    }
}
