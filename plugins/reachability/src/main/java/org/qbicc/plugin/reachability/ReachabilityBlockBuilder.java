package org.qbicc.plugin.reachability;

import java.util.List;

import org.qbicc.context.CompilationContext;
import org.qbicc.graph.BasicBlockBuilder;
import org.qbicc.graph.DelegatingBasicBlockBuilder;
import org.qbicc.graph.DispatchInvocation;
import org.qbicc.graph.Node;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.plugin.layout.Layout;
import org.qbicc.type.ArrayObjectType;
import org.qbicc.type.ClassObjectType;
import org.qbicc.type.InterfaceObjectType;
import org.qbicc.type.ObjectType;
import org.qbicc.type.ReferenceArrayObjectType;
import org.qbicc.type.definition.DefinedTypeDefinition;
import org.qbicc.type.definition.LoadedTypeDefinition;
import org.qbicc.type.definition.element.ConstructorElement;
import org.qbicc.type.definition.element.ExecutableElement;
import org.qbicc.type.definition.element.FieldElement;
import org.qbicc.type.definition.element.InitializerElement;
import org.qbicc.type.definition.element.MethodElement;
import org.jboss.logging.Logger;

/**
 * A block builder stage which recursively enqueues all referenced executable elements.
 * We implement an RTA-style analysis to identify reachable virtual methods based on
 * the set of reachable call sites and instantiated types.
 */
public class ReachabilityBlockBuilder extends DelegatingBasicBlockBuilder {
    static final Logger rtaLog = Logger.getLogger("org.qbicc.plugin.reachability.rta");

    private final CompilationContext ctxt;
    private final ExecutableElement originalElement;

    public ReachabilityBlockBuilder(final CompilationContext ctxt, final BasicBlockBuilder delegate) {
        super(delegate);
        this.ctxt = ctxt;
        this.originalElement = delegate.getCurrentElement();
    }

    public Node invokeStatic(final MethodElement target, final List<Value> arguments) {
        // cause the class to be initialized
        InitializerElement initializer = target.getEnclosingType().load().getInitializer();
        if (initializer != null) {
            ctxt.enqueue(initializer);
        }
        ctxt.enqueue(target);
        return super.invokeStatic(target, arguments);
    }

    public Node invokeInstance(final DispatchInvocation.Kind kind, final Value instance, final MethodElement target, final List<Value> arguments) {
        if (!ctxt.wasEnqueued(target)) {
            rtaLog.debugf("Adding method %s (directly invoked in %s)", target, originalElement);
            ctxt.enqueue(target);
            processInvokeTarget(target);
        }
        return super.invokeInstance(kind, instance, target, arguments);
    }

    public Value invokeValueStatic(final MethodElement target, final List<Value> arguments) {
        // cause the class to be initialized
        InitializerElement initializer = target.getEnclosingType().load().getInitializer();
        if (initializer != null) {
            ctxt.enqueue(initializer);
        }
        ctxt.enqueue(target);
        return super.invokeValueStatic(target, arguments);
    }

    public Value invokeValueInstance(final DispatchInvocation.Kind kind, final Value instance, final MethodElement target, final List<Value> arguments) {
        if (!ctxt.wasEnqueued(target)) {
            rtaLog.debugf("Adding method %s (directly invoked in %s)", target, originalElement);
            ctxt.enqueue(target);
            processInvokeTarget(target);
        }
        return super.invokeValueInstance(kind, instance, target, arguments);
    }

    public Value invokeConstructor(final Value instance, final ConstructorElement target, final List<Value> arguments) {
        // cause the class to be initialized
        InitializerElement initializer = target.getEnclosingType().load().getInitializer();
        if (initializer != null) {
            ctxt.enqueue(initializer);
        }
        processInstantiatedClass(target.getEnclosingType().load(), true, false);
        ctxt.enqueue(target);
        return super.invokeConstructor(instance, target, arguments);
    }

    public Value newArray(final ArrayObjectType arrayType, Value size) {
        if (arrayType instanceof ReferenceArrayObjectType) {
            // To avoid obscure corner cases errors in dynamic type checking code,
            // force the array's leaf element type to be resolved (and thus assigned a typeId).
            ObjectType elemType = ((ReferenceArrayObjectType)arrayType).getLeafElementType();
            if (elemType instanceof ClassObjectType) {
                processInstantiatedClass(elemType.getDefinition().load(), false, true);
            } else if (elemType instanceof InterfaceObjectType) {
                processInstantiatedInterface(RTAInfo.get(ctxt), elemType.getDefinition().load(), true);
            }
        }
        processInstantiatedClass(Layout.get(ctxt).getArrayContentField(arrayType).getEnclosingType().load(), true, false);
        return super.newArray(arrayType, size);
    }

    public Value multiNewArray(final ArrayObjectType arrayType, final List<Value> dimensions) {
        if (arrayType instanceof ReferenceArrayObjectType) {
            // To avoid obscure corner cases errors in dynamic type checking code,
            // force the array's leaf element type to be resolved (and thus assigned a typeId).
            ObjectType elemType = ((ReferenceArrayObjectType) arrayType).getLeafElementType();
            if (elemType instanceof ClassObjectType) {
                processInstantiatedClass(elemType.getDefinition().load(), false, true);
            } else if (elemType instanceof InterfaceObjectType) {
                processInstantiatedInterface(RTAInfo.get(ctxt), elemType.getDefinition().load(), true);
            }
        }
        return super.multiNewArray(arrayType, dimensions);
    }

    @Override
    public ValueHandle staticField(FieldElement field) {
        DefinedTypeDefinition enclosingType = field.getEnclosingType();
        // initialize referenced field
        ctxt.enqueue(enclosingType.load().getInitializer());
        return super.staticField(field);
    }

