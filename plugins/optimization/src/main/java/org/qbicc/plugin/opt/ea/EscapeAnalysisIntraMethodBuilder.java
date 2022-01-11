package org.qbicc.plugin.opt.ea;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.qbicc.context.ClassContext;
import org.qbicc.context.CompilationContext;
import org.qbicc.graph.BasicBlock;
import org.qbicc.graph.BasicBlockBuilder;
import org.qbicc.graph.BlockLabel;
import org.qbicc.graph.Call;
import org.qbicc.graph.CheckCast;
import org.qbicc.graph.DelegatingBasicBlockBuilder;
import org.qbicc.graph.Executable;
import org.qbicc.graph.InstanceFieldOf;
import org.qbicc.graph.LocalVariable;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.NotNull;
import org.qbicc.graph.OrderedNode;
import org.qbicc.graph.ParameterValue;
import org.qbicc.graph.PhiValue;
import org.qbicc.graph.ReferenceHandle;
import org.qbicc.graph.StaticField;
import org.qbicc.graph.Store;
import org.qbicc.graph.Truncate;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.graph.atomic.ReadAccessMode;
import org.qbicc.graph.atomic.WriteAccessMode;
import org.qbicc.type.ClassObjectType;
import org.qbicc.type.FunctionType;
import org.qbicc.type.ObjectType;
import org.qbicc.type.ValueType;
import org.qbicc.type.WordType;
import org.qbicc.type.annotation.type.TargetInfo;
import org.qbicc.type.definition.element.ConstructorElement;
import org.qbicc.type.definition.element.FieldElement;
import org.qbicc.type.definition.element.LocalVariableElement;
import org.qbicc.type.definition.element.MethodElement;
import org.qbicc.type.descriptor.ClassTypeDescriptor;
import org.qbicc.type.descriptor.MethodDescriptor;
import org.qbicc.type.descriptor.TypeDescriptor;

public final class EscapeAnalysisIntraMethodBuilder extends DelegatingBasicBlockBuilder  {
    private final EscapeAnalysisState escapeAnalysisState;
    private final ConnectionGraph connectionGraph;
    private final ClassContext bootstrapClassContext;
    private final Map<Node, Boolean> subgraphHandlingCache = new HashMap<>();

    public EscapeAnalysisIntraMethodBuilder(final CompilationContext ctxt, final BasicBlockBuilder delegate) {
        super(delegate);
        this.connectionGraph = new ConnectionGraph(getCurrentElement().toString());
        this.escapeAnalysisState = EscapeAnalysisState.get(ctxt);
        this.bootstrapClassContext = ctxt.getBootstrapClassContext();
    }

    @Override
    public Value new_(final ClassObjectType type, final Value typeId, final Value size, final Value align) {
        final New result = (New) super.new_(type, typeId, size, align);
        checkHandling(result, true);
        connectionGraph.trackNew(result, defaultEscapeValue(type));
        return result;
    }

    @Override
    public Value new_(ClassTypeDescriptor desc) {
        final New result = (New) super.new_(desc);
        connectionGraph.trackNew(result, EscapeValue.NO_ESCAPE);
        return result;
    }

    private EscapeValue defaultEscapeValue(ClassObjectType type) {
        if (isSubtypeOfClass("java/lang/Thread", type) ||
            isSubtypeOfClass("java/lang/ThreadGroup", type)) {
            return EscapeValue.GLOBAL_ESCAPE;
        }

        return EscapeValue.NO_ESCAPE;
    }

    private boolean isSubtypeOfClass(String name, ClassObjectType type) {
        return type.isSubtypeOf(bootstrapClassContext.findDefinedType(name).load().getType());
    }

    @Override
    public ValueHandle instanceFieldOf(ValueHandle handle, FieldElement field) {
        final InstanceFieldOf result = (InstanceFieldOf) super.instanceFieldOf(handle, field);
        checkHandling(result, handle, true);

        // T a = new T(...);
        // To get represent the GC from 'a' to 'new T(...)',
        // we hijack future 'a' references to fix the pointer.
        // When 'a.x' is accessed, we fix the pointer from 'a' to 'new T(...)'.
        handleInstanceFieldOf(result, handle, handle);

        return result;
    }

