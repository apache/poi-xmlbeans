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

package org.apache.xmlbeans;

import org.apache.xmlbeans.impl.store.Saaj;
import org.xml.sax.EntityResolver;
import org.xml.sax.XMLReader;

import javax.xml.namespace.QName;
import java.net.URI;
import java.util.*;

/**
 * Used to supply options for loading, saving, and compiling, and validating.
 * <p>
 * There are two styles for using XmlOptions: multiline setup, and single-line use.
 * Here are two examples.  First, multiline style:
 * <pre>{@code
 * XmlOptions opts = new XmlOptions();
 * opts.setSavePrettyPrint();
 * opts.setSavePrettyPrintIndent(4);
 * System.out.println(xobj.xmlText(opts));
 * }</pre>
 * <p>
 * The alternative is single-line usage:
 * <pre>{@code
 * System.out.println(xobj.xmlText(
 *     new XmlOptions().setSavePrettyPrint().setSavePrettyPrintIndent(4)));
 * }</pre>
 * <p>
 * Table showing where each option gets used.
 * Note that:
 * <ul>
 * <li>options available for {@code newInstance} methods will also
 * apply for {@code parse} methods</li>
 * <li>options used for {@code validate} methods are also used for
 * {@code compile} methods, since compilation usually implies
 * validation against Schema for Schemas</li>
 * </ul>
 *
 * <table border="1">
 * <caption>Option matrix</caption>
 * <tr>
 *   <th>{@code newInstance} methods</th>
 *   <th>{@code parse} methods</th>
 *   <th>{@code validate} methods</th>
 *   <th>{@code compile} methods</th>
 *   <th>{@code save} and {@code xmlText}methods</th>
 * </tr>
 * <tr>
 *   <td>{@code setDocumentType}<br>
 *       {@code setDocumentSourceName}<br>
 *       {@code setValidateOnSet}<br>
 *       {@code setUnsynchronized}</td>
 *   <td>{@code setLoad***}<br>
 *       {@code setEntityResolver}</td>
 *   <td>{@code setErrorListener}<br>
 *       {@code setValidateTreatLaxAsSkip}
 *       {@code setValidateStrict}</td>
 *   <td>{@code setErrorListener}<br>
 *       {@code setCompile***}<br>
 *       {@code setEntityResolver}<br>
 *       {@code setBaseURI}<br>
 *       {@code setGenerateJavaVersion}</td>
 *   <td>{@code setSave***}<br>
 *       {@code setUseDefaultNamespace}<br>
 *       {@code setCharacterEncoding}</td>
 * </tr>
 * </table>
 */
public class XmlOptions implements java.io.Serializable {
    //
    // Complete set of XmlOption's
    //

    // TODO - Add selectPath option to track the selection (default is to clean selections fast).
    public enum XmlOptionsKeys {
        SAVE_NAMESPACES_FIRST,
        SAVE_SYNTHETIC_DOCUMENT_ELEMENT,
        SAVE_PRETTY_PRINT,
        SAVE_PRETTY_PRINT_INDENT,
        SAVE_PRETTY_PRINT_OFFSET,
        SAVE_AGGRESSIVE_NAMESPACES,
        SAVE_USE_DEFAULT_NAMESPACE,
        SAVE_IMPLICIT_NAMESPACES,
        SAVE_SUGGESTED_PREFIXES,
        SAVE_FILTER_PROCINST,
        SAVE_USE_OPEN_FRAGMENT,
        SAVE_OUTER,
        SAVE_INNER,
        SAVE_NO_XML_DECL,
        SAVE_SUBSTITUTE_CHARACTERS,
        SAVE_OPTIMIZE_FOR_SPEED,
        SAVE_CDATA_LENGTH_THRESHOLD,
        SAVE_CDATA_ENTITY_COUNT_THRESHOLD,
        SAVE_SAX_NO_NSDECLS_IN_ATTRIBUTES,
        SAVE_NO_ATTRIBUTE_WHITESPACE_ESCAPE,
        LOAD_REPLACE_DOCUMENT_ELEMENT,
        LOAD_STRIP_WHITESPACE,
        LOAD_STRIP_COMMENTS,
        LOAD_STRIP_PROCINSTS,
        LOAD_LINE_NUMBERS,
        LOAD_LINE_NUMBERS_END_ELEMENT,
        LOAD_SAVE_CDATA_BOOKMARKS,
        LOAD_SUBSTITUTE_NAMESPACES,
        LOAD_TRIM_TEXT_BUFFER,
        LOAD_ADDITIONAL_NAMESPACES,
        LOAD_MESSAGE_DIGEST,
        LOAD_USE_DEFAULT_RESOLVER,
        LOAD_USE_XMLREADER,
        XQUERY_CURRENT_NODE_VAR,
        XQUERY_VARIABLE_MAP,
        CHARACTER_ENCODING,
        ERROR_LISTENER,
        DOCUMENT_TYPE,
        DOCUMENT_SOURCE_NAME,
        COMPILE_SUBSTITUTE_NAMES,
        COMPILE_NO_VALIDATION,
        COMPILE_NO_UPA_RULE,
        COMPILE_NO_PVR_RULE,
        COMPILE_NO_ANNOTATIONS,
        COMPILE_DOWNLOAD_URLS,
        COMPILE_MDEF_NAMESPACES,
        COMPILE_PARTIAL_TYPESYSTEM,
        COMPILE_PARTIAL_METHODS,
        COMPILE_ANNOTATION_JAVADOC,
        VALIDATE_ON_SET,
        VALIDATE_TREAT_LAX_AS_SKIP,
        VALIDATE_STRICT,
        VALIDATE_TEXT_ONLY,
        UNSYNCHRONIZED,
        ENTITY_RESOLVER,
        BASE_URI,
        SCHEMA_CODE_PRINTER,
        GENERATE_JAVA_VERSION,
        USE_SAME_LOCALE,
        COPY_USE_NEW_SYNC_DOMAIN,
        LOAD_ENTITY_BYTES_LIMIT,
        ENTITY_EXPANSION_LIMIT,
        LOAD_DTD_GRAMMAR,
        LOAD_EXTERNAL_DTD,
        DISALLOW_DOCTYPE_DECLARATION,
        SAAJ_IMPL,
        LOAD_USE_LOCALE_CHAR_UTIL,
        XPATH_USE_SAXON,
        XPATH_USE_XMLBEANS,
        ATTRIBUTE_VALIDATION_COMPAT_MODE,
        LOAD_STRICT_FLOATING_POINT,
        LOAD_ALLOW_DECIMAL_EXPONENT,
        MAX_NUMBER_CHARS
    }


    public static final int DEFAULT_ENTITY_EXPANSION_LIMIT = 2048;
    public static final int DEFAULT_MAX_NUMBER_CHARS = 1024;

    private static final XmlOptions EMPTY_OPTIONS;

    static {
        EMPTY_OPTIONS = new XmlOptions();
        EMPTY_OPTIONS._map = Collections.unmodifiableMap(EMPTY_OPTIONS._map);
    }


    private static final long serialVersionUID = 1L;

    private Map<XmlOptionsKeys, Object> _map = new HashMap<>();


    /**
     * Construct a new blank XmlOptions.
     */
    public XmlOptions() {
    }

    /**
     * Construct a new XmlOptions, copying the options.
     */
    public XmlOptions(XmlOptions other) {
        if (other != null) {
            _map.putAll(other._map);
        }
    }

