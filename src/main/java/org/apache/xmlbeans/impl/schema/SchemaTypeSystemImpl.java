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
import org.apache.xmlbeans.impl.common.DefaultClassLoaderResourceLoader;
import org.apache.xmlbeans.impl.common.QNameHelper;
import org.apache.xmlbeans.impl.common.XBeanDebug;
import org.apache.xmlbeans.impl.util.ExceptionUtil;
import org.apache.xmlbeans.impl.util.FilerImpl;
import org.apache.xmlbeans.impl.util.HexBin;
import org.apache.xmlbeans.impl.util.LongUTFDataInputStream;
import org.apache.xmlbeans.impl.util.LongUTFDataOutputStream;

import javax.xml.namespace.QName;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SchemaTypeSystemImpl extends SchemaTypeLoaderBase implements SchemaTypeSystem {
    public static final int DATA_BABE = 0xDA7ABABE;
    public static final int MAJOR_VERSION = 2;  // must match == to be compatible
    public static final int MINOR_VERSION = 24; // must be <= to be compatible
    public static final int RELEASE_NUMBER = 0; // should be compatible even if < or >

    public static final int FILETYPE_SCHEMAINDEX = 1;
    public static final int FILETYPE_SCHEMATYPE = 2;
    public static final int FILETYPE_SCHEMAELEMENT = 3;
    public static final int FILETYPE_SCHEMAATTRIBUTE = 4;
    public static final int FILETYPE_SCHEMAPOINTER = 5;
    public static final int FILETYPE_SCHEMAMODELGROUP = 6;
    public static final int FILETYPE_SCHEMAATTRIBUTEGROUP = 7;
    public static final int FILETYPE_SCHEMAIDENTITYCONSTRAINT = 8;

    public static final int FLAG_PART_SKIPPABLE = 1;
    public static final int FLAG_PART_FIXED = 4;
    public static final int FLAG_PART_NILLABLE = 8;
    public static final int FLAG_PART_BLOCKEXT = 16;
    public static final int FLAG_PART_BLOCKREST = 32;
    public static final int FLAG_PART_BLOCKSUBST = 64;
    public static final int FLAG_PART_ABSTRACT = 128;
    public static final int FLAG_PART_FINALEXT = 256;
    public static final int FLAG_PART_FINALREST = 512;

    public static final int FLAG_PROP_ISATTR = 1;
    public static final int FLAG_PROP_JAVASINGLETON = 2;
    public static final int FLAG_PROP_JAVAOPTIONAL = 4;
    public static final int FLAG_PROP_JAVAARRAY = 8;

    public static final int FIELD_NONE = 0;
    public static final int FIELD_GLOBAL = 1;
    public static final int FIELD_LOCALATTR = 2;
    public static final int FIELD_LOCALELT = 3;

    // type flags
    static final int FLAG_SIMPLE_TYPE = 0x1;
    static final int FLAG_DOCUMENT_TYPE = 0x2;
    static final int FLAG_ORDERED = 0x4;
    static final int FLAG_BOUNDED = 0x8;
    static final int FLAG_FINITE = 0x10;
    static final int FLAG_NUMERIC = 0x20;
    static final int FLAG_STRINGENUM = 0x40;
    static final int FLAG_UNION_OF_LISTS = 0x80;
    static final int FLAG_HAS_PATTERN = 0x100;
    static final int FLAG_ORDER_SENSITIVE = 0x200;
    static final int FLAG_TOTAL_ORDER = 0x400;
    static final int FLAG_COMPILED = 0x800;
    static final int FLAG_BLOCK_EXT = 0x1000;
    static final int FLAG_BLOCK_REST = 0x2000;
    static final int FLAG_FINAL_EXT = 0x4000;
    static final int FLAG_FINAL_REST = 0x8000;
    static final int FLAG_FINAL_UNION = 0x10000;
    static final int FLAG_FINAL_LIST = 0x20000;
    static final int FLAG_ABSTRACT = 0x40000;
    static final int FLAG_ATTRIBUTE_TYPE = 0x80000;

    /**
     * regex to identify the type system holder package namespace
     */
    private static final Pattern packPat = Pattern.compile("^(.+)(\\.[^.]+){2}$");

    /**
     * This is to support the feature of a separate/private XMLBeans
     * distribution that will not colide with the public org apache
     * xmlbeans one.
     * METADATA_PACKAGE_GEN will be "" for the original and something like
     * com.mycompany.private.xmlbeans for a private distribution of XMLBeans.
     * <p>
     * There are two properties:
     * METADATA_PACKAGE_GEN - used for generating metadata
     * and METADATA_PACKAGE_LOAD - used for loading the metadata.
     * Most of the time they have the same value, with one exception, during the
     * repackage process scomp needs to load from old package and generate into
     * a new package.
     */
    public static String METADATA_PACKAGE_GEN = "org/apache/xmlbeans/metadata";


    private static final SchemaType[] EMPTY_ST_ARRAY = new SchemaType[0];
    private static final SchemaGlobalElement[] EMPTY_GE_ARRAY = new SchemaGlobalElement[0];
    private static final SchemaGlobalAttribute[] EMPTY_GA_ARRAY = new SchemaGlobalAttribute[0];
    private static final SchemaModelGroup[] EMPTY_MG_ARRAY = new SchemaModelGroup[0];
    private static final SchemaAttributeGroup[] EMPTY_AG_ARRAY = new SchemaAttributeGroup[0];
    private static final SchemaIdentityConstraint[] EMPTY_IC_ARRAY = new SchemaIdentityConstraint[0];
    private static final SchemaAnnotation[] EMPTY_ANN_ARRAY = new SchemaAnnotation[0];

    private final String _name;

    // EXPERIMENTAL: recovery from compilation errors and partial type systems
    private boolean _incomplete = false;

    // classloader is available for sts's that were compiled and loaded, not dynamic ones
    private ClassLoader _classloader;

    // the loader for loading .xsb resources
    private ResourceLoader _resourceLoader;

    // the following is used to link references during load
    SchemaTypeLoader _linker;

    private SchemaTypePool _localHandles;
    private Filer _filer;

    // top-level annotations
    private List<SchemaAnnotation> _annotations;

    // container
    private Map<String, SchemaContainer> _containers = new HashMap<>();
    // dependencies
    private SchemaDependencies _deps;

    private List<SchemaComponent.Ref> _redefinedModelGroups;
    private List<SchemaComponent.Ref> _redefinedAttributeGroups;
    private List<SchemaComponent.Ref> _redefinedGlobalTypes;

    // actual type system data, map QNames -> SchemaComponent.Ref
    private Map<QName, SchemaComponent.Ref> _globalElements;
    private Map<QName, SchemaComponent.Ref> _globalAttributes;
    private Map<QName, SchemaComponent.Ref> _modelGroups;
    private Map<QName, SchemaComponent.Ref> _attributeGroups;
    private Map<QName, SchemaComponent.Ref> _globalTypes;
    private Map<QName, SchemaComponent.Ref> _documentTypes;
    private Map<QName, SchemaComponent.Ref> _attributeTypes;
    private Map<QName, SchemaComponent.Ref> _identityConstraints = Collections.emptyMap();
    private Map<String, SchemaComponent.Ref> _typeRefsByClassname = new HashMap<>();
    private Set<String> _namespaces;

    // the additional config option
    private String _sourceCodeEncoding ;

    static String nameToPathString(String nameForSystem) {
        nameForSystem = nameForSystem.replace('.', '/');

        if (!nameForSystem.endsWith("/") && nameForSystem.length() > 0) {
            nameForSystem = nameForSystem + "/";
        }

        return nameForSystem;
    }

    protected SchemaTypeSystemImpl() {
        String fullname = getClass().getName();
        _name = fullname.substring(0, fullname.lastIndexOf('.'));
        XBeanDebug.LOG.atTrace().log("Loading type system {}", _name);
        _classloader = getClass().getClassLoader();
        _linker = this;
        _resourceLoader = new ClassLoaderResourceLoader(_classloader);
        try {
            initFromHeader();
        } catch (Error | RuntimeException e) {
            XBeanDebug.LOG.atDebug().withThrowable(e).log(e.getMessage());
            throw e;
        }
        XBeanDebug.LOG.atTrace().log("Finished loading type system {}", _name);
    }

    public SchemaTypeSystemImpl(Class<?> indexclass) {
        String fullname = indexclass.getName();
        _name = fullname.substring(0, fullname.lastIndexOf('.'));
        XBeanDebug.LOG.atTrace().log("Loading type system {}", _name);
        _classloader = indexclass.getClassLoader();
        _linker = SchemaTypeLoaderImpl.build(null, new DefaultClassLoaderResourceLoader(), _classloader, getMetadataPath());
        _resourceLoader = new ClassLoaderResourceLoader(_classloader);
        try {
            initFromHeader();
        } catch (RuntimeException | Error e) {
            XBeanDebug.LOG.atDebug().withThrowable(e).log(e.getMessage());
            throw e;
        }
        XBeanDebug.LOG.atTrace().log("Finished loading type system {}", _name);
    }

    public static SchemaTypeSystemImpl forName(String name, ClassLoader loader) {
        try {
            Class<?> c = Class.forName(name + "." + SchemaTypeCodePrinter.INDEX_CLASSNAME, true, loader);
            return (SchemaTypeSystemImpl) c.getField("typeSystem").get(null);
        } catch (Throwable e) {
            if (ExceptionUtil.isFatal(e)) {
                ExceptionUtil.rethrow(e);
            }
            return null;
        }
    }

    public SchemaTypeSystemImpl(ResourceLoader resourceLoader, String name, SchemaTypeLoader linker) {
        this(resourceLoader, null, name, linker);
    }

    /**
     * @since 5.4.0
     */
    public SchemaTypeSystemImpl(ResourceLoader resourceLoader, ClassLoader classLoader,
                                String name, SchemaTypeLoader linker) {
        _name = name;
        _linker = linker;
        _resourceLoader = resourceLoader;
        _classloader = classLoader;
        try {
            initFromHeader();
        } catch (RuntimeException | Error e) {
            XBeanDebug.LOG.atDebug().withThrowable(e).log(e.getMessage());
            throw e;
        }
    }

    private void initFromHeader() {
        XBeanDebug.LOG.atTrace().log("Reading unresolved handles for type system {}", _name);
        XsbReader reader = null;
        try {
            // Read the index file, which starts with a header.
            reader = new XsbReader(getTypeSystem(), "index", FILETYPE_SCHEMAINDEX);

            // has a handle pool (count, handle/type, handle/type...)
            _localHandles = new SchemaTypePool(getTypeSystem());
            _localHandles.readHandlePool(reader);

            // then a qname map of global elements (count, qname/handle, qname/handle...)
            _globalElements = reader.readQNameRefMap();

            // qname map of global attributes
            _globalAttributes = reader.readQNameRefMap();

            // qname map of model groups
            _modelGroups = reader.readQNameRefMap();

            // qname map of attribute groups
            _attributeGroups = reader.readQNameRefMap();

            _identityConstraints = reader.readQNameRefMap();

            // qname map of global types
            _globalTypes = reader.readQNameRefMap();

            // qname map of document types, by the qname of the contained element
            _documentTypes = reader.readQNameRefMap();

            // qname mape of attribute types, by the qname of the contained attribute
            _attributeTypes = reader.readQNameRefMap();

            // string map of all types, by fully qualified classname
            _typeRefsByClassname = reader.readClassnameRefMap();

            _namespaces = reader.readNamespaces();

            // support for redefine, at the end of the file
            List<QName> typeNames = new ArrayList<>();
            List<QName> modelGroupNames = new ArrayList<>();
            List<QName> attributeGroupNames = new ArrayList<>();
            if (reader.atLeast(2, 15, 0)) {
                _redefinedGlobalTypes = reader.readQNameRefMapAsList(typeNames);
                _redefinedModelGroups = reader.readQNameRefMapAsList(modelGroupNames);
                _redefinedAttributeGroups = reader.readQNameRefMapAsList(attributeGroupNames);
            }
            if (reader.atLeast(2, 19, 0)) {
                _annotations = reader.readAnnotations();
            }

            buildContainers(typeNames, modelGroupNames, attributeGroupNames);
        } finally {
            if (reader != null) {
                reader.readEnd();
            }
        }
    }

    void saveIndex() {
        String handle = "index";
        XsbReader saver = new XsbReader(getTypeSystem(), handle);
        try {
            saver.writeIndexData();
            saver.writeRealHeader(handle, FILETYPE_SCHEMAINDEX);
            saver.writeIndexData();
            saver.writeEnd();
        } finally {
            saver.closeOutputQuietly();
        }
    }

    void savePointers() {
        savePointersForComponents(globalElements(), getMetadataPath() + "/element/");
        savePointersForComponents(globalAttributes(), getMetadataPath() + "/attribute/");
        savePointersForComponents(modelGroups(), getMetadataPath() + "/modelgroup/");
        savePointersForComponents(attributeGroups(), getMetadataPath() + "/attributegroup/");
        savePointersForComponents(globalTypes(), getMetadataPath() + "/type/");
        savePointersForComponents(identityConstraints(), getMetadataPath() + "/identityconstraint/");
        savePointersForNamespaces(_namespaces, getMetadataPath() + "/namespace/");
        savePointersForClassnames(_typeRefsByClassname.keySet(), getMetadataPath() + "/javaname/");
        savePointersForComponents(redefinedModelGroups(), getMetadataPath() + "/redefinedmodelgroup/");
        savePointersForComponents(redefinedAttributeGroups(), getMetadataPath() + "/redefinedattributegroup/");
        savePointersForComponents(redefinedGlobalTypes(), getMetadataPath() + "/redefinedtype/");
    }

    void savePointersForComponents(SchemaComponent[] components, String dir) {
        for (SchemaComponent component : components) {
            savePointerFile(dir + QNameHelper.hexsafedir(component.getName()), _name);
        }
    }

    void savePointersForClassnames(Set<String> classnames, String dir) {
        for (String classname : classnames) {
            savePointerFile(dir + classname.replace('.', '/'), _name);
        }
    }

    void savePointersForNamespaces(Set<String> namespaces, String dir) {
        for (String ns : namespaces) {
            savePointerFile(dir + QNameHelper.hexsafedir(new QName(ns, "xmlns")), _name);
        }
    }

    void savePointerFile(String filename, String name) {
        XsbReader saver = new XsbReader(getTypeSystem(), filename);
        try {
            saver.writeString(name);
            saver.writeRealHeader(filename, FILETYPE_SCHEMAPOINTER);
            saver.writeString(name);
            saver.writeEnd();
        } finally {
            saver.closeOutputQuietly();
        }
    }

    private Map<String, SchemaComponent.Ref> buildTypeRefsByClassname(Map<String, SchemaType> typesByClassname) {
        Map<String, SchemaComponent.Ref> result = new LinkedHashMap<>();
        for (String className : typesByClassname.keySet()) {
            result.put(className, typesByClassname.get(className).getRef());
        }
        return result;
    }

    private static Map<QName, SchemaComponent.Ref> buildComponentRefMap(SchemaComponent[] components) {
        return buildComponentRefMap(Arrays.asList(components));
    }

    private static Map<QName, SchemaComponent.Ref> buildComponentRefMap(List<? extends SchemaComponent> components) {
        return components.stream().collect(Collectors.toMap(SchemaComponent::getName, SchemaComponent::getComponentRef,
            (u, v) -> v, LinkedHashMap::new));
    }

    private static List<SchemaComponent.Ref> buildComponentRefList(SchemaComponent[] components) {
        return buildComponentRefList(Arrays.asList(components));
    }

    private static List<SchemaComponent.Ref> buildComponentRefList(List<? extends SchemaComponent> components) {
        return components.stream().map(SchemaComponent::getComponentRef).collect(Collectors.toList());
    }

    private static Map<QName, SchemaComponent.Ref> buildDocumentMap(SchemaType[] types) {
        return buildDocumentMap(Arrays.asList(types));
    }

    private static Map<QName, SchemaComponent.Ref> buildDocumentMap(List<? extends SchemaComponent> types) {
        Map<QName, SchemaComponent.Ref> result = new LinkedHashMap<>();
        for (SchemaComponent comp : types) {
            SchemaType type = (SchemaType) comp;
            result.put(type.getDocumentElementName(), type.getRef());
        }
        return result;
    }

    private static Map<QName, SchemaComponent.Ref> buildAttributeTypeMap(SchemaType[] types) {
        Map<QName, SchemaComponent.Ref> result = new LinkedHashMap<>();
        for (SchemaType type : types) {
            result.put(type.getAttributeTypeAttributeName(), type.getRef());
        }
        return result;
    }

    private static Map<QName, SchemaComponent.Ref> buildAttributeTypeMap(List<? extends SchemaComponent> types) {
        Map<QName, SchemaComponent.Ref> result = new LinkedHashMap<>();
        for (SchemaComponent comp : types) {
            SchemaType type = (SchemaType) comp;
            result.put(type.getAttributeTypeAttributeName(), type.getRef());
        }
        return result;
    }

    // Container operation
    SchemaContainer getContainer(String namespace) {
        return _containers.get(namespace);
    }

    private void addContainer(String namespace) {
        SchemaContainer c = new SchemaContainer(namespace);
        c.setTypeSystem(this);
        _containers.put(namespace, c);
    }

    SchemaContainer getContainerNonNull(String namespace) {
        SchemaContainer result = getContainer(namespace);
        if (result == null) {
            addContainer(namespace);
            result = getContainer(namespace);
        }
        return result;
    }

    String getSourceCodeEncoding() {
        return _sourceCodeEncoding ;
    }

    @SuppressWarnings("unchecked")
    private <T extends SchemaComponent.Ref> void buildContainersHelper(Map<QName, SchemaComponent.Ref> elements, BiConsumer<SchemaContainer, T> adder) {
        elements.forEach((k, v) -> adder.accept(getContainerNonNull(k.getNamespaceURI()), (T) v));
    }

    @SuppressWarnings("unchecked")
    private <T extends SchemaComponent.Ref> void buildContainersHelper(List<SchemaComponent.Ref> refs, List<QName> names, BiConsumer<SchemaContainer, T> adder) {
        Iterator<SchemaComponent.Ref> it = refs.iterator();
        Iterator<QName> itname = names.iterator();
        while (it.hasNext()) {
            String ns = itname.next().getNamespaceURI();
            SchemaContainer sc = getContainerNonNull(ns);
            adder.accept(sc, (T) it.next());
        }
    }

    // Only called during init
    private void buildContainers(List<QName> redefTypeNames, List<QName> redefModelGroupNames, List<QName> redefAttributeGroupNames) {
        // This method walks the reference maps and copies said references
        // into the appropriate container
        buildContainersHelper(_globalElements, SchemaContainer::addGlobalElement);
        buildContainersHelper(_globalAttributes, SchemaContainer::addGlobalAttribute);
        buildContainersHelper(_modelGroups, SchemaContainer::addModelGroup);
        buildContainersHelper(_attributeGroups, SchemaContainer::addAttributeGroup);
        buildContainersHelper(_identityConstraints, SchemaContainer::addIdentityConstraint);
        buildContainersHelper(_globalTypes, SchemaContainer::addGlobalType);
        buildContainersHelper(_attributeTypes, SchemaContainer::addAttributeType);

        // Some earlier .xsb versions don't have records for redefinitions
        if (_redefinedGlobalTypes != null && _redefinedModelGroups != null &&
            _redefinedAttributeGroups != null) {
            assert _redefinedGlobalTypes.size() == redefTypeNames.size();
            buildContainersHelper(_redefinedGlobalTypes, redefTypeNames, SchemaContainer::addRedefinedType);
            buildContainersHelper(_redefinedModelGroups, redefModelGroupNames, SchemaContainer::addRedefinedModelGroup);
            buildContainersHelper(_redefinedAttributeGroups, redefAttributeGroupNames, SchemaContainer::addRedefinedAttributeGroup);
        }
        // Some earlier .xsb versions don't have records for annotations
        if (_annotations != null && !_annotations.isEmpty()) {
            // BUGBUG(radup)
            _annotations.forEach(getContainerNonNull("")::addAnnotation);
        }
        _containers.values().forEach(SchemaContainer::setImmutable);
    }

    /**
     * This is the crux of the container work and role.
     * It makes a sweep over all containers and fixes each container's
     * typesystem to point to this typesystem.
     * Because SchemaComponents have a link to their containers, this has as
     * effect all components now indirectly pointing to this typesystem
     * even though they (as well as the typesystem itself) are immutable.
     */
    private void fixupContainers() {
        for (SchemaContainer container : _containers.values()) {
            container.setTypeSystem(this);
            container.setImmutable();
        }
    }

    private void assertContainersHelper(Map<QName, SchemaComponent.Ref> comp, Function<SchemaContainer, List<? extends SchemaComponent>> fun, Function<List<? extends SchemaComponent>, ? extends Map<QName, SchemaComponent.Ref>> fun2) {
        final Map<QName, SchemaComponent.Ref> temp = _containers.values().stream()
            .map(fun).map(scs -> fun2 == null ? SchemaTypeSystemImpl.buildComponentRefMap(scs) : fun2.apply(scs) )
            .map(Map::entrySet).flatMap(Set::stream)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assert comp.equals(temp);
    }

    private void assertContainersHelper(List<? extends SchemaComponent.Ref> comp, Function<SchemaContainer, List<? extends SchemaComponent>> fun) {
        final Set<SchemaComponent.Ref> temp = _containers.values().stream()
            .map(fun).map(SchemaTypeSystemImpl::buildComponentRefList)
            .flatMap(List::stream).collect(Collectors.toSet());
        assert new HashSet<>(comp).equals(temp);
    }

    @SuppressWarnings({"AssertWithSideEffects", "ConstantConditions"})
    private void assertContainersSynchronized() {
        boolean assertEnabled = false;
        // This code basically checks whether asserts are enabled so we don't do
        // all the work if they arent
        assert assertEnabled = true;
        if (!assertEnabled) {
            return;
        }

        assertContainersHelper(_globalElements, SchemaContainer::globalElements, null);
        assertContainersHelper(_globalAttributes, SchemaContainer::globalAttributes, null);
        assertContainersHelper(_modelGroups, SchemaContainer::modelGroups, null);
        assertContainersHelper(_modelGroups, SchemaContainer::modelGroups, null);
        assertContainersHelper(_redefinedModelGroups, SchemaContainer::redefinedModelGroups);
        assertContainersHelper(_attributeGroups, SchemaContainer::attributeGroups, null);
        assertContainersHelper(_redefinedAttributeGroups, SchemaContainer::redefinedAttributeGroups);
        assertContainersHelper(_globalTypes, SchemaContainer::globalTypes, null);
        assertContainersHelper(_redefinedGlobalTypes, SchemaContainer::redefinedGlobalTypes);
        assertContainersHelper(_documentTypes, SchemaContainer::documentTypes, SchemaTypeSystemImpl::buildDocumentMap);
        assertContainersHelper(_attributeTypes, SchemaContainer::attributeTypes, SchemaTypeSystemImpl::buildAttributeTypeMap);
        assertContainersHelper(_identityConstraints, SchemaContainer::identityConstraints, null);

        // annotations
        Set<SchemaAnnotation> temp3 = _containers.values().stream()
            .map(SchemaContainer::annotations).flatMap(List::stream).collect(Collectors.toSet());
        assert new HashSet<>(_annotations).equals(temp3);
        // namespaces
        Set<String> temp4 = _containers.values().stream()
            .map(SchemaContainer::getNamespace).collect(Collectors.toSet());
        assert _namespaces.equals(temp4);
    }

    private static Random _random;
    private static final byte[] _mask = new byte[128 / 8];

    /**
     * Fun, fun.  Produce 128 bits of uniqueness randomly.
     * We used to use SecureRandom, but now we don't because SecureRandom
     * hits the filesystem and hangs us on a filesystem lock.  It also eats
     * a thread and other expensive resources.. :-).
     * <p>
     * We don't really care that non-secure Random() can only do 48 bits of
     * randomness, since we're certainly not going to be called more than 2^48
     * times within our process lifetime.
     * <p>
     * Our real concern is that by seeding Random() with the current
     * time, two users will end up with the same bits if they start a
     * schema compilation within the same millisecond.  That makes the
     * probability of collision in the real world slightly too high.
     * We're going to have millions of users, remember?  With a million
     * users, and one-compilation-per-day each, we'd see a collision every
     * few months.
     * <p>
     * So we'll just xor the results of random with our few extra
     * bits of information computed below to help reduce the probability
     * of collision by a few decimal places.  To collide, you will have had
     * to have the same amount of free memory, the same user name, timezone,
     * and country, the same current directory, the same java classpath,
     * the same operating system and jvm version, and the same choices of
     * identity hashcodes for a few objects. And be started within the same
     * millisecond. Or you can collide if you have a cosmic 128-bit mathematical
     * coincidence. No worries.
     */
    private static synchronized void nextBytes(byte[] result) {
        if (_random == null) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                try (LongUTFDataOutputStream daos = new LongUTFDataOutputStream(baos)) {
                    // at least 10 bits of uniqueness, right?  Maybe even 50 or 60.
                    daos.writeInt(System.identityHashCode(SchemaTypeSystemImpl.class));
                    String[] props = new String[]{"user.name", "user.dir", "user.timezone", "user.country", "java.class.path", "java.home", "java.vendor", "java.version", "os.version"};
                    for (String s : props) {
                        String prop = SystemProperties.getProperty(s);
                        if (prop != null) {
                            daos.writeUTF(prop);
                            daos.writeInt(System.identityHashCode(prop));
                        }
                    }
                    daos.writeLong(Runtime.getRuntime().freeMemory());
                }
                byte[] bytes = baos.toByteArray();
                for (int i = 0; i < bytes.length; i++) {
                    int j = i % _mask.length;
                    _mask[j] *= (byte) 21;
                    _mask[j] += (byte) i;
                }
            } catch (IOException e) {
                XBeanDebug.LOG.atDebug().withThrowable(e).log(e.getMessage());
            }

            _random = new Random(System.currentTimeMillis());
        }
        _random.nextBytes(result);
        for (int i = 0; i < result.length; i++) {
            int j = i & _mask.length;
            result[i] ^= _mask[j];
        }
    }

    public SchemaTypeSystemImpl(String nameForSystem) {
        // if we have no name, select a random one
        if (nameForSystem == null) {
            // get 128 random bits (that'll be 32 hex digits)
            byte[] bytes = new byte[128 / 8];
            nextBytes(bytes);
            nameForSystem = "s" + new String(HexBin.encode(bytes), StandardCharsets.ISO_8859_1);
        }

        _name = SchemaTypeSystemImpl.METADATA_PACKAGE_GEN.replace('/', '.') + ".system." + nameForSystem;
        _classloader = null;
    }

    public void loadFromStscState(StscState state) {
        assert (_classloader == null);
        _localHandles = new SchemaTypePool(getTypeSystem());
        _globalElements = buildComponentRefMap(state.globalElements());
        _globalAttributes = buildComponentRefMap(state.globalAttributes());
        _modelGroups = buildComponentRefMap(state.modelGroups());
        _redefinedModelGroups = buildComponentRefList(state.redefinedModelGroups());
        _attributeGroups = buildComponentRefMap(state.attributeGroups());
        _redefinedAttributeGroups = buildComponentRefList(state.redefinedAttributeGroups());
        _globalTypes = buildComponentRefMap(state.globalTypes());
        _redefinedGlobalTypes = buildComponentRefList(state.redefinedGlobalTypes());
        _documentTypes = buildDocumentMap(state.documentTypes());
        _attributeTypes = buildAttributeTypeMap(state.attributeTypes());
        _typeRefsByClassname = buildTypeRefsByClassname(state.typesByClassname());
        _identityConstraints = buildComponentRefMap(state.idConstraints());
        _annotations = state.annotations();
        _namespaces = new HashSet<>(Arrays.asList(state.getNamespaces()));
        _containers = state.getContainerMap();
        _sourceCodeEncoding  = state.sourceCodeEncoding();
        fixupContainers();
        // Checks that data in the containers matches the lookup maps
        assertContainersSynchronized();
        setDependencies(state.getDependencies());
    }

    final SchemaTypeSystemImpl getTypeSystem() {
        return this;
    }

    void setDependencies(SchemaDependencies deps) {
        _deps = deps;
    }

    SchemaDependencies getDependencies() {
        return _deps;
    }

    // EXPERIMENTAL
    public boolean isIncomplete() {
        return _incomplete;
    }

    // EXPERIMENTAL
    void setIncomplete(boolean incomplete) {
        _incomplete = incomplete;
    }

    static class StringPool {
        private final List<String> intsToStrings = new ArrayList<>();
        private final Map<String, Integer> stringsToInts = new HashMap<>();
        private final String _handle;
        private final String _name;

        /**
         * Constructs an empty StringPool to be filled with strings.
         */
        StringPool(String handle, String name) {
            _handle = handle;
            _name = name;
            intsToStrings.add(null);
        }

        int codeForString(String str) {
            if (str == null) {
                return 0;
            }
            Integer result = stringsToInts.get(str);
            if (result == null) {
                result = intsToStrings.size();
                intsToStrings.add(str);
                stringsToInts.put(str, result);
            }
            return result;
        }

        String stringForCode(int code) {
            if (code < 0 || code >= intsToStrings.size()) {
                throw new SchemaTypeLoaderException("String code " + code + " out of range", _name, _handle, SchemaTypeLoaderException.UNRECOGNIZED_INDEX_ENTRY);
            }
            return code == 0 ? null : intsToStrings.get(code);
        }

        void writeTo(LongUTFDataOutputStream output) {
            try {
                int cnt = intsToStrings.size();
                output.writeShortOrInt(cnt);
                boolean isNext = false;
                for (String str : intsToStrings) {
                    if (isNext) {
                        output.writeLongUTF(str);
                    }
                    isNext = true;
                }
            } catch (IOException e) {
                throw new SchemaTypeLoaderException(e.getMessage(), _name, _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
            }
        }

        void readFrom(LongUTFDataInputStream input) {
            if (intsToStrings.size() != 1 || !stringsToInts.isEmpty()) {
                throw new IllegalStateException();
            }

            try {
                int size = input.readUnsignedShortOrInt();
                for (int i = 1; i < size; i++) {
                    String str = input.readLongUTF().intern();
                    int code = codeForString(str);
                    if (code != i) {
                        throw new SchemaTypeLoaderException("Repeated string pool entry", _name, _handle, SchemaTypeLoaderException.UNRECOGNIZED_INDEX_ENTRY);
                    }
                }
            } catch (IOException e) {
                throw new SchemaTypeLoaderException(e.getMessage() == null ? e.getMessage() : "IO Exception", _name, _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
            }
        }
    }

    public void saveToDirectory(File classDir) {
        save(new FilerImpl(classDir, null, null, false, false));
    }

    public void save(Filer filer) {
        if (_incomplete) {
            throw new IllegalStateException("Incomplete SchemaTypeSystems cannot be saved.");
        }

        if (filer == null) {
            throw new IllegalArgumentException("filer must not be null");
        }
        _filer = filer;

        _localHandles.startWriteMode();
        saveTypesRecursively(globalTypes());
        saveTypesRecursively(documentTypes());
        saveTypesRecursively(attributeTypes());
        saveGlobalElements(globalElements());
        saveGlobalAttributes(globalAttributes());
        saveModelGroups(modelGroups());
        saveAttributeGroups(attributeGroups());
        saveIdentityConstraints(identityConstraints());

        saveTypesRecursively(redefinedGlobalTypes());
        saveModelGroups(redefinedModelGroups());
        saveAttributeGroups(redefinedAttributeGroups());

        saveIndex();
        savePointers();
    }

    void saveTypesRecursively(SchemaType[] types) {
        for (SchemaType type : types) {
            if (type.getTypeSystem() != getTypeSystem()) {
                continue;
            }
            saveType(type);
            saveTypesRecursively(type.getAnonymousTypes());
        }
    }

    public void saveGlobalElements(SchemaGlobalElement[] elts) {
        if (_incomplete) {
            throw new IllegalStateException("This SchemaTypeSystem cannot be saved.");
        }
        for (SchemaGlobalElement elt : elts) {
            saveGlobalElement(elt);
        }
    }

    public void saveGlobalAttributes(SchemaGlobalAttribute[] attrs) {
        if (_incomplete) {
            throw new IllegalStateException("This SchemaTypeSystem cannot be saved.");
        }
        for (SchemaGlobalAttribute attr : attrs) {
            saveGlobalAttribute(attr);
        }
    }

    public void saveModelGroups(SchemaModelGroup[] groups) {
        if (_incomplete) {
            throw new IllegalStateException("This SchemaTypeSystem cannot be saved.");
        }
        for (SchemaModelGroup group : groups) {
            saveModelGroup(group);
        }
    }

    public void saveAttributeGroups(SchemaAttributeGroup[] groups) {
        if (_incomplete) {
            throw new IllegalStateException("This SchemaTypeSystem cannot be saved.");
        }
        for (SchemaAttributeGroup group : groups) {
            saveAttributeGroup(group);
        }
    }

    public void saveIdentityConstraints(SchemaIdentityConstraint[] idcs) {
        if (_incomplete) {
            throw new IllegalStateException("This SchemaTypeSystem cannot be saved.");
        }
        for (SchemaIdentityConstraint idc : idcs) {
            saveIdentityConstraint(idc);
        }
    }

    public void saveGlobalElement(SchemaGlobalElement elt) {
        if (_incomplete) {
            throw new IllegalStateException("This SchemaTypeSystem cannot be saved.");
        }
        String handle = _localHandles.handleForElement(elt);
        XsbReader saver = new XsbReader(getTypeSystem(), handle);
        try {
            saver.writeParticleData((SchemaParticle) elt);
            saver.writeString(elt.getSourceName());
            saver.writeRealHeader(handle, FILETYPE_SCHEMAELEMENT);
            saver.writeParticleData((SchemaParticle) elt);
            saver.writeString(elt.getSourceName());
            saver.writeEnd();
        } finally {
            saver.closeOutputQuietly();
        }
    }

    public void saveGlobalAttribute(SchemaGlobalAttribute attr) {
        if (_incomplete) {
            throw new IllegalStateException("This SchemaTypeSystem cannot be saved.");
        }
        String handle = _localHandles.handleForAttribute(attr);
        XsbReader saver = new XsbReader(getTypeSystem(), handle);
        try {
            saver.writeAttributeData(attr);
            saver.writeString(attr.getSourceName());
            saver.writeRealHeader(handle, FILETYPE_SCHEMAATTRIBUTE);
            saver.writeAttributeData(attr);
            saver.writeString(attr.getSourceName());
            saver.writeEnd();
        } finally {
            saver.closeOutputQuietly();
        }
    }

    public void saveModelGroup(SchemaModelGroup grp) {
        if (_incomplete) {
            throw new IllegalStateException("This SchemaTypeSystem cannot be saved.");
        }
        String handle = _localHandles.handleForModelGroup(grp);
        XsbReader saver = new XsbReader(getTypeSystem(), handle);
        try {
            saver.writeModelGroupData(grp);
            saver.writeRealHeader(handle, FILETYPE_SCHEMAMODELGROUP);
            saver.writeModelGroupData(grp);
            saver.writeEnd();
        } finally {
            saver.closeOutputQuietly();
        }
    }

    public void saveAttributeGroup(SchemaAttributeGroup grp) {
        if (_incomplete) {
            throw new IllegalStateException("This SchemaTypeSystem cannot be saved.");
        }
        String handle = _localHandles.handleForAttributeGroup(grp);
        XsbReader saver = new XsbReader(getTypeSystem(), handle);
        try {
            saver.writeAttributeGroupData(grp);
            saver.writeRealHeader(handle, FILETYPE_SCHEMAATTRIBUTEGROUP);
            saver.writeAttributeGroupData(grp);
            saver.writeEnd();
        } finally {
            saver.closeOutputQuietly();
        }
    }

    public void saveIdentityConstraint(SchemaIdentityConstraint idc) {
        if (_incomplete) {
            throw new IllegalStateException("This SchemaTypeSystem cannot be saved.");
        }
        String handle = _localHandles.handleForIdentityConstraint(idc);
        XsbReader saver = new XsbReader(getTypeSystem(), handle);
        try {
            saver.writeIdConstraintData(idc);
            saver.writeRealHeader(handle, FILETYPE_SCHEMAIDENTITYCONSTRAINT);
            saver.writeIdConstraintData(idc);
            saver.writeEnd();
        } finally {
            saver.closeOutputQuietly();
        }
    }

    void saveType(SchemaType type) {
        String handle = _localHandles.handleForType(type);
        XsbReader saver = new XsbReader(getTypeSystem(), handle);
        try {
            saver.writeTypeData(type);
            saver.writeRealHeader(handle, FILETYPE_SCHEMATYPE);
            saver.writeTypeData(type);
            saver.writeEnd();
        } finally {
            saver.closeOutputQuietly();
        }
    }

    public static String crackPointer(InputStream stream) {
        try (LongUTFDataInputStream input = new LongUTFDataInputStream(stream)) {

            int magic = input.readInt();
            if (magic != DATA_BABE) {
                return null;
            }

            int majorver = input.readShort();
            int minorver = input.readShort();

            if (majorver != MAJOR_VERSION) {
                return null;
            }

            if (minorver > MINOR_VERSION) {
                return null;
            }

            if (minorver >= 18) {
                input.readShort(); // release number present in atLeast(2, 18, 0)
            }

            int actualfiletype = input.readShort();
            if (actualfiletype != FILETYPE_SCHEMAPOINTER) {
                return null;
            }

            StringPool stringPool = new StringPool("pointer", "unk");
            stringPool.readFrom(input);

            return stringPool.stringForCode(input.readShort());
        } catch (IOException e) {
            return null;
        }
    }

    static final byte[] SINGLE_ZERO_BYTE = {0};

    public SchemaType typeForHandle(String handle) {
        synchronized (_resolvedHandles) {
            return (SchemaType) _resolvedHandles.get(handle);
        }
    }

    public SchemaType typeForClassname(String classname) {
        SchemaType.Ref ref = (SchemaType.Ref) _typeRefsByClassname.get(classname);
        return (ref != null) ? ref.get() : null;
    }

    public SchemaComponent resolveHandle(String handle) {
        SchemaComponent result;

        synchronized (_resolvedHandles) {
            result = _resolvedHandles.get(handle);
        }
        if (result == null) {
            XsbReader reader = new XsbReader(getTypeSystem(), handle, 0xFFFF);
            int filetype = reader.getActualFiletype();
            switch (filetype) {
                case FILETYPE_SCHEMATYPE:
                    XBeanDebug.LOG.atTrace().log("Resolving type for handle {}", handle);
                    result = reader.finishLoadingType();
                    break;
                case FILETYPE_SCHEMAELEMENT:
                    XBeanDebug.LOG.atTrace().log("Resolving element for handle {}", handle);
                    result = reader.finishLoadingElement();
                    break;
                case FILETYPE_SCHEMAATTRIBUTE:
                    XBeanDebug.LOG.atTrace().log("Resolving attribute for handle {}", handle);
                    result = reader.finishLoadingAttribute();
                    break;
                case FILETYPE_SCHEMAMODELGROUP:
                    XBeanDebug.LOG.atTrace().log("Resolving model group for handle {}", handle);
                    result = reader.finishLoadingModelGroup();
                    break;
                case FILETYPE_SCHEMAATTRIBUTEGROUP:
                    XBeanDebug.LOG.atTrace().log("Resolving attribute group for handle {}", handle);
                    result = reader.finishLoadingAttributeGroup();
                    break;
                case FILETYPE_SCHEMAIDENTITYCONSTRAINT:
                    XBeanDebug.LOG.atTrace().log("Resolving id constraint for handle {}", handle);
                    result = reader.finishLoadingIdentityConstraint();
                    break;
                default:
                    // every finishLoadingXxx() above closes the reader in a finally;
                    // this arm has to release it before unwinding
                    reader.readEnd();
                    throw new IllegalStateException("Illegal handle type");
            }

            synchronized (_resolvedHandles) {
                if (!_resolvedHandles.containsKey(handle)) {
                    _resolvedHandles.put(handle, result);
                } else {
                    result = _resolvedHandles.get(handle);
                }
            }
        }
        return result;
    }

    private final Map<String, SchemaComponent> _resolvedHandles = new HashMap<>();
    private boolean _allNonGroupHandlesResolved = false;

    public void resolve() {
        XBeanDebug.LOG.atTrace().log("Resolve called type system {}", _name);
        if (_allNonGroupHandlesResolved) {
            return;
        }

        XBeanDebug.LOG.atTrace().log("Resolving all handles for type system {}", _name);

        List<SchemaComponent.Ref> refs = new ArrayList<>();
        refs.addAll(_globalElements.values());
        refs.addAll(_globalAttributes.values());
        refs.addAll(_globalTypes.values());
        refs.addAll(_documentTypes.values());
        refs.addAll(_attributeTypes.values());
        refs.addAll(_identityConstraints.values());

        for (SchemaComponent.Ref ref : refs) {
            // Forces ref to be resolved
            ref.getComponent();
        }

        XBeanDebug.LOG.atTrace().log("Finished resolving type system {}", _name);
        _allNonGroupHandlesResolved = true;
    }


    public boolean isNamespaceDefined(String namespace) {
        return _namespaces.contains(namespace);
    }

    public SchemaType.Ref findTypeRef(QName name) {
        return (SchemaType.Ref) _globalTypes.get(name);
    }

    public SchemaType.Ref findDocumentTypeRef(QName name) {
        return (SchemaType.Ref) _documentTypes.get(name);
    }

    public SchemaType.Ref findAttributeTypeRef(QName name) {
        return (SchemaType.Ref) _attributeTypes.get(name);
    }

    public SchemaGlobalElement.Ref findElementRef(QName name) {
        return (SchemaGlobalElement.Ref) _globalElements.get(name);
    }

    public SchemaGlobalAttribute.Ref findAttributeRef(QName name) {
        return (SchemaGlobalAttribute.Ref) _globalAttributes.get(name);
    }

    public SchemaModelGroup.Ref findModelGroupRef(QName name) {
        return (SchemaModelGroup.Ref) _modelGroups.get(name);
    }

    public SchemaAttributeGroup.Ref findAttributeGroupRef(QName name) {
        return (SchemaAttributeGroup.Ref) _attributeGroups.get(name);
    }

    public SchemaIdentityConstraint.Ref findIdentityConstraintRef(QName name) {
        return (SchemaIdentityConstraint.Ref) _identityConstraints.get(name);
    }

    private static <T, U> U[] refHelper(Map<QName, SchemaComponent.Ref> map, Function<T, U> fun, IntFunction<U[]> target, U[] emptyTarget) {
        return refHelper(map == null ? null : map.values(), fun, target, emptyTarget);
    }

    private static <T, U> U[] refHelper(Collection<SchemaComponent.Ref> list, Function<T, U> fun, IntFunction<U[]> target, U[] emptyTarget) {
        //noinspection unchecked
        return (list == null || list.isEmpty()) ? emptyTarget : list.stream().map(e -> (T) e).map(fun).toArray(target);
    }

    public SchemaType[] globalTypes() {
        return refHelper(_globalTypes, SchemaType.Ref::get, SchemaType[]::new, EMPTY_ST_ARRAY);
    }

    public SchemaType[] redefinedGlobalTypes() {
        return refHelper(_redefinedGlobalTypes, SchemaType.Ref::get, SchemaType[]::new, EMPTY_ST_ARRAY);
    }

    public InputStream getSourceAsStream(String sourceName) {
        if (!sourceName.startsWith("/")) {
            sourceName = "/" + sourceName;
        }

        return _resourceLoader.getResourceAsStream(getMetadataPath() + "/src" + sourceName);
    }

    SchemaContainer[] containers() {
        return _containers.values().toArray(new SchemaContainer[0]);
    }

    public SchemaType[] documentTypes() {
        return refHelper(_documentTypes, SchemaType.Ref::get, SchemaType[]::new, EMPTY_ST_ARRAY);
    }

    public SchemaType[] attributeTypes() {
        return refHelper(_attributeTypes, SchemaType.Ref::get, SchemaType[]::new, EMPTY_ST_ARRAY);
    }

    public SchemaGlobalElement[] globalElements() {
        return refHelper(_globalElements, SchemaGlobalElement.Ref::get, SchemaGlobalElement[]::new, EMPTY_GE_ARRAY);
    }

    public SchemaGlobalAttribute[] globalAttributes() {
        return refHelper(_globalAttributes, SchemaGlobalAttribute.Ref::get, SchemaGlobalAttribute[]::new, EMPTY_GA_ARRAY);
    }

    public SchemaModelGroup[] modelGroups() {
        return refHelper(_modelGroups, SchemaModelGroup.Ref::get, SchemaModelGroup[]::new, EMPTY_MG_ARRAY);
    }

    public SchemaModelGroup[] redefinedModelGroups() {
        return refHelper(_redefinedModelGroups, SchemaModelGroup.Ref::get, SchemaModelGroup[]::new, EMPTY_MG_ARRAY);
    }

    public SchemaAttributeGroup[] attributeGroups() {
        return refHelper(_attributeGroups, SchemaAttributeGroup.Ref::get, SchemaAttributeGroup[]::new, EMPTY_AG_ARRAY);
    }

    public SchemaAttributeGroup[] redefinedAttributeGroups() {
        return refHelper(_redefinedAttributeGroups, SchemaAttributeGroup.Ref::get, SchemaAttributeGroup[]::new, EMPTY_AG_ARRAY);
    }

    public SchemaAnnotation[] annotations() {
        return (_annotations == null || _annotations.isEmpty()) ? EMPTY_ANN_ARRAY : _annotations.toArray(EMPTY_ANN_ARRAY);
    }

    public SchemaIdentityConstraint[] identityConstraints() {
        return refHelper(_identityConstraints, SchemaIdentityConstraint.Ref::get, SchemaIdentityConstraint[]::new, EMPTY_IC_ARRAY);
    }

    public ClassLoader getClassLoader() {
        return _classloader;
    }

    /**
     * Used INTERNALLY ONLY by the code output AFTER the type system has
     * been saved and a handle has been established for each type.
     */
    public String handleForType(SchemaType type) {
        return _localHandles.handleForType(type);
    }

    public String getName() {
        return _name;
    }

    /**
     * Provide method to be overridden by user typesystems using a different metadata path
     *
     * @return the metadata directory
     * @since XmlBeans 3.1.0
     */
    public String getMetadataPath() {
        Matcher m = packPat.matcher(_name);
        String n = m.find() ? m.group(1) : _name;
        return n.replace('.', '/');
    }

    String getBasePackage() {
        return nameToPathString(_name);
    }

    SchemaTypeLoader getLinker() {
        return _linker;
    }

    SchemaTypePool getTypePool() {
        return _localHandles;
    }

    Set<String> getNamespaces() {
        return _namespaces;
    }

    Map<String, SchemaComponent.Ref> getTypeRefsByClassname() {
        return _typeRefsByClassname;
    }

    OutputStream getSaverStream(String name, String handle) {
        try {
            return _filer.createBinaryFile(name);
        } catch (IOException e) {
            throw new SchemaTypeLoaderException(e.getMessage(), getName(), handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
        }
    }

    InputStream getLoaderStream(String resourcename) {
        return _resourceLoader.getResourceAsStream(resourcename);
    }
}
