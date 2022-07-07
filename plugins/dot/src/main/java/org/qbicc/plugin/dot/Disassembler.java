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
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.qbicc.context.CompilationContext;
import org.qbicc.graph.Action;
import org.qbicc.graph.Add;
import org.qbicc.graph.AddressOf;
import org.qbicc.graph.And;
import org.qbicc.graph.AsmHandle;
import org.qbicc.graph.BasicBlock;
import org.qbicc.graph.BinaryValue;
import org.qbicc.graph.BitCast;
import org.qbicc.graph.BitReverse;
import org.qbicc.graph.BlockEntry;
import org.qbicc.graph.ByteSwap;
import org.qbicc.graph.Call;
import org.qbicc.graph.CallNoReturn;
import org.qbicc.graph.CallNoSideEffects;
import org.qbicc.graph.CastValue;
import org.qbicc.graph.CheckCast;
import org.qbicc.graph.ClassOf;
import org.qbicc.graph.Cmp;
import org.qbicc.graph.CmpAndSwap;
import org.qbicc.graph.CmpG;
import org.qbicc.graph.CmpL;
import org.qbicc.graph.Comp;
import org.qbicc.graph.ConstructorElementHandle;
import org.qbicc.graph.Convert;
import org.qbicc.graph.CountLeadingZeros;
import org.qbicc.graph.CountTrailingZeros;
import org.qbicc.graph.CurrentThread;
import org.qbicc.graph.DebugAddressDeclaration;
import org.qbicc.graph.DebugValueDeclaration;
import org.qbicc.graph.Div;
import org.qbicc.graph.ElementOf;
import org.qbicc.graph.ExactMethodElementHandle;
import org.qbicc.graph.Executable;
import org.qbicc.graph.Extend;
import org.qbicc.graph.ExtractElement;
import org.qbicc.graph.ExtractInstanceField;
import org.qbicc.graph.ExtractMember;
import org.qbicc.graph.Fence;
import org.qbicc.graph.FunctionElementHandle;
import org.qbicc.graph.GetAndAdd;
import org.qbicc.graph.GetAndBitwiseAnd;
import org.qbicc.graph.GetAndBitwiseNand;
import org.qbicc.graph.GetAndBitwiseOr;
import org.qbicc.graph.GetAndBitwiseXor;
import org.qbicc.graph.GetAndSet;
import org.qbicc.graph.GetAndSetMax;
import org.qbicc.graph.GetAndSetMin;
import org.qbicc.graph.GetAndSub;
import org.qbicc.graph.GlobalVariable;
import org.qbicc.graph.Goto;
import org.qbicc.graph.If;
import org.qbicc.graph.InitCheck;
import org.qbicc.graph.InitializerHandle;
import org.qbicc.graph.InsertElement;
import org.qbicc.graph.InsertMember;
import org.qbicc.graph.InstanceFieldOf;
import org.qbicc.graph.InstanceOf;
import org.qbicc.graph.InterfaceMethodElementHandle;
import org.qbicc.graph.Invoke;
import org.qbicc.graph.InvokeNoReturn;
import org.qbicc.graph.IsEq;
import org.qbicc.graph.IsGe;
import org.qbicc.graph.IsGt;
import org.qbicc.graph.IsLe;
import org.qbicc.graph.IsLt;
import org.qbicc.graph.IsNe;
import org.qbicc.graph.Jsr;
import org.qbicc.graph.Load;
import org.qbicc.graph.LocalVariable;
import org.qbicc.graph.Max;
import org.qbicc.graph.MemberOf;
import org.qbicc.graph.MemberSelector;
import org.qbicc.graph.Min;
import org.qbicc.graph.Mod;
import org.qbicc.graph.MonitorEnter;
import org.qbicc.graph.MonitorExit;
import org.qbicc.graph.MultiNewArray;
import org.qbicc.graph.Multiply;
import org.qbicc.graph.Neg;
import org.qbicc.graph.New;
import org.qbicc.graph.NewArray;
import org.qbicc.graph.NewReferenceArray;
import org.qbicc.graph.Node;
import org.qbicc.graph.NodeVisitor;
import org.qbicc.graph.NotNull;
import org.qbicc.graph.OffsetOfField;
import org.qbicc.graph.Or;
import org.qbicc.graph.ParameterValue;
import org.qbicc.graph.PhiValue;
import org.qbicc.graph.PointerHandle;
import org.qbicc.graph.PopCount;
import org.qbicc.graph.ReadModifyWriteValue;
import org.qbicc.graph.ReferenceHandle;
import org.qbicc.graph.ReferenceTo;
import org.qbicc.graph.Ret;
import org.qbicc.graph.Return;
import org.qbicc.graph.Rol;
import org.qbicc.graph.Ror;
import org.qbicc.graph.Select;
import org.qbicc.graph.Shl;
import org.qbicc.graph.Shr;
import org.qbicc.graph.StackAllocation;
import org.qbicc.graph.StaticField;
import org.qbicc.graph.StaticMethodElementHandle;
import org.qbicc.graph.Store;
import org.qbicc.graph.Sub;
import org.qbicc.graph.Switch;
import org.qbicc.graph.TailCall;
import org.qbicc.graph.TailInvoke;
import org.qbicc.graph.Terminator;
import org.qbicc.graph.Throw;
import org.qbicc.graph.Truncate;
import org.qbicc.graph.UnaryValue;
import org.qbicc.graph.Unreachable;
import org.qbicc.graph.UnsafeHandle;
import org.qbicc.graph.Unschedulable;
import org.qbicc.graph.VaArg;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.graph.ValueReturn;
import org.qbicc.graph.VirtualMethodElementHandle;
import org.qbicc.graph.Xor;
import org.qbicc.graph.literal.ArrayLiteral;
import org.qbicc.graph.literal.BitCastLiteral;
import org.qbicc.graph.literal.BlockLiteral;
import org.qbicc.graph.literal.BooleanLiteral;
import org.qbicc.graph.literal.ByteArrayLiteral;
import org.qbicc.graph.literal.CompoundLiteral;
import org.qbicc.graph.literal.ConstantLiteral;
import org.qbicc.graph.literal.ElementOfLiteral;
import org.qbicc.graph.literal.FloatLiteral;
import org.qbicc.graph.literal.IntegerLiteral;
import org.qbicc.graph.literal.Literal;
import org.qbicc.graph.literal.MethodHandleLiteral;
import org.qbicc.graph.literal.NullLiteral;
import org.qbicc.graph.literal.ObjectLiteral;
import org.qbicc.graph.literal.PointerLiteral;
import org.qbicc.graph.literal.StringLiteral;
import org.qbicc.graph.literal.TypeLiteral;
import org.qbicc.graph.literal.UndefinedLiteral;
import org.qbicc.graph.literal.ValueConvertLiteral;
import org.qbicc.graph.literal.ZeroInitializerLiteral;
import org.qbicc.graph.schedule.Schedule;
import org.qbicc.type.ClassObjectType;
import org.qbicc.type.ValueType;
import org.qbicc.type.definition.element.ExecutableElement;
import org.qbicc.type.generic.BaseTypeSignature;

