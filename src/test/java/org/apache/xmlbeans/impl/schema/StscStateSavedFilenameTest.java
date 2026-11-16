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

import org.apache.xmlbeans.impl.tool.Parameters;
import org.apache.xmlbeans.impl.tool.SchemaCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StscStateSavedFilenameTest {

    private static String savedFilename(URI base, String uri) {
        StscState state = StscState.start();
        try {
            state.setBaseUri(base);
            return state.computeSavedFilename(uri);
        } finally {
            StscState.end();
        }
    }

    @Test
    void keepsRelativePath() {
        URI base = URI.create("file:/proj/");
        assertEquals("a/b/c.xsd", savedFilename(base, "file:/proj/a/b/c.xsd"));
    }

    @Test
    void escapesQueryAndFragment() {
        URI base = URI.create("file:/proj/");
        String query = savedFilename(base, "file:/proj/b.xsd?x=/../../../../escaped/pwned.xsd");
        String fragment = savedFilename(base, "file:/proj/b.xsd#/../../../../escaped/pwned.xsd");
        assertTrue(query.startsWith("b.xsd"), query);
        assertTrue(fragment.startsWith("b.xsd"), fragment);
        assertFalse(query.contains("/"), query);
        assertFalse(fragment.contains("/"), fragment);
    }

    @Test
    void schemaSourceCopyStaysInSchemasDir(@TempDir Path tmp) throws IOException {
        Path proj = Files.createDirectories(tmp.resolve("proj"));
        Files.write(proj.resolve("a.xsd"), (
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:a'>" +
            "<xs:include schemaLocation='b.xsd?x=/c.xsd'/>" +
            "<xs:include schemaLocation='b.xsd?x=/../../../../../../../../escaped/pwned.xsd'/>" +
            "<xs:element name='root' type='xs:string'/>" +
            "</xs:schema>").getBytes(StandardCharsets.UTF_8));
        Files.write(proj.resolve("b.xsd"), (
            "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:a'>" +
            "<xs:element name='other' type='xs:string'/>" +
            "</xs:schema>").getBytes(StandardCharsets.UTF_8));

        Path out = tmp.resolve("out");
        Parameters params = new Parameters();
        params.setBaseDir(proj.toFile());
        params.setXsdFiles(new File[]{proj.resolve("a.xsd").toFile()});
        params.setSrcDir(out.resolve("src").toFile());
        params.setClassesDir(out.resolve("classes").toFile());
        params.setNojavac(true);
        params.setQuiet(true);
        assertTrue(SchemaCompiler.compile(params));

        Path schemasDir = out.resolve("classes").resolve(SchemaTypeSystemImpl.METADATA_PACKAGE_GEN).resolve("src");
        List<Path> xsds;
        try (Stream<Path> files = Files.walk(tmp)) {
            xsds = files.filter(p -> p.toString().endsWith(".xsd") && !p.startsWith(proj))
                .map(Path::normalize).collect(Collectors.toList());
        }
        assertFalse(xsds.isEmpty());
        for (Path xsd : xsds) {
            assertTrue(xsd.startsWith(schemasDir), xsd + " is outside " + schemasDir);
        }
    }
}
