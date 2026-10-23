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

import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.SchemaTypeLoader;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlIDREFS;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IdrefsMinLengthValidateTest {

    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:t='urn:t' " +
        "targetNamespace='urn:t' elementFormDefault='qualified'>" +
        "  <xs:element name='root'>" +
        "    <xs:complexType>" +
        "      <xs:sequence>" +
        "        <xs:element name='id' type='xs:ID' minOccurs='0' maxOccurs='unbounded'/>" +
        "        <xs:element name='refs' type='xs:IDREFS' minOccurs='0'/>" +
        "      </xs:sequence>" +
        "      <xs:attribute name='refs' type='xs:IDREFS'/>" +
        "    </xs:complexType>" +
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
    void idrefsHasBuiltinMinLength() {
        // xs:IDREFS, like xs:ENTITIES and xs:NMTOKENS, is defined with minLength=1
        XmlObject minLength = XmlIDREFS.type.getFacet(SchemaType.FACET_MIN_LENGTH);
        assertNotNull(minLength);
        assertEquals(1, ((SimpleValue) minLength).getIntValue());
        assertEquals(SchemaType.WS_COLLAPSE, XmlIDREFS.type.getWhiteSpaceRule());
    }

    @Test
    void emptyIdrefsIsReported() throws Exception {
        assertFalse(validates("<t:root xmlns:t='urn:t'><t:refs/></t:root>"));
        assertFalse(validates("<t:root xmlns:t='urn:t'><t:refs>  </t:refs></t:root>"));
        assertFalse(validates("<t:root xmlns:t='urn:t' refs=''/>"));
        assertFalse(validates("<t:root xmlns:t='urn:t' refs=' '/>"));
    }

    @Test
    void nonEmptyIdrefsStillValidate() throws Exception {
        assertTrue(validates("<t:root xmlns:t='urn:t'><t:id>a</t:id><t:id>b</t:id><t:refs> a  b </t:refs></t:root>"));
        assertTrue(validates("<t:root xmlns:t='urn:t' refs='a'><t:id>a</t:id></t:root>"));
    }
}
