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

package scomp.derivation.restriction.detailed;

import org.apache.xmlbeans.XmlErrorCodes;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.Test;
import xbean.scomp.derivation.attributeWCRestriction.Any2ConcreteDocument;
import xbean.scomp.derivation.attributeWCRestriction.Any2LocalDocument;
import xbean.scomp.derivation.attributeWCRestriction.List2SubsetDocument;
import xbean.scomp.derivation.attributeWCRestriction.Other2ListDocument;

import static org.junit.jupiter.api.Assertions.*;
import static scomp.common.BaseCase.createOptions;
import static scomp.common.BaseCase.getErrorCodes;

public class AttrWCRestriction {
    /**
     * Replace a wildcard with a concrete attribute
     * No other attr should be valid here
     */
    @Test
    void testAny2Instance() throws Throwable {
        XmlOptions validateOptions = createOptions();
        String input =
            "<foo:Any2Concrete " +
            " xmlns:foo=\"http://xbean/scomp/derivation/AttributeWCRestriction\"" +
            " xmlns:at=\"http://xbean/scomp/attribute/GlobalAttrDefault\"" +
            " at:testattribute=\"XBean\"/>";
        Any2ConcreteDocument doc = Any2ConcreteDocument.Factory.parse(input);
        assertTrue(doc.validate(validateOptions));

        input =
            "<foo:Any2Concrete " +
            " xmlns:foo=\"http://xbean/scomp/derivation/AttributeWCRestriction\"" +
            " xmlns:at=\"http://xbean/scomp/attribute/GlobalAttrDefault\"" +
            " at:testatt=\"3\"/>";
        doc = Any2ConcreteDocument.Factory.parse(input);
        assertFalse(doc.validate(validateOptions));
        String[] errExpected = {XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$NO_WILDCARD};
        assertArrayEquals(errExpected, getErrorCodes(validateOptions));
    }

    /**
     * should be able to replace the any w/ local...
     * attrs not in target ns are therefore illegal
     * Skip to strict
     * A no-namespace attribute matches the wildcard, but the restriction's own anyAttribute is strict
     * (XML Schema 1.0 3.4.2) and no global no-namespace attribute is declared, so it is not resolved.
     * Any other namespace does not match the wildcard at all.
     */
    @Test
    void testAny2LocalStrict() throws Throwable {
        XmlOptions validateOptions = createOptions();
        String input =
            "<foo:Any2Local " +
            " xmlns:foo=\"http://xbean/scomp/derivation/AttributeWCRestriction\"" +
            " testattribute=\"XBean\"/>";
        Any2LocalDocument doc = Any2LocalDocument.Factory.parse(input);
        assertFalse(doc.validate(validateOptions));
        assertArrayEquals(new String[]{XmlErrorCodes.ASSESS_ATTR_SCHEMA_VALID$NOT_RESOLVED},
            getErrorCodes(validateOptions));

        //a diff ns is not OK
        validateOptions = createOptions();
        input =
            "<foo:Any2Local " +
            " xmlns:foo=\"http://xbean/scomp/derivation/AttributeWCRestriction\"" +
            " xmlns:at=\"http://xbean/scomp/attribute/GlobalAttrDefault\"" +
            " at:testattribute=\"XBean\"/>";
        doc = Any2LocalDocument.Factory.parse(input);
        assertFalse(doc.validate(validateOptions));
        String[] errExpected = {XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$NOT_WILDCARD_VALID};
        assertArrayEquals(errExpected, getErrorCodes(validateOptions));
    }

    /**
     * should be able to replace list of ns w/ subset
     * lax to strict
     */
    @Test
    void testList2SubsetStrict() throws Throwable {
        XmlOptions validateOptions = createOptions();
        String input =
            "<foo:List2Subset " +
            " xmlns:foo=\"http://xbean/scomp/derivation/AttributeWCRestriction\"" +
            " xmlns:at=\"http://ap.org\"" +
            " at:testattribute=\"XBean\"/>";
        List2SubsetDocument doc = List2SubsetDocument.Factory.parse(input);
        //this is a non-existing NS...strict should complain
        assertFalse(doc.validate(validateOptions));
        String[] errExpected = {XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$NOT_WILDCARD_VALID};
        assertArrayEquals(errExpected, getErrorCodes(validateOptions));

        //invalid case: give the attr a bad value, make sure it's being validated
    }

    /**
     * should be able to ##other w/ spec. namespaces
     * skip to lax
     */
    @Test
    void testOther2ListLax() throws Throwable {
        XmlOptions validateOptions = createOptions();
        String input =
            "<foo:Other2List" +
            " xmlns:foo=\"http://xbean/scomp/derivation/AttributeWCRestriction\"" +
            " xmlns:at=\"http://xbean/scomp/attribute/GlobalAttrDefault\"" +
            " at:testattribute=\"XBean\"/>";
        Other2ListDocument doc = Other2ListDocument.Factory.parse(input);
        assertTrue(doc.validate(validateOptions));

        //invalid case: a ns not in the list
        input =
            "<foo:Other2List" +
            " xmlns:foo=\"http://xbean/scomp/derivation/AttributeWCRestriction\"" +
            " xmlns:at=\"http://foobar\"" +
            " at:testattribute=\"XBean\"/>";
        doc = Other2ListDocument.Factory.parse(input);
        assertFalse(doc.validate(validateOptions));
        String[] errExpected = {XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$NOT_WILDCARD_VALID};
        assertArrayEquals(errExpected, getErrorCodes(validateOptions));
    }
}