    @Override
    public ValueHandle instanceFieldOf(ValueHandle handle, TypeDescriptor owner, String name, TypeDescriptor type) {
        final InstanceFieldOf result = (InstanceFieldOf) super.instanceFieldOf(handle, owner, name, type);
        checkHandling(result, handle, true);

        // T a = new T(...);
        // To get represent the GC from 'a' to 'new T(...)',
        // we hijack future 'a' references to fix the pointer.
        // When 'a.x' is accessed, we fix the pointer from 'a' to 'new T(...)'.
        handleInstanceFieldOf(result, handle, handle);

        return result;
    }

    @Override
    public Node store(ValueHandle handle, Value value, WriteAccessMode mode) {
        final Node result = super.store(handle, value, mode);
        checkHandling(result, handle, handle instanceof InstanceFieldOf || handle instanceof StaticField);

        if (handle instanceof StaticField) {
            // static T a = ...
            if (value instanceof NotNull nn) {
                connectionGraph.trackStoreStaticField(handle, nn.getInput());
            } else {
                connectionGraph.trackStoreStaticField(handle, value);
            }
        } else if (handle instanceof InstanceFieldOf && value instanceof New) {
            // p.f = new T(); // where p is a parameter
            connectionGraph.fixEdgesNew(handle, (New) value);
        }
//        else if (handle instanceof LocalVariable && value instanceof New) {
//            connectionGraph.trackLocalNew((LocalVariable) handle, (New) value);
//        }

        return result;
    }

    @Override
    public Value call(ValueHandle target, List<Value> arguments) {
        final Value result = super.call(target, arguments);

        if (Boolean.TRUE.equals(subgraphHandlingCache.get(target))) {
            escapeAnalysisState.trackCall(getCurrentElement(), (Call) result);
        }

        return result;
    }

    @Override
    public void startMethod(List<ParameterValue> arguments) {
        super.startMethod(arguments);
        escapeAnalysisState.trackMethod(getCurrentElement(), this.connectionGraph);
        connectionGraph.trackParameters(arguments);
    }

    @Override
    public BasicBlock return_(Value value) {
        final BasicBlock result = super.return_(value);

        // TODO do truncate values make it to analyze?
        // Skip primitive values truncated, they are not objects
        if (!(value instanceof Truncate)) {
            connectionGraph.trackReturn(value);
        }

        return result;
    }

    @Override
    public BasicBlock throw_(Value value) {
        final BasicBlock result = super.throw_(value);
        checkHandling(value, value instanceof New);

        if (value instanceof New) {
            connectionGraph.trackThrowNew((New) value);
        }

        return result;
    }

    @Override
    public Value checkcast(Value value, Value toType, Value toDimensions, CheckCast.CastType kind, ObjectType expectedType) {
        final CheckCast result = (CheckCast) super.checkcast(value, toType, toDimensions, kind, expectedType);
        checkHandling(result, value, true);
        checkHandling(result, toType, true);
        checkHandling(result, toDimensions, true);
        connectionGraph.trackCast(result);
        return result;
    }

    @Override
    public Value checkcast(Value value, TypeDescriptor desc) {
        final CheckCast result = (CheckCast) super.checkcast(value, desc);
        checkHandling(result, value, true);
        connectionGraph.trackCast(result);
        return result;
    }

    @Override
    public void finish() {
        super.finish();
        // Incoming values for phi nodes can only be calculated upon finish.
        connectionGraph.resolveReturnedPhiValues();

        // TODO Look for unhandled nodes and traverse those back to see if there's any New along the way.
        //      If there are any, change their escape state value to global escape pessimistically.
    }

    private void handleInstanceFieldOf(InstanceFieldOf result, ValueHandle handle, Node target) {
        if (target instanceof New) {
            connectionGraph.fixEdgesField((New) target, handle, result);
        } else if (target instanceof ParameterValue) {
            connectionGraph.fixEdgesParameterValue((ParameterValue) target, result);
        } else if (target instanceof Store) {
            final Value value = ((Store) target).getValue();
            if (value instanceof New) {
                handleInstanceFieldOf(result, handle, value);
            } else {
                handleInstanceFieldOf(result, handle, target.getValueHandle());
            }
        } else if (target instanceof InstanceFieldOf) {
            handleInstanceFieldOf(result, handle, target.getValueHandle());
        } else if (target instanceof ReferenceHandle) {
            handleInstanceFieldOf(result, handle, ((ReferenceHandle) target).getReferenceValue());
        } else if (target instanceof OrderedNode) {
            handleInstanceFieldOf(result, handle, ((OrderedNode) target).getDependency());
        }
    }

