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
import org.apache.xmlbeans.impl.util.LongUTFDataInputStream;
import org.apache.xmlbeans.impl.util.LongUTFDataOutputStream;
import org.apache.xmlbeans.impl.values.XmlObjectBase;
import org.apache.xmlbeans.impl.xb.xsdschema.AttributeGroupDocument;
import org.apache.xmlbeans.impl.xb.xsdschema.GroupDocument;
import org.apache.xmlbeans.soap.SOAPArrayType;
import org.apache.xmlbeans.soap.SchemaWSDLArrayType;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.*;

import static org.apache.xmlbeans.impl.schema.SchemaTypeSystemImpl.*;

class XsbReader {

    // MAX_UNSIGNED_SHORT
    private static final int MAX_UNSIGNED_SHORT = Short.MAX_VALUE * 2 + 1;

    private final SchemaTypeSystemImpl typeSystem;
    private LongUTFDataInputStream _input;
    private LongUTFDataOutputStream _output;
    private SchemaTypeSystemImpl.StringPool _stringPool;
    private String _handle;
    private int _majorver;
    private int _minorver;
    private int _releaseno;
    int _actualfiletype;

    XsbReader(SchemaTypeSystemImpl typeSystem, String handle) {
        this.typeSystem = typeSystem;
        _handle = handle;
        _stringPool = new SchemaTypeSystemImpl.StringPool(_handle, typeSystem.getName());
    }

    public XsbReader(SchemaTypeSystemImpl typeSystem, String handle, int filetype) {
        this.typeSystem = typeSystem;
        String resourcename = typeSystem.getBasePackage()  + handle + ".xsb";
        InputStream rawinput = typeSystem.getLoaderStream(resourcename);
        if (rawinput == null) {
            throw new SchemaTypeLoaderException("XML-BEANS compiled schema: Could not locate compiled schema resource " + resourcename, typeSystem.getName(), handle, SchemaTypeLoaderException.NO_RESOURCE);
        }

        _input = new LongUTFDataInputStream(rawinput);
        _handle = handle;

        // A rejected header leaves the caller without a reference to close, so the
        // resource stream has to be released here on the way out.
        boolean headerRead = false;
        try {
            readHeader(handle, filetype);
            headerRead = true;
        } finally {
            if (!headerRead) {
                readEnd();
            }
        }
    }

    private void readHeader(String handle, int filetype) {
        int magic = readInt();
        if (magic != DATA_BABE) {
            throw new SchemaTypeLoaderException("XML-BEANS compiled schema: Wrong magic cookie", typeSystem.getName(), handle, SchemaTypeLoaderException.WRONG_MAGIC_COOKIE);
        }

        _majorver = readShort();
        _minorver = readShort();

        if (_majorver != MAJOR_VERSION) {
            throw new SchemaTypeLoaderException("XML-BEANS compiled schema: Wrong major version - expecting " + MAJOR_VERSION + ", got " + _majorver, typeSystem.getName(), handle, SchemaTypeLoaderException.WRONG_MAJOR_VERSION);
        }

        if (_minorver > MINOR_VERSION) {
            throw new SchemaTypeLoaderException("XML-BEANS compiled schema: Incompatible minor version - expecting up to " + MINOR_VERSION + ", got " + _minorver, typeSystem.getName(), handle, SchemaTypeLoaderException.WRONG_MINOR_VERSION);
        }

        // Clip to 14 because we're not backward compatible with earlier
        // minor versions.  Remove this when upgrading to a new major
        // version

        if (_minorver < 14) {
            throw new SchemaTypeLoaderException("XML-BEANS compiled schema: Incompatible minor version - expecting at least 14, got " + _minorver, typeSystem.getName(), handle, SchemaTypeLoaderException.WRONG_MINOR_VERSION);
        }

        if (atLeast(2, 18, 0)) {
            _releaseno = readShort();
        }

        int actualfiletype = readShort();
        if (actualfiletype != filetype && filetype != 0xFFFF) {
            throw new SchemaTypeLoaderException("XML-BEANS compiled schema: File has the wrong type - expecting type " + filetype + ", got type " + actualfiletype, typeSystem.getName(), handle, SchemaTypeLoaderException.WRONG_FILE_TYPE);
        }

        _stringPool = new SchemaTypeSystemImpl.StringPool(_handle, typeSystem.getName());
        _stringPool.readFrom(_input);

        _actualfiletype = actualfiletype;
    }

    protected boolean atLeast(int majorver, int minorver, int releaseno) {
        if (_majorver > majorver) {
            return true;
        }
        if (_majorver < majorver) {
            return false;
        }
        if (_minorver > minorver) {
            return true;
        }
        if (_minorver < minorver) {
            return false;
        }
        return (_releaseno >= releaseno);
    }

    protected boolean atMost(int majorver, int minorver, int releaseno) {
        if (_majorver > majorver) {
            return false;
        }
        if (_majorver < majorver) {
            return true;
        }
        if (_minorver > minorver) {
            return false;
        }
        if (_minorver < minorver) {
            return true;
        }
        return (_releaseno <= releaseno);
    }

    int getActualFiletype() {
        return _actualfiletype;
    }

    void writeRealHeader(String handle, int filetype) {
        // hackeroo: if handle contains a "/" it's not relative.
        String resourcename;

        if (handle.indexOf('/') >= 0) {
            resourcename = handle + ".xsb";
        } else {
            resourcename = typeSystem.getBasePackage() + handle + ".xsb";
        }

        OutputStream rawoutput = typeSystem.getSaverStream(resourcename, _handle);
        if (rawoutput == null) {
            throw new SchemaTypeLoaderException("Could not write compiled schema resource " + resourcename, typeSystem.getName(), handle, SchemaTypeLoaderException.NOT_WRITEABLE);
        }

        _output = new LongUTFDataOutputStream(rawoutput);
        _handle = handle;

        writeInt(DATA_BABE);
        writeShort(MAJOR_VERSION);
        writeShort(MINOR_VERSION);
        writeShort(RELEASE_NUMBER);
        writeShort(filetype);

        _stringPool.writeTo(_output);
    }

    void readEnd() {
        try {
            if (_input != null) {
                _input.close();
            }
        } catch (IOException e) {
            // oh, well.
        }
        _input = null;
        _stringPool = null;
        _handle = null;
    }

    /**
     * Releases an output stream left open by a write that was abandoned part-way
     * through. A completed writeEnd() has already cleared it, so this does nothing.
     */
    void closeOutputQuietly() {
        if (_output != null) {
            try {
                _output.close();
            } catch (IOException e) {
                // the caller is already unwinding - don't mask the real failure
            }
            _output = null;
            _stringPool = null;
            _handle = null;
        }
    }

    void writeEnd() {
        try {
            if (_output != null) {
                _output.flush();
                _output.close();
            }
        } catch (IOException e) {
            throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
        }
        _output = null;
        _stringPool = null;
        _handle = null;
    }

    void writeIndexData() {
        // has a handle pool (count, handle/type, handle/type...)
        typeSystem.getTypePool().writeHandlePool(this);

        // then a qname map of global elements (count, qname/handle, qname/handle...)
        writeQNameMap(typeSystem.globalElements());

        // qname map of global attributes
        writeQNameMap(typeSystem.globalAttributes());

        // qname map of model groups
        writeQNameMap(typeSystem.modelGroups());

        // qname map of attribute groups
        writeQNameMap(typeSystem.attributeGroups());

        // qname map of identity constraints
        writeQNameMap(typeSystem.identityConstraints());

        // qname map of global types
        writeQNameMap(typeSystem.globalTypes());

        // qname map of document types, by the qname of the contained element
        writeDocumentTypeMap(typeSystem.documentTypes());

        // qname map of attribute types, by the qname of the contained attribute
        writeAttributeTypeMap(typeSystem.attributeTypes());

        // all the types by classname
        writeClassnameMap(typeSystem.getTypeRefsByClassname());

        // all the namespaces
        writeNamespaces(typeSystem.getNamespaces());

        // VERSION 2.15 and newer below
        writeQNameMap(typeSystem.redefinedGlobalTypes());
        writeQNameMap(typeSystem.redefinedModelGroups());
        writeQNameMap(typeSystem.redefinedAttributeGroups());
        writeAnnotations(typeSystem.annotations());
    }

