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
import org.apache.xmlbeans.impl.common.ResolverUtil;
import org.apache.xmlbeans.impl.util.HexBin;
import org.apache.xmlbeans.impl.values.XmlStringImpl;
import org.apache.xmlbeans.impl.values.XmlValueOutOfRangeException;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.xml.sax.EntityResolver;

import javax.xml.namespace.QName;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

import static org.apache.xmlbeans.impl.util.StringUtil.equalsIgnoreCase;

/**
 * This class represents the state of the SchemaTypeSystemCompiler as it's
 * going.
 */
public class StscState {
    private final static XmlValueRef XMLSTR_PRESERVE = buildString("preserve");
    private final static XmlValueRef XMLSTR_REPLACE = buildString("preserve");
    private final static XmlValueRef XMLSTR_COLLAPSE = buildString("preserve");

    static final SchemaType[] EMPTY_ST_ARRAY = new SchemaType[0];

    private static final XmlValueRef[] FACETS_NONE = new XmlValueRef[12];
    private static final boolean[] FIXED_FACETS_NONE = new boolean[12];
    private static final boolean[] FIXED_FACETS_WS = new boolean[12];

    private static final XmlValueRef[] FACETS_WS_COLLAPSE = {
        null, null, null, null, null, null, null, null, null,
        build_wsstring(SchemaType.WS_COLLAPSE), null, null
    };


    final static XmlValueRef[] FACETS_UNION = FACETS_NONE;
    final static boolean[] FIXED_FACETS_UNION = FIXED_FACETS_NONE;
    final static XmlValueRef[] FACETS_LIST = FACETS_WS_COLLAPSE;
    final static boolean[] FIXED_FACETS_LIST = FIXED_FACETS_WS;

    private static final ThreadLocal<StscStack> tl_stscStack = new ThreadLocal<>();

    private final static String PROJECT_URL_PREFIX = "project://local";


    private String _givenStsName;
    private Collection<XmlError> _errorListener;
    private SchemaTypeSystemImpl _target;
    private BindingConfig _config;
    private Map<QName, QName> _compatMap;
    private boolean _doingDownloads;
    private byte[] _digest = null;
    private boolean _noDigest = false;

    // EXPERIMENTAL: recovery from compilation errors and partial type systems
    private boolean _allowPartial = false;
    private int _recoveredErrors = 0;

    private SchemaTypeLoader _importingLoader;

    private final Map<String, SchemaContainer> _containers = new LinkedHashMap<>();
    private SchemaDependencies _dependencies;

    private final Map<SchemaTypeImpl, SchemaTypeImpl> _redefinedGlobalTypes = new LinkedHashMap<>();
    private final Map<SchemaModelGroupImpl, SchemaModelGroupImpl> _redefinedModelGroups = new LinkedHashMap<>();
    private final Map<SchemaAttributeGroupImpl, SchemaAttributeGroupImpl> _redefinedAttributeGroups = new LinkedHashMap<>();

    private final Map<QName, SchemaType> _globalTypes = new LinkedHashMap<>();
    private final Map<QName, SchemaGlobalElement> _globalElements = new LinkedHashMap<>();
    private final Map<QName, SchemaGlobalAttribute> _globalAttributes = new LinkedHashMap<>();
    private final Map<QName, SchemaModelGroup> _modelGroups = new LinkedHashMap<>();
    private final Map<QName, SchemaAttributeGroup> _attributeGroups = new LinkedHashMap<>();
    private final Map<QName, SchemaType> _documentTypes = new LinkedHashMap<>();
    private final Map<QName, SchemaType> _attributeTypes = new LinkedHashMap<>();
    private final Map<String, SchemaType> _typesByClassname = new LinkedHashMap<>();
    private final Map<String, SchemaComponent> _misspelledNames = new HashMap<>();
    private final Set<SchemaComponent> _processingGroups = new LinkedHashSet<>();
    private final Map<QName, SchemaIdentityConstraint> _idConstraints = new LinkedHashMap<>();
    private final Set<String> _namespaces = new HashSet<>();
    private final List<SchemaAnnotation> _annotations = new ArrayList<>();
    private boolean _noUpa;
    private boolean _noPvr;
    private boolean _noAnn;
    private boolean _mdefAll;
    private String _sourceCodeEncoding ;
    private int _maxNumberOfCharsForNumbers = XmlOptions.DEFAULT_MAX_NUMBER_CHARS;
    private final Set<String> _mdefNamespaces = buildDefaultMdefNamespaces();
    private EntityResolver _entityResolver;
    private File _schemasDir;


    private final Map<String, String> _sourceForUri = new HashMap<>();
    private URI _baseURI = URI.create(PROJECT_URL_PREFIX + "/");
    private final SchemaTypeLoader _s4sloader = XmlBeans.typeLoaderForClassLoader(SchemaDocument.class.getClassLoader());


    private static Set<String> buildDefaultMdefNamespaces() {
        // namespaces which are known to appear in WSDLs redundantly
        return new HashSet<>(
            Collections.singletonList("http://www.openuri.org/2002/04/soap/conversation/"));
    }

    /**
     * Only constructed via StscState.start().
     */
    private StscState() {
    }

    /**
     * Initializer for incremental compilation
     */
    public void initFromTypeSystem(SchemaTypeSystemImpl system, Set<String> newNamespaces) {
//         setGivenTypeSystemName(system.getName().substring(14));

        SchemaContainer[] containers = system.containers();
        for (SchemaContainer container : containers) {
            if (!newNamespaces.contains(container.getNamespace())) {
                // Copy data from the given container
                addContainer(container);
            }
        }
    }


    /* CONTAINERS ================================================================*/

