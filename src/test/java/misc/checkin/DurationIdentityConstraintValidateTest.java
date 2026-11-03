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
import org.apache.xmlbeans.XmlDuration;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DurationIdentityConstraintValidateTest {

    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
        "xmlns:t='urn:t' targetNamespace='urn:t' elementFormDefault='qualified'>" +
        "  <xs:element name='u'>" +
        "    <xs:complexType><xs:sequence>" +
        "      <xs:element name='d' type='xs:duration' maxOccurs='unbounded'/>" +
        "    </xs:sequence></xs:complexType>" +
        "    <xs:unique name='uniq'><xs:selector xpath='t:d'/><xs:field xpath='.'/></xs:unique>" +
        "  </xs:element>" +
        "  <xs:element name='k'>" +
        "    <xs:complexType><xs:sequence>" +
        "      <xs:element name='d' type='xs:duration' maxOccurs='unbounded'/>" +
        "      <xs:element name='r' type='xs:duration' maxOccurs='unbounded'/>" +
        "    </xs:sequence></xs:complexType>" +
        "    <xs:key name='key'><xs:selector xpath='t:d'/><xs:field xpath='.'/></xs:key>" +
        "    <xs:keyref name='ref' refer='t:key'><xs:selector xpath='t:r'/><xs:field xpath='.'/></xs:keyref>" +
        "  </xs:element>" +
        "</xs:schema>";

    private static SchemaTypeLoader loader;

    @BeforeAll
    static void compileSchema() throws Exception {
        loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)});
    }

    private static List<String> validate(String xml) throws Exception {
        XmlObject doc = loader.parse(xml, null, null);
        List<XmlError> errors = new ArrayList<>();
        doc.validate(new XmlOptions().setErrorListener(errors));
        return errors.stream().map(XmlError::getErrorCode).collect(Collectors.toList());
    }

    private static List<String> unique(String a, String b) throws Exception {
        return validate("<t:u xmlns:t='urn:t'><t:d>" + a + "</t:d><t:d>" + b + "</t:d></t:u>");
    }

    private static List<String> keyref(String key, String ref) throws Exception {
        return validate("<t:k xmlns:t='urn:t'><t:d>" + key + "</t:d><t:r>" + ref + "</t:r></t:k>");
    }

    @Test
    void equalDurationsWrittenDifferentlyAreDuplicates() throws Exception {
        String[][] same = {
            {"P1D", "PT24H"}, {"PT1M", "PT60S"}, {"P1Y", "P12M"},
            {"PT1.5S", "PT1.50S"}, {"PT90S", "PT1M30S"}, {"P0D", "-PT0S"}
        };
        for (String[] pair : same) {
            assertEquals(Collections.singletonList("cvc-identity-constraint.4.1"),
                unique(pair[0], pair[1]), pair[0] + " " + pair[1]);
            assertEquals(Collections.emptyList(), keyref(pair[0], pair[1]), pair[0] + " " + pair[1]);
        }
    }

    @Test
    void differentDurationsAreNotDuplicates() throws Exception {
        // P1M is 28 to 31 days, so it is not equal to P30D
        String[][] different = {{"P1D", "PT23H"}, {"P1M", "P30D"}, {"PT1S", "-PT1S"}, {"PT1.5S", "PT1.25S"}};
        for (String[] pair : different) {
            assertEquals(Collections.emptyList(), unique(pair[0], pair[1]), pair[0] + " " + pair[1]);
            assertEquals(Collections.singletonList("cvc-identity-constraint.4.3"),
                keyref(pair[0], pair[1]), pair[0] + " " + pair[1]);
        }
    }

    @Test
    void valueEqualsAgreesWithValueHashCode() {
        XmlDuration a = XmlDuration.Factory.newValue("P1DT1H");
        XmlDuration b = XmlDuration.Factory.newValue("PT25H");
        assertTrue(a.valueEquals(b));
        assertEquals(a.valueHashCode(), b.valueHashCode());
        assertFalse(a.valueEquals(XmlDuration.Factory.newValue("PT26H")));
    }
}
