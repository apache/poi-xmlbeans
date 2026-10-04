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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// XMLBEANS-676
public class NamespaceContextTest {

    @AfterEach
    void cleanUp() {
        NamespaceContext.clearThreadLocals();
    }

    @Test
    void getCurrentDoesNotCreateThreadLocalState() {
        assertNull(NamespaceContext.getCurrent());
        assertFalse(NamespaceContext.hasThreadLocalState());
    }

    @Test
    void nestedPushPopClearsThreadLocalState() {
        NamespaceContext outer = new NamespaceContext(Collections.singletonMap("a", "urn:a"));
        NamespaceContext inner = new NamespaceContext(Collections.singletonMap("b", "urn:b"));

        NamespaceContext.push(outer);
        NamespaceContext.push(inner);
        assertSame(inner, NamespaceContext.getCurrent());
        assertEquals("urn:b", NamespaceContext.getCurrent().getNamespaceForPrefix("b"));
        NamespaceContext.pop();
        assertSame(outer, NamespaceContext.getCurrent());
        assertTrue(NamespaceContext.hasThreadLocalState());
        NamespaceContext.pop();

        assertNull(NamespaceContext.getCurrent());
        assertFalse(NamespaceContext.hasThreadLocalState());
    }

    @Test
    void unbalancedPopDoesNotThrowOrLeak() {
        assertDoesNotThrow(NamespaceContext::pop);
        assertFalse(NamespaceContext.hasThreadLocalState());

        NamespaceContext.push(new NamespaceContext(Collections.emptyMap()));
        NamespaceContext.pop();
        assertDoesNotThrow(NamespaceContext::pop);
        assertFalse(NamespaceContext.hasThreadLocalState());
    }
}
