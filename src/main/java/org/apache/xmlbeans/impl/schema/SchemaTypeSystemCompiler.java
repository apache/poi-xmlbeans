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
import org.apache.xmlbeans.impl.common.XmlErrorWatcher;
import org.apache.xmlbeans.impl.repackage.Repackager;
import org.apache.xmlbeans.impl.util.FilerImpl;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument.Schema;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.*;

public class SchemaTypeSystemCompiler {
    public static class Parameters {
        private SchemaTypeSystem existingSystem;
        private String name;
        private Schema[] schemas;
        private BindingConfig config;
        private SchemaTypeLoader linkTo;
        private XmlOptions options;
        private Collection<XmlError> errorListener;
        private boolean javaize;
        private URI baseURI;
        private Map<String, String> sourcesToCopyMap;
        private File schemasDir;

        public SchemaTypeSystem getExistingTypeSystem() {
            return existingSystem;
        }

        public void setExistingTypeSystem(SchemaTypeSystem system) {
            this.existingSystem = system;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public SchemaDocument.Schema[] getSchemas() {
            return schemas;
        }

        public void setSchemas(SchemaDocument.Schema[] schemas) {
            this.schemas = (schemas == null) ? null : schemas.clone();
        }

        public BindingConfig getConfig() {
            return config;
        }

        public void setConfig(BindingConfig config) {
            this.config = config;
        }

        public SchemaTypeLoader getLinkTo() {
            return linkTo;
        }

        public void setLinkTo(SchemaTypeLoader linkTo) {
            this.linkTo = linkTo;
        }

        public XmlOptions getOptions() {
            return options;
        }

        public void setOptions(XmlOptions options) {
            this.options = options;
        }

        public Collection<XmlError> getErrorListener() {
            return errorListener;
        }

        public void setErrorListener(Collection<XmlError> errorListener) {
            this.errorListener = errorListener;
        }

        public boolean isJavaize() {
            return javaize;
        }

        public void setJavaize(boolean javaize) {
            this.javaize = javaize;
        }

        public URI getBaseURI() {
            return baseURI;
        }

        public void setBaseURI(URI baseURI) {
            this.baseURI = baseURI;
        }

        public Map<String, String> getSourcesToCopyMap() {
            return sourcesToCopyMap;
        }

        public void setSourcesToCopyMap(Map<String, String> sourcesToCopyMap) {
            this.sourcesToCopyMap = sourcesToCopyMap;
        }

        public File getSchemasDir() {
            return schemasDir;
        }

        public void setSchemasDir(File schemasDir) {
            this.schemasDir = schemasDir;
        }

    }

    /**
     * Compiles a SchemaTypeSystem.  Use XmlBeans.compileXmlBeans() if you can.
     */
    public static SchemaTypeSystem compile(Parameters params) {
        return compileImpl(params.getExistingTypeSystem(), params.getName(),
            params.getSchemas(), params.getConfig(), params.getLinkTo(),
            params.getOptions(), params.getErrorListener(), params.isJavaize(),
            params.getBaseURI(), params.getSourcesToCopyMap(), params.getSchemasDir());
    }

    /**
     * Please do not invoke this method directly as the signature could change unexpectedly.
     * Use one of
     * {@link XmlBeans#loadXsd(XmlObject[])},
     * {@link XmlBeans#compileXsd(XmlObject[], SchemaTypeLoader, XmlOptions)},
     * or
     * {@link XmlBeans#compileXmlBeans(String, SchemaTypeSystem, XmlObject[], BindingConfig, SchemaTypeLoader, Filer, XmlOptions)}
     */
    public static SchemaTypeSystemImpl compile(String name, SchemaTypeSystem existingSTS,
                                               XmlObject[] input, BindingConfig config, SchemaTypeLoader linkTo, Filer filer, XmlOptions options)
        throws XmlException {
        options = XmlOptions.maskNull(options);
        ArrayList<Schema> schemas = new ArrayList<>();

        if (input != null) {
            for (int i = 0; i < input.length; i++) {
                if (input[i] instanceof Schema schema) {
                    schemas.add(schema);
                } else if (input[i] instanceof SchemaDocument document && document.getSchema() != null) {
                    schemas.add(document.getSchema());
                } else {
                    throw new XmlException("Thread " + Thread.currentThread().getName() + ": The " + i + "th supplied input is not a schema document: its type is " + input[i].schemaType());
                }
            }
        }

        Collection<XmlError> userErrors = options.getErrorListener();
        XmlErrorWatcher errorWatcher = new XmlErrorWatcher(userErrors);

        SchemaTypeSystemImpl stsi = compileImpl(existingSTS, name,
            schemas.toArray(new Schema[0]),
            config, linkTo, options, errorWatcher, filer != null, options.getBaseURI(),
            null, null);

        // if there is an error and compile didn't recover (stsi==null), throw exception
        if (errorWatcher.hasError() && stsi == null) {
            throw new XmlException(errorWatcher.firstError());
        }

        if (stsi != null && !stsi.isIncomplete() && filer != null) {
            stsi.save(filer);
            generateTypes(stsi, filer, options);
        }

        return stsi;
    }

