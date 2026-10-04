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

package org.apache.xmlbeans.impl.validator;

import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.common.*;
import org.apache.xmlbeans.impl.schema.SchemaTypeImpl;
import org.apache.xmlbeans.impl.schema.SchemaTypeVisitorImpl;
import org.apache.xmlbeans.impl.util.ExceptionUtil;
import org.apache.xmlbeans.impl.util.MathUtil;
import org.apache.xmlbeans.impl.util.XsTypeConverter;
import org.apache.xmlbeans.impl.values.*;

import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

public final class Validator
    implements ValidatorListener {
    public Validator(
        SchemaType type, SchemaField field, SchemaTypeLoader globalLoader,
        XmlOptions options, Collection<XmlError> defaultErrorListener) {
        _options = XmlOptions.maskNull(options);
        _errorListener = _options.getErrorListener();
        _treatLaxAsSkip = _options.isValidateTreatLaxAsSkip();
        _strict = _options.isValidateStrict();

        if (_errorListener == null) {
            _errorListener = defaultErrorListener;
        }

        _constraintEngine = new IdentityConstraint(_errorListener, type.isDocumentType());

        _globalTypes = globalLoader;
        _rootType = type;
        _rootField = field;

        _vc = new ValidatorVC();
    }

    private class ValidatorVC implements ValidationContext {
        // KHK: remove this
        public void invalid(String message) {
            // TODO (dutta) Additional Attributes for validation error have limited information
            //at this time but will be a part of the second round of refactoring

            Validator.this.emitError(_event, message, null, null,
                XmlValidationError.ATTRIBUTE_TYPE_INVALID);
        }

        public void invalid(String code, Object[] args) {
            // TODO (dutta) Additional Attributes for validation error have limited information
            //at this time but will be a part of the second round of refactoring

            Validator.this.emitError(_event, code, args, null, null,
                XmlValidationError.ATTRIBUTE_TYPE_INVALID, null);
        }

        Event _event;
    }

    public boolean isValid() {
        return !_invalid && _constraintEngine.isValid();
    }

    // KHK: remove this
    private void emitError(Event event, String message, QName offendingQName,
                           SchemaType expectedSchemaType,
                           int errorType) {
        emitError(event, message, null, null, XmlError.SEVERITY_ERROR, null, offendingQName, expectedSchemaType,
            null, errorType, null);
    }

    private void emitError(Event event, String code, Object[] args, QName offendingQName,
                           SchemaType expectedSchemaType,
                           int errorType, SchemaType badSchemaType) {
        emitError(event, null, code, args, XmlError.SEVERITY_ERROR, null, offendingQName, expectedSchemaType,
            null, errorType, badSchemaType);
    }

    // KHK: remove 'message' parameter
    private void emitError(Event event, String message, String code, Object[] args, int severity,
                           QName fieldName, QName offendingQName,
                           SchemaType expectedSchemaType, List<QName> expectedQNames,
                           int errorType, SchemaType badSchemaType) {
        _errorState++;

        if (_suspendErrors == 0) {
            if (severity == XmlError.SEVERITY_ERROR) {
                _invalid = true;
            }

            if (_errorListener != null) {
                assert event != null;
                XmlError error;
                XmlCursor curs = event.getLocationAsCursor();
                if (curs != null) {
                    // non-streaming validation uses XmlCursor
                    error = XmlValidationError.forCursorWithDetails(message, code, args, severity,
                        curs, fieldName, offendingQName, expectedSchemaType, expectedQNames,
                        errorType, badSchemaType);
                } else {
                    // streaming validation uses Location
                    error = XmlValidationError.forLocationWithDetails(message, code, args, severity,
                        event.getLocation(), fieldName, offendingQName, expectedSchemaType, expectedQNames,
                        errorType, badSchemaType);
                }

                _errorListener.add(error);
            }
        }
    }

    private void emitFieldError(Event event, String code, Object[] args, QName offendingQName,
                                SchemaType expectedSchemaType, List<QName> expectedQNames,
                                int errorType, SchemaType badSchemaType) {
        QName fieldName = null;
        if (_stateStack != null && _stateStack._field != null) {
            fieldName = _stateStack._field.getName();
        }

        Validator.this.emitError(event, null, code, args, XmlError.SEVERITY_ERROR, fieldName, offendingQName, expectedSchemaType,
            expectedQNames, errorType, badSchemaType);
    }

    public void nextEvent(int kind, Event event) {
        resetValues();

        if (_eatContent > 0) {
            switch (kind) {
                case END:
                    _eatContent--;
                    break;
                case BEGIN:
                    _eatContent++;
                    break;
            }
        } else {
            assert
                kind == BEGIN || kind == ATTR ||
                kind == END || kind == TEXT || kind == ENDATTRS;

            switch (kind) {
                case BEGIN:
                    beginEvent(event);
                    break;
                case ATTR:
                    attrEvent(event);
                    break;
                case ENDATTRS:
                    endAttrsEvent(event);
                    break;
                case TEXT:
                    textEvent(event);
                    break;
                case END:
                    endEvent(event);
                    break;
            }
        }
    }

    private void beginEvent(Event event) {
        _localElement = null;
        _wildcardElement = null;
        State state = topState();

        SchemaType elementType;
        SchemaField elementField;

        if (state == null) {
            elementType = _rootType;
            elementField = _rootField;
        } else {

            QName name = event.getName();

            assert name != null;

            state._isEmpty = false;

            if (state._isNil) {
                emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$NIL_WITH_CONTENT,
                    null, state._field.getName(), state._type, null,
                    XmlValidationError.NIL_ELEMENT, state._type);

                _eatContent = 1;
                return;
            }

            if (state._field != null && state._field.isFixed()) {
                emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$FIXED_WITH_CONTENT,
                    new Object[]{QNameHelper.pretty(state._field.getName())},
                    state._field.getName(), state._type, null,
                    XmlValidationError.ELEMENT_NOT_ALLOWED, state._type);
            }

            if (!state.visit(name)) {
                findDetailedErrorBegin(event, state, name);

                _eatContent = 1;

                return;
            }

            SchemaParticle currentParticle = state.currentParticle();
            _wildcardElement = currentParticle;

            if (currentParticle.getParticleType() == SchemaParticle.WILDCARD) {
                //_wildcardElement = currentParticle;
                QNameSet elemWildcardSet = currentParticle.getWildcardSet();

                if (!elemWildcardSet.contains(name)) {
                    // Additional processing may be needed to generate more
                    // descriptive messages
                    // KHK: cvc-complex-type.2.4? cvc-particle.1.3? cvc-wildcard-namespace ?
                    emitFieldError(event, XmlErrorCodes.PARTICLE_VALID$NOT_WILDCARD_VALID,
                        new Object[]{QNameHelper.pretty(name)},
                        name, null, null,
                        XmlValidationError.ELEMENT_NOT_ALLOWED, state._type);

                    _eatContent = 1;

                    return;
                }

                int wildcardProcess = currentParticle.getWildcardProcess();

                if (wildcardProcess == SchemaParticle.SKIP ||
                    wildcardProcess == SchemaParticle.LAX && _treatLaxAsSkip) {
                    _eatContent = 1;
                    return;
                }

                _localElement = _globalTypes.findElement(name);
                elementField = _localElement;

                if (elementField == null) {
                    if (wildcardProcess == SchemaParticle.STRICT) {
                        // KHK: cvc-complex-type.2.4c? cvc-assess-elt.1.1.1.3.2?
                        emitFieldError(event, XmlErrorCodes.ASSESS_ELEM_SCHEMA_VALID$NOT_RESOLVED,
                            new Object[]{QNameHelper.pretty(name)},
                            name, state._type, null,
                            XmlValidationError.ELEMENT_NOT_ALLOWED, state._type);
                    }

                    _eatContent = 1;

                    return;
                }
            } else {
                assert currentParticle.getParticleType() == SchemaParticle.ELEMENT;

                // If the current element particle name does not match the name
                // of the event, then the current element is a substitute for
                // the current particle. Replace the field with the global
                // element for the replacement

                if (!currentParticle.getName().equals(name)) {
                    if (((SchemaLocalElement) currentParticle).blockSubstitution()) {
                        emitFieldError(event, XmlErrorCodes.PARTICLE_VALID$BLOCK_SUBSTITUTION,
                            new Object[]{QNameHelper.pretty(name)},
                            name, state._type, null,
                            XmlValidationError.ELEMENT_NOT_ALLOWED, state._type);

                        _eatContent = 1;
                        return;
                    }

                    SchemaGlobalElement newField = _globalTypes.findElement(name);

                    assert newField != null;

                    elementField = newField;
                    _localElement = newField;
                } else {
                    elementField = (SchemaField) currentParticle;
                }
            }

            elementType = elementField.getType();
        }

        assert elementType != null;

        //
        // the no-type is always invalid (even if there is an xsi:type)
        //

        if (elementType.isNoType()) {
            emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$NO_TYPE,
                null, event.getName(), null, null,
                XmlValidationError.ELEMENT_TYPE_INVALID, null);

            _eatContent = 1;
        }

        //
        // See if the element has an xsi:type on it
        //

        SchemaType xsiType = null;

        String value = event.getXsiType();

        if (value != null) {
            // Turn off the listener so a public error message
            // does not get generated, but I can see if there was
            // an error through the error state

            int originalErrorState = _errorState;

            _suspendErrors++;

            try {
                _vc._event = null;

                xsiType = _globalTypes.findType(XmlQNameImpl.validateLexical(value, _vc, event));
            } catch (Throwable t) {
                if (ExceptionUtil.isFatal(t)) {
                    ExceptionUtil.rethrow(t);
                }
                _errorState++;
            } finally {
                _suspendErrors--;
            }

            if (originalErrorState != _errorState) {
                // not sure how to extract this one
                emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$XSI_TYPE_INVALID_QNAME,
                    new Object[]{value}, event.getName(), xsiType, null,
                    XmlValidationError.ELEMENT_TYPE_INVALID, (state == null ? null : state._type));

                _eatContent = 1;

                return;
            } else if (xsiType == null) {
                // NOT SURE errorAttributes._expectedSchemaType = xsiType;
                emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$XSI_TYPE_NOT_FOUND,
                    new Object[]{value}, event.getName(), null, null,
                    XmlValidationError.ELEMENT_TYPE_INVALID, null);

                _eatContent = 1;

                return;
            }
        }

        if (xsiType != null && !xsiType.equals(elementType)) {
            if (!elementType.isAssignableFrom(xsiType)) {
                emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$XSI_TYPE_NOT_DERIVED,
                    new Object[]{xsiType, elementType}, event.getName(), elementType, null,
                    XmlValidationError.ELEMENT_TYPE_INVALID, (state == null ? null : state._type));

                _eatContent = 1;

                return;
            }

            if (elementType.blockExtension()) {
                for (SchemaType t = xsiType; !t.equals(elementType);
                     t = t.getBaseType()) {
                    if (t.getDerivationType() == SchemaType.DT_EXTENSION) {
                        emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$XSI_TYPE_BLOCK_EXTENSION,
                            new Object[]{xsiType, elementType}, event.getName(), elementType, null,
                            XmlValidationError.ELEMENT_TYPE_INVALID, (state == null ? null : state._type));

                        _eatContent = 1;

                        return;
                    }
                }
            }

            if (elementType.blockRestriction()) {
                for (SchemaType t = xsiType; !t.equals(elementType);
                     t = t.getBaseType()) {
                    if (t.getDerivationType() == SchemaType.DT_RESTRICTION) {
                        emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$XSI_TYPE_BLOCK_RESTRICTION,
                            new Object[]{xsiType, elementType}, event.getName(), elementType, null,
                            XmlValidationError.ELEMENT_TYPE_INVALID, (state == null ? null : state._type));

                        _eatContent = 1;

                        return;
                    }
                }
            }

            if (elementField instanceof SchemaLocalElement sle) {
                _localElement = sle;

                if (sle.blockExtension() || sle.blockRestriction()) {
                    for (SchemaType t = xsiType; !t.equals(elementType);
                         t = t.getBaseType()) {
                        if ((t.getDerivationType() == SchemaType.DT_RESTRICTION && sle.blockRestriction()) ||
                            (t.getDerivationType() == SchemaType.DT_EXTENSION && sle.blockExtension())) {
                            //need to find a way to get the right type
                            emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$XSI_TYPE_PROHIBITED_SUBST,
                                new Object[]{xsiType, QNameHelper.pretty(sle.getName())},
                                sle.getName(), null, null, XmlValidationError.ELEMENT_TYPE_INVALID, null);

                            _eatContent = 1;

                            return;
                        }
                    }
                }
            }

            elementType = xsiType;
        }

        if (elementField instanceof SchemaLocalElement sle) {
            _localElement = sle;

            if (sle.isAbstract()) {
                //todo (dutta) need to find a way to get the right type
                emitError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$ABSTRACT,
                    new Object[]{QNameHelper.pretty(sle.getName())},
                    sle.getName(), null, XmlValidationError.ELEMENT_TYPE_INVALID, null);

                _eatContent = 1;
                return;
            }
        }

        if (elementType.isAbstract()) {
            emitError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$ABSTRACT,
                new Object[]{elementType},
                event.getName(), elementType, XmlValidationError.ELEMENT_TYPE_INVALID, (state == null ? null : state._type));

            _eatContent = 1;

            return;
        }

        boolean isNil = false;
        boolean hasNil = false;

        String nilValue = event.getXsiNil();

        if (nilValue != null) {
            _vc._event = event;
            isNil = JavaBooleanHolder.validateLexical(nilValue, _vc);
            hasNil = true;
        }

        // note in schema spec 3.3.4, you're not even allowed to say xsi:nil="false" if you're not nillable!
        if (hasNil && (elementField == null || !elementField.isNillable())) {
            emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$NOT_NILLABLE, null,
                elementField == null ? null : elementField.getName(), elementType, null,
                XmlValidationError.ELEMENT_TYPE_INVALID, (state == null ? null : state._type));

            _eatContent = 1;
            return;
        }

        if (isNil && elementField != null && elementField.isFixed()) {
            emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$NIL_WITH_FIXED, null,
                elementField.getName(), elementType, null,
                XmlValidationError.ELEMENT_TYPE_INVALID, (state == null ? null : state._type));
        }

        newState(elementType, elementField, isNil);

        // Dispatch this element event to any identity constraints
        // As well as adding any new identity constraints that exist

        _constraintEngine.element(
            event,
            elementType,
            elementField instanceof SchemaLocalElement sle
                ? sle.getIdentityConstraints()
                : null);
    }

    private void attrEvent(Event event) {
        QName attrName = event.getName();

        State state = topState();

        if (state._attrs == null) {
            state._attrs = new HashSet<>();
        }

        if (state._attrs.contains(attrName)) {
            emitFieldError(event, XmlErrorCodes.XML_DUPLICATE_ATTRIBUTE,
                new Object[]{QNameHelper.pretty(attrName)},
                attrName, null, null, XmlValidationError.INCORRECT_ATTRIBUTE, state._type);

            return;
        }

        state._attrs.add(attrName);

        if (!state._canHaveAttrs) {
            emitFieldError(event, XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$NO_WILDCARD,
                new Object[]{QNameHelper.pretty(attrName)}, attrName, null, null,
                XmlValidationError.INCORRECT_ATTRIBUTE, state._type);
            return;
        }

        SchemaLocalAttribute attrSchema =
            state._attrModel == null
                ? null
                : state._attrModel.getAttribute(attrName);

        if (attrSchema != null) {
            _localAttribute = attrSchema;

            if (attrSchema.getUse() == SchemaLocalAttribute.PROHIBITED) {
                emitFieldError(event, XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$PROHIBITED_ATTRIBUTE,
                    new Object[]{QNameHelper.pretty(attrName)},
                    attrName, null, null, XmlValidationError.INCORRECT_ATTRIBUTE, state._type);

                return;
            }

            String value =
                validateSimpleType(
                    attrSchema.getType(), attrSchema, event, false, false);

            _constraintEngine.attr(event, attrName, attrSchema.getType(), value);

            return;
        }

        int wildcardProcess = state._attrModel.getWildcardProcess();

        _wildcardAttribute = state._attrModel;

        if (wildcardProcess == SchemaAttributeModel.NONE) {
            // todo (dutta) need additional logic to determine the expectedSchemaType
            emitFieldError(event, XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$NO_WILDCARD,
                new Object[]{QNameHelper.pretty(attrName)},
                attrName, null, null, XmlValidationError.INCORRECT_ATTRIBUTE, state._type);

            return;
        }

        QNameSet attrWildcardSet = state._attrModel.getWildcardSet();

        if (!attrWildcardSet.contains(attrName)) {
            // todo (dutta) need additional logic to determine the expectedSchemaType
            emitFieldError(event, XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$NOT_WILDCARD_VALID,
                new Object[]{QNameHelper.pretty(attrName)},
                attrName, null, null, XmlValidationError.INCORRECT_ATTRIBUTE, state._type);

            return;
        }

        if (wildcardProcess == SchemaAttributeModel.SKIP ||
            wildcardProcess == SchemaAttributeModel.LAX && _treatLaxAsSkip) {
            return;
        }

        attrSchema = _globalTypes.findAttribute(attrName);
        _localAttribute = attrSchema;

        if (attrSchema == null) {
            if (wildcardProcess == SchemaAttributeModel.LAX) {
                return;
            }

            assert wildcardProcess == SchemaAttributeModel.STRICT;

            // KHK: cvc-assess-attr.1.2 ?
            // todo (dutta) need additional logic to determine the expectedSchemaType
            emitFieldError(event, XmlErrorCodes.ASSESS_ATTR_SCHEMA_VALID$NOT_RESOLVED,
                new Object[]{QNameHelper.pretty(attrName)},
                attrName, null, null, XmlValidationError.INCORRECT_ATTRIBUTE, state._type);

            return;
        }

        String value =
            validateSimpleType(
                attrSchema.getType(), attrSchema, event, false, false);

        _constraintEngine.attr(event, attrName, attrSchema.getType(), value);
    }

    private void endAttrsEvent(Event event) {
        State state = topState();

        if (state._attrModel != null) {
            SchemaLocalAttribute[] attrs = state._attrModel.getAttributes();

            for (SchemaLocalAttribute sla : attrs) {
                if (state._attrs == null ||
                    !state._attrs.contains(sla.getName())) {
                    if (sla.getUse() == SchemaLocalAttribute.REQUIRED) {
                        // KHK: cvc-complex-type.4
                        emitFieldError(event, XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$MISSING_REQUIRED_ATTRIBUTE,
                            new Object[]{QNameHelper.pretty(sla.getName())},
                            sla.getName(), null, null, XmlValidationError.INCORRECT_ATTRIBUTE, state._type);
                    } else if (sla.isDefault() || sla.isFixed()) {
                        _constraintEngine.attr(event, sla.getName(), sla.getType(), sla.getDefaultText());

                        // We don't need to validate attribute defaults because this is done at compiletime.
                        /*
                        String value = sla.getDefaultText();
                        SchemaType type = sla.getType();

                        if (XmlQName.type.isAssignableFrom(type))
                        {
                            emitFieldError(
                                event,
                                "Default QName values are unsupported for attribute: " +
                                    QNameHelper.pretty(sla.getName()),
                                XmlError.SEVERITY_INFO);
                        }

                        else
                        {
                            validateSimpleType(
                                type, sla.getDefaultText(), event );

                            _constraintEngine.attr( event, type, value );
                        }
                        */
                    }
                }
            }
        }
    }

    private void endEvent(Event event) {
        _localElement = null;
        _wildcardElement = null;
        State state = topState();

        if (!state._isNil) {
            if (!state.end()) {
                findDetailedErrorEnd(event, state);
            }

            // This end event has no text, use this fact to pass no text to
            // handleText

            if (state._isEmpty) {
                handleText(event, true, state._field);
            }
        }

        popState(event);

        _constraintEngine.endElement(event);
    }

    private void textEvent(Event event) {
        State state = topState();

        if (state._isNil) {
            emitFieldError(event, XmlErrorCodes.ELEM_LOCALLY_VALID$NIL_WITH_CONTENT, null,
                state._field.getName(), state._type, null,
                XmlValidationError.NIL_ELEMENT, state._type);
        } else {
            handleText(event, false, state._field);
        }

        state._isEmpty = false;
    }


    private void handleText(
        Event event, boolean emptyContent, SchemaField field) {
        State state = topState();

        if (!state._sawText) {
            if (state._hasSimpleContent) {
                String value =
                    validateSimpleType(
                        state._type, field, event, emptyContent, true);

                _constraintEngine.text(event, state._type, value, false);
            } else if (state._canHaveMixedContent) {
                // handles cvc-elt.5.2.2.2.1, checking mixed content against fixed.
                // if we see <mixedType>a</b>c</mixedType>, we validate against
                // the first 'a' text and we check the content of mixedType to
                // be empty in beginElem().  we don't care about checking against
                // the 'c' text since there will already be an error for <b/>
                String value =
                    validateSimpleType(
                        XmlString.type, field, event, emptyContent, true);

                _constraintEngine.text(event, XmlString.type, value, false);
            } else if (emptyContent) {
                _constraintEngine.text(event, state._type, null, true);
            } else {
                _constraintEngine.text(event, state._type, "", false);
            }
        }

        if (!emptyContent && !state._canHaveMixedContent &&
            !event.textIsWhitespace() && !state._hasSimpleContent) {
            if (field instanceof SchemaLocalElement e) {
                assert state._type.getContentType() == SchemaType.EMPTY_CONTENT ||
                       state._type.getContentType() == SchemaType.ELEMENT_CONTENT;

                // KHK: cvc-complex-type.2.1 or .2.3
                String errorCode = (state._type.getContentType() == SchemaType.EMPTY_CONTENT ?
                    XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$EMPTY_WITH_CONTENT :
                    XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$ELEMENT_ONLY_WITH_TEXT);

                emitError(event, errorCode, new Object[]{QNameHelper.pretty(e.getName())},
                    e.getName(), field.getType(),
                    XmlValidationError.ELEMENT_TYPE_INVALID, null);
            } else {
                // KHK: cvc-complex-type.2.1 or .2.3
                // todo (dutta) offendingQName = not sure how to get this(event.getName()??);
                emitError(event, "Can't have mixed content", event.getName(),
                    state._type, XmlValidationError.ELEMENT_TYPE_INVALID);
            }
        }

        if (!emptyContent) {
            state._sawText = true;
        }
    }

    private void findDetailedErrorBegin(Event event, State state, QName qName) {
        ArrayList<QName> expectedNames = new ArrayList<>();
        ArrayList<QName> optionalNames = new ArrayList<>();

        SchemaProperty[] eltProperties = state._type.getElementProperties();
        for (SchemaProperty sProp : eltProperties) {
            //Get the element from the schema
            // test if the element is valid
            if (state.test(sProp.getName())) {
                if (0 == BigInteger.ZERO.compareTo(sProp.getMinOccurs())) {
                    optionalNames.add(sProp.getName());
                } else {
                    expectedNames.add(sProp.getName());
                }
            }
        }

        List<QName> names = expectedNames.isEmpty() ? optionalNames : expectedNames;

        if (!names.isEmpty()) {
            String buf = names.stream().map(QNameHelper::pretty).collect(Collectors.joining(" "));
            emitFieldError(event, XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$EXPECTED_DIFFERENT_ELEMENT,
                new Object[]{names.size(), buf, QNameHelper.pretty(qName)},
                qName, null, names, XmlValidationError.INCORRECT_ELEMENT, state._type);
        } else {
            emitFieldError(event, XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$ELEMENT_NOT_ALLOWED,
                new Object[]{QNameHelper.pretty(qName)},
                qName, null, null, XmlValidationError.INCORRECT_ELEMENT, state._type);
        }
    }

    private void findDetailedErrorEnd(Event event, State state) {
        SchemaProperty[] eltProperties = state._type.getElementProperties();

        ArrayList<QName> expectedNames = new ArrayList<>();
        ArrayList<QName> optionalNames = new ArrayList<>();

        for (SchemaProperty sProp : eltProperties) {
            //Get the element from the schema
            // test if the element is valid
            if (state.test(sProp.getName())) {
                if (0 == BigInteger.ZERO.compareTo(sProp.getMinOccurs())) {
                    optionalNames.add(sProp.getName());
                } else {
                    expectedNames.add(sProp.getName());
                }
            }
        }

        List<QName> names = expectedNames.isEmpty() ? optionalNames : expectedNames;

        if (!names.isEmpty()) {
            String buf = names.stream().map(QNameHelper::pretty).collect(Collectors.joining(" "));

            emitFieldError(event, XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$MISSING_ELEMENT,
                new Object[]{names.size(), buf},
                null, null, names, XmlValidationError.INCORRECT_ELEMENT, state._type);
        } else {
            emitFieldError(event, XmlErrorCodes.ELEM_COMPLEX_TYPE_LOCALLY_VALID$EXPECTED_ELEMENT,
                null, null, null, null, XmlValidationError.ELEMENT_NOT_ALLOWED, state._type);
        }
    }


    private static final class State {
        boolean visit(QName name) {
            return _canHaveElements && _visitor.visit(name);
        }

        boolean test(QName name) {
            return _canHaveElements && _visitor.testValid(name);
        }

        boolean end() {
            return !_canHaveElements || _visitor.visit(null);
        }

        SchemaParticle currentParticle() {
            assert _visitor != null;
            return _visitor.currentParticle();
        }

        SchemaType _type;
        SchemaField _field;

        boolean _canHaveAttrs;
        boolean _canHaveMixedContent;
        boolean _hasSimpleContent;

        boolean _sawText;
        boolean _isEmpty;
        boolean _isNil;

        SchemaTypeVisitorImpl _visitor;
        boolean _canHaveElements;

        SchemaAttributeModel _attrModel;

        HashSet<QName> _attrs;

        State _next;
    }

    private boolean derivedFromInteger(SchemaType type) {
        int btc = type.getBuiltinTypeCode();

        while (btc == SchemaType.BTC_NOT_BUILTIN) {
            type = type.getBaseType();
            btc = type.getBuiltinTypeCode();
        }
        // This depends on the ordering of the constant values, which is not ideal but is easier
        return btc >= SchemaType.BTC_INTEGER && btc <= SchemaType.BTC_UNSIGNED_BYTE;
    }

    private void newState(SchemaType type, SchemaField field, boolean isNil) {
        State state = new State();

        state._type = type;
        state._field = field;
        state._isEmpty = true;
        state._isNil = isNil;

        if (type.isSimpleType()) {
            state._hasSimpleContent = true;
        } else {
            state._canHaveAttrs = true;
            state._attrModel = type.getAttributeModel();

            switch (type.getContentType()) {
                case SchemaType.EMPTY_CONTENT:
                    break;

                case SchemaType.SIMPLE_CONTENT:
                    state._hasSimpleContent = true;
                    break;

                case SchemaType.MIXED_CONTENT:
                    state._canHaveMixedContent = true;
                    // Fall through

                case SchemaType.ELEMENT_CONTENT:

                    SchemaParticle particle = type.getContentModel();

                    state._canHaveElements = particle != null;

                    if (state._canHaveElements) {
                        state._visitor = initVisitor(particle);
                    }

                    break;

                default:
                    throw new RuntimeException("Unexpected content type");
            }
        }

        pushState(state);
    }

    private void popState(Event e) {
        if (_stateStack._visitor != null) {
            poolVisitor(_stateStack._visitor);
            _stateStack._visitor = null;
        }

        _stateStack = _stateStack._next;
    }

    private void pushState(State state) {
        state._next = _stateStack;
        _stateStack = state;
    }

    private final LinkedList<TypeStoreVisitor> _visitorPool = new LinkedList<>();

    private void poolVisitor(SchemaTypeVisitorImpl visitor) {
        _visitorPool.add(visitor);
    }

    private SchemaTypeVisitorImpl initVisitor(SchemaParticle particle) {
        if (_visitorPool.isEmpty()) {
            return new SchemaTypeVisitorImpl(particle);
        }

        SchemaTypeVisitorImpl result =
            (SchemaTypeVisitorImpl) _visitorPool.removeLast();

        result.init(particle);

        return result;
    }

    private State topState() {
        return _stateStack;
    }

    //
    // Simple Type Validation
    //
    // emptyContent means that you can't use the event to get text: there is
    // no text, but you can use the event to do prefix resolution (in the case
    // where the default is a qname)
    //

    private String validateSimpleType(
        SchemaType type, SchemaField field, Event event,
        boolean emptyContent, boolean canApplyDefault) {
        if (!type.isSimpleType() &&
            type.getContentType() != SchemaType.SIMPLE_CONTENT) {
            assert false;
            // throw new RuntimeException( "Not a simple type" );
            return null; // should never happen
        }

        //
        // the no-type is always invalid
        //

        if (type.isNoType()) {
            emitError(event, (field.isAttribute() ? XmlErrorCodes.ATTR_LOCALLY_VALID$NO_TYPE : XmlErrorCodes.ELEM_LOCALLY_VALID$NO_TYPE),
                null, field.getName(), type, XmlValidationError.ELEMENT_TYPE_INVALID, null);

            return null;
        }

        // Get the value as a string (as normalized by the white space rule
        // TODO - will want to optimize this later

        String value = "";

        if (!emptyContent) {
            int wsr = type.getWhiteSpaceRule();
            value = wsr == SchemaType.WS_PRESERVE ? event.getText() : event.getText(wsr);
        }

        // See if I can apply a default/fixed value

        if (value.isEmpty() && canApplyDefault && field != null &&
            (field.isDefault() || field.isFixed())) {
            if (XmlQName.type.isAssignableFrom(type)) {
                // TODO: will be fixed in XmlSchema 1.1
                emitError(event, "Default QName values are unsupported for " +
                                 QNameHelper.readable(type) + " - ignoring.", null, null,
                    XmlError.SEVERITY_INFO, field.getName(), null, type, null,
                    XmlValidationError.ELEMENT_TYPE_INVALID, null);

                return null;
            }

            String defaultValue =
                XmlWhitespace.collapse(
                    field.getDefaultText(), type.getWhiteSpaceRule());

// BUGBUG - should validate defaultValue at compile time
            // KHK: cvc-elt.5.1.2 ?
            return
                validateSimpleType(type, defaultValue, event)
                    ? defaultValue
                    : null;
        }

        // KHK: cvc-elt.5.2.1 ?
        if (!validateSimpleType(type, value, event)) {
            return null;
        }

        if (field != null && field.isFixed()) {
// TODO - fixed value should have been cooked at compile time
            String fixedValue =
                XmlWhitespace.collapse(
                    field.getDefaultText(), type.getWhiteSpaceRule());

            if (!validateSimpleType(type, fixedValue, event)) {
                return null;
            }

            XmlObject val = type.newValue(value);
            XmlObject def = type.newValue(fixedValue);

            if (!val.valueEquals(def)) {
                // TODO (dutta) - make this more verbose
                if (field.isAttribute()) {
                    // KHK: check for is cvc-complex-type.3.1 or cvc-au
                    emitError(event, XmlErrorCodes.ATTR_LOCALLY_VALID$FIXED,
                        new Object[]{value, fixedValue, QNameHelper.pretty(event.getName())},
                        null, field.getType(), XmlValidationError.ELEMENT_TYPE_INVALID, null);
                } else {
                    String errorCode = null;

                    // see rule 5 of cvc-elt: Element Locally Valid (Element)
                    if (field.getType().getContentType() == SchemaType.MIXED_CONTENT) {
                        errorCode = XmlErrorCodes.ELEM_LOCALLY_VALID$FIXED_VALID_MIXED_CONTENT;
                    } else if (type.isSimpleType()) {
                        errorCode = XmlErrorCodes.ELEM_LOCALLY_VALID$FIXED_VALID_SIMPLE_TYPE;
                    } else {
                        assert false : "Element with fixed may not be EMPTY or ELEMENT_ONLY";
                    }

                    emitError(event, errorCode, new Object[]{value, fixedValue},
                        field.getName(), field.getType(), XmlValidationError.ELEMENT_TYPE_INVALID, null);
                }

                return null;
            }
        }

        return value;
    }

    private boolean validateSimpleType(
        SchemaType type, String value, Event event) {
        if (!type.isSimpleType() &&
            type.getContentType() != SchemaType.SIMPLE_CONTENT) {
            assert false;
            throw new RuntimeException("Not a simple type");
        }

        int retState = _errorState;

        switch (type.getSimpleVariety()) {
            case SchemaType.ATOMIC:
                validateAtomicType(type, value, event);
                break;
            case SchemaType.UNION:
                validateUnionType(type, value, event);
                break;
            case SchemaType.LIST:
                validateListType(type, value, event);
                break;

            default:
                throw new RuntimeException("Unexpected simple variety");
        }

        return retState == _errorState;
    }

    private void validateAtomicType(
        SchemaType type, String value, Event event) {
        // Now we should have only an atomic type to validate

        assert type.getSimpleVariety() == SchemaType.ATOMIC;

        // Record the current error state to see if any new errors are made
        int errorState = _errorState;
        _vc._event = event;

        switch (type.getPrimitiveType().getBuiltinTypeCode()) {
            case SchemaType.BTC_ANY_SIMPLE: {
                // Always valid!
                _stringValue = value;
                break;
            }
            case SchemaType.BTC_STRING: {
                JavaStringEnumerationHolderEx.validateLexical(value, type, _vc);
                _stringValue = value;
                break;
            }
            case SchemaType.BTC_DECIMAL: {
                JavaDecimalHolderEx.validateLexical(value, type, _vc,
                        false, _options.getMaxNumberOfCharsForNumbers());

                // An additional rule states that if the type is xs:integer or derived from it,
                // then the decimal dot is not allowed.
                // verify that values extending xsd:integer don't have a decimal point
                if (derivedFromInteger(type) && value.lastIndexOf('.') >= 0) {
                    _vc.invalid(XmlErrorCodes.INTEGER, new Object[]{value});
                }

                if (errorState == _errorState) {
                    BigDecimal parsed = null;
                    try {
                        parsed = MathUtil.parseAsBigDecimal(value, _options.getMaxNumberOfCharsForNumbers());
                    } catch (IllegalArgumentException e) {
                        // a number longer than the configured maximum is invalid input,
                        // not a programming error - report it like any other bad value
                        // rather than throwing out of validate()
                        _vc.invalid(derivedFromInteger(type) ? XmlErrorCodes.INTEGER : XmlErrorCodes.DECIMAL,
                            new Object[]{value});
                    }
                    if (parsed != null) {
                        _decimalValue = parsed;
                        JavaDecimalHolderEx.validateValue(_decimalValue, type, _vc);
                    }
                }

                break;
            }
            case SchemaType.BTC_BOOLEAN: {
                _booleanValue = JavaBooleanHolderEx.validateLexical(value, type, _vc);
                break;
            }
            case SchemaType.BTC_FLOAT: {
                float f =
                    JavaFloatHolderEx.validateLexical(value, type, _vc, _options.getMaxNumberOfCharsForNumbers());

                if (errorState == _errorState) {
                    JavaFloatHolderEx.validateValue(f, type, _vc);
                }

                _floatValue = f;
                break;
            }
            case SchemaType.BTC_DOUBLE: {
                double d =
                    JavaDoubleHolderEx.validateLexical(value, type, _vc, _options.getMaxNumberOfCharsForNumbers());

                if (errorState == _errorState) {
                    JavaDoubleHolderEx.validateValue(d, type, _vc);
                }

                _doubleValue = d;
                break;
            }
            case SchemaType.BTC_QNAME: {
                QName n =
                    JavaQNameHolderEx.validateLexical(
                        value, type, _vc, event);

                if (errorState == _errorState) {
                    JavaQNameHolderEx.validateValue(n, type, _vc);
                }

                _qnameValue = n;
                break;
            }
            case SchemaType.BTC_ANY_URI: {
                JavaUriHolderEx.validateLexical(value, type, _vc);
                // Do strict validation
                if (_strict) {
                    try {
                        XsTypeConverter.lexAnyURI(value);
                    } catch (InvalidLexicalValueException ilve) {
                        _vc.invalid(XmlErrorCodes.ANYURI, new Object[]{value});
                    }
                }
                _stringValue = value;
                break;
            }
            case SchemaType.BTC_G_MONTH: {
                // In the case of gMonth, there is some strict mode validation to do
                if (_strict && value.length() == 6 &&
                    value.charAt(4) == '-' && value.charAt(5) == '-') {
                    _vc.invalid(XmlErrorCodes.DATE, new Object[]{value});
                }
                // Fall through
            }
            case SchemaType.BTC_DATE_TIME:
            case SchemaType.BTC_TIME:
            case SchemaType.BTC_DATE:
            case SchemaType.BTC_G_YEAR_MONTH:
            case SchemaType.BTC_G_YEAR:
            case SchemaType.BTC_G_MONTH_DAY:
            case SchemaType.BTC_G_DAY: {
                GDate d = XmlDateImpl.validateLexical(value, type, _vc);

                if (d != null) {
                    XmlDateImpl.validateValue(d, type, _vc);
                }

                _gdateValue = d;
                break;
            }
            case SchemaType.BTC_DURATION: {
                GDuration d = XmlDurationImpl.validateLexical(value, type, _vc);

                if (d != null) {
                    XmlDurationImpl.validateValue(d, type, _vc);
                }

                _gdurationValue = d;
                break;
            }
            case SchemaType.BTC_BASE_64_BINARY: {
                byte[] v =
                    JavaBase64HolderEx.validateLexical(value, type, _vc);

                if (v != null) {
                    JavaBase64HolderEx.validateValue(v, type, _vc);
                }

                _byteArrayValue = v;
                break;
            }
            case SchemaType.BTC_HEX_BINARY: {
                byte[] v =
                    JavaHexBinaryHolderEx.validateLexical(value, type, _vc);

                if (v != null) {
                    JavaHexBinaryHolderEx.validateValue(v, type, _vc);
                }

                _byteArrayValue = v;
                break;
            }
            case SchemaType.BTC_NOTATION: {
                QName n =
                    JavaNotationHolderEx.validateLexical(
                        value, type, _vc, event);

                if (errorState == _errorState) {
                    JavaNotationHolderEx.validateValue(n, type, _vc);
                }

                _qnameValue = n;
                break;
            }

            default:
                throw new RuntimeException("Unexpected primitive type code");
        }
    }

    private void validateListType(
        SchemaType type, String value, Event event) {
        int errorState = _errorState;

        if (!type.matchPatternFacet(value)) {
            emitError(event, XmlErrorCodes.DATATYPE_VALID$PATTERN_VALID,
                new Object[]{"list", value, QNameHelper.readable(type)},
                null, type, XmlValidationError.LIST_INVALID, null);
        }

        String[] items = XmlListImpl.split_list(value);

        int i;
        XmlObject o;

        if ((o = type.getFacet(SchemaType.FACET_LENGTH)) != null) {
            if ((i = ((SimpleValue) o).getIntValue()) != items.length) {
                //offending Qname not valid
                emitError(event, XmlErrorCodes.DATATYPE_LENGTH_VALID$LIST_LENGTH,
                    new Object[]{value, items.length, i, QNameHelper.readable(type)},
                    null, type, XmlValidationError.LIST_INVALID, null);
            }
        }

        if ((o = type.getFacet(SchemaType.FACET_MIN_LENGTH)) != null) {
            if ((i = ((SimpleValue) o).getIntValue()) > items.length) {
                //offending Qname not valid
                emitError(event, XmlErrorCodes.DATATYPE_LENGTH_VALID$LIST_LENGTH,
                    new Object[]{value, items.length, i, QNameHelper.readable(type)},
                    null, type, XmlValidationError.LIST_INVALID, null);
            }
        }

        if ((o = type.getFacet(SchemaType.FACET_MAX_LENGTH)) != null) {
            if ((i = ((SimpleValue) o).getIntValue()) < items.length) {
                //offending Qname not valid
                emitError(event, XmlErrorCodes.DATATYPE_LENGTH_VALID$LIST_LENGTH,
                    new Object[]{value, items.length, i, QNameHelper.readable(type)},
                    null, type, XmlValidationError.LIST_INVALID, null);
            }
        }

        SchemaType itemType = type.getListItemType();
        _listValue = new ArrayList<>();
        _listTypes = new ArrayList<>();

        for (i = 0; i < items.length; i++) {
            validateSimpleType(
                itemType, items[i], event);
            addToList(itemType);
        }

        // If no errors up to this point, then I can create an
        // XmlList from this value and campare it again enums.

        if (errorState == _errorState) {
            if (type.getEnumerationValues() != null) {
                // Lists which contain QNames will need a resolver

                NamespaceContext.push(
                    new NamespaceContext(event));

                try {
                    XmlObject listValue = ((SchemaTypeImpl) type).newValidatingValue(value);
                } catch (XmlValueOutOfRangeException e) {
                    //offending Qname not valid ??
                    emitError(event, XmlErrorCodes.DATATYPE_ENUM_VALID,
                        new Object[]{"list", value, QNameHelper.readable(type)},
                        null, type, XmlValidationError.LIST_INVALID, null);
                } finally {
                    NamespaceContext.pop();
                }
            }
        }
    }

    private void validateUnionType(
        SchemaType type, String value, Event event) {
        // TODO - if xsi:type is specified on a union, it selects
        // that union member type

        if (!type.matchPatternFacet(value)) {
            //offending Qname not valid ??
            emitError(event, XmlErrorCodes.DATATYPE_VALID$PATTERN_VALID,
                new Object[]{"union", value, QNameHelper.readable(type)},
                null, type, XmlValidationError.UNION_INVALID, null);
        }

        int currentWsr = SchemaType.WS_PRESERVE;
        String currentValue = value;

        SchemaType[] types = type.getUnionMemberTypes();

        int originalState = _errorState;

        int i;
        for (i = 0; i < types.length; i++) {
            int memberWsr = types[i].getWhiteSpaceRule();

            if (memberWsr == SchemaType.WS_UNSPECIFIED) {
                memberWsr = SchemaType.WS_PRESERVE;
            }

            if (memberWsr != currentWsr) {
                currentWsr = memberWsr;
                currentValue = XmlWhitespace.collapse(value, currentWsr);
            }

            int originalErrorState = _errorState;

            _suspendErrors++;

            try {
                validateSimpleType(types[i], currentValue, event);
            } finally {
                _suspendErrors--;
            }

            if (originalErrorState == _errorState) {
                _unionType = types[i];
                break;
            }
        }

        _errorState = originalState;

        if (i >= types.length) {
            //offending Qname not valid ??
            emitError(event, XmlErrorCodes.DATATYPE_VALID$UNION,
                new Object[]{value, QNameHelper.readable(type)},
                null, type, XmlValidationError.UNION_INVALID, null);
        } else {
            XmlObject[] unionEnumvals = type.getEnumerationValues();

            if (unionEnumvals != null) {
                // Unions which contain QNames will need a resolver

                NamespaceContext.push(new NamespaceContext(event));

                try {
                    XmlObject unionValue = type.newValue(value);

                    for (i = 0; i < unionEnumvals.length; i++) {
                        if (unionValue.valueEquals(unionEnumvals[i])) {
                            break;
                        }
                    }

                    if (i >= unionEnumvals.length) {
                        //offending Qname not valid ??
                        emitError(event, XmlErrorCodes.DATATYPE_ENUM_VALID,
                            new Object[]{"union", value, QNameHelper.readable(type)},
                            null, type, XmlValidationError.UNION_INVALID, null);
                    }
                } catch (XmlValueOutOfRangeException e) {
                    // actually, the current union code always ends up here when invalid

                    //offending Qname not valid ??
                    emitError(event, XmlErrorCodes.DATATYPE_ENUM_VALID,
                        new Object[]{"union", value, QNameHelper.readable(type)},
                        null, type, XmlValidationError.UNION_INVALID, null);
                } finally {
                    NamespaceContext.pop();
                }
            }
        }
    }

    private void addToList(SchemaType type) {
        if (type.getSimpleVariety() != SchemaType.ATOMIC &&
            type.getSimpleVariety() != SchemaType.UNION) {
            return;
        }

        if (type.getUnionMemberTypes().length > 0 && getUnionType() != null) {
            type = getUnionType();
            _unionType = null;
        }

        _listTypes.add(type);

        if (type.getPrimitiveType() == null) {
            // instance has an error for this value so there is no primitive type.
            // an error should already have been produced.
            _listValue.add(null);
            return;
        }

        switch (type.getPrimitiveType().getBuiltinTypeCode()) {
            case SchemaType.BTC_ANY_SIMPLE:
            case SchemaType.BTC_ANY_URI: {
                _listValue.add(_stringValue);
                break;
            }
            case SchemaType.BTC_STRING: {
                _listValue.add(_stringValue);
                _stringValue = null;
                break;
            }
            case SchemaType.BTC_DECIMAL: {
                _listValue.add(_decimalValue);
                _decimalValue = null;
                break;
            }
            case SchemaType.BTC_BOOLEAN: {
                _listValue.add(_booleanValue ? Boolean.TRUE : Boolean.FALSE);
                _booleanValue = false;
                break;
            }
            case SchemaType.BTC_FLOAT: {
                _listValue.add(_floatValue);
                _floatValue = 0;
                break;
            }
            case SchemaType.BTC_DOUBLE: {
                _listValue.add(_doubleValue);
                _doubleValue = 0;
                break;
            }
            case SchemaType.BTC_QNAME:
            case SchemaType.BTC_NOTATION: {
                _listValue.add(_qnameValue);
                _qnameValue = null;
                break;
            }
            case SchemaType.BTC_DATE_TIME:
            case SchemaType.BTC_TIME:
            case SchemaType.BTC_DATE:
            case SchemaType.BTC_G_YEAR_MONTH:
            case SchemaType.BTC_G_YEAR:
            case SchemaType.BTC_G_MONTH_DAY:
            case SchemaType.BTC_G_DAY:
            case SchemaType.BTC_G_MONTH: {
                _listValue.add(_gdateValue);
                _gdateValue = null;
                break;
            }
            case SchemaType.BTC_DURATION: {
                _listValue.add(_gdurationValue);
                _gdurationValue = null;
                break;
            }
            case SchemaType.BTC_BASE_64_BINARY:
            case SchemaType.BTC_HEX_BINARY: {
                _listValue.add(_byteArrayValue);
                _byteArrayValue = null;
                break;
            }

            default:
                throw new RuntimeException("Unexpected primitive type code");
        }
    }

    //
    // Members of the validator class
    //

    private final XmlOptions _options;
    private boolean _invalid;
    private final SchemaType _rootType;
    private final SchemaField _rootField;
    private final SchemaTypeLoader _globalTypes;
    private State _stateStack;
    private int _errorState;
    private Collection<XmlError> _errorListener;
    private final boolean _treatLaxAsSkip;
    private final boolean _strict;
    private final ValidatorVC _vc;
    private int _suspendErrors;
    private final IdentityConstraint _constraintEngine;
    private int _eatContent;

    private SchemaLocalElement _localElement;
    private SchemaParticle _wildcardElement;
    private SchemaLocalAttribute _localAttribute;
    private SchemaAttributeModel _wildcardAttribute;
    private SchemaType _unionType;

    // Strongly typed values
    private String _stringValue;
    private BigDecimal _decimalValue;
    private boolean _booleanValue;
    private float _floatValue;
    private double _doubleValue;
    private QName _qnameValue;
    private GDate _gdateValue;
    private GDuration _gdurationValue;
    private byte[] _byteArrayValue;
    private List<Object> _listValue;
    private List<SchemaType> _listTypes;

    private void resetValues() {
        _localAttribute = null;
        _wildcardAttribute = null;
        _stringValue = null;
        _decimalValue = null;
        _booleanValue = false;
        _floatValue = 0;
        _doubleValue = 0;
        _qnameValue = null;
        _gdateValue = null;
        _gdurationValue = null;
        _byteArrayValue = null;
        _listValue = null;
        _listTypes = null;
        _unionType = null;
    }

    /**
     * @return Returns the SchemaType of the current element.
     * This can be different than getCurrentElement().getType() if xsi:type attribute is used.
     * Null is returned if no schema type is available.
     * For attribute types use {@link #getCurrentAttribute()}.getType().
     * Warning: the returned SchemaType can be an {@link org.apache.xmlbeans.XmlBeans#NO_TYPE},
     * see {@link SchemaType#isNoType}. Or can be the parent type, for unrecognized elements
     * that are part of wildcards.
     */
    public SchemaType getCurrentElementSchemaType() {
        State state = topState();
        if (state != null) {
            return state._type;
        }

        return null;
    }

    /**
     * @return Returns the curent local element, null if one is not available, see {@link #getCurrentWildcardElement()}.
     */
    public SchemaLocalElement getCurrentElement() {
        if (_localElement != null) {
            return _localElement;
        }

        // it means the element is to be skiped and it doesn't have a known SchemaLocalElement

        if (_eatContent > 0) {
            return null;
        }

        //try getting it from the stack (this should happen after END)

        if (_stateStack != null && _stateStack._field instanceof SchemaLocalElement element) {
            return element;
        }

        return null;
    }

    /**
     * @return Returns the current particle, if this is a wildcard particle {@link SchemaParticle#WILDCARD}
     * method {@link #getCurrentElement()} might return null if wildcard's processContents is skip or lax.
     */
    public SchemaParticle getCurrentWildcardElement() {
        return _wildcardElement;
    }

    /**
     * @return Returns the curent local attribute, global attribute if the current attribute is part of an
     * attribute wildcard, or null if none is available.
     */
    public SchemaLocalAttribute getCurrentAttribute() {
        return _localAttribute;
    }

    /**
     * @return Returns the attribute model for attributes if available, else null is returned.
     */
    public SchemaAttributeModel getCurrentWildcardAttribute() {
        return _wildcardAttribute;
    }

    public String getStringValue() {
        return _stringValue;
    }

    public BigDecimal getDecimalValue() {
        return _decimalValue;
    }

    public boolean getBooleanValue() {
        return _booleanValue;
    }

    public float getFloatValue() {
        return _floatValue;
    }

    public double getDoubleValue() {
        return _doubleValue;
    }

    public QName getQNameValue() {
        return _qnameValue;
    }

    public GDate getGDateValue() {
        return _gdateValue;
    }

    public GDuration getGDurationValue() {
        return _gdurationValue;
    }

    public byte[] getByteArrayValue() {
        return _byteArrayValue;
    }

    public List<Object> getListValue() {
        return _listValue;
    }

    public List<SchemaType> getListTypes() {
        return _listTypes;
    }

    public SchemaType getUnionType() {
        return _unionType;
    }
}
