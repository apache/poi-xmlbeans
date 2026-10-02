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

public class DecimalValueHashCodeTest {

    private static final String XSD = "http://www.w3.org/2001/XMLSchema";

    private static XmlObject value(String localName, String lexical) {
        SchemaType type = XmlBeans.getBuiltinTypeSystem().findType(new QName(XSD, localName));
        return type.newValue(lexical);
    }

    @Test
    void equalDecimalValuesHashTheSame() {
        String[] intTypes = {"int", "short", "byte", "unsignedShort", "unsignedByte"};
        String[] wideTypes = {"long", "integer", "decimal"};
        String[] lexicals = {"-1", "-5", "-128", "0", "5", "127"};

        for (String intType : intTypes) {
            for (String wideType : wideTypes) {
                for (String lexical : lexicals) {
                    XmlObject narrow = value(intType, lexical);
                    XmlObject wide = value(wideType, lexical);

                    assertTrue(narrow.valueEquals(wide),
                        "xs:" + intType + " and xs:" + wideType + " '" + lexical + "' should be equal");
                    assertEquals(narrow.valueHashCode(), wide.valueHashCode(),
                        "xs:" + intType + " and xs:" + wideType + " '" + lexical + "' hash differently");
                }
            }
        }
    }

    @Test
    void duplicateNegativeUniqueValueIsReported() throws Exception {
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
            "xmlns:t='urn:t' targetNamespace='urn:t' elementFormDefault='qualified'>" +
            "  <xs:element name='root'>" +
            "    <xs:complexType>" +
            "      <xs:sequence>" +
            "        <xs:element name='i' type='xs:int'/>" +
            "        <xs:element name='l' type='xs:long'/>" +
            "      </xs:sequence>" +
            "    </xs:complexType>" +
            "    <xs:unique name='u'>" +
            "      <xs:selector xpath='t:i|t:l'/>" +
            "      <xs:field xpath='.'/>" +
            "    </xs:unique>" +
            "  </xs:element>" +
            "</xs:schema>";

        SchemaTypeLoader loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});

        // the same value spelled as xs:int and as xs:long is one value, so it breaks the
        // unique constraint whatever its sign
        assertFalse(loader.parse("<t:root xmlns:t='urn:t'><t:i>-5</t:i><t:l>-5</t:l></t:root>", null, null).validate());
        assertFalse(loader.parse("<t:root xmlns:t='urn:t'><t:i>5</t:i><t:l>5</t:l></t:root>", null, null).validate());
        assertTrue(loader.parse("<t:root xmlns:t='urn:t'><t:i>-5</t:i><t:l>-6</t:l></t:root>", null, null).validate());
    }
}
