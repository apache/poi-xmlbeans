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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Base64BinaryValidateTest {

    private static boolean validates(String value) throws Exception {
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:t='urn:t' " +
            "targetNamespace='urn:t' elementFormDefault='qualified'>" +
            "  <xs:element name='root' type='xs:base64Binary'/>" +
            "</xs:schema>";
        SchemaTypeLoader loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});
        XmlObject doc = loader.parse("<t:root xmlns:t='urn:t'>" + value + "</t:root>", null, null);
        return doc.validate();
    }

    @Test
    void charsOutsideAlphabetAreReported() throws Exception {
        // the MIME decoder silently drops non-alphabet characters, so these used
        // to validate clean (decoding to "Hello" or an empty array)
        assertFalse(validates("SGVsbG8=!!!!"));
        assertFalse(validates("SGV!!!sbG8="));
        assertFalse(validates("!!!!"));
    }

    @Test
    void validValuesStillValidate() throws Exception {
        assertTrue(validates("SGVsbG8="));
        // whitespace and line wrapping are part of the lexical space
        assertTrue(validates("SGVs bG8="));
        assertTrue(validates("SGVs\n  bG8="));
        assertTrue(validates(""));
    }
}
