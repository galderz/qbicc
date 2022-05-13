package org.qbicc.plugin.opt.ea;

import org.qbicc.context.ClassContext;
import org.qbicc.context.CompilationContext;
import org.qbicc.graph.Action;
import org.qbicc.graph.BasicBlockBuilder;
import org.qbicc.graph.BlockEntry;
import org.qbicc.graph.Call;
import org.qbicc.graph.ConstructorElementHandle;
import org.qbicc.graph.DelegatingBasicBlockBuilder;
import org.qbicc.graph.If;
import org.qbicc.graph.InstanceFieldOf;
import org.qbicc.graph.IsEq;
import org.qbicc.graph.Load;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.NodeVisitor;
import org.qbicc.graph.OrderedNode;
import org.qbicc.graph.ParameterValue;
import org.qbicc.graph.ReferenceHandle;
import org.qbicc.graph.Return;
import org.qbicc.graph.StaticField;
import org.qbicc.graph.StaticMethodElementHandle;
import org.qbicc.graph.Store;
import org.qbicc.graph.Terminator;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.graph.ValueReturn;
import org.qbicc.graph.literal.Literal;
import org.qbicc.type.ClassObjectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.lang.Boolean.TRUE;

// It's still a basic block builder because start method ParameterValue instances are required
public class EscapeAnalysisIntraMethodAnalysis extends DelegatingBasicBlockBuilder {
    private final EscapeAnalysisState escapeAnalysisState;
    private final ConnectionGraph connectionGraph;
    private final ClassContext bootstrapClassContext;

    public EscapeAnalysisIntraMethodAnalysis(final CompilationContext ctxt, final BasicBlockBuilder delegate) {
        super(delegate);
        this.connectionGraph = new ConnectionGraph(getCurrentElement().toString());
        this.escapeAnalysisState = EscapeAnalysisState.get(ctxt);
        this.bootstrapClassContext = ctxt.getBootstrapClassContext();
    }

    @Override
    public void startMethod(List<ParameterValue> arguments) {
        super.startMethod(arguments);
        escapeAnalysisState.trackMethod(getCurrentElement(), connectionGraph);
        connectionGraph.trackParameters(arguments);
    }

    @Override
    public void finish() {
        super.finish();
        // Incoming values for phi nodes can only be calculated upon finish.
        connectionGraph.resolveReturnedPhiValues();

        // Do escape analysis on method
        final AnalysisContext analysisContext = new AnalysisContext(connectionGraph, bootstrapClassContext);
        getFirstBlock().getTerminator().accept(new AnalysisVisitor(), analysisContext);

        final List<New> supported = analysisContext.supported.entrySet().stream()
            .filter(e -> e.getKey() instanceof New && e.getValue())
            .filter(e -> analysisContext.connectionGraph.getEscapeValue(e.getKey()).notGlobalEscape())
            .map(e -> (New) e.getKey())
            .toList();

        analysisContext.connectionGraph.validateNewNodes(supported);
    }


    static final class AnalysisVisitor implements NodeVisitor<AnalysisContext, Void, Void, Void, Void> {
        @Override
        public Void visit(AnalysisContext param, New node) {
            param.connectionGraph.trackNew(node, defaultEscapeValue(param, node.getClassObjectType()));
            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, ReferenceHandle ref) {
            final Value refValue = ref.getReferenceValue();
            if (refValue instanceof New new_) {
                // (1) p = new T();
                // Add points-to from `p` to new object
                param.connectionGraph.addPointsToEdge(ref, new_);
            }
            return visitSupported(param, ref);
        }

