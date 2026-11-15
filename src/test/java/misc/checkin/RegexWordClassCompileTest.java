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
import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XmlOptions.setCompileLegacyRegexWordClass switches pattern facets back to the
 * XML 1.0 Letter and Digit tables for \w, and the choice survives saving the
 * type system to .xsb files and loading it again.
 */
public class RegexWordClassCompileTest {

    private static final String XSD =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' " +
        "xmlns:t='urn:t' targetNamespace='urn:t'>" +
        "  <xs:element name='e'><xs:simpleType>" +
        "    <xs:restriction base='xs:string'><xs:pattern value='[^\\w]+'/></xs:restriction>" +
        "  </xs:simpleType></xs:element>" +
        "</xs:schema>";

    // '+' is a symbol: inside the XML Schema \w, outside the XML 1.0 Letter and Digit tables
    private static final String PLUS = "<t:e xmlns:t='urn:t'>+</t:e>";
    // '.' is punctuation: outside \w in both
    private static final String DOT = "<t:e xmlns:t='urn:t'>.</t:e>";

    private static SchemaTypeSystem compile(XmlOptions options) throws Exception {
        return XmlBeans.compileXsd(new XmlObject[]{SchemaDocument.Factory.parse(XSD)},
            XmlBeans.getBuiltinTypeSystem(), options);
    }

    private static boolean valid(SchemaTypeLoader loader, String xml) throws Exception {
        return loader.parse(xml, null, null).validate();
    }

    private static SchemaTypeLoader saveAndReload(SchemaTypeSystem sts, File dir) throws Exception {
        sts.saveToDirectory(dir);
        // load the .xsb files back from disk rather than using the compiled instance
        SchemaTypeLoader loaded = XmlBeans.typeLoaderForResource(XmlBeans.resourceLoaderForPath(new File[]{dir}));
        return XmlBeans.typeLoaderUnion(loaded, XmlBeans.getBuiltinTypeSystem());
    }

    @Test
    void schemaWordClassByDefault(@TempDir File dir) throws Exception {
        SchemaTypeSystem sts = compile(null);
        SchemaTypeLoader loader = XmlBeans.typeLoaderUnion(sts, XmlBeans.getBuiltinTypeSystem());
        assertFalse(valid(loader, PLUS));
        assertTrue(valid(loader, DOT));

        SchemaTypeLoader reloaded = saveAndReload(sts, dir);
        assertFalse(valid(reloaded, PLUS));
        assertTrue(valid(reloaded, DOT));
    }

    @Test
    void legacyWordClassOption(@TempDir File dir) throws Exception {
        SchemaTypeSystem sts = compile(new XmlOptions().setCompileLegacyRegexWordClass());
        SchemaTypeLoader loader = XmlBeans.typeLoaderUnion(sts, XmlBeans.getBuiltinTypeSystem());
        assertTrue(valid(loader, PLUS));
        assertTrue(valid(loader, DOT));

        SchemaTypeLoader reloaded = saveAndReload(sts, dir);
        assertTrue(valid(reloaded, PLUS));
        assertTrue(valid(reloaded, DOT));
    }
}
