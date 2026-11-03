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

public class SplitTextCollapseValidateTest {

    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:t='urn:t' " +
        "targetNamespace='urn:t' elementFormDefault='qualified'>" +
        "  <xs:element name='enum'>" +
        "    <xs:simpleType><xs:restriction base='xs:token'>" +
        "      <xs:enumeration value='a b'/>" +
        "    </xs:restriction></xs:simpleType>" +
        "  </xs:element>" +
        "  <xs:element name='nospace'>" +
        "    <xs:simpleType><xs:restriction base='xs:token'>" +
        "      <xs:pattern value='[^ ]*'/>" +
        "    </xs:restriction></xs:simpleType>" +
        "  </xs:element>" +
        "</xs:schema>";

    private static SchemaTypeLoader loader;

    @BeforeAll
    static void compileSchema() throws Exception {
        loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)});
    }

    private static boolean validates(String elem, String content) throws Exception {
        XmlObject doc = loader.parse("<t:" + elem + " xmlns:t='urn:t'>" + content + "</t:" + elem + ">", null, null);
        return doc.validate();
    }

    @Test
    void splitTextIsCollapsed() throws Exception {
        // a comment or pi splits the text into two chunks; a single tab, newline
        // or carriage return between them must still collapse to a space
        assertTrue(validates("enum", "a b"));
        assertTrue(validates("enum", "a\tb"));
        assertTrue(validates("enum", "a<!--c-->\tb"));
        assertTrue(validates("enum", "a<?pi x?>\nb"));
        assertTrue(validates("enum", "a&#13;<!--c-->b"));

        assertFalse(validates("nospace", "a b"));
        assertFalse(validates("nospace", "a<!--c-->\tb"));
        assertFalse(validates("nospace", "a<?pi x?>\nb"));
        assertTrue(validates("nospace", "a<!--c-->b"));
    }
}
