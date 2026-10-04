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

package org.apache.xmlbeans.impl.schema;

import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.common.NameUtil;

import javax.xml.namespace.QName;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.apache.xmlbeans.impl.schema.SchemaTypeSystemImpl.*;

class SchemaTypePool {
    private final SchemaTypeSystemImpl typeSystem;
    private final Map<String, SchemaComponent.Ref> _handlesToRefs = new LinkedHashMap<>();
    // populated on write
    private final Map<SchemaComponent, String> _componentsToHandles = new LinkedHashMap<>();
    private boolean _started;


    /**
     * Constructs an empty HandlePool to be populated.
     */
    SchemaTypePool(SchemaTypeSystemImpl typeSystem) {
        this.typeSystem = typeSystem;
    }

    private String addUniqueHandle(SchemaComponent obj, String base) {
        // we lowercase handles because of case-insensitive Windows filenames!!!
        base = base.toLowerCase(Locale.ROOT);
        String handle = base;
        for (int index = 2; _handlesToRefs.containsKey(handle); index++) {
            handle = base + index;
        }
        _handlesToRefs.put(handle, obj.getComponentRef());
        _componentsToHandles.put(obj, handle);
        return handle;
    }

    String handleForComponent(SchemaComponent comp) {
        if (comp == null) {
            return null;
        }
        if (comp.getTypeSystem() != typeSystem) {
            throw new IllegalArgumentException("Cannot supply handles for types from another type system");
        }
        if (comp instanceof SchemaType type) {
            return handleForType(type);
        }
        if (comp instanceof SchemaGlobalElement element) {
            return handleForElement(element);
        }
        if (comp instanceof SchemaGlobalAttribute attribute) {
            return handleForAttribute(attribute);
        }
        if (comp instanceof SchemaModelGroup group) {
            return handleForModelGroup(group);
        }
        if (comp instanceof SchemaAttributeGroup group) {
            return handleForAttributeGroup(group);
        }
        if (comp instanceof SchemaIdentityConstraint constraint) {
            return handleForIdentityConstraint(constraint);
        }
        throw new IllegalStateException("Component type cannot have a handle");
    }

    String handleForElement(SchemaGlobalElement element) {
        if (element == null) {
            return null;
        }
        if (element.getTypeSystem() != typeSystem) {
            throw new IllegalArgumentException("Cannot supply handles for types from another type system");
        }
        String handle = _componentsToHandles.get(element);
        if (handle == null) {
            handle = addUniqueHandle(element, NameUtil.upperCamelCase(element.getName().getLocalPart()) + "Element");
        }
        return handle;
    }

    String handleForAttribute(SchemaGlobalAttribute attribute) {
        if (attribute == null) {
            return null;
        }
        if (attribute.getTypeSystem() != typeSystem) {
            throw new IllegalArgumentException("Cannot supply handles for types from another type system");
        }
        String handle = _componentsToHandles.get(attribute);
        if (handle == null) {
            handle = addUniqueHandle(attribute, NameUtil.upperCamelCase(attribute.getName().getLocalPart()) + "Attribute");
        }
        return handle;
    }

    String handleForModelGroup(SchemaModelGroup group) {
        if (group == null) {
            return null;
        }
        if (group.getTypeSystem() != typeSystem) {
            throw new IllegalArgumentException("Cannot supply handles for types from another type system");
        }
        String handle = _componentsToHandles.get(group);
        if (handle == null) {
            handle = addUniqueHandle(group, NameUtil.upperCamelCase(group.getName().getLocalPart()) + "ModelGroup");
        }
        return handle;
    }

    String handleForAttributeGroup(SchemaAttributeGroup group) {
        if (group == null) {
            return null;
        }
        if (group.getTypeSystem() != typeSystem) {
            throw new IllegalArgumentException("Cannot supply handles for types from another type system");
        }
        String handle = _componentsToHandles.get(group);
        if (handle == null) {
            handle = addUniqueHandle(group, NameUtil.upperCamelCase(group.getName().getLocalPart()) + "AttributeGroup");
        }
        return handle;
    }

    String handleForIdentityConstraint(SchemaIdentityConstraint idc) {
        if (idc == null) {
            return null;
        }
        if (idc.getTypeSystem() != typeSystem) {
            throw new IllegalArgumentException("Cannot supply handles for types from another type system");
        }
        String handle = _componentsToHandles.get(idc);
        if (handle == null) {
            handle = addUniqueHandle(idc, NameUtil.upperCamelCase(idc.getName().getLocalPart()) + "IdentityConstraint");
        }
        return handle;
    }

