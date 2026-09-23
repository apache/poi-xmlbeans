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

package org.apache.xmlbeans.impl.schema;

import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.common.QNameHelper;
import org.apache.xmlbeans.impl.store.Locale;
import org.apache.xmlbeans.impl.util.MathUtil;
import org.apache.xmlbeans.impl.xpath.XPathFactory;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Node;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public abstract class SchemaTypeLoaderBase implements SchemaTypeLoader {
    private static final String USER_AGENT = "XMLBeans/" + XmlBeans.getVersion() + " (" + XmlBeans.getTitle() + ")";

    private static String doCompilePath(String pathExpr, XmlOptions options) {
        return XPathFactory.compilePath(pathExpr, options);
    }

    private static String doCompileQuery(String queryExpr, XmlOptions options) {
        return XPathFactory.compileQuery(queryExpr, options);
    }

    public SchemaType findType(QName name) {
        SchemaType.Ref ref = findTypeRef(name);
        if (ref == null) {
            return null;
        }
        SchemaType result = ref.get();
        assert (result != null);
        return result;
    }

    public SchemaType findDocumentType(QName name) {
        SchemaType.Ref ref = findDocumentTypeRef(name);
        if (ref == null) {
            return null;
        }
        SchemaType result = ref.get();
        assert (result != null);
        return result;
    }

    public SchemaType findAttributeType(QName name) {
        SchemaType.Ref ref = findAttributeTypeRef(name);
        if (ref == null) {
            return null;
        }
        SchemaType result = ref.get();
        assert (result != null);
        return result;
    }

    public SchemaModelGroup findModelGroup(QName name) {
        SchemaModelGroup.Ref ref = findModelGroupRef(name);
        if (ref == null) {
            return null;
        }
        SchemaModelGroup result = ref.get();
        assert (result != null);
        return result;
    }

    public SchemaAttributeGroup findAttributeGroup(QName name) {
        SchemaAttributeGroup.Ref ref = findAttributeGroupRef(name);
        if (ref == null) {
            return null;
        }
        SchemaAttributeGroup result = ref.get();
        assert (result != null);
        return result;
    }

    public SchemaGlobalElement findElement(QName name) {
        SchemaGlobalElement.Ref ref = findElementRef(name);
        if (ref == null) {
            return null;
        }
        SchemaGlobalElement result = ref.get();
        assert (result != null);
        return result;
    }

    public SchemaGlobalAttribute findAttribute(QName name) {
        SchemaGlobalAttribute.Ref ref = findAttributeRef(name);
        if (ref == null) {
            return null;
        }
        SchemaGlobalAttribute result = ref.get();
        assert (result != null);
        return result;
    }

    //
    //
    //

    public XmlObject newInstance(SchemaType type, XmlOptions options) {
        XmlFactoryHook hook = XmlFactoryHook.ThreadContext.getHook();

        if (hook != null) {
            return hook.newInstance(this, type, options);
        }

        return Locale.newInstance(this, type, options);
    }

    public XmlObject parse(String xmlText, SchemaType type, XmlOptions options) throws XmlException {
        XmlFactoryHook hook = XmlFactoryHook.ThreadContext.getHook();

        if (hook != null) {
            return hook.parse(this, xmlText, type, options);
        }

        return Locale.parseToXmlObject(this, xmlText, type, options);
    }

    public XmlObject parse(XMLStreamReader xsr, SchemaType type, XmlOptions options) throws XmlException {
        XmlFactoryHook hook = XmlFactoryHook.ThreadContext.getHook();

        if (hook != null) {
            return hook.parse(this, xsr, type, options);
        }

        return Locale.parseToXmlObject(this, xsr, type, options);
    }

    public XmlObject parse(File file, SchemaType type, XmlOptions options) throws XmlException, IOException {
        String fileName = file.toURI().normalize().toString();
        if (options == null) {
            options = new XmlOptions();
            options.setDocumentSourceName(fileName);
        } else if (options.getDocumentSourceName() == null) {
            options = new XmlOptions(options);
            options.setDocumentSourceName(fileName);
        }

        try (InputStream fis = Files.newInputStream(file.toPath())) {
            return parse(fis, type, options);
        }
    }

    public XmlObject parse(URL url, SchemaType type, XmlOptions options) throws XmlException, IOException {
        if (options == null) {
            options = new XmlOptions();
            options.setDocumentSourceName(url.toString());
        } else if (options.getDocumentSourceName() == null) {
            options = new XmlOptions(options);
            options.setDocumentSourceName(url.toString());
        }


        boolean redirected = false;
        int count = 0;
        URLConnection conn;

        do {
            conn = url.openConnection();
            conn.addRequestProperty("User-Agent", USER_AGENT);
            conn.addRequestProperty("Accept", "application/xml, text/xml, */*");
            if (conn instanceof HttpURLConnection) {
                HttpURLConnection httpcon = (HttpURLConnection) conn;
                int code = httpcon.getResponseCode();
                redirected = (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP);
                if (redirected && count > 5) {
                    redirected = false;
                }

                if (redirected) {
                    String newLocation = httpcon.getHeaderField("Location");
                    URL newUrl = (newLocation == null) ? null : new URL(newLocation);
                    if (newUrl == null || !isHttpUrl(newUrl)) {
                        // a redirect off http(s) would open a file:, jar: or ftp:
                        // resource the caller never asked for; stop here like
                        // HttpURLConnection does for a cross-protocol redirect
                        redirected = false;
                    } else {
                        url = newUrl;
                        count++;
                        // the redirect body is never read, so hand the socket back
                        // instead of leaving it for the keep-alive cache to reap
                        httpcon.disconnect();
                    }
                }
            }
        } while (redirected);

        try (InputStream stream = conn.getInputStream()) {
            return parse(stream, type, options);
        }
    }

    private static boolean isHttpUrl(URL url) {
        String protocol = url.getProtocol();
        return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
    }

    public XmlObject parse(InputStream jiois, SchemaType type, XmlOptions options) throws XmlException, IOException {
        XmlFactoryHook hook = XmlFactoryHook.ThreadContext.getHook();

        DigestInputStream digestStream = null;

        setupDigest:
        if (options != null && options.isLoadMessageDigest()) {
            MessageDigest sha;

            try {
                sha = MessageDigest.getInstance("SHA");
            } catch (NoSuchAlgorithmException e) {
                break setupDigest;
            }

            digestStream = new DigestInputStream(jiois, sha);
            jiois = digestStream;
        }

        if (hook != null) {
            return hook.parse(this, jiois, type, options);
        }

        XmlObject result = Locale.parseToXmlObject(this, jiois, type, options);

        if (digestStream != null) {
            result.documentProperties().setMessageDigest(digestStream.getMessageDigest().digest());
        }

        return result;
    }

    public XmlObject parse(Reader jior, SchemaType type, XmlOptions options) throws XmlException, IOException {
        XmlFactoryHook hook = XmlFactoryHook.ThreadContext.getHook();

        if (hook != null) {
            return hook.parse(this, jior, type, options);
        }

        return Locale.parseToXmlObject(this, jior, type, options);
    }

    public XmlObject parse(Node node, SchemaType type, XmlOptions options) throws XmlException {
        XmlFactoryHook hook = XmlFactoryHook.ThreadContext.getHook();

        if (hook != null) {
            return hook.parse(this, node, type, options);
        }

        return Locale.parseToXmlObject(this, node, type, options);
    }

    public XmlSaxHandler newXmlSaxHandler(SchemaType type, XmlOptions options) {
        XmlFactoryHook hook = XmlFactoryHook.ThreadContext.getHook();

        if (hook != null) {
            return hook.newXmlSaxHandler(this, type, options);
        }

        return Locale.newSaxHandler(this, type, options);
    }

    public DOMImplementation newDomImplementation(XmlOptions options) {
        return Locale.newDomImplementation(this, options);
    }

    //
    //
    //

    public String compilePath(String pathExpr) {
        return compilePath(pathExpr, null);
    }

    public String compilePath(String pathExpr, XmlOptions options) {
        return doCompilePath(pathExpr, options);
    }

    public String compileQuery(String queryExpr) {
        return compileQuery(queryExpr, null);
    }

    public String compileQuery(String queryExpr, XmlOptions options) {
        return doCompileQuery(queryExpr, options);
    }

    /**
     * Utility function to load a type from a signature.
     * <p>
     * A signature is the string you get from type.toString().
     */
    public SchemaType typeForSignature(String signature) {
        int end = signature.indexOf('@');
        String uri;

        if (end < 0) {
            uri = "";
            end = signature.length();
        } else {
            uri = signature.substring(end + 1);
        }

        List<String> parts = new ArrayList<>();

        for (int index = 0; index < end; ) {
            int nextc = signature.indexOf(':', index);
            int nextd = signature.indexOf('|', index);
            int next = (nextc < 0 ? nextd : nextd < 0 ? nextc : Math.min(nextc, nextd));
            if (next < 0 || next > end) {
                next = end;
            }
            String part = signature.substring(index, next);
            parts.add(part);
            index = next + 1;
        }

        SchemaType curType = null;

        for (int i = parts.size() - 1; i >= 0; i -= 1) {
            String part = parts.get(i);
            if (part.isEmpty()) {
                throw new IllegalArgumentException();
            }
            int offset = (part.length() >= 2 && part.charAt(1) == '=') ? 2 : 1;
            cases:
            switch (part.charAt(0)) {
                case 'T':
                    if (curType != null) {
                        throw new IllegalArgumentException();
                    }
                    curType = findType(QNameHelper.forLNS(part.substring(offset), uri));
                    if (curType == null) {
                        return null;
                    }
                    break;

                case 'D':
                    if (curType != null) {
                        throw new IllegalArgumentException();
                    }
                    curType = findDocumentType(QNameHelper.forLNS(part.substring(offset), uri));
                    if (curType == null) {
                        return null;
                    }
                    break;

                case 'C': // deprecated
                case 'R': // current
                    if (curType != null) {
                        throw new IllegalArgumentException();
                    }
                    curType = findAttributeType(QNameHelper.forLNS(part.substring(offset), uri));
                    if (curType == null) {
                        return null;
                    }
                    break;

                case 'E':
                case 'U': // distinguish qualified/unqualified TBD
                    if (curType != null) {
                        if (curType.getContentType() < SchemaType.ELEMENT_CONTENT) {
                            return null;
                        }
                        SchemaType[] subTypes = curType.getAnonymousTypes();
                        String localName = part.substring(offset);
                        for (SchemaType subType : subTypes) {
                            SchemaField field = subType.getContainerField();
                            if (field != null && !field.isAttribute() && field.getName().getLocalPart().equals(localName)) {
                                curType = subType;
                                break cases;
                            }
                        }
                        return null;
                    } else {
                        SchemaGlobalElement elt = findElement(QNameHelper.forLNS(part.substring(offset), uri));
                        if (elt == null) {
                            return null;
                        }
                        curType = elt.getType();
                    }
                    break;

                case 'A':
                case 'Q': // distinguish qualified/unqualified TBD
                    if (curType != null) {
                        if (curType.isSimpleType()) {
                            return null;
                        }
                        SchemaType[] subTypes = curType.getAnonymousTypes();
                        String localName = part.substring(offset);
                        for (SchemaType subType : subTypes) {
                            SchemaField field = subType.getContainerField();
                            if (field != null && field.isAttribute() && field.getName().getLocalPart().equals(localName)) {
                                curType = subType;
                                break cases;
                            }
                        }
                        return null;
                    } else {
                        SchemaGlobalAttribute attr = findAttribute(QNameHelper.forLNS(part.substring(offset), uri));
                        if (attr == null) {
                            return null;
                        }
                        curType = attr.getType();
                    }
                    break;

                case 'B':
                    if (curType == null) {
                        throw new IllegalArgumentException();
                    } else {
                        if (curType.getSimpleVariety() != SchemaType.ATOMIC) {
                            return null;
                        }
                        SchemaType[] subTypes = curType.getAnonymousTypes();
                        if (subTypes.length != 1) {
                            return null;
                        }
                        curType = subTypes[0];
                    }
                    break;

                case 'I':
                    if (curType == null) {
                        throw new IllegalArgumentException();
                    } else {
                        if (curType.getSimpleVariety() != SchemaType.LIST) {
                            return null;
                        }
                        SchemaType[] subTypes = curType.getAnonymousTypes();
                        if (subTypes.length != 1) {
                            return null;
                        }
                        curType = subTypes[0];
                    }
                    break;

                case 'M':
                    if (curType == null) {
                        throw new IllegalArgumentException();
                    } else {
                        int index = MathUtil.parseAsInt(part.substring(offset));

                        if (curType.getSimpleVariety() != SchemaType.UNION) {
                            return null;
                        }
                        SchemaType[] subTypes = curType.getAnonymousTypes();
                        if (subTypes.length <= index) {
                            return null;
                        }
                        curType = subTypes[index];
                    }
                    break;

                default:
                    throw new IllegalArgumentException();
            }
        }
        return curType;
    }
}
