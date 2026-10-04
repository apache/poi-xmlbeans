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

package org.apache.xmlbeans;

import javax.xml.namespace.QName;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * This interface represents a lattice of finite and infinite sets of QNames.
 * The lattice the minimal one that is closed under union, intersection, and
 * inverse, and contains individual QNames as well as entire namespaces.
 * Here is a summary of the two kinds of QNameSets:
 * <ul>
 * <li>A QNameSet can cover a finite set of namespaces, additionally including a finite
 *     set of QNames outside those namespaces, and with the exception of
 *     a finite set of QNames excluded from those namespaces:
 *   <ul>
 *   <li>excludedQNamesInIncludedURIs == the set of excluded QNames from coveredURIs namespaces
 *   <li>excludedURIs == null
 *   <li>includedURIs == the set of covered namespace URIs
 *   <li>includedQNamesInExcludedURIs == set of additional QNames outside coveredURIs namespaces
 *   </ul>
 * </li>
 * <li>A QNameSet can cover all namespaces except for a finite number of excluded ones,
 *     additionally including a finite set of QNames within the excluded namespaces,
 *     and with the exception of a finite set of QNames outside the excluded namespaces:
 *   <ul>
 *   <li>excludedQNamesInIncludedURIs == the set of excluded QNames outside uncoveredURIs namespaces
 *   <li>excludedURIs == the set of uncovered namespace URIs
 *   <li>includedURIs == null
 *   <li>includedQNamesInExcludedURIs == set of additional QNames from uncoveredURIs namespaces
 *   </ul>
 * </li>
 * </ul>
 * <p>
 * Notice that a finite set of QNames is a degenerate case of the first
 * category outlined above:
 * <ul>
 * <li>A QnameSet can contain a finite number of QNames:
 *   <ul>
 *   <li>excludedQNamesInIncludedURIs == empty set
 *   <li>excludedURIs == null
 *   <li>includedURIs == empty set
 *   <li>includedQNamesInExcludedURIs == set of included QNames
 *   </ul>
 * </li>
 * </ul>
 *
 * @see QNameSetBuilder
 */
