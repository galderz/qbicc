package org.qbicc.plugin.dot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.qbicc.graph.Action;
import org.qbicc.graph.BasicBlock;
import org.qbicc.graph.Call;
import org.qbicc.graph.CallNoReturn;
import org.qbicc.graph.CastValue;
import org.qbicc.graph.CheckCast;
import org.qbicc.graph.ConstructorElementHandle;
import org.qbicc.graph.CurrentThread;
import org.qbicc.graph.Executable;
import org.qbicc.graph.Extend;
import org.qbicc.graph.GetAndSet;
import org.qbicc.graph.If;
import org.qbicc.graph.InstanceFieldOf;
import org.qbicc.graph.InstanceOf;
import org.qbicc.graph.InterfaceMethodElementHandle;
import org.qbicc.graph.Invoke;
import org.qbicc.graph.IsEq;
import org.qbicc.graph.IsNe;
import org.qbicc.graph.Load;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.NodeVisitor;
import org.qbicc.graph.NotNull;
import org.qbicc.graph.ParameterValue;
import org.qbicc.graph.ReferenceHandle;
import org.qbicc.graph.Return;
import org.qbicc.graph.Select;
import org.qbicc.graph.StaticField;
import org.qbicc.graph.StaticMethodElementHandle;
import org.qbicc.graph.Store;
import org.qbicc.graph.Terminator;
import org.qbicc.graph.Throw;
import org.qbicc.graph.Truncate;
import org.qbicc.graph.Unschedulable;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.graph.ValueReturn;
import org.qbicc.graph.VirtualMethodElementHandle;
import org.qbicc.graph.literal.IntegerLiteral;
import org.qbicc.graph.literal.NullLiteral;
import org.qbicc.graph.literal.StringLiteral;
import org.qbicc.graph.literal.TypeLiteral;
import org.qbicc.graph.schedule.Schedule;
import org.qbicc.type.ClassObjectType;
import org.qbicc.type.ValueType;
import org.qbicc.type.generic.BaseTypeSignature;

final class Disassembler {
    private final Schedule schedule;
    private final DisassembleVisitor visitor = new DisassembleVisitor();

    // TODO do we need the list of blocks if we already have the Node -> NodeInfo mapping?
    //      the nodeInfo could be post-processed making sure the NodeInfo are sorted by id and you'd get the list back?
    // private final List<Block> blocks = new ArrayList<>();
    private final Map<BasicBlock, BlockInfo> blocks = new HashMap<>();

    private final Map<Node, NodeInfo> nodeInfo = new HashMap<>();
    private final Set<BasicBlock> blockQueued = ConcurrentHashMap.newKeySet();
    private final Queue<BasicBlock> blockQueue = new ArrayDeque<>();
    private final List<BlockEdge> blockEdges = new ArrayList<>(); // stores pair of Terminator, BlockEntry
    private BasicBlock currentBlock;
    private int currentBlockId;
    private int currentNodeId;

    Disassembler(BasicBlock entryBlock) {
        schedule = Schedule.forMethod(entryBlock);
        blockQueue.add(entryBlock);
    }

    void run() {
        do {
            disassemble(blockQueue.poll());
        } while (!blockQueue.isEmpty());

        // TODO process phi queue
    }

    void disassemble(BasicBlock block) {
        final List<Node> nodes = schedule.getNodesForBlock(block);

        currentNodeId = 0;
        currentBlock = block;
        blocks.put(block, new BlockInfo(currentBlockId, new ArrayList<>()));
        
        for (Node node : nodes) {
            if (!(node instanceof Terminator)) {
                disassemble(node);
            }
        }
        disassemble(block.getTerminator());
        incrementBlockId();
    }

    Map<BasicBlock, BlockInfo> getBlocks() {
        return blocks;
    }

    List<BlockEdge> getBlockEdges() {
        return blockEdges;
    }

    private void queueBlock(BasicBlock from, BasicBlock to, String label, DotAttributes style) {
        blockEdges.add(new BlockEdge(from, to, label, style));
        if (blockQueued.add(to)) {
            blockQueue.add(to);
        }
    }

    private void addLine(String line) {
        blocks.get(currentBlock).lines.add(line);
    }

    private void incrementBlockId() {
        currentBlockId++;
    }