    //
    // Compiles a SchemaTypeSystem
    //
    /* package */
    static SchemaTypeSystemImpl compileImpl(SchemaTypeSystem system, String name,
                                            Schema[] schemas, BindingConfig config, SchemaTypeLoader linkTo,
                                            XmlOptions options, Collection<XmlError> outsideErrors, boolean javaize,
                                            URI baseURI, Map<String, String> sourcesToCopyMap, File schemasDir) {
        if (linkTo == null) {
            throw new IllegalArgumentException("Must supply linkTo");
        }

        XmlErrorWatcher errorWatcher = new XmlErrorWatcher(outsideErrors);
        boolean incremental = system != null;

        // construct the state
        StscState state = StscState.start();
        boolean validate = (options == null || !options.isCompileNoValidation());
        try {
            state.setErrorListener(errorWatcher);
            state.setBindingConfig(config);
            state.setOptions(options);
            state.setGivenTypeSystemName(name);
            state.setSchemasDir(schemasDir);
            if (baseURI != null) {
                state.setBaseUri(baseURI);
            }

            // construct the classpath (you always get the builtin types)
            linkTo = SchemaTypeLoaderImpl.build(new SchemaTypeLoader[]{BuiltinSchemaTypeSystem.get(), linkTo}, null, null);
            state.setImportingTypeLoader(linkTo);

            List<Schema> validSchemas = new ArrayList<>(schemas.length);

            // load all the xsd files into it
            if (validate) {
                XmlOptions validateOptions = new XmlOptions().setErrorListener(errorWatcher);
                if (options != null) {
                    if (options.isValidateTreatLaxAsSkip()) {
                        validateOptions.setValidateTreatLaxAsSkip();
                    }
                    validateOptions.setMaxNumberOfCharsForNumbers(options.getMaxNumberOfCharsForNumbers());
                }
                for (Schema schema : schemas) {
                    if (schema.validate(validateOptions)) {
                        validSchemas.add(schema);
                    }
                }
            } else {
                validSchemas.addAll(Arrays.asList(schemas));
            }

            Schema[] startWith = validSchemas.toArray(new Schema[0]);

            if (incremental) {
                Set<String> namespaces = new HashSet<>();
                startWith = getSchemasToRecompile((SchemaTypeSystemImpl) system, startWith, namespaces);
                state.initFromTypeSystem((SchemaTypeSystemImpl) system, namespaces);
            } else {
                state.setDependencies(new SchemaDependencies());
            }

            // deal with imports and includes
            StscImporter.SchemaToProcess[] schemasAndChameleons = StscImporter.resolveImportsAndIncludes(startWith, incremental);

            // call the translator so that it may also perform magic
            StscTranslator.addAllDefinitions(schemasAndChameleons);

            // call the resolver to do its magic
            StscResolver.resolveAll();

            // call the checker to check both restrictions and defaults
            StscChecker.checkAll();

            // call the javaizer to do its magic
            StscJavaizer.javaizeAllTypes(javaize);

            // construct the loader out of the state
            StscState.get().sts().loadFromStscState(state);

            // fill in the source-copy map
            if (sourcesToCopyMap != null) {
                sourcesToCopyMap.putAll(state.sourceCopyMap());
            }

            if (errorWatcher.hasError()) {
                // EXPERIMENTAL: recovery from compilation errors and partial type system
                if (state.allowPartial() && state.getRecovered() == errorWatcher.size()) {
                    // if partial type system allowed and all errors were recovered
                    StscState.get().sts().setIncomplete(true);
                } else {
                    // if any non-recoverable errors, return null
                    return null;
                }
            }

            if (system != null) {
                ((SchemaTypeSystemImpl) system).setIncomplete(true);
            }

            return StscState.get().sts();
        } finally {
            StscState.end();
        }
    }

