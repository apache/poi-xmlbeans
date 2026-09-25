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
import org.apache.xmlbeans.impl.common.IOUtil;
import org.apache.xmlbeans.impl.common.XmlEncodingSniffer;
import org.apache.xmlbeans.impl.xb.xsdschema.ImportDocument.Import;
import org.apache.xmlbeans.impl.xb.xsdschema.IncludeDocument.Include;
import org.apache.xmlbeans.impl.xb.xsdschema.RedefineDocument.Redefine;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument.Schema;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

public class StscImporter {
    public static SchemaToProcess[] resolveImportsAndIncludes(Schema[] startWith, boolean forceSrcSave) {
        DownloadTable engine = new DownloadTable(startWith);
        return engine.resolveImportsAndIncludes(forceSrcSave);
    }

    public static class SchemaToProcess {
        private final Schema schema;
        private final String chameleonNamespace;
        // list of SchemaToProcess objects directly included by this
        private List<SchemaToProcess> includes;
        // list of SchemaToProcess objects directly redefined by this
        private List<SchemaToProcess> redefines;
        // list of Redefine objects associated to each redefinition
        private List<Redefine> redefineObjects;
        // set of SchemaToProcess  objects directly/indirectly included by this
        private Set<SchemaToProcess> indirectIncludes;
        // set of SchemaToProcess objects that include this directly/indirectly
        private Set<SchemaToProcess> indirectIncludedBy;

        public SchemaToProcess(Schema schema, String chameleonNamespace) {
            this.schema = schema;
            this.chameleonNamespace = chameleonNamespace;
        }

        /**
         * The schema to parse.
         */
        public Schema getSchema() {
            return schema;
        }

        /**
         * The base URI for this stp
         */
        public String getSourceName() {
            return schema.documentProperties().getSourceName();
        }

        /**
         * The chameleon namespace. Null if this schema is not being treated
         * as a chameleon. (The ordinary targetNamespace will just be extracted
         * from the syntax of the schema.)
         */
        public String getChameleonNamespace() {
            return chameleonNamespace;
        }

        /**
         * This method and the remaining methods are used to represent a
         * directed graph of includes/redefines. This is required in order
         * to establish identity component by component, as required in
         * xmlschema-1, chapter 4.2.2
         */
        public List<SchemaToProcess> getRedefines() {
            return redefines;
        }

        public List<Redefine> getRedefineObjects() {
            return redefineObjects;
        }

        private void addInclude(SchemaToProcess include) {
            if (includes == null) {
                includes = new ArrayList<>();
            }
            includes.add(include);
        }

        private void addRedefine(SchemaToProcess redefine, Redefine object) {
            if (redefines == null || redefineObjects == null) {
                redefines = new ArrayList<>();
                redefineObjects = new ArrayList<>();
            }
            redefines.add(redefine);
            redefineObjects.add(object);
        }

        private void buildIndirectReferences() {
            if (includes != null) {
                for (SchemaToProcess schemaToProcess : includes) {
                    /* We have a this-schemaToProcess vertex
                     * This means that all nodes accessible from schemaToProcess are
                     * also accessible from this and all nodes that have access to
                     * this also have access to schemaToProcess */
                    this.addIndirectIncludes(schemaToProcess);
                }
            }
            // Repeat the same algorithm for redefines, since redefines are also includes
            if (redefines != null) {
                for (SchemaToProcess schemaToProcess : redefines) {
                    this.addIndirectIncludes(schemaToProcess);
                }
            }
        }

        private void addIndirectIncludes(SchemaToProcess schemaToProcess) {
            if (indirectIncludes == null) {
                indirectIncludes = new HashSet<>();
            }
            indirectIncludes.add(schemaToProcess);
            if (schemaToProcess.indirectIncludedBy == null) {
                schemaToProcess.indirectIncludedBy = new HashSet<>();
            }
            schemaToProcess.indirectIncludedBy.add(this);
            addIndirectIncludesHelper(this, schemaToProcess);
            if (indirectIncludedBy != null) {
                for (SchemaToProcess stp : indirectIncludedBy) {
                    stp.indirectIncludes.add(schemaToProcess);
                    schemaToProcess.indirectIncludedBy.add(stp);
                    addIndirectIncludesHelper(stp, schemaToProcess);
                }
            }
        }

