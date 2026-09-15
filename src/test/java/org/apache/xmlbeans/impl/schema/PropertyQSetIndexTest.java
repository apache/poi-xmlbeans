/*   Copyright 2026 The Apache Software Foundation
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
 *   limitations under the License.
 */
package org.apache.xmlbeans.impl.schema;

import org.apache.xmlbeans.Filer;
import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a type has two or more element properties that each accept several
 * QNames (for example two substitution group heads), the generated impl class
 * shares one {@code PROPERTY_QSET} array between them. Each property must
 * reference its own slot in that array; a regression in 5.2.1 made every
 * property reference slot 0, so the second property matched the first
 * property's element names.
 */
public class PropertyQSetIndexTest {

    private static final String SCHEMA =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'" +
        "  targetNamespace='urn:qset' xmlns='urn:qset' elementFormDefault='qualified'>" +
        "  <xs:element name='headA' type='xs:string'/>" +
        "  <xs:element name='subA' type='xs:string' substitutionGroup='headA'/>" +
        "  <xs:element name='headB' type='xs:string'/>" +
        "  <xs:element name='subB' type='xs:string' substitutionGroup='headB'/>" +
        "  <xs:complexType name='Holder'>" +
        "    <xs:sequence>" +
        "      <xs:element ref='headA' maxOccurs='unbounded'/>" +
        "      <xs:element ref='headB' maxOccurs='unbounded'/>" +
        "    </xs:sequence>" +
        "  </xs:complexType>" +
        "</xs:schema>";

    private static class InMemoryFiler implements Filer {
        final Map<String, StringWriter> sources = new HashMap<>();

        @Override
        public OutputStream createBinaryFile(String typename) {
            return new ByteArrayOutputStream();
        }

        @Override
        public Writer createSourceFile(String typename, String sourceCodeEncoding) {
            StringWriter sw = new StringWriter();
            sources.put(typename, sw);
            return sw;
        }
    }

    @Test
    void eachMultiNamePropertyGetsItsOwnQSetSlot() throws Exception {
        XmlObject xsd = XmlObject.Factory.parse(SCHEMA);
        InMemoryFiler filer = new InMemoryFiler();
        // supplying a Filer makes the compiler assign Java names and emit the sources
        SchemaTypeSystem sts = XmlBeans.compileXmlBeans(null, null, new XmlObject[]{xsd}, null, null, filer, new XmlOptions());
        assertNotNull(sts);

        String impl = filer.sources.entrySet().stream()
            .filter(e -> e.getKey().endsWith("HolderImpl"))
            .map(e -> e.getValue().toString())
            .findFirst().orElse(null);
        assertNotNull(impl, "HolderImpl source not generated: " + filer.sources.keySet());

        // both properties accept two QNames, so both must be backed by a QNameSet slot
        assertTrue(impl.contains("PROPERTY_QSET[0]"), "generated source lacks PROPERTY_QSET[0]");
        assertTrue(impl.contains("PROPERTY_QSET[1]"), "generated source lacks PROPERTY_QSET[1]");

        // headA is the first property and headB the second: check the accessor
        // for headB uses slot 1 rather than slot 0
        assertEquals(0, qsetSlotFor(impl, "getHeadAArray"), "headA slot");
        assertEquals(1, qsetSlotFor(impl, "getHeadBArray"), "headB slot");
    }

    /**
     * Finds the {@code PROPERTY_QSET[n]} slot referenced in the body of the
     * given no-arg accessor method.
     */
    private static int qsetSlotFor(String source, String methodName) {
        Pattern p = Pattern.compile(methodName + "\\(\\)\\s*\\{.*?PROPERTY_QSET\\[(\\d+)\\]", Pattern.DOTALL);
        Matcher m = p.matcher(source);
        assertTrue(m.find(), "no PROPERTY_QSET reference found in " + methodName);
        return Integer.parseInt(m.group(1));
    }
}
