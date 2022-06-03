package org.qbicc.plugin.dot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.qbicc.graph.Action;
import org.qbicc.graph.BasicBlock;
import org.qbicc.graph.Call;
import org.qbicc.graph.ConstructorElementHandle;
import org.qbicc.graph.InstanceFieldOf;
import org.qbicc.graph.Load;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.NodeVisitor;
import org.qbicc.graph.ParameterValue;
import org.qbicc.graph.ReferenceHandle;
import org.qbicc.graph.Store;
import org.qbicc.graph.Terminator;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.graph.ValueReturn;
import org.qbicc.graph.schedule.Schedule;
import org.qbicc.type.ClassObjectType;
import org.qbicc.type.PhysicalObjectType;

final class Disassembler {
    private final Schedule schedule;
    private final DisassembleVisitor visitor = new DisassembleVisitor();

    // TODO do we need the list of blocks if we already have the Node -> NodeInfo mapping?
    //      the nodeInfo could be post-processed making sure the NodeInfo are sorted by id and you'd get the list back?
    private final List<Block> blocks = new ArrayList<>();

    private final Map<Node, NodeInfo> nodeInfo = new HashMap<>();
    private int blockId;
    private int id;

    Disassembler(BasicBlock entryBlock) {
        schedule = Schedule.forMethod(entryBlock);
    }

    void addBlock(BasicBlock block) {
        final List<Node> nodes = schedule.getNodesForBlock(block);

        id = 0;
        blocks.add(new Block(blockId, new ArrayList<>()));
        
        for (Node node : nodes) {
            if (!(node instanceof Terminator)) {
                disassemble(node);
            }
        }
        disassemble(block.getTerminator());
        incrementBlockId();
    }

    List<Block> getBlocks() {
        return blocks;
    }

    private void addLine(String line) {
        blocks.get(blockId).lines.add(line);
    }

    private void incrementBlockId() {
        blockId++;
    }

    private String nextId() {
        final String nextId = "%b" + blockId + "." + id;
        // nodeIds.put(node, nextId);
        incrementId();
        return nextId;
    }

    private void incrementId() {
        id++;
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

    private static String getTypeName(PhysicalObjectType type) {
        if (type instanceof ClassObjectType classObjectType) {
            return classObjectType.getDefinition().getInternalName();
        }

        return type.toString();
    }

    // TODO remove -> switch back to List<List<String>
    record Block(int id, List<String> lines) {}

    record NodeInfo(String id, String description) {}

    // TODO switch to Void, Void, Void, Void
    private final class DisassembleVisitor implements NodeVisitor<Disassembler, String, String, String, String> {
        @Override
        public String visit(Disassembler param, New node) {
            final String id = param.nextId();
            final String description = "new " + getTypeName(node.getType().getUpperBound());
            param.addLine(id + " = " + description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, ValueReturn node) {
            final String id = param.nextId();
            final String description = "return " + getId(node.getReturnValue());
            param.addLine(description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, Call node) {
            final String id = param.nextId();
            final String description = "call " + getDescription(node.getValueHandle());
            param.addLine(description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, Store node) {
            final String id = param.nextId();
            final String description = String.format(
                "store %s %s ← %s"
                , getValueId(node.getValueHandle())
                , getDescription(node.getValueHandle())
                , getDescription(node.getValue())
            );
            param.addLine(description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, Load node) {
            final String id = param.nextId();
            String description = String.format(
                "load %s %s"
                , getValueId(node.getValueHandle())
                , getDescription(node.getValueHandle())
            );
            param.addLine(id + " = " + description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, ConstructorElementHandle node) {
            final String id = param.nextId();
            final String description = getId(node.getInstance()) + " constructor";
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return id;
        }

        @Override
        public String visit(Disassembler param, InstanceFieldOf node) {
            final String id = param.nextId();
            final String description = node.getVariableElement().getName();
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

        private String getId(Node node) {
            final NodeInfo nodeInfo = disassemble(node);
            if (Objects.nonNull(nodeInfo)) {
                return nodeInfo.id;
            }

            // TODO temporary measure until all situations covered
            return "?";
        }

        private String getDescription(Node node) {
            final NodeInfo nodeInfo = disassemble(node);
            if (Objects.nonNull(nodeInfo)) {
                return nodeInfo.description;
            }

            // TODO temporary measure until all situations covered
            return "?";
        }

        private String getValueId(ValueHandle handle) {
            if (handle instanceof InstanceFieldOf fieldOf && fieldOf.getValueHandle() instanceof ReferenceHandle ref) {
                return getId(ref.getReferenceValue());
            }

            // TODO temporary measure until all situations covered
            return "?";
        }
    }
}
