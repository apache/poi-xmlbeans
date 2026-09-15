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
import org.apache.xmlbeans.XmlOptions.BeanMethod;
import org.apache.xmlbeans.impl.common.NameUtil;
import org.apache.xmlbeans.impl.repackage.Repackager;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.*;

/**
 * Prints the java code for a single schema type
 */
public final class SchemaTypeCodePrinter implements SchemaCodePrinter {

    static final String INDEX_CLASSNAME = "TypeSystemHolder";
    private static final String MAX_SPACES = "                                        ";
    private static final int INDENT_INCREMENT = 4;

    private Writer _writer;
    private int _indent;
    private XmlOptions opt;

    public SchemaTypeCodePrinter() {
        _indent = 0;
    }

    void indent() {
        _indent += INDENT_INCREMENT;
    }

    void outdent() {
        _indent -= INDENT_INCREMENT;
    }

    void emit(String s, BeanMethod method) throws IOException {
        Set<BeanMethod> partMet = opt == null ? null : opt.getCompilePartialMethod();
        if ((partMet == null || partMet.contains(method))) {
            emit(s);
        }
    }

    void emit(String s) throws IOException {
        if (!s.trim().isEmpty()) {
            int indent = _indent;

            if (indent > MAX_SPACES.length() / 2) {
                indent = MAX_SPACES.length() / 4 + indent / 2;
            }

            if (indent > MAX_SPACES.length()) {
                indent = MAX_SPACES.length();
            }

            _writer.write(MAX_SPACES.substring(0, indent));
        }
        try {
            _writer.write(s);
        } catch (CharacterCodingException cce) {
            _writer.write(makeSafe(s));
        }
        _writer.write(System.lineSeparator());
    }

