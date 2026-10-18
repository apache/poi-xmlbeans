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

package org.apache.xmlbeans.impl.tool;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.xmlbeans.XmlError;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.xmlbeans.impl.tool.SchemaCompiler.parsePartialMethods;

@SuppressWarnings("unused")
@Mojo(name = "compile", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class MavenPlugin extends AbstractMojo {
    // ******************************************************************************************
    // As we don't use the maven plugin-plugin, the defaultValues and others need to be manually
    // copied into resources/maven/plugin.xml
    // ******************************************************************************************

    /** The maven project */
    @Parameter( readonly = true, defaultValue = "${project}" )
    private MavenProject project;

    /** sourceDir is a base directory for the list in sourceschema */
    @Parameter( defaultValue = "${project.basedir}/src/main/schema" )
    private String sourceDir;

    /** sourceSchemas is a comma-delimited list of all the schemas you want to compile */
    @Parameter( defaultValue = "*.xsd,*.wsdl,*.java" )
    private String sourceSchemas;

    /** recurseSourceSubdirs determines if subdirectories of sourceDir are checked for schemas */
    @Parameter( defaultValue = "false" )
    private boolean recurseSourceSubdirs;

    /** xmlConfigs points to your xmlconfig.xml file */
    @Parameter( defaultValue = "${project.basedir}/src/schema/xmlconfig.xml" )
    private String xmlConfigs;

    /** javaTargetdir is where you want generated java source to appear */
    @Parameter( defaultValue = "${project.basedir}/target/generated-sources" )
    private String javaTargetDir;

    /** classTargetDir is where you want compiled class files to appear */
    @Parameter( defaultValue = "${project.basedir}/target/generated-resources" )
    private String classTargetDir;

    /** catalogLocation is the location of an entity resolver catalog to use for resolving namespace to schema locations. */
    @Parameter
    private String catalogLocation;

    @Parameter
    private String classPath;

    @Parameter
    private List<Resource> resources;

    /** buildSchemas sets build process of the generated sources */
    @Parameter(defaultValue = "true")
    private boolean buildSchemas;

    /** destination directory of the copied xsd files - default: schemaorg_apache_xmlbeans/src */
    @Parameter( defaultValue = "schemaorg_apache_xmlbeans/src" )
    private String baseSchemaLocation;

    /** schema system name - default: ${project.artifactId} */
    @Parameter( defaultValue = "${project.artifactId}" )
    private String name;

    /** verbose output  - default: false */
    @Parameter( defaultValue = "false" )
    private boolean verbose;

    /** no output  - default: true */
    @Parameter( defaultValue = "true" )
    private boolean quiet;

    /** no output (deprecated - use quiet instead) - default: true */
    @Parameter( defaultValue = "true" )
    private boolean quite;

    /** deactivate unique particle attribution - default: false */
    @Parameter( defaultValue = "false" )
    private boolean noUpa;

    /** deactivate particle valid (restriction) - default: false */
    @Parameter( defaultValue = "false" )
    private boolean noPvr;

    /** deactivate annotation generation - default: false */
    @Parameter( defaultValue = "false" )
    private boolean noAnn;

    /** do not validate contents of documentation-tags - default: false */
    @Parameter( defaultValue = "false" )
    private boolean noVDoc;

    /** Metadata package name. If explicitly set empty, generates to org.apache.xmlbeans.metadata - default: ${project.groupId}.${project.artifactId}.metadata */
    @Parameter( defaultValue = "${project.groupId}.${project.artifactId}.metadata" )
    private String repackage;

    /**
     * If this option is set, then the schema compiler will permit and
     * ignore multiple definitions of the same component (element, attribute,
     * type, etc) names in the given namespaces.  If multiple definitions
     * with the same name appear, the definitions that happen to be processed
     * last will be ignored.
     *
     * a list of namespace URIs
     */
    @Parameter
    private List<String> mdefNamespaces;

    /**
     * Only generate a subset of the bean methods. Comma-seperated list of the following method types:
     * GET, XGET, IS_SET, IS_NIL, IS_NIL_IDX, SET, SET_NIL, SET_NIL_IDX, XSET, UNSET,
     * GET_ARRAY, XGET_ARRAY, GET_IDX, XGET_IDX, XSET_ARRAY, XSET_IDX,
     * SIZE_OF_ARRAY, SET_ARRAY, SET_IDX,
     * INSERT_IDX, INSERT_NEW_IDX,
     * ADD, ADD_NEW, REMOVE_IDX,
     * GET_LIST, XGET_LIST, SET_LIST,
     * INSTANCE_TYPE
     *
     * Example: "ALL,-GET_LIST,-XGET_LIST" excludes GET_LIST and XGET_LIST methods
     */
    @Parameter
    private String partialMethods;

    @Parameter( defaultValue = "false" )
    private boolean download;

    @Parameter( defaultValue = "true" )
    private boolean sourceOnly;

    @Parameter
    private File basedir;

    @Parameter
    private String compiler;

    @Parameter( defaultValue = CodeGenUtil.DEFAULT_MEM_START )
    private String memoryInitialSize;

    @Parameter( defaultValue = CodeGenUtil.DEFAULT_MEM_MAX )
    private String memoryMaximumSize;

    @Parameter( defaultValue = "${project.basedir}/target/${project.artifactId}-${project.version}-xmltypes.jar" )
    private File outputJar;

    @Parameter( defaultValue = "false" )
    private boolean debug;

    /** copy annotations to javadoc of generated sources - default: false */
    @Parameter( defaultValue = "false" )
    private boolean copyAnn;

    /**
     * The source code encoding to use when compiling the generated sources.
     *
     * @since 5.2.2
     */
    @Parameter
    private String sourceCodeEncoding;

    @Parameter
    private List<Extension> extensions;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (sourceDir == null || sourceDir.isEmpty() || !new File(sourceDir).isDirectory()) {
            throw new MojoFailureException("Set configuration <sourceDir> (='"+sourceDir+"') to a valid directory containing *.xsd,*.wsdl files.");
        }

        if (baseSchemaLocation == null || baseSchemaLocation.isEmpty()) {
            throw new MojoFailureException("baseSchemaLocation is empty");
        }

        if (sourceSchemas == null) {
            getLog().debug("sourceSchemas is null");
        }

        if (classPath == null) {
            getLog().debug("classPath is null");
        }

        final List<File> xsds = new ArrayList<>();
        final List<File> wsdls = new ArrayList<>();
        final List<File> javas = new ArrayList<>();
        File base = new File(sourceDir);
        Resource resource = new Resource();
        resource.setDirectory(sourceDir);
        resource.setTargetPath(baseSchemaLocation);

        // if sourceSchemas is not specified use all found schemas
        // otherwise convert comma-separated string to regex including glob parameter
        Pattern pat = Pattern.compile(sourceSchemas == null ? ".*" :
                "(" + sourceSchemas
                        .replace(",","|")
                        .replace(".", "\\.")
                        .replace("*",".*") +
                        ")");
        final Collection<File> schemaFiles = FileUtil.find(base, pat, recurseSourceSubdirs);
        for (File sf : schemaFiles) {
            String name = sf.getName();
            switch (name.substring(name.lastIndexOf('.') + 1)) {
                case "wsdl":
                    wsdls.add(sf);
                    break;
                case "java":
                    javas.add(sf);
                    break;
                default:
                    xsds.add(sf);
                    break;
            }
            resource.addInclude(name);
        }

        resources = Collections.singletonList(resource);

        if (buildSchemas) {
            List<File> configs = (xmlConfigs == null || xmlConfigs.isEmpty()) ? Collections.emptyList()
                    : Stream.of(xmlConfigs.split(",")).flatMap(s ->
                    Stream.of(new File(s), new File(base, s)).filter(File::exists)
            ).collect(Collectors.toList());

            List<File> classPathList = new ArrayList<>();
            List<URL> urls = new ArrayList<>();
            if (classPath != null) {
                for (String classpathElement : classPath.split(",")) {
                    File file = new File(classpathElement);
                    classPathList.add(file);
                    try {
                        urls.add(file.toURI().toURL());
                    } catch (MalformedURLException e) {
                        throw new MojoFailureException("invalid classpath: "+file, e);
                    }
                }
            }
            ClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]));
            EntityResolver entityResolver = MavenPluginResolver.getResolver(catalogLocation);
            URI sourceDirURI = new File(sourceDir).toURI();
            entityResolver = new PassThroughResolver(cl, entityResolver, sourceDirURI, baseSchemaLocation);

            Parameters params = new Parameters();
            params.setXsdFiles(files(xsds));
            params.setWsdlFiles(files(wsdls));
            params.setJavaFiles(files(javas));
            params.setConfigFiles(files(configs));
            params.setClasspath(files(classPathList));
            params.setName(name);
            params.setSrcDir(new File(javaTargetDir));
            params.setClassesDir(new File(classTargetDir));
            params.setNojavac(sourceOnly);
            params.setVerbose(verbose);
            params.setEntityResolver(entityResolver);
            params.setQuiet(quiet && quite); //setting either quiet or quite to false will disable quiet mode
            params.setNoUpa(noUpa);
            params.setNoPvr(noPvr);
            params.setNoAnn(noAnn);
            params.setCopyAnn(copyAnn);
            params.setNoVDoc(noVDoc);
            if (repackage != null && !repackage.isEmpty()) {
                params.setRepackage("org.apache.xmlbeans.metadata:"+repackage);
            }
            if (mdefNamespaces != null && !mdefNamespaces.isEmpty()) {
                params.setMdefNamespaces(new HashSet<>(mdefNamespaces));
            }
            List<XmlError> errorList = new ArrayList<>();
            params.setErrorListener(errorList);

            if (partialMethods != null && !partialMethods.isEmpty()) {
                params.setPartialMethods(parsePartialMethods(partialMethods));
            }
            params.setDownload(download);
            params.setBaseDir(basedir);
            params.setCompiler(compiler);
            params.setMemoryInitialSize(memoryInitialSize);
            params.setMemoryMaximumSize(memoryMaximumSize);
            params.setOutputJar(outputJar);
            params.setDebug(debug);
            params.setExtensions(extensions);
            if (sourceCodeEncoding != null && !sourceCodeEncoding.isEmpty()) {
                params.setSourceCodeEncoding(sourceCodeEncoding);
            }

            boolean result = SchemaCompiler.compile(params);

            if (!result) {
                throw new MojoFailureException("Schema compilation failed!\n"+
                        errorList.stream().map(XmlError::toString).collect(Collectors.joining("\n"))
                );
            }

            Resource genResource = new Resource();
            genResource.setDirectory(classTargetDir);
            project.addResource(genResource);
            project.addCompileSourceRoot(javaTargetDir);
        }

    }

    private static File[] files(List<File> files) {
        return (files == null || files.isEmpty()) ? null : files.toArray(new File[0]);
    }

    private static class PassThroughResolver implements EntityResolver {
        private final ClassLoader cl;
        private final EntityResolver delegate;
        private final URI sourceDir;
        //this copy has an / appended
        private final String baseSchemaLocation;

        public PassThroughResolver(ClassLoader cl, EntityResolver delegate, URI sourceDir, String baseSchemaLocation) {
            this.cl = cl;
            this.delegate = delegate;
            this.sourceDir = sourceDir;
            this.baseSchemaLocation = baseSchemaLocation + "/";
        }

        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            if (delegate != null) {
                InputSource is = delegate.resolveEntity(publicId, systemId);
                if (is != null) {
                    return is;
                }
            }
            System.out.println("Could not resolve publicId: " + publicId + ", systemId: " + systemId + " from catalog");
            String localSystemId;
            try {
                localSystemId = sourceDir.relativize(new URI(systemId)).toString();
            } catch (URISyntaxException e) {
                throw new IOException("Could not relativeize systemId", e);
            }
            InputStream in = cl.getResourceAsStream(localSystemId);
            if (in != null) {
                System.out.println("found in classpath at: " + localSystemId);
                return new InputSource(in);
            }
            in = cl.getResourceAsStream(baseSchemaLocation + localSystemId);
            if (in != null) {
                System.out.println("found in classpath at: META-INF/" + localSystemId);
                return new InputSource(in);
            }
            System.out.println("Not found in classpath, looking in current directory: " + systemId);
            return new InputSource(systemId);
        }
    }

}