    //
    // Handy-dandy helper methods for setting some options
    //

    /**
     * This option will cause the saver to save namespace attributes first.
     *
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSaveNamespacesFirst() {
        return setSaveNamespacesFirst(true);
    }

    public XmlOptions setSaveNamespacesFirst(boolean b) {
        return set(XmlOptionsKeys.SAVE_NAMESPACES_FIRST, b);
    }

    public boolean isSaveNamespacesFirst() {
        return hasOption(XmlOptionsKeys.SAVE_NAMESPACES_FIRST);
    }

    /**
     * This option will cause the saver to reformat white space for easier reading.
     *
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSavePrettyPrint() {
        return setSavePrettyPrint(true);
    }

    public XmlOptions setSavePrettyPrint(boolean b) {
        return set(XmlOptionsKeys.SAVE_PRETTY_PRINT, b);
    }

    public boolean isSavePrettyPrint() {
        return hasOption(XmlOptionsKeys.SAVE_PRETTY_PRINT);
    }


    /**
     * When used with {@code setSavePrettyPrint} this sets the indent
     * amount to use.
     *
     * @param indent the indent amount to use
     * @see #setSavePrettyPrint
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSavePrettyPrintIndent(int indent) {
        return set(XmlOptionsKeys.SAVE_PRETTY_PRINT_INDENT, indent);
    }

    public Integer getSavePrettyPrintIndent() {
        return (Integer) get(XmlOptionsKeys.SAVE_PRETTY_PRINT_INDENT);
    }

    /**
     * When used with {@code setSavePrettyPrint} this sets the offset
     * amount to use.
     *
     * @param offset the offset amount to use
     * @see #setSavePrettyPrint
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSavePrettyPrintOffset(int offset) {
        return set(XmlOptionsKeys.SAVE_PRETTY_PRINT_OFFSET, offset);
    }

    public Integer getSavePrettyPrintOffset() {
        return (Integer) get(XmlOptionsKeys.SAVE_PRETTY_PRINT_OFFSET);
    }

    /**
     * When writing a document, this sets the character
     * encoding to use.
     *
     * @param encoding the character encoding
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     */
    public XmlOptions setCharacterEncoding(String encoding) {
        return set(XmlOptionsKeys.CHARACTER_ENCODING, encoding);
    }

    public String getCharacterEncoding() {
        return (String) get(XmlOptionsKeys.CHARACTER_ENCODING);
    }

    /**
     * When parsing a document, this sets the type of the root
     * element. If this is set, the parser will not try to guess
     * the type based on the document's {@code QName}.
     *
     * @param type The root element's document type.
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setDocumentType(SchemaType type) {
        return set(XmlOptionsKeys.DOCUMENT_TYPE, type);
    }

    public SchemaType getDocumentType() {
        return (SchemaType) get(XmlOptionsKeys.DOCUMENT_TYPE);
    }


    /**
     * <p>Sets a collection object for collecting {@link XmlError} objects
     * during parsing, validation, and compilation. When set, the collection
     * will contain all the errors after the operation takes place.  Notice that
     * the errors will only have line numbers if the document was
     * loaded with line numbers enabled.</p>
     *
     * <p>The following simple example illustrates using an error listener
     * during validation.</p>
     *
     * <pre>{@code
     * // Create an XmlOptions instance and set the error listener.
     * XmlOptions validateOptions = new XmlOptions();
     * ArrayList<XmlError> errorList = new ArrayList<>();
     * validateOptions.setErrorListener(errorList);
     *
     * // Validate the XML.
     * boolean isValid = newEmp.validate(validateOptions);
     *
     * // If the XML isn't valid, loop through the listener's contents,
     * // printing contained messages.
     * if (!isValid)
     * {
     *      for (int i = 0; i < errorList.size(); i++)
     *      {
     *          XmlError error = (XmlError)errorList.get(i);
     *
     *          System.out.println("\n");
     *          System.out.println("Message: " + error.getMessage() + "\n");
     *          System.out.println("Location of invalid XML: " +
     *              error.getCursorLocation().xmlText() + "\n");
     *      }
     * }
     * }</pre>
     *
     * @param c A collection that will be filled with {@link XmlError} objects
     *          via {@link Collection#add}
     * @see XmlError
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     * @see XmlObject#validate(XmlOptions)
     * @see XmlBeans#compileXsd
     * @see XmlOptions#setLoadLineNumbers
     */
    public XmlOptions setErrorListener(Collection<XmlError> c) {
        return set(XmlOptionsKeys.ERROR_LISTENER, c);
    }

    @SuppressWarnings("unchecked")
    public Collection<XmlError> getErrorListener() {
        return (Collection<XmlError>) get(XmlOptionsKeys.ERROR_LISTENER);
    }

    /**
     * Causes the saver to reduce the number of namespace prefix declarations.
     * The saver will do this by passing over the document twice, first to
     * collect the set of needed namespace declarations, and then second
     * to actually save the document with the declarations collected
     * at the root.
     *
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSaveAggressiveNamespaces() {
        return setSaveAggressiveNamespaces(true);
    }

    public XmlOptions setSaveAggressiveNamespaces(boolean b) {
        return set(XmlOptionsKeys.SAVE_AGGRESSIVE_NAMESPACES, b);
    }

    public boolean isSaveAggressiveNamespaces() {
        return hasOption(XmlOptionsKeys.SAVE_AGGRESSIVE_NAMESPACES);
    }


    /**
     * This option causes the saver to wrap the current fragment in
     * an element with the given name.
     *
     * @param name the name to use for the top level element
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSaveSyntheticDocumentElement(QName name) {
        return set(XmlOptionsKeys.SAVE_SYNTHETIC_DOCUMENT_ELEMENT, name);
    }

    public QName getSaveSyntheticDocumentElement() {
        return (QName) get(XmlOptionsKeys.SAVE_SYNTHETIC_DOCUMENT_ELEMENT);
    }


    /**
     * If this option is set, the saver will try to use the default
     * namespace for the most commonly used URI. If it is not set
     * the saver will always created named prefixes.
     *
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setUseDefaultNamespace() {
        return setUseDefaultNamespace(true);
    }

    public XmlOptions setUseDefaultNamespace(boolean b) {
        return set(XmlOptionsKeys.SAVE_USE_DEFAULT_NAMESPACE, b);
    }

    public boolean isUseDefaultNamespace() {
        return hasOption(XmlOptionsKeys.SAVE_USE_DEFAULT_NAMESPACE);
    }

    /**
     * If namespaces have already been declared outside the scope of the
     * fragment being saved, this allows those mappings to be passed
     * down to the saver, so the prefixes are not re-declared.
     *
     * @param implicitNamespaces a map of prefixes to uris that can be
     *                           used by the saver without being declared
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSaveImplicitNamespaces(Map<String, String> implicitNamespaces) {
        return set(XmlOptionsKeys.SAVE_IMPLICIT_NAMESPACES, implicitNamespaces);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getSaveImplicitNamespaces() {
        return (Map<String, String>) get(XmlOptionsKeys.SAVE_IMPLICIT_NAMESPACES);
    }

    /**
     * A map of hints to pass to the saver for which prefixes to use
     * for which namespace URI.
     *
     * @param suggestedPrefixes a map from URIs to prefixes
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSaveSuggestedPrefixes(Map<String, String> suggestedPrefixes) {
        return set(XmlOptionsKeys.SAVE_SUGGESTED_PREFIXES, suggestedPrefixes);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getSaveSuggestedPrefixes() {
        return (Map<String, String>) get(XmlOptionsKeys.SAVE_SUGGESTED_PREFIXES);
    }

    /**
     * This option causes the saver to filter a Processing Instruction
     * with the given target
     *
     * @param filterProcinst the name of a Processing Instruction to filter
     *                       on save
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSaveFilterProcinst(String filterProcinst) {
        return set(XmlOptionsKeys.SAVE_FILTER_PROCINST, filterProcinst);
    }

    public String getSaveFilterProcinst() {
        return (String) get(XmlOptionsKeys.SAVE_FILTER_PROCINST);
    }

    /**
     * This option causes the saver to replace characters with other values in
     * the output stream.  It is intended to be used for escaping non-standard
     * characters during output.
     *
     * @param characterReplacementMap is an XmlOptionCharEscapeMap containing
     *                                the characters to be escaped.
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     * @see XmlOptionCharEscapeMap
     */
    public XmlOptions setSaveSubstituteCharacters(
        XmlOptionCharEscapeMap characterReplacementMap) {
        return set(XmlOptionsKeys.SAVE_SUBSTITUTE_CHARACTERS, characterReplacementMap);
    }