    /**
     * Get the list of Schemas to be recompiled, based on the list of Schemas that
     * were modified.
     * We make use of the depencency information that we stored in the typesystem
     * and of the entity resolvers that have been set up
     */
    private static Schema[] getSchemasToRecompile(SchemaTypeSystemImpl system,
                                                  Schema[] modified, Set<String> namespaces) {
        Set<String> modifiedFiles = new HashSet<>();
        Map<String, SchemaDocument.Schema> haveFile = new HashMap<>();
        List<SchemaDocument.Schema> result = new ArrayList<>();
        for (Schema schema : modified) {
            String fileURL = schema.documentProperties().getSourceName();
            if (fileURL == null) {
                throw new IllegalArgumentException("One of the Schema files passed in" +
                                                   " doesn't have the source set, which prevents it to be incrementally" +
                                                   " compiled");
            }
            modifiedFiles.add(fileURL);
            haveFile.put(fileURL, schema);
            result.add(schema);
        }
        SchemaDependencies dep = system.getDependencies();
        List<String> nss = dep.getNamespacesTouched(modifiedFiles);
        namespaces.addAll(dep.computeTransitiveClosure(nss));
        List<String> needRecompilation = dep.getFilesTouched(namespaces);
        StscState.get().setDependencies(new SchemaDependencies(dep, namespaces));
        for (String url : needRecompilation) {
            Schema have = haveFile.get(url);
            if (have == null) {
                // We have to load the file from the entity resolver
                try {
                    XmlObject xdoc = StscImporter.DownloadTable.
                        downloadDocument(StscState.get().getS4SLoader(), null, url);
                    XmlOptions voptions = new XmlOptions();
                    voptions.setErrorListener(StscState.get().getErrorListener());
                    if (!(xdoc instanceof SchemaDocument) || !xdoc.validate(voptions)) {
                        StscState.get().error("Referenced document is not a valid schema, URL = " + url, XmlErrorCodes.CANNOT_FIND_RESOURCE, null);
                        continue;
                    }

                    SchemaDocument sDoc = (SchemaDocument) xdoc;

                    result.add(sDoc.getSchema());
                } catch (java.net.MalformedURLException mfe) {
                    StscState.get().error(XmlErrorCodes.EXCEPTION_LOADING_URL, new Object[]{"MalformedURLException", url, mfe.getMessage()}, null);
                } catch (IOException ioe) {
                    StscState.get().error(XmlErrorCodes.EXCEPTION_LOADING_URL, new Object[]{"IOException", url, ioe.getMessage()}, null);
                } catch (XmlException xmle) {
                    StscState.get().error(XmlErrorCodes.EXCEPTION_LOADING_URL, new Object[]{"XmlException", url, xmle.getMessage()}, null);
                }
            }
        }
        return result.toArray(new Schema[0]);
    }


    /**
     * Generate java source files for a SchemaTypeSystem.
     * Please do not invoke this method directly as the signature could change unexpectedly.
     * Use {@link org.apache.xmlbeans.XmlBeans#compileXmlBeans}
     *
     * @param system  the SchemaTypeSystem to generated java source for
     * @param filer   to create the java source files
     * @param options See {@link XmlOptions#setSchemaCodePrinter(org.apache.xmlbeans.SchemaCodePrinter)}
     * @return true if saving the generated source succeeded.
     */
    public static boolean generateTypes(SchemaTypeSystem system, Filer filer, XmlOptions options) {
        // partial type systems not allowed to be saved
        if (system instanceof SchemaTypeSystemImpl impl && impl.isIncomplete()) {
            return false;
        }

        boolean success = true;

        List<SchemaType> types = new ArrayList<>();
        types.addAll(Arrays.asList(system.globalTypes()));
        types.addAll(Arrays.asList(system.documentTypes()));
        types.addAll(Arrays.asList(system.attributeTypes()));


        SchemaCodePrinter printer = options == null ? null : options.getSchemaCodePrinter();
        if (printer == null) {
            printer = new SchemaTypeCodePrinter();
        }

        String indexClassName = SchemaTypeCodePrinter.indexClassForSystem(system);

        try (Writer out = filer.createSourceFile(indexClassName, options == null ? null : options.getCharacterEncoding())) {
            Repackager repackager = (filer instanceof FilerImpl fi) ? fi.getRepackager() : null;
            printer.printHolder(out, system, options, repackager);
        } catch (IOException e) {
            System.err.println("IO Error " + e);
            success = false;
        }

        for (SchemaType type : types) {
            if (type.isBuiltinType()) {
                continue;
            }
            if (type.getFullJavaName() == null) {
                continue;
            }

            String fjn = type.getFullJavaName();

            try (Writer writer = filer.createSourceFile(fjn, options == null ? null : options.getCharacterEncoding())) {
                // Generate interface class
                printer.printType(writer, type, options);
            } catch (IOException e) {
                System.err.println("IO Error " + e);
                success = false;
            }

            fjn = type.getFullJavaImplName();

            try (Writer writer = filer.createSourceFile(fjn, options == null ? null : options.getCharacterEncoding())) {
                // Generate Implementation class
                printer.printTypeImpl(writer, type, options);
            } catch (IOException e) {
                System.err.println("IO Error " + e);
                success = false;
            }
        }

        return success;
    }
}
