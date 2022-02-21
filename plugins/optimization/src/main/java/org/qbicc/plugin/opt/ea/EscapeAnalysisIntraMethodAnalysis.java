package org.qbicc.plugin.opt.ea;

import org.qbicc.context.ClassContext;
import org.qbicc.context.CompilationContext;
import org.qbicc.graph.Action;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.NodeVisitor;
import org.qbicc.graph.OrderedNode;
import org.qbicc.graph.Terminator;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.type.ClassObjectType;
import org.qbicc.type.definition.MethodBody;
import org.qbicc.type.definition.classfile.ClassFile;
import org.qbicc.type.definition.element.BasicElement;
import org.qbicc.type.definition.element.ElementVisitor;
import org.qbicc.type.definition.element.ExecutableElement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.Boolean.TRUE;

public class EscapeAnalysisIntraMethodAnalysis implements ElementVisitor<CompilationContext, Void> {

    @Override
    public Void visitUnknown(final CompilationContext param, final BasicElement basicElement) {
        if (basicElement instanceof ExecutableElement) {
            ExecutableElement element = (ExecutableElement) basicElement;
            if (element.hasMethodBody()) {
                MethodBody methodBody = element.getMethodBody();
                process(element, methodBody, param.getBootstrapClassContext());
            }
        }
        return null;
    }

    private void process(final ExecutableElement element, MethodBody methodBody, ClassContext bootstrapClassContext) {
        if (element.hasAllModifiersOf(ClassFile.ACC_ABSTRACT)) return;
        methodBody.getEntryBlock().getTerminator().accept(new AnalysisVisitor(), new AnalysisContext(element.toString(), bootstrapClassContext));
    }

    private static final class AnalysisContext {
        final Set<Node> visited = new HashSet<>();
        final Map<Node, Boolean> supported = new HashMap<>();
        final Set<Class<?>> types = new HashSet<>(); // supported type
        final ConnectionGraph connectionGraph;
        final ClassContext bootstrapClassContext;

        AnalysisContext(String name, ClassContext bootstrapClassContext) {
            this.connectionGraph = new ConnectionGraph(name);
            this.bootstrapClassContext = bootstrapClassContext;
        }

        void addType(Class<?> type) {
            types.add(type);
        }

        void setSupported(Node node, boolean value) {
            this.supported.put(node, value);
        }

        boolean switchToUnsupported(Node node) {
            return this.supported.replace(node, true, false);
        }

        boolean addUnsupported(Node node) {
            return this.supported.putIfAbsent(node, false) == null;
        }

        private boolean isSubtypeOfClass(String name, ClassObjectType type) {
            return type.isSubtypeOf(bootstrapClassContext.findDefinedType(name).load().getType());
        }
    }

    private static final class AnalysisVisitor implements NodeVisitor<AnalysisContext, Void, Void, Void, Void> {
        @Override
        public Void visit(AnalysisContext param, New node) {
            param.connectionGraph.trackNew(node, defaultEscapeValue(param, node.getClassObjectType()));
            return supports(param, node);
        }

        private EscapeValue defaultEscapeValue(AnalysisContext param, ClassObjectType type) {
            if (param.isSubtypeOfClass("java/lang/Thread", type) ||
                param.isSubtypeOfClass("java/lang/ThreadGroup", type)) {
                return EscapeValue.GLOBAL_ESCAPE;
            }

            return EscapeValue.NO_ESCAPE;
        }

        @Override
        public Void visitUnknown(AnalysisContext param, Action node) {
            visitUnknown(param, (Node) node);
            return null;
        }

        @Override
        public Void visitUnknown(AnalysisContext param, Terminator node) {
            if (visitUnknown(param, (Node) node)) {
                // process reachable successors
                int cnt = node.getSuccessorCount();
                for (int i = 0; i < cnt; i ++) {
                    node.getSuccessor(i).getTerminator().accept(this, param);
                }
            }
            return null;
        }

        @Override
        public Void visitUnknown(AnalysisContext param, ValueHandle node) {
            visitUnknown(param, (Node) node);
            return null;
        }

        @Override
        public Void visitUnknown(AnalysisContext param, Value node) {
            visitUnknown(param, (Node) node);
            return null;
        }

        boolean visitUnknown(AnalysisContext param, Node node) {
            if (param.visited.add(node)) {
                boolean isNodeSupported = isSupported(param, node);

                if (node.hasValueHandleDependency()) {
                    final ValueHandle dependency = node.getValueHandle();
                    checkSupport(isNodeSupported, dependency, param);
                    dependency.accept(this, param);
                }

                int cnt = node.getValueDependencyCount();
                for (int i = 0; i < cnt; i ++) {
                    final Value dependency = node.getValueDependency(i);
                    checkSupport(isNodeSupported, dependency, param);
                    dependency.accept(this, param);
                }

                if (node instanceof OrderedNode) {
                    Node dependency = ((OrderedNode) node).getDependency();
                    checkSupport(isNodeSupported, dependency, param);
                    if (dependency instanceof Action) {
                        ((Action) dependency).accept(this, param);
                    } else if (dependency instanceof Value) {
                        ((Value) dependency).accept(this, param);
                    } else if (dependency instanceof Terminator) {
                        ((Terminator) dependency).accept(this, param);
                    } else if (dependency instanceof ValueHandle) {
                        ((ValueHandle) dependency).accept(this, param);
                    }
                }

                return true;
            }

            return false;
        }

        private Void supports(AnalysisContext param, Node value) {
            param.addType(value.getClass());
            return null;
        }

        private void checkSupport(boolean isSupported, Node node, AnalysisContext param) {
            if (!isSupported) {
                if (TRUE.equals(param.supported.get(node))) {
                    param.switchToUnsupported(node);
                    // Remove a node from visited if it switched from supported to unsupported.
                    // This way the unsupported new status can trickle back through its dependencies.
                    param.visited.remove(node);
                } else {
                    param.addUnsupported(node);
                }
            }
        }

        /**
         * Checks if a node is supported or not.
         * A node's support status might have been set by a dependency (value or control), if the dependency itself was unsupported.
         * In this case, irrespective of the node type, the node will remain unsupported.
         * If a node's support status is unknown, a node will be supported if its type is amongst supported types.
         * Otherwise, it will return false.
         */
        private boolean isSupported(AnalysisContext param, Node node) {
            final Boolean prev = param.supported.get(node);
            if (prev == null) {
                boolean supported = param.types.contains(node.getClass());
                param.setSupported(node, supported);
                return supported;
            }
            return prev.booleanValue();
        }
    }
}
