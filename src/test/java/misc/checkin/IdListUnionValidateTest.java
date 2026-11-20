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
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlErrorCodes;
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

/**
 * ID and IDREF values reached through a list item type, a union member type or a
 * complex type with simple content belong in the ID/IDREF table like directly typed ones.
 */
public class IdListUnionValidateTest {

    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:t='urn:t' " +
        "targetNamespace='urn:t' elementFormDefault='qualified'>" +
        "  <xs:simpleType name='idList'><xs:list itemType='xs:ID'/></xs:simpleType>" +
        "  <xs:simpleType name='idUnion'><xs:union memberTypes='xs:int xs:ID'/></xs:simpleType>" +
        "  <xs:simpleType name='idUnionList'><xs:list itemType='t:idUnion'/></xs:simpleType>" +
        "  <xs:simpleType name='idrefList'><xs:list itemType='xs:IDREF'/></xs:simpleType>" +
        "  <xs:simpleType name='idrefUnion'><xs:union memberTypes='xs:int xs:IDREF'/></xs:simpleType>" +
        "  <xs:simpleType name='idrefsFirstUnion'><xs:union memberTypes='xs:IDREFS xs:int'/></xs:simpleType>" +
        "  <xs:complexType name='idrefContent'>" +
        "    <xs:simpleContent><xs:extension base='xs:IDREF'><xs:attribute name='n' type='xs:int'/></xs:extension></xs:simpleContent>" +
        "  </xs:complexType>" +
        "  <xs:element name='root'>" +
        "    <xs:complexType>" +
        "      <xs:choice minOccurs='0' maxOccurs='unbounded'>" +
        "        <xs:element name='id' type='xs:ID'/>" +
        "        <xs:element name='ids' type='t:idList'/>" +
        "        <xs:element name='idu' type='t:idUnion'/>" +
        "        <xs:element name='idul' type='t:idUnionList'/>" +
        "        <xs:element name='ref' type='xs:IDREF'/>" +
        "        <xs:element name='refs' type='t:idrefList'/>" +
        "        <xs:element name='refu' type='t:idrefUnion'/>" +
        "        <xs:element name='refsu' type='t:idrefsFirstUnion'/>" +
        "        <xs:element name='refc' type='t:idrefContent'/>" +
        "        <xs:element name='e'>" +
        "          <xs:complexType>" +
        "            <xs:attribute name='ids' type='t:idList'/>" +
        "            <xs:attribute name='idu' type='t:idUnion'/>" +
        "            <xs:attribute name='refs' type='t:idrefList'/>" +
        "            <xs:attribute name='refu' type='t:idrefUnion'/>" +
        "          </xs:complexType>" +
        "        </xs:element>" +
        "      </xs:choice>" +
        "    </xs:complexType>" +
        "  </xs:element>" +
        "</xs:schema>";

    private static SchemaTypeLoader loader;

    @BeforeAll
    static void compileSchema() throws Exception {
        loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)});
    }

    private static List<XmlError> validate(String content) throws Exception {
        XmlObject doc = loader.parse("<t:root xmlns:t='urn:t'>" + content + "</t:root>", null, null);
        List<XmlError> errors = new ArrayList<>();
        doc.validate(new XmlOptions().setErrorListener(errors));
        return errors;
    }

    private static void assertDuplicateId(String content) throws Exception {
        List<XmlError> errors = validate(content);
        assertEquals(1, errors.size(), errors.toString());
        assertEquals(XmlErrorCodes.ID_VALID$DUPLICATE, errors.get(0).getErrorCode());
    }

    private static void assertMissingId(String content) throws Exception {
        List<XmlError> errors = validate(content);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).getMessage().startsWith("ID not found for IDRef value"), errors.get(0).toString());
    }

    @Test
    void duplicateIdThroughListIsReported() throws Exception {
        assertDuplicateId("<t:ids>a b</t:ids><t:id>a</t:id>");
        assertDuplicateId("<t:ids>a a</t:ids>");
        assertDuplicateId("<t:ids>a b</t:ids><t:ids>c b</t:ids>");
        assertDuplicateId("<t:e ids='a b'/><t:id>b</t:id>");
    }

    @Test
    void duplicateIdThroughUnionIsReported() throws Exception {
        assertDuplicateId("<t:idu>a</t:idu><t:id>a</t:id>");
        assertDuplicateId("<t:idu>a</t:idu><t:idu>a</t:idu>");
        assertDuplicateId("<t:e idu='a'/><t:id>a</t:id>");
        assertDuplicateId("<t:idul>a 1 b</t:idul><t:id>b</t:id>");
        assertDuplicateId("<t:idul>a 1 a</t:idul>");
    }

    @Test
    void unionValueOfAnotherMemberIsNotAnId() throws Exception {
        assertTrue(validate("<t:idu>1</t:idu><t:idu>1</t:idu>").isEmpty());
        assertTrue(validate("<t:idul>1 1</t:idul>").isEmpty());
        assertTrue(validate("<t:refu>7</t:refu>").isEmpty());
        // 7 is not an NCName, so it is the xs:int member even though xs:IDREFS comes first
        assertTrue(validate("<t:refsu>7</t:refsu>").isEmpty());
        assertMissingId("<t:refsu>a</t:refsu>");
    }

    @Test
    void missingIdThroughListOrUnionIsReported() throws Exception {
        assertMissingId("<t:refs>a zz</t:refs><t:id>a</t:id>");
        assertMissingId("<t:refu>zz</t:refu>");
        assertMissingId("<t:e refs='a zz'/><t:id>a</t:id>");
        assertMissingId("<t:e refu='zz'/>");
    }

    @Test
    void idrefInComplexTypeWithSimpleContentResolves() throws Exception {
        assertTrue(validate("<t:id>a</t:id><t:refc n='1'>a</t:refc>").isEmpty());
        assertMissingId("<t:refc>zz</t:refc>");
    }

    @Test
    void resolvedReferencesStillValidate() throws Exception {
        assertTrue(validate("<t:ids>a b</t:ids><t:idu>c</t:idu><t:refs>a b</t:refs><t:refu>c</t:refu>" +
                            "<t:e refs='b a' refu='a'/>").isEmpty());
        assertTrue(validate("<t:id>a</t:id><t:id>b</t:id><t:ref>a</t:ref><t:refs>a b</t:refs>").isEmpty());
        assertFalse(validate("<t:id>a</t:id><t:id>a</t:id>").isEmpty());
    }
}