        @Override
        public Void visit(AnalysisContext param, ParameterValue node) {
            if (!param.visited.contains(node)) {
                // The phantom node servers as an anchor for the summary information,
                //   that will be generated when we finish analyzing the current method.
                final Phantom anchor = new Phantom("a" + node.getIndex());
                param.connectionGraph.setArgEscape(anchor);
                param.connectionGraph.addDeferredEdge(node, anchor);
            }

            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, Store store) {
            final ValueHandle handle = store.getValueHandle();
            final Value value = store.getValue();

            final boolean isNew = value instanceof New;
            if (handle instanceof InstanceFieldOf fieldOf && fieldOf.getValueHandle() instanceof ReferenceHandle ref) {
                visit(param, ref); // visit early to populate points-to if necessary

                if (isNew) {
                    if (isThisRef(ref)) {
                        // this.f = new T();
                        param.connectionGraph.setArgEscape(value);
                    } else {
                        // p.f = new T();
                        if (Objects.isNull(param.connectionGraph.getPointsToEdge(ref)) && ref.getReferenceValue() instanceof ParameterValue pv) {
                            // Object that `p` points to was created outside this method (e.g. `p` is a formal parameter)

                            // Create a phantom node that represents the object that the parameter value points to in a caller's context,
                            //   and whose field is accessed via the parameter value passed in.
                            Phantom phantom = new Phantom("?");

                            // Establish the link between the anchor and object in caller's context
                            final Node anchor = param.connectionGraph.getDeferredEdge(pv);
                            param.connectionGraph.addPointsToEdge(anchor, phantom);

                            // Set link from object in caller's context, via field, to the new value
                            param.connectionGraph.addPointsToEdge(phantom, ref);
                            param.connectionGraph.addPointsToEdge(ref, value);
                        }
                    }
                }
                

//                if (isNew && isThisRef(ref)) {
//                    // this.f = new T();
//                    param.connectionGraph.setArgEscape(value);
//                }
//
//                // (3.1) p.f = q
//                // (3.2) p.f = new T();
//                Node pointsTo = param.connectionGraph.getPointsToEdge(ref); // Look up points-to from `p`
//                if (pointsTo == null && ref.getReferenceValue() instanceof ParameterValue pv) {
//                    Phantom phantom = new Phantom("?");
//
//                    final Node anchor = param.connectionGraph.getDeferredEdge(pv);
//                    param.connectionGraph.addPointsToEdge(anchor, phantom);
//
//                    param.connectionGraph.addPointsToEdge(phantom, ref);
//
//                    if (isNew) {
//                        param.connectionGraph.addPointsToEdge(ref, value);
//                    }
//                }


//                    // Object that `p` points to was created outside this method (e.g. `p` is a formal parameter)
//                    // Create phantom node and insert points-to edge from `p` to phantom node
//                    pointsTo = new Phantom("?");
//                    param.connectionGraph.addPointsToEdge(ref, pointsTo);
//
//                    // (3.2) p.f = new T(); // where `p` is a parameter
//                    if (isNew) {
//                        param.connectionGraph.addPointsToEdge(pointsTo, value);
//                    }

//                    // TODO Is this always true?
//                    if (ref.getReferenceValue() instanceof ParameterValue pv) {
//                        // Early step for intra-method analysis, establishing the link between parameter and phantom
//                        param.connectionGraph.addDeferredEdge(pv, pointsTo);
//                        param.connectionGraph.setArgEscape(pointsTo);
//                    }
//                }

//                // Add field edge from new (or phantom) instance to field
//                param.connectionGraph.addFieldEdge(pointsTo, fieldOf);
//                // Add deferred edge from field to value assigned
//                param.connectionGraph.addDeferredEdge(fieldOf, value);
            } else if (handle instanceof StaticField && isNew) {
                param.connectionGraph.addPointsToEdge(handle, value);
                param.connectionGraph.setGlobalEscape(value);
            }

            return visitSupported(param, store);
        }

        private boolean isThisRef(ReferenceHandle ref) {
            return ref.getReferenceValue() instanceof ParameterValue param
                && "this".equals(param.getLabel());
        }

        @Override
        public Void visit(AnalysisContext param, Call node) {
            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, ConstructorElementHandle node) {
            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, InstanceFieldOf node) {
            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, ValueReturn node) {
            if (node.getReturnValue() instanceof New new_) {
                param.connectionGraph.setArgEscape(new_);
            }

            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, Return node) {
            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, Load node) {
            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, StaticField node) {
            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, StaticMethodElementHandle node) {
            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, If node) {
            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, IsEq node) {
            return visitSupported(param, node);
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

        private Void visitSupported(AnalysisContext param, ValueHandle node) {
            param.addType(node.getClass());
            visitUnknown(param, node);
            return null;
        }

        private Void visitSupported(AnalysisContext param, Value node) {
            param.addType(node.getClass());
            visitUnknown(param, node);
            return null;
        }

        private Void visitSupported(AnalysisContext param, Action node) {
            param.addType(node.getClass());
            visitUnknown(param, node);
            return null;
        }

        private Void visitSupported(AnalysisContext param, Terminator node) {
            param.addType(node.getClass());
            visitUnknown(param, node);
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
            if (node instanceof Literal) {
                return true;
            }

            final Boolean prev = param.supported.get(node);
            if (prev == null) {
                boolean supported = param.types.contains(node.getClass());
                param.setSupported(node, supported);
                return supported;
            }
            return prev.booleanValue();
        }
    }

    static final class AnalysisContext {
        final Set<Node> visited = new HashSet<>();
        final Map<Node, Boolean> supported = new HashMap<>();
        final Set<Class<?>> types = new HashSet<>(); // supported types
        final ConnectionGraph connectionGraph;
        final ClassContext bootstrapClassContext;

        AnalysisContext(ConnectionGraph connectionGraph, ClassContext bootstrapClassContext) {
            this.connectionGraph = connectionGraph;
            this.bootstrapClassContext = bootstrapClassContext;
            this.types.add(BlockEntry.class);
            this.types.add(ParameterValue.class);
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
            return type.isSubtypeOf(bootstrapClassContext.findDefinedType(name).load().getObjectType());
        }
    }
}