    int readShort() {
        try {
            return _input.readUnsignedShort();
        } catch (IOException e) {
            throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
        }
    }

    void writeShort(int s) {
        if (s >= MAX_UNSIGNED_SHORT || s < -1) {
            throw new SchemaTypeLoaderException("Value " + s + " out of range: must fit in a 16-bit unsigned short.", typeSystem.getName(), _handle, SchemaTypeLoaderException.INT_TOO_LARGE);
        }
        if (_output != null) {
            try {
                _output.writeShort(s);
            } catch (IOException e) {
                throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
            }
        }
    }

    int readUnsignedShortOrInt() {
        try {
            return _input.readUnsignedShortOrInt();
        } catch (IOException e) {
            throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
        }
    }

    void writeShortOrInt(int s) {
        if (_output != null) {
            try {
                _output.writeShortOrInt(s);
            } catch (IOException e) {
                throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
            }
        }
    }

    int readInt() {
        try {
            return _input.readInt();
        } catch (IOException e) {
            throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
        }
    }

    void writeInt(int i) {
        if (_output != null) {
            try {
                _output.writeInt(i);
            } catch (IOException e) {
                throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
            }
        }
    }

    String readString() {
        int code = readUnsignedShortOrInt();
        return _stringPool.stringForCode(code);
    }

    void writeString(String str) {
        int code = _stringPool.codeForString(str);
        writeShortOrInt(code);
    }

    QName readQName() {
        String namespace = readString();
        String localname = readString();
        if (localname == null) {
            return null;
        }
        return new QName(namespace, localname);
    }

    void writeQName(QName qname) {
        if (qname == null) {
            writeString(null);
            writeString(null);
            return;
        }
        writeString(qname.getNamespaceURI());
        writeString(qname.getLocalPart());
    }

    SOAPArrayType readSOAPArrayType() {
        QName qName = readQName();
        String dimensions = readString();
        if (qName == null) {
            return null;
        }
        return new SOAPArrayType(qName, dimensions);
    }

    void writeSOAPArrayType(SOAPArrayType arrayType) {
        if (arrayType == null) {
            writeQName(null);
            writeString(null);
        } else {
            writeQName(arrayType.getQName());
            writeString(arrayType.soap11DimensionString());
        }
    }

    void writeAnnotation(SchemaAnnotation a) {
        // Write attributes
        if (a == null) {
            writeInt(-1);
            return;
        }
        SchemaAnnotation.Attribute[] attributes = a.getAttributes();
        writeInt(attributes.length);
        for (SchemaAnnotation.Attribute attribute : attributes) {
            QName name = attribute.getName();
            String value = attribute.getValue();
            String valueURI = attribute.getValueUri();
            writeQName(name);
            writeString(value);
            writeString(valueURI);
        }

        // Write documentation items
        XmlObject[] documentationItems = a.getUserInformation();
        writeInt(documentationItems.length);
        XmlOptions opt = new XmlOptions().setSaveOuter().setSaveAggressiveNamespaces();
        for (XmlObject doc : documentationItems) {
            writeString(doc.xmlText(opt));
        }

        // Write application info items
        XmlObject[] appInfoItems = a.getApplicationInformation();
        writeInt(appInfoItems.length);
        for (XmlObject doc : appInfoItems) {
            writeString(doc.xmlText(opt));
        }
    }

    SchemaAnnotation readAnnotation(SchemaContainer c) {
        if (!atLeast(2, 19, 0)) {
            return null; // no annotations for this version of the file
        }
        // Read attributes
        int n = readInt();
        if (n == -1) {
            return null;
        }
        checkAnnotationCount(n);
        SchemaAnnotation.Attribute[] attributes =
            new SchemaAnnotation.Attribute[n];
        for (int i = 0; i < n; i++) {
            QName name = readQName();
            String value = readString();
            String valueUri = null;
            if (atLeast(2, 24, 0)) {
                valueUri = readString();
            }
            attributes[i] = new SchemaAnnotationImpl.AttributeImpl(name, value, valueUri);
        }

        // Read documentation items
        n = readInt();
        checkAnnotationCount(n);
        String[] docStrings = new String[n];
        for (int i = 0; i < n; i++) {
            docStrings[i] = readString();
        }

        // Read application info items
        n = readInt();
        checkAnnotationCount(n);
        String[] appInfoStrings = new String[n];
        for (int i = 0; i < n; i++) {
            appInfoStrings[i] = readString();
        }

        return new SchemaAnnotationImpl(c, appInfoStrings,
            docStrings, attributes);
    }

    void writeAnnotations(SchemaAnnotation[] anns) {
        writeInt(anns.length);
        for (SchemaAnnotation ann : anns) {
            writeAnnotation(ann);
        }
    }

    List<SchemaAnnotation> readAnnotations() {
        int n = readInt();
        checkAnnotationCount(n);
        List<SchemaAnnotation> result = new ArrayList<>(n);
        // BUGBUG(radup)
        SchemaContainer container = typeSystem.getContainerNonNull("");
        for (int i = 0; i < n; i++) {
            result.add(readAnnotation(container));
        }
        return result;
    }

    private void checkAnnotationCount(int n) {
        if (n < 0) {
            throw new SchemaTypeLoaderException("Invalid annotation count " + n,
                typeSystem.getName(), _handle, SchemaTypeLoaderException.UNRECOGNIZED_INDEX_ENTRY);
        }
    }

    SchemaComponent.Ref readHandle() {
        String handle = readString();
        if (handle == null) {
            return null;
        }

        if (handle.isEmpty()) {
            throw new SchemaTypeLoaderException("Cannot resolve handle " + handle, typeSystem.getName(), _handle, SchemaTypeLoaderException.BAD_HANDLE);
        }

        if (handle.charAt(0) != '_') {
            return typeSystem.getTypePool().refForHandle(handle);
        }

        // every '_'-prefixed handle is a 4-char tag ("_BI_", "_XT_", ...) plus a
        // payload; charAt(2) and forPretty(handle, 4) below read past a shorter one
        if (handle.length() < 4) {
            throw new SchemaTypeLoaderException("Cannot resolve handle " + handle, typeSystem.getName(), _handle, SchemaTypeLoaderException.BAD_HANDLE);
        }

        switch (handle.charAt(2)) {
            case 'I': // _BI_ - built-in schema type system
                SchemaType st = (SchemaType) BuiltinSchemaTypeSystem.get().resolveHandle(handle);
                if (st != null) {
                    return st.getRef();
                }
                st = (SchemaType) XQuerySchemaTypeSystem.get().resolveHandle(handle);
                return st.getRef();
            case 'T': // _XT_ - external type
                return typeSystem.getLinker().findTypeRef(QNameHelper.forPretty(handle, 4));
            case 'E': // _XE_ - external element
                return typeSystem.getLinker().findElementRef(QNameHelper.forPretty(handle, 4));
            case 'A': // _XA_ - external attribute
                return typeSystem.getLinker().findAttributeRef(QNameHelper.forPretty(handle, 4));
            case 'M': // _XM_ - external model group
                return typeSystem.getLinker().findModelGroupRef(QNameHelper.forPretty(handle, 4));
            case 'N': // _XN_ - external attribute group
                return typeSystem.getLinker().findAttributeGroupRef(QNameHelper.forPretty(handle, 4));
            case 'D': // _XD_ - external identity constraint
                return typeSystem.getLinker().findIdentityConstraintRef(QNameHelper.forPretty(handle, 4));
            case 'R': // _XR_ - external ref to attribute's type
                // deprecated: replaced by _XY_
                SchemaGlobalAttribute attr = typeSystem.getLinker().findAttribute(QNameHelper.forPretty(handle, 4));
                if (attr == null) {
                    throw new SchemaTypeLoaderException("Cannot resolve attribute for handle " + handle, typeSystem.getName(), _handle, SchemaTypeLoaderException.BAD_HANDLE);
                }
                return attr.getType().getRef();
            case 'S': // _XS_ - external ref to element's type
                // deprecated: replaced by _XY_
                SchemaGlobalElement elem = typeSystem.getLinker().findElement(QNameHelper.forPretty(handle, 4));
                if (elem == null) {
                    throw new SchemaTypeLoaderException("Cannot resolve element for handle " + handle, typeSystem.getName(), _handle, SchemaTypeLoaderException.BAD_HANDLE);
                }
                return elem.getType().getRef();
            case 'O': // _XO_ - external ref to document type
                return typeSystem.getLinker().findDocumentTypeRef(QNameHelper.forPretty(handle, 4));
            case 'Y': // _XY_ - external ref to any possible type
                SchemaType type = typeSystem.getLinker().typeForSignature(handle.substring(4));
                if (type == null) {
                    throw new SchemaTypeLoaderException("Cannot resolve type for handle " + handle, typeSystem.getName(), _handle, SchemaTypeLoaderException.BAD_HANDLE);
                }
                return type.getRef();
            default:
                throw new SchemaTypeLoaderException("Cannot resolve handle " + handle, typeSystem.getName(), _handle, SchemaTypeLoaderException.BAD_HANDLE);
        }
    }

