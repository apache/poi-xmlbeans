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
import org.apache.xmlbeans.XmlErrorCodes;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.common.QNameHelper;
import org.apache.xmlbeans.impl.common.ValidationContext;


public abstract class JavaFloatHolderEx extends JavaFloatHolder {
    public JavaFloatHolderEx(SchemaType type, boolean complex) {
        _schemaType = type;
        initComplexType(complex, false);
    }

    private final SchemaType _schemaType;

    public SchemaType schemaType() {
        return _schemaType;
    }

    protected void set_float(float v) {
        if (_validateOnSet()) {
            validateValue(v, _schemaType, _voorVc);
        }
        super.set_float(v);
    }

    public static float validateLexical(String v, SchemaType sType, ValidationContext context) {
        return validateLexical(v, sType, context, XmlOptions.DEFAULT_MAX_NUMBER_CHARS);
    }

    public static float validateLexical(String v, SchemaType sType, ValidationContext context,
                                        int maxNumberOfChars) {
        float f = JavaFloatHolder.validateLexical(v, context, false, maxNumberOfChars);

        if (!sType.matchPatternFacet(v)) {
            context.invalid(XmlErrorCodes.DATATYPE_VALID$PATTERN_VALID,
                    new Object[]{"float", v, QNameHelper.readable(sType)});
        }

        return f;
    }

    public static void validateValue(float v, SchemaType sType, ValidationContext context) {
        XmlObject x;
        float f;

        if ((x = sType.getFacet(SchemaType.FACET_MIN_EXCLUSIVE)) != null) {
            if (compare(v, f = ((XmlObjectBase) x).getFloatValue()) <= 0 || incomparable(v, f)) {
                context.invalid(XmlErrorCodes.DATATYPE_MIN_EXCLUSIVE_VALID,
                    new Object[]{"float", v, f, QNameHelper.readable(sType)});
            }
        }

        if ((x = sType.getFacet(SchemaType.FACET_MIN_INCLUSIVE)) != null) {
            if (compare(v, f = ((XmlObjectBase) x).getFloatValue()) < 0 || incomparable(v, f)) {
                context.invalid(XmlErrorCodes.DATATYPE_MIN_INCLUSIVE_VALID,
                    new Object[]{"float", v, f, QNameHelper.readable(sType)});
            }
        }

        if ((x = sType.getFacet(SchemaType.FACET_MAX_INCLUSIVE)) != null) {
            if (compare(v, f = ((XmlObjectBase) x).getFloatValue()) > 0 || incomparable(v, f)) {
                context.invalid(XmlErrorCodes.DATATYPE_MAX_INCLUSIVE_VALID,
                    new Object[]{"float", v, f, QNameHelper.readable(sType)});
            }
        }

        if ((x = sType.getFacet(SchemaType.FACET_MAX_EXCLUSIVE)) != null) {
            if (compare(v, f = ((XmlObjectBase) x).getFloatValue()) >= 0 || incomparable(v, f)) {
                context.invalid(XmlErrorCodes.DATATYPE_MAX_EXCLUSIVE_VALID,
                    new Object[]{"float", v, f, QNameHelper.readable(sType)});
            }
        }

        XmlObject[] vals = sType.getEnumerationValues();
        if (vals != null) {
            for (XmlObject val : vals) {
                if (compare(v, ((XmlObjectBase) val).getFloatValue()) == 0) {
                    return;
                }
            }
            context.invalid(XmlErrorCodes.DATATYPE_ENUM_VALID,
                new Object[]{"float", v, QNameHelper.readable(sType)});
        }
    }

    // NaN is equal to itself but neither less than nor greater than any other
    // value, so it satisfies none of the bound facets of a non-NaN bound
    private static boolean incomparable(float v, float bound) {
        return Float.isNaN(v) != Float.isNaN(bound);
    }

    protected void validate_simpleval(String lexical, ValidationContext ctx) {
        validateLexical(lexical, schemaType(), ctx);
        validateValue(getFloatValue(), schemaType(), ctx);
    }

}