    public XmlOptionCharEscapeMap getSaveSubstituteCharacters() {
        return (XmlOptionCharEscapeMap) get(XmlOptionsKeys.SAVE_SUBSTITUTE_CHARACTERS);
    }

    /**
     * When saving a fragment, this option changes the qname of the synthesized
     * root element.  Normally &lt;xml-fragment&gt; is used.
     *
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSaveUseOpenFrag() {
        return setSaveUseOpenFrag(true);
    }

    public XmlOptions setSaveUseOpenFrag(boolean b) {
        return set(XmlOptionsKeys.SAVE_USE_OPEN_FRAGMENT, b);
    }

    public boolean isSaveUseOpenFrag() {
        return hasOption(XmlOptionsKeys.SAVE_USE_OPEN_FRAGMENT);
    }

    /**
     * This option controls whether saving begins on the element or its contents
     *
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSaveOuter() {
        return setSaveOuter(true);
    }

    public XmlOptions setSaveOuter(boolean b) {
        return set(XmlOptionsKeys.SAVE_OUTER, b);
    }

    public boolean isSaveOuter() {
        return hasOption(XmlOptionsKeys.SAVE_OUTER);
    }

    /**
     * This option controls whether saving begins on the element or its contents
     *
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSaveInner() {
        return setSaveInner(true);
    }

    public XmlOptions setSaveInner(boolean b) {
        return set(XmlOptionsKeys.SAVE_INNER, b);
    }

    public boolean isSaveInner() {
        return hasOption(XmlOptionsKeys.SAVE_INNER);
    }

    /**
     * This option controls whether saving saves out the XML
     * declaration {@code <?xml ... ?>}
     *
     * @see XmlTokenSource#save(java.io.File, XmlOptions)
     * @see XmlTokenSource#xmlText(XmlOptions)
     */
    public XmlOptions setSaveNoXmlDecl() {
        return setSaveNoXmlDecl(true);
    }

    public XmlOptions setSaveNoXmlDecl(boolean b) {
        return set(XmlOptionsKeys.SAVE_NO_XML_DECL, b);
    }

    public boolean isSaveNoXmlDecl() {
        return hasOption(XmlOptionsKeys.SAVE_NO_XML_DECL);
    }

    /**
     * By default the saver now escapes a tab ({@code #x9}), newline ({@code #xA})
     * and carriage return ({@code #xD}) inside an attribute value as a character
     * reference ({@code &#9;}, {@code &#10;}, {@code &#13;}). Without that, the
     * literal characters are normalised to spaces when the document is read back
     * in, so a save followed by a load silently rewrites the value. Set this
     * option to restore the long-standing behaviour of writing those characters
     * literally.
     *
     * @return this
     * @since 5.4.0
     */
    public XmlOptions setSaveNoAttributeWhitespaceEscape() {
        return setSaveNoAttributeWhitespaceEscape(true);
    }

    /**
     * Sets whether tab, newline and carriage return are written literally in
     * attribute values instead of being escaped as character references. See
     * {@link #setSaveNoAttributeWhitespaceEscape()}.
     *
     * @param b {@code true} to write those characters literally (pre-5.4.0 behaviour)
     * @return this
     * @since 5.4.0
     */
    public XmlOptions setSaveNoAttributeWhitespaceEscape(boolean b) {
        return set(XmlOptionsKeys.SAVE_NO_ATTRIBUTE_WHITESPACE_ESCAPE, b);
    }

    /**
     * Returns whether tab, newline and carriage return are written literally in
     * attribute values instead of being escaped. See
     * {@link #setSaveNoAttributeWhitespaceEscape()}.
     *
     * @return {@code true} if those characters are written literally
     * @since 5.4.0
     */
    public boolean isSaveNoAttributeWhitespaceEscape() {
        return hasOption(XmlOptionsKeys.SAVE_NO_ATTRIBUTE_WHITESPACE_ESCAPE);
    }


    /**
     * This option controls when saving will use CDATA blocks.
     * CDATA will be used if the folowing condition is true:
     * <br>{@code textLength > cdataLengthThreshold && entityCount > cdataEntityCountThreshold}
     * <br>The default value of cdataLengthThreshold is 32.
     * <br>
     * <br>Use the folowing values for these cases:
     * <table border=1>
     * <caption>Option matrix</caption>
     * <tr><th>Scenario</th> <th>cdataLengthThreshold</th> <th>cdataEntityCountThreshold</th></tr>
     * <tr><td>Every text is CDATA</td> <td>0</td> <td>-1</td></tr>
     * <tr><td>Only text that has an entity is CDATA</td> <td>0</td> <td>0</td></tr>
     * <tr><td>Only text longer than x chars is CDATA</td> <td>x</td> <td>-1</td></tr>
     * <tr><td>Only text that has y entitazable chars is CDATA</td> <td>0</td> <td>y</td></tr>
     * <tr><td>Only text longer than x chars and has y entitazable chars is CDATA</td> <td>x</td> <td>y</td></tr>
     * </table>
     *
     * @see XmlOptions#setSaveCDataEntityCountThreshold(int)
     */
    public XmlOptions setSaveCDataLengthThreshold(int cdataLengthThreshold) {
        return set(XmlOptionsKeys.SAVE_CDATA_LENGTH_THRESHOLD, cdataLengthThreshold);
    }

    public Integer getSaveCDataLengthThreshold() {
        return (Integer) get(XmlOptionsKeys.SAVE_CDATA_LENGTH_THRESHOLD);
    }


    /**
     * This option controls when saving will use CDATA blocks.
     * CDATA will be used if the folowing condition is true:
     * <br>{@code textLength > cdataLengthThreshold && entityCount > cdataEntityCountThreshold}
     * <br>The default value of cdataEntityCountThreshold is 5.
     *
     * @see XmlOptions#setSaveCDataLengthThreshold(int)
     */
    public XmlOptions setSaveCDataEntityCountThreshold(int cdataEntityCountThreshold) {
        return set(XmlOptionsKeys.SAVE_CDATA_ENTITY_COUNT_THRESHOLD, cdataEntityCountThreshold);
    }

    public Integer getSaveCDataEntityCountThreshold() {
        return (Integer) get(XmlOptionsKeys.SAVE_CDATA_ENTITY_COUNT_THRESHOLD);
    }

