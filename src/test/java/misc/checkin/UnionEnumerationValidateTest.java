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
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnionEnumerationValidateTest {

    // The first member type that accepts a lexical value gives a union its value,
    // so '1' is the xs:int 1 here and is not in the enumeration.
    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:t='urn:t' " +
        "targetNamespace='urn:t' elementFormDefault='qualified'>" +
        "  <xs:simpleType name='flag'>" +
        "    <xs:restriction>" +
        "      <xs:simpleType><xs:union memberTypes='xs:int xs:boolean'/></xs:simpleType>" +
        "      <xs:enumeration value='true'/>" +
        "      <xs:enumeration value='5'/>" +
        "    </xs:restriction>" +
        "  </xs:simpleType>" +
        "  <xs:simpleType name='ints'><xs:list itemType='xs:int'/></xs:simpleType>" +
        "  <xs:simpleType name='listOrFlag'>" +
        "    <xs:restriction>" +
        "      <xs:simpleType><xs:union memberTypes='t:ints xs:boolean'/></xs:simpleType>" +
        "      <xs:enumeration value='1 2'/>" +
        "      <xs:enumeration value='true'/>" +
        "    </xs:restriction>" +
        "  </xs:simpleType>" +
        "  <xs:element name='flag' type='t:flag'/>" +
        "  <xs:element name='listOrFlag' type='t:listOrFlag'/>" +
        "  <xs:element name='e'>" +
        "    <xs:complexType><xs:attribute name='flag' type='t:flag'/></xs:complexType>" +
        "  </xs:element>" +
        "</xs:schema>";

    private static SchemaTypeLoader loader;

    @BeforeAll
    static void compileSchema() throws Exception {
        loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)});
    }

    private static boolean validates(String body) throws Exception {
        XmlObject doc = loader.parse(body, null, null);
        return doc.validate();
    }

    @Test
    void laterMemberDoesNotSatisfyEnumeration() throws Exception {
        assertFalse(validates("<t:flag xmlns:t='urn:t'>1</t:flag>"));
        assertFalse(validates("<t:e xmlns:t='urn:t' flag='1'/>"));
        assertFalse(validates("<t:listOrFlag xmlns:t='urn:t'>1</t:listOrFlag>"));
    }

    @Test
    void enumeratedValuesStillValidate() throws Exception {
        assertTrue(validates("<t:flag xmlns:t='urn:t'>true</t:flag>"));
        assertTrue(validates("<t:flag xmlns:t='urn:t'>05</t:flag>"));
        assertTrue(validates("<t:e xmlns:t='urn:t' flag='true'/>"));
        assertTrue(validates("<t:listOrFlag xmlns:t='urn:t'>1  2</t:listOrFlag>"));
        assertTrue(validates("<t:listOrFlag xmlns:t='urn:t'>true</t:listOrFlag>"));
        assertFalse(validates("<t:flag xmlns:t='urn:t'>false</t:flag>"));
        assertFalse(validates("<t:flag xmlns:t='urn:t'>6</t:flag>"));
    }
}