    void writeHandle(SchemaComponent comp) {
        if (comp == null || comp.getTypeSystem() == typeSystem) {
            writeString(typeSystem.getTypePool().handleForComponent(comp));
            return;
        }

        switch (comp.getComponentType()) {
            case SchemaComponent.ATTRIBUTE:
                writeString("_XA_" + QNameHelper.pretty(comp.getName()));
                return;
            case SchemaComponent.MODEL_GROUP:
                writeString("_XM_" + QNameHelper.pretty(comp.getName()));
                return;
            case SchemaComponent.ATTRIBUTE_GROUP:
                writeString("_XN_" + QNameHelper.pretty(comp.getName()));
                return;
            case SchemaComponent.ELEMENT:
                writeString("_XE_" + QNameHelper.pretty(comp.getName()));
                return;
            case SchemaComponent.IDENTITY_CONSTRAINT:
                writeString("_XD_" + QNameHelper.pretty(comp.getName()));
                return;
            case SchemaComponent.TYPE:
                SchemaType type = (SchemaType) comp;
                if (type.isBuiltinType()) {
                    writeString("_BI_" + type.getName().getLocalPart());
                    return;
                }

                // fix for CR120759 - added output of types _XR_ & _XS_
                // when an attribute (_XR_) or element (_XS_) declaration
                // uses ref to refer to an attribute or element in another
                // schema and the type of that attribute or element
                // is an anonymous (local) type
                // kkrouse 02/1/2005: _XR_ and _XS_ refs are replaced by _XY_
                if (type.getName() != null) {
                    writeString("_XT_" + QNameHelper.pretty(type.getName()));
                } else if (type.isDocumentType()) {
                    // Substitution groups will create document types that
                    // extend from other document types, possibly in
                    // different jars
                    writeString("_XO_" + QNameHelper.pretty(type.getDocumentElementName()));
                } else {
                    // fix for XMLBEANS-105:
                    // save out the external type reference using the type's signature.
                    writeString("_XY_" + type);
                }

                return;

            default:
                assert (false);
                throw new SchemaTypeLoaderException("Cannot write handle for component " + comp, typeSystem.getName(), _handle, SchemaTypeLoaderException.BAD_HANDLE);
        }
    }

    SchemaType.Ref readTypeRef() {
        return (SchemaType.Ref) readHandle();
    }

    void writeType(SchemaType type) {
        writeHandle(type);
    }

    Map<QName, SchemaComponent.Ref> readQNameRefMap() {
        Map<QName, SchemaComponent.Ref> result = new HashMap<>();
        int size = readShort();
        for (int i = 0; i < size; i++) {
            QName name = readQName();
            SchemaComponent.Ref obj = readHandle();
            result.put(name, obj);
        }
        return result;
    }

