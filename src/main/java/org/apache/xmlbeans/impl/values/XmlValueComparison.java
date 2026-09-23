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

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlCursor.TokenType;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.common.XMLChar;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

/**
 * Compares complex values by structure, on behalf of
 * {@link XmlObjectBase#valueEquals(XmlObject)}.
 * <p>
 * The callers hold the monitor of both objects being compared, and every object
 * in a tree shares the monitor of its root, so nothing here locks again.
 */
final class XmlValueComparison {
    private XmlValueComparison() {
    }

    /**
     * Compares two complex values: their attributes, regardless of order, and
     * their content - the child elements in document order, each compared by
     * value, plus the text between them when the content is mixed.
     * <p>
     * Comments and processing instructions are ignored, as is whitespace that is
     * not part of mixed content.
     */
    static boolean complex_values_equal(XmlObject a, XmlObject b, boolean mixed) {
        if (!has_store(a) || !has_store(b)) {
            // a value that is not attached to a store has no content to walk
            return !has_store(a) && !has_store(b);
        }

        try (XmlCursor cursorA = a.newCursor();
             XmlCursor cursorB = b.newCursor()) {
            return attributes_equal(cursorA, cursorB) &&
                   content_equal(cursorA, cursorB, mixed);
        }
    }

    /**
     * Compares the attributes of two values whose content was compared
     * elsewhere, as is the case for a complex type with simple content: the
     * simple value implementation compares the text but knows nothing of the
     * attributes the type adds to it.
     * <p>
     * A value object without a store cannot carry attributes, so it is equal
     * only to a value that has none.
     */
    static boolean attributes_equal(XmlObject a, XmlObject b) {
        boolean storedA = has_store(a);
        boolean storedB = has_store(b);

        if (!storedA && !storedB) {
            return true;
        }
        if (!storedA) {
            return !has_attributes(b);
        }
        if (!storedB) {
            return !has_attributes(a);
        }

        try (XmlCursor cursorA = a.newCursor();
             XmlCursor cursorB = b.newCursor()) {
            return attributes_equal(cursorA, cursorB);
        }
    }

    private static boolean has_store(XmlObject o) {
        return !(o instanceof XmlObjectBase) || ((XmlObjectBase) o).has_store();
    }

    private static boolean has_attributes(XmlObject o) {
        try (XmlCursor c = o.newCursor()) {
            return c.toFirstAttribute();
        }
    }

    /**
     * Compares the attributes of the elements the cursors are on. Namespace
     * declarations are not attributes and take no part in the comparison. Both
     * cursors are left where they were found.
     */
    private static boolean attributes_equal(XmlCursor a, XmlCursor b) {
        Map<QName, XmlObject> attributesA = attributes(a);
        Map<QName, XmlObject> attributesB = attributes(b);

        if (!attributesA.keySet().equals(attributesB.keySet())) {
            return false;
        }

        for (Map.Entry<QName, XmlObject> attribute : attributesA.entrySet()) {
            if (!values_equal(attribute.getValue(), attributesB.get(attribute.getKey()))) {
                return false;
            }
        }

        return true;
    }

    private static Map<QName, XmlObject> attributes(XmlCursor c) {
        Map<QName, XmlObject> attributes = new HashMap<>();

        c.push();
        if (c.toFirstAttribute()) {
            do {
                attributes.put(c.getName(), c.getObject());
            } while (c.toNextAttribute());
        }
        c.pop();

        return attributes;
    }

    /**
     * Walks the content of both elements in step. The cursors are left at the
     * point where the walk stopped.
     */
    private static boolean content_equal(XmlCursor a, XmlCursor b, boolean mixed) {
        a.toFirstContentToken();
        b.toFirstContentToken();

        for (; ; ) {
            TokenType tokenA = skip_ignorable(a, mixed);
            TokenType tokenB = skip_ignorable(b, mixed);

            if (tokenA != tokenB) {
                return false;
            }

            if (tokenA.isText()) {
                // collect_text moves each cursor past the text it returns
                if (!collect_text(a, mixed).equals(collect_text(b, mixed))) {
                    return false;
                }
            } else if (tokenA.isStart()) {
                if (!a.getName().equals(b.getName()) ||
                    !values_equal(a.getObject(), b.getObject())) {
                    return false;
                }
                a.toEndToken();
                a.toNextToken();
                b.toEndToken();
                b.toNextToken();
            } else {
                // both sides ran out of content at the same place
                return true;
            }
        }
    }

    /**
     * Moves the cursor past what carries no value - comments, processing
     * instructions and, outside of mixed content, whitespace - and returns the
     * type of the token it comes to rest on.
     */
    private static TokenType skip_ignorable(XmlCursor c, boolean mixed) {
        for (; ; ) {
            TokenType token = c.currentTokenType();

            if (token.isComment() || token.isProcinst() ||
                (!mixed && token.isText() && is_whitespace(c.getChars()))) {
                c.toNextToken();
            } else {
                return token;
            }
        }
    }

    /**
     * Returns the run of text starting at the cursor, joining the chunks that a
     * comment or processing instruction splits it into, and leaves the cursor on
     * the token that ends the run.
     */
    private static String collect_text(XmlCursor c, boolean mixed) {
        StringBuilder text = new StringBuilder();

        for (; ; ) {
            TokenType token = c.currentTokenType();

            if (token.isText()) {
                String chars = c.getChars();
                if (mixed || !is_whitespace(chars)) {
                    text.append(chars);
                }
                c.toNextToken();
            } else if (token.isComment() || token.isProcinst()) {
                c.toNextToken();
            } else {
                return text.toString();
            }
        }
    }

    private static boolean is_whitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!XMLChar.isSpace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two values found inside the values being compared. valueEquals
     * would lock both trees again - and for trees in different synchronization
     * domains that means taking the global lock - once per child, for nothing.
     */
    private static boolean values_equal(XmlObject a, XmlObject b) {
        if (a == null || b == null) {
            return a == b;
        }

        try {
            if (a instanceof XmlObjectBase && b instanceof XmlObjectBase) {
                return ((XmlObjectBase) a).value_equals_locked(b);
            }
            return a.valueEquals(b);
        } catch (XmlValueOutOfRangeException e) {
            // A value that does not fit its type has no value space to be
            // compared in - compare what the document says instead, rather than
            // letting one bad value in a tree turn a comparison into a throw.
            return text_equal(a, b);
        }
    }

    private static boolean text_equal(XmlObject a, XmlObject b) {
        if (!has_store(a) || !has_store(b)) {
            return false;
        }

        try (XmlCursor cursorA = a.newCursor();
             XmlCursor cursorB = b.newCursor()) {
            return cursorA.getTextValue().equals(cursorB.getTextValue());
        }
    }
}
