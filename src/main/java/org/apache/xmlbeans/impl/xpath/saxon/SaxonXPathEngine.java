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

import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.GDateValue;
import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.store.Cur;
import org.apache.xmlbeans.impl.store.DomImpl;
import org.apache.xmlbeans.impl.store.Locale;
import org.apache.xmlbeans.impl.xpath.XPathEngine;
import org.apache.xmlbeans.impl.xpath.XPathExecutionContext;
import org.w3c.dom.Node;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.List;

public class SaxonXPathEngine extends XPathExecutionContext implements XPathEngine {
    // full datetime format: yyyy-MM-dd'T'HH:mm:ss'Z'
    private final DateFormat xmlDateFormat = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);

    private Cur _cur;
    private SaxonXPath _engine;
    private boolean _firstCall = true;
    private final long _version;


    SaxonXPathEngine(SaxonXPath xpathImpl, Cur c) {
        _engine = xpathImpl;
        _version = c.getLocale().version();
        _cur = c.weakCur(this);
    }

    public boolean next(Cur c) {
        if (!_firstCall) {
            return false;
        }

        _firstCall = false;

        if (_cur != null && _version != _cur.getLocale().version()) {
            throw new ConcurrentModificationException("Document changed during select");
        }

        List resultsList = _engine.selectPath(_cur.getDom());

        int i;
        for (i = 0; i < resultsList.size(); i++) {
            //simple type function results
            Object node = resultsList.get(i);
            Cur pos = null;
            if (!(node instanceof Node)) {
                Object obj = resultsList.get(i);
                String value;
                if (obj instanceof Date) {
                    value = xmlDateFormat.format((Date) obj);
                } else if (obj instanceof GDateValue) {
                    value = ((GDateValue) obj).getStringValue();
                } else if (obj instanceof DateTimeValue) {
                    value = ((DateTimeValue) obj).getStringValue();
                } else if (obj instanceof BigDecimal) {
                    value = ((BigDecimal) obj).toPlainString();
                } else if (obj instanceof AtomicValue) {
                    // e.g. DurationValue - since Saxon 13, toString() no longer returns the lexical form
                    value = ((AtomicValue) obj).getStringValue();
                } else {
                    value = obj.toString();
                }

                //we cannot leave the cursor's locale, as
                //everything is done in the selections of this cursor

                org.apache.xmlbeans.impl.store.Locale l = c.getLocale();
                try {
                    pos = l.load("<xml-fragment/>").tempCur();
                    pos.setValue(value);
                    SchemaType type = getType(node);
                    Locale.autoTypeDocument(pos, type, null);
                    //move the cur to the actual text
                    pos.next();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                assert (node instanceof DomImpl.Dom) :
                    "New object created in XPATH!";
                pos = ((DomImpl.Dom) node).tempCur();

            }
            c.addToSelection(pos);
            pos.release();
        }
        release();
        _engine = null;
        return true;
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
        } else if (node instanceof GDateValue) {
            type = XmlDate.type;
        } else if (node instanceof DateTimeValue) {
            type = XmlDateTime.type;
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


}