public final class Disassembler {
    private final Schedule schedule;
    private final DisassembleVisitor visitor;
    private final ExecutableElement element;
    private final Map<BasicBlock, BlockInfo> blocks = new HashMap<>();
    private final Map<Node, NodeInfo> nodeInfo = new HashMap<>();
    private final Set<BasicBlock> blockQueued = ConcurrentHashMap.newKeySet();
    private final Queue<BasicBlock> blockQueue = new ArrayDeque<>();
    private final List<BlockEdge> blockEdges = new ArrayList<>();
    private final List<CellEdge> cellEdges = new ArrayList<>();
    private final Queue<PhiValue> phiQueue = new ArrayDeque<>();
    private final Map<Node, CellId> cellIds = new HashMap<>();
    private BasicBlock currentBlock;
    private int currentBlockId;
    private int currentNodeId;

    Disassembler(BasicBlock entryBlock, ExecutableElement element, CompilationContext ctxt, BiFunction<CompilationContext, NodeVisitor<Disassembler, String, String, String, String>, NodeVisitor<Disassembler, String, String, String, String>> nodeVisitorFactory) {
        this.schedule = Schedule.forMethod(entryBlock);
        this.visitor = new DisassembleVisitor(nodeVisitorFactory.apply(ctxt, new Terminus()));
        this.element = element;
        this.blockQueue.add(entryBlock);
    }

