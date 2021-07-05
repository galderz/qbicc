package org.qbicc.plugin.opt;

import org.qbicc.context.AttachmentKey;
import org.qbicc.context.CompilationContext;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.type.definition.element.ExecutableElement;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EscapeAnalysis {
    private static final AttachmentKey<EscapeAnalysis> KEY = new AttachmentKey<>();
    private final Map<ExecutableElement, ConnectionGraph> connectionGraphs = new ConcurrentHashMap<>();

    public void setGlobalEscape(ExecutableElement element, Value value) {
        final ConnectionGraph cg = connectionGraph(element);
        cg.escapeStates.put(value, EscapeState.GLOBAL_ESCAPE);
    }

    void setNoEscape(ExecutableElement element, Value value) {
        final ConnectionGraph cg = connectionGraph(element);
        cg.escapeStates.put(value, EscapeState.NO_ESCAPE);
    }

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

    void methodExit(ExecutableElement element) {
        // TODO: summarise method cg
    }

    boolean notEscapingMethod(Node node) {
        return escapeState(node)
            .filter(escapeState -> escapeState == EscapeState.NO_ESCAPE)
            .isPresent();
    }

    private ConnectionGraph connectionGraph(ExecutableElement element) {
        return connectionGraphs.computeIfAbsent(element, e -> new ConnectionGraph());
    }

    private Optional<EscapeState> escapeState(Node node) {
        return connectionGraphs.values().stream()
            .flatMap(cg -> cg.escapeStates.entrySet().stream())
            .filter(e -> e.getKey().equals(node))
            .map(Map.Entry::getValue)
            .findFirst();
    }

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

    enum EscapeState {
        GLOBAL_ESCAPE, ARG_ESCAPE, NO_ESCAPE;
    }

    private static final class ConnectionGraph {
        private final Map<ValueHandle, Value> pointsToEdges = new ConcurrentHashMap<>(); // solid (P) edges
        private final Map<Value, ValueHandle> deferredEdges = new ConcurrentHashMap<>(); // dashed (D) edges
        private final Map<Value, Set<ValueHandle>> fieldEdges = new ConcurrentHashMap<>(); // solid (F) edges
        private final Map<Node, EscapeState> escapeStates = new ConcurrentHashMap<>();
   }

}