    @Override
    public ValueHandle referenceHandle(Value reference) {
        final ValueHandle refHandle = super.referenceHandle(reference);
        checkHandling(refHandle, reference, true);
        return refHandle;
    }

    @Override
    public ValueHandle constructorOf(Value instance, ConstructorElement constructor, MethodDescriptor callSiteDescriptor, FunctionType callSiteType) {
        final ValueHandle ctorHandle = super.constructorOf(instance, constructor, callSiteDescriptor, callSiteType);
        checkHandling(ctorHandle, instance, ctorHandle instanceof Executable); // Only Executable constructors handled
        return ctorHandle;
    }

    @Override
    public ValueHandle constructorOf(Value instance, TypeDescriptor owner, MethodDescriptor descriptor) {
        final ValueHandle ctorHandle = super.constructorOf(instance, owner, descriptor);
        checkHandling(ctorHandle, instance, ctorHandle instanceof Executable); // Only Executable constructors handled
        return ctorHandle;
    }

    @Override
    public Value load(ValueHandle handle, ReadAccessMode accessMode) {
        final Value load = super.load(handle, accessMode);
        checkHandling(load, handle, true);
        return load;
    }

    @Override
    public ParameterValue parameter(ValueType type, String label, int index) {
        final ParameterValue parameter = super.parameter(type, label, index);
        checkHandling(parameter, true);
        return parameter;
    }

    @Override
    public ValueHandle staticField(FieldElement field) {
        final ValueHandle staticField = super.staticField(field);
        checkHandling(staticField, true);
        return staticField;
    }

    @Override
    public ValueHandle staticField(TypeDescriptor owner, String name, TypeDescriptor type) {
        final ValueHandle staticField = super.staticField(owner, name, type);
        checkHandling(staticField, true);
        return staticField;
    }

    @Override
    public ValueHandle staticMethod(MethodElement method, MethodDescriptor callSiteDescriptor, FunctionType callSiteType) {
        final ValueHandle staticMethod = super.staticMethod(method, callSiteDescriptor, callSiteType);
        checkHandling(staticMethod, true);
        return staticMethod;
    }

    @Override
    public ValueHandle staticMethod(TypeDescriptor owner, String name, MethodDescriptor descriptor) {
        final ValueHandle staticMethod = super.staticMethod(owner, name, descriptor);
        checkHandling(staticMethod, true);
        return staticMethod;
    }

    @Override
    public BasicBlock if_(Value condition, BlockLabel trueTarget, BlockLabel falseTarget) {
        final BasicBlock if_ = super.if_(condition, trueTarget, falseTarget);
        checkHandling(condition, true);
        return if_;
    }

    @Override
    public Value isEq(Value v1, Value v2) {
        final Value isEq = super.isEq(v1, v2);
        checkHandling(isEq, v1,true);
        checkHandling(isEq, v2,true);
        return isEq;
    }

    @Override
    public Value isLt(Value v1, Value v2) {
        final Value isLt = super.isLt(v1, v2);
        checkHandling(isLt, v1,true);
        checkHandling(isLt, v2,true);
        return isLt;
    }

    @Override
    public Value isGt(Value v1, Value v2) {
        final Value isGt = super.isGt(v1, v2);
        checkHandling(isGt, v1,true);
        checkHandling(isGt, v2,true);
        return isGt;
    }

    @Override
    public Value isLe(Value v1, Value v2) {
        final Value isLe = super.isLe(v1, v2);
        checkHandling(isLe, v1,true);
        checkHandling(isLe, v2,true);
        return isLe;
    }

    @Override
    public Value isGe(Value v1, Value v2) {
        final Value isGe = super.isGe(v1, v2);
        checkHandling(isGe, v1,true);
        checkHandling(isGe, v2,true);
        return isGe;
    }

