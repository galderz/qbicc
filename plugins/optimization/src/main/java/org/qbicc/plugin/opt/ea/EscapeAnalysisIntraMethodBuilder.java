package org.qbicc.plugin.opt.ea;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import static java.lang.Boolean.TRUE;

// TODO Rename to EscapeAnalysisIntraMethodAnalysis
// It's still a basic block builder because start method ParameterValue instances are required
public final class EscapeAnalysisIntraMethodBuilder extends DelegatingBasicBlockBuilder  {
    private final EscapeAnalysisState escapeAnalysisState;
    private final ConnectionGraph connectionGraph;
    private final ClassContext bootstrapClassContext;

    public EscapeAnalysisIntraMethodBuilder(final CompilationContext ctxt, final BasicBlockBuilder delegate) {
        super(delegate);
        this.connectionGraph = new ConnectionGraph(getCurrentElement().toString());
        this.escapeAnalysisState = EscapeAnalysisState.get(ctxt);
        this.bootstrapClassContext = ctxt.getBootstrapClassContext();
    }

//    @Override
//    public ValueHandle instanceFieldOf(ValueHandle handle, FieldElement field) {
//        final InstanceFieldOf result = (InstanceFieldOf) super.instanceFieldOf(handle, field);
//
//        // T a = new T(...);
//        // To get represent the GC from 'a' to 'new T(...)',
//        // we hijack future 'a' references to fix the pointer.
//        // When 'a.x' is accessed, we fix the pointer from 'a' to 'new T(...)'.
//        handleInstanceFieldOf(result, handle, handle);
//
//        return supports(result);
//    }

//    @Override
//    public Node store(ValueHandle handle, Value value, WriteAccessMode mode) {
//        final Node result = super.store(handle, value, mode);
//
//        if (handle instanceof StaticField) {
//            // static T a = ...
//            if (value instanceof NotNull nn) {
//                connectionGraph.trackStoreStaticField(handle, nn.getInput());
//            } else {
//                connectionGraph.trackStoreStaticField(handle, value);
//            }
//        } else if (handle instanceof InstanceFieldOf fieldOf && value instanceof New) {
//            if (isThisHandle(fieldOf)) {
//                // this.f = new T();
//                connectionGraph.trackStoreThisField(value);
//            } else {
//                // p.f = new T(); // where p is a parameter
//                connectionGraph.fixEdgesNew(handle, (New) value);
//            }
//        } else if (handle instanceof LocalVariable && value instanceof New) {
//            connectionGraph.trackLocalNew((LocalVariable) handle, (New) value);
//        }
//
//        return supports(result);
//    }

//    private boolean isThisHandle(InstanceFieldOf fieldOf) {
//        return fieldOf.getValueHandle() instanceof ReferenceHandle ref
//            && ref.getReferenceValue() instanceof ParameterValue param
//            && "this".equals(param.getLabel());
//    }

//    @Override
//    public Value call(ValueHandle target, List<Value> arguments) {
//        final Value result = super.call(target, arguments);
//
//        if (target instanceof Executable) {
//            escapeAnalysisState.trackCall(getCurrentElement(), (Call) result);
//        }
//
//        return supports(result);
//    }

//    @Override
//    public ValueHandle constructorOf(Value instance, ConstructorElement constructor, MethodDescriptor callSiteDescriptor, FunctionType callSiteType) {
//        return supports(super.constructorOf(instance, constructor, callSiteDescriptor, callSiteType));
//    }

