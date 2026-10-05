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

public class IncomparableMinFacetValidateTest {

    // dateTime and duration are only partially ordered. A dateTime without a
    // timezone is incomparable with one that has a timezone when the two are
    // within 14 hours of each other, and P1M is incomparable with P30D because a
    // month is 28 to 31 days long.
    private static final String DT_BOUND = "2000-01-01T12:00:00Z";
    private static final String DT_INCOMPARABLE = "2000-01-01T12:00:00";
    private static final String DUR_BOUND = "P30D";
    private static final String DUR_INCOMPARABLE = "P1M";

    private static final String[] FACETS = {
        "minInclusive", "minExclusive", "maxInclusive", "maxExclusive"
    };

    private static String element(String name, String base, String facet, String bound) {
        return
            "  <xs:element name='" + name + "'>" +
            "    <xs:simpleType>" +
            "      <xs:restriction base='xs:" + base + "'>" +
            "        <xs:" + facet + " value='" + bound + "'/>" +
            "      </xs:restriction>" +
            "    </xs:simpleType>" +
            "  </xs:element>";
    }

    private static String xsd() {
        StringBuilder sb = new StringBuilder(
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
            "xmlns:t='urn:t' targetNamespace='urn:t' elementFormDefault='qualified'>");
        for (String facet : FACETS) {
            sb.append(element("dt-" + facet, "dateTime", facet, DT_BOUND));
            sb.append(element("dur-" + facet, "duration", facet, DUR_BOUND));
        }
        return sb.append("</xs:schema>").toString();
    }

    private static SchemaTypeLoader loader;

    @BeforeAll
    static void compileSchema() throws Exception {
        loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd())});
    }

    // the error codes reported when validating value against the element's facet
    private static List<String> validate(String element, String value) throws Exception {
        XmlObject doc = loader.parse(
            "<t:" + element + " xmlns:t='urn:t'>" + value + "</t:" + element + ">", null, null);
        List<XmlError> errors = new ArrayList<>();
        doc.validate(new XmlOptions().setErrorListener(errors));
        return errors.stream().map(XmlError::getErrorCode).collect(Collectors.toList());
    }

    private static void assertValid(String element, String value) throws Exception {
        assertEquals(Collections.emptyList(), validate(element, value), element + " " + value);
    }

    // element names end in the facet, and each facet has its own error code
    private static void assertInvalid(String element, String value) throws Exception {
        String facet = element.substring(element.indexOf('-') + 1);
        assertEquals(Collections.singletonList("cvc-" + facet + "-valid"), validate(element, value),
            element + " " + value);
    }

    @Test
    void incomparableDateTimeFailsEveryBound() throws Exception {
        // not greater than, less than, or equal to the bound, so no facet is satisfied
        for (String facet : FACETS) {
            assertInvalid("dt-" + facet, DT_INCOMPARABLE);
        }
    }

    @Test
    void incomparableDurationFailsEveryBound() throws Exception {
        for (String facet : FACETS) {
            assertInvalid("dur-" + facet, DUR_INCOMPARABLE);
        }
    }

    @Test
    void comparableDateTimeStillChecked() throws Exception {
        // equal to the bound
        assertValid("dt-minInclusive", "2000-01-01T12:00:00Z");
        assertInvalid("dt-minExclusive", "2000-01-01T12:00:00Z");
        // more than 14 hours past the bound, so the missing timezone does not matter
        assertValid("dt-minInclusive", "2000-01-03T12:00:00");
        assertValid("dt-minExclusive", "2000-01-03T12:00:00");
        // more than 14 hours short of it
        assertInvalid("dt-minInclusive", "1999-12-30T12:00:00");
        assertInvalid("dt-minExclusive", "1999-12-30T12:00:00");
    }

    @Test
    void comparableDurationStillChecked() throws Exception {
        assertValid("dur-minInclusive", "P30D");
        assertInvalid("dur-minExclusive", "P30D");
        // two months are at least 59 days
        assertValid("dur-minInclusive", "P2M");
        assertValid("dur-minExclusive", "P2M");
        assertInvalid("dur-minInclusive", "P27D");
        assertInvalid("dur-minExclusive", "P27D");
    }
}