        private static void addIndirectIncludesHelper(SchemaToProcess including,
                                                      SchemaToProcess schemaToProcess) {
            if (schemaToProcess.indirectIncludes != null) {
                for (SchemaToProcess stp : schemaToProcess.indirectIncludes) {
                    including.indirectIncludes.add(stp);
                    stp.indirectIncludedBy.add(including);
                }
            }
        }

        public boolean indirectIncludes(SchemaToProcess schemaToProcess) {
            return indirectIncludes != null && indirectIncludes.contains(schemaToProcess);
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SchemaToProcess)) {
                return false;
            }

            final SchemaToProcess schemaToProcess = (SchemaToProcess) o;

            if (!Objects.equals(chameleonNamespace, schemaToProcess.chameleonNamespace)) {
                return false;
            }
            return schema == schemaToProcess.schema;
        }

        public int hashCode() {
            int result;
            result = schema.hashCode();
            result = 29 * result + (chameleonNamespace != null ? chameleonNamespace.hashCode() : 0);
            return result;
        }
    }

    private final static String PROJECT_URL_PREFIX = "project://local";

    private static String baseURLForDoc(XmlObject obj) {
        String path = obj.documentProperties().getSourceName();

        if (path == null) {
            return null;
        }

        if (path.startsWith("/")) {
            return PROJECT_URL_PREFIX + path.replace('\\', '/');
        }

        // looks like a URL?
        int colon = path.indexOf(':');
        if (colon > 1 && path.substring(0, colon).matches("^\\w+$")) {
            return path;
        }

        return PROJECT_URL_PREFIX + "/" + path.replace('\\', '/');
    }

    private static URI parseURI(String s) {
        if (s == null) {
            return null;
        }

        try {
            return new URI(s);
        } catch (URISyntaxException syntax) {
            return null;
        }
    }

    //workaround for Sun bug # 4723726
    public static URI resolve(URI base, String child)
        throws URISyntaxException {
        URI childUri = new URI(child.replace('\\', '/'));
        URI ruri = base.resolve(childUri);

        // if the child fragment is relative (which we'll assume is the case
        // if URI.resolve doesn't do anything useful with it) and the base
        // URI is pointing at something nested inside a jar, we seem to have
        // to this ourselves to make sure that the nested jar url gets
        // resolved correctly
        if (childUri.equals(ruri) && !childUri.isAbsolute() &&
            (base.getScheme().equals("jar") || base.getScheme().equals("zip"))) {
            String r = base.toString();
            int lastslash = r.lastIndexOf('/');
            r = r.substring(0, lastslash) + "/" + childUri;
            // Sun's implementation of URI doesn't support references to the
            // parent directory ("/..") in the part after "!/" so we have to
            // remove these ourselves
            int exclPointSlashIndex = r.lastIndexOf("!/");
            if (exclPointSlashIndex > 0) {
                int slashDotDotIndex = r.indexOf("/..", exclPointSlashIndex);
                while (slashDotDotIndex > 0) {
                    int prevSlashIndex = r.lastIndexOf("/", slashDotDotIndex - 1);
                    if (prevSlashIndex >= exclPointSlashIndex) {
                        String temp = r.substring(slashDotDotIndex + 3);
                        r = r.substring(0, prevSlashIndex).concat(temp);
                    }
                    slashDotDotIndex = r.indexOf("/..", exclPointSlashIndex);
                }
            }
            return URI.create(r);
        }

        //fix up normalization bug
        if ("file".equals(ruri.getScheme()) && !child.equals(ruri.getPath())) {
            if (base.getPath().startsWith("//") && !ruri.getPath().startsWith("//")) {
                String path = "///".concat(ruri.getPath());
                try {
                    ruri = new URI("file", null, path, ruri.getQuery(), ruri.getFragment());
                } catch (URISyntaxException ignored) {
                }
            }
        }
        return ruri;
    }

    public static class DownloadTable {
        /**
         * Namespace/schemaLocation pair.
         * <p>
         * Downloaded schemas are indexed by namespace, schemaLocation, and both.
         * <p>
         * A perfect match is preferred, but a match-by-namespace is accepted.
         * A match-by-schemaLocation is only accepted for includes (not imports).
         */
        private static class NsLocPair {
            private final String namespaceURI;
            private final String locationURL;

            public NsLocPair(String namespaceURI, String locationURL) {
                this.namespaceURI = namespaceURI;
                this.locationURL = locationURL;
            }

            /**
             * Empty string for no-namespace, null for namespace-not-part-of-key
             */
            public String getNamespaceURI() {
                return namespaceURI;
            }

            public String getLocationURL() {
                return locationURL;
            }

            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof NsLocPair)) {
                    return false;
                }

                final NsLocPair nsLocPair = (NsLocPair) o;

                if (!Objects.equals(locationURL, nsLocPair.locationURL)) {
                    return false;
                }
                return Objects.equals(namespaceURI, nsLocPair.namespaceURI);
            }

            public int hashCode() {
                int result;
                result = (namespaceURI != null ? namespaceURI.hashCode() : 0);
                result = 29 * result + (locationURL != null ? locationURL.hashCode() : 0);
                return result;
            }
        }

        private static class DigestKey {
            byte[] _digest;
            int _hashCode;

            DigestKey(byte[] digest) {
                _digest = digest;
                for (int i = 0; i < 4 && i < digest.length; i++) {
                    _hashCode = _hashCode << 8;
                    _hashCode = _hashCode + digest[i];
                }
            }

            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof DigestKey)) {
                    return false;
                }
                return Arrays.equals(_digest, ((DigestKey) o)._digest);
            }

            public int hashCode() {
                return _hashCode;
            }
        }

        private final Map<NsLocPair, Schema> schemaByNsLocPair = new HashMap<>();
        private final Map<DigestKey, Schema> schemaByDigestKey = new HashMap<>();
        private final LinkedList<SchemaToProcess> scanNeeded = new LinkedList<>();
        private final Set<Schema> emptyNamespaceSchemas = new HashSet<>();
        private final Map<SchemaToProcess, SchemaToProcess> scannedAlready = new HashMap<>();
        private final Set<String> failedDownloads = new HashSet<>();

        private Schema downloadSchema(XmlObject referencedBy, String targetNamespace, String locationURL) {
            // no location URL provided?  Then nothing to do.
            if (locationURL == null) {
                return null;
            }

            StscState state = StscState.get();

            // First resolve relative URLs with respect to base URL for doc
            URI baseURI = parseURI(baseURLForDoc(referencedBy));
            final String absoluteURL;
            try {
                absoluteURL = baseURI == null ? locationURL : resolve(baseURI, locationURL).toString();
            } catch (URISyntaxException e) {
                state.error("Could not find resource - invalid location URL: " + e.getMessage(), XmlErrorCodes.CANNOT_FIND_RESOURCE, referencedBy);
                return null;
            }

            assert (absoluteURL != null);

            // probe 0: this url is already processed, from a previous compile
            if (state.isFileProcessed(absoluteURL)) {
                return null;
            }

            // probe 1: ns+url - perfect match
            if (targetNamespace != null) {
                Schema result = schemaByNsLocPair.get(new NsLocPair(targetNamespace, absoluteURL));
                if (result != null) {
                    return result;
                }
            }

            // probe 2: we have preexisting knowledge of this namespace,
            // either from another schema file or from the linker.
            // If we're not downloading the given URL, skip it silently if the
            // namespace is already represented by a file we have.
            // Also, suppress downloads of URLs to namespaces that are already
            // known by the linker.
            // (We never assume preexisting knowledge of the no-namespace,
            // even if we have some definitions, since it's likely that
            // more than one person is playing in the no-namespace at once.)
            if (targetNamespace != null && !targetNamespace.equals("")) {
                // the URL is not one to download; should we assume we know about the namespace?
                if (!state.shouldDownloadURI(absoluteURL)) {
                    // If we already have a schema representing this namespace,
                    // then skip this URL silently without producing an error.
                    Schema result = schemaByNsLocPair.get(new NsLocPair(targetNamespace, null));
                    if (result != null) {
                        return result;
                    }
                }

                // If the linker already knows about this namespace, skip
                // this URL.
                if (state.linkerDefinesNamespace(targetNamespace)) {
                    return null;
                }
            }

            // probe 3: url only
            final Schema result2 = schemaByNsLocPair.get(new NsLocPair(null, absoluteURL));
            if (result2 != null) {
                return result2;
            }

            // no match: error if we can't or won't download.
            if (previouslyFailedToDownload(absoluteURL)) {
                // an error message has already been produced.
                return null;
            }

            if (!state.shouldDownloadURI(absoluteURL)) {
                state.error("Could not load resource \"" + absoluteURL + "\" (network downloads disabled).", XmlErrorCodes.CANNOT_FIND_RESOURCE, referencedBy);
                addFailedDownload(absoluteURL);
                return null;
            }

            // try to download
            download:
            try {
                XmlObject xdoc = downloadDocument(state.getS4SLoader(), targetNamespace, absoluteURL);

                Schema result = findMatchByDigest(xdoc);
                String shortname = state.relativize(absoluteURL);
                if (result != null) {
                    // if an exactly-the-same document has already been loaded, use the original and spew
                    String dupname = state.relativize(result.documentProperties().getSourceName());
                    if (dupname != null) {
                        state.info(shortname + " is the same as " + dupname + " (ignoring the duplicate file)");
                    } else {
                        state.info(shortname + " is the same as another schema");
                    }
                } else {
                    // otherwise, it's a new document: validate it and grab the contents
                    XmlOptions voptions = new XmlOptions();
                    voptions.setErrorListener(state.getErrorListener());
                    if (!(xdoc instanceof SchemaDocument) || !xdoc.validate(voptions)) {
                        state.error("Referenced document is not a valid schema", XmlErrorCodes.CANNOT_FIND_RESOURCE, referencedBy);
                        break download;
                    }

                    SchemaDocument sDoc = (SchemaDocument) xdoc;

                    result = sDoc.getSchema();
                    state.info("Loading referenced file " + shortname);
                }
                NsLocPair key = new NsLocPair(emptyStringIfNull(result.getTargetNamespace()), absoluteURL);
                addSuccessfulDownload(key, result);
                return result;
            } catch (MalformedURLException malformed) {
                state.error("URL \"" + absoluteURL + "\" is not well-formed", XmlErrorCodes.CANNOT_FIND_RESOURCE, referencedBy);
            } catch (IOException connectionProblem) {
                state.error(connectionProblem.toString(), XmlErrorCodes.CANNOT_FIND_RESOURCE, referencedBy);
            } catch (XmlException e) {
                state.error("Problem parsing referenced XML resource - " + e.getMessage(), XmlErrorCodes.CANNOT_FIND_RESOURCE, referencedBy);
            }

            // record failure so that we don't try to download this URL again
            addFailedDownload(absoluteURL);
            return null;
        }

        static XmlObject downloadDocument(SchemaTypeLoader loader, String namespace, String absoluteURL)
            throws IOException, XmlException {
            StscState state = StscState.get();

            EntityResolver resolver = state.getEntityResolver();
            if (resolver != null) {
                InputSource source;
                try {
                    source = resolver.resolveEntity(namespace, absoluteURL);
                } catch (SAXException e) {
                    throw new XmlException(e);
                }

                if (source != null) {
                    state.addSourceUri(absoluteURL, null);

                    // first preference for InputSource contract: character stream
                    Reader reader = source.getCharacterStream();
                    if (reader != null) {
                        reader = copySchemaSource(absoluteURL, reader, state);
                        XmlOptions options = new XmlOptions();
                        options.setLoadLineNumbers();
                        options.setDocumentSourceName(absoluteURL);
                        options.setCompileNoAnnotations(state.noAnn());
                        options.setCompileNoPvrRule(state.noPvr());
                        options.setCompileNoPvrRule(state.noUpa());
                        return loader.parse(reader, null, options);
                    }

                    // second preference for InputSource contract:
                    InputStream bytes = source.getByteStream();
                    if (bytes != null) {
                        bytes = copySchemaSource(absoluteURL, bytes, state);
                        String encoding = source.getEncoding();
                        XmlOptions options = new XmlOptions();
                        options.setLoadLineNumbers();
                        options.setLoadMessageDigest();
                        options.setDocumentSourceName(absoluteURL);
                        if (encoding != null) {
                            options.setCharacterEncoding(encoding);
                        }
                        return loader.parse(bytes, null, options);
                    }

                    // third preference: use the (possibly redirected) url
                    String urlToLoad = source.getSystemId();
                    if (urlToLoad == null) {
                        throw new IOException("EntityResolver unable to resolve " + absoluteURL + " (for namespace " + namespace + ")");
                    }

                    copySchemaSource(absoluteURL, state, false);
                    XmlOptions options = new XmlOptions();
                    options.setLoadLineNumbers();
                    options.setLoadMessageDigest();
                    options.setDocumentSourceName(absoluteURL);
                    URL urlDownload = new URL(urlToLoad);
                    return loader.parse(urlDownload, null, options);
                }
            }

            // no resolver - just use the URL directly, no substitution
            state.addSourceUri(absoluteURL, null);
            copySchemaSource(absoluteURL, state, false);

            XmlOptions options = new XmlOptions();
            options.setLoadLineNumbers();
            options.setLoadMessageDigest();
            URL urlDownload = new URL(absoluteURL);

            return loader.parse(urlDownload, null, options);
        }

        private void addSuccessfulDownload(NsLocPair key, Schema schema) {
            byte[] digest = schema.documentProperties().getMessageDigest();
            if (digest == null) {
                StscState.get().addSchemaDigest(null);
            } else {
                DigestKey dk = new DigestKey(digest);
                if (!schemaByDigestKey.containsKey(dk)) {
                    schemaByDigestKey.put(new DigestKey(digest), schema);
                    StscState.get().addSchemaDigest(digest);
                }
            }

            schemaByNsLocPair.put(key, schema);
            NsLocPair key1 = new NsLocPair(key.getNamespaceURI(), null);
            if (!schemaByNsLocPair.containsKey(key1)) {
                schemaByNsLocPair.put(key1, schema);
            }
            NsLocPair key2 = new NsLocPair(null, key.getLocationURL());
            if (!schemaByNsLocPair.containsKey(key2)) {
                schemaByNsLocPair.put(key2, schema);
            }
        }

        private Schema findMatchByDigest(XmlObject original) {
            byte[] digest = original.documentProperties().getMessageDigest();
            if (digest == null) {
                return null;
            }
            return schemaByDigestKey.get(new DigestKey(digest));
        }

        private void addFailedDownload(String locationURL) {
            failedDownloads.add(locationURL);
        }

        private boolean previouslyFailedToDownload(String locationURL) {
            return failedDownloads.contains(locationURL);
        }

        private static boolean nullableStringsMatch(String s1, String s2) {
            if (s1 == null && s2 == null) {
                return true;
            }
            if (s1 == null || s2 == null) {
                return false;
            }
            return (s1.equals(s2));
        }

        private static String emptyStringIfNull(String s) {
            if (s == null) {
                return "";
            }
            return s;
        }

        private SchemaToProcess addScanNeeded(SchemaToProcess stp) {
            if (!scannedAlready.containsKey(stp)) {
                scannedAlready.put(stp, stp);
                scanNeeded.add(stp);
                return stp;
            } else {
                return scannedAlready.get(stp);
            }
        }

        private void addEmptyNamespaceSchema(Schema s) {
            emptyNamespaceSchemas.add(s);
        }

        private void usedEmptyNamespaceSchema(Schema s) {
            emptyNamespaceSchemas.remove(s);
        }

        private boolean fetchRemainingEmptyNamespaceSchemas() {
            if (emptyNamespaceSchemas.isEmpty()) {
                return false;
            }

            for (Schema schema : emptyNamespaceSchemas) {
                addScanNeeded(new SchemaToProcess(schema, null));
            }

            emptyNamespaceSchemas.clear();
            return true;
        }

        private boolean hasNextToScan() {
            return !scanNeeded.isEmpty();
        }

        private SchemaToProcess nextToScan() {
            return scanNeeded.removeFirst();
        }

        public DownloadTable(Schema[] startWith) {
            for (Schema schema : startWith) {
                String targetNamespace = schema.getTargetNamespace();
                NsLocPair key = new NsLocPair(targetNamespace, baseURLForDoc(schema));
                addSuccessfulDownload(key, schema);
                if (targetNamespace != null) {
                    addScanNeeded(new SchemaToProcess(schema, null));
                } else {
                    addEmptyNamespaceSchema(schema);
                }
            }
        }

        public SchemaToProcess[] resolveImportsAndIncludes(boolean forceSave) {
            StscState state = StscState.get();
            List<SchemaToProcess> result = new ArrayList<>();
            boolean hasRedefinitions = false;

            // algorithm is to scan through each schema document and
            // 1. download each import and include (if not already downloaded)
            // 2. queue each imported or included schema to be process (if not already queued)

            // The algorithm is run twice: first we begin with non-empty
            // namespace schemas only.  Then we repeat starting with any
            // empty empty-namespace schemas that have NOT been chameleon-
            // included by other schemas and process them.

            do {
                while (hasNextToScan()) {
                    SchemaToProcess stp = nextToScan();
                    String uri = stp.getSourceName();
                    state.addSourceUri(uri, null);
                    result.add(stp);
                    copySchemaSource(uri, state, forceSave);

                    {
                        // handle imports
                        Import[] imports = stp.getSchema().getImportArray();
                        for (Import anImport : imports) {
                            Schema imported = downloadSchema(anImport, emptyStringIfNull(anImport.getNamespace()), anImport.getSchemaLocation());

                            // if download fails, an error has already been reported.
                            if (imported == null) {
                                continue;
                            }

                            if (!nullableStringsMatch(imported.getTargetNamespace(), anImport.getNamespace())) {
                                StscState.get().error("Imported schema has a target namespace \"" + imported.getTargetNamespace() + "\" that does not match the specified \"" + anImport.getNamespace() + "\"", XmlErrorCodes.MISMATCHED_TARGET_NAMESPACE, anImport);
                            } else {
                                addScanNeeded(new SchemaToProcess(imported, null));
                            }
                        }
                    }

                    {
                        // handle includes
                        Include[] includes = stp.getSchema().getIncludeArray();
                        String sourceNamespace = stp.getChameleonNamespace();
                        if (sourceNamespace == null) {
                            sourceNamespace = emptyStringIfNull(stp.getSchema().getTargetNamespace());
                        }

                        for (Include include : includes) {
                            Schema included = downloadSchema(include, null, include.getSchemaLocation());
                            // if download fails, an error has already been reported.
                            if (included == null) {
                                continue;
                            }

                            if (emptyStringIfNull(included.getTargetNamespace()).equals(sourceNamespace)) {
                                // non-chameleon case - just like an import
                                SchemaToProcess s = addScanNeeded(new SchemaToProcess(included, null));
                                stp.addInclude(s);
                            } else if (included.getTargetNamespace() != null) {
                                // illegal include: included schema in wrong namespace.
                                StscState.get().error("Included schema has a target namespace \"" + included.getTargetNamespace() + "\" that does not match the source namespace \"" + sourceNamespace + "\"", XmlErrorCodes.MISMATCHED_TARGET_NAMESPACE, include);
                            } else {
                                // chameleon include
                                SchemaToProcess s = addScanNeeded(new SchemaToProcess(included, sourceNamespace));
                                stp.addInclude(s);
                                usedEmptyNamespaceSchema(included);
                            }
                        }
                    }

                    {
                        // handle redefines
                        Redefine[] redefines = stp.getSchema().getRedefineArray();
                        String sourceNamespace = stp.getChameleonNamespace();
                        if (sourceNamespace == null) {
                            sourceNamespace = emptyStringIfNull(stp.getSchema().getTargetNamespace());
                        }
                        for (Redefine redefine : redefines) {
                            Schema redefined = downloadSchema(redefine, null, redefine.getSchemaLocation());
                            // if download fails, an error has already been reported.
                            if (redefined == null) {
                                continue;
                            }

                            if (emptyStringIfNull(redefined.getTargetNamespace()).equals(sourceNamespace)) {
                                // non-chameleon case
                                SchemaToProcess s = addScanNeeded(new SchemaToProcess(redefined, null));
                                stp.addRedefine(s, redefine);
                                hasRedefinitions = true;
                            } else if (redefined.getTargetNamespace() != null) {
                                // illegal include: included schema in wrong namespace.
                                StscState.get().error("Redefined schema has a target namespace \"" + redefined.getTargetNamespace() + "\" that does not match the source namespace \"" + sourceNamespace + "\"", XmlErrorCodes.MISMATCHED_TARGET_NAMESPACE, redefine);
                            } else {
                                // chameleon redefine
                                SchemaToProcess s = addScanNeeded(new SchemaToProcess(redefined, sourceNamespace));
                                stp.addRedefine(s, redefine);
                                usedEmptyNamespaceSchema(redefined);
                                hasRedefinitions = true;
                            }
                        }
                    }
                }

            } while (fetchRemainingEmptyNamespaceSchemas());

            // Build the lists of indirect references
            // Make all the effort only if there are redefinitions
            if (hasRedefinitions) {
                for (SchemaToProcess schemaToProcess : result) {
                    schemaToProcess.buildIndirectReferences();
                }
            }
            return result.toArray(new SchemaToProcess[0]);
        }

        private static Reader copySchemaSource(String url, Reader reader, StscState state) {
            //Copy the schema file if it wasn't already copied
            if (state.getSchemasDir() == null) {
                return reader;
            }

            String schemalocation = state.sourceNameForUri(url);
            File targetFile = new File(state.getSchemasDir(), schemalocation);
            if (targetFile.exists()) {
                return reader;
            }

            try {
                File parentDir = new File(targetFile.getParent());
                IOUtil.createDir(parentDir, null);

                CharArrayReader car = copy(reader);
                XmlEncodingSniffer xes = new XmlEncodingSniffer(car, null);
                try (Writer out = new OutputStreamWriter(
                        Files.newOutputStream(targetFile.toPath()), xes.getXmlEncoding())) {
                    IOUtil.copyCompletely(car, out);
                }

                car.reset();
                return car;
            } catch (IOException e) {
                System.err.println("IO Error " + e);
                return reader;
            }
        }

        private static InputStream copySchemaSource(String url, InputStream bytes, StscState state) {
            //Copy the schema file if it wasn't already copied
            if (state.getSchemasDir() == null) {
                return bytes;
            }

            String schemalocation = state.sourceNameForUri(url);
            File targetFile = new File(state.getSchemasDir(), schemalocation);
            if (targetFile.exists()) {
                return bytes;
            }

            try {
                File parentDir = new File(targetFile.getParent());
                IOUtil.createDir(parentDir, null);

                ByteArrayInputStream bais = copy(bytes);

                try (OutputStream out = Files.newOutputStream(targetFile.toPath())) {
                    IOUtil.copyCompletely(bais, out);
                }

                bais.reset();
                return bais;
            } catch (IOException e) {
                System.err.println("IO Error " + e);
                return bytes;
            }
        }

        private static void copySchemaSource(String urlLoc, StscState state, boolean forceCopy) {
            //Copy the schema file if it wasn't already copied
            if (state.getSchemasDir() != null) {
                String schemalocation = state.sourceNameForUri(urlLoc);

                File targetFile = new File(state.getSchemasDir(), schemalocation);
                if (forceCopy || !targetFile.exists()) {
                    InputStream in = null;
                    try {
                        File parentDir = new File(targetFile.getParent());
                        IOUtil.createDir(parentDir, null);

                        URL url = new URL(urlLoc);
                        // Copy the file from filepath to schema[METADATA_PACKAGE_GEN]/src/<schemaFile>
                        try {
                            in = url.openStream();
                        } catch (FileNotFoundException fnfe) {
                            if (forceCopy && targetFile.exists()) {
                                targetFile.delete();
                            } else {
                                throw fnfe;
                            }
                        }
                        if (in != null) {
                            OutputStream out = Files.newOutputStream(targetFile.toPath());
                            IOUtil.copyCompletely(in, out);
                        }
                    } catch (IOException e) {
                        System.err.println("IO Error " + e);
                        // failure = true; - not cause for failure
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }

        private static ByteArrayInputStream copy(InputStream is) throws IOException {
            byte[] buf = new byte[1024];
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                int bytesRead;
                while ((bytesRead = is.read(buf, 0, 1024)) > 0) {
                    baos.write(buf, 0, bytesRead);
                }

                return new ByteArrayInputStream(baos.toByteArray());
            }
        }

        private static CharArrayReader copy(Reader is) throws IOException {
            char[] buf = new char[1024];
            try (CharArrayWriter charArrayWriter = new CharArrayWriter()) {
                int charRead;
                while ((charRead = is.read(buf, 0, 1024)) > 0) {
                    charArrayWriter.write(buf, 0, charRead);
                }

                return new CharArrayReader(charArrayWriter.toCharArray());
            }
        }
    }
}