    String handleForType(SchemaType type) {
        if (type == null) {
            return null;
        }
        if (type.getTypeSystem() != typeSystem) {
            throw new IllegalArgumentException("Cannot supply handles for types from another type system");
        }
        String handle = _componentsToHandles.get(type);
        if (handle == null) {
            QName name = type.getName();
            String suffix = "";
            if (name == null) {
                if (type.isDocumentType()) {
                    name = type.getDocumentElementName();
                    suffix = "Doc";
                } else if (type.isAttributeType()) {
                    name = type.getAttributeTypeAttributeName();
                    suffix = "AttrType";
                } else if (type.getContainerField() != null) {
                    name = type.getContainerField().getName();
                    suffix = type.getContainerField().isAttribute() ? "Attr" : "Elem";
                }
            }

            String baseName;
            String uniq = Integer.toHexString(type.toString().hashCode() | 0x80000000).substring(4).toUpperCase(Locale.ROOT);
            if (name == null) {
                baseName = "Anon" + uniq + "Type";
            } else {
                baseName = NameUtil.upperCamelCase(name.getLocalPart()) + uniq + suffix + "Type";
            }

            handle = addUniqueHandle(type, baseName);
        }

        return handle;
    }

    SchemaComponent.Ref refForHandle(String handle) {
        if (handle == null) {
            return null;
        }

        return _handlesToRefs.get(handle);
    }

    void startWriteMode() {
        _started = true;
        _componentsToHandles.clear();
        for (String handle : _handlesToRefs.keySet()) {
            SchemaComponent comp = _handlesToRefs.get(handle).getComponent();
            _componentsToHandles.put(comp, handle);
        }
    }

    void writeHandlePool(XsbReader reader) {
        reader.writeShort(_componentsToHandles.size());
        _componentsToHandles.forEach((comp, handle) -> {
            reader.writeString(handle);
            reader.writeShort(fileTypeFromComponentType(comp.getComponentType()));
        });
    }

    int fileTypeFromComponentType(int componentType) {
        switch (componentType) {
            case SchemaComponent.TYPE:
                return SchemaTypeSystemImpl.FILETYPE_SCHEMATYPE;
            case SchemaComponent.ELEMENT:
                return SchemaTypeSystemImpl.FILETYPE_SCHEMAELEMENT;
            case SchemaComponent.ATTRIBUTE:
                return SchemaTypeSystemImpl.FILETYPE_SCHEMAATTRIBUTE;
            case SchemaComponent.MODEL_GROUP:
                return SchemaTypeSystemImpl.FILETYPE_SCHEMAMODELGROUP;
            case SchemaComponent.ATTRIBUTE_GROUP:
                return SchemaTypeSystemImpl.FILETYPE_SCHEMAATTRIBUTEGROUP;
            case SchemaComponent.IDENTITY_CONSTRAINT:
                return SchemaTypeSystemImpl.FILETYPE_SCHEMAIDENTITYCONSTRAINT;
            default:
                throw new IllegalStateException("Unexpected component type");
        }
    }

    void readHandlePool(XsbReader reader) {
        if (!_handlesToRefs.isEmpty() || _started) {
            throw new IllegalStateException("Nonempty handle set before read");
        }

        int size = reader.readShort();
        for (int i = 0; i < size; i++) {
            String handle = reader.readString();
            int code = reader.readShort();
            SchemaComponent.Ref result;
            switch (code) {
                case FILETYPE_SCHEMATYPE:
                    result = new SchemaType.Ref(typeSystem, handle);
                    break;
                case FILETYPE_SCHEMAELEMENT:
                    result = new SchemaGlobalElement.Ref(typeSystem, handle);
                    break;
                case FILETYPE_SCHEMAATTRIBUTE:
                    result = new SchemaGlobalAttribute.Ref(typeSystem, handle);
                    break;
                case FILETYPE_SCHEMAMODELGROUP:
                    result = new SchemaModelGroup.Ref(typeSystem, handle);
                    break;
                case FILETYPE_SCHEMAATTRIBUTEGROUP:
                    result = new SchemaAttributeGroup.Ref(typeSystem, handle);
                    break;
                case FILETYPE_SCHEMAIDENTITYCONSTRAINT:
                    result = new SchemaIdentityConstraint.Ref(typeSystem, handle);
                    break;
                default:
                    throw new SchemaTypeLoaderException("Schema index has an unrecognized entry of type " + code, typeSystem.getName(), handle, SchemaTypeLoaderException.UNRECOGNIZED_INDEX_ENTRY);
            }
            _handlesToRefs.put(handle, result);
        }
    }
}

