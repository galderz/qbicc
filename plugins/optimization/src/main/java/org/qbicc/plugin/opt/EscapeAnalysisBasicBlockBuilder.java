package org.qbicc.plugin.opt;

import org.qbicc.context.CompilationContext;
import org.qbicc.graph.BasicBlockBuilder;
import org.qbicc.graph.ConstructorElementHandle;
import org.qbicc.graph.DelegatingBasicBlockBuilder;
import org.qbicc.graph.MemoryAtomicityMode;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.ReferenceHandle;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.type.ClassObjectType;
import org.qbicc.type.definition.element.FieldElement;
import org.qbicc.type.descriptor.TypeDescriptor;

import java.util.List;

public class EscapeAnalysisBasicBlockBuilder extends DelegatingBasicBlockBuilder {
    private final CompilationContext ctxt;

    public EscapeAnalysisBasicBlockBuilder(final CompilationContext ctxt, final BasicBlockBuilder delegate) {
        super(delegate);
        this.ctxt = ctxt;
    }

    private static void log(String format, Object... args) {
        System.out.printf(
            "(%s) [ea-bbb] %s%n"
            , Thread.currentThread().getName()
            , String.format(format, args)
        );
    }

    /**
     * CG: new T(...)
     */
    @Override
    public Value call(ValueHandle target, List<Value> arguments) {
        if (target instanceof ConstructorElementHandle && ((ConstructorElementHandle) target).getInstance() instanceof New) {
            final Value value = ((ConstructorElementHandle) target).getInstance();
            EscapeAnalysis.get(ctxt).newObject(value, arguments, getCurrentElement());
            final Value result = super.call(target, arguments);
            log("invokeConstructor(%s) returns %s", value, result);
            return result;
        }

        return super.call(target, arguments);
    }

    public Value new_(ClassObjectType type) {
        final Value newValue = super.new_(type);
        log("new_(%s) returns %s", type, newValue);
        return newValue;
    }

    public Node store(ValueHandle handle, Value value, MemoryAtomicityMode mode) {
        // TODO initialize static fields with GlobalState

        final Node result = super.store(handle, value, mode);

        log("store(%s) into %s returns %s", value, handle, result);

//        if (value instanceof ConstructorInvocation) {
//            connectionGraph().addPointsToEdge(handle, value);
//        } else if (value instanceof Load) {
//            connectionGraph().addDeferredEdge(handle, value);
//        }

        return result;
    }

    /**
     * Workaround lack of local variables.
     *
     * To get represent the GC from 'a' to 'new T(...)',
     * we hijack future 'a' references to fix the pointer.
     * When 'a.x' is accessed, we fix the pointer from 'a' to 'new T(...)'.
     */
    @Override
    public ValueHandle instanceFieldOf(ValueHandle handle, FieldElement field) {
        final ValueHandle result = super.instanceFieldOf(handle, field);
        log("instanceFieldOf(%s->%s) of returns %s", handle, handle.getValueDependency(0), result);

        if (handle instanceof ReferenceHandle) {
            final ReferenceHandle refHandle = (ReferenceHandle) handle;
            EscapeAnalysis.get(ctxt).fixPointsToIfNeeded(refHandle.getReferenceValue(), handle, getCurrentElement());
        }

        return result;
    }

//    // Workaround for lack of local variables in the CFG
//    private void fixPointsToIfNeeded(Value value, ValueHandle handle) {
//
//
////        if (current instanceof Value) {
////            final Value value = (Value) current;
////            final ConnectionGraph cg = connectionGraph();
////            if (cg.fieldEdges.containsKey(value) && !cg.pointsToEdges.containsKey(handle)) {
////                cg.addPointsToEdge(handle, value);
////                return;
////            }
////        }
////
////        if (current instanceof OrderedNode) {
////            fixPointsToIfNeeded(((OrderedNode) current).getDependency(), handle);
////        }
//    }


    public Value load(ValueHandle handle, MemoryAtomicityMode mode) {
        final Value result = super.load(handle, mode);

        log("load(%s) returns %s", handle, result);

        // fixPointsToIfNeeded(value, handle);
        return result;
    }

//    // Workaround for lack of local variables in the CFG
//    private void fixPointsToIfNeeded(Node current, ValueHandle handle) {
//        if (current instanceof Value) {
//            final Value value = (Value) current;
//            final ConnectionGraph cg = connectionGraph();
//            if (cg.fieldEdges.containsKey(value) && !cg.pointsToEdges.containsKey(handle)) {
//                cg.addPointsToEdge(handle, value);
//                return;
//            }
//        }
//
//        if (current instanceof OrderedNode) {
//            fixPointsToIfNeeded(((OrderedNode) current).getDependency(), handle);
//        }
//    }

//    @Override
//    public Value new_(ClassObjectType type) {
//        return super.new_(type);
//    }

//    @Override
//    public Node begin(BlockLabel blockLabel) {
//        System.out.printf("begin(%s)%n", blockLabel.toString());
//        return super.begin(blockLabel);
//    }

//    @Override
//    public ExecutableElement setCurrentElement(ExecutableElement element) {
//        System.out.printf("setCurrentElement(%s)%n", element);
//        return super.setCurrentElement(element);    // TODO: Customise this generated block
//    }
//
//    @Override
//    public BasicBlock return_() {
//        System.out.printf("return()%n");
//        return super.return_();    // TODO: Customise this generated block
//    }
//
//    @Override
//    public BasicBlock return_(Value value) {
//        System.out.printf("return(%s)%n", value);
//        return super.return_(value);    // TODO: Customise this generated block
//    }

    static enum EscapeState {
        GLOBAL_ESCAPE, ARG_ESCAPE, NO_ESCAPE

//        EscapeState merge(EscapeState es) {
//            if (es == NO_ESCAPE) {
//                return this;
//            }
//
//            if (es == GLOBAL_ESCAPE) {
//                return GLOBAL_ESCAPE;
//            }
//
//            return es;
//        }
    }
    
    
}
