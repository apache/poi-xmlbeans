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

import org.apache.xmlbeans.impl.common.PrefixResolver;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlCursor;

import java.util.ArrayList;
import java.util.Map;
import java.lang.reflect.Proxy;

import org.apache.xmlbeans.xml.stream.StartElement;

public class NamespaceContext implements PrefixResolver
{
    private static final int TYPE_STORE    = 1;
    private static final int XML_OBJECT    = 2;
    private static final int MAP           = 3;
    private static final int START_ELEMENT = 4;
    private static final int RESOLVER      = 5;

    private final Object _obj;
    private final int _code;

    public NamespaceContext(Map prefixToUriMap)
    {
        _code = MAP;
        _obj = prefixToUriMap;
    }

    public NamespaceContext(TypeStore typeStore)
    {
        _code = TYPE_STORE;
        _obj = typeStore;
    }

    public NamespaceContext(XmlObject xmlObject)
    {
        _code = XML_OBJECT;
        _obj = xmlObject;
    }

    public NamespaceContext(StartElement start)
    {
        _code = START_ELEMENT;
        _obj = start;
    }

    public NamespaceContext(PrefixResolver resolver)
    {
        _code = RESOLVER;
        _obj = resolver;
    }

    /**
     * Stack management if (heaven help us) we ever need to do
     * nested compilation of schema type system.
     */
    private static final class NamespaceContextStack
    {
        NamespaceContext current;
        ArrayList<NamespaceContext> stack = new ArrayList<>();
        final void push(NamespaceContext next)
        {
            stack.add(current);
            current = next;
        }
        final void pop()
        {
            // single remove() so a failure cannot leave current and stack out of step
            current = stack.remove(stack.size() - 1);
        }
    }

    private static final ThreadLocal<NamespaceContextStack> tl_namespaceContextStack =
            new ThreadLocal<>();

    public static void clearThreadLocals() {
        tl_namespaceContextStack.remove();
    }

    private static NamespaceContextStack getNamespaceContextStack()
    {
        NamespaceContextStack namespaceContextStack = tl_namespaceContextStack.get();
        if (namespaceContextStack==null)
        {
            namespaceContextStack = new NamespaceContextStack();
            tl_namespaceContextStack.set(namespaceContextStack);
        }
        return namespaceContextStack;
    }

    public static void push(NamespaceContext next)
    {
        getNamespaceContextStack().push(next);
    }

    public static void pop()
    {
        NamespaceContextStack nsContextStack = tl_namespaceContextStack.get();
        if (nsContextStack == null || nsContextStack.stack.isEmpty())
        {
            // unbalanced pop - don't throw (it would mask the original exception), just clean up
            tl_namespaceContextStack.remove();
            return;
        }

        nsContextStack.pop();

        if (nsContextStack.stack.isEmpty())
            tl_namespaceContextStack.remove();
    }

    public static PrefixResolver getCurrent()
    {
        // don't create a stack here: nothing would ever remove it from the thread-local
        NamespaceContextStack nsContextStack = tl_namespaceContextStack.get();
        return nsContextStack == null ? null : nsContextStack.current;
    }

    /**
     * @return true if the current thread has namespace context state (for tests)
     */
    static boolean hasThreadLocalState()
    {
        return tl_namespaceContextStack.get() != null;
    }

    public String getNamespaceForPrefix(String prefix)
    {
        if (prefix != null && prefix.equals("xml"))
            return "http://www.w3.org/XML/1998/namespace";

        switch (_code)
        {
            case XML_OBJECT:
            {
                TypeStoreUser impl;
                Object obj = _obj;
                if (Proxy.isProxyClass(obj.getClass()))
                    obj = Proxy.getInvocationHandler(obj);

                if (obj instanceof TypeStoreUser user)
                    return user.get_store().getNamespaceForPrefix(prefix);

                try (XmlCursor cur = ((XmlObject)_obj).newCursor()) {
                    if (cur != null)
                    {
                        if (cur.currentTokenType() == XmlCursor.TokenType.ATTR)
                            cur.toParent();
                        return cur.namespaceForPrefix(prefix);
                    }
                }
            }

            case MAP:
                return (String)((Map)_obj).get(prefix);

            case TYPE_STORE:
                return ((TypeStore)_obj).getNamespaceForPrefix(prefix);

            case START_ELEMENT:
                return ((StartElement)_obj).getNamespaceUri(prefix);

            case RESOLVER:
                return ((PrefixResolver)_obj).getNamespaceForPrefix(prefix);

            default:
                assert false : "Improperly initialized NamespaceContext.";
                return null;
        }
    }
}
