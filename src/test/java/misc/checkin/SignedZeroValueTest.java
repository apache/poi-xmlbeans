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

import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.SchemaTypeLoader;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SignedZeroValueTest {

    private static final String XSD = "http://www.w3.org/2001/XMLSchema";

    private static XmlObject value(String localName, String lexical) {
        SchemaType type = XmlBeans.getBuiltinTypeSystem().findType(new QName(XSD, localName));
        return type.newValue(lexical);
    }

    private static SchemaTypeLoader loader(String facets, String type) throws Exception {
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
            "xmlns:t='urn:t' targetNamespace='urn:t' elementFormDefault='qualified'>" +
            "  <xs:simpleType name='s'>" +
            "    <xs:restriction base='" + type + "'>" + facets + "</xs:restriction>" +
            "  </xs:simpleType>" +
            "  <xs:element name='e' type='t:s'/>" +
            "</xs:schema>";
        return XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});
    }

    private static boolean validates(SchemaTypeLoader loader, String lexical) throws Exception {
        return loader.parse("<t:e xmlns:t='urn:t'>" + lexical + "</t:e>", null, null).validate();
    }

    @Test
    void signedZerosAreOneValue() {
        for (String type : new String[]{"float", "double"}) {
            XmlObject plus = value(type, "0");
            XmlObject minus = value(type, "-0");

            assertTrue(plus.valueEquals(minus), "xs:" + type + " 0 and -0 should be equal");
            assertEquals(0, plus.compareValue(minus), "xs:" + type + " 0 and -0 should compare equal");
            assertEquals(plus.valueHashCode(), minus.valueHashCode(),
                "xs:" + type + " 0 and -0 hash differently");

            // NaN still equals itself and nothing else
            assertTrue(value(type, "NaN").valueEquals(value(type, "NaN")));
            assertFalse(value(type, "NaN").valueEquals(plus));
        }
    }

    @Test
    void boundFacetsTreatSignedZerosAsEqual() throws Exception {
        for (String type : new String[]{"xs:float", "xs:double"}) {
            assertTrue(validates(loader("<xs:minInclusive value='0'/>", type), "-0"),
                type + " -0 should satisfy minInclusive 0");
            assertTrue(validates(loader("<xs:maxInclusive value='-0'/>", type), "0"),
                type + " 0 should satisfy maxInclusive -0");
            assertTrue(validates(loader("<xs:enumeration value='0'/>", type), "-0"),
                type + " -0 should match the enumerated 0");

            assertFalse(validates(loader("<xs:minExclusive value='-0'/>", type), "0"),
                type + " 0 should not satisfy minExclusive -0");
            assertFalse(validates(loader("<xs:maxExclusive value='0'/>", type), "-0"),
                type + " -0 should not satisfy maxExclusive 0");
            assertTrue(validates(loader("<xs:minInclusive value='0'/>", type), "1"),
                type + " 1 should satisfy minInclusive 0");
            assertFalse(validates(loader("<xs:minInclusive value='0'/>", type), "-1"),
                type + " -1 should not satisfy minInclusive 0");
        }
    }

    @Test
    void duplicateSignedZeroUniqueValueIsReported() throws Exception {
        for (String type : new String[]{"xs:float", "xs:double"}) {
            String xsd =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
                "xmlns:t='urn:t' targetNamespace='urn:t' elementFormDefault='qualified'>" +
                "  <xs:element name='root'>" +
                "    <xs:complexType>" +
                "      <xs:sequence>" +
                "        <xs:element name='v' type='" + type + "' maxOccurs='unbounded'/>" +
                "      </xs:sequence>" +
                "    </xs:complexType>" +
                "    <xs:unique name='u'>" +
                "      <xs:selector xpath='t:v'/>" +
                "      <xs:field xpath='.'/>" +
                "    </xs:unique>" +
                "  </xs:element>" +
                "</xs:schema>";

            SchemaTypeLoader loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});

            assertFalse(loader.parse("<t:root xmlns:t='urn:t'><t:v>0</t:v><t:v>-0</t:v></t:root>", null, null).validate(),
                type + " 0 and -0 are one value and break the unique constraint");
            assertTrue(loader.parse("<t:root xmlns:t='urn:t'><t:v>0</t:v><t:v>1</t:v></t:root>", null, null).validate());
        }
    }
}
