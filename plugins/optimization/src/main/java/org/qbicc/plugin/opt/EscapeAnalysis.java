package org.qbicc.plugin.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.qbicc.context.AttachmentKey;
import org.qbicc.context.CompilationContext;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.ParameterValue;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.type.definition.element.ExecutableElement;

final class EscapeAnalysis {
    private static final AttachmentKey<EscapeAnalysis> KEY = new AttachmentKey<>();

    private final Map<ExecutableElement, ConnectionGraph> connectionGraphs = new ConcurrentHashMap<>();

    static EscapeAnalysis get(CompilationContext ctxt) {
        EscapeAnalysis escapeAnalysis = ctxt.getAttachment(KEY);
        if (escapeAnalysis == null) {
            escapeAnalysis = new EscapeAnalysis();
            EscapeAnalysis appearing = ctxt.putAttachmentIfAbsent(KEY, escapeAnalysis);
            if (appearing != null) {
                escapeAnalysis = appearing;
            }
        }
        return escapeAnalysis;
    }

    private ConnectionGraph connectionGraph(ExecutableElement element) {
        return connectionGraphs.computeIfAbsent(element, e -> new ConnectionGraph());
    }

    boolean notEscapingMethod(Node node) {
        return escapeState(node)
            .filter(escapeState -> escapeState == EscapeState.NO_ESCAPE)
            .isPresent();
    }

    private Optional<EscapeState> escapeState(Node node) {
        return connectionGraphs.values().stream()
            .flatMap(cg -> cg.escapeStates.entrySet().stream())
            .filter(e -> e.getKey().equals(node))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    public void setNoEscape(ExecutableElement element, Value value) {
        final ConnectionGraph cg = connectionGraph(element);
        cg.escapeStates.put(value, EscapeState.NO_ESCAPE);
    }

//    public void fieldStore(ValueHandle valueHandle, Value value, ExecutableElement element) {
//        final ConnectionGraph cg = connectionGraph(element);
//        cg.addFieldEdge(valueHandle, value);
//    }

    public boolean addFieldEdgeIfAbsent(ExecutableElement element, New new_, ValueHandle field) {
        final ConnectionGraph cg = connectionGraph(element);
        return cg.fieldEdges
            .computeIfAbsent(new_, obj -> new HashSet<>())
            .add(field);
    }

    public boolean addPointsToEdgeIfAbsent(ExecutableElement element, ValueHandle ref, New new_) {
        final ConnectionGraph cg = connectionGraph(element);
        return cg.pointsToEdges.putIfAbsent(ref, new_) == null;
    }

    public void fieldAccessParam(ParameterValue value, ValueHandle handle, ExecutableElement element) {
        final ConnectionGraph cg = connectionGraph(element);
        cg.addPointsToEdgeParam(handle, value);
    }

    public void setGlobalEscape(ExecutableElement element, Value value) {
        final ConnectionGraph cg = connectionGraph(element);
        cg.escapeStates.put(value, EscapeState.GLOBAL_ESCAPE);
    }

//    public void methodEntry(ParameterValue argument, ValueHandle phantomReference, ExecutableElement element) {
//        final ConnectionGraph cg = connectionGraph(element);
//        cg.addDeferredEdge(argument, phantomReference);
//        cg.setNoEscape(argument);
//        cg.setArgEscape(phantomReference);
//    }

    public void methodExit(ExecutableElement element) {
        // TODO: summarise method cg
    }

//    private void setArgEscape(ConnectionGraph cg, Node value) {
//        cg.escapeStates.put(value, EscapeState.ARG_ESCAPE);
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

    private static final class ConnectionGraph {
        private final Map<ValueHandle, Value> pointsToEdges = new ConcurrentHashMap<>(); // solid (P) edges
        private final Map<Value, ValueHandle> deferredEdges = new ConcurrentHashMap<>(); // dashed (D) edges
        private final Map<Value, Set<ValueHandle>> fieldEdges = new ConcurrentHashMap<>(); // solid (F) edges

        private final Map<Node, EscapeState> escapeStates = new HashMap<>();

//        void addPointsToEdge(ValueHandle handle, Value value) {
//            pointsToEdges.put(handle, value);
//            setNoEscape(value);
//        }

//        boolean addPointsToEdgeIfAbsent(ValueHandle handle, Value value) {
//            return pointsToEdges.putIfAbsent(handle, value) == null;
////            pointsToEdges.put(handle, value);
////            setNoEscape(value);
//        }

//        boolean fixPointsToNewIfNeeded(ValueHandle from, New to) {
//            if (fieldEdges.containsKey(to)) {
//                return pointsToEdges.putIfAbsent(from, to) == null;
//            }
//
//            return false;
//        }

        void addPointsToEdgeParam(ValueHandle from, ParameterValue to) {
            // Algorithm:
            //   Object created outside of the current method
            //   e.g. if from is a formal parameter (or reachable from formal parameter)
            //   Create a phantom node for the object.
            //   Insert a points-to edge from reference to phantom node
            //   Phantom nodes will be mapped back to actual nodes during interprocedural analysis.
            // Implementation:
            //   Treating ParameterValue as a phantom object.
            pointsToEdges.put(from, to);

            // TODO add deferred 
        }

        void addDeferredEdge(Value from, ValueHandle to) {
            deferredEdges.put(from, to);
        }

//        void addFieldEdge(New new_, ValueHandle field) {
//            fieldEdges.put(new_, field);
//        }

//        void addFieldEdges(Value value, List<Value> fields) {
//            fieldEdges.put(value, fields);
//            fields.forEach(field -> escapeStates.put(field, EscapeState.NO_ESCAPE));
//            setNoEscape(value);
//        }

//        public void addNewObject(Value value) {
//            setNoEscape(value);
//        }

    }
}
