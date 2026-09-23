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

import com.sun.net.httpserver.HttpServer;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlObject;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class ParseUrlRedirectTest {

    private static final String DOC = "<doc>hello</doc>";

    @Test
    void followsRedirectsToTheDocument() throws Exception {
        HttpServer server = HttpServer.create(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        String base = "http://" + server.getAddress().getHostString() + ":";

        // /0 -> /1 -> /2 -> /doc, so the loop discards several connections on the way
        for (int hop = 0; hop < 3; hop++) {
            String next = (hop == 2) ? "/doc" : "/" + (hop + 1);
            server.createContext("/" + hop, exchange -> {
                exchange.getResponseHeaders().add("Location",
                    base + server.getAddress().getPort() + next);
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1);
                exchange.close();
            });
        }

        server.createContext("/doc", exchange -> {
            byte[] body = DOC.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.start();
        try {
            URL url = new URL(base + server.getAddress().getPort() + "/0");
            XmlObject parsed = XmlBeans.getContextTypeLoader().parse(url, null, null);
            assertEquals(DOC, parsed.xmlText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotFollowRedirectsOffHttp() throws Exception {
        // a local file the caller never asked for
        Path local = Files.createTempFile("xmlbeans-redirect", ".xml");
        Files.write(local, "<local>secret</local>".getBytes(StandardCharsets.UTF_8));

        HttpServer server = HttpServer.create(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        String base = "http://" + server.getAddress().getHostString() + ":";

        // the server redirects to a file: URL and carries the document in the redirect body
        server.createContext("/0", exchange -> {
            byte[] body = DOC.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Location", local.toUri().toString());
            exchange.getResponseHeaders().add("Content-Type", "text/xml");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.start();
        try {
            URL url = new URL(base + server.getAddress().getPort() + "/0");
            XmlObject parsed = assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> XmlBeans.getContextTypeLoader().parse(url, null, null));
            assertEquals(DOC, parsed.xmlText());
        } finally {
            server.stop(0);
            Files.deleteIfExists(local);
        }
    }
}
