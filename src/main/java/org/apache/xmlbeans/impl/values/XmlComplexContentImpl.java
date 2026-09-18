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
import org.apache.xmlbeans.impl.schema.SchemaTypeImpl;
import org.apache.xmlbeans.impl.schema.SchemaTypeVisitorImpl;

import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;

public class XmlComplexContentImpl extends XmlObjectBase {
    public XmlComplexContentImpl(SchemaType type) {
        _schemaType = (SchemaTypeImpl) type;
        initComplexType(true, true);
    }

    public SchemaType schemaType() {
        return _schemaType;
    }

    private final SchemaTypeImpl _schemaType;

    public String compute_text(NamespaceManager nsm) {
        return null;
    }

    protected final void set_String(String v) {
        assert _schemaType.getContentType() != SchemaType.SIMPLE_CONTENT;

        if (_schemaType.getContentType() != SchemaType.MIXED_CONTENT &&
            !_schemaType.isNoType()) {
            throw new IllegalArgumentException(
                "Type does not allow for textual content: " + _schemaType);
        }

        super.set_String(v);
    }

    public void set_text(String str) {
        assert
            _schemaType.getContentType() == SchemaType.MIXED_CONTENT ||
            _schemaType.isNoType();
    }

    protected void update_from_complex_content() {
        // No complex caching yet ...
    }

    public void set_nil() { /* BUGBUG: what to do? */ }

    /**
     * Compares two complex values by structure: the attributes, regardless of
     * their order, and the child elements in document order - each compared by
     * value, so that a child written in a different lexical form but with the
     * same value still matches - plus, for mixed content, the text between them.
     * <p>
     * Comments and processing instructions are ignored, as is whitespace that is
     * not part of mixed content.
     */
    public boolean equal_to(XmlObject complexObject) {
        if (complexObject == this) {
            return true;
        }
        if (complexObject == null) {
            return false;
        }

        SchemaType otherType = complexObject.schemaType();
        if (otherType == null || otherType.isSimpleType()) {
            return false;
        }

        boolean mixed = is_mixed(_schemaType) || is_mixed(otherType);

        return XmlValueComparison.complex_values_equal(this, complexObject, mixed);
    }

    private static boolean is_mixed(SchemaType type) {
        return type.getContentType() == SchemaType.MIXED_CONTENT;
    }

    // LEFT
    protected int value_hash_code() {
        throw new IllegalStateException("Complex types cannot be used as hash keys");
    }

    // DONE
    public TypeStoreVisitor new_visitor() {
        return new SchemaTypeVisitorImpl(_schemaType.getContentModel());
    }

    // DONE
    public boolean is_child_element_order_sensitive() {
        return schemaType().isOrderSensitive();
    }

    public int get_elementflags(QName eltName) {
        SchemaProperty prop = schemaType().getElementProperty(eltName);
        if (prop == null) {
            return 0;
        }
        if (prop.hasDefault() == SchemaProperty.VARIABLE ||
            prop.hasFixed() == SchemaProperty.VARIABLE ||
            prop.hasNillable() == SchemaProperty.VARIABLE) {
            return -1;
        }
        return
            (prop.hasDefault() == SchemaProperty.NEVER ? 0 : TypeStore.HASDEFAULT) |
            (prop.hasFixed() == SchemaProperty.NEVER ? 0 : TypeStore.FIXED) |
            (prop.hasNillable() == SchemaProperty.NEVER ? 0 : TypeStore.NILLABLE);
    }

    // DONE
    public String get_default_attribute_text(QName attrName) {
        return super.get_default_attribute_text(attrName);
    }

    // DONE
    public String get_default_element_text(QName eltName) {
        SchemaProperty prop = schemaType().getElementProperty(eltName);
        if (prop == null) {
            return "";
        }
        return prop.getDefaultText();
    }

    //
    // Code gen helpers
    //
    // So much redundant code ..... what I'd give for generics!
    //

    protected void unionArraySetterHelper(Object[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setObjectValue);
    }

    protected SimpleValue[] arraySetterHelper(int sourcesLength, QName elemName) {
        SimpleValue[] sources = new SimpleValue[sourcesLength];
        commonSetterHelper(elemName, null, sources, (u, i) -> sources[i] = u);
        return sources;
    }

