package cc.quarkus.plugin.patcher.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cc.quarkus.qcc.graph.BasicBlockBuilder;
import cc.quarkus.qcc.graph.DelegatingGraphFactory;
import cc.quarkus.qcc.graph.DispatchInvocation;
import cc.quarkus.qcc.graph.JavaAccessMode;
import cc.quarkus.qcc.graph.Node;
import cc.quarkus.qcc.graph.Value;
import cc.quarkus.qcc.graph.literal.ArrayTypeIdLiteral;
import cc.quarkus.qcc.graph.literal.ClassTypeIdLiteral;
import cc.quarkus.qcc.graph.literal.TypeIdLiteral;
import cc.quarkus.qcc.type.ReferenceType;
import cc.quarkus.qcc.type.Type;
import cc.quarkus.qcc.type.TypeSystem;
import cc.quarkus.qcc.type.definition.element.ConstructorElement;
import cc.quarkus.qcc.type.definition.element.FieldElement;
import cc.quarkus.qcc.type.definition.element.MethodElement;
import cc.quarkus.qcc.type.definition.element.ParameterizedExecutableElement;

final class PatcherGraphFactory extends DelegatingGraphFactory implements BasicBlockBuilder {
    /**
     * A mapping of patch-to-patched class types.
     */
    private final Map<ClassTypeIdLiteral, ClassTypeIdLiteral> patchTypes = new ConcurrentHashMap<>();
    /**
     * A mapping of patch-to-patched fields.
     */
    private final Map<FieldElement, FieldElement> patchFields = new ConcurrentHashMap<>();
    /**
     * A mapping of patched fields to accessors.
     */
    private final Map<FieldElement, ClassTypeIdLiteral> accessors = new ConcurrentHashMap<>();
    /**
     * A mapping of methods to their replacements.
     */
    private final Map<ParameterizedExecutableElement, ParameterizedExecutableElement> patchMethods = new ConcurrentHashMap<>();
    private final BasicBlockBuilder.Factory.Context ctxt;

    PatcherGraphFactory(final Factory.Context ctxt, final BasicBlockBuilder delegate) {
        super(delegate);
        this.ctxt = ctxt;
    }

    @SuppressWarnings("unchecked")
    private <T extends Type> T remapType(T original) {
        if (original instanceof ReferenceType) {
            final ReferenceType classType = (ReferenceType) original;
            TypeIdLiteral upperBound = classType.getUpperBound();
            TypeSystem ts = ctxt.getTypeSystem();
            return (T) ts.getReferenceType(remapTypeId(upperBound));
        } else {
            return original;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends TypeIdLiteral> T remapTypeId(T original) {
        if (original instanceof ClassTypeIdLiteral) {
            return (T) patchTypes.getOrDefault(original, (ClassTypeIdLiteral) original);
        } else if (original instanceof ArrayTypeIdLiteral) {
            ArrayTypeIdLiteral arrayTypeIdLiteral = (ArrayTypeIdLiteral) original;
            return (T) ctxt.getLiteralFactory().literalOfArrayType(remapType(arrayTypeIdLiteral.getElementType()));
        } else {
            return original;
        }
    }

    private ParameterizedExecutableElement remapMethod(ParameterizedExecutableElement original) {
        return patchMethods.getOrDefault(original, original);
    }

    public Value new_(final ClassTypeIdLiteral typeId) {
        return super.new_(remapTypeId(typeId));
    }

    public Value newArray(final ArrayTypeIdLiteral arrayTypeId, final Value size) {
        return super.newArray(remapTypeId(arrayTypeId), size);
    }

    public Value readInstanceField(final Value instance, final FieldElement fieldElement, final JavaAccessMode mode) {
        ClassTypeIdLiteral accessor = accessors.get(fieldElement);
        if (accessor != null) {
            throw new UnsupportedOperationException("TODO: Look up or create accessor singleton with constant fold API");
        } else {
            FieldElement mapped = patchFields.get(fieldElement);
            if (mapped != null) {
                return readInstanceField(instance, mapped, mode);
            } else {
                return super.readInstanceField(instance, fieldElement, mode);
            }
        }
    }

    public Value readStaticField(final FieldElement fieldElement, final JavaAccessMode mode) {
        ClassTypeIdLiteral accessor = accessors.get(fieldElement);
        if (accessor != null) {
            throw new UnsupportedOperationException("TODO: Look up or create accessor singleton with constant fold API");
        } else {
            FieldElement mapped = patchFields.get(fieldElement);
            if (mapped != null) {
                return readStaticField(mapped, mode);
            } else {
                return super.readStaticField(fieldElement, mode);
            }
        }
    }

    public Node writeInstanceField(final Value instance, final FieldElement fieldElement, final Value value, final JavaAccessMode mode) {
        ClassTypeIdLiteral accessor = accessors.get(fieldElement);
        if (accessor != null) {
            throw new UnsupportedOperationException("TODO: Look up or create accessor singleton with constant fold API");
        } else {
            FieldElement mapped = patchFields.get(fieldElement);
            if (mapped != null) {
                return writeInstanceField(instance, mapped, value, mode);
            } else {
                return super.writeInstanceField(instance, fieldElement, value, mode);
            }
        }
    }

    public Node writeStaticField(final FieldElement fieldElement, final Value value, final JavaAccessMode mode) {
        ClassTypeIdLiteral accessor = accessors.get(fieldElement);
        if (accessor != null) {
            throw new UnsupportedOperationException("TODO: Look up or create accessor singleton with constant fold API");
        } else {
            FieldElement mapped = patchFields.get(fieldElement);
            if (mapped != null) {
                return writeStaticField(fieldElement, value, mode);
            } else {
                return super.writeStaticField(fieldElement, value, mode);
            }
        }
    }

    public Node invokeStatic(final MethodElement target, final List<Value> arguments) {
        return super.invokeStatic((MethodElement) remapMethod(target), arguments);
    }

    public Node invokeInstance(final DispatchInvocation.Kind kind, final Value instance, final MethodElement target, final List<Value> arguments) {
        return super.invokeInstance(kind, instance, (MethodElement) remapMethod(target), arguments);
    }

    public Value invokeValueStatic(final MethodElement target, final List<Value> arguments) {
        return super.invokeValueStatic((MethodElement) remapMethod(target), arguments);
    }

    public Value invokeInstanceValueMethod(final Value instance, final DispatchInvocation.Kind kind, final MethodElement target, final List<Value> arguments) {
        return super.invokeInstanceValueMethod(instance, kind, (MethodElement) remapMethod(target), arguments);
    }

    public Value invokeConstructor(final Value instance, final ConstructorElement target, final List<Value> arguments) {
        return super.invokeConstructor(instance, (ConstructorElement) remapMethod(target), arguments);
    }
}
