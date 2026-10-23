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

public class TotalDigitsFractionValidateTest {

    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
        "xmlns:t='urn:t' targetNamespace='urn:t' elementFormDefault='qualified'>" +
        "  <xs:element name='d'>" +
        "    <xs:simpleType>" +
        "      <xs:restriction base='xs:decimal'>" +
        "        <xs:totalDigits value='3'/>" +
        "      </xs:restriction>" +
        "    </xs:simpleType>" +
        "  </xs:element>" +
        "</xs:schema>";

    private static SchemaTypeLoader loader;

    @BeforeAll
    static void compileSchema() throws Exception {
        loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)});
    }

    private static List<String> validate(String value) throws Exception {
        XmlObject doc = loader.parse("<t:d xmlns:t='urn:t'>" + value + "</t:d>", null, null);
        List<XmlError> errors = new ArrayList<>();
        doc.validate(new XmlOptions().setErrorListener(errors));
        return errors.stream().map(XmlError::getErrorCode).collect(Collectors.toList());
    }

    private static void assertValid(String value) throws Exception {
        assertEquals(Collections.emptyList(), validate(value), value);
    }

    private static void assertInvalid(String value) throws Exception {
        assertEquals(Collections.singletonList("cvc-totalDigits-valid"), validate(value), value);
    }

    @Test
    void leadingFractionZerosCount() throws Exception {
        // 0.0001 is 1 x 10^-4, which needs four digits
        assertInvalid("0.0001");
        assertInvalid("-0.0001");
        assertInvalid(".0005");
        assertInvalid("0.00010");
        assertValid("0.001");
        assertValid("-0.012");
        assertValid("0.00100");
    }

    @Test
    void otherValuesUnchanged() throws Exception {
        assertValid("123");
        assertValid("1.23");
        assertValid("12.300");
        assertValid("0");
        assertValid("0.00000");
        assertValid("-0.0");
        assertInvalid("1234");
        assertInvalid("1.234");
    }
}