public final class QNameSet implements QNameSetSpecification, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean _inverted;
    private final Set<String> _includedURIs;
    private final Set<QName> _excludedQNames;
    private final Set<QName> _includedQNames;

    /**
     * The empty QNameSet.
     */
    public static final QNameSet EMPTY = new QNameSet(null, Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

    /**
     * The QNameSet containing all QNames.
     */
    public static final QNameSet ALL = new QNameSet(Collections.emptySet(), null, Collections.emptySet(), Collections.emptySet());

    /**
     * The QNameSet containing all QNames in the local (no-)namespace.
     */
    public static final QNameSet LOCAL = new QNameSet(null, Collections.singleton(""), Collections.emptySet(), Collections.emptySet());

    /**
     * The QNameSet containing all QNames except for those in the local (no-)namespace.
     */
    public static final QNameSet NONLOCAL = new QNameSet(Collections.singleton(""), null, Collections.emptySet(), Collections.emptySet());

    /**
     * Private function to minimize object creation when copying sets.
     */
    private static <T> Set<T> minSetCopy(Set<T> original) {
        if (original == null) {
            return null;
        }
        if (original.isEmpty()) {
            return Collections.emptySet();
        }
        if (original.size() == 1) {
            return Collections.singleton(original.iterator().next());
        }
        return new HashSet<>(original);
    }

    /**
     * Returns a QNameSet based on the given sets of excluded URIs,
     * included URIs, excluded QNames in included namespaces, and included
     * QNames in excluded namespaces.
     *
     * @param excludedURIs                 the finite set of namespace URI strings to exclude from the set, or null if this set is infinite
     * @param includedURIs                 the finite set of namespace URI strings to include in the set, or null if this set is infinite
     * @param excludedQNamesInIncludedURIs the finite set of exceptional QNames to exclude from the included namespaces
     * @param includedQNamesInExcludedURIs the finite set of exceptional QNames to include that are in the excluded namespaces
     * @return the constructed QNameSet
     */
    public static QNameSet forSets(Set<String> excludedURIs, Set<String> includedURIs, Set<QName> excludedQNamesInIncludedURIs, Set<QName> includedQNamesInExcludedURIs) {
        if ((excludedURIs != null) == (includedURIs != null)) {
            throw new IllegalArgumentException("Exactly one of excludedURIs and includedURIs must be null");
        }

        if (excludedURIs == null && includedURIs.isEmpty() && includedQNamesInExcludedURIs.isEmpty()) {
            return EMPTY;
        }
        if (includedURIs == null && excludedURIs.isEmpty() && excludedQNamesInIncludedURIs.isEmpty()) {
            return ALL;
        }
        if (excludedURIs == null && includedURIs.size() == 1 && includedURIs.contains("") &&
            includedQNamesInExcludedURIs.isEmpty() && excludedQNamesInIncludedURIs.isEmpty()) {
            return LOCAL;
        }
        if (includedURIs == null && excludedURIs.size() == 1 && excludedURIs.contains("") &&
            excludedQNamesInIncludedURIs.isEmpty() && includedQNamesInExcludedURIs.isEmpty()) {
            return NONLOCAL;
        }

        return new QNameSet(
            minSetCopy(excludedURIs),
            minSetCopy(includedURIs),
            minSetCopy(excludedQNamesInIncludedURIs),
            minSetCopy(includedQNamesInExcludedURIs));
    }

    /**
     * Returns a QNameSet based on the given array of included QNames
     *
     * @param includedQNames the array of included QNames
     */
    public static QNameSet forArray(QName[] includedQNames) {
        if (includedQNames == null) {
            throw new IllegalArgumentException("includedQNames cannot be null");
        }

        return new QNameSet(null, Collections.emptySet(), Collections.emptySet(), new HashSet<>(Arrays.asList(includedQNames)));
    }

    /**
     * Returns a QNameSet with the same contents as the given
     * QNameSetSpecification.
     *
     * @return the copied QNameSet
     */
    public static QNameSet forSpecification(QNameSetSpecification spec) {
        if (spec instanceof QNameSet set) {
            return set;
        }
        return QNameSet.forSets(spec.excludedURIs(), spec.includedURIs(), spec.excludedQNamesInIncludedURIs(), spec.includedQNamesInExcludedURIs());
    }

    /**
     * Returns a QNameSet corresponding to the given wildcard namespace string.
     * This is a space-separated list of URIs, plus special tokens as specified
     * in the XML Schema specification (##any, ##other, ##targetNamespace, ##local).
     *
     * @return the constructed QNameSet
     */
    public static QNameSet forWildcardNamespaceString(String wildcard, String targetURI) {
        return QNameSet.forSpecification(new QNameSetBuilder(wildcard, targetURI));
    }

    /**
     * Returns a QNameSet containing only the given QName.
     *
     * @return the constructed QNameSet
     */
    public static QNameSet singleton(QName name) {
        return new QNameSet(null, Collections.emptySet(), Collections.emptySet(), Collections.singleton(name));
    }

    /**
     * Constructs a QNameSetBuilder whose contents are given by
     * the four sets.
     * <p>
     * This constructor is PRIVATE because it uses the given
     * sets directly, and it trusts its callers to set only immutable values.
     * This constructor is only called by the static builder methods on
     * QNameSet: those methods are all careful assign only unchanging sets.
     */
    private QNameSet(Set<String> excludedURIs, Set<String> includedURIs, Set<QName> excludedQNamesInIncludedURIs, Set<QName> includedQNamesInExcludedURIs) {
        if (includedURIs != null && excludedURIs == null) {
            _inverted = false;
            _includedURIs = includedURIs;
            _excludedQNames = excludedQNamesInIncludedURIs;
            _includedQNames = includedQNamesInExcludedURIs;
        } else if (excludedURIs != null && includedURIs == null) {
            _inverted = true;
            _includedURIs = excludedURIs;
            _excludedQNames = includedQNamesInExcludedURIs;
            _includedQNames = excludedQNamesInIncludedURIs;
        } else {
            throw new IllegalArgumentException("Exactly one of excludedURIs and includedURIs must be null");
        }
    }

    /**
     * Local xml names are hashed using "" as the namespace.
     */
    private static String nsFromName(QName xmlName) {
        String ns = xmlName.getNamespaceURI();
        return ns == null ? "" : ns;
    }

    /**
     * True if this ModelTransitionSet contains the given qname.
     */
    public boolean contains(QName name) {
        boolean in = _includedURIs.contains(nsFromName(name)) ?
            !_excludedQNames.contains(name) :
            _includedQNames.contains(name);
        return _inverted ^ in;
    }

    /**
     * True if this ModelTransitionSet contains all QNames.
     */
    public boolean isAll() {
        return _inverted && _includedURIs.isEmpty() && _includedQNames.isEmpty();
    }

    /**
     * True if this ModelTransitionSet contains no QNames.
     */
    public boolean isEmpty() {
        return !_inverted && _includedURIs.isEmpty() && _includedQNames.isEmpty();
    }

    /**
     * Returns a new QNameSet that is the intersection of this one and another.
     *
     * @param set the set to intersect with
     * @return the intersection
     */
    public QNameSet intersect(QNameSetSpecification set) {
        QNameSetBuilder result = new QNameSetBuilder(this);
        result.restrict(set);
        return result.toQNameSet();
    }

    /**
     * Returns a new QNameSet that is the union of this one and another.
     *
     * @param set the set to union with
     * @return the union
     */
    public QNameSet union(QNameSetSpecification set) {
        QNameSetBuilder result = new QNameSetBuilder(this);
        result.addAll(set);
        return result.toQNameSet();
    }

    /**
     * Returns a new QNameSet that is the inverse of this one.
     */
    public QNameSet inverse() {
        if (this == EMPTY) {
            return ALL;
        }
        if (this == ALL) {
            return EMPTY;
        }
        if (this == LOCAL) {
            return NONLOCAL;
        }
        if (this == NONLOCAL) {
            return LOCAL;
        }
        return new QNameSet(includedURIs(), excludedURIs(), includedQNamesInExcludedURIs(), excludedQNamesInIncludedURIs());
    }

    /**
     * True if the given set is a subset of this one.
     *
     * @param set the set to test
     * @return true if this contains all QNames contained by the given set
     */
    public boolean containsAll(QNameSetSpecification set) {
        // a.contains(b) == a.inverse.isDisjoint(b)
        if (!_inverted && set.excludedURIs() != null) {
            return false;
        }

        return inverse().isDisjoint(set);
    }

    /**
     * True if the given set is disjoint from this one.
     *
     * @param set the set to test
     * @return true if the set is disjoint from this set
     */
    public boolean isDisjoint(QNameSetSpecification set) {
        if (_inverted && set.excludedURIs() != null) {
            return false;
        }

        if (_inverted) {
            return isDisjointImpl(set, this);
        } else {
            return isDisjointImpl(this, set);
        }
    }

    private boolean isDisjointImpl(QNameSetSpecification set1, QNameSetSpecification set2) {
        Set<String> includeURIs = set1.includedURIs();
        Set<String> otherIncludeURIs = set2.includedURIs();

        if (otherIncludeURIs != null) {
            if (!Collections.disjoint(includeURIs, otherIncludeURIs)) {
                return false;
            }
        } else {
            if (!set2.excludedURIs().containsAll(includeURIs)) {
                return false;
            }
        }

        if (set1.includedQNamesInExcludedURIs().stream().anyMatch(set2::contains)) {
            return false;
        }

        return set2.includedQNamesInExcludedURIs().stream().noneMatch(set1::contains);
    }


    /**
     * Namespaces that are fully excluded from the set except for a finite
     * number of individual QName exceptions.  Returns null if this set is infinite.
     *
     * @return the set of excluded namespace URI strings
     */
    public Set<String> excludedURIs() {
        if (_inverted) {
            return Collections.unmodifiableSet(_includedURIs);
        }
        return null;
    }

    /**
     * Namespaces that are fully included in set except for a finite
     * number of individual QName exceptions. Returns null if this set is infinite.
     *
     * @return the set of included namespace URI strings
     */
    public Set<String> includedURIs() {
        if (!_inverted) {
            return _includedURIs;
        }
        return null;
    }

    /**
     * The set of QNames excluded from the set even though they are within
     * a namespace that is otherwise fully included in the set.
     *
     * @return the set of excluded QNames from within includedURI namespaces
     */
    public Set<QName> excludedQNamesInIncludedURIs() {
        return Collections.unmodifiableSet(_inverted ? _includedQNames : _excludedQNames);
    }

    /**
     * The set of QNames included in the set even though they are within
     * a namespace that is otherwise fully included in the set.
     *
     * @return the set of included QNames from within excludedURI namespaces
     */
    public Set<QName> includedQNamesInExcludedURIs() {
        return Collections.unmodifiableSet(_inverted ? _excludedQNames : _includedQNames);
    }

    private String prettyQName(QName name) {
        if (name.getNamespaceURI() == null) {
            return name.getLocalPart();
        }
        return name.getLocalPart() + "@" + name.getNamespaceURI();
    }

    /**
     * Returns a string representation useful for debugging, subject to change.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("QNameSet");
        sb.append(_inverted ? "-(" : "+(");
        for (String includedURIs : _includedURIs) {
            sb.append("+*@");
            sb.append(includedURIs);
            sb.append(", ");
        }
        for (QName excludedQName : _excludedQNames) {
            sb.append("-");
            sb.append(prettyQName(excludedQName));
            sb.append(", ");
        }
        for (QName includedQName : _includedQNames) {
            sb.append("+");
            sb.append(prettyQName(includedQName));
            sb.append(", ");
        }
        int index = sb.lastIndexOf(", ");
        if (index > 0) {
            sb.setLength(index);
        }
        sb.append(')');
        return sb.toString();
    }
}
