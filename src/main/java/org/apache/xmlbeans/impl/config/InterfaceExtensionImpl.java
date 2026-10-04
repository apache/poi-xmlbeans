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

package org.apache.xmlbeans.impl.config;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.types.ResolvedType;
import org.apache.xmlbeans.InterfaceExtension;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.xb.xmlconfig.Extensionconfig;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InterfaceExtensionImpl implements InterfaceExtension {
    private NameSet _xbeanSet;
    private String _interfaceClassName;
    private String _delegateToClassName;
    private MethodSignatureImpl[] _methods;

    static InterfaceExtensionImpl newInstance(Parser loader, NameSet xbeanSet, Extensionconfig.Interface intfXO) {
        InterfaceExtensionImpl result = new InterfaceExtensionImpl();

        result._xbeanSet = xbeanSet;

        ClassOrInterfaceDeclaration interfaceJClass = validateInterface(loader, intfXO.getName(), intfXO);


        if (interfaceJClass == null)
        {
            BindingConfigImpl.error("Interface '" + intfXO.getStaticHandler() + "' not found.", intfXO);
            return null;
        }

        result._interfaceClassName = interfaceJClass.getFullyQualifiedName().get();

        result._delegateToClassName = intfXO.getStaticHandler();
        ClassOrInterfaceDeclaration delegateJClass = validateClass(loader, result._delegateToClassName, intfXO);

        if (delegateJClass == null) {
            // no HandlerClass
            BindingConfigImpl.warning("Handler class '" + intfXO.getStaticHandler() + "' not found on classpath, skip validation.", intfXO);
            return result;
        }

        if (!result.validateMethods(loader, interfaceJClass, delegateJClass, intfXO))
            return null;

        return result;
    }

    private static ClassOrInterfaceDeclaration validateInterface(Parser loader, String intfStr, XmlObject loc) {
        return validateJava(loader, intfStr, true, loc);
    }

    static ClassOrInterfaceDeclaration validateClass(Parser loader, String clsStr, XmlObject loc) {
        return validateJava(loader, clsStr, false, loc);
    }

    static ClassOrInterfaceDeclaration validateJava(Parser loader, String clsStr, boolean isInterface, XmlObject loc) {
        if (loader==null) {
            return null;
        }

        final String ent = isInterface ? "Interface" : "Class";
        ClassOrInterfaceDeclaration cls = loader.loadSource(clsStr);

        if (cls == null) {
            BindingConfigImpl.error(ent + " '" + clsStr + "' not found.", loc);
            return null;
        }

        if ( isInterface != cls.isInterface() ) {
            BindingConfigImpl.error("'" + clsStr + "' must be " + (isInterface ? "an interface" : "a class") + ".", loc);
        }

        if (!cls.isPublic()) {
            BindingConfigImpl.error(ent + " '" + clsStr + "' is not public.", loc);
        }

        return cls;
    }

    private boolean validateMethods(Parser loader, ClassOrInterfaceDeclaration interfaceJClass, ClassOrInterfaceDeclaration delegateJClass, XmlObject loc) {
        _methods = interfaceJClass.resolve().getAllMethods().stream()
            .filter(m -> !Object.class.getName().equals(m.declaringType().getQualifiedName()))
            .map(m -> validateMethod(interfaceJClass, delegateJClass, m, loc))
            .map(m -> m == null ? null : new MethodSignatureImpl(getStaticHandler(), m))
            .toArray(MethodSignatureImpl[]::new);

        return Stream.of(_methods).allMatch(Objects::nonNull);
    }

    private MethodDeclaration validateMethod(ClassOrInterfaceDeclaration interfaceJClass,
        ClassOrInterfaceDeclaration delegateJClass, MethodUsage ifMethod, XmlObject loc) {
        String methodName = ifMethod.getName();

        MethodDeclaration delMethod = delegateJClass.getMethodsByName(methodName).stream()
            .filter(mDel -> matchParams(ifMethod, mDel)).findFirst().orElse(null);

        String delegateFQN = delegateJClass.getFullyQualifiedName().orElse("");
        String methodFQN =  methodName + "(" + ifMethod.getParamTypes().toString() + ")";
        String interfaceFQN = interfaceJClass.getFullyQualifiedName().orElse("");

        if (delMethod == null) {
            BindingConfigImpl.error("Handler class '" + delegateFQN + "' does not contain method " + methodFQN, loc);
            return null;
        }

        // check for throws exceptions
        String[] ifEx = ifMethod.getDeclaration().getSpecifiedExceptions().stream().map(ResolvedType::describe).sorted().toArray(String[]::new);
        String[] delEx = delMethod.getThrownExceptions().stream().map(Type::resolve).map(ResolvedType::describe).sorted().toArray(String[]::new);

        if (!Arrays.equals(ifEx, delEx)) {
            BindingConfigImpl.error("Handler method '" + delegateFQN + "." + methodName + "' must declare the same " +
                                    "exceptions as the interface method '" + interfaceFQN + "." + methodFQN, loc);
            return null;
        }

        if (!delMethod.isPublic() || !delMethod.isStatic()) {
            BindingConfigImpl.error("Method '" + delegateFQN + "." + methodFQN + "' must be declared public and static.", loc);
            return null;
        }


        if (!ifMethod.getDeclaration().getReturnType().equals(delMethod.resolve().getReturnType())) {
            String returnType = ifMethod.getDeclaration().getReturnType().describe();
            BindingConfigImpl.error("Return type for method '" + returnType + " " + delegateFQN + "." + methodName +
                                    "(...)' does not match the return type of the interface method :'" + returnType + "'.", loc);
            return null;
        }

        return delMethod;
    }

//    private MethodDeclaration validateMethod(ClassOrInterfaceDeclaration interfaceJClass,
//         ClassOrInterfaceDeclaration delegateJClass, MethodDeclaration method, XmlObject loc) {
//
//        String methodName = method.getName().asString();
//
//        String[] delegateParams = Stream.concat(
//            Stream.of("org.apache.xmlbeans.XmlObject"),
//            Stream.of(paramStrings(method.getParameters()))
//        ).toArray(String[]::new);
//
//        MethodDeclaration handlerMethod = getMethod(delegateJClass, methodName, delegateParams);
//
//        String delegateFQN = delegateJClass.getFullyQualifiedName().orElse("");
//        String methodFQN =  methodName + "(" + method.getParameters().toString() + ")";
//        String interfaceFQN = interfaceJClass.getFullyQualifiedName().orElse("");
//
//        if (handlerMethod == null) {
//            BindingConfigImpl.error("Handler class '" + delegateFQN + "' does not contain method " + methodFQN, loc);
//            return null;
//        }
//
//        // check for throws exceptions
//        if (!Arrays.equals(exceptionStrings(method), exceptionStrings(handlerMethod))) {
//            BindingConfigImpl.error("Handler method '" + delegateFQN + "." + methodName + "' must declare the same " +
//            "exceptions as the interface method '" + interfaceFQN + "." + methodFQN, loc);
//            return null;
//        }
//
//        if (!handlerMethod.isPublic() || !handlerMethod.isStatic()) {
//            BindingConfigImpl.error("Method '" + delegateJClass.getFullyQualifiedName() + "." +
//            methodFQN + "' must be declared public and static.", loc);
//            return null;
//        }
//
//        String returnType = method.getTypeAsString();
//        if (!returnType.equals(handlerMethod.getTypeAsString())) {
//            BindingConfigImpl.error("Return type for method '" + returnType + " " + delegateFQN + "." + methodName +
//            "(...)' does not match the return type of the interface method :'" + returnType + "'.", loc);
//            return null;
//        }
//
//        return method;
//    }

    static MethodDeclaration getMethod(ClassOrInterfaceDeclaration cls, String name, String[] paramTypes) {
        // cls.getMethodsBySignature only checks the type name as-is ... i.e. if the type name is imported
        // only the simple name is checked, otherwise the full qualified name
        return cls.getMethodsByName(name).stream()
            .filter(m -> parameterMatches(paramStrings(m.getParameters()), paramTypes))
            .findFirst().orElse(null);
    }

    private static String[] paramStrings(NodeList<?> params) {
        return params.stream().map(p -> {
            if (p instanceof Parameter param) {
                return param.getType().resolve().describe();
            } else if (p instanceof TypeParameter typeParam) {
                return typeParam.getNameAsString();
            } else {
                return "unknown";
            }
        }).toArray(String[]::new);
    }

    private static boolean matchParams(MethodUsage mIf, MethodDeclaration mDel) {
        // the delegate needs to have the XmlObject as first parameter
        List<ResolvedType> pIf = mIf.getParamTypes();
        NodeList<Parameter> pDel = mDel.getParameters();
        if (pDel.size() != pIf.size()+1) {
            return false;
        }

        if (!XmlObject.class.getName().equals(pDel.get(0).resolve().describeType())) {
            return false;
        }

        // the other parameters need to match
        int idx = 1;
        for (ResolvedType rt : pIf) {
            if (!rt.describe().equals(pDel.get(idx++).resolve().describeType())) {
                return false;
            }
        }
        return true;
    }

    private static boolean parameterMatches(String[] params1, String[] params2) {
        // compare all parameters type strings
        // a type string can be a simple name (e.g. "XmlObject") or
        // fully qualified name ("org.apache.xmlbeans.XmlObject")
        // try to loosely match the names
        if (params1.length != params2.length) {
            return false;
        }
        for (int i=0; i<params1.length; i++) {
            String p1 = params1[i];
            String p2 = params2[i];
            if (p1.contains(".")) {
                String tmp = p1;
                p1 = p2;
                p2 = tmp;
            }
            if (!p2.endsWith(p1)) {
                return false;
            }
        }
        return true;
    }

    /* public getters */
    public boolean contains(String fullJavaName) {
        return _xbeanSet.contains(fullJavaName);
    }

    public String getStaticHandler() {
        return _delegateToClassName;
    }

    public String getInterface() {
        return _interfaceClassName;
    }

    public InterfaceExtension.MethodSignature[] getMethods() {
        return _methods;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("  static handler: ").append(_delegateToClassName).append('\n');
        buf.append("  interface: ").append(_interfaceClassName).append('\n');
        buf.append("  name set: ").append(_xbeanSet).append('\n');

        for (int i = 0; i < _methods.length; i++)
            buf.append("  method[").append(i).append("]=").append(_methods[i]).append('\n');

        return buf.toString();
    }

    // this is used only for detecting method colisions of extending interfaces
    static class MethodSignatureImpl implements InterfaceExtension.MethodSignature {
        private final String _intfName;
        private final int NOTINITIALIZED = -1;
        private int _hashCode = NOTINITIALIZED;
        private String _signature;

        private final String _name;
        private final String _return;
        private final String[] _params;
        private final String[] _paramNames;
        private final String[] _exceptions;

        MethodSignatureImpl(String intfName, MethodDeclaration method) {
            if (intfName==null || method==null) {
                throw new IllegalArgumentException("Interface: " + intfName + " method: " + method);
            }

            _intfName = intfName;
            _signature = null;

            _name = method.getName().asString();
            String typeParams = method.getTypeParameters().stream().map(TypeParameter::toString).collect(Collectors.joining(", "));
            _return = (typeParams.isEmpty() ? "" : ( " <" + typeParams + "> ") ) +
                    replaceInner(method.getType().resolve().describe());

            _params = method.getParameters().stream().map(p -> p.getType().resolve().describe())
                    .map(MethodSignatureImpl::replaceInner).toArray(String[]::new);

            _exceptions = method.getThrownExceptions().stream().map(e -> e.resolve().describe())
                    .map(MethodSignatureImpl::replaceInner).toArray(String[]::new);

            _paramNames = method.getParameters().stream().map(p -> p.getNameAsString()).toArray(String[]::new);
        }

        private static String replaceInner(String classname) {
            return classname.replace('$', '.');
        }

        String getInterfaceName() {
            return _intfName;
        }

        public String getName() {
            return _name;
        }

        public String getReturnType() {
            return _return;
        }

        public String[] getParameterTypes() {
            return _params;
        }

        public String[] getParameterNames() {
            return _paramNames;
        }

        public String[] getExceptionTypes() {
            return _exceptions;
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (!(o instanceof MethodSignatureImpl)) {
                return false;
            }
            MethodSignatureImpl ms = (MethodSignatureImpl)o;

            return ms.getName().equals(getName()) &&
                   _intfName.equals(ms._intfName) &&
                   Arrays.equals(getParameterTypes(),ms.getParameterTypes());
        }

        public int hashCode() {
            return (_hashCode!=NOTINITIALIZED) ? _hashCode :
                (_hashCode = Objects.hash(getName(), Arrays.hashCode(getParameterTypes()), _intfName));
        }

        String getSignature() {
            return (_signature!=null) ? _signature :
                (_signature = _name+"("+String.join(" ,", _params)+")");
        }

        public String toString() {
            return getReturnType() + " " + getSignature();
        }
    }
}