    void addNewContainer(String namespace) {
        if (_containers.containsKey(namespace)) {
            return;
        }

        SchemaContainer container = new SchemaContainer(namespace);
        container.setTypeSystem(sts());
        addNamespace(namespace);
        _containers.put(namespace, container);
    }

    private void addContainer(SchemaContainer container) {
        _containers.put(container.getNamespace(), container);

        // container.redefinedModelGroups() / .redefinedAttributeGroups() / .redefinedGlobalTypes() are always empty
        // no need to copy them over to _redefinedModelGroups / _redefinedAttributeGroups / _redefinedGlobalTypes

        container.globalElements().forEach(g -> _globalElements.put(g.getName(), g));
        container.globalAttributes().forEach(g -> _globalAttributes.put(g.getName(), g));
        container.modelGroups().forEach(g -> _modelGroups.put(g.getName(), g));
        container.attributeGroups().forEach(g -> _attributeGroups.put(g.getName(), g));

        container.globalTypes().forEach(mapTypes(_globalTypes, false));
        container.documentTypes().forEach(mapTypes(_documentTypes, true));
        container.attributeTypes().forEach(mapTypes(_attributeTypes, true));

        container.identityConstraints().forEach(g -> _idConstraints.put(g.getName(), g));

        _annotations.addAll(container.annotations());
        _namespaces.add(container.getNamespace());
        container.unsetImmutable();
    }

    private Consumer<SchemaType> mapTypes(Map<QName, SchemaType> map, boolean useProperties) {
        return (t) -> {
            QName name = useProperties ? t.getProperties()[0].getName() : t.getName();
            map.put(name, t);
            if (t.getFullJavaName() != null) {
                addClassname(t.getFullJavaName(), t);
            }
        };
    }

    SchemaContainer getContainer(String namespace) {
        return _containers.get(namespace);
    }

    Map<String, SchemaContainer> getContainerMap() {
        return Collections.unmodifiableMap(_containers);
    }

    /* DEPENDENCIES ================================================================*/

    void registerDependency(String sourceNs, String targetNs) {
        _dependencies.registerDependency(sourceNs, targetNs);
    }

    void registerContribution(String ns, String fileUrl) {
        _dependencies.registerContribution(ns, fileUrl);
    }

    SchemaDependencies getDependencies() {
        return _dependencies;
    }

    void setDependencies(SchemaDependencies deps) {
        _dependencies = deps;
    }

    boolean isFileProcessed(String url) {
        return _dependencies.isFileRepresented(url);
    }


    /**
     * Initializer for schematypepath
     */
    public void setImportingTypeLoader(SchemaTypeLoader loader) {
        _importingLoader = loader;
    }

    /**
     * Initializer for error handling.
     */
    public void setErrorListener(Collection<XmlError> errorListener) {
        _errorListener = errorListener;
    }

    /**
     * Passes an error on to the current error listener.
     * KHK: remove this
     */
    public void error(String message, int code, XmlObject loc) {
        addError(_errorListener, message, code, loc);
    }

    /**
     * Passes an error on to the current error listener.
     */
    public void error(String code, Object[] args, XmlObject loc) {
        addError(_errorListener, code, args, loc);
    }

    /**
     * Passes a recovered error on to the current error listener.
     */
    public void recover(String code, Object[] args, XmlObject loc) {
        addError(_errorListener, code, args, loc);
        _recoveredErrors++;
    }

    /**
     * Passes an error on to the current error listener.
     */
    public void warning(String message, int code, XmlObject loc) {
        addWarning(_errorListener, message, code, loc);
    }

    /**
     * Passes an error on to the current error listener.
     */
    public void warning(String code, Object[] args, XmlObject loc) {
        // it's OK for XMLSchema.xsd itself to have reserved type names
        if (XmlErrorCodes.RESERVED_TYPE_NAME.equals(code) &&
            loc.documentProperties().getSourceName() != null &&
            loc.documentProperties().getSourceName().indexOf("XMLSchema.xsd") > 0) {
            return;
        }

        addWarning(_errorListener, code, args, loc);
    }

    /**
     * Passes a warning on to the current error listener.
     */
    public void info(String message) {
        addInfo(_errorListener, message);
    }

    /**
     * Passes a warning on to the current error listener.
     */
    public void info(String code, Object[] args) {
        addInfo(_errorListener, code, args);
    }

    // KHK: remove this
    public static void addError(Collection<XmlError> errorListener, String message, int code, XmlObject location) {
        XmlError err =
            XmlError.forObject(
                message,
                XmlError.SEVERITY_ERROR,
                location);
        errorListener.add(err);
    }

    public static void addError(Collection<XmlError> errorListener, String code, Object[] args, XmlObject location) {
        XmlError err =
            XmlError.forObject(
                code,
                args,
                XmlError.SEVERITY_ERROR,
                location);
        errorListener.add(err);
    }

    public static void addError(Collection<XmlError> errorListener, String code, Object[] args, File location) {
        XmlError err =
            XmlError.forLocation(
                code,
                args,
                XmlError.SEVERITY_ERROR,
                location.toURI().toString(), 0, 0, 0);
        errorListener.add(err);
    }

    public static void addError(Collection<XmlError> errorListener, String code, Object[] args, URL location) {
        XmlError err =
            XmlError.forLocation(
                code,
                args,
                XmlError.SEVERITY_ERROR,
                location.toString(), 0, 0, 0);
        errorListener.add(err);
    }

    // KHK: remove this
    public static void addWarning(Collection<XmlError> errorListener, String message, int code, XmlObject location) {
        XmlError err =
            XmlError.forObject(
                message,
                XmlError.SEVERITY_WARNING,
                location);
        errorListener.add(err);
    }

    public static void addWarning(Collection<XmlError> errorListener, String code, Object[] args, XmlObject location) {
        XmlError err =
            XmlError.forObject(
                code,
                args,
                XmlError.SEVERITY_WARNING,
                location);
        errorListener.add(err);
    }

