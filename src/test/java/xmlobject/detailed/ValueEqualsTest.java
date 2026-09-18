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


package xmlobject.detailed;

import org.apache.xmlbeans.XmlObject;
import org.junit.jupiter.api.Test;
import org.tranxml.tranXML.version40.CarLocationMessageDocument;
import xmlcursor.common.Common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xmlcursor.common.BasicCursorTestCase.jobj;

public class ValueEqualsTest  {
    private static final String CLM =
        "<CarLocationMessage xmlns=\"http://www.tranxml.org/TranXML/Version4.0\" Transaction=\"CLM\">" +
        "<FleetID>FLEETNAME</FleetID>" +
        "<StandardCarrierAlphaCode>CSXT</StandardCarrierAlphaCode>" +
        "<EventStatus>" +
        "<EquipmentStructure>" +
        "<Initial>GATX</Initial>" +
        "<EquipmentNumber>123456</EquipmentNumber>" +
        "<NumberCheckDigit>7</NumberCheckDigit>" +
        "</EquipmentStructure>" +
        "<GeographicLocation><CityName>DALLAS</CityName></GeographicLocation>" +
        "</EventStatus>" +
        "</CarLocationMessage>";

    @Test
    void testValueEqualsTrue() throws Exception {
        CarLocationMessageDocument clmDoc = (CarLocationMessageDocument) jobj(Common.TRANXML_FILE_CLM);
        XmlObject m_xo = jobj(Common.TRANXML_FILE_CLM);
        assertTrue(clmDoc.valueEquals(m_xo));
    }

    @Test
    void testIssue658() throws Exception {
        CarLocationMessageDocument clm1 = CarLocationMessageDocument.Factory.newInstance();
        CarLocationMessageDocument.CarLocationMessage msg1 = clm1.addNewCarLocationMessage();
        msg1.setFleetID("fleet1");

        CarLocationMessageDocument clm2 = CarLocationMessageDocument.Factory.newInstance();
        CarLocationMessageDocument.CarLocationMessage msg2 = clm2.addNewCarLocationMessage();
        msg2.setFleetID("fleet2");

        assertTrue(clm1.valueEquals(clm1));
        assertTrue(clm2.valueEquals(clm2));
        assertFalse(clm1.valueEquals(clm2));
        assertFalse(clm2.valueEquals(clm1));
    }

    @Test
    void testMissingChildElement() throws Exception {
        CarLocationMessageDocument clm1 = CarLocationMessageDocument.Factory.newInstance();
        clm1.addNewCarLocationMessage().setFleetID("fleet1");

        CarLocationMessageDocument clm2 = CarLocationMessageDocument.Factory.newInstance();
        clm2.addNewCarLocationMessage();

        assertFalse(clm1.valueEquals(clm2));
        assertFalse(clm2.valueEquals(clm1));

        clm2.getCarLocationMessage().setFleetID("fleet1");
        assertTrue(clm1.valueEquals(clm2));
    }

    @Test
    void testNestedElementDifference() throws Exception {
        CarLocationMessageDocument clm1 = CarLocationMessageDocument.Factory.parse(CLM);
        CarLocationMessageDocument clm2 = CarLocationMessageDocument.Factory.parse(CLM);
        assertTrue(clm1.valueEquals(clm2));

        CarLocationMessageDocument clm3 =
            CarLocationMessageDocument.Factory.parse(CLM.replace("DALLAS", "AUSTIN"));
        assertFalse(clm1.valueEquals(clm3));
        assertFalse(clm3.valueEquals(clm1));

        // the elements above the changed one differ as well
        assertFalse(clm1.getCarLocationMessage().valueEquals(clm3.getCarLocationMessage()));
        assertFalse(clm1.getCarLocationMessage().getEventStatusArray(0)
            .valueEquals(clm3.getCarLocationMessage().getEventStatusArray(0)));
    }

    @Test
    void testAttributesCompared() throws Exception {
        CarLocationMessageDocument clm1 = CarLocationMessageDocument.Factory.parse(CLM);

        // an attribute of the element itself
        CarLocationMessageDocument clm2 =
            CarLocationMessageDocument.Factory.parse(CLM.replace("Transaction=\"CLM\"", "Transaction=\"CLM\" Version=\"4.0\""));
        assertFalse(clm1.valueEquals(clm2));
        assertFalse(clm2.valueEquals(clm1));

        // an attribute of a nested element with simple content
        CarLocationMessageDocument clm3 =
            CarLocationMessageDocument.Factory.parse(CLM.replace("<CityName>", "<CityName CityNameQualifierCode=\"AA\">"));
        assertFalse(clm1.valueEquals(clm3));
        assertFalse(clm3.valueEquals(clm1));
    }

    @Test
    void testChildrenComparedByValue() throws Exception {
        // the same integer, written two ways: one value, two lexical forms
        CarLocationMessageDocument clm1 = CarLocationMessageDocument.Factory.parse(CLM);
        CarLocationMessageDocument clm2 = CarLocationMessageDocument.Factory.parse(
            CLM.replace("<NumberCheckDigit>7<", "<NumberCheckDigit>007<"));
        assertTrue(clm1.valueEquals(clm2));

        CarLocationMessageDocument clm3 = CarLocationMessageDocument.Factory.parse(
            CLM.replace("<NumberCheckDigit>7<", "<NumberCheckDigit>8<"));
        assertFalse(clm1.valueEquals(clm3));
    }

    @Test
    void testValueThatDoesNotFitItsType() throws Exception {
        // 12:34 is not a valid xs:time - a value that cannot be computed is
        // compared as it was written rather than throwing
        String withTime = CLM.replace("<GeographicLocation>", "<Time>12:34</Time><GeographicLocation>");

        CarLocationMessageDocument clm1 = CarLocationMessageDocument.Factory.parse(withTime);
        CarLocationMessageDocument clm2 = CarLocationMessageDocument.Factory.parse(withTime);
        assertTrue(clm1.valueEquals(clm2));

        CarLocationMessageDocument clm3 =
            CarLocationMessageDocument.Factory.parse(withTime.replace("12:34", "12:35"));
        assertFalse(clm1.valueEquals(clm3));
    }

    @Test
    void testWhitespaceAndCommentsIgnored() throws Exception {
        CarLocationMessageDocument clm1 = CarLocationMessageDocument.Factory.parse(CLM);
        CarLocationMessageDocument clm2 = CarLocationMessageDocument.Factory.parse(
            CLM.replace("><", ">\n  <").replace("<EventStatus>", "<!-- a comment --><EventStatus>"));
        assertTrue(clm1.valueEquals(clm2));
    }

    @Test
    void testUntypedTextIsCompared() throws Exception {
        // untyped content is mixed, so the text around the children counts
        XmlObject xo1 = XmlObject.Factory.parse("<a>one<b/>two</a>");
        XmlObject xo2 = XmlObject.Factory.parse("<a>one<b/>two</a>");
        XmlObject xo3 = XmlObject.Factory.parse("<a>one<b/>three</a>");
        XmlObject xo4 = XmlObject.Factory.parse("<a><b/></a>");

        assertTrue(xo1.valueEquals(xo2));
        assertFalse(xo1.valueEquals(xo3));
        assertFalse(xo1.valueEquals(xo4));
    }
}