    /**
     * <p>Use this option when parsing and saving XML documents.</p>
     *
     * <p>For parsing this option will annotate the text fields in the store with CDataBookmark.</p>
     *
     * <p>For saving this option will save the text fields annotated with CDataBookmark as
     * CDATA XML text.<br>
     * Note: The SaveCDataEntityCountThreshold and SaveCDataLengthThreshold options and
     * their default values still apply.</p>
     *
     * <p><b>Note: Due to the store representation, a CDATA will not be recognized
     * if it is imediately after non CDATA text and all text following it will
     * be considered CDATA.</b><br>
     * Example:<br>
     * <pre>{@code
     * <a><![CDATA[cdata text]]></a>               - is considered as: <a><![CDATA[cdata text]]></a>
     * <b><![CDATA[cdata text]]> regular text</b>  - is considered as: <b><![CDATA[cdata text regular text]]></b>
     * <c>text <![CDATA[cdata text]]></c>          - is considered as: <c>text cdata text</c>
     * }</pre>
     *
     * <p>Sample code:
     * <pre>{@code
     * String xmlText = "<a>\n" +
     * "<a><![CDATA[cdata text]]></a>\n" +
     * "<b><![CDATA[cdata text]]> regular text</b>\n" +
     * "<c>text <![CDATA[cdata text]]></c>\n" +
     * "</a>";
     * System.out.println(xmlText);
     *
     * XmlOptions opts = new XmlOptions();
     * opts.setUseCDataBookmarks();
     *
     * XmlObject xo = org.apache.xmlbeans.impl.schema.XmlObjectFactory.parse( xmlText , opts);
     *
     * System.out.println("xo1:\n" + xo.xmlText(opts));
     * System.out.println("\n");
     *
     * opts.setSavePrettyPrint();
     * System.out.println("xo2:\n" + xo.xmlText(opts));
     * }</pre>
     *
     * @see CDataBookmark
     * @see CDataBookmark#CDATA_BOOKMARK
     */
    public XmlOptions setUseCDataBookmarks() {
        return set(XmlOptionsKeys.LOAD_SAVE_CDATA_BOOKMARKS);
    }

    public boolean isUseCDataBookmarks() {
        return hasOption(XmlOptionsKeys.LOAD_SAVE_CDATA_BOOKMARKS);
    }

    /**
     * This option controls whether namespace declarations are included as attributes in the
     * startElement event. By default, up to and including XMLBeans 2.3.0 they were included, in
     * subsequent versions, they are no longer included.
     */
    public XmlOptions setSaveSaxNoNSDeclsInAttributes() {
        return setSaveSaxNoNSDeclsInAttributes(true);
    }

    public XmlOptions setSaveSaxNoNSDeclsInAttributes(boolean b) {
        return set(XmlOptionsKeys.SAVE_SAX_NO_NSDECLS_IN_ATTRIBUTES, b);
    }

    public boolean isSaveSaxNoNSDeclsInAttributes() {
        return hasOption(XmlOptionsKeys.SAVE_SAX_NO_NSDECLS_IN_ATTRIBUTES);
    }


    /**
     * If this option is set, the document element is replaced with the
     * given QName when parsing.  If null is supplied, the document element
     * is removed.
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setLoadReplaceDocumentElement(QName replacement) {
        return set(XmlOptionsKeys.LOAD_REPLACE_DOCUMENT_ELEMENT, replacement);
    }

    public QName getLoadReplaceDocumentElement() {
        return (QName) get(XmlOptionsKeys.LOAD_REPLACE_DOCUMENT_ELEMENT);
    }

    /**
     * If this option is set, all insignificant whitespace is stripped
     * when parsing a document.  Can be used to save memory on large
     * documents when you know there is no mixed content.
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setLoadStripWhitespace() {
        return setLoadStripWhitespace(true);
    }

    public XmlOptions setLoadStripWhitespace(boolean b) {
        return set(XmlOptionsKeys.LOAD_STRIP_WHITESPACE, b);
    }

    public boolean isSetLoadStripWhitespace() {
        return hasOption(XmlOptionsKeys.LOAD_STRIP_WHITESPACE);
    }

    /**
     * If this option is set, all comments are stripped when parsing
     * a document.
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setLoadStripComments() {
        return setLoadStripComments(true);
    }

    public XmlOptions setLoadStripComments(boolean b) {
        return set(XmlOptionsKeys.LOAD_STRIP_COMMENTS, b);
    }

    public boolean isLoadStripComments() {
        return hasOption(XmlOptionsKeys.LOAD_STRIP_COMMENTS);
    }

    /**
     * If this option is set, all processing instructions
     * are stripped when parsing a document.
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setLoadStripProcinsts() {
        return setLoadStripProcinsts(true);
    }

    public XmlOptions setLoadStripProcinsts(boolean b) {
        return set(XmlOptionsKeys.LOAD_STRIP_PROCINSTS, b);
    }

    public boolean isLoadStripProcinsts() {
        return hasOption(XmlOptionsKeys.LOAD_STRIP_PROCINSTS);
    }

    /**
     * If this option is set, line number annotations are placed
     * in the store when parsing a document.  This is particularly
     * useful when you want {@link XmlError} objects to contain
     * line numbers.
     * <br>Note: This adds line numbers info only for start tags.
     * For line number info on end tags use:
     * {@link XmlOptions#setLoadLineNumbersEndElement()}
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     * @see XmlError
     */
    public XmlOptions setLoadLineNumbers() {
        return setLoadLineNumbers(true);
    }

    public XmlOptions setLoadLineNumbers(boolean b) {
        return set(XmlOptionsKeys.LOAD_LINE_NUMBERS, b);
    }

    public boolean isLoadLineNumbers() {
        return hasOption(XmlOptionsKeys.LOAD_LINE_NUMBERS);
    }


    /**
     * If this option is set, line number annotations are placed
     * in the store when parsing a document.  This is particularly
     * useful when you want {@link XmlError} objects to contain
     * line numbers. Use the option to load line numbers at the end of an element.
     */
    public XmlOptions setLoadLineNumbersEndElement() {
        return setLoadLineNumbersEndElement(true);
    }

    public XmlOptions setLoadLineNumbersEndElement(boolean b) {
        setLoadLineNumbers(true);
        return set(XmlOptionsKeys.LOAD_LINE_NUMBERS_END_ELEMENT, b);
    }

    public boolean isLoadLineNumbersEndElement() {
        return hasOption(XmlOptionsKeys.LOAD_LINE_NUMBERS_END_ELEMENT);
    }