    protected SimpleValue[] arraySetterHelper(int sourcesLength, QName elemName, QNameSet set) {
        SimpleValue[] sources = new SimpleValue[sourcesLength];
        commonSetterHelper(elemName, set, sources, (u, i) -> sources[i] = u);
        return sources;
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(boolean[] sources, QName elemName) {
        commonSetterHelper(elemName, null, (sources == null) ? 0 : sources.length, (u, i) -> u.setBooleanValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(float[] sources, QName elemName) {
        commonSetterHelper(elemName, null, (sources == null) ? 0 : sources.length, (u, i) -> u.setFloatValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(double[] sources, QName elemName) {
        commonSetterHelper(elemName, null, (sources == null) ? 0 : sources.length, (u, i) -> u.setDoubleValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(byte[] sources, QName elemName) {
        commonSetterHelper(elemName, null, (sources == null) ? 0 : sources.length, (u, i) -> u.setByteValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(short[] sources, QName elemName) {
        commonSetterHelper(elemName, null, (sources == null) ? 0 : sources.length, (u, i) -> u.setShortValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(int[] sources, QName elemName) {
        commonSetterHelper(elemName, null, (sources == null) ? 0 : sources.length, (u, i) -> u.setIntValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(long[] sources, QName elemName) {
        commonSetterHelper(elemName, null, (sources == null) ? 0 : sources.length, (u, i) -> u.setLongValue(sources[i]));
    }

    protected void arraySetterHelper(BigDecimal[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setBigDecimalValue);
    }

    protected void arraySetterHelper(BigInteger[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setBigIntegerValue);
    }

    protected void arraySetterHelper(String[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setStringValue);
    }

    protected void arraySetterHelper(byte[][] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setByteArrayValue);
    }

    protected void arraySetterHelper(GDate[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setGDateValue);
    }

    protected void arraySetterHelper(GDuration[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setGDurationValue);
    }

    protected void arraySetterHelper(Calendar[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setCalendarValue);
    }

    protected void arraySetterHelper(Date[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setDateValue);
    }

    protected void arraySetterHelper(QName[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setQNameValue);
    }

    protected void arraySetterHelper(StringEnumAbstractBase[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setEnumValue);
    }


    protected void arraySetterHelper(List<?>[] sources, QName elemName) {
        commonSetterHelper2(elemName, null, sources, XmlObjectBase::setListValue);
    }

    protected void unionArraySetterHelper(Object[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setObjectValue);
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(boolean[] sources, QName elemName, QNameSet set) {
        commonSetterHelper(elemName, set, (sources == null) ? 0 : sources.length, (u, i) -> u.setBooleanValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(float[] sources, QName elemName, QNameSet set) {
        commonSetterHelper(elemName, set, (sources == null) ? 0 : sources.length, (u, i) -> u.setFloatValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(double[] sources, QName elemName, QNameSet set) {
        commonSetterHelper(elemName, set, (sources == null) ? 0 : sources.length, (u, i) -> u.setDoubleValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(byte[] sources, QName elemName, QNameSet set) {
        commonSetterHelper(elemName, set, (sources == null) ? 0 : sources.length, (u, i) -> u.setByteValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(short[] sources, QName elemName, QNameSet set) {
        commonSetterHelper(elemName, set, (sources == null) ? 0 : sources.length, (u, i) -> u.setShortValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(int[] sources, QName elemName, QNameSet set) {
        commonSetterHelper(elemName, set, (sources == null) ? 0 : sources.length, (u, i) -> u.setIntValue(sources[i]));
    }

    @SuppressWarnings("ConstantConditions")
    protected void arraySetterHelper(long[] sources, QName elemName, QNameSet set) {
        commonSetterHelper(elemName, set, (sources == null) ? 0 : sources.length, (u, i) -> u.setLongValue(sources[i]));
    }

    protected void arraySetterHelper(BigDecimal[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setBigDecimalValue);
    }

    protected void arraySetterHelper(BigInteger[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setBigIntegerValue);
    }

    protected void arraySetterHelper(String[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setStringValue);
    }

    protected void arraySetterHelper(byte[][] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setByteArrayValue);
    }

    protected void arraySetterHelper(GDate[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setGDateValue);
    }

    protected void arraySetterHelper(GDuration[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setGDurationValue);
    }

    protected void arraySetterHelper(Calendar[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setCalendarValue);
    }

    protected void arraySetterHelper(Date[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setDateValue);
    }

    protected void arraySetterHelper(QName[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setQNameValue);
    }

    protected void arraySetterHelper(StringEnumAbstractBase[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setEnumValue);
    }

    protected void arraySetterHelper(List<?>[] sources, QName elemName, QNameSet set) {
        commonSetterHelper2(elemName, set, sources, XmlObjectBase::setListValue);
    }

    protected void arraySetterHelper(XmlObject[] sources, QName elemName) {
        arraySetterHelper(sources, elemName, null);
    }

    protected void arraySetterHelper(XmlObject[] sources, QName elemName, QNameSet set) {
        TypeStore store = get_store();

        if (sources == null || sources.length == 0) {
            int m = (set == null) ? store.count_elements(elemName) : store.count_elements(set);
            for (; m > 0; m--) {
                if (set == null) {
                    store.remove_element(elemName, 0);
                } else {
                    store.remove_element(set, 0);
                }
            }
            return;
        }

        // Verify if the sources contain children of this node
        int i;
        // how many elements in the original array
        int m = (set == null) ? store.count_elements(elemName) : store.count_elements(set);
        int startSrc = 0, startDest = 0;
        for (i = 0; i < sources.length; i++) {
            if (sources[i].isImmutable()) {
                continue;
            }
            try (XmlCursor c = sources[i].newCursor()) {
                if (c.toParent() && c.getObject() == this) {
                    break;
                }
            }
        }
        if (i < sources.length) {
            TypeStoreUser current = (set == null) ? store.find_element_user(elemName, 0) : store.find_element_user(set, 0);
            if (current == sources[i]) {
                // The new object matches what already exists in the array
                // Heuristic: we optimize for the case where the new elements
                // in the array are the same as the existing elements with
                // potentially new elements inserted

                // First insert the new element in the array at position 0
                int j;
                for (j = 0; j < i; j++) {
                    TypeStoreUser user = (set == null) ? store.insert_element_user(elemName, j) : store.insert_element_user(set, elemName, j);
                    ((XmlObjectBase) user).set(sources[j]);
                }
                for (i++, j++; i < sources.length; i++, j++) {
                    // Cursor is implicitly closed
                    XmlCursor c = sources[i].isImmutable() ? null : sources[i].newCursor();
                    if (c != null && c.toParent() && c.getObject() == this) {
                        c.close();
                        current = (set == null) ? store.find_element_user(elemName, j) : store.find_element_user(set, j);
                        if (current != sources[i]) {
                            // Fall back to the general case
                            break;
                        }
                    } else {
                        if (c != null) {
                            c.close();
                        }
                        // Insert before the current element
                        TypeStoreUser user = (set == null) ? store.insert_element_user(elemName, j) : store.insert_element_user(set, elemName, j);
                        ((XmlObjectBase) user).set(sources[i]);
                    }
                }
                startDest = j;
                startSrc = i;
                m = store.count_elements(elemName);
            }
            // Fall through
        } else {
            // All of the elements in the existing array are to
            // be deleted and replaced with elements from the
            // sources array
        }

        // The general case: we assume that some of the elements
        // in the new array already exist, but at different indexes

        // Starting with position i in the sources array, copy the remaining elements
        // to the end of the original array...
        for (int j = i; j < sources.length; j++) {
            TypeStoreUser user = store.add_element_user(elemName);
            ((XmlObjectBase) user).set(sources[j]);
        }

        // ... then come back and insert the elements starting with startSource
        // up to i from the sources array into the current array, starting with
        // startDest
        int n = i;
        for (; m > n - startSrc + startDest; m--) {
            if (set == null) {
                store.remove_element(elemName, m - 1);
            } else {
                store.remove_element(set, m - 1);
            }
        }

        int j;
        for (i = startSrc, j = startDest; i < n; i++, j++) {
            TypeStoreUser user;

            if (j >= m) {
                user = store.add_element_user(elemName);
            } else if (set == null) {
                user = store.find_element_user(elemName, j);
            } else {
                user = store.find_element_user(set, j);
            }

            ((XmlObjectBase) user).set(sources[i]);
        }

        // We can't just delegate to array_setter because we need
        // synchronization on the sources (potentially each element
        // in the array on a different lock)
        // get_store().array_setter( sources, elemName );
    }


    private <T> void commonSetterHelper(QName elemName, QNameSet set, T[] sources, BiConsumer<XmlObjectBase, Integer> fun) {
        commonSetterHelper(elemName, set, (sources == null) ? 0 : sources.length, fun);
    }

    private void commonSetterHelper(QName elemName, QNameSet set, int n, BiConsumer<XmlObjectBase, Integer> fun) {
        TypeStore store = get_store();

        int m = (set == null) ? store.count_elements(elemName) : store.count_elements(set);

        for (; m > n; m--) {
            if (set == null) {
                store.remove_element(elemName, m - 1);
            } else {
                store.remove_element(set, m - 1);
            }
        }

        for (int i = 0; i < n; i++) {
            TypeStoreUser user;

            if (i >= m) {
                user = store.add_element_user(elemName);
            } else if (set == null) {
                user = store.find_element_user(elemName, i);
            } else {
                user = store.find_element_user(set, i);
            }
            fun.accept((XmlObjectBase) user, i);
        }
    }

    private <T> void commonSetterHelper2(QName elemName, QNameSet set, T[] sources, BiConsumer<XmlObjectBase, T> c) {
        int n = (sources == null) ? 0 : sources.length;

        TypeStore store = get_store();

        int m = (set == null) ? store.count_elements(elemName) : store.count_elements(set);

        for (; m > n; m--) {
            if (set == null) {
                store.remove_element(elemName, m - 1);
            } else {
                store.remove_element(set, m - 1);
            }
        }

        for (int i = 0; i < n; i++) {
            TypeStoreUser user;

            if (i >= m) {
                user = store.add_element_user(elemName);
            } else if (set == null) {
                user = store.find_element_user(elemName, i);
            } else {
                user = store.find_element_user(set, i);
            }
            c.accept((XmlObjectBase) user, sources[i]);
        }
    }
}
