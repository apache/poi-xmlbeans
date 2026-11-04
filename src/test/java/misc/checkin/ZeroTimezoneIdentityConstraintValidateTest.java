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

import org.apache.xmlbeans.GDate;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ZeroTimezoneIdentityConstraintValidateTest {

    // the six date types without a time, each with a Z, +00:00 and -00:00 form of the same value
    private static final String[][] SAME = {
        {"date", "2000-01-01Z", "2000-01-01+00:00", "2000-01-01-00:00"},
        {"gYearMonth", "2000-01Z", "2000-01+00:00", "2000-01-00:00"},
        {"gYear", "2000Z", "2000+00:00", "2000-00:00"},
        {"gMonthDay", "--01-01Z", "--01-01+00:00", "--01-01-00:00"},
        {"gDay", "---01Z", "---01+00:00", "---01-00:00"},
        {"gMonth", "--01Z", "--01+00:00", "--01-00:00"}
    };

    private static List<String> unique(String type, String a, String b) throws Exception {
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
            "xmlns:t='urn:t' targetNamespace='urn:t' elementFormDefault='qualified'>" +
            "  <xs:element name='u'>" +
            "    <xs:complexType><xs:sequence>" +
            "      <xs:element name='d' type='xs:" + type + "' maxOccurs='unbounded'/>" +
            "    </xs:sequence></xs:complexType>" +
            "    <xs:unique name='uniq'><xs:selector xpath='t:d'/><xs:field xpath='.'/></xs:unique>" +
            "  </xs:element>" +
            "</xs:schema>";
        XmlObject doc = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)})
            .parse("<t:u xmlns:t='urn:t'><t:d>" + a + "</t:d><t:d>" + b + "</t:d></t:u>", null, null);
        List<XmlError> errors = new ArrayList<>();
        doc.validate(new XmlOptions().setErrorListener(errors));
        return errors.stream().map(XmlError::getErrorCode).collect(Collectors.toList());
    }

    @Test
    void zeroOffsetIsTheSameTimezoneAsZ() throws Exception {
        for (String[] row : SAME) {
            for (int i = 2; i < row.length; i++) {
                GDate z = new GDate(row[1]);
                GDate other = new GDate(row[i]);
                assertEquals(z, other, row[1] + " " + row[i]);
                assertEquals(z.hashCode(), other.hashCode(), row[1] + " " + row[i]);
                assertEquals(row[1], other.canonicalString());
                assertEquals(Collections.singletonList("cvc-identity-constraint.4.1"),
                    unique(row[0], row[1], row[i]), row[1] + " " + row[i]);
            }
        }
    }

    @Test
    void otherTimezonesStayDistinct() throws Exception {
        assertNotEquals(new GDate("2000-01-01Z"), new GDate("2000-01-01+01:00"));
        assertNotEquals(new GDate("2000-01-01Z"), new GDate("2000-01-01"));
        assertEquals("2000-01-01+01:00", new GDate("2000-01-01+01:00").canonicalString());
        assertEquals(Collections.emptyList(), unique("date", "2000-01-01Z", "2000-01-01"));
        assertEquals(Collections.emptyList(), unique("gYear", "2000Z", "2001+00:00"));
    }
}