    /**
     * This option sets a map of namespace uri substitutions that happen
     * when parsing a document.
     * <p>
     * This is particularly useful if you
     * have documents that use no namespace, but you wish to avoid
     * the name collision problems that occur when you introduce
     * schema definitions without a target namespace.
     * <p>
     * By mapping the empty string "" (the absence of a URI) to a specific
     * namespace, you can force the parser to behave as if a no-namespace
     * document were actually in the specified namespace. This allows you
     * to type the instance according to a schema in a nonempty namespace,
     * and therefore avoid the problematic practice of using schema
     * definitions without a target namespace.
     *
     * @param substNamespaces a map of document URIs to replacement URIs
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setLoadSubstituteNamespaces(Map<String, String> substNamespaces) {
        return set(XmlOptionsKeys.LOAD_SUBSTITUTE_NAMESPACES, substNamespaces);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getLoadSubstituteNamespaces() {
        return (Map<String, String>) get(XmlOptionsKeys.LOAD_SUBSTITUTE_NAMESPACES);
    }

    /**
     * If this option is set, the underlying xml text buffer is trimmed
     * immediately after parsing a document resulting in a smaller memory
     * footprint.  Use this option if you are loading a large number
     * of unchanging documents that will stay in memory for some time.
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setLoadTrimTextBuffer() {
        return setLoadTrimTextBuffer(true);
    }

    public XmlOptions setLoadTrimTextBuffer(boolean b) {
        return set(XmlOptionsKeys.LOAD_TRIM_TEXT_BUFFER, b);
    }

    public boolean isLoadTrimTextBuffer() {
        return hasOption(XmlOptionsKeys.LOAD_TRIM_TEXT_BUFFER);
    }

    /**
     * Set additional namespace mappings to be added when parsing
     * a document.
     *
     * @param nses additional namespace mappings
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setLoadAdditionalNamespaces(Map<String, String> nses) {
        return set(XmlOptionsKeys.LOAD_ADDITIONAL_NAMESPACES, nses);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getLoadAdditionalNamespaces() {
        return (Map<String, String>) get(XmlOptionsKeys.LOAD_ADDITIONAL_NAMESPACES);
    }

    /**
     * If this option is set when loading from an InputStream or File, then
     * the loader will compute a 160-bit SHA-1 message digest of the XML
     * file while loading it and make it available via
     * XmlObject.documentProperties().getMessageDigest();
     * <br>
     * The schema compiler uses message digests to detect and eliminate
     * duplicate imported xsd files.
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setLoadMessageDigest() {
        return setLoadMessageDigest(true);
    }

    public XmlOptions setLoadMessageDigest(boolean b) {
        return set(XmlOptionsKeys.LOAD_MESSAGE_DIGEST, b);
    }

    public boolean isLoadMessageDigest() {
        return hasOption(XmlOptionsKeys.LOAD_MESSAGE_DIGEST);
    }

    /**
     * By default, XmlBeans does not resolve entities when parsing xml
     * documents (unless an explicit entity resolver is specified).
     * Use this option to turn on entity resolving by default.
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setLoadUseDefaultResolver() {
        return setLoadUseDefaultResolver(true);
    }

    public XmlOptions setLoadUseDefaultResolver(boolean b) {
        return set(XmlOptionsKeys.LOAD_USE_DEFAULT_RESOLVER, b);
    }

    public boolean isLoadUseDefaultResolver() {
        return hasOption(XmlOptionsKeys.LOAD_USE_DEFAULT_RESOLVER);
    }


    /**
     * By default, XmlBeans creates a JAXP parser,
     * other parsers can be used by providing an XMLReader.
     * For using the default JDK's SAX parser use:
     * xmlOptions.setLoadUseXMLReader( SAXParserFactory.newInstance().newSAXParser().getXMLReader() );
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setLoadUseXMLReader(XMLReader xmlReader) {
        return set(XmlOptionsKeys.LOAD_USE_XMLREADER, xmlReader);
    }

    public XMLReader getLoadUseXMLReader() {
        return (XMLReader) get(XmlOptionsKeys.LOAD_USE_XMLREADER);
    }

    /**
     * Sets the name of the variable that represents
     * the current node in a query expression.
     *
     * @param varName The new variable name to use for the query.
     * @see XmlObject#execQuery
     * @see XmlCursor#execQuery
     */
    public XmlOptions setXqueryCurrentNodeVar(String varName) {
        return set(XmlOptionsKeys.XQUERY_CURRENT_NODE_VAR, varName);
    }

    public String getXqueryCurrentNodeVar() {
        return (String) get(XmlOptionsKeys.XQUERY_CURRENT_NODE_VAR);
    }

    /**
     * Map the names and values of external variables in an xquery
     * expression.  The keys of the map are the variable names
     * in the query without the '$' prefix.  The values of the map
     * are objects and can be any of the primitive wrapper classes,
     * String, XmlObject, or XmlCursor. The mapping only applies to
     * xquery and has no effect on xpath expressions.
     *
     * @param varMap a map from Strings to variable instances.
     * @see XmlObject#execQuery
     * @see XmlCursor#execQuery
     */
    public XmlOptions setXqueryVariables(Map<String, Object> varMap) {
        return set(XmlOptionsKeys.XQUERY_VARIABLE_MAP, varMap);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getXqueryVariables() {
        return (Map<String, Object>) get(XmlOptionsKeys.XQUERY_VARIABLE_MAP);
    }

    /**
     * This option sets the document source name into the xml store
     * when parsing a document.  If a document is parsed from a
     * File or URI, it is automatically set to the URI of the
     * source; otherwise, for example, when parsing a String,
     * you can use this option to specify the source name yourself.
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.lang.String, XmlOptions)
     */
    public XmlOptions setDocumentSourceName(String documentSourceName) {
        return set(XmlOptionsKeys.DOCUMENT_SOURCE_NAME, documentSourceName);
    }

    public String getDocumentSourceName() {
        return (String) get(XmlOptionsKeys.DOCUMENT_SOURCE_NAME);
    }

    /**
     * This option allows for {@code QName} substitution during schema compilation.
     *
     * @param nameMap a map from {@code QName}s to substitute {@code QName}s.
     * @see XmlBeans#compileXsd
     */
    public XmlOptions setCompileSubstituteNames(Map<QName, QName> nameMap) {
        return set(XmlOptionsKeys.COMPILE_SUBSTITUTE_NAMES, nameMap);
    }

    @SuppressWarnings("unchecked")
    public Map<QName, QName> getCompileSubstituteNames() {
        return (Map<QName, QName>) get(XmlOptionsKeys.COMPILE_SUBSTITUTE_NAMES);
    }

    /**
     * If this option is set, validation is not done on the Schema XmlBeans
     * when building a {@code SchemaTypeSystem}
     *
     * @see XmlBeans#compileXsd
     */
    public XmlOptions setCompileNoValidation() {
        return set(XmlOptionsKeys.COMPILE_NO_VALIDATION);
    }

    public boolean isCompileNoValidation() {
        return hasOption(XmlOptionsKeys.COMPILE_NO_VALIDATION);
    }

    /**
     * If this option is set, the unique particle attribution rule is not
     * enforced when building a {@code SchemaTypeSystem}. See
     * <a target="_blank" href="http://www.w3.org/TR/xmlschema-1/#non-ambig">Appendix H of the XML Schema specification</a>
     * for information on the UPA rule.
     *
     * @see XmlBeans#compileXsd
     */
    public XmlOptions setCompileNoUpaRule() {
        return setCompileNoUpaRule(true);
    }

    public XmlOptions setCompileNoUpaRule(boolean b) {
        return set(XmlOptionsKeys.COMPILE_NO_UPA_RULE, b);
    }

    public boolean isCompileNoUpaRule() {
        return hasOption(XmlOptionsKeys.COMPILE_NO_UPA_RULE);
    }

    /**
     * If this option is set, the particle valid (restriciton) rule is not
     * enforced when building a {@code SchemaTypeSystem}. See
     * <a target="_blank" href="http://www.w3.org/TR/xmlschema-1/#cos-particle-restrict">Section 3.9.6 of the XML Schema specification</a>
     * for information on the PVR rule.
     *
     * @see XmlBeans#compileXsd
     */
    public XmlOptions setCompileNoPvrRule() {
        return setCompileNoPvrRule(true);
    }

    public XmlOptions setCompileNoPvrRule(boolean b) {
        return set(XmlOptionsKeys.COMPILE_NO_PVR_RULE, b);
    }

    public boolean isCompileNoPvrRule() {
        return hasOption(XmlOptionsKeys.COMPILE_NO_PVR_RULE);
    }

    /**
     * if this option is set, the schema compiler will skip annotations when
     * processing Schema components.
     *
     * @see XmlBeans#compileXsd
     */
    public XmlOptions setCompileNoAnnotations() {
        return setCompileNoAnnotations(true);
    }

    public XmlOptions setCompileNoAnnotations(boolean b) {
        return set(XmlOptionsKeys.COMPILE_NO_ANNOTATIONS, b);
    }

    public boolean isCompileNoAnnotations() {
        return hasOption(XmlOptionsKeys.COMPILE_NO_ANNOTATIONS);
    }

    /**
     * If this option is set, then the schema compiler will try to download
     * schemas that appear in imports and includes from network based URLs.
     *
     * @see XmlBeans#compileXsd
     */
    public XmlOptions setCompileDownloadUrls() {
        return setCompileDownloadUrls(true);
    }

    public XmlOptions setCompileDownloadUrls(boolean b) {
        return set(XmlOptionsKeys.COMPILE_DOWNLOAD_URLS, b);
    }

    public boolean isCompileDownloadUrls() {
        return hasOption(XmlOptionsKeys.COMPILE_DOWNLOAD_URLS);
    }

    /**
     * If this option is set, then the schema compiler will permit and
     * ignore multiple definitions of the same component (element, attribute,
     * type, etc) names in the given namespaces.  If multiple definitions
     * with the same name appear, the definitions that happen to be processed
     * last will be ignored.
     *
     * @param mdefNamespaces a set of namespace URIs as Strings
     * @see XmlBeans#compileXsd
     */
    public XmlOptions setCompileMdefNamespaces(Set<String> mdefNamespaces) {
        return set(XmlOptionsKeys.COMPILE_MDEF_NAMESPACES, mdefNamespaces);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getCompileMdefNamespaces() {
        return (Set<String>) get(XmlOptionsKeys.COMPILE_MDEF_NAMESPACES);
    }

    public XmlOptions setCompilePartialTypesystem() {
        return setCompilePartialTypesystem(true);
    }

    public XmlOptions setCompilePartialTypesystem(boolean compilePartialTypesystem) {
        return set(XmlOptionsKeys.COMPILE_PARTIAL_TYPESYSTEM, compilePartialTypesystem);
    }

    public boolean isCompilePartialTypesystem() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.COMPILE_PARTIAL_TYPESYSTEM);
        return flag != null && flag;
    }

