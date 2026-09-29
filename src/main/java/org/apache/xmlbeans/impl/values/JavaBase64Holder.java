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

import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlBase64Binary;
import org.apache.xmlbeans.XmlErrorCodes;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.common.QNameHelper;
import org.apache.xmlbeans.impl.common.ValidationContext;
import org.apache.xmlbeans.impl.common.XMLChar;
import org.apache.xmlbeans.impl.schema.BuiltinSchemaTypeSystem;

import java.util.Arrays;
import java.util.Base64;

public abstract class JavaBase64Holder extends JavaDigestableHolder {
    public SchemaType schemaType() {
        return BuiltinSchemaTypeSystem.ST_BASE_64_BINARY;
    }

    // SIMPLE VALUE ACCESSORS BELOW -------------------------------------------

    // gets raw text value
    protected String compute_text(NamespaceManager nsm) {
        return Base64.getEncoder().encodeToString(_value);
    }

    protected void set_text(String s) {
        _hashcached = false;
        if (_validateOnSet()) {
            _value = validateLexical(s, schemaType(), _voorVc);
        } else {
            _value = lex(s, _voorVc);
        }
    }

    protected void set_nil() {
        _hashcached = false;
        _value = null;
    }

    public static byte[] lex(String v, ValidationContext c) {
        // The MIME decoder silently discards any character outside the base64
        // alphabet, so a value carrying stray characters decodes to a truncated
        // result instead of being rejected. The base64Binary lexical space only
        // permits the alphabet and XML whitespace, so reject anything else here.
        for (int i = 0, len = v.length(); i < len; i++) {
            char ch = v.charAt(i);
            if (!isBase64Char(ch) && !XMLChar.isSpace(ch)) {
                c.invalid(XmlErrorCodes.BASE64BINARY, new Object[]{"not encoded properly"});
                return null;
            }
        }
        try {
            return Base64.getMimeDecoder().decode(v);
        } catch (IllegalArgumentException e) {
            // TODO - get a decent error with line numbers and such here
            c.invalid(XmlErrorCodes.BASE64BINARY, new Object[]{"not encoded properly"});
            return null;
        }
    }

    private static boolean isBase64Char(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') ||
               (ch >= '0' && ch <= '9') || ch == '+' || ch == '/' || ch == '=';
    }

    public static byte[] validateLexical(String v, SchemaType sType, ValidationContext context) {
        final byte[] bytes = lex(v, context);
        if (bytes == null) {
            return null;
        }

        if (!sType.matchPatternFacet(v)) {
            context.invalid(XmlErrorCodes.DATATYPE_VALID$PATTERN_VALID$NO_VALUE,
                new Object[]{"base 64", QNameHelper.readable(sType)});
            return null;
        }

        return bytes;
    }

    public byte[] getByteArrayValue() {
        check_dated();
        if (_value == null) {
            return null;
        }

        byte[] result = new byte[_value.length];
        System.arraycopy(_value, 0, result, 0, _value.length);
        return result;
    }

    // setters
    protected void set_ByteArray(byte[] ba) {
        _hashcached = false;
        _value = new byte[ba.length];
        System.arraycopy(ba, 0, _value, 0, ba.length);
    }

    // comparators
    protected boolean equal_to(XmlObject i) {
        byte[] ival = ((XmlBase64Binary) i).getByteArrayValue();
        return Arrays.equals(_value, ival);
    }
}