    public ExecutableElement getElement() {
        return element;
    }

    public void setLineColor(String color) {
        final BlockInfo blockInfo = blocks.get(currentBlock);
        final List<String> lines = blockInfo.lines;
        blockInfo.lineColors.put(lines.size() - 1, color);
    }

    public CellId getCellId(Node node) {
        return cellIds.get(node);
    }

    public void addCellEdge(Node fromIndex, Node toIndex, String label, DotAttributes style) {
        cellEdges.add(new CellEdge(fromIndex, toIndex, label, style));
    }

    void run() {
        // Processing phi queues can lead to further blocks being found,
        // so both block and phi queues need to be processed
        // until there's nothing else in the queues.
        do {
            processBlockQueue();
            processPhiQueue();
        } while (!blockQueue.isEmpty() || !phiQueue.isEmpty());
    }

    int findBlockId(BasicBlock block) {
        return blocks.get(block).id;
    }

    private void processBlockQueue() {
        do {
            disassemble(blockQueue.poll());
        } while (!blockQueue.isEmpty());
    }

    private void processPhiQueue() {
        PhiValue phi;
        while ((phi = phiQueue.poll()) != null) {
            final BlockInfo blockInfo = blocks.get(phi.getPinnedBlock());
            // TODO all blocks (unless unreachable) should probably be there
            //      if some are missing it could be due to blocks not yet visited
            //      revisited the check once all nodes are handled
            if (Objects.nonNull(blockInfo)) {
                final Integer phiIndex = blockInfo.phiIndexes.get(phi);
                final StringBuilder phiLine = new StringBuilder(blockInfo.lines.get(phiIndex));
                for (BasicBlock block : phi.getPinnedBlock().getIncoming()) {
                    Value value = phi.getValueForInput(block.getTerminator());
                    phiLine.append(" ").append(visitor.show(value));
                }
                blockInfo.lines.set(phiIndex, phiLine.toString());
            }
        }
    }