    @Override
    public Value isNe(Value v1, Value v2) {
        final Value isNe = super.isNe(v1, v2);
        checkHandling(isNe, v1,true);
        checkHandling(isNe, v2,true);
        return isNe;
    }

//    @Override
//    public BasicBlock callNoReturn(ValueHandle target, List<Value> arguments) {
//        final BasicBlock result = super.callNoReturn(target, arguments);
//        checkHandling(target, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value invoke(ValueHandle target, List<Value> arguments, BlockLabel catchLabel, BlockLabel resumeLabel) {
//        final Value result = super.invoke(target, arguments, catchLabel, resumeLabel);
//        checkHandling(result, target, false); // TODO make it handle
//        for (Value argument : arguments) {
//            checkHandling(result, argument, false); // TODO make it handle
//        }
//        return result;
//    }
//
//    @Override
//    public Value notNull(Value v) {
//        final Value result = super.notNull(v);
//        checkHandling(result, v,false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value instanceOf(Value input, ObjectType expectedType, int expectedDimensions) {
//        final Value reult = super.instanceOf(input, expectedType, expectedDimensions);
//        checkHandling(reult, input, false); // TODO make it handle
//        return reult;
//    }
//
//    @Override
//    public Value instanceOf(Value input, TypeDescriptor desc) {
//        final Value result = super.instanceOf(input, desc);
//        checkHandling(result, input, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value getAndAdd(ValueHandle target, Value update, ReadAccessMode readMode, WriteAccessMode writeMode) {
//        final Value result = super.getAndAdd(target, update, readMode, writeMode);
//        checkHandling(result, target, false); // TODO make it handle
//        checkHandling(result, update, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value getAndBitwiseAnd(ValueHandle target, Value update, ReadAccessMode readMode, WriteAccessMode writeMode) {
//        final Value result = super.getAndBitwiseAnd(target, update, readMode, writeMode);
//        checkHandling(result, target, false); // TODO make it handle
//        checkHandling(result, update, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value getAndBitwiseNand(ValueHandle target, Value update, ReadAccessMode readMode, WriteAccessMode writeMode) {
//        final Value result = super.getAndBitwiseNand(target, update, readMode, writeMode);
//        checkHandling(result, target, false); // TODO make it handle
//        checkHandling(result, update, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value getAndBitwiseOr(ValueHandle target, Value update, ReadAccessMode readMode, WriteAccessMode writeMode) {
//        final Value result = super.getAndBitwiseOr(target, update, readMode, writeMode);
//        checkHandling(result, target, false); // TODO make it handle
//        checkHandling(result, update, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value getAndBitwiseXor(ValueHandle target, Value update, ReadAccessMode readMode, WriteAccessMode writeMode) {
//        final Value result = super.getAndBitwiseXor(target, update, readMode, writeMode);
//        checkHandling(result, target, false); // TODO make it handle
//        checkHandling(result, update, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value getAndSet(ValueHandle target, Value update, ReadAccessMode readMode, WriteAccessMode writeMode) {
//        final Value result = super.getAndSet(target, update, readMode, writeMode);
//        checkHandling(result, target, false); // TODO make it handle
//        checkHandling(result, update, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value getAndSetMax(ValueHandle target, Value update, ReadAccessMode readMode, WriteAccessMode writeMode) {
//        final Value result = super.getAndSetMax(target, update, readMode, writeMode);
//        checkHandling(result, target, false); // TODO make it handle
//        checkHandling(result, update, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value getAndSetMin(ValueHandle target, Value update, ReadAccessMode readMode, WriteAccessMode writeMode) {
//        final Value result = super.getAndSetMin(target, update, readMode, writeMode);
//        checkHandling(result, target, false); // TODO make it handle
//        checkHandling(result, update, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value getAndSub(ValueHandle target, Value update, ReadAccessMode readMode, WriteAccessMode writeMode) {
//        final Value result = super.getAndSub(target, update, readMode, writeMode);
//        checkHandling(result, target, false); // TODO make it handle
//        checkHandling(result, update, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value bitCast(Value value, WordType toType) {
//        final Value result = super.bitCast(value, toType);
//        checkHandling(result, value, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value select(Value condition, Value trueValue, Value falseValue) {
//        final Value result = super.select(condition, trueValue, falseValue);
//        checkHandling(result, condition, false); // TODO make it handle
//        checkHandling(result, trueValue, false); // TODO make it handle
//        checkHandling(result, falseValue, false); // TODO make it handle
//        return result;
//    }

