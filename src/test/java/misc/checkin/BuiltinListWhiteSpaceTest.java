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
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlENTITIES;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlIDREFS;
import org.apache.xmlbeans.XmlNMTOKENS;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.schema.XQuerySchemaTypeSystem;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BuiltinListWhiteSpaceTest {

    private static void load(String types) throws Exception {
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t'>" +
            types +
            "</xs:schema>";
        XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});
    }

    @Test
    void builtinListsCollapse() {
        // xs:NMTOKENS, xs:ENTITIES and xs:IDREFS are lists, whose whiteSpace is collapse
        assertEquals(SchemaType.WS_COLLAPSE, XmlNMTOKENS.type.getWhiteSpaceRule());
        assertEquals(SchemaType.WS_COLLAPSE, XmlENTITIES.type.getWhiteSpaceRule());
        assertEquals(SchemaType.WS_COLLAPSE, XmlIDREFS.type.getWhiteSpaceRule());
        assertEquals(SchemaType.WS_COLLAPSE, XQuerySchemaTypeSystem.ST_NMTOKENS.getWhiteSpaceRule());
        assertEquals(SchemaType.WS_COLLAPSE, XQuerySchemaTypeSystem.ST_ENTITIES.getWhiteSpaceRule());
        assertEquals(SchemaType.WS_COLLAPSE, XQuerySchemaTypeSystem.ST_IDREFS.getWhiteSpaceRule());
    }

    @Test
    void preserveRestrictionOfNmtokensIsRejected() {
        assertThrows(XmlException.class, () -> load(
            "<xs:simpleType name='loose'>" +
            "  <xs:restriction base='xs:NMTOKENS'>" +
            "    <xs:whiteSpace value='preserve'/>" +
            "  </xs:restriction>" +
            "</xs:simpleType>"));
    }
}
