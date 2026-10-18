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

package org.apache.xmlbeans.impl.schema;

import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;


public class StscImporterResolveTest {

    private static final String JAR = "jar:file:/nonexistent/schemas.jar!";
    private static final URI JAR_BASE = URI.create(JAR + "/xsd/a.xsd");

    @Test
    void resolvesParentInsideJar() throws Exception {
        assertEquals(JAR + "/common/b.xsd",
            StscImporter.resolve(JAR_BASE, "../common/b.xsd").toString());
    }

    @Test
    void resolvesCurrentDirInsideJar() throws Exception {
        assertEquals(JAR + "/xsd/common/b.xsd",
            StscImporter.resolve(JAR_BASE, "./common/./b.xsd").toString());
    }

    @Test
    void keepsDotDotPrefixedNames() throws Exception {
        assertEquals(JAR + "/xsd/..b.xsd",
            StscImporter.resolve(JAR_BASE, "..b.xsd").toString());
        assertEquals(JAR + "/xsd/.../b.xsd",
            StscImporter.resolve(JAR_BASE, ".../b.xsd").toString());
    }

    @Test
    void keepsFragment() throws Exception {
        assertEquals(JAR + "/common/b.xsd#frag",
            StscImporter.resolve(JAR_BASE, "../common/b.xsd#frag").toString());
    }

    @Test
    void parentAboveJarRootIsLeftUnresolved() {
        URI resolved = assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
            StscImporter.resolve(JAR_BASE, "../../b.xsd"));
        assertEquals(JAR + "/../b.xsd", resolved.toString());
    }

    @Test
    void compileWithImportAboveJarRootTerminates() throws Exception {
        String xsd =
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:a'>" +
            "<xs:import namespace='urn:b' schemaLocation='../../b.xsd'/>" +
            "</xs:schema>";
        XmlOptions parseOpts = new XmlOptions().setDocumentSourceName(JAR_BASE.toString());
        XmlObject schema = XmlObject.Factory.parse(xsd, parseOpts);

        List<XmlError> errors = new ArrayList<>();
        XmlOptions compileOpts = new XmlOptions().setErrorListener(errors);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
            assertThrows(XmlException.class, () ->
                XmlBeans.compileXsd(new XmlObject[]{schema}, XmlBeans.getBuiltinTypeSystem(), compileOpts)));
        assertFalse(errors.isEmpty());
    }
}