    void disassemble(BasicBlock block) {
        final List<Node> nodes = schedule.getNodesForBlock(block);

        currentNodeId = 0;
        currentBlock = block;
        blocks.put(block, new BlockInfo(currentBlockId, new ArrayList<>(), new HashMap<>(), new HashMap<>()));

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

    List<CellEdge> getCellEdges() {
        return cellEdges;
    }

    private void queueBlock(BasicBlock from, BasicBlock to, String label, DotAttributes style) {
        blockEdges.add(new BlockEdge(from, to, label, style));
        if (blockQueued.add(to)) {
            blockQueue.add(to);
        }
    }

    private void queuePhi(PhiValue node) {
        phiQueue.add(node);
    }

    private int addLine(String line, Node... nodes) {
        final List<String> lines = blocks.get(currentBlock).lines;
        lines.add(line);
        final int lineIndex = lines.size() - 1;
        for (Node node : nodes) {
            cellIds.put(node, new CellId(currentBlockId, lineIndex));
        }
        return lineIndex;
    }

    private void addPhiLine(PhiValue node, String line) {
        final int index = addLine(line, node);
        blocks.get(currentBlock).phiIndexes.put(node, index);
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

    // The vast majority of lines will have the same color.
    // Hence, keep just a small collection for those lines that have a different color.
    record BlockInfo(int id, List<String> lines, Map<Integer, String> lineColors, Map<PhiValue, Integer> phiIndexes) {}

    record BlockEdge(BasicBlock from, BasicBlock to, String label, DotAttributes edgeType) {}

    record CellEdge(Node from, Node to, String label, DotAttributes edgeType) {}

    record NodeInfo(String id, String description) {}

    // TODO switch to Void, Void, Void, Void
    private final class DisassembleVisitor implements NodeVisitor.Delegating<Disassembler, String, String, String, String> {
        private final NodeVisitor<Disassembler, String, String, String, String> delegate;

        private DisassembleVisitor(NodeVisitor<Disassembler, String, String, String, String> delegate) {
            this.delegate = delegate;
        }

        @Override
        public NodeVisitor<Disassembler, String, String, String, String> getDelegateNodeVisitor() {
            return delegate;
        }

        @Override
        public String visitUnknown(Disassembler param, Value node) {
            throw new IllegalStateException("Visitor for node " + node.getClass() + " is not implemented");
        }

        @Override
        public String visitUnknown(Disassembler param, Action node) {
            throw new IllegalStateException("Visitor for node " + node.getClass() + " is not implemented");
        }

        @Override
        public String visitUnknown(Disassembler param, Terminator node) {
            throw new IllegalStateException("Visitor for node " + node.getClass() + " is not implemented");
        }

        @Override
        public String visitUnknown(Disassembler disassembler, ValueHandle node) {
            throw new IllegalStateException("Visitor for node " + node.getClass() + " is not implemented");
        }

        // START actions

        public String visit(Disassembler param, BlockEntry node) {
            return null; // not shown
        }

        @Override
        public String visit(Disassembler param, DebugAddressDeclaration node) {
            final String id = param.nextId();
            final String description = String.format(
                "declare %s %s"
                , node.getVariable()
                , show(node.getAddress())
            );
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, DebugValueDeclaration node) {
            final String id = param.nextId();
            final String description = String.format(
                "declare %s %s"
                , node.getVariable()
                , show(node.getValue())
            );
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, InitCheck node) {
            final String id = param.nextId();
            final String description = String.format(
                "check-init %s %s"
                , node.getInitializerElement()
                , show(node.getInitThunk())
            );
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Fence node) {
            final String id = param.nextId();
            final String description = "fence";
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, MonitorEnter node) {
            final String id = param.nextId();
            final String description = "monitor-enter " + show(node.getInstance());
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, MonitorExit node) {
            final String id = param.nextId();
            final String description = "monitor-exit " + show(node.getInstance());
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Store node) {
            final String id = param.nextId();
            final String description = String.format(
                "store %s ← %s"
                , showDescription(node.getValueHandle())
                , show(node.getValue())
            );
            param.addLine(description, node, node.getValueHandle());
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        // END actions

        // START values

        @Override
        public String visit(Disassembler param, AddressOf node) {
            final String id = param.nextId();
            final String description = "address-of " + show(node.getValueHandle());
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Call node) {
            call("call", param, node, node.getArguments());
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, CallNoSideEffects node) {
            call("call-nse", param, node, node.getArguments());
            return delegate.visit(param, node);
        }

        private void call(String prefix, Disassembler param, Node node, List<Value> args) {
            final String id = param.nextId();
            final String description = showWithArguments(prefix, node.getValueHandle(), args);
            if (node.getValueHandle() instanceof Executable exec
                && !exec.getExecutable().getSignature().getReturnTypeSignature().equals(BaseTypeSignature.V)) {
                param.addLine(id + " = " + description, node);
            } else {
                param.addLine(description, node);
            }
            param.nodeInfo.put(node, new NodeInfo(id, description));
        }

        @Override
        public String visit(Disassembler param, CheckCast node) {
            final String id = param.nextId();
            final String description = String.format(
                "(%s) %s"
                , show(node.getToType())
                , show(node.getInput())
            );
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, CmpAndSwap node) {
            final String id = param.nextId();
            final String description = String.format(
                "cmp-and-swap %s ← %s %s"
                , show(node.getValueHandle())
                , show(node.getExpectedValue())
                , show(node.getUpdateValue())
            );
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ExtractElement node) {
            final String id = param.nextId();
            final String description = String.format(
                "extract-element %s %s"
                , show(node.getIndex())
                , show(node.getArrayValue())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ExtractInstanceField node) {
            final String id = param.nextId();
            final String description = String.format(
                "extract-field %s %s"
                , node.getFieldElement().getName()
                , show(node.getObjectValue())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ExtractMember node) {
            final String id = param.nextId();
            final String description = String.format(
                "extract-member %s %s"
                , node.getMember().getName()
                , show(node.getCompoundValue())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, GetAndAdd node) {
            readModifyWrite("get+add %s ← %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, GetAndBitwiseAnd node) {
            readModifyWrite("get+and %s ← %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, GetAndBitwiseNand node) {
            readModifyWrite("get+nand %s ← %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, GetAndBitwiseOr node) {
            readModifyWrite("get+or %s ← %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, GetAndBitwiseXor node) {
            readModifyWrite("get+xor %s ← %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, GetAndSet node) {
            readModifyWrite("get+set %s ← %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, GetAndSetMax node) {
            readModifyWrite("get+set-max %s ← %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, GetAndSetMin node) {
            readModifyWrite("get+set-min %s ← %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, GetAndSub node) {
            readModifyWrite("get+sub %s ← %s", param, node);
            return delegate.visit(param, node);
        }

        private void readModifyWrite(String format, Disassembler param, ReadModifyWriteValue node) {
            final String id = param.nextId();
            final String description = String.format(
                format
                , show(node.getValueHandle())
                , show(node.getUpdateValue())
            );
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
        }

        @Override
        public String visit(Disassembler param, InsertElement node) {
            final String id = param.nextId();
            final String description = String.format(
                "insert-element %s %s %s"
                , show(node.getIndex())
                , show(node.getInsertedValue())
                , show(node.getArrayValue())
            );
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, InsertMember node) {
            final String id = param.nextId();
            final String description = String.format(
                "insert-member %s %s"
                , show(node.getInsertedValue())
                , show(node.getCompoundValue())
            );
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
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
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Invoke.ReturnValue node) {
            return visit(param, node.getInvoke());
        }

        @Override
        public String visit(Disassembler param, Load node) {
            final String id = param.nextId();
            String description = "load " + showDescription(node.getValueHandle());
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, MemberSelector node) {
            final String id = param.nextId();
            String description = "sel " + show(node.getValueHandle());
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, MultiNewArray node) {
            final String id = param.nextId();
            String dimensions = node.getDimensions().stream()
                .map(this::show)
                .collect(Collectors.joining(" "));
            final String description = String.format(
                "new-multi-array %s %s"
                , node.getArrayType()
                , dimensions
            );
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, New node) {
            final String id = param.nextId();
            final String description = "new " + show(node.getTypeId());
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, NewArray node) {
            final String id = param.nextId();
            final String description = String.format(
                "new-array %s[%s]"
                , node.getArrayType().toString()
                , show(node.getSize())
            );
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, NewReferenceArray node) {
            final String id = param.nextId();
            final String description = String.format(
                "new-ref-array %s[%s]"
                , node.getArrayType().toString()
                , show(node.getSize())
            );
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, OffsetOfField node) {
            final String id = param.nextId();
            final String description = "offset-of " + node.getFieldElement().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ParameterValue node) {
            final String id = param.nextId();
            final String description = node.getLabel() + node.getIndex();
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, PhiValue node) {
            final String id = param.nextId();
            final String description = "phi";
            param.addPhiLine(node, id + " = " + description);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            param.queuePhi(node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ReferenceTo node) {
            final String id = param.nextId();
            final String description = "ref-to " + node.getType().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
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
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, StackAllocation node) {
            final String id = param.nextId();
            final String description = String.format(
                "alloca %s %s"
                , node.getType()
                , show(node.getCount())
            );
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, VaArg node) {
            final String id = param.nextId();
            final String description = String.format(
                "va-arg %s %s"
                , node.getType().toString()
                , show(node.getVaList())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        // END values

        // START binary values

        @Override
        public String visit(Disassembler param, Add node) {
            binary("%s + %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, And node) {
            binary("%s & %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Cmp node) {
            binary("cmp %s %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, CmpL node) {
            binary("cmpl %s %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, CmpG node) {
            binary("cmpg %s %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Div node) {
            binary("%s / %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, IsEq node) {
            binary("%s == %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, IsGe node) {
            binary("%s ≥ %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, IsGt node) {
            binary("%s > %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, IsLe node) {
            binary("%s ≤ %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, IsLt node) {
            binary("%s < %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, IsNe node) {
            binary("%s != %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Max node) {
            binary("max %s %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Min node) {
            binary("min %s %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Mod node) {
            binary("%s %% %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Multiply node) {
            binary("%s * %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Or node) {
            binary("%s | %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Rol node) {
            binary("%s |<< %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Ror node) {
            binary("%s |>> %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Shl node) {
            binary("%s << %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Shr node) {
            binary("%s >> %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Sub node) {
            binary("%s - %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Xor node) {
            binary("%s ^ %s", param, node);
            return delegate.visit(param, node);
        }

        private void binary(String format, Disassembler param, BinaryValue node) {
            final String id = param.nextId();
            final String description = String.format(
                format
                , show(node.getLeftInput())
                , show(node.getRightInput())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
        }

        // END binary values

        // START cast values

        @Override
        public String visit(Disassembler param, BitCast node) {
            final String id = param.nextId();
            final String description = String.format(
                "bit-cast→%s %s"
                , node.getType()
                , show(node.getInput())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Convert node) {
            final String id = param.nextId();
            final String description = "convert " + show(node.getInput());
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
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
            return delegate.visit(param, node);
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
            return delegate.visit(param, node);
        }

        // END cast values

        // START literal values

        @Override
        public String visit(Disassembler param, ArrayLiteral node) {
            literal(param, node, "array [" + node.getValues().size() + "]");
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, BitCastLiteral node) {
            literal(param, node, "bit cast →" + node.getType());
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, BooleanLiteral node) {
            literal(param, node, String.valueOf(node.booleanValue()));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, BlockLiteral node) {
            literal(param, node, "block");
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ByteArrayLiteral node) {
            literal(param, node, "byte-array [" + node.getValues().length + "]");
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, CompoundLiteral node) {
            literal(param, node, "compound " + node.getValues());
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ConstantLiteral node) {
            literal(param, node, "const " + node.getType().toString());
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ElementOfLiteral node) {
            literal(param, node, "element-of " + node.getType());
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, FloatLiteral node) {
            literal(param, node, String.valueOf(node.doubleValue()));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, IntegerLiteral node) {
            literal(param, node, String.valueOf(node.longValue()));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, MethodHandleLiteral node) {
            literal(param, node, node.toString());
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, NullLiteral node) {
            literal(param, node,"null");
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ObjectLiteral node) {
            literal(param, node,"object");
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, PointerLiteral node) {
            literal(param, node,"pointer");
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, StringLiteral node) {
            literal(param, node,'"' + node.getValue() + '"');
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, TypeLiteral node) {
            literal(param, node, unwrapTypeName(node.getType().getUpperBound()));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, UndefinedLiteral node) {
            literal(param, node, "undef");
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ValueConvertLiteral node) {
            literal(param, node, "convert →" + node.getType().toString());
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ZeroInitializerLiteral node) {
            literal(param, node, "zero");
            return delegate.visit(param, node);
        }

        private void literal(Disassembler param, Literal node, String description) {
            final String id = param.nextId();
            param.nodeInfo.put(node, new NodeInfo(id, description));
        }

        // END literal values

        // START unary values

        @Override
        public String visit(Disassembler param, BitReverse node) {
            unary("bit-reverse %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ByteSwap node) {
            unary("bit-swap %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ClassOf node) {
            unary("%s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Comp node) {
            unary("~%s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, CountLeadingZeros node) {
            unary("clz %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, CountTrailingZeros node) {
            unary("ctz %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Neg node) {
            unary("-%s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, NotNull node) {
            unary("not-null %s", param, node);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, PopCount node) {
            unary("pop-count %s", param, node);
            return delegate.visit(param, node);
        }

        private void unary(String format, Disassembler param, UnaryValue node) {
            final String id = param.nextId();
            final String description = String.format(format, show(node.getInput()));
            param.nodeInfo.put(node, new NodeInfo(id, description));
        }

        // END unary values

        // START value handles

        @Override
        public String visit(Disassembler param, AsmHandle node) {
            final String id = param.nextId();
            final String description = String.format(
                "asm %s %s"
                , node.getInstruction()
                , node.getConstraints()
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ConstructorElementHandle node) {
            final String id = param.nextId();
            final String description = showId(node.getInstance()) + " constructor";
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, CurrentThread node) {
            final String id = param.nextId();
            final String description = "current-thread";
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ElementOf node) {
            final String id = param.nextId();
            final String description = String.format(
                "element-of %s %s"
                , show(node.getValueHandle())
                , show(node.getIndex())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ExactMethodElementHandle node) {
            final String id = param.nextId();
            final String description = node.getExecutable().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, FunctionElementHandle node) {
            final String id = param.nextId();
            final String description = node.getExecutable().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, GlobalVariable node) {
            final String id = param.nextId();
            final String description = node.getVariableElement().getName();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, InitializerHandle node) {
            final String id = param.nextId();
            final String description = node.getExecutable().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, InstanceFieldOf node) {
            final String id = param.nextId();
            String description = node.getValueHandle() instanceof ReferenceHandle ref
                ? show(ref.getReferenceValue()) + " " + node.getVariableElement().getName()
                : "?";
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, InterfaceMethodElementHandle node) {
            final String id = param.nextId();
            final String description = node.getExecutable().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, LocalVariable node) {
            final String id = param.nextId();
            final String description = node.getVariableElement().getName();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, MemberOf node) {
            final String id = param.nextId();
            final String description = "member-of " + show(node.getValueHandle());
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, PointerHandle node) {
            final String id = param.nextId();
            final String description = "prt " + show(node.getPointerValue());
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ReferenceHandle node) {
            final String id = param.nextId();
            final String description = "ref " + show(node.getReferenceValue());
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, StaticField node) {
            final String id = param.nextId();
            final String description = node.getVariableElement().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, StaticMethodElementHandle node) {
            final String id = param.nextId();
            final String description = node.getExecutable().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, UnsafeHandle node) {
            final String id = param.nextId();
            final String description = String.format(
                "unsafe-handle %s %s"
                , show(node.getValueHandle())
                , show(node.getOffset())
            );
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, VirtualMethodElementHandle node) {
            final String id = param.nextId();
            final String description = node.getExecutable().toString();
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        // END value handles

        // START terminators

        @Override
        public String visit(Disassembler param, CallNoReturn node) {
            final String id = param.nextId();
            final String description = showWithArguments("call-no-return", node.getValueHandle(), node.getArguments());
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Goto node) {
            final String id = param.nextId();
            final String description = "goto";
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            param.queueBlock(currentBlock, node.getResumeTarget(), "\"\"", DotNodeVisitor.EdgeType.CONTROL_FLOW);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, If node) {
            final String id = param.nextId();
            final String description = "if " + showDescription(node.getCondition());
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            param.queueBlock(currentBlock, node.getTrueBranch(), "true", DotNodeVisitor.EdgeType.COND_TRUE_FLOW);
            param.queueBlock(currentBlock, node.getFalseBranch(), "false", DotNodeVisitor.EdgeType.COND_FALSE_FLOW);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Invoke node) {
            final String id = param.nextId();
            final String description = showWithArguments("invoke", node.getValueHandle(), node.getArguments());
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            param.queueBlock(currentBlock, node.getCatchBlock(), "catch", DotNodeVisitor.EdgeType.CONTROL_FLOW);
            param.queueBlock(currentBlock, node.getResumeTarget(), "resume", DotNodeVisitor.EdgeType.CONTROL_FLOW);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, InvokeNoReturn node) {
            final String id = param.nextId();
            final String description = showWithArguments("invoke-no-return", node.getValueHandle(), node.getArguments());
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            param.queueBlock(currentBlock, node.getCatchBlock(), "catch", DotNodeVisitor.EdgeType.CONTROL_FLOW);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Jsr node) {
            final String id = param.nextId();
            final String description = "jsr";
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            param.queueBlock(currentBlock, node.getResumeTarget(), "ret", DotNodeVisitor.EdgeType.RET_RESUME_FLOW);
            param.queueBlock(currentBlock, node.getJsrTarget(), "to", DotNodeVisitor.EdgeType.CONTROL_FLOW);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Ret node) {
            final String id = param.nextId();
            final String description = "ret";
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Return node) {
            final String id = param.nextId();
            final String description = "return";
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Switch node) {
            final String id = param.nextId();
            final String description = "switch " + show(node.getSwitchValue());
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            for (int i = 0; i < node.getNumberOfValues(); i++) {
                param.queueBlock(currentBlock, node.getTargetForIndex(i), String.valueOf(node.getValueForIndex(i)), DotNodeVisitor.EdgeType.COND_TRUE_FLOW);
            }
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, TailCall node) {
            final String id = param.nextId();
            final String description = showWithArguments("tail-call", node.getValueHandle(), node.getArguments());
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, TailInvoke node) {
            final String id = param.nextId();
            final String description = showWithArguments("tail-invoke", node.getValueHandle(), node.getArguments());
            param.addLine(id + " = " + description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            param.queueBlock(currentBlock, node.getCatchBlock(), "catch", DotNodeVisitor.EdgeType.CONTROL_FLOW);
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Throw node) {
            final String id = param.nextId();
            final String description = "throw " + show(node.getThrownValue());
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, Unreachable node) {
            final String id = param.nextId();
            final String description = "unreachable";
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        @Override
        public String visit(Disassembler param, ValueReturn node) {
            final String id = param.nextId();
            final String description = "return " + show(node.getReturnValue());
            param.addLine(description, node);
            param.nodeInfo.put(node, new NodeInfo(id, description));
            return delegate.visit(param, node);
        }

        // END terminators

        private String show(Node node) {
            if (node instanceof Unschedulable
                || node instanceof UnaryValue
                || node instanceof CastValue) {
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

    private static final class Terminus implements NodeVisitor<Disassembler, String, String, String, String> {
        @Override
        public String visitUnknown(Disassembler param, Action node) {
            return null;
        }

        @Override
        public String visitUnknown(Disassembler param, Terminator node) {
            return null;
        }

        @Override
        public String visitUnknown(Disassembler param, ValueHandle node) {
            return null;
        }

        @Override
        public String visitUnknown(Disassembler param, Value node) {
            return null;
        }
    }

    record CellId(int block, int line) {
        @Override
        public String toString() {
            return String.format("b%d:%d", block, line);
        }
    }
}