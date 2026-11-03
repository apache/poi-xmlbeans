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
import org.apache.xmlbeans.impl.util.MathUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;

public abstract class JavaDecimalHolderEx extends JavaDecimalHolder {
    private final SchemaType _schemaType;

    public SchemaType schemaType() {
        return _schemaType;
    }

    public JavaDecimalHolderEx(SchemaType type, boolean complex) {
        _schemaType = type;
        initComplexType(complex, false);
    }

    protected void set_text(String s) {
        int maxNumberOfChars = get_max_number_chars();
        if (_validateOnSet()) {
            boolean allowExponent = has_store() && get_store().get_locale().isLoadAllowDecimalExponent();
            validateLexical(s, _schemaType, _voorVc, allowExponent, maxNumberOfChars);
        }

        BigDecimal v = null;
        try {
            v = MathUtil.parseAsBigDecimal(s, maxNumberOfChars);
        } catch (Exception e) {
            _voorVc.invalid(XmlErrorCodes.DECIMAL, new Object[]{s});
        }

        if (_validateOnSet()) {
            validateValue(v, _schemaType, _voorVc);
        }

        super.set_BigDecimal(v);
    }

    protected void set_BigDecimal(BigDecimal v) {
        if (_validateOnSet()) {
            validateValue(v, _schemaType, _voorVc);
        }
        super.set_BigDecimal(v);
    }

    public static void validateLexical(String v, SchemaType sType, ValidationContext context) {
        validateLexical(v, sType, context, false);
    }

    public static void validateLexical(String v, SchemaType sType, ValidationContext context, boolean allowExponent) {
        validateLexical(v, sType, context, allowExponent, XmlOptions.DEFAULT_MAX_NUMBER_CHARS);
    }

    public static void validateLexical(String v, SchemaType sType, ValidationContext context,
                                       boolean allowExponent, int maxNumberOfChars) {
        JavaDecimalHolder.validateLexical(v, context, allowExponent, maxNumberOfChars);

        // check pattern
        if (sType.hasPatternFacet()) {
            if (!sType.matchPatternFacet(v)) {
                // TODO - describe string and pattern here in error
                context.invalid(XmlErrorCodes.DATATYPE_VALID$PATTERN_VALID,
                    new Object[]{"decimal", v, QNameHelper.readable(sType)});
            }
        }
    }

    /**
     * Performs facet validation only.
     */
    public static void validateValue(BigDecimal v, SchemaType sType, ValidationContext context) {
        // fractional digits
        XmlObject fd = sType.getFacet(SchemaType.FACET_FRACTION_DIGITS);
        if (fd != null) {
            int scale = ((XmlObjectBase) fd).getBigIntegerValue().intValue();
            try {
                // used only for side-effect - this does not change v despite
                // the name of the method
                // noinspection ResultOfMethodCallIgnored
                v.setScale(scale, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException e) {
                // ArithmeticException will be thrown if cannot represent as an Integer
                // with this scale - i.e. would need a fraction which would correspond
                // to digits beyond the allowed number
                context.invalid(XmlErrorCodes.DATATYPE_FRACTION_DIGITS_VALID,
                    new Object[]{v.scale(), v.toString(), scale, QNameHelper.readable(sType)});
                return;
            }
        }

        // total digits
        XmlObject td = sType.getFacet(SchemaType.FACET_TOTAL_DIGITS);
        if (td != null) {
            String temp = v.unscaledValue().toString();
            int tdf = ((XmlObjectBase) td).getBigIntegerValue().intValue();
            int origLen = temp.length();
            int len = origLen;
            if (origLen > 0) {
                // don't count leading minus
                if (temp.charAt(0) == '-') {
                    len -= 1;
                }

                // don't count trailing zeros if we can absorb them into scale
                int insignificantTrailingZeros = 0;
                int vScale = v.scale();
                for (int j = origLen - 1;
                     temp.charAt(j) == '0' && j > 0 && insignificantTrailingZeros < vScale;
                     j--) {
                    insignificantTrailingZeros++;
                }

                len -= insignificantTrailingZeros;

                // leading zeros of the fraction count too: 0.05 is 5 x 10^-2,
                // and the 2 must not exceed totalDigits either
                int fractionDigits = vScale - insignificantTrailingZeros;
                if (v.signum() != 0 && fractionDigits > len) {
                    len = fractionDigits;
                }
            }

            if (len > tdf) {
                context.invalid(XmlErrorCodes.DATATYPE_TOTAL_DIGITS_VALID,
                    new Object[]{len, v.toString(), tdf, QNameHelper.readable(sType)});
                return;
            }
        }

        // min ex
        XmlObject mine = sType.getFacet(SchemaType.FACET_MIN_EXCLUSIVE);
        if (mine != null) {
            BigDecimal m = ((XmlObjectBase) mine).getBigDecimalValue();
            if (v.compareTo(m) <= 0) {
                context.invalid(XmlErrorCodes.DATATYPE_MIN_EXCLUSIVE_VALID,
                    new Object[]{"decimal", v, m, QNameHelper.readable(sType)});
                return;
            }
        }

        // min in
        XmlObject mini = sType.getFacet(SchemaType.FACET_MIN_INCLUSIVE);
        if (mini != null) {
            BigDecimal m = ((XmlObjectBase) mini).getBigDecimalValue();
            if (v.compareTo(m) < 0) {
                context.invalid(XmlErrorCodes.DATATYPE_MIN_INCLUSIVE_VALID,
                    new Object[]{"decimal", v, m, QNameHelper.readable(sType)});
                return;
            }
        }

        // max in
        XmlObject maxi = sType.getFacet(SchemaType.FACET_MAX_INCLUSIVE);
        if (maxi != null) {
            BigDecimal m = ((XmlObjectBase) maxi).getBigDecimalValue();
            if (v.compareTo(m) > 0) {
                context.invalid(XmlErrorCodes.DATATYPE_MAX_INCLUSIVE_VALID,
                    new Object[]{"decimal", v, m, QNameHelper.readable(sType)});
                return;
            }
        }

        // max ex
        XmlObject maxe = sType.getFacet(SchemaType.FACET_MAX_EXCLUSIVE);
        if (maxe != null) {
            BigDecimal m = ((XmlObjectBase) maxe).getBigDecimalValue();
            if (v.compareTo(m) >= 0) {
                context.invalid(XmlErrorCodes.DATATYPE_MAX_EXCLUSIVE_VALID,
                    new Object[]{"decimal", v, m, QNameHelper.readable(sType)});
                return;
            }
        }

        // enumeration
        XmlObject[] vals = sType.getEnumerationValues();
        if (vals != null) {
            for (XmlObject val : vals) {
                if (v.compareTo(((XmlObjectBase) val).getBigDecimalValue()) == 0) {
                    return;
                }
            }
            context.invalid(XmlErrorCodes.DATATYPE_ENUM_VALID,
                new Object[]{"decimal", v, QNameHelper.readable(sType)});
        }
    }

    protected void validate_simpleval(String lexical, ValidationContext ctx) {
        boolean allowExponent = has_store() && get_store().get_locale().isLoadAllowDecimalExponent();
        validateLexical(lexical, schemaType(), ctx, allowExponent);
        validateValue(getBigDecimalValue(), schemaType(), ctx);
    }

}
