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

package misc.checkin;

import org.apache.xmlbeans.impl.regex.ParseException;
import org.apache.xmlbeans.impl.regex.RegularExpression;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegularExpressionTest {

    @Test
    void testLongString() {
        RegularExpression regex = new RegularExpression("[A-Z0-9]+");
        String rnd = randomString(10000);
        assertTrue(regex.matches(rnd));
    }

    @Test
    void testLookbehindRangeAtInputEnd() {
        // a lookbehind containing a character class, evaluated at the end of the
        // input, used to read one character past the string in the RANGE op and
        // throw StringIndexOutOfBoundsException
        assertTrue(new RegularExpression("(?<=[a-c])$").matches("abc"));
        assertTrue(new RegularExpression(".*(?<=[0-9])").matches("ab9"));
        // the same off-by-one read also returned the wrong match result: the char
        // before the lookbehind is 'x', not in [a-c], so this must not match
        assertFalse(new RegularExpression("x(?<=[a-c])").matches("xc"));
    }

    @Test
    void testHexEscapeOverflowErrorKey() {
        // a \v escape (6 hex digits) whose value is above U+10FFFF was reported with
        // the error key "parser.descappe.4", which is not in the message bundle, so
        // ResourceBundle.getString threw MissingResourceException instead of the
        // ParseException callers expect. The sibling \x{...} path already reports the
        // same condition cleanly via "parser.descape.4".
        assertThrows(ParseException.class, () -> new RegularExpression("\\v110000"));
        assertThrows(ParseException.class, () -> new RegularExpression("\\vFFFFFF"));
        assertThrows(ParseException.class, () -> new RegularExpression("\\x{110000}"));
        // the top of the Unicode range is still valid and must parse
        new RegularExpression("\\v10FFFF");
    }

    @Test
    void testQuantifierOverflow() {
        // a {min,max} count larger than Integer.MAX_VALUE overflowed the int
        // accumulator. the only guard was a post-multiply min<0/max<0 check, so
        // counts that wrapped to a non-negative value slipped through: "a{4294967296}"
        // parsed as "a{0}" (matched the empty string) and "a{1,4294967298}" as "a{1,2}",
        // while bigger ones such as "a{99999999999}" blew the heap at match time.
        assertThrows(ParseException.class, () -> new RegularExpression("a{4294967296}"));
        assertThrows(ParseException.class, () -> new RegularExpression("a{4294967297}"));
        assertThrows(ParseException.class, () -> new RegularExpression("a{99999999999}"));
        assertThrows(ParseException.class, () -> new RegularExpression("a{1,4294967298}"));
        // counts up to Integer.MAX_VALUE are representable and must still parse
        new RegularExpression("a{2147483647}");
        new RegularExpression("a{0,2147483647}");
        assertTrue(new RegularExpression("a{2,4}").matches("aaa"));
    }


    @Test
    void testLongClosureIsLinear() {
        // each closure iteration records its offset to stop zero-width repeats; those
        // offsets used to be kept in a list that was scanned on every iteration, which
        // made this match quadratic (a 50,000 char name took seconds, 1M chars minutes)
        String s = "a".repeat(1_000_000);
        RegularExpression regex = new RegularExpression("\\c+", "X");
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> assertTrue(regex.matches(s)));
    }

    @Test
    void testCharClassAboveLatin1() {
        // chars >= 256 are looked up by binary search of the class ranges rather
        // than the latin-1 bitmap, so check every code point against java.util.regex
        String ranges = "a-zà-ŐЀ-ӿぁ-ゖ一-鿿😀-🙏";
        RegularExpression pos = new RegularExpression("[" + ranges + "]", "X");
        RegularExpression neg = new RegularExpression("[^" + ranges + "]", "X");
        java.util.regex.Pattern expected = java.util.regex.Pattern.compile("[" + ranges + "]");
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (Character.getType(cp) == Character.SURROGATE) {
                continue;
            }
            String s = new String(Character.toChars(cp));
            boolean in = expected.matcher(s).matches();
            assertEquals(in, pos.matches(s), () -> "positive class, code point " + Integer.toHexString(s.codePointAt(0)));
            assertEquals(!in, neg.matches(s), () -> "negated class, code point " + Integer.toHexString(s.codePointAt(0)));
        }
    }

    @Test
    void testWordEscapeDefaultsToLetterAndDigitTables() {
        // without xmlbean.regex.strictword, \w keeps the XML 1.0 Letter and Digit tables
        RegularExpression word = new RegularExpression("\\w+", "X");
        RegularExpression notWord = new RegularExpression("\\W+", "X");
        assertFalse(word.matches("+"));
        assertTrue(notWord.matches("+"));
        assertFalse(word.matches("\u017F"));
        assertTrue(word.matches("a1"));
    }

    @Test
    void testWordEscapeFollowsSchemaDefinition() {
        System.setProperty("xmlbean.regex.strictword", "true");
        try {
            checkStrictWordEscape();
        } finally {
            System.clearProperty("xmlbean.regex.strictword");
        }
    }

    private static void checkStrictWordEscape() {
        // XML Schema defines \w as [#x0000-#x10FFFF]-[\p{P}\p{Z}\p{C}]: everything that
        // is not punctuation, a separator or "other". The schema-mode parser builds it
        // from the XML 1.0 Letter and Digit tables by default, so symbols, marks,
        // letter-like numbers and letters added after Unicode 2.0 are outside \w while
        // \W and [^\w] match them (the same bug as XERCESJ-1651 in the Xerces port)
        RegularExpression word = new RegularExpression("\\w+", "X");
        RegularExpression notWord = new RegularExpression("\\W+", "X");
        RegularExpression negated = new RegularExpression("[^\\w]+", "X");
        for (int cp = 0; cp < 0x10000; cp++) {
            int type = Character.getType(cp);
            if (type == Character.SURROGATE) {
                continue;
            }
            String s = String.valueOf((char) cp);
            boolean in;
            switch (type) {
                case Character.CONNECTOR_PUNCTUATION:
                case Character.DASH_PUNCTUATION:
                case Character.START_PUNCTUATION:
                case Character.END_PUNCTUATION:
                case Character.INITIAL_QUOTE_PUNCTUATION:
                case Character.FINAL_QUOTE_PUNCTUATION:
                case Character.OTHER_PUNCTUATION:
                case Character.SPACE_SEPARATOR:
                case Character.LINE_SEPARATOR:
                case Character.PARAGRAPH_SEPARATOR:
                case Character.CONTROL:
                case Character.FORMAT:
                case Character.PRIVATE_USE:
                case Character.UNASSIGNED:
                    in = false;
                    break;
                default:
                    in = true;
            }
            String hex = Integer.toHexString(cp);
            assertEquals(in, word.matches(s), () -> "\\w, code point " + hex);
            assertEquals(!in, notWord.matches(s), () -> "\\W, code point " + hex);
            assertEquals(!in, negated.matches(s), () -> "[^\\w], code point " + hex);
        }
        // a few of the characters the old tables got wrong, spelled out
        for (String s : new String[]{"^", "+", "|", "$", "\u0301", "\u2167", "\u01C5", "\u017F", "\uFB00"}) {
            assertTrue(word.matches(s), s);
            assertFalse(notWord.matches(s), s);
        }
        for (String s : new String[]{".", "-", "(", "\u00AB", " ", "\u00A0", "\t", "\u200B"}) {
            assertFalse(word.matches(s), s);
            assertTrue(notWord.matches(s), s);
        }
    }

    private static final String AB ="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Random rnd = new Random();

    private String randomString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(AB.charAt(rnd.nextInt(AB.length())));
        }
        return sb.toString();
    }
}
