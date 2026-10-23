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
import org.apache.xmlbeans.impl.validator.ValidatingXMLStreamReader;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.xml.stream.XMLInputFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QNameIdentityConstraintValidateTest {

    enum Mode { XML_OBJECT, STREAM }

    // xmlns:s is bound only in the schema, and xmlns:p means different things in the schema and the instance
    private static String schema(String constraint) {
        return
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
            "xmlns:t='urn:t' xmlns:s='urn:p' xmlns:p='urn:s' targetNamespace='urn:t' elementFormDefault='qualified'>" +
            "  <xs:complexType name='named'><xs:attribute name='n' type='xs:QName' use='required'/></xs:complexType>" +
            "  <xs:complexType name='defaultS'><xs:attribute name='n' type='xs:QName' default='s:a'/></xs:complexType>" +
            "  <xs:complexType name='defaultP'><xs:attribute name='n' type='xs:QName' default='p:a'/></xs:complexType>" +
            "  <xs:complexType name='wrapped'><xs:sequence><xs:element name='v' type='xs:QName'/></xs:sequence></xs:complexType>" +
            "  <xs:element name='root'>" +
            "    <xs:complexType><xs:sequence>" +
            "      <xs:element name='k' type='t:named' minOccurs='0' maxOccurs='unbounded'/>" +
            "      <xs:element name='ds' type='t:defaultS' minOccurs='0' maxOccurs='unbounded'/>" +
            "      <xs:element name='dp' type='t:defaultP' minOccurs='0' maxOccurs='unbounded'/>" +
            "      <xs:element name='w' type='t:wrapped' minOccurs='0' maxOccurs='unbounded'/>" +
            "      <xs:element name='ref' type='t:named' minOccurs='0' maxOccurs='unbounded'/>" +
            "    </xs:sequence></xs:complexType>" +
            constraint +
            "    <xs:keyref name='kr' refer='t:kk'><xs:selector xpath='t:ref'/><xs:field xpath='@n'/></xs:keyref>" +
            "  </xs:element>" +
            "</xs:schema>";
    }

    private static String constraint(String kind, String selector, String field) {
        return "<xs:" + kind + " name='kk'><xs:selector xpath='" + selector + "'/><xs:field xpath='" + field + "'/></xs:" + kind + ">";
    }

    private static final String KEY = constraint("key", "t:k", "@n");

    private static final String UNIQUE = constraint("unique", "t:k", "@n");

    private static final String UNIQUE_DEFAULTED = constraint("unique", "t:k|t:ds|t:dp", "@n");

    private static final String UNIQUE_ELEMENT = constraint("unique", "t:w", "t:v");

    private static List<XmlError> validate(Mode mode, String xsd, String body) throws Exception {
        SchemaTypeLoader loader = XmlBeans.loadXsd(new XmlObject[]{SchemaDocument.Factory.parse(xsd)});
        String instance = "<t:root xmlns:t='urn:t' xmlns:p='urn:p'>" + body + "</t:root>";
        List<XmlError> errors = new ArrayList<>();
        boolean valid;
        if (mode == Mode.XML_OBJECT) {
            XmlObject doc = loader.parse(instance, null, null);
            valid = doc.validate(new XmlOptions().setErrorListener(errors));
        } else {
            ValidatingXMLStreamReader valXsr = new ValidatingXMLStreamReader();
            valXsr.init(XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(instance)),
                true, null, loader, null, errors);
            while (valXsr.hasNext()) {
                valXsr.next();
            }
            valid = valXsr.isValid();
        }
        assertEquals(errors.isEmpty(), valid, errors.toString());
        return errors;
    }

    private static void assertSingleError(String code, List<XmlError> errors) {
        assertEquals(1, errors.size(), errors.toString());
        assertEquals(code, errors.get(0).getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void prefixedQNameKeyIsFilled(Mode mode) throws Exception {
        List<XmlError> errors = validate(mode, schema(KEY), "<t:k n='p:a'/><t:ref n='p:a'/>");
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void prefixedQNameDuplicateUniqueIsReported(Mode mode) throws Exception {
        List<XmlError> errors = validate(mode, schema(UNIQUE), "<t:k n='p:a'/><t:k n='p:a'/>");
        assertSingleError("cvc-identity-constraint.4.1", errors);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void prefixedQNameDanglingKeyrefIsReported(Mode mode) throws Exception {
        List<XmlError> errors = validate(mode, schema(UNIQUE), "<t:k n='p:a'/><t:ref n='p:zzz'/>");
        assertSingleError("cvc-identity-constraint.4.3", errors);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void defaultNamespaceQNameMatchesPrefixed(Mode mode) throws Exception {
        List<XmlError> errors = validate(mode, schema(UNIQUE), "<t:k xmlns='urn:p' n='a'/><t:k n='p:a'/>");
        assertSingleError("cvc-identity-constraint.4.1", errors);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void unprefixedQNameWithoutDefaultNamespaceIsDistinct(Mode mode) throws Exception {
        List<XmlError> errors = validate(mode, schema(UNIQUE), "<t:k n='a'/><t:k n='p:a'/>");
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void prefixedQNameElementDuplicateUniqueIsReported(Mode mode) throws Exception {
        List<XmlError> errors = validate(mode, schema(UNIQUE_ELEMENT), "<t:w><t:v>p:a</t:v></t:w><t:w><t:v>p:a</t:v></t:w>");
        assertSingleError("cvc-identity-constraint.4.1", errors);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void defaultedQNameUsesSchemaPrefixUnboundInInstance(Mode mode) throws Exception {
        // the default s:a is {urn:p}a, the same as p:a in the instance
        List<XmlError> errors = validate(mode, schema(UNIQUE_DEFAULTED), "<t:k n='p:a'/><t:ds/>");
        assertSingleError("cvc-identity-constraint.4.1", errors);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void defaultedQNameUsesSchemaPrefixRedefinedInInstance(Mode mode) throws Exception {
        // the default p:a is {urn:s}a, not the instance's p:a
        List<XmlError> errors = validate(mode, schema(UNIQUE_DEFAULTED), "<t:k n='p:a'/><t:dp/>");
        assertTrue(errors.isEmpty(), errors.toString());
    }
}