    @Override
    public ValueHandle interfaceMethodOf(Value instance, MethodElement method, MethodDescriptor callSiteDescriptor, FunctionType callSiteType) {
        final ValueHandle result = super.interfaceMethodOf(instance, method, callSiteDescriptor, callSiteType);
        checkHandling(result, instance, true);
        return result;
    }

    @Override
    public ValueHandle interfaceMethodOf(Value instance, TypeDescriptor owner, String name, MethodDescriptor descriptor) {
        final ValueHandle result = super.interfaceMethodOf(instance, owner, name, descriptor);
        checkHandling(result, instance, true);
        return result;
    }

    @Override
    public ValueHandle exactMethodOf(Value instance, MethodElement method, MethodDescriptor callSiteDescriptor, FunctionType callSiteType) {
        final ValueHandle result = super.exactMethodOf(instance, method, callSiteDescriptor, callSiteType);
        checkHandling(result, instance, true);
        return result;
    }

    @Override
    public ValueHandle exactMethodOf(Value instance, TypeDescriptor owner, String name, MethodDescriptor descriptor) {
        final ValueHandle result = super.exactMethodOf(instance, owner, name, descriptor);
        checkHandling(result, instance, true);
        return result;
    }

    @Override
    public ValueHandle virtualMethodOf(Value instance, MethodElement method, MethodDescriptor callSiteDescriptor, FunctionType callSiteType) {
        ValueHandle result = super.virtualMethodOf(instance, method, callSiteDescriptor, callSiteType);
        checkHandling(result, instance, true);
        return result;
    }

    @Override
    public ValueHandle virtualMethodOf(Value instance, TypeDescriptor owner, String name, MethodDescriptor descriptor) {
        ValueHandle result = super.virtualMethodOf(instance, owner, name, descriptor);
        checkHandling(result, instance, true);
        return result;
    }

//    @Override
//    public Value add(Value v1, Value v2) {
//        Value result = super.add(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value multiply(Value v1, Value v2) {
//        Value result = super.multiply(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }

//    @Override
//    public Value and(Value v1, Value v2) {
//        Value result = super.and(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value or(Value v1, Value v2) {
//        Value result = super.or(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value xor(Value v1, Value v2) {
//        Value result = super.xor(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value shr(Value v1, Value v2) {
//        Value result = super.shr(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value shl(Value v1, Value v2) {
//        Value result = super.shl(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value sub(Value v1, Value v2) {
//        Value result = super.sub(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value divide(Value v1, Value v2) {
//        Value result = super.divide(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }

//    @Override
//    public Value remainder(Value v1, Value v2) {
//        Value result = super.remainder(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value min(Value v1, Value v2) {
//        Value result = super.min(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value max(Value v1, Value v2) {
//        Value result = super.max(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value rol(Value v1, Value v2) {
//        Value result = super.rol(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public Value ror(Value v1, Value v2) {
//        Value result = super.ror(v1, v2);
//        checkHandling(result, v1, false); // TODO make it handle
//        return result;
//    }
//
//    @Override
//    public ValueHandle localVariable(LocalVariableElement variable) {
//        final ValueHandle result = super.localVariable(variable);
//        checkHandling(result, false); // TODO make it handle
//        return result;
//    }

    @Override
    public PhiValue phi(ValueType type, BlockLabel owner, PhiValue.Flag... flags) {
        final PhiValue result = super.phi(type, owner, flags);
        checkHandling(result, true);
        return result;
    }

    private void checkHandling(Node from, Node to, boolean handled) {
        final Boolean handledTo = subgraphHandlingCache.get(to);
        if (handledTo == null) {
            subgraphHandlingCache.put(to, handled);
            subgraphHandlingCache.put(from, handled);
        } else {
            if (!handledTo) {
                // If unhandled, the subgraph needs to be skipped
                subgraphHandlingCache.put(from, false);
            } else {
                subgraphHandlingCache.put(from, handled);
            }
        }
    }

    private void checkHandling(Node node, boolean handled) {
        subgraphHandlingCache.put(node, handled);
    }
}