    @Override
    public Value classOf(Value typeId) {
        MethodElement methodElement = ctxt.getVMHelperMethod("classof_from_typeid");
        ctxt.enqueue(methodElement);
        return super.classOf(typeId);
    }

    private void processInstantiatedClass(final LoadedTypeDefinition type, boolean directlyInstantiated, boolean arrayElement) {
        RTAInfo info = RTAInfo.get(ctxt);
        if (!info.isLiveClass(type)) {
            if (directlyInstantiated) {
                rtaLog.debugf("Adding class %s (instantiated in %s)", type.getDescriptor().getClassName(), originalElement);
            } else if (arrayElement) {
                rtaLog.debugf("Adding class %s (array element in %s)", type.getDescriptor().getClassName(), originalElement);
            } else {
                rtaLog.debugf("\tadding ancestor class: %s", type.getDescriptor().getClassName());
            }
            if (type.hasSuperClass()) {
                processInstantiatedClass(type.getSuperClass(), false, false);
                info.addLiveClass(type);

                // For every instance method that is not already enqueued,
                // check to see if it is overriding an enqueued method and thus should be enqueued.
                for (MethodElement im: type.getInstanceMethods()) {
                    if (!ctxt.wasEnqueued(im)) {
                        MethodElement overiddenMethod = type.getSuperClass().resolveMethodElementVirtual(im.getName(), im.getDescriptor());
                        if (overiddenMethod != null && ctxt.wasEnqueued(overiddenMethod)) {
                            ctxt.enqueue(im);
                            rtaLog.debugf("\tnewly reachable class: enqueued overriding method: %s", im);
                        }
                    }
                }
            }

            // Extend the interface hierarchy
            for (LoadedTypeDefinition i: type.getInterfaces()) {
                processInstantiatedInterface(info, i, false);
                info.addInterfaceEdge(type, i);
            }

            // For every enqueued interface method, make sure my implementation of that method is also enqueued.
            for (LoadedTypeDefinition i: type.getInterfaces()) {
                for (MethodElement sig: i.getInstanceMethods()) {
                    if (ctxt.wasEnqueued((sig))) {
                        MethodElement impl = type.resolveMethodElementVirtual(sig.getName(), sig.getDescriptor());
                        if (impl != null && !ctxt.wasEnqueued(impl)) {
                            ctxt.enqueue(impl);
                            rtaLog.debugf("\tnewly reachable class: enqueued implementing method:  %s", impl);
                        }
                    }
                }
            }
        }
    }

    private void processInstantiatedInterface(RTAInfo info, final LoadedTypeDefinition type, boolean arrayElement) {
        if (!info.isLiveInterface(type)) {
            info.makeInterfaceLive(type);
            if (arrayElement) {
                rtaLog.debugf("Adding interface used as array element: %s", type.getDescriptor().getClassName());
            } else {
                rtaLog.debugf("\tadding implemented interface: %s", type.getDescriptor().getClassName());
            }
            for (LoadedTypeDefinition i: type.getInterfaces()) {
                processInstantiatedInterface(info, i, false);
                info.addInterfaceEdge(type, i);
            }

            // For every instance method that is not already enqueued,
            // check to see if it has the same selector as an enqueued super-interface method and thus should be enqueued.
            outer: for (MethodElement im: type.getInstanceMethods()) {
                if (!ctxt.wasEnqueued(im)) {
                    for (LoadedTypeDefinition si : type.getInterfaces()) {
                        MethodElement sm = si.resolveMethodElementInterface(im.getName(), im.getDescriptor());
                        if (sm != null && ctxt.wasEnqueued(sm)) {
                            rtaLog.debugf("\tnewly reachable interface: enqueued implementing method:  %s", im);
                            ctxt.enqueue(im);
                            continue outer;
                        }
                    }
                }
            }
        }
    }

    private void processInvokeTarget(final MethodElement target) {
        RTAInfo info = RTAInfo.get(ctxt);
        LoadedTypeDefinition definingClass = target.getEnclosingType().load();

        if (definingClass.isInterface()) {
            // Traverse the instantiated extenders and implementors and handle as-if we just saw
            // an invoke of their overriding/implementing method
            info.visitLiveImplementors(definingClass, (c) -> {
                MethodElement cand;
                if (c.isInterface()) {
                    cand = c.resolveMethodElementInterface(target.getName(), target.getDescriptor());
                } else {
                    cand = c.resolveMethodElementVirtual(target.getName(), target.getDescriptor());
                }
                if (!ctxt.wasEnqueued(cand)) {
                    rtaLog.debugf("\tadding method (implements): %s", cand);
                    try {
                        ctxt.enqueue(cand);
                    } catch (IllegalStateException e) {
                        ctxt.error(getLocation(), "Unexpected failure enqueueing a reachable element: %s", e);
                    }
                    processInvokeTarget(cand);
                }
            });
        } else {
            // Traverse the instantiated subclasses of target's defining class and
            // ensure that all overriding implementations of this method are marked invokable.
            info.visitLiveSubclassesPreOrder(definingClass, (sc) -> {
                MethodElement cand = sc.resolveMethodElementVirtual(target.getName(), target.getDescriptor());
                if (!ctxt.wasEnqueued(cand)) {
                    rtaLog.debugf("\tadding method (subclass overrides): %s", cand);
                    try {
                        ctxt.enqueue(cand);
                    } catch (IllegalStateException e) {
                        ctxt.error(getLocation(), "Unexpected failure enqueueing a reachable element: %s", e);
                    }
                }
            });
        }
    }
}
