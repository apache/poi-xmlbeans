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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AttributeGroupWildcardValidateTest {

    // the complete attribute wildcard of a type is the intersection of its own
    // anyAttribute and those of its attribute groups; an extension adds the
    // base type's wildcard by union
    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
        "xmlns:t='urn:t' targetNamespace='urn:t'>" +
        "  <xs:attributeGroup name='ab'>" +
        "    <xs:anyAttribute namespace='urn:a urn:b' processContents='skip'/>" +
        "  </xs:attributeGroup>" +
        "  <xs:attributeGroup name='nested'>" +
        "    <xs:attributeGroup ref='t:ab'/>" +
        "    <xs:anyAttribute namespace='urn:b urn:c' processContents='skip'/>" +
        "  </xs:attributeGroup>" +
        "  <xs:element name='local'><xs:complexType>" +
        "    <xs:attributeGroup ref='t:ab'/>" +
        "    <xs:anyAttribute namespace='urn:b urn:c' processContents='skip'/>" +
        "  </xs:complexType></xs:element>" +
        "  <xs:element name='group'><xs:complexType>" +
        "    <xs:attributeGroup ref='t:nested'/>" +
        "  </xs:complexType></xs:element>" +
        "  <xs:complexType name='base'>" +
        "    <xs:anyAttribute namespace='urn:d' processContents='skip'/>" +
        "  </xs:complexType>" +
        "  <xs:element name='ext'><xs:complexType><xs:complexContent>" +
        "    <xs:extension base='t:base'>" +
        "      <xs:attributeGroup ref='t:ab'/>" +
        "      <xs:anyAttribute namespace='urn:b urn:c' processContents='skip'/>" +
        "    </xs:extension>" +
        "  </xs:complexContent></xs:complexType></xs:element>" +
        "</xs:schema>";

    private static SchemaTypeLoader loader;

    @BeforeAll
    static void compileSchema() throws Exception {
        loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)});
    }

    @ParameterizedTest
    @CsvSource({
        "local, urn:a, false",
        "local, urn:b, true",
        "local, urn:c, false",
        "group, urn:a, false",
        "group, urn:b, true",
        "group, urn:c, false",
        "ext, urn:a, false",
        "ext, urn:b, true",
        "ext, urn:c, false",
        "ext, urn:d, true",
        "ext, urn:e, false"
    })
    void attributeWildcardsAreIntersected(String element, String ns, boolean valid) throws Exception {
        XmlObject doc = loader.parse(
            "<t:" + element + " xmlns:t='urn:t' xmlns:p='" + ns + "' p:x='1'/>", null, null);
        List<XmlError> errors = new ArrayList<>();
        assertEquals(valid, doc.validate(new XmlOptions().setErrorListener(errors)), errors.toString());
    }
}