    /**
     * If this option is set when an instance is created, then value
     * facets will be checked on each call to a setter or getter
     * method on instances of XmlObject within the instance document.
     * If the facets are not satisfied, then an unchecked exception is
     * thrown immediately.  This option is useful for finding code that
     * is introducing invalid values in an XML document, but it
     * slows performance.
     *
     * @see org.apache.xmlbeans.impl.schema.XmlObjectFactory#parse(java.io.File, XmlOptions)
     */
    public XmlOptions setValidateOnSet() {
        return setValidateOnSet(true);
    }

    public XmlOptions setValidateOnSet(boolean b) {
        return set(XmlOptionsKeys.VALIDATE_ON_SET, b);
    }

    public boolean isValidateOnSet() {
        return hasOption(XmlOptionsKeys.VALIDATE_ON_SET);
    }

    /**
     * If this option is set, xsd:float and xsd:double values are held to the XSD
     * lexical space when parsing. {@link Float#parseFloat}/{@link Double#parseDouble}
     * also accept lexical forms that XSD does not allow: hexadecimal floats
     * ({@code 0x1p4}), the Java {@code Infinity} token, and a trailing type suffix
     * ({@code f}/{@code F}/{@code d}/{@code D}). With this option set those forms are
     * rejected as invalid; XSD only permits a decimal number with an optional
     * exponent, or the special values {@code INF}, {@code -INF} and {@code NaN}.
     * The default value is false, so the long-standing lenient behaviour is
     * unchanged unless this is set.
     *
     * @return this
     * @since 5.4.0
     */
    public XmlOptions setLoadStrictFloatingPoint() {
        return setLoadStrictFloatingPoint(true);
    }

    /**
     * Sets whether xsd:float and xsd:double values are held to the XSD lexical
     * space when parsing. See {@link #setLoadStrictFloatingPoint()}.
     *
     * @param b {@code true} to reject lexical forms outside the XSD float/double space
     * @return this
     * @since 5.4.0
     */
    public XmlOptions setLoadStrictFloatingPoint(boolean b) {
        return set(XmlOptionsKeys.LOAD_STRICT_FLOATING_POINT, b);
    }

    /**
     * Returns whether xsd:float and xsd:double values are held to the XSD lexical
     * space when parsing. See {@link #setLoadStrictFloatingPoint()}.
     *
     * @return {@code true} if strict XSD float/double parsing is enabled
     * @since 5.4.0
     */
    public boolean isLoadStrictFloatingPoint() {
        return hasOption(XmlOptionsKeys.LOAD_STRICT_FLOATING_POINT);
    }

    /**
     * If this option is set, xsd:decimal values are allowed to use scientific/exponent
     * notation (e.g. {@code 1E5}) when parsing. That form is outside the xsd:decimal
     * lexical space - it belongs to xsd:double/xsd:float - and {@link java.math.BigDecimal}
     * would otherwise parse it to a wrong value ({@code 1E5 -> 100000}). The default is to
     * disallow it: an exponent in a decimal is reported as invalid. Such values can also be
     * expensive to parse when the exponent is very large. Set this only to restore the
     * long-standing lenient behaviour.
     *
     * @return this
     * @since 5.4.0
     */
    public XmlOptions setLoadAllowDecimalExponent() {
        return setLoadAllowDecimalExponent(true);
    }

    /**
     * Sets whether xsd:decimal values may use scientific/exponent notation when parsing.
     * See {@link #setLoadAllowDecimalExponent()}.
     *
     * @param b {@code true} to accept an exponent in a decimal lexical value
     * @return this
     * @since 5.4.0
     */
    public XmlOptions setLoadAllowDecimalExponent(boolean b) {
        return set(XmlOptionsKeys.LOAD_ALLOW_DECIMAL_EXPONENT, b);
    }

    /**
     * Returns whether xsd:decimal values may use scientific/exponent notation when parsing.
     * See {@link #setLoadAllowDecimalExponent()}.
     *
     * @return {@code true} if an exponent is accepted in a decimal lexical value
     * @since 5.4.0
     */
    public boolean isLoadAllowDecimalExponent() {
        return hasOption(XmlOptionsKeys.LOAD_ALLOW_DECIMAL_EXPONENT);
    }

    /**
     * Instructs the validator to skip elements matching an {@code <any>}
     * particle with contentModel="lax". This is useful because,
     * in certain situations, XmlBeans will find types on the
     * classpath that the document author did not anticipate.
     */
    public XmlOptions setValidateTreatLaxAsSkip() {
        return setValidateTreatLaxAsSkip(true);
    }

