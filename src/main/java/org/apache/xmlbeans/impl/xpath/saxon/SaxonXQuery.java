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

package org.apache.xmlbeans.impl.xpath.saxon;

import net.sf.saxon.Configuration;
import net.sf.saxon.dom.DocumentWrapper;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.ma.map.GeneralMapBuilder;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.str.StringView;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.*;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.logging.XmlBeansLogManager;
import org.apache.xmlbeans.impl.store.Cur;
import org.apache.xmlbeans.impl.store.Cursor;
import org.apache.xmlbeans.impl.store.Locale;
import org.apache.xmlbeans.impl.xpath.XQuery;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class SaxonXQuery implements XQuery {
    private static final Logger LOG = XmlBeansLogManager.getLogger(SaxonXQuery.class);

    private final XQueryExpression xquery;
    private final String contextVar;
    private final Configuration config;

    private Cur _cur;
    private long _version;
    private XmlOptions _options;


    /**
     * Construct given an XQuery expression string.
     *
     * @param query      The XQuery expression
     * @param contextVar The name of the context variable
     * @param boundary   The offset of the end of the prolog
     */
    public SaxonXQuery(final String query, String contextVar, Integer boundary, XmlOptions xmlOptions) {
        assert !(contextVar.startsWith(".") || contextVar.startsWith(".."));

        _options = xmlOptions;


        config = new Configuration();
        StaticQueryContext sc = config.newStaticQueryContext();
        Map<String, String> nsMap = xmlOptions.getLoadAdditionalNamespaces();
        if (nsMap != null) {
            for (Map.Entry<String, String> entry : nsMap.entrySet()) {
                sc.declareNamespace(entry.getKey(), NamespaceUri.of(entry.getValue()));
            }
        }
        this.contextVar = contextVar;
        //Saxon requires external variables at the end of the prolog...
        try {
            xquery = sc.compileQuery(
                query.substring(0, boundary) + " declare variable $" + contextVar + " external;" + query.substring(boundary)
            );
        } catch (TransformerException e) {
            throw new XmlRuntimeException(e);
        }
    }


    public XmlObject[] objectExecute(Cur c, XmlOptions options) {
        _version = c.getLocale().version();
        _cur = c.weakCur(this);
        this._options = options;

        Map<String, Object> bindings = XmlOptions.maskNull(_options).getXqueryVariables();
        List<Object> resultsList = execQuery(_cur.getDom(), bindings);

        XmlObject[] result = new XmlObject[resultsList.size()];
        for (int i = 0; i < resultsList.size(); i++) {
            //copy objects into the locale
            Locale l = Locale.getLocale(_cur.getLocale().getSchemaTypeLoader(), _options);

            l.enter();
            Object node = resultsList.get(i);
            Cur res;
            try {
                //typed function results of XQuery
                if (!(node instanceof Node)) {
                    res = l.load("<xml-fragment/>").tempCur();
                    res.setValue(node.toString());
                    SchemaType type = getType(node);
                    Locale.autoTypeDocument(res, type, null);
                    result[i] = res.getObject();
                } else {
                    res = loadNode(l, (Node) node);
                }
                result[i] = res.getObject();
            } catch (XmlException e) {
                throw new RuntimeException(e);
            } finally {
                l.exit();
            }
            res.release();
        }
        release();
        return result;
    }

    public XmlCursor cursorExecute(Cur c, XmlOptions options) {
        _version = c.getLocale().version();
        _cur = c.weakCur(this);
        this._options = options;

        Map<String, Object> bindings = XmlOptions.maskNull(_options).getXqueryVariables();
        List<Object> resultsList = execQuery(_cur.getDom(), bindings);

        int i;

        Locale locale = Locale.getLocale(_cur.getLocale().getSchemaTypeLoader(), _options);
        locale.enter();
        Locale.LoadContext _context = new Cur.CurLoadContext(locale, _options);
        Cursor resultCur = null;
        try {
            for (i = 0; i < resultsList.size(); i++) {
                loadNodeHelper(locale, (Node) resultsList.get(i), _context);
            }
            Cur c2 = _context.finish();
            Locale.associateSourceName(c, _options);
            Locale.autoTypeDocument(c, null, _options);
            resultCur = new Cursor(c2);
        } catch (XmlException e) {
            LOG.atInfo().withThrowable(e).log("Can't autotype document");
        } finally {
            locale.exit();
        }
        release();
        return resultCur;
    }


    public List<Object> execQuery(Object node, Map<String,Object> variableBindings) {
        try {
            Node contextNode = (Node) node;

            Document dom = (contextNode.getNodeType() == Node.DOCUMENT_NODE)
                ? (Document) contextNode : contextNode.getOwnerDocument();

            DocumentWrapper docWrapper = new DocumentWrapper(dom, null, config);
            NodeInfo root = docWrapper.wrap(contextNode);

            NamespaceUri emptyUri = NamespaceUri.of("");
            DynamicQueryContext dc = new DynamicQueryContext(config);
            dc.setContextItem(root);
            dc.setParameter(new StructuredQName("", emptyUri, contextVar), root);
            // Set the other variables
            if (variableBindings != null) {
                for (Map.Entry<String, Object> me : variableBindings.entrySet()) {
                    StructuredQName key = new StructuredQName("", emptyUri, me.getKey());
                    Object value = me.getValue();
                    if (value instanceof XmlTokenSource) {
                        Node paramObject = ((XmlTokenSource) value).getDomNode();
                        dc.setParameter(key, docWrapper.wrap(paramObject));
                    } else {
                        try {
                            dc.setParameter(key, objectToItem(value, config));
                        } catch (XPathException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

            List<Object> saxonNodes = xquery.evaluate(dc);
            for (ListIterator<Object> it = saxonNodes.listIterator(); it.hasNext(); ) {
                Object o = it.next();
                if (o instanceof NodeInfo) {
                    Node n = NodeOverNodeInfo.wrap((NodeInfo) o);
                    it.set(n);
                }
            }
            return saxonNodes;
        } catch (TransformerException e) {
            throw new RuntimeException("Error binding " + contextVar, e);
        }
    }


    private static Item objectToItem(Object value, Configuration config) throws XPathException, net.sf.saxon.trans.XPathException {
        if (value == null) {
            return null;
        }

        // convert to switch..
        if (value instanceof Boolean) {
            return BooleanValue.get((Boolean) value);
        } else if (value instanceof byte[]) {
            return new HexBinaryValue((byte[]) value);
        } else if (value instanceof Byte) {
            return new Int64Value((Byte) value, BuiltInAtomicType.BYTE, false);
        } else if (value instanceof Float) {
            return new FloatValue((Float) value);
        } else if (value instanceof Double) {
            return new DoubleValue((Double) value);
        } else if (value instanceof Integer) {
            return new Int64Value((Integer) value, BuiltInAtomicType.INT, false);
        } else if (value instanceof Long) {
            return new Int64Value((Long) value, BuiltInAtomicType.LONG, false);
        } else if (value instanceof Short) {
            return new Int64Value((Short) value, BuiltInAtomicType.SHORT, false);
        } else if (value instanceof String) {
            return new StringValue((String) value);
        } else if (value instanceof BigDecimal) {
            return new BigDecimalValue((BigDecimal) value);
        } else if (value instanceof BigInteger) {
            return new BigIntegerValue((BigInteger) value);
        } else if (value instanceof SaxonDuration) {
            return ((SaxonDuration) value).getDurationValue();
        } else if (value instanceof Duration) {
            // this is simpler and safer (but perhaps slower) than extracting all the components
            return DurationValue.makeDuration(StringView.tidy(value.toString())).asAtomic();
        } else if (value instanceof SaxonXMLGregorianCalendar) {
            return ((SaxonXMLGregorianCalendar) value).toCalendarValue();
        } else if (value instanceof XMLGregorianCalendar) {
            XMLGregorianCalendar g = (XMLGregorianCalendar) value;
            QName gtype = g.getXMLSchemaType();
            if (gtype.equals(DatatypeConstants.DATETIME)) {
                return DateTimeValue.makeDateTimeValue(StringView.tidy(value.toString()),
                        config.getConversionRules()).asAtomic();
            } else if (gtype.equals(DatatypeConstants.DATE)) {
                return DateValue.tryParseDate(value.toString(),
                        config.getConversionRules().isAllowYearZero()).asAtomic();
            } else if (gtype.equals(DatatypeConstants.TIME)) {
                return TimeValue.makeTimeValue(StringView.tidy(value.toString())).asAtomic();
            } else if (gtype.equals(DatatypeConstants.GYEAR)) {
                return GYearValue.makeGYearValue(StringView.tidy(value.toString()),
                        config.getConversionRules()).asAtomic();
            } else if (gtype.equals(DatatypeConstants.GYEARMONTH)) {
                return GYearMonthValue.makeGYearMonthValue(StringView.tidy(value.toString()),
                        config.getConversionRules()).asAtomic();
            } else if (gtype.equals(DatatypeConstants.GMONTH)) {
                // a workaround for W3C schema bug
                String val = value.toString();
                if (val.endsWith("--")) {
                    val = val.substring(0, val.length() - 2);
                }
                return GMonthValue.makeGMonthValue(StringView.tidy(val)).asAtomic();
            } else if (gtype.equals(DatatypeConstants.GMONTHDAY)) {
                return GMonthDayValue.makeGMonthDayValue(StringView.tidy(value.toString())).asAtomic();
            } else if (gtype.equals(DatatypeConstants.GDAY)) {
                return GDayValue.makeGDayValue(StringView.tidy(value.toString())).asAtomic();
            } else {
                throw new AssertionError("Unknown Gregorian date type");
            }
        } else if (value instanceof QName) {
            QName q = (QName) value;
            return new QNameValue(q.getPrefix(), NamespaceUri.of(q.getNamespaceURI()), q.getLocalPart()); //BuiltInAtomicType.QNAME, null);
        } else if (value instanceof URI) {
            return new AnyURIValue(value.toString());
        } else if (value instanceof Map) {
            GeneralMapBuilder mb = new GeneralMapBuilder(config.getMapSpecVersion());
            for (Map.Entry<?, ?> me : ((Map<?, ?>) value).entrySet()) {
                mb.put(
                    (AtomicValue) objectToItem(me.getKey(), config),
                    objectToItem(me.getValue(), config));
            }
            return mb.getCompletedMap();
        } else {
            return new ObjectValue<>(value);
        }
    }


    private SchemaType getType(Object node) {
        SchemaType type;
        if (node instanceof Integer) {
            type = XmlInteger.type;
        } else if (node instanceof Double) {
            type = XmlDouble.type;
        } else if (node instanceof Long) {
            type = XmlLong.type;
        } else if (node instanceof Float) {
            type = XmlFloat.type;
        } else if (node instanceof BigDecimal) {
            type = XmlDecimal.type;
        } else if (node instanceof Boolean) {
            type = XmlBoolean.type;
        } else if (node instanceof String) {
            type = XmlString.type;
        } else if (node instanceof Date) {
            type = XmlDate.type;
        } else {
            type = XmlAnySimpleType.type;
        }
        return type;
    }

    public void release() {
        if (_cur != null) {
            _cur.release();
            _cur = null;
        }
    }


    private Cur loadNode(Locale locale, Node node) {
        Locale.LoadContext context = new Cur.CurLoadContext(locale, _options);

        try {
            loadNodeHelper(locale, node, context);
            Cur c = context.finish();
            Locale.associateSourceName(c, _options);
            Locale.autoTypeDocument(c, null, _options);
            return c;
        } catch (Exception e) {
            throw new XmlRuntimeException(e.getMessage(), e);
        }
    }

    private void loadNodeHelper(Locale locale, Node node, Locale.LoadContext context) {
        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            QName attName = new QName(node.getNamespaceURI(),
                node.getLocalName(),
                node.getPrefix());
            context.attr(attName, node.getNodeValue());
        } else {
            locale.loadNode(node, context);
        }

    }

}