    public static void addInfo(Collection<XmlError> errorListener, String message) {
        XmlError err = XmlError.forMessage(message, XmlError.SEVERITY_INFO);
        errorListener.add(err);
    }

    public static void addInfo(Collection<XmlError> errorListener, String code, Object[] args) {
        XmlError err = XmlError.forMessage(code, args, XmlError.SEVERITY_INFO);
        errorListener.add(err);
    }

    public void setGivenTypeSystemName(String name) {
        _givenStsName = name;
    }

    /**
     * Initializer for references to the SchemaTypeLoader
     */
    public void setTargetSchemaTypeSystem(SchemaTypeSystemImpl target) {
        _target = target;
    }

    /**
     * Accumulates a schema digest...
     */
    public void addSchemaDigest(byte[] digest) {
        if (_noDigest) {
            return;
        }

        if (digest == null) {
            _noDigest = true;
            _digest = null;
            return;
        }

        if (_digest == null) {
            _digest = new byte[128 / 8]; // 128 bits.
        }
        int len = _digest.length;
        if (digest.length < len) {
            len = digest.length;
        }
        for (int i = 0; i < len; i++) {
            _digest[i] ^= digest[i];
        }
    }

    /**
     * The SchemaTypeSystem which we're building types on behalf of.
     */
    public SchemaTypeSystemImpl sts() {
        if (_target != null) {
            return _target;
        }

        String name = _givenStsName;
        if (name == null && _digest != null) {
            name = "s" + new String(HexBin.encode(_digest), StandardCharsets.ISO_8859_1);
        }

        _target = new SchemaTypeSystemImpl(name);
        return _target;
    }