    public XmlOptions setValidateTreatLaxAsSkip(boolean b) {
        return set(XmlOptionsKeys.VALIDATE_TREAT_LAX_AS_SKIP, b);
    }

    public boolean isValidateTreatLaxAsSkip() {
        return hasOption(XmlOptionsKeys.VALIDATE_TREAT_LAX_AS_SKIP);
    }

    /**
     * Performs additional validation checks that are disabled by
     * default for better compatibility.
     */
    public XmlOptions setValidateStrict() {
        return setValidateStrict(true);
    }

    public XmlOptions setValidateStrict(boolean b) {
        return set(XmlOptionsKeys.VALIDATE_STRICT, b);
    }

    public boolean isValidateStrict() {
        return hasOption(XmlOptionsKeys.VALIDATE_STRICT);
    }

    public XmlOptions setValidateTextOnly() {
        return setValidateTextOnly(true);
    }

    public XmlOptions setValidateTextOnly(boolean b) {
        return set(XmlOptionsKeys.VALIDATE_TEXT_ONLY, b);
    }

    public boolean isValidateTextOnly() {
        return hasOption(XmlOptionsKeys.VALIDATE_TEXT_ONLY);
    }


    /**
     * This option controls whether or not operations on XmlBeans are
     * thread safe.  When not on, all XmlBean operations will be synchronized.
     * This provides for multiple thread the ability to access a single
     * XmlBeans simultaneously, but has a perf impact.  If set, then
     * only one thread may access an XmlBean.
     */
    public XmlOptions setUnsynchronized() {
        return setUnsynchronized(true);
    }

    public XmlOptions setUnsynchronized(boolean b) {
        return set(XmlOptionsKeys.UNSYNCHRONIZED, b);
    }

    public boolean isUnsynchronized() {
        return hasOption(XmlOptionsKeys.UNSYNCHRONIZED);
    }

    /**
     * If this option is set when compiling a schema, then the given
     * EntityResolver will be consulted in order to resolve any
     * URIs while downloading imported schemas.
     * <p>
     * EntityResolvers are currently only used by compileXsd; they
     * are not consulted by other functions, for example, parse.
     * This will likely change in the future.
     *
     * @see XmlBeans#compileXsd
     */
    public XmlOptions setEntityResolver(EntityResolver resolver) {
        return set(XmlOptionsKeys.ENTITY_RESOLVER, resolver);
    }

    public EntityResolver getEntityResolver() {
        return (EntityResolver) get(XmlOptionsKeys.ENTITY_RESOLVER);
    }

    /**
     * If this option is set when compiling a schema, then the given
     * URI will be considered as base URI when deciding the directory
     * structure for saving the sources inside the generated JAR file.
     *
     * @param baseURI the URI to be considered as "base"
     * @see XmlBeans#compileXsd
     */
    public XmlOptions setBaseURI(URI baseURI) {
        return set(XmlOptionsKeys.BASE_URI, baseURI);
    }

    public URI getBaseURI() {
        return (URI) get(XmlOptionsKeys.BASE_URI);
    }

    /**
     * If this option is set when compiling a schema, then the given
     * SchemaTypeCodePrinter.Printer will be used to generate the
     * Java code.
     *
     * @see XmlBeans#compileXsd
     */
    public XmlOptions setSchemaCodePrinter(SchemaCodePrinter printer) {
        return set(XmlOptionsKeys.SCHEMA_CODE_PRINTER, printer);
    }

    public SchemaCodePrinter getSchemaCodePrinter() {
        return (SchemaCodePrinter) get(XmlOptionsKeys.SCHEMA_CODE_PRINTER);
    }

    /**
     * If this option is set to true, the return of XmlObject.copy() method will
     * return an object in it's own synchronization domain, otherwise both objects
     * will share the same synchronization domain, requiring explicit synchronization
     * when concurent accessing the two objects.
     *
     * @param useNewSyncDomain A flag representing the usage of new domain
     * @see XmlObject#copy()
     */
    public XmlOptions setCopyUseNewSynchronizationDomain(boolean useNewSyncDomain) {
        return set(XmlOptionsKeys.COPY_USE_NEW_SYNC_DOMAIN, useNewSyncDomain);
    }

