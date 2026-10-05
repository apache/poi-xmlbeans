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
package tools.xsd2inst.checkin;

import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.common.DocumentHelper;
import org.apache.xmlbeans.impl.xsd2inst.SampleXmlUtil;
import org.apache.xmlbeans.impl.xsd2inst.SchemaInstanceGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Xsd2InstTest {
    private static final String PRICEQ = "/xbean/compile/scomp/pricequote/PriceQuote.xsd";
    private static final String BASE64BIN = "/xbean/compile/scomp/base64Binary/Base64BinaryElement.xsd";

    @Test
    void testPriceQuote() throws Exception {
        XmlObject xobj;
        try (InputStream xsdStream = Xsd2InstTest.class.getResourceAsStream(PRICEQ)) {
            xobj = XmlObject.Factory.parse(xsdStream, (new XmlOptions()).setLoadLineNumbers().setLoadMessageDigest());
        }
        SchemaInstanceGenerator.Xsd2InstOptions options = new SchemaInstanceGenerator.Xsd2InstOptions();
        String result = SchemaInstanceGenerator.xsd2inst(new XmlObject[]{xobj}, "price-quote", options);
        assertTrue(result.contains("<price-quote>"), "price-quote element found?");
        assertTrue(result.contains("<stock-symbol>string</stock-symbol>"), "stock-symbol element found?");
        assertTrue(result.contains("<stock-price>string</stock-price>"), "stock-price element found?");
        try (InputStream docStream = new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8))) {
            assertNotNull(DocumentHelper.readDocument(new XmlOptions(), docStream));
        }
    }

    @Test
    void testBase64Binary() throws Exception {
        XmlObject xobj;
        try (InputStream xsdStream = Xsd2InstTest.class.getResourceAsStream(BASE64BIN)) {
            xobj = XmlObject.Factory.parse(xsdStream, (new XmlOptions()).setLoadLineNumbers().setLoadMessageDigest());
        }
        SchemaInstanceGenerator.Xsd2InstOptions options = new SchemaInstanceGenerator.Xsd2InstOptions();
        String result = SchemaInstanceGenerator.xsd2inst(new XmlObject[]{xobj}, "echoBase64BinaryElement", options);
        assertTrue(result.contains("<ns:echoBase64BinaryElement"), "echoBase64BinaryElement element found?");
        assertTrue(result.contains("<ns:base64BinaryElement>"), "base64BinaryElement element found?");
        try (InputStream docStream = new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8))) {
            assertNotNull(DocumentHelper.readDocument(new XmlOptions(), docStream));
        }
    }

    private static String decimalSchema(String facets) {
        return "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
            "<xs:element name='value' type='constrainedDecimal'/>" +
            "<xs:simpleType name='constrainedDecimal'>" +
            "<xs:restriction base='xs:decimal'>" + facets + "</xs:restriction>" +
            "</xs:simpleType></xs:schema>";
    }

    private static String sampleFor(String facets) throws Exception {
        XmlObject xsd = XmlObject.Factory.parse(decimalSchema(facets));
        SchemaTypeSystem sts = XmlBeans.compileXsd(new XmlObject[]{xsd},
            XmlBeans.getBuiltinTypeSystem(), new XmlOptions());
        return SampleXmlUtil.createSampleForType(sts.globalElements()[0]);
    }

    @Test
    void testDigitFacetsWiderThanTheNumberLengthLimit() throws Exception {
        // xsd:totalDigits and xsd:fractionDigits are positiveInteger and so have no
        // ceiling of their own; building the bounds they imply as strings of digits ran
        // them into the maximum number of characters allowed for a number
        String result = sampleFor("<xs:totalDigits value='2000'/>");
        assertTrue(result.contains("<value>"), result);
        try (InputStream docStream = new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8))) {
            assertNotNull(DocumentHelper.readDocument(new XmlOptions(), docStream));
        }

        result = sampleFor("<xs:fractionDigits value='1500'/>");
        assertTrue(result.contains("<value>"), result);
        try (InputStream docStream = new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8))) {
            assertNotNull(DocumentHelper.readDocument(new XmlOptions(), docStream));
        }
    }

    @Test
    void testTotalDigitsStillNarrowsTheBounds() throws Exception {
        // the sample seed for a decimal is 1000.00, which xsd:totalDigits has to pull
        // down to the widest value the facet allows
        String result = sampleFor("<xs:maxInclusive value='999999'/><xs:totalDigits value='3'/>");
        assertTrue(result.contains("<value>999</value>"), result);

        String unconstrained = sampleFor("<xs:maxInclusive value='999999'/>");
        assertTrue(unconstrained.contains("<value>1000.00</value>"), unconstrained);
    }

    private static SchemaTypeSystem simpleTypeSchema(String base, String facets) throws Exception {
        XmlObject xsd = XmlObject.Factory.parse("<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
            "<xs:element name='value'><xs:simpleType>" +
            "<xs:restriction base='xs:" + base + "'>" + facets + "</xs:restriction>" +
            "</xs:simpleType></xs:element></xs:schema>");
        return XmlBeans.compileXsd(new XmlObject[]{xsd}, XmlBeans.getBuiltinTypeSystem(), new XmlOptions());
    }

    @Test
    void testDateSamplesSatisfyTheirBounds() throws Exception {
        // the random seed is a full dateTime, so for the other date types the facet
        // checks compared values that were never comparable, and the bounds were
        // ignored. The seed and the timezone are random, so try each case repeatedly.
        String[][] cases = {
            {"dateTime", "<xs:minInclusive value='2100-01-01T00:00:00Z'/>"},
            {"dateTime", "<xs:maxExclusive value='1900-01-01T00:00:00'/>"},
            {"dateTime", "<xs:minExclusive value='2010-01-01T00:00:00Z'/>" +
                "<xs:maxExclusive value='2010-01-01T00:00:01Z'/>"},
            {"time", "<xs:minInclusive value='10:00:00'/><xs:maxInclusive value='10:00:30'/>"},
            {"date", "<xs:minExclusive value='2100-01-01'/>"},
            {"date", "<xs:maxInclusive value='1900-06-30Z'/>"},
            {"gYearMonth", "<xs:maxInclusive value='1950-06'/>"},
            {"gYear", "<xs:minInclusive value='2100'/>"},
            {"gYear", "<xs:maxExclusive value='1900'/>"},
            {"gMonthDay", "<xs:minInclusive value='--12-25'/>"},
            {"gMonth", "<xs:minExclusive value='--10'/>"},
            {"gDay", "<xs:maxInclusive value='---03'/>"},
        };
        for (String[] c : cases) {
            SchemaTypeSystem sts = simpleTypeSchema(c[0], c[1]);
            for (int i = 0; i < 20; i++) {
                String sample = SampleXmlUtil.createSampleForType(sts.globalElements()[0]);
                XmlObject doc = sts.parse(sample, null, null);
                assertTrue(doc.validate(), c[0] + " " + c[1] + ": " + sample);
            }
        }
    }

    @Test
    void testSampleXmlUtil() throws Exception {
        XmlObject xobj;
        try (InputStream xsdStream = Xsd2InstTest.class.getResourceAsStream(PRICEQ)) {
            xobj = XmlObject.Factory.parse(xsdStream, (new XmlOptions()).setLoadLineNumbers().setLoadMessageDigest());
        }
        SchemaTypeSystem sts = XmlBeans.compileXsd(new XmlObject[]{xobj}, XmlBeans.getBuiltinTypeSystem(), new XmlOptions());
        SchemaGlobalElement[] elements = sts.globalElements();
        SchemaGlobalElement element = elements[0];
        String result = SampleXmlUtil.createSampleForType(element);
        assertTrue(result.contains("<price-quote>"), "price-quote element found?");
        assertTrue(result.contains("<stock-symbol>string</stock-symbol>"), "stock-symbol element found?");
        assertTrue(result.contains("<stock-price>string</stock-price>"), "stock-price element found?");
        try (InputStream docStream = new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8))) {
            assertNotNull(DocumentHelper.readDocument(new XmlOptions(), docStream));
        }
    }
}