    List<SchemaComponent.Ref> readQNameRefMapAsList(List<QName> names) {
        int size = readShort();
        List<SchemaComponent.Ref> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            QName name = readQName();
            SchemaComponent.Ref obj = readHandle();
            result.add(obj);
            names.add(name);
        }
        return result;
    }

    void writeQNameMap(SchemaComponent[] components) {
        writeShort(components.length);
        for (SchemaComponent component : components) {
            writeQName(component.getName());
            writeHandle(component);
        }
    }

    void writeDocumentTypeMap(SchemaType[] doctypes) {
        writeShort(doctypes.length);
        for (SchemaType doctype : doctypes) {
            writeQName(doctype.getDocumentElementName());
            writeHandle(doctype);
        }
    }

    void writeAttributeTypeMap(SchemaType[] attrtypes) {
        writeShort(attrtypes.length);
        for (SchemaType attrtype : attrtypes) {
            writeQName(attrtype.getAttributeTypeAttributeName());
            writeHandle(attrtype);
        }
    }

    SchemaType.Ref[] readTypeRefArray() {
        int size = readShort();
        SchemaType.Ref[] result = new SchemaType.Ref[size];
        for (int i = 0; i < size; i++) {
            result[i] = readTypeRef();
        }
        return result;
    }

    void writeTypeArray(SchemaType[] array) {
        writeShort(array.length);
        for (SchemaType schemaType : array) {
            writeHandle(schemaType);
        }
    }

    Map<String, SchemaComponent.Ref> readClassnameRefMap() {
        Map<String, SchemaComponent.Ref> result = new HashMap<>();
        int size = readShort();
        for (int i = 0; i < size; i++) {
            String name = readString();
            SchemaComponent.Ref obj = readHandle();
            result.put(name, obj);
        }
        return result;
    }

    void writeClassnameMap(Map<String, SchemaComponent.Ref> typesByClass) {
        writeShort(typesByClass.size());
        typesByClass.forEach((className, ref) -> {
            writeString(className);
            writeHandle(((SchemaType.Ref) ref).get());
        });
    }

    Set<String> readNamespaces() {
        Set<String> result = new HashSet<>();
        int size = readShort();
        for (int i = 0; i < size; i++) {
            String ns = readString();
            result.add(ns);
        }
        return result;
    }

    void writeNamespaces(Set<String> namespaces) {
        writeShort(namespaces.size());
        namespaces.forEach(this::writeString);
    }

    void checkContainerNotNull(SchemaContainer container, QName name) {
        if (container == null) {
            throw new LinkageError("Loading of resource " + name + '.' + _handle +
                                   "failed, information from " + name + ".index.xsb is " +
                                   " out of sync (or conflicting index files found)");
        }
    }

    /**
     * Finishes loading an element after the header has already been loaded.
     */
    public SchemaGlobalElement finishLoadingElement() {
        try {
            int particleType = readShort();
            if (particleType != SchemaParticle.ELEMENT) {
                throw new SchemaTypeLoaderException("Wrong particle type ", typeSystem.getName(), _handle, SchemaTypeLoaderException.BAD_PARTICLE_TYPE);
            }
            int particleFlags = readShort();
            BigInteger minOccurs = readBigInteger();
            BigInteger maxOccurs = readBigInteger();
            QNameSet transitionRules = readQNameSet();
            QName name = readQName();
            SchemaContainer container = typeSystem.getContainer(name.getNamespaceURI());
            checkContainerNotNull(container, name);
            SchemaGlobalElementImpl impl = new SchemaGlobalElementImpl(container);
            impl.setParticleType(particleType);
            impl.setMinOccurs(minOccurs);
            impl.setMaxOccurs(maxOccurs);
            impl.setTransitionRules(transitionRules,
                (particleFlags & FLAG_PART_SKIPPABLE) != 0);
            impl.setNameAndTypeRef(name, readTypeRef());
            impl.setDefault(readString(), (particleFlags & FLAG_PART_FIXED) != 0, null);
            if (atLeast(2, 16, 0)) {
                impl.setDefaultValue(readXmlValueObject());
            }
            impl.setNillable((particleFlags & FLAG_PART_NILLABLE) != 0);
            impl.setBlock((particleFlags & FLAG_PART_BLOCKEXT) != 0,
                (particleFlags & FLAG_PART_BLOCKREST) != 0,
                (particleFlags & FLAG_PART_BLOCKSUBST) != 0);
            impl.setWsdlArrayType(readSOAPArrayType());
            impl.setAbstract((particleFlags & FLAG_PART_ABSTRACT) != 0);
            impl.setAnnotation(readAnnotation(container));
            impl.setFinal(
                (particleFlags & FLAG_PART_FINALEXT) != 0,
                (particleFlags & FLAG_PART_FINALREST) != 0);

            if (atLeast(2, 17, 0)) {
                impl.setSubstitutionGroup((SchemaGlobalElement.Ref) readHandle());
            }

            int substGroupCount = readShort();
            for (int i = 0; i < substGroupCount; i++) {
                impl.addSubstitutionGroupMember(readQName());
            }
            SchemaIdentityConstraint.Ref[] idcs = new SchemaIdentityConstraint.Ref[readShort()];

            for (int i = 0; i < idcs.length; i++) {
                idcs[i] = (SchemaIdentityConstraint.Ref) readHandle();
            }

            impl.setIdentityConstraints(idcs);
            impl.setFilename(readString());
            return impl;
        } catch (SchemaTypeLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaTypeLoaderException("Cannot load type from typesystem", typeSystem.getName(), null, SchemaTypeLoaderException.NESTED_EXCEPTION, e);
        } finally {
            readEnd();
        }
    }

    public SchemaGlobalAttribute finishLoadingAttribute() {
        try {
            QName name = readQName();
            SchemaContainer container = typeSystem.getContainer(name.getNamespaceURI());
            checkContainerNotNull(container, name);
            SchemaGlobalAttributeImpl impl = new SchemaGlobalAttributeImpl(container);
            loadAttribute(impl, name, container);
            impl.setFilename(readString());

            return impl;
        } catch (SchemaTypeLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaTypeLoaderException("Cannot load type from typesystem", typeSystem.getName(), _handle, SchemaTypeLoaderException.NESTED_EXCEPTION, e);
        } finally {
            readEnd();
        }
    }

    SchemaModelGroup finishLoadingModelGroup() {
        try {
            QName name = readQName();
            SchemaContainer container = typeSystem.getContainer(name.getNamespaceURI());
            checkContainerNotNull(container, name);
            SchemaModelGroupImpl impl = new SchemaModelGroupImpl(container);
            impl.init(name, readString(), readShort() == 1,
                atLeast(2, 22, 0) ? readString() : null,
                atLeast(2, 22, 0) ? readString() : null,
                atLeast(2, 15, 0) && readShort() == 1,
                GroupDocument.Factory.parse(readString()).getGroup(), readAnnotation(container), null);
            if (atLeast(2, 21, 0)) {
                impl.setFilename(readString());
            }
            return impl;
        } catch (SchemaTypeLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaTypeLoaderException("Cannot load type from typesystem", typeSystem.getName(), _handle, SchemaTypeLoaderException.NESTED_EXCEPTION, e);
        } finally {
            readEnd();
        }
    }

    SchemaIdentityConstraint finishLoadingIdentityConstraint() {
        try {
            QName name = readQName();
            SchemaContainer container = typeSystem.getContainer(name.getNamespaceURI());
            checkContainerNotNull(container, name);
            SchemaIdentityConstraintImpl impl = new SchemaIdentityConstraintImpl(container);
            impl.setName(name);
            impl.setConstraintCategory(readShort());
            impl.setSelector(readString());
            impl.setAnnotation(readAnnotation(container));

            String[] fields = new String[readShort()];
            for (int i = 0; i < fields.length; i++) {
                fields[i] = readString();
            }
            impl.setFields(fields);

            if (impl.getConstraintCategory() == SchemaIdentityConstraint.CC_KEYREF) {
                impl.setReferencedKey((SchemaIdentityConstraint.Ref) readHandle());
            }

            int mapCount = readShort();
            Map<String, String> nsMappings = new HashMap<>();
            for (int i = 0; i < mapCount; i++) {
                String prefix = readString();
                String uri = readString();
                nsMappings.put(prefix, uri);
            }
            impl.setNSMap(nsMappings);

            if (atLeast(2, 21, 0)) {
                impl.setFilename(readString());
            }

            return impl;
        } catch (SchemaTypeLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaTypeLoaderException("Cannot load type from typesystem", typeSystem.getName(), _handle, SchemaTypeLoaderException.NESTED_EXCEPTION, e);
        } finally {
            readEnd();
        }
    }

    SchemaAttributeGroup finishLoadingAttributeGroup() {
        try {
            QName name = readQName();
            SchemaContainer container = typeSystem.getContainer(name.getNamespaceURI());
            checkContainerNotNull(container, name);
            SchemaAttributeGroupImpl impl = new SchemaAttributeGroupImpl(container);
            impl.init(name, readString(), readShort() == 1,
                atLeast(2, 22, 0) ? readString() : null,
                atLeast(2, 15, 0) && readShort() == 1,
                AttributeGroupDocument.Factory.parse(readString()).getAttributeGroup(),
                readAnnotation(container), null);
            if (atLeast(2, 21, 0)) {
                impl.setFilename(readString());
            }
            return impl;
        } catch (SchemaTypeLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaTypeLoaderException("Cannot load type from typesystem", typeSystem.getName(), _handle, SchemaTypeLoaderException.NESTED_EXCEPTION, e);
        } finally {
            readEnd();
        }
    }

    public SchemaType finishLoadingType() {
        try {
            SchemaContainer cNonNull = typeSystem.getContainerNonNull(""); //HACKHACK
            SchemaTypeImpl impl = new SchemaTypeImpl(cNonNull, true);
            impl.setName(readQName());
            impl.setOuterSchemaTypeRef(readTypeRef());
            impl.setBaseDepth(readShort());
            impl.setBaseTypeRef(readTypeRef());
            impl.setDerivationType(readShort());
            impl.setAnnotation(readAnnotation(null));

            switch (readShort()) {
                case FIELD_GLOBAL:
                    impl.setContainerFieldRef(readHandle());
                    break;
                case FIELD_LOCALATTR:
                    impl.setContainerFieldIndex((short) 1, readShort());
                    break;
                case FIELD_LOCALELT:
                    impl.setContainerFieldIndex((short) 2, readShort());
                    break;
            }
            // TODO (radup) find the right solution here
            String jn = readString();
            impl.setFullJavaName(jn == null ? "" : jn);
            jn = readString();
            impl.setFullJavaImplName(jn == null ? "" : jn);

            impl.setAnonymousTypeRefs(readTypeRefArray());

            impl.setAnonymousUnionMemberOrdinal(readShort());

            int flags;
            flags = readInt();


            boolean isComplexType = ((flags & FLAG_SIMPLE_TYPE) == 0);
            impl.setCompiled((flags & FLAG_COMPILED) != 0);
            impl.setDocumentType((flags & FLAG_DOCUMENT_TYPE) != 0);
            impl.setAttributeType((flags & FLAG_ATTRIBUTE_TYPE) != 0);
            impl.setSimpleType(!isComplexType);

            int complexVariety = SchemaType.NOT_COMPLEX_TYPE;
            if (isComplexType) {
                impl.setAbstractFinal((flags & FLAG_ABSTRACT) != 0,
                    (flags & FLAG_FINAL_EXT) != 0,
                    (flags & FLAG_FINAL_REST) != 0,
                    (flags & FLAG_FINAL_LIST) != 0,
                    (flags & FLAG_FINAL_UNION) != 0);
                impl.setBlock((flags & FLAG_BLOCK_EXT) != 0,
                    (flags & FLAG_BLOCK_REST) != 0);

                impl.setOrderSensitive((flags & FLAG_ORDER_SENSITIVE) != 0);
                complexVariety = readShort();
                impl.setComplexTypeVariety(complexVariety);

                if (atLeast(2, 23, 0)) {
                    impl.setContentBasedOnTypeRef(readTypeRef());
                }

                // Attribute Model Table
                SchemaAttributeModelImpl attrModel = new SchemaAttributeModelImpl();

                int attrCount = readShort();
                for (int i = 0; i < attrCount; i++) {
                    attrModel.addAttribute(readAttributeData());
                }

                attrModel.setWildcardSet(readQNameSet());
                attrModel.setWildcardProcess(readShort());

                // Attribute Property Table
                Map<QName, SchemaProperty> attrProperties = new LinkedHashMap<>();
                int attrPropCount = readShort();
                for (int i = 0; i < attrPropCount; i++) {
                    SchemaProperty prop = readPropertyData();
                    if (!prop.isAttribute()) {
                        throw new SchemaTypeLoaderException("Attribute property " + i + " is not an attribute", typeSystem.getName(), _handle, SchemaTypeLoaderException.WRONG_PROPERTY_TYPE);
                    }
                    attrProperties.put(prop.getName(), prop);
                }

                SchemaParticle contentModel = null;
                Map<QName, SchemaProperty> elemProperties = null;
                int isAll = 0;

                if (complexVariety == SchemaType.ELEMENT_CONTENT || complexVariety == SchemaType.MIXED_CONTENT) {
                    // Content Model Tree
                    isAll = readShort();
                    SchemaParticle[] parts = readParticleArray();
                    if (parts.length == 1) {
                        contentModel = parts[0];
                    } else if (parts.length == 0) {
                        contentModel = null;
                    } else {
                        throw new SchemaTypeLoaderException("Content model not well-formed", typeSystem.getName(), _handle, SchemaTypeLoaderException.MALFORMED_CONTENT_MODEL);
                    }

                    // Element Property Table

                    elemProperties = new LinkedHashMap<>();
                    int elemPropCount = readShort();
                    for (int i = 0; i < elemPropCount; i++) {
                        SchemaProperty prop = readPropertyData();
                        if (prop.isAttribute()) {
                            throw new SchemaTypeLoaderException("Element property " + i + " is not an element", typeSystem.getName(), _handle, SchemaTypeLoaderException.WRONG_PROPERTY_TYPE);
                        }
                        elemProperties.put(prop.getName(), prop);
                    }
                }

                impl.setContentModel(contentModel, attrModel, elemProperties, attrProperties, isAll == 1);
                StscComplexTypeResolver.WildcardResult wcElt = StscComplexTypeResolver.summarizeEltWildcards(contentModel);
                StscComplexTypeResolver.WildcardResult wcAttr = StscComplexTypeResolver.summarizeAttrWildcards(attrModel);
                impl.setWildcardSummary(wcElt.typedWildcards, wcElt.hasWildcards, wcAttr.typedWildcards, wcAttr.hasWildcards);
            }

            if (!isComplexType || complexVariety == SchemaType.SIMPLE_CONTENT) {
                int simpleVariety = readShort();
                impl.setSimpleTypeVariety(simpleVariety);

                boolean isStringEnum = ((flags & FLAG_STRINGENUM) != 0);

                impl.setOrdered((flags & FLAG_ORDERED) != 0 ? SchemaType.UNORDERED : ((flags & FLAG_TOTAL_ORDER) != 0 ? SchemaType.TOTAL_ORDER : SchemaType.PARTIAL_ORDER));
                impl.setBounded((flags & FLAG_BOUNDED) != 0);
                impl.setFinite((flags & FLAG_FINITE) != 0);
                impl.setNumeric((flags & FLAG_NUMERIC) != 0);
                impl.setUnionOfLists((flags & FLAG_UNION_OF_LISTS) != 0);
                impl.setSimpleFinal((flags & FLAG_FINAL_REST) != 0,
                    (flags & FLAG_FINAL_LIST) != 0,
                    (flags & FLAG_FINAL_UNION) != 0);

                XmlValueRef[] facets = new XmlValueRef[SchemaType.LAST_FACET + 1];
                boolean[] fixedFacets = new boolean[SchemaType.LAST_FACET + 1];
                int facetCount = readShort();
                for (int i = 0; i < facetCount; i++) {
                    int facetCode = readShort();
                    facets[facetCode] = readXmlValueObject();
                    fixedFacets[facetCode] = (readShort() == 1);
                }
                impl.setBasicFacets(facets, fixedFacets);

                impl.setWhiteSpaceRule(readShort());

                impl.setPatternFacet((flags & FLAG_HAS_PATTERN) != 0);

                int patternCount = readShort();
                org.apache.xmlbeans.impl.regex.RegularExpression[] patterns = new org.apache.xmlbeans.impl.regex.RegularExpression[patternCount];
                for (int i = 0; i < patternCount; i++) {
                    patterns[i] = new org.apache.xmlbeans.impl.regex.RegularExpression(readString(), "X");
                }
                impl.setPatterns(patterns);

                int enumCount = readShort();
                XmlValueRef[] enumValues = new XmlValueRef[enumCount];
                for (int i = 0; i < enumCount; i++) {
                    enumValues[i] = readXmlValueObject();
                }
                impl.setEnumerationValues(enumCount == 0 ? null : enumValues);

                impl.setBaseEnumTypeRef(readTypeRef());
                if (isStringEnum) {
                    int seCount = readUnsignedShortOrInt();
                    SchemaStringEnumEntry[] entries = new SchemaStringEnumEntry[seCount];
                    for (int i = 0; i < seCount; i++) {
                        entries[i] = new SchemaStringEnumEntryImpl(readString(), readShort(), readString());
                    }
                    impl.setStringEnumEntries(entries);
                }

                switch (simpleVariety) {
                    case SchemaType.ATOMIC:
                        impl.setPrimitiveTypeRef(readTypeRef());
                        impl.setDecimalSize(readInt());
                        break;

                    case SchemaType.LIST:
                        impl.setPrimitiveTypeRef(BuiltinSchemaTypeSystem.ST_ANY_SIMPLE.getRef());
                        impl.setListItemTypeRef(readTypeRef());
                        break;

                    case SchemaType.UNION:
                        impl.setPrimitiveTypeRef(BuiltinSchemaTypeSystem.ST_ANY_SIMPLE.getRef());
                        impl.setUnionMemberTypeRefs(readTypeRefArray());
                        break;

                    default:
                        throw new SchemaTypeLoaderException("Simple type does not have a recognized variety", typeSystem.getName(), _handle, SchemaTypeLoaderException.WRONG_SIMPLE_VARIETY);
                }
            }

            impl.setFilename(readString());
            // Set the container for global, attribute or document types
            if (impl.getName() != null) {
                SchemaContainer container = typeSystem.getContainer(impl.getName().getNamespaceURI());
                checkContainerNotNull(container, impl.getName());
                impl.setContainer(container);
            } else if (impl.isDocumentType()) {
                QName name = impl.getDocumentElementName();
                if (name != null) {
                    SchemaContainer container = typeSystem.getContainer(name.getNamespaceURI());
                    checkContainerNotNull(container, name);
                    impl.setContainer(container);
                }
            } else if (impl.isAttributeType()) {
                QName name = impl.getAttributeTypeAttributeName();
                if (name != null) {
                    SchemaContainer container = typeSystem.getContainer(name.getNamespaceURI());
                    checkContainerNotNull(container, name);
                    impl.setContainer(container);
                }
            }

            return impl;
        } catch (SchemaTypeLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaTypeLoaderException("Cannot load type from typesystem", typeSystem.getName(), _handle, SchemaTypeLoaderException.NESTED_EXCEPTION, e);
        } finally {
            readEnd();
        }
    }

    void writeTypeData(SchemaType type) {
        writeQName(type.getName());
        writeType(type.getOuterType());
        writeShort(((SchemaTypeImpl) type).getBaseDepth());
        writeType(type.getBaseType());
        writeShort(type.getDerivationType());
        writeAnnotation(type.getAnnotation());
        if (type.getContainerField() == null) {
            writeShort(FIELD_NONE);
        } else if (type.getOuterType().isAttributeType() || type.getOuterType().isDocumentType()) {
            writeShort(FIELD_GLOBAL);
            writeHandle((SchemaComponent) type.getContainerField());
        } else if (type.getContainerField().isAttribute()) {
            writeShort(FIELD_LOCALATTR);
            writeShort(((SchemaTypeImpl) type.getOuterType()).getIndexForLocalAttribute((SchemaLocalAttribute) type.getContainerField()));
        } else {
            writeShort(FIELD_LOCALELT);
            writeShort(((SchemaTypeImpl) type.getOuterType()).getIndexForLocalElement((SchemaLocalElement) type.getContainerField()));
        }
        writeString(type.getFullJavaName());
        writeString(type.getFullJavaImplName());
        writeTypeArray(type.getAnonymousTypes());
        writeShort(type.getAnonymousUnionMemberOrdinal());

        int flags = 0;
        if (type.isSimpleType()) {
            flags |= FLAG_SIMPLE_TYPE;
        }
        if (type.isDocumentType()) {
            flags |= FLAG_DOCUMENT_TYPE;
        }
        if (type.isAttributeType()) {
            flags |= FLAG_ATTRIBUTE_TYPE;
        }
        if (type.ordered() != SchemaType.UNORDERED) {
            flags |= FLAG_ORDERED;
        }
        if (type.ordered() == SchemaType.TOTAL_ORDER) {
            flags |= FLAG_TOTAL_ORDER;
        }
        if (type.isBounded()) {
            flags |= FLAG_BOUNDED;
        }
        if (type.isFinite()) {
            flags |= FLAG_FINITE;
        }
        if (type.isNumeric()) {
            flags |= FLAG_NUMERIC;
        }
        if (type.hasStringEnumValues()) {
            flags |= FLAG_STRINGENUM;
        }
        if (((SchemaTypeImpl) type).isUnionOfLists()) {
            flags |= FLAG_UNION_OF_LISTS;
        }
        if (type.hasPatternFacet()) {
            flags |= FLAG_HAS_PATTERN;
        }
        if (type.isOrderSensitive()) {
            flags |= FLAG_ORDER_SENSITIVE;
        }

        if (type.blockExtension()) {
            flags |= FLAG_BLOCK_EXT;
        }
        if (type.blockRestriction()) {
            flags |= FLAG_BLOCK_REST;
        }
        if (type.finalExtension()) {
            flags |= FLAG_FINAL_EXT;
        }
        if (type.finalRestriction()) {
            flags |= FLAG_FINAL_EXT;
        }
        if (type.finalList()) {
            flags |= FLAG_FINAL_LIST;
        }
        if (type.finalUnion()) {
            flags |= FLAG_FINAL_UNION;
        }
        if (type.isAbstract()) {
            flags |= FLAG_ABSTRACT;
        }

        writeInt(flags);

        if (!type.isSimpleType()) {
            writeShort(type.getContentType());

            writeType(type.getContentBasedOnType());

            // Attribute Model Table
            SchemaAttributeModel attrModel = type.getAttributeModel();
            SchemaLocalAttribute[] attrs = attrModel.getAttributes();

            writeShort(attrs.length);
            for (SchemaLocalAttribute attr : attrs) {
                writeAttributeData(attr);
            }

            writeQNameSet(attrModel.getWildcardSet());
            writeShort(attrModel.getWildcardProcess());

            // Attribute Property Table
            SchemaProperty[] attrProperties = type.getAttributeProperties();
            writeShort(attrProperties.length);
            for (SchemaProperty attrProperty : attrProperties) {
                writePropertyData(attrProperty);
            }

            if (type.getContentType() == SchemaType.ELEMENT_CONTENT ||
                type.getContentType() == SchemaType.MIXED_CONTENT) {
                // Content Model Tree
                writeShort(type.hasAllContent() ? 1 : 0);
                SchemaParticle[] parts;
                if (type.getContentModel() != null) {
                    parts = new SchemaParticle[]{type.getContentModel()};
                } else {
                    parts = new SchemaParticle[0];
                }

                writeParticleArray(parts);

                // Element Property Table
                SchemaProperty[] eltProperties = type.getElementProperties();
                writeShort(eltProperties.length);
                for (SchemaProperty eltProperty : eltProperties) {
                    writePropertyData(eltProperty);
                }
            }
        }

        if (type.isSimpleType() || type.getContentType() == SchemaType.SIMPLE_CONTENT) {
            writeShort(type.getSimpleVariety());

            int facetCount = 0;
            for (int i = 0; i <= SchemaType.LAST_FACET; i++) {
                if (type.getFacet(i) != null) {
                    facetCount++;
                }
            }
            writeShort(facetCount);
            for (int i = 0; i <= SchemaType.LAST_FACET; i++) {
                XmlAnySimpleType facet = type.getFacet(i);
                if (facet != null) {
                    writeShort(i);
                    writeXmlValueObject(facet);
                    writeShort(type.isFacetFixed(i) ? 1 : 0);
                }
            }

            writeShort(type.getWhiteSpaceRule());

            org.apache.xmlbeans.impl.regex.RegularExpression[] patterns = ((SchemaTypeImpl) type).getPatternExpressions();
            writeShort(patterns.length);
            for (org.apache.xmlbeans.impl.regex.RegularExpression pattern : patterns) {
                writeString(pattern.getPattern());
            }

            XmlAnySimpleType[] enumValues = type.getEnumerationValues();
            if (enumValues == null) {
                writeShort(0);
            } else {
                writeShortOrInt(enumValues.length);
                for (XmlAnySimpleType enumValue : enumValues) {
                    writeXmlValueObject(enumValue);
                }
            }

            // new for version 2.3
            writeType(type.getBaseEnumType());
            if (type.hasStringEnumValues()) {
                SchemaStringEnumEntry[] entries = type.getStringEnumEntries();
                writeShort(entries.length);
                for (SchemaStringEnumEntry entry : entries) {
                    writeString(entry.getString());
                    writeShort(entry.getIntValue());
                    writeString(entry.getEnumName());
                }
            }

            switch (type.getSimpleVariety()) {
                case SchemaType.ATOMIC:
                    writeType(type.getPrimitiveType());
                    writeInt(type.getDecimalSize());
                    break;

                case SchemaType.LIST:
                    writeType(type.getListItemType());
                    break;

                case SchemaType.UNION:
                    writeTypeArray(type.getUnionMemberTypes());
                    break;
            }
        }

        writeString(type.getSourceName());
    }

        /*
        void readExtensionsList() {
            int count = readShort();
            assert count == 0;

            for (int i = 0; i < count; i++) {
                readString();
                readString();
                readString();
            }
        }
         */

    SchemaLocalAttribute readAttributeData() {
        SchemaLocalAttributeImpl result = new SchemaLocalAttributeImpl();
        loadAttribute(result, readQName(), null);
        return result;
    }


    void loadAttribute(SchemaLocalAttributeImpl result, QName name, SchemaContainer container) {
        // name, type, use, deftext, defval, fixed, soaparraytype, annotation
        result.init(name, readTypeRef(), readShort(), readString(), null, atLeast(2, 16, 0) ? readXmlValueObject() : null, readShort() == 1, readSOAPArrayType(), readAnnotation(container), null);
    }

    void writeAttributeData(SchemaLocalAttribute attr) {
        writeQName(attr.getName());
        writeType(attr.getType());
        writeShort(attr.getUse());
        writeString(attr.getDefaultText());
        writeXmlValueObject(attr.getDefaultValue());
        writeShort(attr.isFixed() ? 1 : 0);
        writeSOAPArrayType(((SchemaWSDLArrayType) attr).getWSDLArrayType());
        writeAnnotation(attr.getAnnotation());
    }

    void writeIdConstraintData(SchemaIdentityConstraint idc) {
        writeQName(idc.getName());
        writeShort(idc.getConstraintCategory());
        writeString(idc.getSelector());
        writeAnnotation(idc.getAnnotation());

        String[] fields = idc.getFields();
        writeShort(fields.length);
        for (String field : fields) {
            writeString(field);
        }


        if (idc.getConstraintCategory() == SchemaIdentityConstraint.CC_KEYREF) {
            writeHandle(idc.getReferencedKey());
        }

        Map<String, String> mappings = idc.getNSMap();
        writeShort(mappings.size());
        mappings.forEach((prefix, uri) -> {
            writeString(prefix);
            writeString(uri);
        });
        writeString(idc.getSourceName());
    }

    SchemaParticle[] readParticleArray() {
        SchemaParticle[] result = new SchemaParticle[readShort()];
        for (int i = 0; i < result.length; i++) {
            result[i] = readParticleData();
        }
        return result;
    }

    void writeParticleArray(SchemaParticle[] spa) {
        writeShort(spa.length);
        for (SchemaParticle schemaParticle : spa) {
            writeParticleData(schemaParticle);
        }
    }

    SchemaParticle readParticleData() {
        int particleType = readShort();
        SchemaParticleImpl result;
        if (particleType != SchemaParticle.ELEMENT) {
            result = new SchemaParticleImpl();
        } else {
            result = new SchemaLocalElementImpl();
        }
        loadParticle(result, particleType);
        return result;
    }

    void loadParticle(SchemaParticleImpl result, int particleType) {
        int particleFlags = readShort();

        result.setParticleType(particleType);
        result.setMinOccurs(readBigInteger());
        result.setMaxOccurs(readBigInteger());

        result.setTransitionRules(readQNameSet(),
            (particleFlags & FLAG_PART_SKIPPABLE) != 0);

        switch (particleType) {
            case SchemaParticle.WILDCARD:
                result.setWildcardSet(readQNameSet());
                result.setWildcardProcess(readShort());
                break;

            case SchemaParticle.ELEMENT:
                SchemaLocalElementImpl lresult = (SchemaLocalElementImpl) result;
                lresult.setNameAndTypeRef(readQName(), readTypeRef());
                lresult.setDefault(readString(), (particleFlags & FLAG_PART_FIXED) != 0, null);
                if (atLeast(2, 16, 0)) {
                    lresult.setDefaultValue(readXmlValueObject());
                }
                lresult.setNillable((particleFlags & FLAG_PART_NILLABLE) != 0);
                lresult.setBlock((particleFlags & FLAG_PART_BLOCKEXT) != 0,
                    (particleFlags & FLAG_PART_BLOCKREST) != 0,
                    (particleFlags & FLAG_PART_BLOCKSUBST) != 0);
                lresult.setWsdlArrayType(readSOAPArrayType());
                lresult.setAbstract((particleFlags & FLAG_PART_ABSTRACT) != 0);
                lresult.setAnnotation(readAnnotation(null));

                SchemaIdentityConstraint.Ref[] idcs = new SchemaIdentityConstraint.Ref[readShort()];

                for (int i = 0; i < idcs.length; i++) {
                    idcs[i] = (SchemaIdentityConstraint.Ref) readHandle();
                }

                lresult.setIdentityConstraints(idcs);

                break;

            case SchemaParticle.ALL:
            case SchemaParticle.SEQUENCE:
            case SchemaParticle.CHOICE:
                result.setParticleChildren(readParticleArray());
                break;

            default:
                throw new SchemaTypeLoaderException("Unrecognized particle type ", typeSystem.getName(), _handle, SchemaTypeLoaderException.BAD_PARTICLE_TYPE);
        }
    }

    void writeParticleData(SchemaParticle part) {
        writeShort(part.getParticleType());
        short flags = 0;
        if (part.isSkippable()) {
            flags |= FLAG_PART_SKIPPABLE;
        }
        if (part.getParticleType() == SchemaParticle.ELEMENT) {
            SchemaLocalElement lpart = (SchemaLocalElement) part;
            if (lpart.isFixed()) {
                flags |= FLAG_PART_FIXED;
            }
            if (lpart.isNillable()) {
                flags |= FLAG_PART_NILLABLE;
            }
            if (lpart.blockExtension()) {
                flags |= FLAG_PART_BLOCKEXT;
            }
            if (lpart.blockRestriction()) {
                flags |= FLAG_PART_BLOCKREST;
            }
            if (lpart.blockSubstitution()) {
                flags |= FLAG_PART_BLOCKSUBST;
            }
            if (lpart.isAbstract()) {
                flags |= FLAG_PART_ABSTRACT;
            }

            if (lpart instanceof SchemaGlobalElement gpart) {
                if (gpart.finalExtension()) {
                    flags |= FLAG_PART_FINALEXT;
                }
                if (gpart.finalRestriction()) {
                    flags |= FLAG_PART_FINALREST;
                }
            }
        }
        writeShort(flags);
        writeBigInteger(part.getMinOccurs());
        writeBigInteger(part.getMaxOccurs());
        writeQNameSet(part.acceptedStartNames());

        switch (part.getParticleType()) {
            case SchemaParticle.WILDCARD:
                writeQNameSet(part.getWildcardSet());
                writeShort(part.getWildcardProcess());
                break;

            case SchemaParticle.ELEMENT:
                SchemaLocalElement lpart = (SchemaLocalElement) part;
                writeQName(lpart.getName());
                writeType(lpart.getType());
                writeString(lpart.getDefaultText());
                writeXmlValueObject(lpart.getDefaultValue());
                writeSOAPArrayType(((SchemaWSDLArrayType) lpart).getWSDLArrayType());
                writeAnnotation(lpart.getAnnotation());
                if (lpart instanceof SchemaGlobalElement gpart) {
                    writeHandle(gpart.substitutionGroup());

                    QName[] substGroupMembers = gpart.substitutionGroupMembers();
                    writeShort(substGroupMembers.length);
                    for (QName substGroupMember : substGroupMembers) {
                        writeQName(substGroupMember);
                    }
                }

                SchemaIdentityConstraint[] idcs = lpart.getIdentityConstraints();

                writeShort(idcs.length);
                for (SchemaIdentityConstraint idc : idcs) {
                    writeHandle(idc);
                }

                break;

            case SchemaParticle.ALL:
            case SchemaParticle.SEQUENCE:
            case SchemaParticle.CHOICE:
                writeParticleArray(part.getParticleChildren());
                break;

            default:
                throw new SchemaTypeLoaderException("Unrecognized particle type ", typeSystem.getName(), _handle, SchemaTypeLoaderException.BAD_PARTICLE_TYPE);
        }
    }

    SchemaProperty readPropertyData() {
        SchemaPropertyImpl prop = new SchemaPropertyImpl();
        prop.setName(readQName());
        prop.setTypeRef(readTypeRef());
        int propflags = readShort();
        prop.setAttribute((propflags & FLAG_PROP_ISATTR) != 0);
        prop.setContainerTypeRef(readTypeRef());
        prop.setMinOccurs(readBigInteger());
        prop.setMaxOccurs(readBigInteger());
        prop.setNillable(readShort());
        prop.setDefault(readShort());
        prop.setFixed(readShort());
        prop.setDefaultText(readString());

        prop.setJavaPropertyName(readString());
        prop.setJavaTypeCode(readShort());
        prop.setExtendsJava(readTypeRef(),
            (propflags & FLAG_PROP_JAVASINGLETON) != 0,
            (propflags & FLAG_PROP_JAVAOPTIONAL) != 0,
            (propflags & FLAG_PROP_JAVAARRAY) != 0);
        if (atMost(2, 19, 0)) {
            prop.setJavaSetterDelimiter(readQNameSet());
        }
        if (atLeast(2, 16, 0)) {
            prop.setDefaultValue(readXmlValueObject());
        }

        if (!prop.isAttribute() && atLeast(2, 17, 0)) {
            int size = readShort();
            Set<QName> qnames = new LinkedHashSet<>(size);
            for (int i = 0; i < size; i++) {
                qnames.add(readQName());
            }
            prop.setAcceptedNames(qnames);
        }
        prop.setImmutable();
        return prop;
    }

    void writePropertyData(SchemaProperty prop) {
        writeQName(prop.getName());
        writeType(prop.getType());
        writeShort((prop.isAttribute() ? FLAG_PROP_ISATTR : 0) |
                   (prop.extendsJavaSingleton() ? FLAG_PROP_JAVASINGLETON : 0) |
                   (prop.extendsJavaOption() ? FLAG_PROP_JAVAOPTIONAL : 0) |
                   (prop.extendsJavaArray() ? FLAG_PROP_JAVAARRAY : 0));
        writeType(prop.getContainerType());
        writeBigInteger(prop.getMinOccurs());
        writeBigInteger(prop.getMaxOccurs());
        writeShort(prop.hasNillable());
        writeShort(prop.hasDefault());
        writeShort(prop.hasFixed());
        writeString(prop.getDefaultText());

        writeString(prop.getJavaPropertyName());
        writeShort(prop.getJavaTypeCode());
        writeType(prop.javaBasedOnType());
        writeXmlValueObject(prop.getDefaultValue());

        if (!prop.isAttribute()) {
            QName[] names = prop.acceptedNames();
            writeShort(names.length);
            for (QName name : names) {
                writeQName(name);
            }
        }
    }

    void writeModelGroupData(SchemaModelGroup grp) {
        SchemaModelGroupImpl impl = (SchemaModelGroupImpl) grp;
        writeQName(impl.getName());
        writeString(impl.getTargetNamespace());
        writeShort(impl.getChameleonNamespace() != null ? 1 : 0);
        writeString(impl.getElemFormDefault()); // new for version 2.22
        writeString(impl.getAttFormDefault()); // new for version 2.22
        writeShort(impl.isRedefinition() ? 1 : 0); // new for version 2.15
        writeString(impl.getParseObject().xmlText(new XmlOptions().setSaveOuter()));
        writeAnnotation(impl.getAnnotation());
        writeString(impl.getSourceName());
    }

    void writeAttributeGroupData(SchemaAttributeGroup grp) {
        SchemaAttributeGroupImpl impl = (SchemaAttributeGroupImpl) grp;
        writeQName(impl.getName());
        writeString(impl.getTargetNamespace());
        writeShort(impl.getChameleonNamespace() != null ? 1 : 0);
        writeString(impl.getFormDefault()); // new for version 2.22
        writeShort(impl.isRedefinition() ? 1 : 0); // new for version 2.15
        writeString(impl.getParseObject().xmlText(new XmlOptions().setSaveOuter()));
        writeAnnotation(impl.getAnnotation());
        writeString(impl.getSourceName());
    }

    XmlValueRef readXmlValueObject() {
        SchemaType.Ref typeref = readTypeRef();
        if (typeref == null) {
            return null;
        }
        int btc = readShort();
        switch (btc) {
            default:
                assert (false);
            case 0:
                return new XmlValueRef(typeref, null);
            case 0xFFFF: {
                int size = readShort();
                List<XmlValueRef> values = new ArrayList<>();
                // BUGBUG: this was: writeShort(values.size());
                writeShort(size);
                for (int i = 0; i < size; i++) {
                    values.add(readXmlValueObject());
                }
                return new XmlValueRef(typeref, values);
            }


            case SchemaType.BTC_ANY_SIMPLE:
            case SchemaType.BTC_ANY_URI:
            case SchemaType.BTC_STRING:
            case SchemaType.BTC_DURATION:
            case SchemaType.BTC_DATE_TIME:
            case SchemaType.BTC_TIME:
            case SchemaType.BTC_DATE:
            case SchemaType.BTC_G_YEAR_MONTH:
            case SchemaType.BTC_G_YEAR:
            case SchemaType.BTC_G_MONTH_DAY:
            case SchemaType.BTC_G_DAY:
            case SchemaType.BTC_G_MONTH:
            case SchemaType.BTC_DECIMAL:
            case SchemaType.BTC_BOOLEAN:
                return new XmlValueRef(typeref, readString());

            case SchemaType.BTC_BASE_64_BINARY:
            case SchemaType.BTC_HEX_BINARY:
                return new XmlValueRef(typeref, readByteArray());

            case SchemaType.BTC_QNAME:
            case SchemaType.BTC_NOTATION:
                return new XmlValueRef(typeref, readQName());

            case SchemaType.BTC_FLOAT:
            case SchemaType.BTC_DOUBLE:
                return new XmlValueRef(typeref, readDouble());
        }
    }

    void writeXmlValueObject(XmlAnySimpleType value) {
        SchemaType type = value == null ? null : value.schemaType();
        writeType(type);
        if (type == null) {
            return;
        }

        SchemaType iType = ((SimpleValue) value).instanceType();
        if (iType == null) {
            writeShort(0);
        } else if (iType.getSimpleVariety() == SchemaType.LIST) {
            writeShort(-1);
            List<? extends XmlAnySimpleType> values = ((XmlObjectBase) value).xgetListValue();
            writeShort(values.size());
            values.forEach(this::writeXmlValueObject);
        } else {
            int btc = iType.getPrimitiveType().getBuiltinTypeCode();
            writeShort(btc);
            switch (btc) {
                case SchemaType.BTC_ANY_SIMPLE:
                case SchemaType.BTC_ANY_URI:
                case SchemaType.BTC_STRING:
                case SchemaType.BTC_DURATION:
                case SchemaType.BTC_DATE_TIME:
                case SchemaType.BTC_TIME:
                case SchemaType.BTC_DATE:
                case SchemaType.BTC_G_YEAR_MONTH:
                case SchemaType.BTC_G_YEAR:
                case SchemaType.BTC_G_MONTH_DAY:
                case SchemaType.BTC_G_DAY:
                case SchemaType.BTC_G_MONTH:
                case SchemaType.BTC_DECIMAL:
                case SchemaType.BTC_BOOLEAN:
                    writeString(value.getStringValue());
                    break;

                case SchemaType.BTC_BASE_64_BINARY:
                case SchemaType.BTC_HEX_BINARY:
                    writeByteArray(((SimpleValue) value).getByteArrayValue());
                    break;

                case SchemaType.BTC_QNAME:
                case SchemaType.BTC_NOTATION:
                    writeQName(((SimpleValue) value).getQNameValue());
                    break;

                case SchemaType.BTC_FLOAT:
                    writeDouble(((SimpleValue) value).getFloatValue());
                    break;

                case SchemaType.BTC_DOUBLE:
                    writeDouble(((SimpleValue) value).getDoubleValue());
                    break;
            }
        }
    }

    double readDouble() {
        try {
            return _input.readDouble();
        } catch (IOException e) {
            throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
        }
    }

    void writeDouble(double d) {
        if (_output != null) {
            try {
                _output.writeDouble(d);
            } catch (IOException e) {
                throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
            }
        }
    }

    QNameSet readQNameSet() {
        int flag = readShort();

        Set<String> uriSet = new HashSet<>();
        int uriCount = readShort();
        for (int i = 0; i < uriCount; i++) {
            uriSet.add(readString());
        }

        Set<QName> qnameSet1 = new HashSet<>();
        int qncount1 = readShort();
        for (int i = 0; i < qncount1; i++) {
            qnameSet1.add(readQName());
        }

        Set<QName> qnameSet2 = new HashSet<>();
        int qncount2 = readShort();
        for (int i = 0; i < qncount2; i++) {
            qnameSet2.add(readQName());
        }

        if (flag == 1) {
            return QNameSet.forSets(uriSet, null, qnameSet1, qnameSet2);
        } else {
            return QNameSet.forSets(null, uriSet, qnameSet2, qnameSet1);
        }
    }

    void writeQNameSet(QNameSet set) {
        boolean invert = (set.excludedURIs() != null);
        writeShort(invert ? 1 : 0);

        Set<String> uriSet = invert ? set.excludedURIs() : set.includedURIs();
        assert (uriSet != null);
        writeShort(uriSet.size());
        uriSet.forEach(this::writeString);

        Set<QName> qnameSet1 = invert ? set.excludedQNamesInIncludedURIs() : set.includedQNamesInExcludedURIs();
        writeShort(qnameSet1.size());
        qnameSet1.forEach(this::writeQName);

        Set<QName> qnameSet2 = invert ? set.includedQNamesInExcludedURIs() : set.excludedQNamesInIncludedURIs();
        writeShort(qnameSet2.size());
        qnameSet2.forEach(this::writeQName);
    }

    byte[] readByteArray() {
        try {
            int len = _input.readUnsignedShort();
            byte[] result = new byte[len];
            _input.readFully(result);
            return result;
        } catch (IOException e) {
            throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
        }
    }

    void writeByteArray(byte[] ba) {
        try {
            writeShort(ba.length);
            if (_output != null) {
                _output.write(ba);
            }
        } catch (IOException e) {
            throw new SchemaTypeLoaderException(e.getMessage(), typeSystem.getName(), _handle, SchemaTypeLoaderException.IO_EXCEPTION, e);
        }
    }

    BigInteger readBigInteger() {
        byte[] result = readByteArray();
        if (result.length == 0) {
            return null;
        }
        if (result.length == 1 && result[0] == 0) {
            return BigInteger.ZERO;
        }
        if (result.length == 1 && result[0] == 1) {
            return BigInteger.ONE;
        }
        return new BigInteger(result);
    }

    void writeBigInteger(BigInteger bi) {
        if (bi == null) {
            writeShort(0);
        } else if (bi.signum() == 0) {
            writeByteArray(SINGLE_ZERO_BYTE);
        } else {
            writeByteArray(bi.toByteArray());
        }
    }

}