    public boolean isCopyUseNewSynchronizationDomain() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.COPY_USE_NEW_SYNC_DOMAIN);
        return flag != null && flag;
    }

    public XmlOptions setUseSameLocale(Object localeOrXmlTokenSource) {
        return set(XmlOptionsKeys.USE_SAME_LOCALE, localeOrXmlTokenSource);
    }

    public Object getUseSameLocale() {
        return get(XmlOptionsKeys.USE_SAME_LOCALE);
    }


    /**
     * Sets the maximum number of bytes allowed when an Entity is expanded during parsing.
     * The default value is 10240 bytes.
     *
     * @param entityBytesLimit the maximum number of bytes allowed when an Entity is expanded during parsing
     * @return this
     */
    public XmlOptions setLoadEntityBytesLimit(int entityBytesLimit) {
        return set(XmlOptionsKeys.LOAD_ENTITY_BYTES_LIMIT, entityBytesLimit);
    }

    public Integer getLoadEntityBytesLimit() {
        return (Integer) get(XmlOptionsKeys.LOAD_ENTITY_BYTES_LIMIT);
    }


    /**
     * Sets the maximum number of entity expansions allowed during parsing.
     * The default value is 2048.
     *
     * @param entityExpansionLimit the maximum number of entity expansions allowed during parsing
     * @return this
     */
    public XmlOptions setEntityExpansionLimit(int entityExpansionLimit) {
        return set(XmlOptionsKeys.ENTITY_EXPANSION_LIMIT, entityExpansionLimit);
    }

    public int getEntityExpansionLimit() {
        Integer limit = (Integer) get(XmlOptionsKeys.ENTITY_EXPANSION_LIMIT);
        return limit == null ? DEFAULT_ENTITY_EXPANSION_LIMIT : limit;
    }

    /**
     * Controls whether DTD grammar is loaded during parsing.
     * The default value is false.
     *
     * @param loadDTDGrammar {@code true}, if DTD grammar is loaded during parsing
     * @return this
     */
    public XmlOptions setLoadDTDGrammar(boolean loadDTDGrammar) {
        return set(XmlOptionsKeys.LOAD_DTD_GRAMMAR, loadDTDGrammar);
    }

    /**
     * Returns whether DTD grammar is loaded during parsing.
     * The default value is false.
     *
     * @return {@code true}, if DTD grammar is loaded during parsing
     */
    public boolean isLoadDTDGrammar() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.LOAD_DTD_GRAMMAR);
        return flag != null && flag;
    }

    /**
     * Controls whether external DTDs are loaded during parsing.
     * The default value is false.
     *
     * @param loadExternalDTD {@code true}, if external DTDs are loaded during parsing
     * @return this
     */
    public XmlOptions setLoadExternalDTD(boolean loadExternalDTD) {
        return set(XmlOptionsKeys.LOAD_EXTERNAL_DTD, loadExternalDTD);
    }

    /**
     * Returns whether external DTDs are loaded during parsing.
     * The default value is false.
     *
     * @return {@code true}, if external DTDs are loaded during parsing
     */
    public boolean isLoadExternalDTD() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.LOAD_EXTERNAL_DTD);
        return flag != null && flag;
    }

    /**
     * Controls whether DocType declarations are disallowed during XML parsing. If they are disallowed,
     * the parser will throw an exception if a DocType declaration is encountered.
     * The default value is false.
     *
     * @param disallowDocTypeDeclaration {@code true}, if DocType declarations are to be disallowed
     * @return this
     * @since 5.0.2
     */
    public XmlOptions setDisallowDocTypeDeclaration(boolean disallowDocTypeDeclaration) {
        return set(XmlOptionsKeys.DISALLOW_DOCTYPE_DECLARATION, disallowDocTypeDeclaration);
    }

    /**
     * Returns whether DocType declarations are disallowed during XML parsing. If they are disallowed,
     * the parser will throw an exception if a DocType declaration is encountered.
     * The default value is false.
     *
     * @return {@code true}, if DocType declarations are to be disallowed
     * @since 5.0.2
     */
    public boolean disallowDocTypeDeclaration() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.DISALLOW_DOCTYPE_DECLARATION);
        return flag != null && flag;
    }

    public XmlOptions setSaveOptimizeForSpeed(boolean saveOptimizeForSpeed) {
        return set(XmlOptionsKeys.SAVE_OPTIMIZE_FOR_SPEED, saveOptimizeForSpeed);
    }

    public boolean isSaveOptimizeForSpeed() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.SAVE_OPTIMIZE_FOR_SPEED);
        return flag != null && flag;
    }

    // Use in XmlOptions to enable SAAJ support in store
    public XmlOptions setSaaj(Saaj saaj) {
        return set(XmlOptionsKeys.SAAJ_IMPL, saaj);
    }

    public Saaj getSaaj() {
        return (Saaj) get(XmlOptionsKeys.SAAJ_IMPL);
    }

    public XmlOptions setLoadUseLocaleCharUtil(boolean useCharUtil) {
        return set(XmlOptionsKeys.LOAD_USE_LOCALE_CHAR_UTIL, useCharUtil);
    }

    public boolean isLoadUseLocaleCharUtil() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.LOAD_USE_LOCALE_CHAR_UTIL);
        return flag != null && flag;
    }

    public XmlOptions setXPathUseSaxon() {
        return setXPathUseSaxon(true);
    }

    public XmlOptions setXPathUseSaxon(boolean xpathUseSaxon) {
        return set(XmlOptionsKeys.XPATH_USE_SAXON, xpathUseSaxon);
    }

    public boolean isXPathUseSaxon() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.XPATH_USE_SAXON);
        return flag != null && flag;
    }


    public XmlOptions setXPathUseXmlBeans() {
        return setXPathUseSaxon(true);
    }

    public XmlOptions setXPathUseXmlBeans(boolean xpathUseXmlBeans) {
        return set(XmlOptionsKeys.XPATH_USE_XMLBEANS, xpathUseXmlBeans);
    }

    public boolean isXPathUseXmlBeans() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.XPATH_USE_XMLBEANS);
        return flag != null && flag;
    }

    public XmlOptions setCompileAnnotationAsJavadoc() {
        return setCompileAnnotationAsJavadoc(true);
    }

    /**
     * When generating the schema sources, copy over the schema annotations to the javadoc.
     * Be aware basic code injection is filtered, but annotation based RCE aren't filtered.
     * So think twice before activating this on untrusted schemas!
     *
     * @param useAnnotationAsJavadoc {@code true} = copy the annotation - defaults to {@code false}
     */
    public XmlOptions setCompileAnnotationAsJavadoc(boolean useAnnotationAsJavadoc) {
        return set(XmlOptionsKeys.COMPILE_ANNOTATION_JAVADOC, useAnnotationAsJavadoc);
    }

    public boolean isCompileAnnotationAsJavadoc() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.COMPILE_ANNOTATION_JAVADOC);
        return flag != null && flag;
    }

    public XmlOptions setAttributeValidationCompatMode(boolean attributeValidationCompatMode) {
        return set(XmlOptionsKeys.ATTRIBUTE_VALIDATION_COMPAT_MODE, attributeValidationCompatMode);
    }

    public boolean isAttributeValidationCompatMode() {
        Boolean flag = (Boolean) get(XmlOptionsKeys.ATTRIBUTE_VALIDATION_COMPAT_MODE);
        return flag != null && flag;
    }

    public enum BeanMethod {
        GET, XGET, IS_SET, IS_NIL, IS_NIL_IDX, SET, SET_NIL, SET_NIL_IDX, XSET, UNSET,
        GET_ARRAY, XGET_ARRAY, GET_IDX, XGET_IDX, XSET_ARRAY, XSET_IDX,
        SIZE_OF_ARRAY, SET_ARRAY, SET_IDX,
        INSERT_IDX, INSERT_NEW_IDX,
        ADD, ADD_NEW, REMOVE_IDX,
        GET_LIST, XGET_LIST, SET_LIST,
        INSTANCE_TYPE
    }

    /**
     * @return the list of methods to be generated in the XmlBean or {@code null} for all
     */
    @SuppressWarnings("unchecked")
    public Set<BeanMethod> getCompilePartialMethod() {
        return (Set<BeanMethod>)get(XmlOptionsKeys.COMPILE_PARTIAL_METHODS);
    }

    public void setCompilePartialMethod(Set<BeanMethod> list) {
        if (list == null || list.isEmpty()) {
            remove(XmlOptionsKeys.COMPILE_PARTIAL_METHODS);
        } else {
            set(XmlOptionsKeys.COMPILE_PARTIAL_METHODS, list);
        }
    }

    /**
     * @return the maximum number of characters allowed for a number
     * @since 5.4.0
     */
    public int getMaxNumberOfCharsForNumbers() {
        Object value = get(XmlOptionsKeys.MAX_NUMBER_CHARS);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return DEFAULT_MAX_NUMBER_CHARS;
    }

    /**
     * Sets the maximum number of characters allowed for a number
     * @param max the maximum number of characters
     * @since 5.4.0
     */
    public void setMaxNumberOfCharsForNumbers(int max) {
        set(XmlOptionsKeys.MAX_NUMBER_CHARS, max);
    }

    /**
     * Sets the maximum number of characters allowed for a number
     * @param max the maximum number of characters, null means apply the default of 1024
     * @since 5.4.0
     */
    public void setMaxNumberOfCharsForNumbers(Integer max) {
        if (max == null) {
            remove(XmlOptionsKeys.MAX_NUMBER_CHARS);
        } else {
            set(XmlOptionsKeys.MAX_NUMBER_CHARS, max);
        }
    }

    /**
     * If passed null, returns an empty options object.  Otherwise, returns its argument.
     */
    public static XmlOptions maskNull(XmlOptions o) {
        return (o == null) ? EMPTY_OPTIONS : o;
    }


    private XmlOptions set(XmlOptionsKeys option) {
        return set(option, true);
    }

    private XmlOptions set(XmlOptionsKeys option, Object value) {
        _map.put(option, value);
        return this;
    }

    private XmlOptions set(XmlOptionsKeys option, int value) {
        return set(option, (Integer)value);
    }

    private XmlOptions set(XmlOptionsKeys option, boolean value) {
        if (value) {
            set(option, Boolean.TRUE);
        } else {
            remove(option);
        }
        return this;
    }

    /**
     * Used to test a generic option
     */
    public boolean hasOption(XmlOptionsKeys option) {
        return _map.containsKey(option);
    }

    /**
     * Used to get a generic option
     */
    public Object get(XmlOptionsKeys option) {
        return _map.get(option);
    }

    public void remove(XmlOptionsKeys option) {
        _map.remove(option);
    }
}
