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

package org.apache.xmlbeans.impl.values;

import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.common.PrefixResolver;
import org.apache.xmlbeans.impl.common.QNameHelper;
import org.apache.xmlbeans.impl.common.ValidationContext;
import org.apache.xmlbeans.impl.common.XMLChar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class XmlListImpl extends XmlObjectBase implements XmlAnySimpleType {
    public XmlListImpl(SchemaType type, boolean complex) {
        _schemaType = type;
        initComplexType(complex, false);
    }

    public SchemaType schemaType() {
        return _schemaType;
    }

    private final SchemaType _schemaType;
    private XmlSimpleList<? extends XmlAnySimpleType> _value;
    private XmlSimpleList<?> _jvalue;


    // SIMPLE VALUE ACCESSORS BELOW -------------------------------------------
    // gets raw text value

    private static String compute_list_text(List<? extends XmlAnySimpleType> xList) {
        return xList.isEmpty() ? "" : xList.stream().map(XmlListImpl::object2String).collect(Collectors.joining(" "));

    }

    private static String object2String(Object o) {
        String s = (o instanceof SimpleValue sv) ? sv.getStringValue() : o.toString();
        return (s == null) ? "" : s;
    }

    protected String compute_text(NamespaceManager nsm) {
        return compute_list_text(_value);
    }

    protected boolean is_defaultable_ws(String v) {
        try {
            XmlSimpleList<? extends XmlAnySimpleType> savedValue = _value;
            set_text(v);

            // restore the saved value
            _value = savedValue;

            return false;
        } catch (XmlValueOutOfRangeException e) {
            return true;
        }
    }

    protected void set_text(String s) {
        // first check against any patterns...
        if (_validateOnSet() && !_schemaType.matchPatternFacet(s)) {
            throw new XmlValueOutOfRangeException(XmlErrorCodes.DATATYPE_VALID$PATTERN_VALID,
                new Object[]{"list", s, QNameHelper.readable(_schemaType)});
        }

        SchemaType itemType = _schemaType.getListItemType();

        XmlSimpleList<? extends XmlAnySimpleType> newval = lex(s, itemType, _voorVc, has_store() ? get_store() : null);

        // check enumeration
        if (_validateOnSet()) {
            validateValue(newval, _schemaType, _voorVc);
        }

        // we made it all the way through; so we're OK.
        _value = newval;
        _jvalue = null;
    }

    private static final String[] EMPTY_STRINGARRAY = new String[0];

    public static String[] split_list(String s) {
        if (s.isEmpty()) {
            return EMPTY_STRINGARRAY;
        }

        List<String> result = new ArrayList<>();
        int i = 0;
        int start;
        for (; ; ) {
            while (i < s.length() && XMLChar.isSpace(s.charAt(i))) {
                i += 1;
            }
            if (i >= s.length()) {
                return result.toArray(EMPTY_STRINGARRAY);
            }
            start = i;
            while (i < s.length() && !XMLChar.isSpace(s.charAt(i))) {
                i += 1;
            }
            result.add(s.substring(start, i));
        }
    }

    public static XmlSimpleList<? extends XmlAnySimpleType> lex(String s, SchemaType itemType, ValidationContext ctx, PrefixResolver resolver) {
        String[] parts = split_list(s);

        Function<String, XmlAnySimpleType> fun = (str) -> {
            try {
                return itemType.newValue(str);
            } catch (XmlValueOutOfRangeException e) {
                Object[] obj = {"item '" + str + "' is not a valid value of " + QNameHelper.readable(itemType)};
                ctx.invalid(XmlErrorCodes.LIST, obj);
                return null;
            }
        };
        boolean pushed = false;
        if (resolver != null) {
            NamespaceContext.push(new NamespaceContext(resolver));
            pushed = true;
        }
        try {
            List<? extends XmlAnySimpleType> list = Stream.of(parts).map(fun).collect(Collectors.toList());
            return new XmlSimpleList<>(list);
        } finally {
            if (pushed) {
                NamespaceContext.pop();
            }
        }
    }

    protected void set_nil() {
        _value = null;
    }

    @Override
    public XmlSimpleList<? extends XmlAnySimpleType> xgetListValue() {
        check_dated();
        return _value;
    }

    public List<?> getListValue() {
        check_dated();
        if (_value == null) {
            return null;
        }
        if (_jvalue != null) {
            return _jvalue;
        }
        List<Object> javaResult = new ArrayList<>();
        for (Object o : _value) {
            javaResult.add(java_value((XmlObject) o));
        }
        _jvalue = new XmlSimpleList<>(javaResult);
        return _jvalue;
    }

    private static boolean permits_inner_space(XmlObject obj) {
        switch (((SimpleValue) obj).instanceType().getPrimitiveType().getBuiltinTypeCode()) {
            case SchemaType.BTC_STRING:
            case SchemaType.BTC_ANY_URI:
            case SchemaType.BTC_ANY_SIMPLE:
            case SchemaType.BTC_ANY_TYPE:
                return true;
            default:
                return false;
        }
    }

    private static boolean contains_white_space(String s) {
        return s.indexOf(' ') >= 0 ||
               s.indexOf('\t') >= 0 ||
               s.indexOf('\n') >= 0 ||
               s.indexOf('\r') >= 0;
    }

    public void set_list(List<?> list) {
        SchemaType itemType = _schemaType.getListItemType();
        XmlSimpleList<? extends XmlAnySimpleType> xList;

        boolean pushed = false;
        if (has_store()) {
            NamespaceContext.push(new NamespaceContext(get_store()));
            pushed = true;
        }

        Function<Object, XmlAnySimpleType> fun = (entry) -> {
            if ((entry instanceof XmlObject object) && permits_inner_space(object)) {
                String stringrep = entry.toString();
                if (contains_white_space(stringrep)) {
                    throw new XmlValueOutOfRangeException();
                }
            }
            return itemType.newValue(entry);
        };

        try {
            xList = new XmlSimpleList<>(list.stream().map(fun).collect(Collectors.toList()));
        } finally {
            if (pushed) {
                NamespaceContext.pop();
            }
        }

        if (_validateOnSet()) {
            // check enumeration + min/max/etc
            validateValue(xList, _schemaType, _voorVc);
        }

        _value = xList;
        _jvalue = null;
    }

    public static void validateValue(XmlSimpleList<? extends XmlAnySimpleType> items, SchemaType sType, ValidationContext context) {
        XmlObject[] enumvals = sType.getEnumerationValues();
        checkEnum:
        if (enumvals != null) {
            for (XmlObject enumval : enumvals) {
                if (equal_xmlLists(items, ((XmlObjectBase) enumval).xgetListValue())) {
                    break checkEnum;
                }
            }
            context.invalid(XmlErrorCodes.DATATYPE_ENUM_VALID,
                new Object[]{"list", items, QNameHelper.readable(sType)});
        }

        XmlObject o;
        int i;

        if ((o = sType.getFacet(SchemaType.FACET_LENGTH)) != null) {
            if ((i = ((SimpleValue) o).getIntValue()) != items.size()) {
                context.invalid(XmlErrorCodes.DATATYPE_LENGTH_VALID$LIST_LENGTH,
                    new Object[]{items, items.size(), i, QNameHelper.readable(sType)});
            }
        }

        if ((o = sType.getFacet(SchemaType.FACET_MIN_LENGTH)) != null) {
            if ((i = ((SimpleValue) o).getIntValue()) > items.size()) {
                context.invalid(XmlErrorCodes.DATATYPE_MIN_LENGTH_VALID$LIST_LENGTH,
                    new Object[]{items, items.size(), i, QNameHelper.readable(sType)});
            }
        }

        if ((o = sType.getFacet(SchemaType.FACET_MAX_LENGTH)) != null) {
            if ((i = ((SimpleValue) o).getIntValue()) < items.size()) {
                context.invalid(XmlErrorCodes.DATATYPE_MAX_LENGTH_VALID$LIST_LENGTH,
                    new Object[]{items, items.size(), i, QNameHelper.readable(sType)});
            }
        }
    }

    // comparators
    // protected int compare_to(XmlObject i) - no sorting order; inherit from base

    protected boolean equal_to(XmlObject obj) {
        return equal_xmlLists(_value, ((XmlObjectBase) obj).xgetListValue());
    }


    private static boolean equal_xmlLists(List<?> a, List<?> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    protected int value_hash_code() {
        if (_value == null) {
            return 0;
        }

        // hash code probes 9 distributed values, plus the last
        int hash = _value.size();
        int incr = _value.size() / 9;
        if (incr < 1) {
            incr = 1;
        }

        int i;
        for (i = 0; i < _value.size(); i += incr) {
            hash *= 19;
            hash += _value.get(i).hashCode();
        }

        return hash;
    }

    protected void validate_simpleval(String lexical, ValidationContext ctx) {
        validateValue(xgetListValue(), schemaType(), ctx);
    }

}