    @Override
    public void startMethod(List<ParameterValue> arguments) {
        super.startMethod(arguments);
        escapeAnalysisState.trackMethod(getCurrentElement(), connectionGraph);
        connectionGraph.trackParameters(arguments);
    }

//    @Override
//    public BasicBlock return_(Value value) {
//        final BasicBlock result = super.return_(value);
//
//        // Skip primitive values truncated, they are not objects
//        if (!(value instanceof Truncate)) {
//            connectionGraph.trackReturn(value);
//        }
//
//        return supports(result.getTerminator(), result);
//    }

//    @Override
//    public BasicBlock throw_(Value value) {
//        final BasicBlock result = super.throw_(value);
//
//        if (value instanceof New) {
//            connectionGraph.trackThrowNew((New) value);
//        }
//
//        return result;
//    }

//    @Override
//    public Value checkcast(Value value, Value toType, Value toDimensions, CheckCast.CastType kind, ObjectType expectedType) {
//        final CastValue result = (CastValue) super.checkcast(value, toType, toDimensions, kind, expectedType);
//        connectionGraph.trackCast(result);
//        return supports(result);
//    }

//    @Override
//    public Value bitCast(Value value, WordType toType) {
//        final CastValue result = (CastValue) super.bitCast(value, toType);
//        connectionGraph.trackCast(result);
//        return supports(result);
//    }

//    @Override
//    public ValueHandle referenceHandle(Value reference) {
//        return supports(super.referenceHandle(reference));
//    }

//    @Override
//    public BasicBlock if_(Value condition, BlockLabel trueTarget, BlockLabel falseTarget) {
//        final BasicBlock result = super.if_(condition, trueTarget, falseTarget);
//        return supports(result.getTerminator(), result);
//    }
//
//    @Override
//    public Value isEq(Value v1, Value v2) {
//        return supports(super.isEq(v1, v2));
//    }
//
//    @Override
//    public Value sub(Value v1, Value v2) {
//        return supports(super.sub(v1, v2));
//    }
//
//    @Override
//    public Value add(Value v1, Value v2) {
//        return supports(super.add(v1, v2));
//    }
//
//    @Override
//    public Value truncate(Value value, WordType toType) {
//        return supports(super.truncate(value, toType));
//    }
//
//    @Override
//    public Value extend(Value value, WordType toType) {
//        return supports(super.extend(value, toType));
//    }
//
//    @Override
//    public ValueHandle virtualMethodOf(Value instance, MethodElement method, MethodDescriptor callSiteDescriptor, FunctionType callSiteType) {
//        return supports(super.virtualMethodOf(instance, method, callSiteDescriptor, callSiteType));
//    }
//
//    @Override
//    public BasicBlock goto_(BlockLabel resumeLabel) {
//        final BasicBlock result = super.goto_(resumeLabel);
//        return supports(result.getTerminator(), result);
//    }

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

//    private <T extends Node> T supports(T value) {
//        analysisContext.addType(value.getClass());
//        return value;
//    }
//
//    private BasicBlock supports(Terminator terminator, BasicBlock block) {
//        analysisContext.addToQueue(terminator);
//        analysisContext.addType(terminator.getClass());
//        return block;
//    }

//    private void handleInstanceFieldOf(InstanceFieldOf result, ValueHandle handle, Node target) {
//        if (target instanceof New) {
//            connectionGraph.fixEdgesField((New) target, handle, result);
//        } else if (target instanceof ParameterValue) {
//            connectionGraph.fixEdgesParameterValue((ParameterValue) target, result);
//        } else if (target instanceof Store) {
//            final Value value = ((Store) target).getValue();
//            if (value instanceof New) {
//                handleInstanceFieldOf(result, handle, value);
//            } else {
//                handleInstanceFieldOf(result, handle, target.getValueHandle());
//            }
//        } else if (target instanceof InstanceFieldOf) {
//            handleInstanceFieldOf(result, handle, target.getValueHandle());
//        } else if (target instanceof ReferenceHandle) {
//            handleInstanceFieldOf(result, handle, ((ReferenceHandle) target).getReferenceValue());
//        } else if (target instanceof OrderedNode) {
//            handleInstanceFieldOf(result, handle, ((OrderedNode) target).getDependency());
//        }
//    }

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
            return type.isSubtypeOf(bootstrapClassContext.findDefinedType(name).load().getType());
        }
    }

    static final class AnalysisVisitor implements NodeVisitor<AnalysisContext, Void, Void, Void, Void> {
        @Override
        public Void visit(AnalysisContext param, New node) {
            param.connectionGraph.trackNew(node, defaultEscapeValue(param, node.getClassObjectType()));
            return visitSupported(param, node);
        }

        @Override
        public Void visit(AnalysisContext param, ReferenceHandle ref) {
            if (ref.getReferenceValue() instanceof New new_) {
                // (1) p = new T();
                // Add points-to from `p` to new object
                param.connectionGraph.addPointsToEdge(ref, new_);
            }
            return visitSupported(param, ref);
        }

        @Override
        public Void visit(AnalysisContext param, Store store) {
            final ValueHandle handle = store.getValueHandle();
            final Value value = store.getValue();

            if (handle instanceof InstanceFieldOf fieldOf && fieldOf.getValueHandle() instanceof ReferenceHandle ref) {
                visit(param, ref); // visit early to populate points-to if necessary

                if (value instanceof New new_ && isThisRef(ref)) {
                    // this.f = new T();
                    param.connectionGraph.setArgEscape(new_);
                }
                
                // (3.1) p.f = q
                // (3.2) p.f = new T();
                Node pointsTo = param.connectionGraph.getPointsToEdge(ref); // Look up points-to from `p`
                if (pointsTo == null) {
                    // Object that `p` points to was created outside this method (e.g. `p` is a formal parameter)
                    // Create phantom node and insert points-to edge from `p` to phantom node
                    pointsTo = new Phantom();
                    param.connectionGraph.addPointsToEdge(ref, pointsTo);
                }
                // Add field edge from new (or phantom) instance to field
                param.connectionGraph.addFieldEdge(pointsTo, fieldOf);
                // Add deferred edge from field to value assigned
                param.connectionGraph.addDeferredEdge(fieldOf, value);
            } else if (handle instanceof StaticField && value instanceof New) {
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
}
