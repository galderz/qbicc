package org.qbicc.plugin.opt;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.qbicc.context.AttachmentKey;
import org.qbicc.context.CompilationContext;
import org.qbicc.graph.Node;
import org.qbicc.type.definition.element.Element;

final class EscapeAnalysis {
    private static final AttachmentKey<EscapeAnalysis> KEY = new AttachmentKey<>();

    private final Map<Node, EscapeState> escapeStates = new HashMap<>(128);

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

    void noEscape(Node node) {
        // System.out.println("No escape: " + element + ";" + (Objects.isNull(element) ? null : element.getClass()));
        escapeStates.putIfAbsent(node, EscapeState.NO_ESCAPE);
    }

    void argEscape(Node node) {
        // System.out.println("Arg escape: " + element + ";" + (Objects.isNull(element) ? null : element.getClass()));
        escapeStates.put(node, EscapeState.ARG_ESCAPE);
    }

    void copy(Node original, Node copy) {
        final EscapeState previous = escapeStates.remove(original);
        if (previous != null) {
            escapeStates.put(copy, previous);
        }
    }

    boolean notEscapingThread(Node node) {
        final EscapeState escapeState = escapeStates.get(node);
        return escapeState == EscapeState.NO_ESCAPE || escapeState == EscapeState.ARG_ESCAPE;
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
}