    /**
     * True if the given URI is a local file
     */
    public boolean shouldDownloadURI(String uriString) {
        if (_doingDownloads) {
            return true;
        }

        if (uriString == null) {
            return false;
        }

        try {
            URI uri = new URI(uriString);
            if (equalsIgnoreCase(uri.getScheme(), "jar") ||
                    equalsIgnoreCase(uri.getScheme(), "zip")) {
                // It may be local or not, depending on the embedded URI
                String s = uri.getSchemeSpecificPart();
                int i = s.lastIndexOf('!');
                return shouldDownloadURI(i > 0 ? s.substring(0, i) : s);
            }
            return equalsIgnoreCase(uri.getScheme(), "file") && isLocalFileAuthority(uri);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * True if a file: URI has no remote authority. A file: URI with a non-local
     * host (e.g. a Windows UNC path file://server/share/schema.xsd) would otherwise
     * be fetched even when network downloads are disabled, letting an untrusted
     * schema's import/include reach an attacker-controlled host over SMB.
     */
    private static boolean isLocalFileAuthority(URI uri) {
        String authority = uri.getAuthority();
        return authority == null || authority.isEmpty() ||
               authority.equalsIgnoreCase("localhost");
    }

    /**
     * Initializer for compatMap.
     */
    public void setOptions(XmlOptions options) {
        if (options == null) {
            return; // defaults are all false.
        }

        _allowPartial = options.isCompilePartialTypesystem();

        _compatMap = options.getCompileSubstituteNames();
        _noUpa = options.isCompileNoUpaRule() ||
                 !"true".equals(SystemProperties.getProperty("xmlbean.uniqueparticleattribution", "true"));
        _noPvr = options.isCompileNoPvrRule() ||
                 !"true".equals(SystemProperties.getProperty("xmlbean.particlerestriction", "true"));
        _noAnn = options.isCompileNoAnnotations() ||
                 !"true".equals(SystemProperties.getProperty("xmlbean.schemaannotations", "true"));
        _doingDownloads = options.isCompileDownloadUrls() ||
                          "true".equals(SystemProperties.getProperty("xmlbean.downloadurls", "false"));
        _maxNumberOfCharsForNumbers = options.getMaxNumberOfCharsForNumbers();
        _sourceCodeEncoding = options.getCharacterEncoding();
        if (_sourceCodeEncoding == null || _sourceCodeEncoding.isEmpty()) {
            _sourceCodeEncoding = SystemProperties.getProperty("xmlbean.sourcecodeencoding");
        }
        _entityResolver = options.getEntityResolver();

        if (_entityResolver == null) {
            _entityResolver = ResolverUtil.getGlobalEntityResolver();
        }

        if (_entityResolver != null) {
            _doingDownloads = true;
        }

        Set<String> mdef = options.getCompileMdefNamespaces();
        if (mdef != null) {
            _mdefNamespaces.addAll(mdef);

            String local = "##local";
            String any = "##any";

            if (_mdefNamespaces.contains(local)) {
                _mdefNamespaces.remove(local);
                _mdefNamespaces.add("");
            }
            if (_mdefNamespaces.contains(any)) {
                _mdefNamespaces.remove(any);
                _mdefAll = true;
            }
        }
    }

    /**
     * May return null if there is no custom entity resolver.
     */
    public EntityResolver getEntityResolver() {
        return _entityResolver;
    }

    /**
     * True if no unique particle attribution option is set
     */
    public boolean noUpa() {
        return _noUpa;
    }

    /**
     * True if no particle valid (restriction) option is set
     */
    public boolean noPvr() {
        return _noPvr;
    }

    /**
     * True if annotations should be skipped
     */
    public boolean noAnn() {
        return _noAnn;
    }

    /**
     * True if a partial SchemaTypeSystem should be produced
     */
    // EXPERIMENTAL
    public boolean allowPartial() {
        return _allowPartial;
    }

    /**
     * An optional encoding to use when compiling generated java source file (can be <code>null</code>)
     */
    // EXPERIMENTAL
    public String sourceCodeEncoding() {
        return _sourceCodeEncoding ;
    }

    /**
     * The maximum number of characters a number in the schema being compiled may have,
     * as configured by XmlOptions.setMaxNumberOfCharsForNumbers.
     */
    public int maxNumberOfCharsForNumbers() {
        return _maxNumberOfCharsForNumbers;
    }

    /**
     * Get count of recovered errors. Not for public.
     */
    // EXPERIMENTAL
    public int getRecovered() {
        return _recoveredErrors;
    }

    /**
     * Intercepts XML names and translates them
     * through the compat map, if any.
     * <p>
     * Also looks for a default namespace for global definitions.
     */
    private QName compatName(QName name, String chameleonNamespace) {
        // first check for a chameleonNamespace namespace
        if (name.getNamespaceURI().isEmpty() && chameleonNamespace != null && chameleonNamespace.length() > 0) {
            name = new QName(chameleonNamespace, name.getLocalPart());
        }

        if (_compatMap == null) {
            return name;
        }

        QName subst = _compatMap.get(name);
        if (subst == null) {
            return name;
        }
        return subst;
    }

    /**
     * Initializer for the schema config object.
     */
    public void setBindingConfig(BindingConfig config)
        throws IllegalArgumentException {
        _config = config;
    }

    public BindingConfig getBindingConfig()
        throws IllegalArgumentException {
        return _config;
    }

    /**
     * Looks up package override for a namespace URI
     */
    public String getPackageOverride(String namespace) {
        if (_config == null) {
            return null;
        }
        return _config.lookupPackageForNamespace(namespace);
    }

    /**
     * Looks up package override for a namespace URI
     */
    public String getJavaPrefix(String namespace) {
        if (_config == null) {
            return null;
        }
        return _config.lookupPrefixForNamespace(namespace);
    }

    /**
     * Looks up package override for a namespace URI
     */
    public String getJavaSuffix(String namespace) {
        if (_config == null) {
            return null;
        }
        return _config.lookupSuffixForNamespace(namespace);
    }

    /**
     * Looks up configured java name for the given qname.
     */
    public String getJavaname(QName qname, int kind) {
        if (_config == null) {
            return null;
        }
        return _config.lookupJavanameForQName(qname, kind);
    }

    /* SPELLINGS ======================================================*/

    private static String crunchName(QName name) {
        // lowercase, and drop namespace.
        return name.getLocalPart().toLowerCase(Locale.ROOT);
    }

    void addSpelling(QName name, SchemaComponent comp) {
        _misspelledNames.put(crunchName(name), comp);
    }

    SchemaComponent findSpelling(QName name) {
        return _misspelledNames.get(crunchName(name));
    }

    /* NAMESPACES ======================================================*/

    void addNamespace(String targetNamespace) {
        _namespaces.add(targetNamespace);
    }

    String[] getNamespaces() {
        return _namespaces.toArray(new String[0]);
    }

    boolean linkerDefinesNamespace(String namespace) {
        return _importingLoader.isNamespaceDefined(namespace);
    }

    /* TYPES ==========================================================*/

    SchemaTypeImpl findGlobalType(QName name, String chameleonNamespace, String sourceNamespace) {
        name = compatName(name, chameleonNamespace);
        SchemaTypeImpl result = (SchemaTypeImpl) _globalTypes.get(name);
        boolean foundOnLoader = false;
        if (result == null) {
            result = (SchemaTypeImpl) _importingLoader.findType(name);
            foundOnLoader = result != null;
        }
        if (!foundOnLoader && sourceNamespace != null) {
            registerDependency(sourceNamespace, name.getNamespaceURI());
        }
        return result;
    }

    SchemaTypeImpl findRedefinedGlobalType(QName name, String chameleonNamespace, SchemaTypeImpl redefinedBy) {
        QName redefinedName = redefinedBy.getName();
        name = compatName(name, chameleonNamespace);
        if (name.equals(redefinedName)) {
            return _redefinedGlobalTypes.get(redefinedBy);
            // BUGBUG: should also link against _importingLoader.findRedefinedType
        }
        SchemaTypeImpl result = (SchemaTypeImpl) _globalTypes.get(name);
        if (result == null) {
            result = (SchemaTypeImpl) _importingLoader.findType(name);
        }
        // no dependency is needed here, necause it's intra-namespace
        return result;
    }

    void addGlobalType(SchemaTypeImpl type, SchemaTypeImpl redefined) {
        if (type != null) {
            QName name = type.getName();
            SchemaContainer container = getContainer(name.getNamespaceURI());
            assert container != null && container == type.getContainer();

            if (redefined != null) {
                if (_redefinedGlobalTypes.containsKey(redefined)) {
                    if (!ignoreMdef(name)) {
                        if (_mdefAll) {
                            warning(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"global type", QNameHelper.pretty(name), _redefinedGlobalTypes.get(redefined).getSourceName()},
                                type.getParseObject());
                        } else {
                            error(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"global type", QNameHelper.pretty(name), _redefinedGlobalTypes.get(redefined).getSourceName()},
                                type.getParseObject());
                        }
                    }
                } else {
                    _redefinedGlobalTypes.put(redefined, type);
                    container.addRedefinedType(type.getRef());
                }
            } else {
                if (_globalTypes.containsKey(name)) {
                    if (!ignoreMdef(name)) {
                        if (_mdefAll) {
                            warning(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"global type", QNameHelper.pretty(name), _globalTypes.get(name).getSourceName()},
                                type.getParseObject());
                        } else {
                            error(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"global type", QNameHelper.pretty(name), _globalTypes.get(name).getSourceName()},
                                type.getParseObject());
                        }
                    }
                } else {
                    _globalTypes.put(name, type);
                    container.addGlobalType(type.getRef());
                    addSpelling(name, type);
                }
            }
        }
    }

    private boolean ignoreMdef(QName name) {
        return _mdefNamespaces.contains(name.getNamespaceURI());
    }

    SchemaType[] globalTypes() {
        return _globalTypes.values().toArray(new SchemaType[0]);
    }

    SchemaType[] redefinedGlobalTypes() {
        return _redefinedGlobalTypes.values().toArray(new SchemaType[0]);
    }

    /* DOCUMENT TYPES =================================================*/

    SchemaTypeImpl findDocumentType(QName name, String chameleonNamespace, String sourceNamespace) {
        name = compatName(name, chameleonNamespace);
        SchemaTypeImpl result = (SchemaTypeImpl) _documentTypes.get(name);
        boolean foundOnLoader = false;
        if (result == null) {
            result = (SchemaTypeImpl) _importingLoader.findDocumentType(name);
            foundOnLoader = result != null;
        }
        if (!foundOnLoader && sourceNamespace != null) {
            registerDependency(sourceNamespace, name.getNamespaceURI());
        }
        return result;
    }

    void addDocumentType(SchemaTypeImpl type, QName name) {
        if (_documentTypes.containsKey(name)) {
            if (!ignoreMdef(name)) {
                if (_mdefAll) {
                    warning(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                        new Object[]{"global element", QNameHelper.pretty(name), _documentTypes.get(name).getSourceName()},
                        type.getParseObject());
                } else {
                    error(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                        new Object[]{"global element", QNameHelper.pretty(name), _documentTypes.get(name).getSourceName()},
                        type.getParseObject());
                }
            }
        } else {
            _documentTypes.put(name, type);
            SchemaContainer container = getContainer(name.getNamespaceURI());
            assert container != null && container == type.getContainer();
            container.addDocumentType(type.getRef());
        }
    }

    SchemaType[] documentTypes() {
        return _documentTypes.values().toArray(new SchemaType[0]);
    }

    /* ATTRIBUTE TYPES =================================================*/

    SchemaTypeImpl findAttributeType(QName name, String chameleonNamespace, String sourceNamespace) {
        name = compatName(name, chameleonNamespace);
        SchemaTypeImpl result = (SchemaTypeImpl) _attributeTypes.get(name);
        boolean foundOnLoader = false;
        if (result == null) {
            result = (SchemaTypeImpl) _importingLoader.findAttributeType(name);
            foundOnLoader = result != null;
        }
        if (!foundOnLoader && sourceNamespace != null) {
            registerDependency(sourceNamespace, name.getNamespaceURI());
        }
        return result;
    }

    void addAttributeType(SchemaTypeImpl type, QName name) {
        if (_attributeTypes.containsKey(name)) {
            if (!ignoreMdef(name)) {
                if (_mdefAll) {
                    warning(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                        new Object[]{"global attribute", QNameHelper.pretty(name), _attributeTypes.get(name).getSourceName()},
                        type.getParseObject());
                } else {
                    error(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                        new Object[]{"global attribute", QNameHelper.pretty(name), _attributeTypes.get(name).getSourceName()},
                        type.getParseObject());
                }
            }
        } else {
            _attributeTypes.put(name, type);
            SchemaContainer container = getContainer(name.getNamespaceURI());
            assert container != null && container == type.getContainer();
            container.addAttributeType(type.getRef());
        }
    }

    SchemaType[] attributeTypes() {
        return _attributeTypes.values().toArray(new SchemaType[0]);
    }

    /* ATTRIBUTES =====================================================*/

    SchemaGlobalAttributeImpl findGlobalAttribute(QName name, String chameleonNamespace, String sourceNamespace) {
        name = compatName(name, chameleonNamespace);
        SchemaGlobalAttributeImpl result = (SchemaGlobalAttributeImpl) _globalAttributes.get(name);
        boolean foundOnLoader = false;
        if (result == null) {
            result = (SchemaGlobalAttributeImpl) _importingLoader.findAttribute(name);
            foundOnLoader = result != null;
        }
        if (!foundOnLoader && sourceNamespace != null) {
            registerDependency(sourceNamespace, name.getNamespaceURI());
        }
        return result;
    }

    void addGlobalAttribute(SchemaGlobalAttributeImpl attribute) {
        if (attribute != null) {
            QName name = attribute.getName();
            _globalAttributes.put(name, attribute);
            addSpelling(name, attribute);
            SchemaContainer container = getContainer(name.getNamespaceURI());
            assert container != null && container == attribute.getContainer();
            container.addGlobalAttribute(attribute.getRef());
        }
    }

    SchemaGlobalAttribute[] globalAttributes() {
        return _globalAttributes.values().toArray(new SchemaGlobalAttribute[0]);
    }

    /* ELEMENTS =======================================================*/

    SchemaGlobalElementImpl findGlobalElement(QName name, String chameleonNamespace, String sourceNamespace) {
        name = compatName(name, chameleonNamespace);
        SchemaGlobalElementImpl result = (SchemaGlobalElementImpl) _globalElements.get(name);
        boolean foundOnLoader = false;
        if (result == null) {
            result = (SchemaGlobalElementImpl) _importingLoader.findElement(name);
            foundOnLoader = result != null;
        }
        if (!foundOnLoader && sourceNamespace != null) {
            registerDependency(sourceNamespace, name.getNamespaceURI());
        }
        return result;
    }

    void addGlobalElement(SchemaGlobalElementImpl element) {
        if (element != null) {
            QName name = element.getName();
            _globalElements.put(name, element);
            SchemaContainer container = getContainer(name.getNamespaceURI());
            assert container != null && container == element.getContainer();
            container.addGlobalElement(element.getRef());
            addSpelling(name, element);
        }
    }

    SchemaGlobalElement[] globalElements() {
        return _globalElements.values().toArray(new SchemaGlobalElement[0]);
    }

    /* ATTRIBUTE GROUPS ===============================================*/

    SchemaAttributeGroupImpl findAttributeGroup(QName name, String chameleonNamespace, String sourceNamespace) {
        name = compatName(name, chameleonNamespace);
        SchemaAttributeGroupImpl result = (SchemaAttributeGroupImpl) _attributeGroups.get(name);
        boolean foundOnLoader = false;
        if (result == null) {
            result = (SchemaAttributeGroupImpl) _importingLoader.findAttributeGroup(name);
            foundOnLoader = result != null;
        }
        if (!foundOnLoader && sourceNamespace != null) {
            registerDependency(sourceNamespace, name.getNamespaceURI());
        }
        return result;
    }

    SchemaAttributeGroupImpl findRedefinedAttributeGroup(QName name, String chameleonNamespace, SchemaAttributeGroupImpl redefinedBy) {
        QName redefinitionFor = redefinedBy.getName();
        name = compatName(name, chameleonNamespace);
        if (name.equals(redefinitionFor)) {
            return _redefinedAttributeGroups.get(redefinedBy);
            // BUGBUG: should also link against _importingLoader.findRedefinedAttributeGroup
        }
        SchemaAttributeGroupImpl result = (SchemaAttributeGroupImpl) _attributeGroups.get(name);
        if (result == null) {
            result = (SchemaAttributeGroupImpl) _importingLoader.findAttributeGroup(name);
        }
        return result;
    }

    void addAttributeGroup(SchemaAttributeGroupImpl attributeGroup, SchemaAttributeGroupImpl redefined) {
        if (attributeGroup != null) {
            QName name = attributeGroup.getName();
            SchemaContainer container = getContainer(name.getNamespaceURI());
            assert container != null && container == attributeGroup.getContainer();
            if (redefined != null) {
                if (_redefinedAttributeGroups.containsKey(redefined)) {
                    if (!ignoreMdef(name)) {
                        if (_mdefAll) {
                            warning(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"attribute group", QNameHelper.pretty(name), _redefinedAttributeGroups.get(redefined).getSourceName()},
                                attributeGroup.getParseObject());
                        } else {
                            error(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"attribute group", QNameHelper.pretty(name), _redefinedAttributeGroups.get(redefined).getSourceName()},
                                attributeGroup.getParseObject());
                        }
                    }
                } else {
                    _redefinedAttributeGroups.put(redefined, attributeGroup);
                    container.addRedefinedAttributeGroup(attributeGroup.getRef());
                }
            } else {
                if (_attributeGroups.containsKey(name)) {
                    if (!ignoreMdef(name)) {
                        if (_mdefAll) {
                            warning(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"attribute group", QNameHelper.pretty(name), _attributeGroups.get(name).getSourceName()},
                                attributeGroup.getParseObject());
                        } else {
                            error(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"attribute group", QNameHelper.pretty(name), _attributeGroups.get(name).getSourceName()},
                                attributeGroup.getParseObject());
                        }
                    }
                } else {
                    _attributeGroups.put(attributeGroup.getName(), attributeGroup);
                    addSpelling(attributeGroup.getName(), attributeGroup);
                    container.addAttributeGroup(attributeGroup.getRef());
                }
            }
        }
    }

    SchemaAttributeGroup[] attributeGroups() {
        return _attributeGroups.values().toArray(new SchemaAttributeGroup[0]);
    }

    SchemaAttributeGroup[] redefinedAttributeGroups() {
        return _redefinedAttributeGroups.values().toArray(new SchemaAttributeGroup[0]);
    }

    /* MODEL GROUPS ===================================================*/

    SchemaModelGroupImpl findModelGroup(QName name, String chameleonNamespace, String sourceNamespace) {
        name = compatName(name, chameleonNamespace);
        SchemaModelGroupImpl result = (SchemaModelGroupImpl) _modelGroups.get(name);
        boolean foundOnLoader = false;
        if (result == null) {
            result = (SchemaModelGroupImpl) _importingLoader.findModelGroup(name);
            foundOnLoader = result != null;
        }
        if (!foundOnLoader && sourceNamespace != null) {
            registerDependency(sourceNamespace, name.getNamespaceURI());
        }
        return result;
    }

    SchemaModelGroupImpl findRedefinedModelGroup(QName name, String chameleonNamespace, SchemaModelGroupImpl redefinedBy) {
        QName redefinitionFor = redefinedBy.getName();
        name = compatName(name, chameleonNamespace);
        if (name.equals(redefinitionFor)) {
            return _redefinedModelGroups.get(redefinedBy);
            // BUGBUG: should also link against _importingLoader.findRedefinedModelGroup
        }
        SchemaModelGroupImpl result = (SchemaModelGroupImpl) _modelGroups.get(name);
        if (result == null) {
            result = (SchemaModelGroupImpl) _importingLoader.findModelGroup(name);
        }
        return result;
    }

    void addModelGroup(SchemaModelGroupImpl modelGroup, SchemaModelGroupImpl redefined) {
        if (modelGroup != null) {
            QName name = modelGroup.getName();
            SchemaContainer container = getContainer(name.getNamespaceURI());
            assert container != null && container == modelGroup.getContainer();
            if (redefined != null) {
                if (_redefinedModelGroups.containsKey(redefined)) {
                    if (!ignoreMdef(name)) {
                        if (_mdefAll) {
                            warning(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"model group", QNameHelper.pretty(name), ((SchemaComponent) _redefinedModelGroups.get(redefined)).getSourceName()},
                                modelGroup.getParseObject());
                        } else {
                            error(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"model group", QNameHelper.pretty(name), ((SchemaComponent) _redefinedModelGroups.get(redefined)).getSourceName()},
                                modelGroup.getParseObject());
                        }
                    }
                } else {
                    _redefinedModelGroups.put(redefined, modelGroup);
                    container.addRedefinedModelGroup(modelGroup.getRef());
                }
            } else {
                if (_modelGroups.containsKey(name)) {
                    if (!ignoreMdef(name)) {
                        if (_mdefAll) {
                            warning(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"model group", QNameHelper.pretty(name), _modelGroups.get(name).getSourceName()},
                                modelGroup.getParseObject());
                        } else {
                            error(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                                new Object[]{"model group", QNameHelper.pretty(name), _modelGroups.get(name).getSourceName()},
                                modelGroup.getParseObject());
                        }
                    }
                } else {
                    _modelGroups.put(modelGroup.getName(), modelGroup);
                    addSpelling(modelGroup.getName(), modelGroup);
                    container.addModelGroup(modelGroup.getRef());
                }
            }
        }
    }

    SchemaModelGroup[] modelGroups() {
        return _modelGroups.values().toArray(new SchemaModelGroup[0]);
    }

    SchemaModelGroup[] redefinedModelGroups() {
        return _redefinedModelGroups.values().toArray(new SchemaModelGroup[0]);
    }

    /* IDENTITY CONSTRAINTS ===========================================*/

    SchemaIdentityConstraintImpl findIdConstraint(QName name, String chameleonNamespace, String sourceNamespace) {
        name = compatName(name, chameleonNamespace);
        if (sourceNamespace != null) {
            registerDependency(sourceNamespace, name.getNamespaceURI());
        }
        return (SchemaIdentityConstraintImpl) _idConstraints.get(name);
    }

    void addIdConstraint(SchemaIdentityConstraintImpl idc) {
        if (idc != null) {
            QName name = idc.getName();
            SchemaContainer container = getContainer(name.getNamespaceURI());
            assert container != null && container == idc.getContainer();
            if (_idConstraints.containsKey(name)) {
                if (!ignoreMdef(name)) {
                    warning(XmlErrorCodes.SCHEMA_PROPERTIES$DUPLICATE,
                        new Object[]{"identity constraint", QNameHelper.pretty(name), _idConstraints.get(name).getSourceName()},
                        idc.getParseObject());
                }
            } else {
                _idConstraints.put(name, idc);
                addSpelling(idc.getName(), idc);
                container.addIdentityConstraint(idc.getRef());
            }
        }
    }

    SchemaIdentityConstraintImpl[] idConstraints() {
        return _idConstraints.values().toArray(new SchemaIdentityConstraintImpl[0]);
    }

    /* ANNOTATIONS ===========================================*/

    void addAnnotation(SchemaAnnotationImpl ann, String targetNamespace) {
        if (ann != null) {
            SchemaContainer container = getContainer(targetNamespace);
            assert container != null && container == ann.getContainer();
            _annotations.add(ann);
            container.addAnnotation(ann);
        }
    }

    List<SchemaAnnotation> annotations() {
        return _annotations;
    }

    /* RECURSION AVOIDANCE ============================================*/
    boolean isProcessing(SchemaComponent obj) {
        return _processingGroups.contains(obj);
    }

    void startProcessing(SchemaComponent obj) {
        assert (!_processingGroups.contains(obj));
        _processingGroups.add(obj);
    }

    void finishProcessing(SchemaComponent obj) {
        assert (_processingGroups.contains(obj));
        _processingGroups.remove(obj);
    }

    SchemaComponent[] getCurrentProcessing() {
        return _processingGroups.toArray(new SchemaComponent[0]);
    }

    /* JAVAIZATION ====================================================*/

    Map<String, SchemaType> typesByClassname() {
        return Collections.unmodifiableMap(_typesByClassname);
    }

    void addClassname(String classname, SchemaType type) {
        _typesByClassname.put(classname, type);
    }


    /**
     * Stack management if (heaven help us) we ever need to do
     * nested compilation of schema type system.
     */
    private static final class StscStack {
        StscState current;
        List<StscState> stack = new ArrayList<>();

        final StscState push() {
            stack.add(current);
            current = new StscState();
            return current;
        }

        final void pop() {
            current = stack.get(stack.size() - 1);
            stack.remove(stack.size() - 1);
        }
    }

    public static void clearThreadLocals() {
        tl_stscStack.remove();
    }

    public static StscState start() {
        StscStack stscStack = tl_stscStack.get();

        if (stscStack == null) {
            stscStack = new StscStack();
            tl_stscStack.set(stscStack);
        }
        return stscStack.push();
    }

    public static StscState get() {
        return tl_stscStack.get().current;
    }

    public static void end() {
        StscStack stscStack = tl_stscStack.get();
        stscStack.pop();
        if (stscStack.stack.isEmpty()) {
            // this is required to release all the references in this classloader
            tl_stscStack.remove();
        }
        // which will enable class unloading and avoid OOM in PermGen
    }


    static XmlValueRef build_wsstring(int wsr) {
        switch (wsr) {
            case SchemaType.WS_PRESERVE:
                return XMLSTR_PRESERVE;
            case SchemaType.WS_REPLACE:
                return XMLSTR_REPLACE;
            case SchemaType.WS_COLLAPSE:
                return XMLSTR_COLLAPSE;
        }
        return null;
    }

    static XmlValueRef buildString(String str) {
        if (str == null) {
            return null;
        }

        try {
            XmlStringImpl i = new XmlStringImpl();
            i.setStringValue(str);
            i.setImmutable();
            return new XmlValueRef(i);
        } catch (XmlValueOutOfRangeException e) {
            return null;
        }
    }

    public void notFoundError(QName itemName, int code, XmlObject loc, boolean recovered) {
        String expected;
        String expectedName = QNameHelper.pretty(itemName);
        String found = null;
        String foundName = null;
        String sourceName = null;

        if (recovered) {
            _recoveredErrors++;
        }

        switch (code) {
            case SchemaType.TYPE:
                expected = "type";
                break;
            case SchemaType.ELEMENT:
                expected = "element";
                break;
            case SchemaType.ATTRIBUTE:
                expected = "attribute";
                break;
            case SchemaType.MODEL_GROUP:
                expected = "model group";
                break;
            case SchemaType.ATTRIBUTE_GROUP:
                expected = "attribute group";
                break;
            case SchemaType.IDENTITY_CONSTRAINT:
                expected = "identity constraint";
                break;
            default:
                assert (false);
                expected = "definition";
                break;
        }

        SchemaComponent foundComponent = findSpelling(itemName);
        QName name;
        if (foundComponent != null) {
            name = foundComponent.getName();
            if (name != null) {
                switch (foundComponent.getComponentType()) {
                    case SchemaComponent.TYPE:
                        found = "type";
                        sourceName = foundComponent.getSourceName();
                        break;
                    case SchemaComponent.ELEMENT:
                        found = "element";
                        sourceName = foundComponent.getSourceName();
                        break;
                    case SchemaComponent.ATTRIBUTE:
                        found = "attribute";
                        sourceName = foundComponent.getSourceName();
                        break;
                    case SchemaComponent.ATTRIBUTE_GROUP:
                        found = "attribute group";
                        break;
                    case SchemaComponent.MODEL_GROUP:
                        found = "model group";
                        break;
                }

                if (sourceName != null) {
                    sourceName = sourceName.substring(sourceName.lastIndexOf('/') + 1);
                }

                if (!name.equals(itemName)) {
                    foundName = QNameHelper.pretty(name);
                }
            }
        }

        if (found == null) {
            // error with no help
            error(XmlErrorCodes.SCHEMA_QNAME_RESOLVE,
                new Object[]{expected, expectedName}, loc);
        } else {
            // error with help
            error(XmlErrorCodes.SCHEMA_QNAME_RESOLVE$HELP,
                new Object[]{
                    expected,
                    expectedName,
                    found,
                    (foundName == null ? 0 : 1),
                    foundName,
                    (sourceName == null ? 0 : 1),
                    sourceName
                },
                loc);
        }
    }


    /**
     * Produces the "sourceName" (to be used within the schema project
     * source file copies) from the URI of the original source.
     * <p>
     * Returns null if none.
     */
    public String sourceNameForUri(String uri) {
        return _sourceForUri.get(uri);
    }

    /**
     * Returns the whole sourceCopyMap, mapping URI's that have
     * been read to "sourceName" local names that have been used
     * to tag the types.
     */
    public Map<String, String> sourceCopyMap() {
        return Collections.unmodifiableMap(_sourceForUri);
    }

    /**
     * The base URI to use for nice filenames when saving sources.
     */
    public void setBaseUri(URI uri) {
        _baseURI = uri;
    }

    public String relativize(String uri) {
        return relativize(uri, false);
    }

    public String computeSavedFilename(String uri) {
        return relativize(uri, true);
    }

    /**
     * @return true if the first <var>end</var> chars of <var>s</var> are all
     * regex word chars ({@code [a-zA-Z_0-9]}), i.e. {@code s.substring(0, end).matches("\\w+")}
     */
    static boolean isWordPrefix(String s, int end) {
        if (end <= 0) {
            return false;
        }
        for (int i = 0; i < end; i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private String relativize(String uri, boolean forSavedFilename) {
        if (uri == null) {
            return null;
        }

        // deal with things that do not look like absolute uris
        if (uri.startsWith("/")) {
            uri = PROJECT_URL_PREFIX + uri.replace('\\', '/');
        } else {
            // looks like a URL?
            int colon = uri.indexOf(':');
            if (colon <= 1 || !isWordPrefix(uri, colon)) {
                uri = PROJECT_URL_PREFIX + "/" + uri.replace('\\', '/');
            }
        }

        // now relativize against that...
        if (_baseURI != null) {
            try {
                URI relative = _baseURI.relativize(new URI(uri));
                if (!relative.isAbsolute()) {
                    return relative.toString();
                } else {
                    uri = relative.toString();
                }
            } catch (URISyntaxException ignored) {
            }
        }

        if (!forSavedFilename) {
            return uri;
        }

        int lastslash = uri.lastIndexOf('/');
        String dir = QNameHelper.hexsafe(lastslash == -1 ? "" : uri.substring(0, lastslash));

        int question = uri.indexOf('?', lastslash + 1);
        if (question == -1) {
            return dir + "/" + uri.substring(lastslash + 1);
        }

        String query = QNameHelper.hexsafe(uri.substring(question));

        // if encoded query part is longer than 64 characters, just drop it
        if (query.startsWith(QNameHelper.URI_SHA1_PREFIX)) {
            return dir + "/" + uri.substring(lastslash + 1, question);
        } else {
            return dir + "/" + uri.substring(lastslash + 1, question) + query;
        }
    }

    /**
     * Notes another URI that has been consumed during compilation
     * (this is the URI that is in the document .NAME property)
     */
    public void addSourceUri(String uri, String nameToUse) {
        if (uri == null) {
            return;
        }

        if (nameToUse == null) {
            nameToUse = computeSavedFilename(uri);
        }

        _sourceForUri.put(uri, nameToUse);
    }

    /**
     * Returns the error listener being filled in during this compilation
     */
    public Collection<XmlError> getErrorListener() {
        return _errorListener;
    }

    /**
     * Returns the schema type loader to use for processing s4s
     */
    public SchemaTypeLoader getS4SLoader() {
        return _s4sloader;
    }

    public File getSchemasDir() {
        return _schemasDir;
    }

    public void setSchemasDir(File _schemasDir) {
        this._schemasDir = _schemasDir;
    }
}
