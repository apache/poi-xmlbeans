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
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The complete attribute wildcard (XML Schema 1.0 3.4.2) of restrictions, where it
 * comes from attribute groups and its processContents, and the pre-6.0.0 behaviour
 * behind XmlOptions.setCompileLegacyAttributeWildcards.
 */
public class AttributeWildcardRestrictionTest {

    private static final String HEAD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
        "xmlns:t='urn:t' targetNamespace='urn:t'>" +
        "  <xs:attributeGroup name='ab'>" +
        "    <xs:anyAttribute namespace='urn:a urn:b' processContents='skip'/>" +
        "  </xs:attributeGroup>" +
        "  <xs:attributeGroup name='bcStrict'>" +
        "    <xs:anyAttribute namespace='urn:b urn:c' processContents='strict'/>" +
        "  </xs:attributeGroup>" +
        "  <xs:complexType name='any'>" +
        "    <xs:anyAttribute namespace='##any' processContents='skip'/>" +
        "  </xs:complexType>" +
        "  <xs:complexType name='bOnly'>" +
        "    <xs:anyAttribute namespace='urn:b' processContents='skip'/>" +
        "  </xs:complexType>";

    private static final String XSD = HEAD +
        // wildcard only from an attribute group
        "  <xs:element name='restGroup'><xs:complexType><xs:complexContent>" +
        "    <xs:restriction base='t:any'><xs:attributeGroup ref='t:ab'/></xs:restriction>" +
        "  </xs:complexContent></xs:complexType></xs:element>" +
        // group and local wildcard are intersected
        "  <xs:element name='restBoth'><xs:complexType><xs:complexContent>" +
        "    <xs:restriction base='t:any'><xs:attributeGroup ref='t:ab'/>" +
        "      <xs:anyAttribute namespace='urn:b urn:c' processContents='skip'/></xs:restriction>" +
        "  </xs:complexContent></xs:complexType></xs:element>" +
        // processContents comes from the local wildcard
        "  <xs:element name='restProc'><xs:complexType><xs:complexContent>" +
        "    <xs:restriction base='t:any'><xs:attributeGroup ref='t:ab'/>" +
        "      <xs:anyAttribute namespace='urn:a urn:b' processContents='strict'/></xs:restriction>" +
        "  </xs:complexContent></xs:complexType></xs:element>" +
        // no local wildcard: processContents comes from the first attribute group
        "  <xs:element name='firstGroup'><xs:complexType>" +
        "    <xs:attributeGroup ref='t:ab'/><xs:attributeGroup ref='t:bcStrict'/>" +
        "  </xs:complexType></xs:element>" +
        // the type's own wildcards are unioned in legacy mode
        "  <xs:element name='local'><xs:complexType>" +
        "    <xs:attributeGroup ref='t:ab'/>" +
        "    <xs:anyAttribute namespace='urn:b urn:c' processContents='skip'/>" +
        "  </xs:complexType></xs:element>" +
        "</xs:schema>";

    private static SchemaTypeLoader loader;
    private static SchemaTypeLoader legacyLoader;

    @BeforeAll
    static void compileSchemas() throws Exception {
        loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)});
        legacyLoader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)},
            new XmlOptions().setCompileLegacyAttributeWildcards());
    }

    private static boolean valid(SchemaTypeLoader stl, String element, String ns) throws Exception {
        XmlObject doc = stl.parse(
            "<t:" + element + " xmlns:t='urn:t' xmlns:p='" + ns + "' p:x='1'/>", null, null);
        List<XmlError> errors = new ArrayList<>();
        return doc.validate(new XmlOptions().setErrorListener(errors));
    }

    @ParameterizedTest
    @CsvSource({
        "restGroup, urn:a, true",
        "restGroup, urn:b, true",
        "restGroup, urn:c, false",
        "restBoth, urn:a, false",
        "restBoth, urn:b, true",
        "restBoth, urn:c, false",
        // strict: there is no global declaration for p:x
        "restProc, urn:b, false",
        "firstGroup, urn:b, true",
        "firstGroup, urn:a, false",
        "firstGroup, urn:c, false",
        "local, urn:a, false",
        "local, urn:b, true"
    })
    void completeWildcard(String element, String ns, boolean expected) throws Exception {
        assertEquals(expected, valid(loader, element, ns));
    }

    @ParameterizedTest
    @CsvSource({
        // a restriction drops a wildcard that only comes from attribute groups
        "restGroup, urn:a, false",
        "restGroup, urn:b, false",
        "restBoth, urn:b, true",
        "restBoth, urn:c, false",
        // processContents is inherited from the base type
        "restProc, urn:b, true",
        // the last wildcard's processContents wins
        "firstGroup, urn:b, false",
        // the type's own wildcards are unioned
        "local, urn:a, true",
        "local, urn:b, true",
        "local, urn:c, true"
    })
    void legacyWildcard(String element, String ns, boolean expected) throws Exception {
        assertEquals(expected, valid(legacyLoader, element, ns));
    }

    private static final String WIDE_GROUP = HEAD +
        // the group alone is wider than the base, the complete wildcard is not
        "  <xs:complexType name='wide'><xs:complexContent>" +
        "    <xs:restriction base='t:bOnly'><xs:attributeGroup ref='t:ab'/>" +
        "      <xs:anyAttribute namespace='urn:b' processContents='skip'/></xs:restriction>" +
        "  </xs:complexContent></xs:complexType>" +
        "  <xs:element name='wide' type='t:wide'/>" +
        "</xs:schema>";

    @Test
    void subsetCheckUsesCompleteWildcard() throws Exception {
        SchemaTypeLoader stl = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(WIDE_GROUP)});
        assertTrue(valid(stl, "wide", "urn:b"));
        assertEquals(false, valid(stl, "wide", "urn:a"));

        // pre-6.0.0 checked the group wildcard against the base on its own
        XmlException e = assertThrows(XmlException.class, () -> XmlBeans.loadXsd(
            new XmlObject[]{SchemaDocument.Factory.parse(WIDE_GROUP)},
            new XmlOptions().setCompileLegacyAttributeWildcards()));
        assertTrue(e.getMessage().contains("derivation-ok-restriction.4.2"), e.getMessage());
    }

    @Test
    void completeWildcardNotSubsetOfBase() throws Exception {
        String xsd = HEAD +
            "  <xs:complexType name='notSubset'><xs:complexContent>" +
            "    <xs:restriction base='t:bOnly'><xs:attributeGroup ref='t:ab'/></xs:restriction>" +
            "  </xs:complexContent></xs:complexType>" +
            "</xs:schema>";
        XmlException e = assertThrows(XmlException.class,
            () -> XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)}));
        assertTrue(e.getMessage().contains("derivation-ok-restriction.4.2"), e.getMessage());
    }

    @Test
    void baseWithoutWildcard() throws Exception {
        String xsd = HEAD +
            "  <xs:complexType name='none'/>" +
            "  <xs:complexType name='addsWildcard'><xs:complexContent>" +
            "    <xs:restriction base='t:none'><xs:attributeGroup ref='t:ab'/></xs:restriction>" +
            "  </xs:complexContent></xs:complexType>" +
            "</xs:schema>";
        XmlException e = assertThrows(XmlException.class,
            () -> XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)}));
        assertTrue(e.getMessage().contains("derivation-ok-restriction.4.1"), e.getMessage());
    }
}
