package org.qbicc.plugin.opt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.qbicc.context.AttachmentKey;
import org.qbicc.context.CompilationContext;
import org.qbicc.graph.Node;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.type.definition.element.ExecutableElement;

final class EscapeAnalysis {
    private static final AttachmentKey<EscapeAnalysis> KEY = new AttachmentKey<>();

    private final Map<ExecutableElement, ConnectionGraph> connectionGraphs = new HashMap<>();

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

    public void newObject(Value value, List<Value> arguments, ExecutableElement element) {
        final ConnectionGraph cg = connectionGraph(element);
        cg.addFieldEdges(value, arguments);
    }

    public void fixPointsToIfNeeded(Value value, ValueHandle handle, ExecutableElement element) {
        final ConnectionGraph cg = connectionGraph(element);
        cg.fixPointsToIfNeeded(value, handle);
    }

    public void staticStore(Value value, ExecutableElement element) {
        final ConnectionGraph cg = connectionGraph(element);
        cg.setGlobalEscape(value);
    }

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
        private final Map<ValueHandle, Value> pointsToEdges = new HashMap<>(); // solid (P) edges
        private final Map<ValueHandle, ValueHandle> deferredEdges = new HashMap<>(); // dashed (D) edges
        private final Map<Value, List<Value>> fieldEdges = new HashMap<>(); // solid (F) edges

        private final Map<Node, EscapeState> escapeStates = new HashMap<>();

        void addPointsToEdge(ValueHandle handle, Value value) {
            pointsToEdges.put(handle, value);
            setNoEscape(value);
        }

        void fixPointsToIfNeeded(Value value, ValueHandle handle) {
            if (fieldEdges.containsKey(value) && !pointsToEdges.containsKey(handle)) {
                log("Fix points-to edge from %s to %s", handle, value);
                pointsToEdges.put(handle, value);
            }
        }

//        void addDeferredEdge(ValueHandle handle, Value value) {
//            deferredEdges.put(handle, value);
//            escapeStates.put(value, EscapeState.NO_ESCAPE);
//        }

        void addFieldEdges(Value value, List<Value> fields) {
            fieldEdges.put(value, fields);
            fields.forEach(field -> escapeStates.put(field, EscapeState.NO_ESCAPE));
            log("Add field edges from %s to %s", value, fields);
            setNoEscape(value);
        }

        private void setNoEscape(Value value) {
            log("Set %s to NO_ESCAPE", value);
            escapeStates.put(value, EscapeState.NO_ESCAPE);
        }

        private void setGlobalEscape(Value value) {
            log("Set %s to GLOBAL_ESCAPE", value);
            escapeStates.replace(value, EscapeState.GLOBAL_ESCAPE);
        }

//        void setArgEscape(Node node) {
//            // escapeStates.put(node, EscapeState.ARG_ESCAPE);
//        }
    }

    private static void log(String format, Object... args) {
        System.out.printf(
            "(%s) [ea] %s%n"
            , Thread.currentThread().getName()
            , String.format(format, args)
        );
    }
}