    private String nextId() {
        final String nextId = "%b" + currentBlockId + "." + currentNodeId;
        // nodeIds.put(node, nextId);
        incrementId();
        return nextId;
    }

    private void incrementId() {
        currentNodeId++;
    }

    private NodeInfo disassemble(Node node) {
        NodeInfo nodeInfo = this.nodeInfo.get(node);
        if (Objects.isNull(nodeInfo)) {
            if (node instanceof Value value) {
                value.accept(visitor, this);
            } else if (node instanceof Action action) {
                action.accept(visitor, this);
            } else if (node instanceof ValueHandle valueHandle) {
                valueHandle.accept(visitor, this);
            } else {
                assert node instanceof Terminator;
                ((Terminator) node).accept(visitor, this);
            }

            return this.nodeInfo.get(node);
        }

        return nodeInfo;
    }

    private static String unwrapTypeName(ValueType type) {
        if (type instanceof ClassObjectType classObjectType) {
            return classObjectType.getDefinition().getInternalName();
        }

        return type.toString();
    }

    public int findBlockId(BasicBlock block) {
        return blocks.get(block).id;
    }

    // TODO remove -> switch back to List<List<String>
    record BlockInfo(int id, List<String> lines) {}

    record BlockEdge(BasicBlock from, BasicBlock to, String label, DotAttributes edgeType) {}

    record NodeInfo(String id, String description) {}

    // TODO switch to Void, Void, Void, Void
    private final class DisassembleVisitor implements NodeVisitor<Disassembler, String, String, String, String> {

        // TODO add visitUnknown once all nodes are presumed to be covered, for catching missing ones

