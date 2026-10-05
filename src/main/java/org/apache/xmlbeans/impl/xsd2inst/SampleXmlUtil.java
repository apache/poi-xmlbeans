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

package org.apache.xmlbeans.impl.xsd2inst;

/*
 * TODO:
 *  Comment on enumerations?
 *  Comment on facets?
 *  Have a verbose option?
 *  Have a sample data option, would create valid instance with sample data?
 *  Add the pattern facet; this is tricky, considering the relationship with length
 */

import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.util.HexBin;
import org.apache.xmlbeans.impl.util.MathUtil;
import org.apache.xmlbeans.soap.SOAPArrayType;
import org.apache.xmlbeans.soap.SchemaWSDLArrayType;

import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SampleXmlUtil {
    private final boolean _soapEnc;
    private static final int MAX_ELEMENTS = 1000;
    private int _nElements;

    private SampleXmlUtil(boolean soapEnc) {
        _soapEnc = soapEnc;
    }

    /**
     * @param sType a schema type
     * @return Sample XML as a string
     */
    public static String createSampleForType(SchemaType sType) {
        XmlObject object = XmlObject.Factory.newInstance();
        try (XmlCursor cursor = object.newCursor()) {
            // Skip the document node
            cursor.toNextToken();
            // Using the type and the cursor, call the utility method to get a
            // sample XML payload for that Schema element
            new SampleXmlUtil(false).createSampleForType(sType, cursor);
        }
        // Cursor now contains the sample payload
        // Pretty print the result.  Note that the cursor is positioned at the
        // end of the doc so we use the original xml object that the cursor was
        // created upon to do the xmlText() against.
        XmlOptions options = new XmlOptions();
        options.setSavePrettyPrint();
        options.setSavePrettyPrintIndent(2);
        options.setSaveAggressiveNamespaces();
        return object.xmlText(options);
    }

    /**
     * @param element a {code SchemaField}
     * @return Sample XML as a string
     * @since v5.0.3
     */
    public static String createSampleForType(SchemaField element) {
        SchemaType sType = element.getType();
        XmlObject object = XmlObject.Factory.newInstance();
        try (XmlCursor cursor = object.newCursor()) {
            // Skip the document node
            cursor.toNextToken();
            // Using the type and the cursor, call the utility method to get a
            // sample XML payload for that Schema element
            new SampleXmlUtil(false).createSampleForType(sType, cursor);
        }
        // Cursor now contains the sample payload
        // Pretty print the result.  Note that the cursor is positioned at the
        // end of the doc so we use the original xml object that the cursor was
        // created upon to do the xmlText() against.
        XmlOptions options = new XmlOptions();
        options.setSavePrettyPrint();
        options.setSavePrettyPrintIndent(2);
        options.setSaveAggressiveNamespaces();
        options.setSaveSyntheticDocumentElement(element.getName());
        return object.xmlText(options);
    }

    Random _picker = new Random();

    /**
     * Cursor position
     * Before:
     * <theElement>^</theElement>
     * After:
     * <theElement><lots of stuff/>^</theElement>
     */
    private void createSampleForType(SchemaType stype, XmlCursor xmlc) {
        if (_typeStack.contains(stype)) {
            return;
        }

        _typeStack.add(stype);

        try {
            if (stype.isSimpleType() || stype.isURType()) {
                processSimpleType(stype, xmlc);
                return;
            }

            // complex Type
            // <theElement>^</theElement>
            processAttributes(stype, xmlc);

            // <theElement attri1="string">^</theElement>
            switch (stype.getContentType()) {
                case SchemaType.NOT_COMPLEX_TYPE:
                case SchemaType.EMPTY_CONTENT:
                    // noop
                    break;
                case SchemaType.SIMPLE_CONTENT: {
                    processSimpleType(stype, xmlc);
                }
                break;
                case SchemaType.MIXED_CONTENT:
                    xmlc.insertChars(pick(WORDS) + " ");
                    if (stype.getContentModel() != null) {
                        processParticle(stype.getContentModel(), xmlc, true);
                    }
                    xmlc.insertChars(pick(WORDS));
                    break;
                case SchemaType.ELEMENT_CONTENT:
                    if (stype.getContentModel() != null) {
                        processParticle(stype.getContentModel(), xmlc, false);
                    }
                    break;
            }
        } finally {
            _typeStack.remove(_typeStack.size() - 1);
        }
    }

    private void processSimpleType(SchemaType stype, XmlCursor xmlc) {
        String sample = sampleDataForSimpleType(stype);
        xmlc.insertChars(sample);
    }

    private String sampleDataForSimpleType(SchemaType sType) {
        if (XmlObject.type.equals(sType)) {
            return "anyType";
        }

        if (XmlAnySimpleType.type.equals(sType)) {
            return "anySimpleType";
        }

        if (sType.getSimpleVariety() == SchemaType.LIST) {
            SchemaType itemType = sType.getListItemType();
            StringBuilder sb = new StringBuilder();
            int length = pickLength(sType);
            if (length > 0) {
                sb.append(sampleDataForSimpleType(itemType));
            }
            for (int i = 1; i < length; i += 1) {
                sb.append(' ');
                sb.append(sampleDataForSimpleType(itemType));
            }
            return sb.toString();
        }

        if (sType.getSimpleVariety() == SchemaType.UNION) {
            SchemaType[] possibleTypes = sType.getUnionConstituentTypes();
            if (possibleTypes.length == 0) {
                return "";
            }
            return sampleDataForSimpleType(possibleTypes[pick(possibleTypes.length)]);
        }

        XmlAnySimpleType[] enumValues = sType.getEnumerationValues();
        if (enumValues != null && enumValues.length > 0) {
            return enumValues[pick(enumValues.length)].getStringValue();
        }

        switch (sType.getPrimitiveType().getBuiltinTypeCode()) {
            default:
            case SchemaType.BTC_NOT_BUILTIN:
                return "";

            case SchemaType.BTC_ANY_TYPE:
            case SchemaType.BTC_ANY_SIMPLE:
                return "anything";

            case SchemaType.BTC_BOOLEAN:
                return pick(2) == 0 ? "true" : "false";

            case SchemaType.BTC_BASE_64_BINARY: {
                byte[] v = formatToLength(pick(WORDS), sType).getBytes(StandardCharsets.UTF_8);
                return Base64.getEncoder().encodeToString(v);
            }

            case SchemaType.BTC_HEX_BINARY:
                return HexBin.encode(formatToLength(pick(WORDS), sType));

            case SchemaType.BTC_ANY_URI:
                return formatToLength("http://www." + pick(DNS1) + "." + pick(DNS2) + "/" + pick(WORDS) + "/" + pick(WORDS), sType);

            case SchemaType.BTC_QNAME:
                return formatToLength("qname", sType);

            case SchemaType.BTC_NOTATION:
                return formatToLength("notation", sType);

            case SchemaType.BTC_FLOAT:
                return "1.5E2";
            case SchemaType.BTC_DOUBLE:
                return "1.051732E7";
            case SchemaType.BTC_DECIMAL:
                switch (closestBuiltin(sType).getBuiltinTypeCode()) {
                    case SchemaType.BTC_SHORT:
                        return formatDecimal("1", sType);
                    case SchemaType.BTC_UNSIGNED_SHORT:
                        return formatDecimal("5", sType);
                    case SchemaType.BTC_BYTE:
                        return formatDecimal("2", sType);
                    case SchemaType.BTC_UNSIGNED_BYTE:
                        return formatDecimal("6", sType);
                    case SchemaType.BTC_INT:
                        return formatDecimal("3", sType);
                    case SchemaType.BTC_UNSIGNED_INT:
                        return formatDecimal("7", sType);
                    case SchemaType.BTC_LONG:
                        return formatDecimal("10", sType);
                    case SchemaType.BTC_UNSIGNED_LONG:
                        return formatDecimal("11", sType);
                    case SchemaType.BTC_INTEGER:
                        return formatDecimal("100", sType);
                    case SchemaType.BTC_NON_POSITIVE_INTEGER:
                        return formatDecimal("-200", sType);
                    case SchemaType.BTC_NEGATIVE_INTEGER:
                        return formatDecimal("-201", sType);
                    case SchemaType.BTC_NON_NEGATIVE_INTEGER:
                        return formatDecimal("200", sType);
                    case SchemaType.BTC_POSITIVE_INTEGER:
                        return formatDecimal("201", sType);
                    default:
                    case SchemaType.BTC_DECIMAL:
                        return formatDecimal("1000.00", sType);
                }

            case SchemaType.BTC_STRING: {
                String result = closestBuiltin(sType).getBuiltinTypeCode() == SchemaType.BTC_TOKEN ? "token" : "string";

                return formatToLength(result, sType);
            }

            case SchemaType.BTC_DURATION:
                return formatDuration(sType);

            case SchemaType.BTC_DATE_TIME:
            case SchemaType.BTC_TIME:
            case SchemaType.BTC_DATE:
            case SchemaType.BTC_G_YEAR_MONTH:
            case SchemaType.BTC_G_YEAR:
            case SchemaType.BTC_G_MONTH_DAY:
            case SchemaType.BTC_G_DAY:
            case SchemaType.BTC_G_MONTH:
                return formatDate(sType);
        }
    }

    // a bit from the Aenid
    private static final String[] WORDS = {
            "ipsa", "iovis", "rapidum", "iaculata", "e", "nubibus", "ignem",
            "disiecitque", "rates", "evertitque", "aequora", "ventis",
            "illum", "exspirantem", "transfixo", "pectore", "flammas",
            "turbine", "corripuit", "scopuloque", "infixit", "acuto",
            "ast", "ego", "quae", "divum", "incedo", "regina", "iovisque",
            "et", "soror", "et", "coniunx", "una", "cum", "gente", "tot", "annos",
            "bella", "gero", "et", "quisquam", "numen", "iunonis", "adorat",
            "praeterea", "aut", "supplex", "aris", "imponet", "honorem",
            "talia", "flammato", "secum", "dea", "corde", "volutans",
            "nimborum", "in", "patriam", "loca", "feta", "furentibus", "austris",
            "aeoliam", "venit", "hic", "vasto", "rex", "aeolus", "antro",
            "luctantis", "ventos", "tempestatesque", "sonoras",
            "imperio", "premit", "ac", "vinclis", "et", "carcere", "frenat",
            "illi", "indignantes", "magno", "cum", "murmure", "montis",
            "circum", "claustra", "fremunt", "celsa", "sedet", "aeolus", "arce",
            "sceptra", "tenens", "mollitque", "animos", "et", "temperat", "iras",
            "ni", "faciat", "maria", "ac", "terras", "caelumque", "profundum",
            "quippe", "ferant", "rapidi", "secum", "verrantque", "per", "auras",
            "sed", "pater", "omnipotens", "speluncis", "abdidit", "atris",
            "hoc", "metuens", "molemque", "et", "montis", "insuper", "altos",
            "imposuit", "regemque", "dedit", "qui", "foedere", "certo",
            "et", "premere", "et", "laxas", "sciret", "dare", "iussus", "habenas",
        };


    private static final String[] DNS1 = new String[]{"corp", "your", "my", "sample", "company", "test", "any"};
    private static final String[] DNS2 = new String[]{"com", "org", "com", "gov", "org", "com", "org", "com", "edu"};

    private int pick(int n) {
        return _picker.nextInt(n);
    }

    private String pick(String[] a) {
        return a[pick(a.length)];
    }

    private int pickLength(SchemaType sType) {
        XmlInteger length = (XmlInteger) sType.getFacet(SchemaType.FACET_LENGTH);
        if (length != null) {
            return length.getBigIntegerValue().intValue();
        }
        XmlInteger min = (XmlInteger) sType.getFacet(SchemaType.FACET_MIN_LENGTH);
        XmlInteger max = (XmlInteger) sType.getFacet(SchemaType.FACET_MAX_LENGTH);
        int minInt, maxInt;
        if (min == null) {
            minInt = 0;
        } else {
            minInt = min.getBigIntegerValue().intValue();
        }
        if (max == null) {
            maxInt = Integer.MAX_VALUE;
        } else {
            maxInt = max.getBigIntegerValue().intValue();
        }
        // We try to keep the length of the array within reasonable limits,
        // at least 1 item and at most 3 if possible
        if (minInt == 0 && maxInt >= 1) {
            minInt = 1;
        }
        if (maxInt > minInt + 2) {
            maxInt = minInt + 2;
        }
        if (maxInt < minInt) {
            maxInt = minInt;
        }
        return minInt + pick(maxInt - minInt);
    }

    /**
     * Formats a given string to the required length, using the following operations:
     * - append the source string to itself as necessary to pass the minLength;
     * - truncate the result of previous step, if necessary, to keep it within minLength.
     */
    private String formatToLength(String s, SchemaType sType) {
        String result = s;
        try {
            SimpleValue min = (SimpleValue) sType.getFacet(SchemaType.FACET_LENGTH);
            if (min == null) {
                min = (SimpleValue) sType.getFacet(SchemaType.FACET_MIN_LENGTH);
            }
            if (min != null) {
                int len = min.getIntValue();
                while (result.length() < len) {
                    result = result + result;
                }
            }
            SimpleValue max = (SimpleValue) sType.getFacet(SchemaType.FACET_LENGTH);
            if (max == null) {
                max = (SimpleValue) sType.getFacet(SchemaType.FACET_MAX_LENGTH);
            }
            if (max != null) {
                int len = max.getIntValue();
                if (result.length() > len) {
                    result = result.substring(0, len);
                }
            }
        } catch (Exception ignored) {
            // intValue can be out of range
        }
        return result;
    }

    /**
     * The number of digits before the decimal point, which is the same for every
     * representation of a value.
     */
    private static long integerDigits(BigDecimal value) {
        return (long) value.precision() - value.scale();
    }

    /**
     * The largest value that fits in the given number of digits, ie 999...9. Computed as
     * 10^digits - 1 rather than built as a string of nines, which the maximum number of
     * characters allowed for a number rejects once the schema asks for more digits than
     * that - xsd:totalDigits is a positiveInteger and has no such ceiling.
     */
    private static BigDecimal widestValue(int digits) {
        return BigDecimal.TEN.pow(digits).subtract(BigDecimal.ONE);
    }

    private String formatDecimal(String start, SchemaType sType) {
        BigDecimal result = MathUtil.parseAsBigDecimal(start);
        XmlDecimal xmlD;
        xmlD = (XmlDecimal) sType.getFacet(SchemaType.FACET_MIN_INCLUSIVE);
        BigDecimal min = xmlD != null ? xmlD.getBigDecimalValue() : null;
        xmlD = (XmlDecimal) sType.getFacet(SchemaType.FACET_MAX_INCLUSIVE);
        BigDecimal max = xmlD != null ? xmlD.getBigDecimalValue() : null;
        boolean minInclusive = true, maxInclusive = true;
        xmlD = (XmlDecimal) sType.getFacet(SchemaType.FACET_MIN_EXCLUSIVE);
        if (xmlD != null) {
            BigDecimal minExcl = xmlD.getBigDecimalValue();
            if (min == null || min.compareTo(minExcl) < 0) {
                min = minExcl;
                minInclusive = false;
            }
        }
        xmlD = (XmlDecimal) sType.getFacet(SchemaType.FACET_MAX_EXCLUSIVE);
        if (xmlD != null) {
            BigDecimal maxExcl = xmlD.getBigDecimalValue();
            if (max == null || max.compareTo(maxExcl) > 0) {
                max = maxExcl;
                maxInclusive = false;
            }
        }
        xmlD = (XmlDecimal) sType.getFacet(SchemaType.FACET_TOTAL_DIGITS);
        int totalDigits = -1;
        if (xmlD != null) {
            totalDigits = MathUtil.toInt(xmlD.getBigDecimalValue());

            // the widest value the facet allows can only narrow a bound that is at least
            // as wide, so it is computed only when it can bind - which also keeps it away
            // from a totalDigits far larger than any bound the schema declares
            if (max != null && integerDigits(max) >= totalDigits) {
                BigDecimal digitsLimit = widestValue(totalDigits);
                if (max.compareTo(digitsLimit) > 0) {
                    max = digitsLimit;
                    maxInclusive = true;
                }
            }
            if (min != null && integerDigits(min) >= totalDigits) {
                BigDecimal digitsLimit = widestValue(totalDigits).negate();
                if (min.compareTo(digitsLimit) < 0) {
                    min = digitsLimit;
                    minInclusive = true;
                }
            }
        }

        int sigMin = min == null ? 1 : result.compareTo(min);
        int sigMax = max == null ? -1 : result.compareTo(max);
        boolean minOk = sigMin > 0 || sigMin == 0 && minInclusive;
        boolean maxOk = sigMax < 0 || sigMax == 0 && maxInclusive;

        // Compute the minimum increment
        xmlD = (XmlDecimal) sType.getFacet(SchemaType.FACET_FRACTION_DIGITS);
        int fractionDigits = -1;
        BigDecimal increment;
        if (xmlD == null) {
            increment = new BigDecimal(1);
        } else {
            fractionDigits = MathUtil.toInt(xmlD.getBigDecimalValue());
            if (fractionDigits > 0) {
                // 1 shifted fractionDigits places right, ie 0.00...1
                increment = BigDecimal.ONE.scaleByPowerOfTen(-fractionDigits);
            } else {
                increment = BigDecimal.ONE;
            }
        }

        //noinspection StatementWithEmptyBody
        if (minOk && maxOk) {
            // OK
        } else if (minOk && !maxOk) {
            // TOO BIG
            if (maxInclusive) {
                result = max;
            } else {
                result = max.subtract(increment);
            }
        } else if (!minOk && maxOk) {
            // TOO SMALL
            if (minInclusive) {
                result = min;
            } else {
                result = min.add(increment);
            }
        } else {
            // MIN > MAX!!
        }

        // We have the number
        // Adjust the scale according to the totalDigits and fractionDigits
        int digits = 0;
        BigDecimal ONE = new BigDecimal(BigInteger.ONE);
        for (BigDecimal n = result; n.abs().compareTo(ONE) >= 0; digits++) {
            n = n.movePointLeft(1);
        }

        if (fractionDigits > 0) {
            if (totalDigits >= 0) {
                result = result.setScale(Math.max(fractionDigits, totalDigits - digits));
            } else {
                result = result.setScale(fractionDigits);
            }
        } else if (fractionDigits == 0) {
            result = result.setScale(0);
        }

        return result.toString();
    }

    private String formatDuration(SchemaType sType) {
        XmlDuration d =
            (XmlDuration) sType.getFacet(SchemaType.FACET_MIN_INCLUSIVE);
        GDuration minInclusive = null;
        if (d != null) {
            minInclusive = d.getGDurationValue();
        }

        d = (XmlDuration) sType.getFacet(SchemaType.FACET_MAX_INCLUSIVE);
        GDuration maxInclusive = null;
        if (d != null) {
            maxInclusive = d.getGDurationValue();
        }

        d = (XmlDuration) sType.getFacet(SchemaType.FACET_MIN_EXCLUSIVE);
        GDuration minExclusive = null;
        if (d != null) {
            minExclusive = d.getGDurationValue();
        }

        d = (XmlDuration) sType.getFacet(SchemaType.FACET_MAX_EXCLUSIVE);
        GDuration maxExclusive = null;
        if (d != null) {
            maxExclusive = d.getGDurationValue();
        }

        GDurationBuilder gdurb = new GDurationBuilder();

        gdurb.setSecond(pick(800000));
        gdurb.setMonth(pick(20));

        // Years
        // Months
        // Days
        // Hours
        // Minutes
        // Seconds
        // Fractions
        if (minInclusive != null) {
            if (gdurb.getYear() < minInclusive.getYear()) {
                gdurb.setYear(minInclusive.getYear());
            }
            if (gdurb.getMonth() < minInclusive.getMonth()) {
                gdurb.setMonth(minInclusive.getMonth());
            }
            if (gdurb.getDay() < minInclusive.getDay()) {
                gdurb.setDay(minInclusive.getDay());
            }
            if (gdurb.getHour() < minInclusive.getHour()) {
                gdurb.setHour(minInclusive.getHour());
            }
            if (gdurb.getMinute() < minInclusive.getMinute()) {
                gdurb.setMinute(minInclusive.getMinute());
            }
            if (gdurb.getSecond() < minInclusive.getSecond()) {
                gdurb.setSecond(minInclusive.getSecond());
            }
            if (gdurb.getFraction().compareTo(minInclusive.getFraction()) < 0) {
                gdurb.setFraction(minInclusive.getFraction());
            }
        }

        if (maxInclusive != null) {
            if (gdurb.getYear() > maxInclusive.getYear()) {
                gdurb.setYear(maxInclusive.getYear());
            }
            if (gdurb.getMonth() > maxInclusive.getMonth()) {
                gdurb.setMonth(maxInclusive.getMonth());
            }
            if (gdurb.getDay() > maxInclusive.getDay()) {
                gdurb.setDay(maxInclusive.getDay());
            }
            if (gdurb.getHour() > maxInclusive.getHour()) {
                gdurb.setHour(maxInclusive.getHour());
            }
            if (gdurb.getMinute() > maxInclusive.getMinute()) {
                gdurb.setMinute(maxInclusive.getMinute());
            }
            if (gdurb.getSecond() > maxInclusive.getSecond()) {
                gdurb.setSecond(maxInclusive.getSecond());
            }
            if (gdurb.getFraction().compareTo(maxInclusive.getFraction()) > 0) {
                gdurb.setFraction(maxInclusive.getFraction());
            }
        }

        if (minExclusive != null) {
            if (gdurb.getYear() <= minExclusive.getYear()) {
                gdurb.setYear(minExclusive.getYear() + 1);
            }
            if (gdurb.getMonth() <= minExclusive.getMonth()) {
                gdurb.setMonth(minExclusive.getMonth() + 1);
            }
            if (gdurb.getDay() <= minExclusive.getDay()) {
                gdurb.setDay(minExclusive.getDay() + 1);
            }
            if (gdurb.getHour() <= minExclusive.getHour()) {
                gdurb.setHour(minExclusive.getHour() + 1);
            }
            if (gdurb.getMinute() <= minExclusive.getMinute()) {
                gdurb.setMinute(minExclusive.getMinute() + 1);
            }
            if (gdurb.getSecond() <= minExclusive.getSecond()) {
                gdurb.setSecond(minExclusive.getSecond() + 1);
            }
            if (gdurb.getFraction().compareTo(minExclusive.getFraction()) <= 0) {
                gdurb.setFraction(minExclusive.getFraction().add(new BigDecimal("0.001")));
            }
        }

        if (maxExclusive != null) {
            if (gdurb.getYear() > maxExclusive.getYear()) {
                gdurb.setYear(maxExclusive.getYear());
            }
            if (gdurb.getMonth() > maxExclusive.getMonth()) {
                gdurb.setMonth(maxExclusive.getMonth());
            }
            if (gdurb.getDay() > maxExclusive.getDay()) {
                gdurb.setDay(maxExclusive.getDay());
            }
            if (gdurb.getHour() > maxExclusive.getHour()) {
                gdurb.setHour(maxExclusive.getHour());
            }
            if (gdurb.getMinute() > maxExclusive.getMinute()) {
                gdurb.setMinute(maxExclusive.getMinute());
            }
            if (gdurb.getSecond() > maxExclusive.getSecond()) {
                gdurb.setSecond(maxExclusive.getSecond());
            }
            if (gdurb.getFraction().compareTo(maxExclusive.getFraction()) > 0) {
                gdurb.setFraction(maxExclusive.getFraction());
            }
        }

        gdurb.normalize();
        return gdurb.toString();
    }

    private String formatDate(SchemaType sType) {
        int btc = sType.getPrimitiveType().getBuiltinTypeCode();
        GDateBuilder gdateb = new GDateBuilder(new Date(1000L * pick(365 * 24 * 60 * 60) + (30L + pick(20)) * 365 * 24 * 60 * 60 * 1000));
        gdateb.setBuiltinTypeCode(btc);
        if (pick(2) == 0) {
            gdateb.clearTimeZone();
        }
        if (satisfiesBounds(gdateb, sType)) {
            return gdateb.toString();
        }

        // Step off a bound by the smallest unit the type shows
        GDuration[] steps;
        switch (btc) {
            case SchemaType.BTC_DATE_TIME:
                steps = new GDuration[]{new GDuration("P1D"), new GDuration("PT1H"), new GDuration("PT1M"),
                    new GDuration("PT1S"), new GDuration("PT0.001S")};
                break;
            case SchemaType.BTC_TIME:
                steps = new GDuration[]{new GDuration("PT1H"), new GDuration("PT1M"),
                    new GDuration("PT1S"), new GDuration("PT0.001S")};
                break;
            case SchemaType.BTC_G_YEAR:
                steps = new GDuration[]{new GDuration("P1Y")};
                break;
            case SchemaType.BTC_G_YEAR_MONTH:
            case SchemaType.BTC_G_MONTH:
                steps = new GDuration[]{new GDuration("P1M")};
                break;
            default:
                steps = new GDuration[]{new GDuration("P1D")};
                break;
        }

        GDate min = facetDate(sType, SchemaType.FACET_MIN_EXCLUSIVE);
        if (min == null) {
            min = facetDate(sType, SchemaType.FACET_MIN_INCLUSIVE);
        }
        GDate max = facetDate(sType, SchemaType.FACET_MAX_EXCLUSIVE);
        if (max == null) {
            max = facetDate(sType, SchemaType.FACET_MAX_INCLUSIVE);
        }

        for (GDate bound : new GDate[]{min, max}) {
            if (bound == null) {
                continue;
            }
            if (satisfiesBounds(bound, sType)) {
                return bound.toString();
            }
            for (GDuration step : steps) {
                GDateBuilder candidate = stepFrom(bound, step, bound == min, btc);
                if (candidate != null && satisfiesBounds(candidate, sType)) {
                    return candidate.toString();
                }
            }
        }

        // the bounds leave no value we can find
        return gdateb.toString();
    }

    private static GDate facetDate(SchemaType sType, int facet) {
        XmlObject x = sType.getFacet(facet);
        return x == null ? null : ((SimpleValue) x).getGDateValue();
    }

    // compareToGDate returns 2 for an incomparable pair, which satisfies no bound
    private static boolean satisfiesBounds(GDateSpecification d, SchemaType sType) {
        GDate g;
        int c;
        if ((g = facetDate(sType, SchemaType.FACET_MIN_EXCLUSIVE)) != null && d.compareToGDate(g) != 1) {
            return false;
        }
        if ((g = facetDate(sType, SchemaType.FACET_MIN_INCLUSIVE)) != null &&
            (c = d.compareToGDate(g)) != 0 && c != 1) {
            return false;
        }
        if ((g = facetDate(sType, SchemaType.FACET_MAX_EXCLUSIVE)) != null && d.compareToGDate(g) != -1) {
            return false;
        }
        return (g = facetDate(sType, SchemaType.FACET_MAX_INCLUSIVE)) == null ||
            (c = d.compareToGDate(g)) == 0 || c == -1;
    }

    private static GDateBuilder stepFrom(GDate bound, GDuration step, boolean up, int btc) {
        // date arithmetic needs a complete date and time, so fill in what the
        // type leaves out and drop it again afterwards; 2000 is a leap year, so
        // --02-29 survives
        GDateBuilder b = new GDateBuilder(bound);
        try {
            if (!b.hasYear()) {
                b.setYear(2000);
            }
            if (!b.hasMonth()) {
                b.setMonth(1);
            }
            if (!b.hasDay()) {
                b.setDay(1);
            }
            if (!b.hasTime()) {
                b.setTime(0, 0, 0, null);
            }
            if (up) {
                b.addGDuration(step);
            } else {
                b.subtractGDuration(step);
            }
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            // stepped past the range of years a GDate can hold
            return null;
        }
        b.setBuiltinTypeCode(btc);
        return b;
    }

    private SchemaType closestBuiltin(SchemaType sType) {
        while (!sType.isBuiltinType()) {
            sType = sType.getBaseType();
        }
        return sType;
    }

    /**
     * Cracks a combined QName of the form URL:localname
     */
    public static QName crackQName(String qName) {
        String ns;
        String name;

        int index = qName.lastIndexOf(':');
        if (index >= 0) {
            ns = qName.substring(0, index);
            name = qName.substring(index + 1);
        } else {
            ns = "";
            name = qName;
        }

        return new QName(ns, name);
    }


    /**
     * Cursor position:
     * Before this call:
     * <outer><foo/>^</outer>  (cursor at the ^)
     * After this call:
     * <<outer><foo/><bar/>som text<etc/>^</outer>
     */
    private void processParticle(SchemaParticle sp, XmlCursor xmlc, boolean mixed) {
        int loop = determineMinMaxForSample(sp, xmlc);

        while (loop-- > 0) {
            switch (sp.getParticleType()) {
                case (SchemaParticle.ELEMENT):
                    processElement(sp, xmlc, mixed);
                    break;
                case (SchemaParticle.SEQUENCE):
                    processSequence(sp, xmlc, mixed);
                    break;
                case (SchemaParticle.CHOICE):
                    processChoice(sp, xmlc, mixed);
                    break;
                case (SchemaParticle.ALL):
                    processAll(sp, xmlc, mixed);
                    break;
                case (SchemaParticle.WILDCARD):
                    processWildCard(sp, xmlc, mixed);
                    break;
                default:
                    // throw new Exception("No Match on Schema Particle Type: " + String.valueOf(sp.getParticleType()));
            }
        }
    }

    private int determineMinMaxForSample(SchemaParticle sp, XmlCursor xmlc) {
        int minOccurs = sp.getIntMinOccurs();
        int maxOccurs = sp.getIntMaxOccurs();

        if (minOccurs == maxOccurs) {
            return minOccurs;
        }

        int result = minOccurs;
        if (result == 0 && _nElements < MAX_ELEMENTS) {
            result = 1;
        }

        if (sp.getParticleType() != SchemaParticle.ELEMENT) {
            return result;
        }

        // it probably only makes sense to put comments in front of individual elements that repeat

        if (sp.getMaxOccurs() == null) {
            // xmlc.insertComment("The next " + getItemNameOrType(sp, xmlc) + " may be repeated " + minOccurs + " or more times");
            if (minOccurs == 0) {
                xmlc.insertComment("Zero or more repetitions:");
            } else {
                xmlc.insertComment(minOccurs + " or more repetitions:");
            }
        } else if (sp.getIntMaxOccurs() > 1) {
            xmlc.insertComment(minOccurs + " to " + sp.getMaxOccurs() + " repetitions:");
        } else {
            xmlc.insertComment("Optional:");
        }
        return result;
    }

    private void processElement(SchemaParticle sp, XmlCursor xmlc, boolean mixed) {
        // cast as schema local element
        SchemaLocalElement element = (SchemaLocalElement) sp;
        /// ^  -> <elemenname></elem>^
        if (_soapEnc) {
            xmlc.insertElement(element.getName().getLocalPart()); // soap encoded? drop namespaces.
        } else {
            xmlc.insertElement(element.getName().getLocalPart(), element.getName().getNamespaceURI());
        }
        _nElements++;
        /// -> <elem>^</elem>
        xmlc.toPrevToken();
        // -> <elem>stuff^</elem>

        createSampleForType(element.getType(), xmlc);
        // -> <elem>stuff</elem>^
        xmlc.toNextToken();

    }

    private static String formatQName(XmlCursor xmlc, QName qName) {
        String prefix;
        try (XmlCursor parent = xmlc.newCursor()) {
            parent.toParent();
            prefix = parent.prefixForNamespace(qName.getNamespaceURI());
        }
        String name;
        if (prefix == null || prefix.isEmpty()) {
            name = qName.getLocalPart();
        } else {
            name = prefix + ":" + qName.getLocalPart();
        }
        return name;
    }

    private static final QName HREF = new QName("href");
    private static final QName ID = new QName("id");
    private static final QName XSI_TYPE = new QName("http://www.w3.org/2001/XMLSchema-instance", "type");
    private static final QName ENC_ARRAYTYPE = new QName("http://schemas.xmlsoap.org/soap/encoding/", "arrayType");
    private static final QName ENC_OFFSET = new QName("http://schemas.xmlsoap.org/soap/encoding/", "offset");

    private static final Set<QName> SKIPPED_SOAP_ATTRS = new HashSet<>(Arrays.asList(HREF, ID, ENC_OFFSET));

    private void processAttributes(SchemaType stype, XmlCursor xmlc) {
        if (_soapEnc) {
            QName typeName = stype.getName();
            if (typeName != null) {
                xmlc.insertAttributeWithValue(XSI_TYPE, formatQName(xmlc, typeName));
            }
        }

        SchemaProperty[] attrProps = stype.getAttributeProperties();
        for (SchemaProperty attr : attrProps) {
            if (_soapEnc) {
                if (SKIPPED_SOAP_ATTRS.contains(attr.getName())) {
                    continue;
                }
                if (ENC_ARRAYTYPE.equals(attr.getName())) {
                    SOAPArrayType arrayType = ((SchemaWSDLArrayType) stype.getAttributeModel().getAttribute(attr.getName())).getWSDLArrayType();
                    if (arrayType != null) {
                        xmlc.insertAttributeWithValue(attr.getName(), formatQName(xmlc, arrayType.getQName()) + arrayType.soap11DimensionString());
                    }
                    continue;
                }
            }
            String defaultValue = attr.getDefaultText();
            xmlc.insertAttributeWithValue(attr.getName(), defaultValue == null ?
                sampleDataForSimpleType(attr.getType()) : defaultValue);
        }
    }

    private void processSequence(SchemaParticle sp, XmlCursor xmlc, boolean mixed) {
        SchemaParticle[] spc = sp.getParticleChildren();
        for (int i = 0; i < spc.length; i++) {
            /// <parent>maybestuff^</parent>
            processParticle(spc[i], xmlc, mixed);
            //<parent>maybestuff...morestuff^</parent>
            if (mixed && i < spc.length - 1) {
                xmlc.insertChars(pick(WORDS));
            }
        }
    }

    private void processChoice(SchemaParticle sp, XmlCursor xmlc, boolean mixed) {
        SchemaParticle[] spc = sp.getParticleChildren();
        xmlc.insertComment("You have a CHOICE of the next " + spc.length + " items at this level");
        for (SchemaParticle schemaParticle : spc) {
            processParticle(schemaParticle, xmlc, mixed);
        }
    }

    private void processAll(SchemaParticle sp, XmlCursor xmlc, boolean mixed) {
        SchemaParticle[] spc = sp.getParticleChildren();
        // xmlc.insertComment("You may enter the following " + String.valueOf(spc.length) + " items in any order");
        for (int i = 0; i < spc.length; i++) {
            processParticle(spc[i], xmlc, mixed);
            if (mixed && i < spc.length - 1) {
                xmlc.insertChars(pick(WORDS));
            }
        }
    }

    private void processWildCard(SchemaParticle sp, XmlCursor xmlc, boolean mixed) {
        xmlc.insertComment("You may enter ANY elements at this point");
        xmlc.insertElement("AnyElement");
    }

    private final ArrayList<SchemaType> _typeStack = new ArrayList<>();
}
