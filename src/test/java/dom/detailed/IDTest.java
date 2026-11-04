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

package dom.detailed;

import common.Common;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import xbean.dom.id.FooDocument;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IDTest {
    private String P = File.separator;

    // Test the getElementById() DOM API with DTDs , run with jvm arg -Dcases.location
    @Test
    void testGetElemById() throws Exception {
        Document doc;
        Element element;
        String tagname;
        XmlOptions xmlOptions = new XmlOptions();
        xmlOptions.setLoadUseDefaultResolver();
        XmlObject o = XmlObject.Factory.parse(new File(Common.CASEROOT + P + "xbean" + P + "dom" + P + "id.xml"));
        doc = (Document) o.getDomNode();
        element = doc.getElementById("CANADA");
        assertNotNull(element);

        tagname = element.getTagName();
        assertEquals("emp:address", tagname, "throw_Equals");
        assertNull(doc.getDoctype());
    }

    // test getElementById() with schema containing DTD with ID definition for untyped XmlObject
    @Test
    void testIDSchema() throws Exception {
        String dtdAndData =
            "<!DOCTYPE xs:schema PUBLIC \"-//W3C//DTD XMLSCHEMA 200102//EN\" \"XMLSchema.dtd\" [\n" +
            "<!ELEMENT first_name (#PCDATA)>\n" +
            "<!ELEMENT hobby (#PCDATA)>\n" +
            "<!ELEMENT homepage EMPTY>\n" +
            "<!ATTLIST homepage href CDATA #REQUIRED>\n" +
            "<!ELEMENT last_name (#PCDATA)>\n" +
            "<!ELEMENT middle_initial (#PCDATA)>\n" +
            "<!ELEMENT name (first_name, middle_initial?, last_name)>\n" +
            "<!ELEMENT person (name, profession+, homepage?, hobby?)>\n" +
            "<!ATTLIST person\n" +
            "        born CDATA #REQUIRED\n" +
            "        died CDATA #REQUIRED\n" +
            "        id ID #REQUIRED\n" +
            ">\n" +
            "<!ELEMENT profession (#PCDATA)>\n" +
            "]>" +
            "<person id=\"25\" born=\"yday\" />";

        XmlObject o = XmlObject.Factory.parse(dtdAndData);
        Document n = (Document) o.getDomNode();
        Element elem = n.getElementById("25");
        assertNotNull(elem);
        System.out.println("Elem: " + elem.getNodeName());

        Element elemInvalid = n.getElementById("100");
        assertNull(elemInvalid);
    }

    // typed XmlObject
    @Test
    @Disabled("doesn't work anymore - xerces 2.11 is not calling the DeclHandler and so no ID attribute is added")
    public void testSchemaWithDTD() throws Exception {
        XmlOptions opt = new XmlOptions();
        List err = new ArrayList();
        opt.setErrorListener(err);
        // opt.setLoadSaxSchema(true);

        String instance =
            "<foo xmlns='http://xbean/dom/id'>" +
            "    <person id=\"abc\"><firstname>John</firstname></person>" +
            "</foo>";

        try {
            FooDocument fooDoc = FooDocument.Factory.parse(instance, opt);

            Document d = (Document) fooDoc.getDomNode();
            Element elem = d.getElementById("abc");
            assertNotNull(elem);

            Element elemInvalid = d.getElementById("100");
            assertNull(elemInvalid);
        } catch (XmlException xme) {
            Collection xmlerrs = xme.getErrors();
            for (Object xmlerr : xmlerrs) {
                XmlError xerr = (XmlError) xmlerr;
                System.out.println("Exception:" + xerr.getMessage());
            }
            throw (new XmlException(new Throwable("XmlException occurred")));
        }

        // parse errors
        for (Object o : err) {
            System.out.println("Err:" + o);
        }
    }

}