        @Override
        public String visit(Disassembler param, New node) {
            final String id = param.nextId();
            final String description = "new " + show(node.getTypeId());
            param.addLine(id + " = " + description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, CallNoReturn node) {
            final String id = param.nextId();
            final String description = showWithArguments("call-no-return", node.getValueHandle(), node.getArguments());
            param.addLine(description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, Call node) {
            final String id = param.nextId();
            final String description = showWithArguments("call", node.getValueHandle(), node.getArguments());
            if (node.getValueHandle() instanceof Executable exec
                && !exec.getExecutable().getSignature().getReturnTypeSignature().equals(BaseTypeSignature.V)) {
                param.addLine(id + " = " + description);
            } else {
                param.addLine(description);
            }
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, Store node) {
            final String id = param.nextId();
            final String description = String.format(
                "store %s ← %s"
                , showDescription(node.getValueHandle())
                , show(node.getValue())
            );
            param.addLine(description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, Load node) {
            final String id = param.nextId();
            String description = "load " + showDescription(node.getValueHandle());
            param.addLine(id + " = " + description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, Select node) {
            final String id = param.nextId();
            final String description = String.format(
                "select %s ? %s : %s"
                , showDescription(node.getCondition())
                , showDescription(node.getTrueValue())
                , showDescription(node.getFalseValue())
            );
            param.addLine(id + " = " + description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, GetAndSet node) {
            final String id = param.nextId();
            final String description = String.format(
                "get-and-set %s ← %s"
                , show(node.getValueHandle())
                , show(node.getUpdateValue())
            );
            param.addLine(id + " = " + description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, ValueReturn node) {
            final String id = param.nextId();
            final String description = "return " + show(node.getReturnValue());
            param.addLine(description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, Return node) {
            final String id = param.nextId();
            final String description = "return";
            param.addLine(description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, If node) {
            final String id = param.nextId();
            final String description = "if " + showDescription(node.getCondition());
            param.addLine(description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            param.queueBlock(currentBlock, node.getTrueBranch(), "true", DotNodeVisitor.EdgeType.COND_TRUE_FLOW);
            param.queueBlock(currentBlock, node.getFalseBranch(), "false", DotNodeVisitor.EdgeType.COND_FALSE_FLOW);
            return id;
        }

        @Override
        public String visit(Disassembler param, Invoke node) {
            final String id = param.nextId();
            final String description = showWithArguments("invoke", node.getValueHandle(), node.getArguments());
            param.addLine(id + " = " + description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            param.queueBlock(currentBlock, node.getCatchBlock(), "catch", DotNodeVisitor.EdgeType.CONTROL_FLOW);
            param.queueBlock(currentBlock, node.getResumeTarget(), "resume", DotNodeVisitor.EdgeType.CONTROL_FLOW);
            return id;
        }

        @Override
        public String visit(Disassembler param, Throw node) {
            final String id = param.nextId();
            final String description = "throw " + show(node.getThrownValue());
            param.addLine(description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, IsNe node) {
            final String id = param.nextId();
            final String description = String.format(
                "%s != %s"
                , show(node.getLeftInput())
                , show(node.getRightInput())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, IsEq node) {
            final String id = param.nextId();
            final String description = String.format(
                "%s == %s"
                , show(node.getLeftInput())
                , show(node.getRightInput())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, InstanceOf node) {
            final String id = param.nextId();
            final String description = String.format(
                "%s instanceof %s"
                , show(node.getInstance())
                , unwrapTypeName(node.getCheckType())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, StaticField node) {
            final String id = param.nextId();
            final String description = node.getVariableElement().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, ConstructorElementHandle node) {
            final String id = param.nextId();
            final String description = showId(node.getInstance()) + " constructor";
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, InterfaceMethodElementHandle node) {
            final String id = param.nextId();
            final String description = node.getExecutable().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, StaticMethodElementHandle node) {
            final String id = param.nextId();
            final String description = node.getExecutable().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, VirtualMethodElementHandle node) {
            final String id = param.nextId();
            final String description = node.getExecutable().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, CheckCast node) {
            final String id = param.nextId();
            final String description = String.format(
                "(%s) %s"
                , show(node.getToType())
                , show(node.getInput())
            );
            param.addLine(id + " = " + description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, InstanceFieldOf node) {
            final String id = param.nextId();
            String description = node.getValueHandle() instanceof ReferenceHandle ref
                ? show(ref.getReferenceValue()) + " " + node.getVariableElement().getName()
                : "?";
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, ParameterValue node) {
            final String id = param.nextId();
            final String description = node.getLabel() + node.getIndex();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, NotNull node) {
            final String id = param.nextId();
            final String description = "not-null " + show(node.getInput());
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, TypeLiteral node) {
            final String id = param.nextId();
            final String description = unwrapTypeName(node.getType().getUpperBound());
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, IntegerLiteral node) {
            final String id = param.nextId();
            final String description = String.valueOf(node.longValue());
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, NullLiteral node) {
            final String id = param.nextId();
            final String description = "null";
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, StringLiteral node) {
            final String id = param.nextId();
            final String description = '"' + node.getValue() + '"';
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, CurrentThread node) {
            final String id = param.nextId();
            final String description = "current-thread";
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, Truncate node) {
            final String id = param.nextId();
            final String description = String.format(
                "trunc→%s %s"
                , node.getType()
                , show(node.getInput())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, Extend node) {
            final String id = param.nextId();
            final String description = String.format(
                "extend→%s %s"
                , node.getType()
                , show(node.getInput())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        private String show(Node node) {
            if (node instanceof NotNull) {
                return showDescription(node);
            }

            if (node instanceof CastValue) {
                return showDescription(node);
            }

            if (node instanceof Unschedulable) {
                return showDescription(node);
            }

            return showId(node);
        }

        private String showId(Node node) {
            if (node instanceof Invoke.ReturnValue invRet) {
                return showId(invRet.getInvoke());
            }

            final NodeInfo nodeInfo = disassemble(node);
            if (Objects.nonNull(nodeInfo)) {
                return nodeInfo.id;
            }

            // TODO temporary measure until all situations covered
            return "(? " + node.getClass() + ")";
        }

        private String showDescription(Node node) {
            final NodeInfo nodeInfo = disassemble(node);
            if (Objects.nonNull(nodeInfo)) {
                return nodeInfo.description;
            }

            // TODO temporary measure until all situations covered
            return "(? " + node.getClass() + ")";
        }

        private String showWithArguments(String prefix, ValueHandle handle, List<Value> arguments) {
            String args = arguments.stream()
                .map(this::show)
                .collect(Collectors.joining(" "));

            return String.format(
                "%s %s %s"
                , prefix
                , showDescription(handle)
                , args
            );
        }
    }
}