    private static String makeSafe(String s) {
        final Charset charset = Charset.defaultCharset();
        CharsetEncoder cEncoder = charset.newEncoder();
        StringBuilder result = new StringBuilder();
        int i;
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!cEncoder.canEncode(c)) {
                break;
            }
        }
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (cEncoder.canEncode(c)) {
                result.append(c);
            } else {
                String hexValue = Integer.toHexString(c);
                switch (hexValue.length()) {
                    case 1:
                        result.append("\\u000").append(hexValue);
                        break;
                    case 2:
                        result.append("\\u00").append(hexValue);
                        break;
                    case 3:
                        result.append("\\u0").append(hexValue);
                        break;
                    case 4:
                        result.append("\\u").append(hexValue);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        }
        return result.toString();
    }

    @Override
    public void printType(Writer writer, SchemaType sType, XmlOptions opt) throws IOException {
        this.opt = opt;
        _writer = writer;
        printTopComment(sType);
        printPackage(sType, true);
        emit("");
        emit("import "+ElementFactory.class.getName()+";");
        emit("import " + AbstractDocumentFactory.class.getName() + ";");
        emit("import " + DocumentFactory.class.getName() + ";");
        emit("import " + SimpleTypeFactory.class.getName() + ";");
        emit("");
        printInnerType(sType, sType.getTypeSystem());
        _writer.flush();
    }

    @Override
    public void printTypeImpl(Writer writer, SchemaType sType, XmlOptions opt) throws IOException {
        this.opt = opt;
        _writer = writer;
        printTopComment(sType);
        printPackage(sType, false);
        emit("");
        emit("import javax.xml.namespace.QName;");
        emit("import org.apache.xmlbeans.QNameSet;");
        emit("import org.apache.xmlbeans.XmlObject;");
        emit("");
        printInnerTypeImpl(sType, sType.getTypeSystem(), false);
    }

    /**
     * Since not all schema types have java types, this skips
     * over any that don't and gives you the nearest java base type.
     */
    String findJavaType(SchemaType sType) {
        while (sType.getFullJavaName() == null) {
            sType = sType.getBaseType();
        }

        return sType.getFullJavaName();
    }

    static String prettyQName(QName qname) {
        if (qname == null) {
            return "";
        }
        String result = qname.getLocalPart();
        if (qname.getNamespaceURI() != null) {
            result += "(@" + javaCommentEscape(qname.getNamespaceURI()) + ")";
        }
        return result;
    }

    void printInnerTypeJavaDoc(SchemaType sType) throws IOException {
        QName name = sType.getName();
        if (name == null) {
            if (sType.isDocumentType()) {
                name = sType.getDocumentElementName();
            } else if (sType.isAttributeType()) {
                name = sType.getAttributeTypeAttributeName();
            } else if (sType.getContainerField() != null) {
                name = sType.getContainerField().getName();
            }
        }

        emit("/**");
        if (opt.isCompileAnnotationAsJavadoc() && sType.getDocumentation() != null && !sType.getDocumentation().isEmpty()){
            emit(" *");
            printJavaDocBody(sType.getDocumentation());
            emit(" *");
        }
        if (sType.isDocumentType()) {
            emit(" * A document containing one " + prettyQName(name) + " element.");
        } else if (sType.isAttributeType()) {
            emit(" * A document containing one " + prettyQName(name) + " attribute.");
        } else if (name != null) {
            emit(" * An XML " + prettyQName(name) + ".");
        } else {
            emit(" * An anonymous inner XML type.");
        }
        emit(" *");
        switch (sType.getSimpleVariety()) {
            case SchemaType.NOT_SIMPLE:
                emit(" * This is a complex type.");
                break;
            case SchemaType.ATOMIC:
                emit(" * This is an atomic type that is a restriction of " + getFullJavaName(sType) + ".");
                break;
            case SchemaType.LIST:
                emit(" * This is a list type whose items are " + sType.getListItemType().getFullJavaName() + ".");
                break;
            case SchemaType.UNION:
                emit(" * This is a union type. Instances are of one of the following types:");
                SchemaType[] members = sType.getUnionConstituentTypes();
                for (SchemaType member : members) {
                    emit(" *     " + member.getFullJavaName());
                }
                break;
        }
        emit(" */");
    }

    private String getFullJavaName(SchemaType sType) {

        SchemaTypeImpl sTypeI = (SchemaTypeImpl) sType;
        String ret = sTypeI.getFullJavaName();

        while (sTypeI != null && sTypeI.isRedefinition()) {
            ret = sTypeI.getFullJavaName();
            sTypeI = (SchemaTypeImpl) sTypeI.getBaseType();
        }
        return ret;
    }

    private String getUserTypeStaticHandlerMethod(boolean encode, SchemaTypeImpl stype) {
        String unqualifiedName = stype.getName().getLocalPart();
        if (unqualifiedName.length() < 2) {
            unqualifiedName = unqualifiedName.toUpperCase(Locale.ROOT);
        } else {
            unqualifiedName = unqualifiedName.substring(0, 1).toUpperCase(Locale.ROOT) + unqualifiedName.substring(1);
        }

        if (encode) {
            return stype.getUserTypeHandlerName() + ".encode" + unqualifiedName;
        } else {
            return stype.getUserTypeHandlerName() + ".decode" + unqualifiedName;
        }
    }


    public static String indexClassForSystem(SchemaTypeSystem system) {
        String name = system.getName();
        return name + "." + INDEX_CLASSNAME;
    }

    void printStaticTypeDeclaration(SchemaType sType, SchemaTypeSystem system) throws IOException {
        // Only need full factories for top-level types
        Class<?> factoryClass;
        if (sType.isAnonymousType() && !sType.isDocumentType() && !sType.isAttributeType()) {
            factoryClass = ElementFactory.class;
        } else if (sType.isSimpleType()) {
            factoryClass = SimpleTypeFactory.class;
        } else if (sType.isAbstract()) {
            factoryClass = AbstractDocumentFactory.class;
        } else {
            factoryClass = DocumentFactory.class;
        }

        String factoryName = factoryClass.getSimpleName();

        String fullName = sType.getFullJavaName().replace('$', '.');
        String sysName = sType.getTypeSystem().getName();


        emit(factoryName + "<" + fullName + "> Factory = new " + factoryName +
                "<>(" + sysName + ".TypeSystemHolder.typeSystem, \"" + ((SchemaTypeSystemImpl) system).handleForType(sType) + "\");"
        );
        emit("org.apache.xmlbeans.SchemaType type = Factory.getType();");
        emit("");
    }

    void printInnerType(SchemaType sType, SchemaTypeSystem system) throws IOException {
        emit("");

        printInnerTypeJavaDoc(sType);

        startInterface(sType);

        printStaticTypeDeclaration(sType, system);

        if (sType.isSimpleType()) {
            if (sType.hasStringEnumValues()) {
                printStringEnumeration(sType);
            }
        } else {
            if (sType.getContentType() == SchemaType.SIMPLE_CONTENT && sType.hasStringEnumValues()) {
                printStringEnumeration(sType);
            }

            SchemaProperty[] props = getDerivedProperties(sType);

            for (SchemaProperty prop : props) {
                printPropertyGetters(prop);

                if (!prop.isReadOnly()) {
                    printPropertySetters(prop);
                }
            }

        }

        printNestedInnerTypes(sType, system);

        endBlock();
    }

    void printNestedInnerTypes(SchemaType sType, SchemaTypeSystem system) throws IOException {
        boolean redefinition = sType.getName() != null &&
                sType.getName().equals(sType.getBaseType().getName());
        while (sType != null) {
            SchemaType[] anonTypes = sType.getAnonymousTypes();
            for (SchemaType anonType : anonTypes) {
                if (anonType.isSkippedAnonymousType()) {
                    printNestedInnerTypes(anonType, system);
                } else {
                    printInnerType(anonType, system);
                }
            }
            // For redefinition other than by extension for complex types, go ahead and print
            // the anonymous types in the base
            if (!redefinition ||
                    (sType.getDerivationType() != SchemaType.DT_EXTENSION && !sType.isSimpleType())) {
                break;
            }
            sType = sType.getBaseType();
        }
    }

    void printTopComment(SchemaType sType) throws IOException {
        // if(sType.getDocumentation() != null && sType.getDocumentation().length() > 0){
        //     printJavaDocParagraph(sType.getDocumentation());
        // }
        emit("/*");
        if (sType.getName() != null) {
            emit(" * XML Type:  " + sType.getName().getLocalPart());
            emit(" * Namespace: " + javaCommentEscape(sType.getName().getNamespaceURI()));
        } else {
            QName thename = null;

            if (sType.isDocumentType()) {
                thename = sType.getDocumentElementName();
                emit(" * An XML document type.");
            } else if (sType.isAttributeType()) {
                thename = sType.getAttributeTypeAttributeName();
                emit(" * An XML attribute type.");
            } else {
                assert false;
            }

            assert (thename != null);

            emit(" * Localname: " + thename.getLocalPart());
            emit(" * Namespace: " + javaCommentEscape(thename.getNamespaceURI()));
        }
        emit(" * Java type: " + sType.getFullJavaName());
        emit(" *");
        emit(" * Automatically generated - do not modify.");
        emit(" */");
    }

    void printPackage(SchemaType sType, boolean intf) throws IOException {
        String fqjn;
        if (intf) {
            fqjn = sType.getFullJavaName();
        } else {
            fqjn = sType.getFullJavaImplName();
        }

        int lastdot = fqjn.lastIndexOf('.');
        if (lastdot < 0) {
            return;
        }
        String pkg = fqjn.substring(0, lastdot);
        emit("package " + pkg + ";");
    }

    void startInterface(SchemaType sType) throws IOException {
        String shortName = sType.getShortJavaName();

        String baseInterface = findJavaType(sType.getBaseType());

        emit("public interface " + shortName + " extends " + baseInterface + getExtensionInterfaces(sType) + " {");
        indent();
        emitSpecializedAccessors(sType);
    }

    private static String getExtensionInterfaces(SchemaType sType) {
        SchemaTypeImpl sImpl = getImpl(sType);
        if (sImpl == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        InterfaceExtension[] exts = sImpl.getInterfaceExtensions();
        if (exts != null) {
            for (InterfaceExtension ext : exts) {
                sb.append(", ").append(ext.getInterface());
            }
        }

        return sb.toString();
    }

    private static SchemaTypeImpl getImpl(SchemaType sType) {
        if (sType instanceof SchemaTypeImpl) {
            return (SchemaTypeImpl) sType;
        } else {
            return null;
        }
    }

    private void emitSpecializedAccessors(SchemaType sType) throws IOException {
        if (sType.getSimpleVariety() == SchemaType.ATOMIC &&
                sType.getPrimitiveType().getBuiltinTypeCode() == SchemaType.BTC_DECIMAL) {
            int bits = sType.getDecimalSize();
            int parentBits = sType.getBaseType().getDecimalSize();
            if (bits != parentBits || sType.getBaseType().getFullJavaName() == null) {
                switch (bits) {
                    case SchemaType.SIZE_BIG_INTEGER:
                        emit("java.math.BigInteger getBigIntegerValue();", BeanMethod.GET);
                        emit("void setBigIntegerValue(java.math.BigInteger bi);", BeanMethod.SET);
                        break;
                    case SchemaType.SIZE_LONG:
                        emit("long getLongValue();", BeanMethod.GET);
                        emit("void setLongValue(long l);", BeanMethod.SET);
                        break;
                    case SchemaType.SIZE_INT:
                        emit("int getIntValue();", BeanMethod.GET);
                        emit("void setIntValue(int i);", BeanMethod.SET);
                        break;
                    case SchemaType.SIZE_SHORT:
                        emit("short getShortValue();", BeanMethod.GET);
                        emit("void setShortValue(short s);", BeanMethod.SET);
                        break;
                    case SchemaType.SIZE_BYTE:
                        emit("byte getByteValue();", BeanMethod.GET);
                        emit("void setByteValue(byte b);", BeanMethod.SET);
                        break;
                }
            }
        }

        if (sType.getSimpleVariety() == SchemaType.UNION) {
            emit("java.lang.Object getObjectValue();", BeanMethod.GET);
            emit("void setObjectValue(java.lang.Object val);", BeanMethod.SET);
            emit("org.apache.xmlbeans.SchemaType instanceType();", BeanMethod.INSTANCE_TYPE);
            SchemaType ctype = sType.getUnionCommonBaseType();
            if (ctype != null && ctype.getSimpleVariety() != SchemaType.UNION) {
                emitSpecializedAccessors(ctype);
            }
        }

        if (sType.getSimpleVariety() == SchemaType.LIST) {
            emit("java.util.List getListValue();", BeanMethod.GET_LIST);
            emit("java.util.List xgetListValue();", BeanMethod.XGET_LIST);
            emit("void setListValue(java.util.List<?> list);", BeanMethod.SET_LIST);
        }
    }

    void startBlock() {
        // emit("{");
        indent();
    }

    void endBlock() throws IOException {
        outdent();
        emit("}");
    }

    void printJavaDoc(String sentence, BeanMethod method) throws IOException {
        Set<BeanMethod> partMet = opt == null ? null : opt.getCompilePartialMethod();
        if ((partMet == null || partMet.contains(method))) {
            printJavaDoc(sentence);
        }
    }

    void printJavaDoc(String sentence) throws IOException {
        emit("");
        emit("/**");
        emit(" * " + sentence);
        emit(" */");
    }

    void printJavaDocParagraph(String s) throws IOException{
        emit("");
        emit("/**");
        printJavaDocBody(s);
        emit(" */");
    }

    void printJavaDocBody(String doc) throws IOException{
        // add some poor mans code injection protection
        // this is not protecting against annotation based RCEs like CVE-2018-16621
        String docClean = javaCommentEscape(doc.trim().replace("\t", ""));

        for (String s : docClean.split("[\\n\\r]+")) {
            emit(" * " + s);
        }
    }

    public static String javaCommentEscape(String str)
    {
        // forbidden: */, and the backslash of a unicode escape - those are decoded
        // before comments are recognized (JLS 3.3), so an escaped */ ends the
        // comment too and the rest of the schema text is compiled as code
        return str.replace("\\", "\\\\").replace("*/", "* /");
    }

    public static String javaStringEscape(String str)
    {
        // forbidden: \n, \r, \", \\.
        test:
        {
            for (int i = 0; i < str.length(); i++) {
                switch (str.charAt(i)) {
                    case '\n':
                    case '\r':
                    case '\"':
                    case '\\':
                        break test;
                }
            }
            return str;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            switch (ch) {
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

    void printStringEnumeration(SchemaType sType) throws IOException {
        SchemaType baseEnumType = sType.getBaseEnumType();
        String baseEnumClass = baseEnumType.getFullJavaName();
        boolean hasBase = hasBase(sType);

        if (!hasBase) {
            emit("");
            emit("org.apache.xmlbeans.StringEnumAbstractBase getEnumValue();", BeanMethod.GET);
            emit("void setEnumValue(org.apache.xmlbeans.StringEnumAbstractBase e);", BeanMethod.SET);
        }

        emit("");
        SchemaStringEnumEntry[] entries = sType.getStringEnumEntries();
        HashSet<String> seenValues = new HashSet<>();
        HashSet<String> repeatValues = new HashSet<>();
        for (SchemaStringEnumEntry entry : entries) {
            String enumValue = entry.getString();
            if (seenValues.contains(enumValue)) {
                repeatValues.add(enumValue);
                continue;
            } else {
                seenValues.add(enumValue);
            }
            String constName = entry.getEnumName();
            if (hasBase) {
                emit(baseEnumClass + ".Enum " + constName + " = " + baseEnumClass + "." + constName + ";");
            } else {
                emit("Enum " + constName + " = Enum.forString(\"" + javaStringEscape(enumValue) + "\");");
            }
        }
        emit("");
        for (SchemaStringEnumEntry entry : entries) {
            if (repeatValues.contains(entry.getString())) {
                continue;
            }
            String constName = "INT_" + entry.getEnumName();
            if (hasBase) {
                emit("int " + constName + " = " + baseEnumClass + "." + constName + ";");
            } else {
                emit("int " + constName + " = Enum." + constName + ";");
            }
        }
        if (!hasBase) {
            emit("");
            emit("/**");
            emit(" * Enumeration value class for " + baseEnumClass + ".");
            emit(" * These enum values can be used as follows:");
            emit(" * <pre>");
            emit(" * enum.toString(); // returns the string value of the enum");
            emit(" * enum.intValue(); // returns an int value, useful for switches");
            if (entries.length > 0) {
                emit(" * // e.g., case Enum.INT_" + entries[0].getEnumName());
            }
            emit(" * Enum.forString(s); // returns the enum value for a string");
            emit(" * Enum.forInt(i); // returns the enum value for an int");
            emit(" * </pre>");
            emit(" * Enumeration objects are immutable singleton objects that");
            emit(" * can be compared using == object equality. They have no");
            emit(" * public constructor. See the constants defined within this");
            emit(" * class for all the valid values.");
            emit(" */");
            emit("final class Enum extends org.apache.xmlbeans.StringEnumAbstractBase {");
            indent();
            emit("/**");
            emit(" * Returns the enum value for a string, or null if none.");
            emit(" */");
            emit("public static Enum forString(java.lang.String s) {");
            emit("    return (Enum)table.forString(s);");
            emit("}");
            emit("");
            emit("/**");
            emit(" * Returns the enum value corresponding to an int, or null if none.");
            emit(" */");
            emit("public static Enum forInt(int i) {");
            emit("    return (Enum)table.forInt(i);");
            emit("}");
            emit("");
            emit("private Enum(java.lang.String s, int i) {");
            emit("    super(s, i);");
            emit("}");
            emit("");
            for (SchemaStringEnumEntry entry : entries) {
                String constName = "INT_" + entry.getEnumName();
                int intValue = entry.getIntValue();
                emit("static final int " + constName + " = " + intValue + ";");
            }
            emit("");
            emit("public static final org.apache.xmlbeans.StringEnumAbstractBase.Table table =");
            emit("    new org.apache.xmlbeans.StringEnumAbstractBase.Table(new Enum[] {");
            indent();
            for (SchemaStringEnumEntry entry : entries) {
                String enumValue = entry.getString();
                String constName = "INT_" + entry.getEnumName();
                emit("new Enum(\"" + javaStringEscape(enumValue) + "\", " + constName + "),");
            }
            outdent();
            emit("});");
            emit("private static final long serialVersionUID = 1L;");
            emit("private java.lang.Object readResolve() {");
            emit("    return forInt(intValue());");
            emit("}");
            outdent();
            emit("}");
        }
    }

    private boolean hasBase(SchemaType sType) {
        boolean hasBase;
        SchemaType baseEnumType = sType.getBaseEnumType();
        if (baseEnumType.isAnonymousType() && baseEnumType.isSkippedAnonymousType()) {
            if (sType.getContentBasedOnType() != null) {
                hasBase = sType.getContentBasedOnType().getBaseType() != baseEnumType;
            } else {
                hasBase = sType.getBaseType() != baseEnumType;
            }
        } else {
            hasBase = baseEnumType != sType;
        }
        return hasBase;
    }

    String xmlTypeForProperty(SchemaProperty sProp) {
        SchemaType sType = sProp.javaBasedOnType();
        return findJavaType(sType).replace('$', '.');
    }

    static boolean xmlTypeForPropertyIsUnion(SchemaProperty sProp) {
        SchemaType sType = sProp.javaBasedOnType();
        return (sType.isSimpleType() && sType.getSimpleVariety() == SchemaType.UNION);
    }

    static boolean isJavaPrimitive(int javaType) {
        return (javaType >= SchemaProperty.JAVA_FIRST_PRIMITIVE && (javaType <= SchemaProperty.JAVA_LAST_PRIMITIVE));
    }

    /**
     * Returns the wrapped type for a java primitive.
     */
    static String javaWrappedType(int javaType) {
        switch (javaType) {
            case SchemaProperty.JAVA_BOOLEAN:
                return "java.lang.Boolean";
            case SchemaProperty.JAVA_FLOAT:
                return "java.lang.Float";
            case SchemaProperty.JAVA_DOUBLE:
                return "java.lang.Double";
            case SchemaProperty.JAVA_BYTE:
                return "java.lang.Byte";
            case SchemaProperty.JAVA_SHORT:
                return "java.lang.Short";
            case SchemaProperty.JAVA_INT:
                return "java.lang.Integer";
            case SchemaProperty.JAVA_LONG:
                return "java.lang.Long";

            // anything else is not a java primitive
            default:
                assert false;
                throw new IllegalStateException();
        }
    }

    String javaTypeForProperty(SchemaProperty sProp) {
        // The type to use is the XML object....
        if (sProp.getJavaTypeCode() == SchemaProperty.XML_OBJECT) {
            SchemaType sType = sProp.javaBasedOnType();
            return findJavaType(sType).replace('$', '.');
        }

        if (sProp.getJavaTypeCode() == SchemaProperty.JAVA_USER) {
            return ((SchemaTypeImpl) sProp.getType()).getUserTypeName();
        }

        switch (sProp.getJavaTypeCode()) {
            case SchemaProperty.JAVA_BOOLEAN:
                return "boolean";
            case SchemaProperty.JAVA_FLOAT:
                return "float";
            case SchemaProperty.JAVA_DOUBLE:
                return "double";
            case SchemaProperty.JAVA_BYTE:
                return "byte";
            case SchemaProperty.JAVA_SHORT:
                return "short";
            case SchemaProperty.JAVA_INT:
                return "int";
            case SchemaProperty.JAVA_LONG:
                return "long";

            case SchemaProperty.JAVA_BIG_DECIMAL:
                return "java.math.BigDecimal";
            case SchemaProperty.JAVA_BIG_INTEGER:
                return "java.math.BigInteger";
            case SchemaProperty.JAVA_STRING:
                return "java.lang.String";
            case SchemaProperty.JAVA_BYTE_ARRAY:
                return "byte[]";
            case SchemaProperty.JAVA_GDATE:
                return "org.apache.xmlbeans.GDate";
            case SchemaProperty.JAVA_GDURATION:
                return "org.apache.xmlbeans.GDuration";
            case SchemaProperty.JAVA_DATE:
                return "java.util.Date";
            case SchemaProperty.JAVA_QNAME:
                return "javax.xml.namespace.QName";
            case SchemaProperty.JAVA_LIST:
                return "java.util.List";
            case SchemaProperty.JAVA_CALENDAR:
                return "java.util.Calendar";

            case SchemaProperty.JAVA_ENUM:
                SchemaType sType = sProp.javaBasedOnType();
                if (sType.getSimpleVariety() == SchemaType.UNION) {
                    sType = sType.getUnionCommonBaseType();
                }
                assert sType.getBaseEnumType() != null;
                if (hasBase(sType)) {
                    return findJavaType(sType.getBaseEnumType()).replace('$', '.') + ".Enum";
                } else {
                    return findJavaType(sType).replace('$', '.') + ".Enum";
                }

            case SchemaProperty.JAVA_OBJECT:
                return "java.lang.Object";

            default:
                assert (false);
                throw new IllegalStateException();
        }
    }

    void printPropertyGetters(SchemaProperty prop) throws IOException {
        String propertyName = prop.getJavaPropertyName();
        int javaType = prop.getJavaTypeCode();
        String type = javaTypeForProperty(prop);
        String xtype = xmlTypeForProperty(prop);
        boolean nillable = prop.hasNillable() != SchemaProperty.NEVER;
        boolean several = prop.extendsJavaArray();
        String propertyDocumentation = prop.getDocumentation();

        String propdesc = "\"" + prop.getName().getLocalPart() + "\"" + (prop.isAttribute() ? " attribute" : " element");
        boolean xmltype = (javaType == SchemaProperty.XML_OBJECT);

        if (prop.extendsJavaSingleton()) {
            if (opt.isCompileAnnotationAsJavadoc() && propertyDocumentation != null && !propertyDocumentation.isEmpty()){
                printJavaDocParagraph(propertyDocumentation);
            }else {
                printJavaDoc((several ? "Gets first " : "Gets the ") + propdesc, BeanMethod.GET);
            }
            emit(type + " get" + propertyName + "();", BeanMethod.GET);

            if (!xmltype) {
                printJavaDoc((several ? "Gets (as xml) first " : "Gets (as xml) the ") + propdesc, BeanMethod.XGET);
                emit(xtype + " xget" + propertyName + "();", BeanMethod.XGET);
            }

            if (nillable) {
                printJavaDoc((several ? "Tests for nil first " : "Tests for nil ") + propdesc, BeanMethod.IS_NIL);
                emit("boolean isNil" + propertyName + "();", BeanMethod.IS_NIL);
            }
        }

        if (prop.extendsJavaOption()) {
            if (opt.isCompileAnnotationAsJavadoc() && propertyDocumentation != null && !propertyDocumentation.isEmpty()){
                printJavaDocParagraph(propertyDocumentation);
            }else {
                printJavaDoc((several ? "True if has at least one " : "True if has ") + propdesc, BeanMethod.IS_SET);
            }
            emit("boolean isSet" + propertyName + "();", BeanMethod.IS_SET);
        }

        if (several) {
            if (opt.isCompileAnnotationAsJavadoc() && propertyDocumentation != null && !propertyDocumentation.isEmpty()){
                printJavaDocParagraph(propertyDocumentation);
            }

            String arrayName = propertyName + "Array";

            String wrappedType = type;
            if (isJavaPrimitive(javaType)) {
                wrappedType = javaWrappedType(javaType);
            }

            if (opt.isCompileAnnotationAsJavadoc() && propertyDocumentation != null && !propertyDocumentation.isEmpty()){
                printJavaDocParagraph(propertyDocumentation);
            }else{
                printJavaDoc("Gets a List of " + propdesc + "s", BeanMethod.GET_LIST);
            }
            emit("java.util.List<" + wrappedType + "> get" + propertyName + "List();", BeanMethod.GET_LIST);

            if (opt.isCompileAnnotationAsJavadoc() && propertyDocumentation != null && !propertyDocumentation.isEmpty()){
                printJavaDocParagraph(propertyDocumentation);
            }else{
                printJavaDoc("Gets array of all " + propdesc + "s", BeanMethod.GET_ARRAY);
            }
            emit(type + "[] get" + arrayName + "();", BeanMethod.GET_ARRAY);

            printJavaDoc("Gets ith " + propdesc, BeanMethod.GET_IDX);
            emit(type + " get" + arrayName + "(int i);", BeanMethod.GET_IDX);

            if (!xmltype) {
                printJavaDoc("Gets (as xml) a List of " + propdesc + "s", BeanMethod.XGET_LIST);
                emit("java.util.List<" + xtype + "> xget" + propertyName + "List();", BeanMethod.XGET_LIST);

                printJavaDoc("Gets (as xml) array of all " + propdesc + "s", BeanMethod.XGET_ARRAY);
                emit(xtype + "[] xget" + arrayName + "();", BeanMethod.XGET_ARRAY);

                printJavaDoc("Gets (as xml) ith " + propdesc, BeanMethod.XGET_IDX);
                emit(xtype + " xget" + arrayName + "(int i);", BeanMethod.XGET_IDX);
            }

            if (nillable) {
                printJavaDoc("Tests for nil ith " + propdesc, BeanMethod.IS_NIL_IDX);
                emit("boolean isNil" + arrayName + "(int i);", BeanMethod.IS_NIL_IDX);
            }

            printJavaDoc("Returns number of " + propdesc, BeanMethod.SIZE_OF_ARRAY);
            emit("int sizeOf" + arrayName + "();", BeanMethod.SIZE_OF_ARRAY);
        }
    }

    void printPropertySetters(SchemaProperty prop) throws IOException {
        QName qName = prop.getName();
        boolean isAttr = prop.isAttribute();
        String propertyName = prop.getJavaPropertyName();
        int javaType = prop.getJavaTypeCode();
        String type = javaTypeForProperty(prop);
        String xtype = xmlTypeForProperty(prop);
        boolean nillable = prop.hasNillable() != SchemaProperty.NEVER;
        boolean optional = prop.extendsJavaOption();
        boolean several = prop.extendsJavaArray();
        boolean singleton = prop.extendsJavaSingleton();
        String propertyDocumentation = prop.getDocumentation();

        String safeVarName = NameUtil.nonJavaKeyword(NameUtil.lowerCamelCase(propertyName));
        if (safeVarName.equals("i")) {
            safeVarName = "iValue";
        }
        boolean xmltype = (javaType == SchemaProperty.XML_OBJECT);

        String propdesc = "\"" + qName.getLocalPart() + "\"" + (isAttr ? " attribute" : " element");

        if (singleton) {
            if (opt.isCompileAnnotationAsJavadoc() && propertyDocumentation != null && !propertyDocumentation.isEmpty()) {
                printJavaDocParagraph(propertyDocumentation);
            } else {
                printJavaDoc((several ? "Sets first " : "Sets the ") + propdesc, BeanMethod.SET);
            }
            emit("void set" + propertyName + "(" + type + " " + safeVarName + ");", BeanMethod.SET);

            if (!xmltype) {
                printJavaDoc((several ? "Sets (as xml) first " : "Sets (as xml) the ") + propdesc, BeanMethod.XSET);
                emit("void xset" + propertyName + "(" + xtype + " " + safeVarName + ");", BeanMethod.XSET);
            }

            if (xmltype && !several) {
                printJavaDoc("Appends and returns a new empty " + propdesc, BeanMethod.ADD_NEW);
                emit(xtype + " addNew" + propertyName + "();", BeanMethod.ADD_NEW);
            }

            if (nillable) {
                printJavaDoc((several ? "Nils the first " : "Nils the ") + propdesc, BeanMethod.SET_NIL);
                emit("void setNil" + propertyName + "();", BeanMethod.SET_NIL);
            }
        }

        if (optional) {
            if (opt.isCompileAnnotationAsJavadoc() && propertyDocumentation != null && !propertyDocumentation.isEmpty()) {
                printJavaDocParagraph(propertyDocumentation);
            } else {
                printJavaDoc((several ? "Removes first " : "Unsets the ") + propdesc, BeanMethod.UNSET);
            }
            emit("void unset" + propertyName + "();", BeanMethod.UNSET);
        }

        if (several) {
            String arrayName = propertyName + "Array";

            if(opt.isCompileAnnotationAsJavadoc() && propertyDocumentation != null && !propertyDocumentation.isEmpty()) {
                printJavaDocParagraph(propertyDocumentation);
            } else {
                printJavaDoc("Sets array of all " + propdesc, BeanMethod.SET_ARRAY);
            }
            emit("void set" + arrayName + "(" + type + "[] " + safeVarName + "Array);", BeanMethod.SET_ARRAY);

            if (opt.isCompileAnnotationAsJavadoc() && propertyDocumentation != null && !propertyDocumentation.isEmpty()) {
                printJavaDocParagraph(propertyDocumentation);
            } else {
                printJavaDoc("Sets ith " + propdesc, BeanMethod.SET_IDX);
            }
            emit("void set" + arrayName + "(int i, " + type + " " + safeVarName + ");", BeanMethod.SET_IDX);

            if (!xmltype) {
                printJavaDoc("Sets (as xml) array of all " + propdesc, BeanMethod.XSET_ARRAY);
                emit("void xset" + arrayName + "(" + xtype + "[] " + safeVarName + "Array);", BeanMethod.XSET_ARRAY);

                printJavaDoc("Sets (as xml) ith " + propdesc, BeanMethod.XSET_IDX);
                emit("void xset" + arrayName + "(int i, " + xtype + " " + safeVarName + ");", BeanMethod.XSET_IDX);
            }

            if (nillable) {
                printJavaDoc("Nils the ith " + propdesc, BeanMethod.SET_NIL_IDX);
                emit("void setNil" + arrayName + "(int i);", BeanMethod.SET_NIL_IDX);
            }

            if (!xmltype) {
                printJavaDoc("Inserts the value as the ith " + propdesc, BeanMethod.INSERT_IDX);
                emit("void insert" + propertyName + "(int i, " + type + " " + safeVarName + ");", BeanMethod.INSERT_IDX);

                printJavaDoc("Appends the value as the last " + propdesc, BeanMethod.ADD);
                emit("void add" + propertyName + "(" + type + " " + safeVarName + ");", BeanMethod.ADD);
            }

            printJavaDoc("Inserts and returns a new empty value (as xml) as the ith " + propdesc, BeanMethod.INSERT_NEW_IDX);
            emit(xtype + " insertNew" + propertyName + "(int i);", BeanMethod.INSERT_NEW_IDX);

            printJavaDoc("Appends and returns a new empty value (as xml) as the last " + propdesc, BeanMethod.ADD_NEW);
            emit(xtype + " addNew" + propertyName + "();", BeanMethod.ADD_NEW);

            printJavaDoc("Removes the ith " + propdesc, BeanMethod.REMOVE_IDX);
            emit("void remove" + propertyName + "(int i);", BeanMethod.REMOVE_IDX);
        }
    }

    String getAtomicRestrictionType(SchemaType sType) {
        SchemaType pType = sType.getPrimitiveType();
        switch (pType.getBuiltinTypeCode()) {
            case SchemaType.BTC_ANY_SIMPLE:
                return "org.apache.xmlbeans.impl.values.XmlAnySimpleTypeImpl";
            case SchemaType.BTC_BOOLEAN:
                return "org.apache.xmlbeans.impl.values.JavaBooleanHolderEx";
            case SchemaType.BTC_BASE_64_BINARY:
                return "org.apache.xmlbeans.impl.values.JavaBase64HolderEx";
            case SchemaType.BTC_HEX_BINARY:
                return "org.apache.xmlbeans.impl.values.JavaHexBinaryHolderEx";
            case SchemaType.BTC_ANY_URI:
                return "org.apache.xmlbeans.impl.values.JavaUriHolderEx";
            case SchemaType.BTC_QNAME:
                return "org.apache.xmlbeans.impl.values.JavaQNameHolderEx";
            case SchemaType.BTC_NOTATION:
                return "org.apache.xmlbeans.impl.values.JavaNotationHolderEx";
            case SchemaType.BTC_FLOAT:
                return "org.apache.xmlbeans.impl.values.JavaFloatHolderEx";
            case SchemaType.BTC_DOUBLE:
                return "org.apache.xmlbeans.impl.values.JavaDoubleHolderEx";
            case SchemaType.BTC_DECIMAL:
                switch (sType.getDecimalSize()) {
                    case SchemaType.SIZE_BIG_DECIMAL:
                        return "org.apache.xmlbeans.impl.values.JavaDecimalHolderEx";
                    case SchemaType.SIZE_BIG_INTEGER:
                        return "org.apache.xmlbeans.impl.values.JavaIntegerHolderEx";
                    case SchemaType.SIZE_LONG:
                        return "org.apache.xmlbeans.impl.values.JavaLongHolderEx";
                    case SchemaType.SIZE_INT:
                    case SchemaType.SIZE_SHORT:
                    case SchemaType.SIZE_BYTE:
                        return "org.apache.xmlbeans.impl.values.JavaIntHolderEx";
                    default:
                        assert (false);
                }
            case SchemaType.BTC_STRING:
                if (sType.hasStringEnumValues()) {
                    return "org.apache.xmlbeans.impl.values.JavaStringEnumerationHolderEx";
                } else {
                    return "org.apache.xmlbeans.impl.values.JavaStringHolderEx";
                }

            case SchemaType.BTC_DATE_TIME:
            case SchemaType.BTC_TIME:
            case SchemaType.BTC_DATE:
            case SchemaType.BTC_G_YEAR_MONTH:
            case SchemaType.BTC_G_YEAR:
            case SchemaType.BTC_G_MONTH_DAY:
            case SchemaType.BTC_G_DAY:
            case SchemaType.BTC_G_MONTH:
                return "org.apache.xmlbeans.impl.values.JavaGDateHolderEx";

            case SchemaType.BTC_DURATION:
                return "org.apache.xmlbeans.impl.values.JavaGDurationHolderEx";
            default:
                assert (false) : "unrecognized primitive type";
                return null;
        }
    }

    static SchemaType findBaseType(SchemaType sType) {
        while (sType.getFullJavaName() == null) {
            sType = sType.getBaseType();
        }
        return sType;
    }

    String getBaseClass(SchemaType sType) {
        SchemaType baseType = findBaseType(sType.getBaseType());

        switch (sType.getSimpleVariety()) {
            case SchemaType.NOT_SIMPLE:
                // non-simple-content: inherit from base type impl
                if (!XmlObject.type.equals(baseType)) {
                    return baseType.getFullJavaImplName();
                }
                return "org.apache.xmlbeans.impl.values.XmlComplexContentImpl";

            case SchemaType.ATOMIC:
                // We should only get called for restrictions
                assert (!sType.isBuiltinType());
                return getAtomicRestrictionType(sType);

            case SchemaType.LIST:
                return "org.apache.xmlbeans.impl.values.XmlListImpl";

            case SchemaType.UNION:
                return "org.apache.xmlbeans.impl.values.XmlUnionImpl";

            default:
                throw new IllegalStateException();
        }
    }

    void printConstructor(SchemaType sType, String shortName) throws IOException {
        emit("");
        emit("public " + shortName + "(org.apache.xmlbeans.SchemaType sType) {");
        startBlock();
        emit("super(sType" + (sType.getSimpleVariety() == SchemaType.NOT_SIMPLE ?
                "" :
                ", " + !sType.isSimpleType()) +
                ");");
        endBlock();

        if (sType.getSimpleVariety() != SchemaType.NOT_SIMPLE) {
            emit("");
            emit("protected " + shortName + "(org.apache.xmlbeans.SchemaType sType, boolean b) {");
            startBlock();
            emit("super(sType, b);");
            endBlock();
        }
    }

    void startClass(SchemaType sType, boolean isInner) throws IOException {
        String shortName = sType.getShortJavaImplName();
        String baseClass = getBaseClass(sType);
        StringBuilder interfaces = new StringBuilder();
        interfaces.append(sType.getFullJavaName().replace('$', '.'));

        if (sType.getSimpleVariety() == SchemaType.UNION) {
            SchemaType[] memberTypes = sType.getUnionMemberTypes();
            for (SchemaType memberType : memberTypes) {
                interfaces.append(", ").append(memberType.getFullJavaName().replace('$', '.'));
            }
        }

        emit("public " + (isInner ? "static " : "") + "class " + shortName +
                " extends " + baseClass + " implements " + interfaces + " {");

        startBlock();

        emit("private static final long serialVersionUID = 1L;");
    }

    void makeAttributeDefaultValue(String jtargetType, SchemaProperty prop, String identifier) throws IOException {
        String fullName = jtargetType;
        if (fullName == null) {
            fullName = prop.javaBasedOnType().getFullJavaName().replace('$', '.');
        }

        emit("target = (" + fullName + ")get_default_attribute_value(" + identifier + ");");
    }

    String makeMissingValue(int javaType) throws IOException {
        switch (javaType) {
            case SchemaProperty.JAVA_BOOLEAN:
                return "false";

            case SchemaProperty.JAVA_FLOAT:
                return "0.0f";

            case SchemaProperty.JAVA_DOUBLE:
                return "0.0";

            case SchemaProperty.JAVA_BYTE:
            case SchemaProperty.JAVA_SHORT:
            case SchemaProperty.JAVA_INT:
                return "0";

            case SchemaProperty.JAVA_LONG:
                return "0L";

            case SchemaProperty.XML_OBJECT:
            case SchemaProperty.JAVA_BIG_DECIMAL:
            case SchemaProperty.JAVA_BIG_INTEGER:
            case SchemaProperty.JAVA_STRING:
            case SchemaProperty.JAVA_BYTE_ARRAY:
            case SchemaProperty.JAVA_GDATE:
            case SchemaProperty.JAVA_GDURATION:
            case SchemaProperty.JAVA_DATE:
            case SchemaProperty.JAVA_QNAME:
            case SchemaProperty.JAVA_LIST:
            case SchemaProperty.JAVA_CALENDAR:
            case SchemaProperty.JAVA_ENUM:
            case SchemaProperty.JAVA_OBJECT:
            default:
                return "null";
        }
    }

    void printJGetArrayValue(int javaType, String type, SchemaTypeImpl stype, String setIdentifier) throws IOException {
        String em;
        switch (javaType) {
            case SchemaProperty.XML_OBJECT:
                em ="XmlObjectArray(#ID#, new " + type + "[0]);";
                break;

            case SchemaProperty.JAVA_ENUM:
                em = "EnumArray(#ID#, " + type + "[]::new);";
                break;

            case SchemaProperty.JAVA_BOOLEAN:
                em = "BooleanArray(#ID#);";
                break;

            case SchemaProperty.JAVA_FLOAT:
                em = "FloatArray(#ID#);";
                break;

            case SchemaProperty.JAVA_DOUBLE:
                em = "DoubleArray(#ID#);";
                break;

            case SchemaProperty.JAVA_BYTE:
                em = "ByteArray(#ID#);";
                break;

            case SchemaProperty.JAVA_SHORT:
                em = "ShortArray(#ID#);";
                break;

            case SchemaProperty.JAVA_INT:
                em = "IntArray(#ID#);";
                break;

            case SchemaProperty.JAVA_LONG:
                em = "LongArray(#ID#);";
                break;

            case SchemaProperty.JAVA_BIG_DECIMAL:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getBigDecimalValue, java.math.BigDecimal[]::new);";
                break;

            case SchemaProperty.JAVA_BIG_INTEGER:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getBigIntegerValue, java.math.BigInteger[]::new);";
                break;

            case SchemaProperty.JAVA_STRING:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getStringValue, String[]::new);";
                break;

            case SchemaProperty.JAVA_BYTE_ARRAY:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getByteArrayValue, byte[][]::new);";
                break;

            case SchemaProperty.JAVA_CALENDAR:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getCalendarValue, java.util.Calendar[]::new);";
                break;

            case SchemaProperty.JAVA_DATE:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getDateValue, java.util.Date[]::new);";
                break;

            case SchemaProperty.JAVA_GDATE:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getGDateValue, org.apache.xmlbeans.GDate[]::new);";
                break;

            case SchemaProperty.JAVA_GDURATION:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getGDurationValue, org.apache.xmlbeans.GDuration[]::new);";
                break;

            case SchemaProperty.JAVA_QNAME:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getQNameValue, javax.xml.namespace.QName[]::new);";
                break;

            case SchemaProperty.JAVA_LIST:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getListValue, java.util.List[]::new);";
                break;

            case SchemaProperty.JAVA_OBJECT:
                em = "ObjectArray(#ID#, org.apache.xmlbeans.SimpleValue::getObjectValue, java.lang.Object[]::new);";
                break;

            case SchemaProperty.JAVA_USER:
                // TOOD: replace lambda with method reference
                em = "ObjectArray(#ID#, e -> " + getUserTypeStaticHandlerMethod(false, stype) + "(e), " + stype.getUserTypeName() + "[]::new);";
                break;

            default:
                throw new IllegalStateException();
        }
        emit("return get" +em.replace("#ID#", setIdentifier), BeanMethod.GET_ARRAY);
    }

    String printJGetValue(int javaType, String type, SchemaTypeImpl stype) throws IOException {
        switch (javaType) {
            case SchemaProperty.XML_OBJECT:
                return "target";

            case SchemaProperty.JAVA_BOOLEAN:
                return "target.getBooleanValue()";

            case SchemaProperty.JAVA_FLOAT:
                return "target.getFloatValue()";

            case SchemaProperty.JAVA_DOUBLE:
                return "target.getDoubleValue()";

            case SchemaProperty.JAVA_BYTE:
                return "target.getByteValue()";

            case SchemaProperty.JAVA_SHORT:
                return "target.getShortValue()";

            case SchemaProperty.JAVA_INT:
                return "target.getIntValue()";

            case SchemaProperty.JAVA_LONG:
                return "target.getLongValue()";

            case SchemaProperty.JAVA_BIG_DECIMAL:
                return "target.getBigDecimalValue()";

            case SchemaProperty.JAVA_BIG_INTEGER:
                return "target.getBigIntegerValue()";

            case SchemaProperty.JAVA_STRING:
                return "target.getStringValue()";

            case SchemaProperty.JAVA_BYTE_ARRAY:
                return "target.getByteArrayValue()";

            case SchemaProperty.JAVA_GDATE:
                return "target.getGDateValue()";

            case SchemaProperty.JAVA_GDURATION:
                return "target.getGDurationValue()";

            case SchemaProperty.JAVA_CALENDAR:
                return "target.getCalendarValue()";

            case SchemaProperty.JAVA_DATE:
                return "target.getDateValue()";

            case SchemaProperty.JAVA_QNAME:
                return "target.getQNameValue()";

            case SchemaProperty.JAVA_LIST:
                return "target.getListValue()";

            case SchemaProperty.JAVA_ENUM:
                return "(" + type + ")target.getEnumValue()";

            case SchemaProperty.JAVA_OBJECT:
                return "target.getObjectValue()";

            case SchemaProperty.JAVA_USER:
                return getUserTypeStaticHandlerMethod(false, stype) + "(target)";

            default:
                throw new IllegalStateException();
        }
    }

    void printJSetValue(int javaType, String safeVarName, SchemaTypeImpl stype) throws IOException {
        String em;
        switch (javaType) {
            case SchemaProperty.XML_OBJECT:
                em = "target.set(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_BOOLEAN:
                em = "target.setBooleanValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_FLOAT:
                em = "target.setFloatValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_DOUBLE:
                em = "target.setDoubleValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_BYTE:
                em = "target.setByteValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_SHORT:
                em = "target.setShortValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_INT:
                em = "target.setIntValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_LONG:
                em = "target.setLongValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_BIG_DECIMAL:
                em = "target.setBigDecimalValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_BIG_INTEGER:
                em = "target.setBigIntegerValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_STRING:
                em = "target.setStringValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_BYTE_ARRAY:
                em = "target.setByteArrayValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_GDATE:
                em = "target.setGDateValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_GDURATION:
                em = "target.setGDurationValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_CALENDAR:
                em = "target.setCalendarValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_DATE:
                em = "target.setDateValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_QNAME:
                em = "target.setQNameValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_LIST:
                em = "target.setListValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_ENUM:
                em = "target.setEnumValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_OBJECT:
                em = "target.setObjectValue(#VARNAME#)";
                break;

            case SchemaProperty.JAVA_USER:
                em = getUserTypeStaticHandlerMethod(true, stype) + "(#VARNAME#, target)";
                break;

            default:
                throw new IllegalStateException();
        }
        emit(em.replace("#VARNAME#", safeVarName) + ";");
    }

    void printStaticFields(SchemaProperty[] properties, Map<SchemaProperty, Identifier> propMap) throws IOException {
        if (properties.length == 0) {
            return;
        }

        int countQSet = 0;
        emit("");
        emit("private static final QName[] PROPERTY_QNAME = {");
        indent();
        for (SchemaProperty prop : properties) {
            final QName name = prop.getName();
            propMap.put(prop, new Identifier(propMap.size()));
            emit("new QName(\"" + javaStringEscape(name.getNamespaceURI()) + "\", \"" + javaStringEscape(name.getLocalPart()) + "\"),");
            countQSet = Math.max(countQSet, (prop.acceptedNames() == null ? 0 : prop.acceptedNames().length));
        }
        outdent();
        emit("};");
        emit("");

        if (countQSet > 1) {
            emit("private static final QNameSet[] PROPERTY_QSET = {");
            int index = 0;
            for (SchemaProperty prop : properties) {
                final QName[] qnames = prop.acceptedNames();
                if (qnames != null && qnames.length > 1) {
                    propMap.get(prop).setSetIndex(index++);
                    emit("QNameSet.forArray( new QName[] { ");
                    indent();
                    for (QName qname : qnames) {
                        emit("new QName(\"" + javaStringEscape(qname.getNamespaceURI()) + "\", \"" + javaStringEscape(qname.getLocalPart()) + "\"),");
                    }
                    outdent();
                    emit("}),");
                }
            }
            emit("};");
        }
    }

    void emitImplementationPreamble() throws IOException {
        emit("synchronized (monitor()) {");
        indent();
        emit("check_orphaned();");
    }

    void emitImplementationPostamble() throws IOException {
        outdent();
        emit("}");
    }

    void emitAddTarget(String identifier, boolean isAttr, String xtype)
            throws IOException {
        if (isAttr) {
            emit("target = (" + xtype + ")get_store().add_attribute_user(" + identifier + ");");
        } else {
            emit("target = (" + xtype + ")get_store().add_element_user(" + identifier + ");");
        }
    }

    void emitPre(SchemaType sType, int opType, String identifier, boolean isAttr) throws IOException {
        emitPre(sType, opType, identifier, isAttr, "-1");
    }

    void emitPre(SchemaType sType, int opType, String identifier, boolean isAttr, String index) throws IOException {
        SchemaTypeImpl sImpl = getImpl(sType);
        if (sImpl == null) {
            return;
        }

        PrePostExtension ext = sImpl.getPrePostExtension();
        if (ext != null) {
            if (ext.hasPreCall()) {
                emit("if ( " + ext.getStaticHandler() + ".preSet(" + prePostOpString(opType) + ", this, " + identifier + ", " + isAttr + ", " + index + ")) {");
                startBlock();
            }
        }
    }

    void emitPost(SchemaType sType, int opType, String identifier, boolean isAttr) throws IOException {
        emitPost(sType, opType, identifier, isAttr, "-1");
    }

    void emitPost(SchemaType sType, int opType, String identifier, boolean isAttr, String index) throws IOException {
        SchemaTypeImpl sImpl = getImpl(sType);
        if (sImpl == null) {
            return;
        }

        PrePostExtension ext = sImpl.getPrePostExtension();
        if (ext != null) {
            if (ext.hasPreCall()) {
                endBlock();
            }

            if (ext.hasPostCall()) {
                emit(ext.getStaticHandler() + ".postSet(" + prePostOpString(opType) + ", this, " + identifier + ", " + isAttr + ", " + index + ");");
            }
        }
    }

    String prePostOpString(int opType) {
        switch (opType) {
            case PrePostExtension.OPERATION_SET:
                return "org.apache.xmlbeans.PrePostExtension.OPERATION_SET";

            case PrePostExtension.OPERATION_INSERT:
                return "org.apache.xmlbeans.PrePostExtension.OPERATION_INSERT";

            case PrePostExtension.OPERATION_REMOVE:
                return "org.apache.xmlbeans.PrePostExtension.OPERATION_REMOVE";

            default:
                throw new IllegalArgumentException("Invalid opType: " + opType);
        }
    }

    private static final int NOTHING = 1;
    private static final int ADD_NEW_VALUE = 3;
    private static final int THROW_EXCEPTION = 4;

    void emitGetTarget(String setIdentifier,
                       String identifier,
                       boolean isAttr,
                       String index,
                       int nullBehaviour,
                       String xtype)
            throws IOException {
        assert setIdentifier != null && identifier != null;

        emit(xtype + " target = null;");

        if (isAttr) {
            emit("target = (" + xtype + ")get_store().find_attribute_user(" + identifier + ");");
        } else {
            emit("target = (" + xtype + ")get_store().find_element_user(" + setIdentifier + ", " + index + ");");
        }

        if (nullBehaviour == NOTHING) {
            return;
        }

        emit("if (target == null) {");

        startBlock();

        switch (nullBehaviour) {
            case ADD_NEW_VALUE:
                // target already emited, no need for emitDeclareTarget(false, xtype);
                emitAddTarget(identifier, isAttr, xtype);
                break;

            case THROW_EXCEPTION:
                emit("throw new IndexOutOfBoundsException();");
                break;

            default:
                assert false : "Bad behaviour type: " + nullBehaviour;
        }

        endBlock();
    }

    void printListGetterImpl(String propdesc, String propertyName, String wrappedType, boolean xmltype, boolean xget)
            throws IOException {
        Set<BeanMethod> bmList = (opt == null) ? null : opt.getCompilePartialMethod();
        if (bmList != null && !bmList.contains(xget ? BeanMethod.XGET_LIST : BeanMethod.GET_LIST)) {
            return;
        }

        String arrayName = propertyName + "Array";

        printJavaDoc("Gets " + (xget ? "(as xml) " : "") + "a List of " + propdesc + "s");
        if (!opt.isCompileNoAnnotations()) {
            emit("@Override");
        }
        emit("public java.util.List<" + wrappedType + "> " + (xget ? "xget" : "get") + propertyName + "List() {");
        startBlock();

        emitImplementationPreamble();

        emit("return new org.apache.xmlbeans.impl.values.JavaList" + ((xmltype || xget) ? "Xml" : "") + "Object<>(");
        indent();
        if (bmList == null || bmList.contains(xget ? BeanMethod.XGET_IDX : BeanMethod.GET_IDX)) {
            emit("this::" + (xget ? "xget" : "get") + arrayName + ",");
        } else {
            emit("null,");
        }
        if (bmList == null || bmList.contains(xget ? BeanMethod.XSET_IDX : BeanMethod.SET_IDX)) {
            emit("this::" + (xget ? "xset" : "set") + arrayName + ",");
        } else {
            emit("null,");
        }
        if (bmList == null || bmList.contains((xmltype || xget) ? BeanMethod.INSERT_NEW_IDX : BeanMethod.INSERT_IDX)) {
            emit("this::insert" + ((xmltype || xget) ? "New" : "") + propertyName + ",");
        } else {
            emit("null,");
        }
        if (bmList == null || bmList.contains(BeanMethod.REMOVE_IDX)) {
            emit("this::remove" + propertyName + ",");
        } else {
            emit("null,");
        }
        if (bmList == null || bmList.contains(BeanMethod.SIZE_OF_ARRAY)) {
            emit("this::sizeOf" + arrayName);
        } else {
            emit("null");
        }
        outdent();
        emit(");");

        emitImplementationPostamble();
        endBlock();
    }

    void printGetterImpls(SchemaProperty prop, Map<SchemaProperty, Identifier> propMap)
            throws IOException {
        final QName qName = prop.getName();
        final String identifier = propMap.get(prop).getIdentifier();
        final String setIdentifier = propMap.get(prop).getSetIdentifier();
        final boolean several = prop.extendsJavaArray();
        final boolean nillable = prop.hasNillable() != SchemaProperty.NEVER;
        final String type = javaTypeForProperty(prop);
        final String xtype = xmlTypeForProperty(prop);
        final int javaType = prop.getJavaTypeCode();
        final boolean isAttr = prop.isAttribute();
        final String propertyName = prop.getJavaPropertyName();
        String propertyDocumentation = prop.getDocumentation();

        String propdesc = "\"" + qName.getLocalPart() + "\"" + (isAttr ? " attribute" : " element");
        boolean xmltype = (javaType == SchemaProperty.XML_OBJECT);
        String jtargetType = (xmlTypeForPropertyIsUnion(prop) || !xmltype) ? "org.apache.xmlbeans.SimpleValue" : xtype;

        Set<BeanMethod> bmList = (opt == null) ? null : opt.getCompilePartialMethod();


        if (prop.extendsJavaSingleton()) {
            if (bmList == null || bmList.contains(BeanMethod.GET)) {
                // Value getProp()
                if(opt.isCompileAnnotationAsJavadoc() && propertyDocumentation != null && !propertyDocumentation.isEmpty()){
                    printJavaDocParagraph(propertyDocumentation);
                } else {
                    printJavaDoc((several ? "Gets first " : "Gets the ") + propdesc);
                }
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public " + type + " get" + propertyName + "() {");
                startBlock();
                emitImplementationPreamble();

                emitGetTarget(setIdentifier, identifier, isAttr, "0", NOTHING, jtargetType);

                if (isAttr && (prop.hasDefault() == SchemaProperty.CONSISTENTLY ||
                        prop.hasFixed() == SchemaProperty.CONSISTENTLY)) {
                    emit("if (target == null) {");
                    startBlock();
                    makeAttributeDefaultValue(jtargetType, prop, identifier);
                    endBlock();
                }

                emit("return (target == null) ? " + makeMissingValue(javaType) +
                        " : " + printJGetValue(javaType, type, (SchemaTypeImpl) prop.getType()) + ";");

                emitImplementationPostamble();

                endBlock();
            }

            if (!xmltype && (bmList == null || bmList.contains(BeanMethod.XGET))) {
                // Value xgetProp()
                printJavaDoc((several ? "Gets (as xml) first " : "Gets (as xml) the ") + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public " + xtype + " xget" + propertyName + "() {");
                startBlock();
                emitImplementationPreamble();
                emitGetTarget(setIdentifier, identifier, isAttr, "0", NOTHING, xtype);

                if (isAttr && (prop.hasDefault() == SchemaProperty.CONSISTENTLY ||
                        prop.hasFixed() == SchemaProperty.CONSISTENTLY)) {
                    emit("if (target == null) {");
                    startBlock();
                    makeAttributeDefaultValue(xtype, prop, identifier);
                    endBlock();
                }

                emit("return target;");
                emitImplementationPostamble();
                endBlock();
            }

            if (nillable && (bmList == null || bmList.contains(BeanMethod.IS_NIL))) {
                // boolean isNilProp()
                printJavaDoc((several ? "Tests for nil first " : "Tests for nil ") + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public boolean isNil" + propertyName + "() {");
                startBlock();
                emitImplementationPreamble();
                emitGetTarget(setIdentifier, identifier, isAttr, "0", NOTHING, xtype);

                emit("return target != null && target.isNil();");
                emitImplementationPostamble();
                endBlock();
            }
        }

        if (prop.extendsJavaOption() && (bmList == null || bmList.contains(BeanMethod.IS_SET))) {
            // boolean isSetProp()
            printJavaDoc((several ? "True if has at least one " : "True if has ") + propdesc);
            if (!opt.isCompileNoAnnotations()) {
                emit("@Override");
            }
            emit("public boolean isSet" + propertyName + "() {");

            startBlock();
            emitImplementationPreamble();

            if (isAttr) {
                emit("return get_store().find_attribute_user(" + identifier + ") != null;");
            } else {
                emit("return get_store().count_elements(" + setIdentifier + ") != 0;");
            }

            emitImplementationPostamble();
            endBlock();
        }

        if (several) {
            String arrayName = propertyName + "Array";

            // use boxed type if the java type is a primitive and jdk1.5
            // jdk1.5 will box/unbox for us
            String wrappedType = type;
            if (isJavaPrimitive(javaType)) {
                wrappedType = javaWrappedType(javaType);
            }

            printListGetterImpl(propdesc, propertyName, wrappedType, xmltype, false);

            if (bmList == null || bmList.contains(BeanMethod.GET_ARRAY)) {
                // Value[] getProp()
                printJavaDoc("Gets array of all " + propdesc + "s");
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public " + type + "[] get" + arrayName + "() {");
                startBlock();

                printJGetArrayValue(javaType, type, (SchemaTypeImpl) prop.getType(), setIdentifier);

                endBlock();
            }

            if (bmList == null || bmList.contains(BeanMethod.GET_IDX)) {
                // Value getProp(int i)
                printJavaDoc("Gets ith " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public " + type + " get" + arrayName + "(int i) {");
                startBlock();
                emitImplementationPreamble();

                emitGetTarget(setIdentifier, identifier, isAttr, "i", THROW_EXCEPTION, jtargetType);
                emit("return " + printJGetValue(javaType, type, (SchemaTypeImpl) prop.getType()) + ";");

                emitImplementationPostamble();
                endBlock();
            }

            if (!xmltype) {
                printListGetterImpl(propdesc, propertyName, xtype, false, true);
            }

            if (!xmltype && (bmList == null || bmList.contains(BeanMethod.XGET_ARRAY))) {
                // Value[] xgetProp()
                printJavaDoc("Gets (as xml) array of all " + propdesc + "s");
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public " + xtype + "[] xget" + arrayName + "() {");
                startBlock();
                emit("return xgetArray(" + setIdentifier + ", " + xtype + "[]::new);");
                endBlock();
            }

            if (!xmltype && (bmList == null || bmList.contains(BeanMethod.XGET_IDX))) {
                // Value xgetProp(int i)
                printJavaDoc("Gets (as xml) ith " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public " + xtype + " xget" + arrayName + "(int i) {");
                startBlock();
                emitImplementationPreamble();
                emitGetTarget(setIdentifier, identifier, isAttr, "i", THROW_EXCEPTION, xtype);
                emit("return target;");
                emitImplementationPostamble();
                endBlock();
            }

            if (nillable && (bmList == null || bmList.contains(BeanMethod.IS_NIL_IDX))) {
                // boolean isNil(int i);
                printJavaDoc("Tests for nil ith " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public boolean isNil" + arrayName + "(int i) {");
                startBlock();
                emitImplementationPreamble();
                emitGetTarget(setIdentifier, identifier, isAttr, "i", THROW_EXCEPTION, xtype);
                emit("return target.isNil();");
                emitImplementationPostamble();
                endBlock();
            }

            // int countProp();
            if (bmList == null || bmList.contains(BeanMethod.SIZE_OF_ARRAY)) {
                printJavaDoc("Returns number of " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public int sizeOf" + arrayName + "() {");
                startBlock();
                emitImplementationPreamble();
                emit("return get_store().count_elements(" + setIdentifier + ");");
                emitImplementationPostamble();
                endBlock();
            }
        }
    }

    void printSetterImpls(SchemaProperty prop, Map<SchemaProperty, Identifier> propMap, SchemaType sType)
            throws IOException {
        final QName qName = prop.getName();
        final String identifier = propMap.get(prop).getIdentifier();
        final String setIdentifier = propMap.get(prop).getSetIdentifier();
        final boolean several = prop.extendsJavaArray();
        final boolean nillable = prop.hasNillable() != SchemaProperty.NEVER;
        final String type = javaTypeForProperty(prop);
        final String xtype = xmlTypeForProperty(prop);
        final int javaType = prop.getJavaTypeCode();
        final boolean isAttr = prop.isAttribute();
        final String propertyName = prop.getJavaPropertyName();
        Set<BeanMethod> bmList = (opt == null) ? null : opt.getCompilePartialMethod();

        String safeVarName = NameUtil.nonJavaKeyword(NameUtil.lowerCamelCase(propertyName));
        safeVarName = NameUtil.nonExtraKeyword(safeVarName);

        boolean xmltype = (javaType == SchemaProperty.XML_OBJECT);
        boolean isobj = (javaType == SchemaProperty.JAVA_OBJECT);
        boolean isSubstGroup = !Objects.equals(identifier, setIdentifier);
        String jtargetType = (xmlTypeForPropertyIsUnion(prop) || !xmltype) ? "org.apache.xmlbeans.SimpleValue" : xtype;

        String propdesc = "\"" + qName.getLocalPart() + "\"" + (isAttr ? " attribute" : " element");

        if (prop.extendsJavaSingleton()) {
            if (bmList == null || bmList.contains(BeanMethod.SET)) {
                // void setProp(Value v);
                printJavaDoc((several ? "Sets first " : "Sets the ") + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public void set" + propertyName + "(" + type + " " + safeVarName + ") {");
                startBlock();
                if (xmltype && !isSubstGroup && !isAttr) {
                    emitPre(sType, PrePostExtension.OPERATION_SET, identifier, false, several ? "0" : "-1");
                    emit("generatedSetterHelperImpl(" + safeVarName + ", " + setIdentifier + ", 0, " +
                            "org.apache.xmlbeans.impl.values.XmlObjectBase.KIND_SETTERHELPER_SINGLETON);");
                    emitPost(sType, PrePostExtension.OPERATION_SET, identifier, false, several ? "0" : "-1");
                } else {
                    emitImplementationPreamble();
                    emitPre(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, several ? "0" : "-1");
                    emitGetTarget(setIdentifier, identifier, isAttr, "0", ADD_NEW_VALUE, jtargetType);
                    printJSetValue(javaType, safeVarName, (SchemaTypeImpl) prop.getType());
                    emitPost(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, several ? "0" : "-1");
                    emitImplementationPostamble();
                }
                endBlock();
            }

            if (!xmltype && (bmList == null || bmList.contains(BeanMethod.XSET))) {
                // void xsetProp(Value v)
                printJavaDoc((several ? "Sets (as xml) first " : "Sets (as xml) the ") + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public void xset" + propertyName + "(" + xtype + " " + safeVarName + ") {");
                startBlock();
                emitImplementationPreamble();
                emitPre(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, several ? "0" : "-1");
                emitGetTarget(setIdentifier, identifier, isAttr, "0", ADD_NEW_VALUE, xtype);
                emit("target.set(" + safeVarName + ");");
                emitPost(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, several ? "0" : "-1");
                emitImplementationPostamble();
                endBlock();

            }

            if (xmltype && !several && (bmList == null || bmList.contains(BeanMethod.ADD_NEW))) {
                // Value addNewProp()
                printJavaDoc("Appends and returns a new empty " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public " + xtype + " addNew" + propertyName + "() {");
                startBlock();
                emitImplementationPreamble();
                emit(xtype + " target = null;");
                emitPre(sType, PrePostExtension.OPERATION_INSERT, identifier, isAttr);
                emitAddTarget(identifier, isAttr, xtype);
                emitPost(sType, PrePostExtension.OPERATION_INSERT, identifier, isAttr);
                emit("return target;");
                emitImplementationPostamble();
                endBlock();
            }

            if (nillable && (bmList == null || bmList.contains(BeanMethod.SET_NIL))) {
                printJavaDoc((several ? "Nils the first " : "Nils the ") + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public void setNil" + propertyName + "() {");
                startBlock();
                emitImplementationPreamble();
                emitPre(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, several ? "0" : "-1");
                emitGetTarget(setIdentifier, identifier, isAttr, "0", ADD_NEW_VALUE, xtype);
                emit("target.setNil();");
                emitPost(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, several ? "0" : "-1");
                emitImplementationPostamble();
                endBlock();
            }
        }

        if (prop.extendsJavaOption() && (bmList == null || bmList.contains(BeanMethod.UNSET))) {
            printJavaDoc((several ? "Removes first " : "Unsets the ") + propdesc);
            if (!opt.isCompileNoAnnotations()) {
                emit("@Override");
            }
            emit("public void unset" + propertyName + "() {");
            startBlock();
            emitImplementationPreamble();
            emitPre(sType, PrePostExtension.OPERATION_REMOVE, identifier, isAttr, several ? "0" : "-1");
            if (isAttr) {
                emit("get_store().remove_attribute(" + identifier + ");");
            } else {
                emit("get_store().remove_element(" + setIdentifier + ", 0);");
            }
            emitPost(sType, PrePostExtension.OPERATION_REMOVE, identifier, isAttr, several ? "0" : "-1");
            emitImplementationPostamble();
            endBlock();
        }

        if (several) {
            String arrayName = propertyName + "Array";

            if (bmList == null || bmList.contains(BeanMethod.SET_ARRAY)) {
                if (xmltype) {
                    printJavaDoc("Sets array of all " + propdesc + "  WARNING: This method is not atomically synchronized.");
                    if (!opt.isCompileNoAnnotations()) {
                        emit("@Override");
                    }
                    emit("public void set" + arrayName + "(" + type + "[] " + safeVarName + "Array) {");
                    startBlock();
                    // do not use synchronize (monitor()) {  and GlobalLock inside  } !!! deadlock
                    //emitImplementationPreamble();
                    emit("check_orphaned();");
                    emitPre(sType, PrePostExtension.OPERATION_SET, identifier, isAttr);

                    if (isobj) {
                        if (!isSubstGroup) {
                            emit("unionArraySetterHelper(" + safeVarName + "Array" + ", " + identifier + ");");
                        } else {
                            emit("unionArraySetterHelper(" + safeVarName + "Array" + ", " + identifier + ", " + setIdentifier + ");");
                        }
                    } else {
                        if (!isSubstGroup) {
                            emit("arraySetterHelper(" + safeVarName + "Array" + ", " + identifier + ");");
                        } else {
                            emit("arraySetterHelper(" + safeVarName + "Array" + ", " + identifier + ", " + setIdentifier + ");");
                        }
                    }

                    emitPost(sType, PrePostExtension.OPERATION_SET, identifier, isAttr);
                    //emitImplementationPostamble();  to avoid deadlock
                    endBlock();
                } else {
                    printJavaDoc("Sets array of all " + propdesc);
                    if (!opt.isCompileNoAnnotations()) {
                        emit("@Override");
                    }
                    emit("public void set" + arrayName + "(" + type + "[] " + safeVarName + "Array) {");
                    startBlock();
                    emitImplementationPreamble();
                    emitPre(sType, PrePostExtension.OPERATION_SET, identifier, isAttr);

                    if (isobj) {
                        if (!isSubstGroup) {
                            emit("unionArraySetterHelper(" + safeVarName + "Array" + ", " + identifier + ");");
                        } else {
                            emit("unionArraySetterHelper(" + safeVarName + "Array" + ", " + identifier + ", " + setIdentifier + ");");
                        }
                    } else if (prop.getJavaTypeCode() == SchemaProperty.JAVA_USER) {
                        if (!isSubstGroup) {
                            emit("org.apache.xmlbeans.SimpleValue[] dests = arraySetterHelper(" + safeVarName + "Array.length" + ", " + identifier + ");");
                            emit("for ( int i = 0 ; i < dests.length ; i++ ) {");
                            emit("    " + getUserTypeStaticHandlerMethod(true, (SchemaTypeImpl) prop.getType())
                                    + "(" + safeVarName + "Array[i], dests[i]);");
                            emit("}");
                        } else {
                            emit("org.apache.xmlbeans.SimpleValue[] dests = arraySetterHelper(" + safeVarName + "Array.length" + ", " + identifier + ", " + setIdentifier + ");");
                            emit("for ( int i = 0 ; i < dests.length ; i++ ) {");
                            emit("    " + getUserTypeStaticHandlerMethod(true, (SchemaTypeImpl) prop.getType())
                                    + "(" + safeVarName + "Array[i], dests[i]);");
                            emit("}");
                        }
                    } else {
                        if (!isSubstGroup) {
                            emit("arraySetterHelper(" + safeVarName + "Array" + ", " + identifier + ");");
                        } else {
                            emit("arraySetterHelper(" + safeVarName + "Array" + ", " + identifier + ", " + setIdentifier + ");");
                        }
                    }

                    emitPost(sType, PrePostExtension.OPERATION_SET, identifier, isAttr);
                    emitImplementationPostamble();
                    endBlock();
                }
            }

            if (bmList == null || bmList.contains(BeanMethod.SET_IDX)) {
                printJavaDoc("Sets ith " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public void set" + arrayName + "(int i, " + type + " " + safeVarName + ") {");
                startBlock();
                if (xmltype && !isSubstGroup) {
                    emitPre(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, "i");
                    emit("generatedSetterHelperImpl(" + safeVarName + ", " + setIdentifier + ", i, " +
                            "org.apache.xmlbeans.impl.values.XmlObjectBase.KIND_SETTERHELPER_ARRAYITEM);");
                    emitPost(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, "i");
                } else {
                    emitImplementationPreamble();
                    emitPre(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, "i");
                    emitGetTarget(setIdentifier, identifier, isAttr, "i", THROW_EXCEPTION, jtargetType);
                    printJSetValue(javaType, safeVarName, (SchemaTypeImpl) prop.getType());
                    emitPost(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, "i");
                    emitImplementationPostamble();
                }
                endBlock();
            }

            if (!xmltype && (bmList == null || bmList.contains(BeanMethod.XSET_ARRAY))) {
                printJavaDoc("Sets (as xml) array of all " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public void xset" + arrayName + "(" + xtype + "[]" + safeVarName + "Array) {");
                startBlock();
                emitImplementationPreamble();
                emitPre(sType, PrePostExtension.OPERATION_SET, identifier, isAttr);
                emit("arraySetterHelper(" + safeVarName + "Array" + ", " + identifier + ");");
                emitPost(sType, PrePostExtension.OPERATION_SET, identifier, isAttr);
                emitImplementationPostamble();
                endBlock();
            }

            if (!xmltype && (bmList == null || bmList.contains(BeanMethod.XSET_IDX))) {
                printJavaDoc("Sets (as xml) ith " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public void xset" + arrayName + "(int i, " + xtype + " " + safeVarName + ") {");
                startBlock();
                emitImplementationPreamble();
                emitPre(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, "i");
                emitGetTarget(setIdentifier, identifier, isAttr, "i", THROW_EXCEPTION, xtype);
                emit("target.set(" + safeVarName + ");");
                emitPost(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, "i");
                emitImplementationPostamble();
                endBlock();
            }

            if (nillable && (bmList == null || bmList.contains(BeanMethod.SET_NIL_IDX))) {
                printJavaDoc("Nils the ith " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public void setNil" + arrayName + "(int i) {");
                startBlock();
                emitImplementationPreamble();
                emitPre(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, "i");
                emitGetTarget(setIdentifier, identifier, isAttr, "i", THROW_EXCEPTION, xtype);
                emit("target.setNil();");
                emitPost(sType, PrePostExtension.OPERATION_SET, identifier, isAttr, "i");
                emitImplementationPostamble();
                endBlock();
            }

            if (!xmltype && (bmList == null || bmList.contains(BeanMethod.INSERT_IDX))) {
                printJavaDoc("Inserts the value as the ith " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public void insert" + propertyName + "(int i, " + type + " " + safeVarName + ") {");
                startBlock();
                emitImplementationPreamble();
                emitPre(sType, PrePostExtension.OPERATION_INSERT, identifier, isAttr, "i");
                emit(jtargetType + " target =");
                indent();
                if (!isSubstGroup) {
                    emit("(" + jtargetType + ")get_store().insert_element_user(" + identifier + ", i);");
                } else // This is a subst group case
                {
                    emit("(" + jtargetType + ")get_store().insert_element_user(" + setIdentifier + ", " +
                            identifier + ", i);");
                }
                outdent();
                printJSetValue(javaType, safeVarName, (SchemaTypeImpl) prop.getType());
                emitPost(sType, PrePostExtension.OPERATION_INSERT, identifier, isAttr, "i");
                emitImplementationPostamble();
                endBlock();
            }

            if (!xmltype && (bmList == null || bmList.contains(BeanMethod.ADD))) {
                printJavaDoc("Appends the value as the last " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public void add" + propertyName + "(" + type + " " + safeVarName + ") {");
                startBlock();
                emitImplementationPreamble();
                emit(jtargetType + " target = null;");
                emitPre(sType, PrePostExtension.OPERATION_INSERT, identifier, isAttr);
                emitAddTarget(identifier, isAttr, jtargetType);
                printJSetValue(javaType, safeVarName, (SchemaTypeImpl) prop.getType());
                emitPost(sType, PrePostExtension.OPERATION_INSERT, identifier, isAttr);
                emitImplementationPostamble();
                endBlock();
            }

            if (bmList == null || bmList.contains(BeanMethod.INSERT_NEW_IDX)) {
                printJavaDoc("Inserts and returns a new empty value (as xml) as the ith " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public " + xtype + " insertNew" + propertyName + "(int i) {");
                startBlock();
                emitImplementationPreamble();
                emit(xtype + " target = null;");
                emitPre(sType, PrePostExtension.OPERATION_INSERT, identifier, isAttr, "i");
                if (!isSubstGroup) {
                    emit("target = (" + xtype + ")get_store().insert_element_user(" + identifier + ", i);");
                } else // This is a subst group case
                {
                    emit("target = (" + xtype + ")get_store().insert_element_user(" +
                            setIdentifier + ", " + identifier + ", i);");
                }
                emitPost(sType, PrePostExtension.OPERATION_INSERT, identifier, isAttr, "i");
                emit("return target;");
                emitImplementationPostamble();
                endBlock();
            }

            if (bmList == null || bmList.contains(BeanMethod.ADD_NEW)) {
                printJavaDoc("Appends and returns a new empty value (as xml) as the last " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public " + xtype + " addNew" + propertyName + "() {");
                startBlock();
                emitImplementationPreamble();
                emit(xtype + " target = null;");
                emitPre(sType, PrePostExtension.OPERATION_INSERT, identifier, isAttr);
                emitAddTarget(identifier, isAttr, xtype);
                emitPost(sType, PrePostExtension.OPERATION_INSERT, identifier, isAttr);
                emit("return target;");
                emitImplementationPostamble();
                endBlock();
            }

            if (bmList == null || bmList.contains(BeanMethod.REMOVE_IDX)) {
                printJavaDoc("Removes the ith " + propdesc);
                if (!opt.isCompileNoAnnotations()) {
                    emit("@Override");
                }
                emit("public void remove" + propertyName + "(int i) {");
                startBlock();
                emitImplementationPreamble();
                emitPre(sType, PrePostExtension.OPERATION_REMOVE, identifier, isAttr, "i");
                emit("get_store().remove_element(" + setIdentifier + ", i);");
                emitPost(sType, PrePostExtension.OPERATION_REMOVE, identifier, isAttr, "i");
                emitImplementationPostamble();
                endBlock();
            }
        }
    }

    SchemaProperty[] getSchemaProperties(SchemaType sType) {
        if (sType.getContentType() != SchemaType.SIMPLE_CONTENT) {
            // complex content type implementations derive from base type impls
            // so derived property impls can be reused
            return getDerivedProperties(sType);
        }

        // simple content types impls derive directly from "holder" impls
        // in order to handle the case (for ints or string enums e.g.) where
        // there is a simple type restriction.  So property getters need to
        // be implemented "from scratch" for each derived complex type
        // Moreover, attribute or element properties can be removed via restriction,
        // but we still need to implement them because this class is supposed to
        // also implement all the interfaces
        SchemaType baseType = sType.getBaseType();
        List<SchemaProperty> extraProperties = null;
        while (!baseType.isSimpleType() && !baseType.isBuiltinType()) {
            for (SchemaProperty baseProperty : baseType.getDerivedProperties()) {
                if (!(baseProperty.isAttribute() && sType.getAttributeProperty(baseProperty.getName()) != null)) {
                    if (extraProperties == null) {
                        extraProperties = new ArrayList<>();
                    }
                    extraProperties.add(baseProperty);
                }
            }
            baseType = baseType.getBaseType();
        }

        SchemaProperty[] properties = sType.getProperties();
        if (extraProperties == null) {
            return properties;
        }

        Collections.addAll(extraProperties, properties);
        return extraProperties.toArray(new SchemaProperty[0]);
    }

    void printInnerTypeImpl(
            SchemaType sType, SchemaTypeSystem system, boolean isInner) throws IOException {
        String shortName = sType.getShortJavaImplName();

        printInnerTypeJavaDoc(sType);

        startClass(sType, isInner);

        printConstructor(sType, shortName);

        printExtensionImplMethods(sType);

        if (!sType.isSimpleType()) {
            SchemaProperty[] properties = getSchemaProperties(sType);
            Map<SchemaProperty, Identifier> propMap = new HashMap<>();
            printStaticFields(properties, propMap);

            for (SchemaProperty prop : properties) {
                printGetterImpls(prop, propMap);

                if (!prop.isReadOnly()) {
                    printSetterImpls(prop, propMap, sType);
                }
            }
        }

        printNestedTypeImpls(sType, system);

        endBlock();
    }

    private SchemaProperty[] getDerivedProperties(SchemaType sType) {
        // We have to see if this is redefined, because if it is we have
        // to include all properties associated to its supertypes
        QName name = sType.getName();
        if (name != null && name.equals(sType.getBaseType().getName())) {
            SchemaType sType2 = sType.getBaseType();
            // Walk all the redefined types and record any properties
            // not present in sType, because the redefined types do not
            // have a generated class to represent them
            SchemaProperty[] props = sType.getDerivedProperties();
            Map<QName, SchemaProperty> propsByName = new LinkedHashMap<>();
            for (SchemaProperty prop : props) {
                propsByName.put(prop.getName(), prop);
            }
            while (sType2 != null && name.equals(sType2.getName())) {
                props = sType2.getDerivedProperties();
                for (SchemaProperty prop : props) {
                    if (!propsByName.containsKey(prop.getName())) {
                        propsByName.put(prop.getName(), prop);
                    }
                }
                sType2 = sType2.getBaseType();
            }
            return propsByName.values().toArray(new SchemaProperty[0]);
        } else {
            return sType.getDerivedProperties();
        }
    }

    private void printExtensionImplMethods(SchemaType sType) throws IOException {
        SchemaTypeImpl sImpl = getImpl(sType);
        if (sImpl == null) {
            return;
        }

        InterfaceExtension[] exts = sImpl.getInterfaceExtensions();
        if (exts != null) {
            for (InterfaceExtension ext : exts) {
                InterfaceExtension.MethodSignature[] methods = ext.getMethods();
                if (methods != null) {
                    for (InterfaceExtension.MethodSignature method : methods) {
                        printJavaDoc("Implementation method for interface " + ext.getStaticHandler());
                        printInterfaceMethodDecl(method);
                        startBlock();
                        printInterfaceMethodImpl(ext.getStaticHandler(), method);
                        endBlock();
                    }
                }
            }
        }
    }

    void printInterfaceMethodDecl(InterfaceExtension.MethodSignature method) throws IOException {
        StringBuilder decl = new StringBuilder(60);

        decl.append("public ").append(method.getReturnType());
        decl.append(' ').append(method.getName()).append('(');

        // first parameter is always XmlObject, i.e. which is "this" and therefore doesn't need
        // to be in the method declaration of the type implementation
        String[] paramTypes = method.getParameterTypes();
        String[] paramNames = method.getParameterNames();
        for (int i = 1; i < paramTypes.length; i++) {
            if (i > 1) {
                decl.append(", ");
            }
            decl.append(paramTypes[i]).append(' ').append(paramNames[i]);
        }

        decl.append(')');

        String[] exceptions = method.getExceptionTypes();
        for (int i = 0; i < exceptions.length; i++) {
            decl.append(i == 0 ? " throws " : ", ").append(exceptions[i]);
        }

        decl.append(" {");

        emit(decl.toString());
    }

    void printInterfaceMethodImpl(String handler, InterfaceExtension.MethodSignature method) throws IOException {
        StringBuilder impl = new StringBuilder(60);

        if (!method.getReturnType().equals("void")) {
            impl.append("return ");
        }

        impl.append(handler).append(".").append(method.getName()).append("(this");

        String[] params = method.getParameterTypes();
        String[] paramsNames = method.getParameterNames();
        for (int i = 1; i < params.length; i++) {
            impl.append(", ").append(paramsNames[i]);
        }

        impl.append(");");

        emit(impl.toString());
    }

    void printNestedTypeImpls(SchemaType sType, SchemaTypeSystem system) throws IOException {
        boolean redefinition = sType.getName() != null &&
                sType.getName().equals(sType.getBaseType().getName());
        while (sType != null) {
            SchemaType[] anonTypes = sType.getAnonymousTypes();
            for (SchemaType anonType : anonTypes) {
                if (anonType.isSkippedAnonymousType()) {
                    printNestedTypeImpls(anonType, system);
                } else {
                    printInnerTypeImpl(anonType, system, true);
                }
            }
            // For redefinition by extension, go ahead and print the anonymous
            // types in the base
            if (!redefinition ||
                    (sType.getDerivationType() != SchemaType.DT_EXTENSION && !sType.isSimpleType())) {
                break;
            }
            sType = sType.getBaseType();
        }
    }

    public void printHolder(Writer writer, SchemaTypeSystem system, XmlOptions opt, Repackager repackager) throws IOException {
        _writer = writer;

        String sysPack = system.getName();
        if (repackager != null) {
            sysPack = repackager.repackage(new StringBuffer(sysPack)).toString();
        }
        emit("package "+sysPack+";");
        emit("");
        emit("import org.apache.xmlbeans.impl.schema.SchemaTypeSystemImpl;");
        emit("");
        emit("public final class TypeSystemHolder extends SchemaTypeSystemImpl {");
        indent();
        emit("public static final TypeSystemHolder typeSystem = new TypeSystemHolder();");
        emit("");
        emit("private TypeSystemHolder() {");
        indent();
        emit("super(TypeSystemHolder.class);");
        outdent();
        emit("}");
        outdent();
        emit("}");
    }

    private static class Identifier {
        private final int getindex;
        private Integer setindex = null;

        private Identifier(int index) {
            this.getindex = index;
        }

        public String getIdentifier() {
            return "PROPERTY_QNAME[" + getindex + "]";
        }

        public String getSetIdentifier() {
            return setindex == null ? getIdentifier() : "PROPERTY_QSET["+ setindex + "]";
        }

        public void setSetIndex(int setindex) {
            this.setindex = setindex;
        }
    }
}
