package org.qbicc.plugin.opt;

import org.qbicc.context.CompilationContext;
import org.qbicc.graph.BasicBlock;
import org.qbicc.graph.BasicBlockBuilder;
import org.qbicc.graph.ConstructorElementHandle;
import org.qbicc.graph.DelegatingBasicBlockBuilder;
import org.qbicc.graph.Load;
import org.qbicc.graph.MemoryAtomicityMode;
import org.qbicc.graph.Node;
import org.qbicc.graph.OrderedNode;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.type.ClassObjectType;
import org.qbicc.type.definition.element.ConstructorElement;
import org.qbicc.type.definition.element.ExecutableElement;
import org.qbicc.type.definition.element.MethodElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EscapeAnalysisBasicBlockBuilder extends DelegatingBasicBlockBuilder {
    private final CompilationContext ctxt;
    private final Map<ExecutableElement, ConnectionGraph> connectionGraphs = new HashMap<>();

    public EscapeAnalysisBasicBlockBuilder(final CompilationContext ctxt, final BasicBlockBuilder delegate) {
        super(delegate);
        this.ctxt = ctxt;
    }

    private ConnectionGraph connectionGraph() {
        return connectionGraphs.computeIfAbsent(getCurrentElement(), e -> new ConnectionGraph());
    }

    @Override
    public Value call(ValueHandle target, List<Value> arguments) {
        if (target instanceof ConstructorElementHandle) {
            final ConstructorElementHandle constructorHandle = (ConstructorElementHandle) target;
            System.out.println("invokeConstructor (EA) : " + constructorHandle.getInstance());
            addFieldEdges(constructorHandle.getInstance(), arguments, connectionGraph());
            return super.call(target, arguments);
        }

        return super.call(target, arguments);
    }

    public Value new_(ClassObjectType type) {
        final Value newValue = super.new_(type);
        System.out.println("new_ (EA) : " + type + ", returning: " + newValue);
        return newValue;
    }

    public Node store(ValueHandle handle, Value value, MemoryAtomicityMode mode) {
        // TODO initialize static fields with GlobalState

//        if (value instanceof ConstructorInvocation) {
//            connectionGraph().addPointsToEdge(handle, value);
//        } else if (value instanceof Load) {
//            connectionGraph().addDeferredEdge(handle, value);
//        }

        return super.store(handle, value, mode);
    }

    public Value load(ValueHandle handle, MemoryAtomicityMode mode) {
        final Value value = super.load(handle, mode);
        fixPointsToIfNeeded(value, handle);
        return value;
    }

    // Workaround for lack of local variables in the CFG
    private void fixPointsToIfNeeded(Node current, ValueHandle handle) {
        if (current instanceof Value) {
            final Value value = (Value) current;
            final ConnectionGraph cg = connectionGraph();
            if (cg.fieldEdges.containsKey(value) && !cg.pointsToEdges.containsKey(handle)) {
                cg.addPointsToEdge(handle, value);
                return;
            }
        }

        if (current instanceof OrderedNode) {
            fixPointsToIfNeeded(((OrderedNode) current).getDependency(), handle);
        }
    }

    public BasicBlock return_(Value value) {
        EscapeAnalysis.get(ctxt).argEscape(value);
        return super.return_(value);
    }

    private void addFieldEdges(Value value, List<Value> fields, ConnectionGraph cg) {
        cg.fieldEdges.put(value, fields);
        fields.forEach(field -> EscapeAnalysis.get(ctxt).noEscape(field));
        EscapeAnalysis.get(ctxt).noEscape(value);
    }

    private static final class ConnectionGraph {
        private final Map<ValueHandle, Value> pointsToEdges = new HashMap<>(); // solid (P) edges
        private final Map<ValueHandle, Value> deferredEdges = new HashMap<>(); // dashed (D) edges
        private final Map<Value, List<Value>> fieldEdges = new HashMap<>(); // solid (F) edges

        // private final Map<Node, EscapeState> escapeStates = new HashMap<>();

        void addPointsToEdge(ValueHandle handle, Value value) {
            pointsToEdges.put(handle, value);
            // TODO fix points-to escape state
            // escapeStates.put(handle, EscapeState.NO_ESCAPE);
        }

        void addDeferredEdge(ValueHandle handle, Value value) {
            deferredEdges.put(handle, value);
            // TODO fix deferred edge escape state
            // escapeStates.put(handle, EscapeState.NO_ESCAPE);
        }

//        void addFieldEdges(Value value, List<Value> fields) {
//            fieldEdges.put(value, fields);
//            fields.forEach(field -> escapeStates.put(field, EscapeState.NO_ESCAPE));
//        }

//        void setArgEscape(Node node) {
//            // escapeStates.put(node, EscapeState.ARG_ESCAPE);
//        }
    }

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
