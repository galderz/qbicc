package org.qbicc.plugin.opt.ea;

import org.qbicc.context.CompilationContext;
import org.qbicc.graph.Action;
import org.qbicc.graph.BasicBlock;
import org.qbicc.graph.CallNoReturn;
import org.qbicc.graph.Goto;
import org.qbicc.graph.If;
import org.qbicc.graph.InstanceFieldOf;
import org.qbicc.graph.Invoke;
import org.qbicc.graph.InvokeNoReturn;
import org.qbicc.graph.Jsr;
import org.qbicc.graph.Load;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.NodeVisitor;
import org.qbicc.graph.ParameterValue;
import org.qbicc.graph.PhiValue;
import org.qbicc.graph.ReferenceHandle;
import org.qbicc.graph.Ret;
import org.qbicc.graph.Return;
import org.qbicc.graph.Switch;
import org.qbicc.graph.TailCall;
import org.qbicc.graph.TailInvoke;
import org.qbicc.graph.Terminator;
import org.qbicc.graph.Throw;
import org.qbicc.graph.Unreachable;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.graph.ValueReturn;
import org.qbicc.type.definition.element.ExecutableElement;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionGraphDotVisitor implements NodeVisitor<Appendable, String, String, String, String> {

    private final ConnectionGraph connectionGraph;

    // TODO copied from DotNodeVisitor
    final BasicBlock entryBlock;
    final Map<Node, String> visited = new HashMap<>();
    private final Set<BasicBlock> blockQueued = ConcurrentHashMap.newKeySet();
    private final Queue<BasicBlock> blockQueue = new ArrayDeque<>();
    int depth;
    int counter;
    int bbCounter;
    boolean attr;
    boolean commaNeeded;
    Queue<String> dependencyList = new ArrayDeque();
    // List<NodePair> bbConnections = new ArrayList<>(); // stores pair of Terminator, BlockEntry
    // private final Queue<PhiValue> phiQueue = new ArrayDeque<>();

    public ConnectionGraphDotVisitor(BasicBlock entryBlock, ConnectionGraph connectionGraph) {
        this.entryBlock = entryBlock;
        this.connectionGraph = connectionGraph;
    }

    @Override
    public String visit(Appendable param, New node) {
        String name = register(node);
        appendTo(param, name);
        final EscapeValue escapeValue = connectionGraph.getEscapeValue(node);
        attr(param, "label", "new\\n" + node.getType().getUpperBound().toString());
        attr(param, "style", "filled");
        attr(param, "fillcolor", String.valueOf(nodeType(escapeValue).fillColor));
        nl(param);
        final Collection<InstanceFieldOf> fields = connectionGraph.getFields(node);
        for (InstanceFieldOf field : fields) {
            String fieldName = visited.get(field);
            if (fieldName == null) {
                // Visit field not yet visited
                fieldName = visit(param, field);
            }

            addEdge(param, name, fieldName, EdgeType.FIELD);
        }
        return name;
    }

    private void addEdge(Appendable param, String fromName, String toName, EdgeType edge) {
        appendTo(param, fromName);
        appendTo(param, " -> ");
        appendTo(param, toName);
        attr(param, "style", edge.style);
        attr(param, "color", edge.color);
        attr(param, "label", String.valueOf(edge.label));
        nl(param);
    }

    @Override
    public String visit(final Appendable param, final ReferenceHandle node) {
        String name = register(node);
        processDependency(param, node.getReferenceValue());
        return name;
    }

    @Override
    public String visit(final Appendable param, final InstanceFieldOf node) {
        String name = register(node);
        appendTo(param, name);
        attr(param, "label", "field access\\n"+node.getVariableElement().getName());
        nl(param);
        processDependency(param, node.getValueHandle());
        return name;
    }

    @Override
    public String visit(final Appendable param, final Load node) {
        String name = register(node);
        processDependency(param, node.getValueHandle());
        return name;
    }

    @Override
    public String visit(Appendable param, ParameterValue node) {
        String name = register(node);
        final ValueHandle deferred = connectionGraph.getDeferred(node);
        if (deferred != null) {
            String deferredName = visited.get(deferred);
            addEdge(param, name, deferredName, EdgeType.DEFERRED);
        }
        return name;
    }

    // terminator
    public String visit(final Appendable param, final CallNoReturn node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(Appendable param, Invoke node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(Appendable param, InvokeNoReturn node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(Appendable param, TailCall node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(Appendable param, TailInvoke node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(final Appendable param, final Goto node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(final Appendable param, final If node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(final Appendable param, final Jsr node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(final Appendable param, final Ret node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(final Appendable param, final Return node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(final Appendable param, final Invoke.ReturnValue node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(final Appendable param, final Unreachable node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(final Appendable param, final Switch node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(final Appendable param, final Throw node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // terminator
    public String visit(final Appendable param, final ValueReturn node) {
        String name = register(node);
        processDependency(param, node.getDependency());
        appendTo(param, "}");
        nl(param);
        return name;
    }

    // TODO copied from DotNodeVisitor
    void processDependency(Appendable param, Node node) {
        if (depth++ > 500) {
            throw new TooBigException();
        }
        try {
            getNodeName(param, node);
        } finally {
            depth--;
        }
    }

    // TODO copied from DotNodeVisitor
    private String getNodeName(Appendable param, Node node) {
        if (node instanceof Value) {
            return getNodeName(param, (Value) node);
        } else if (node instanceof ValueHandle) {
            return getNodeName(param, (ValueHandle)node);
        } else if (node instanceof Action) {
            return getNodeName(param, (Action) node);
        } else {
            assert node instanceof Terminator;
            return getNodeName(param, (Terminator) node);
        }
    }

    // TODO copied from DotNodeVisitor
    private String getNodeName(Appendable param, Action node) {
        String name = visited.get(node);
        if (name == null) {
            name = node.accept(this, param);
        }
        return name;
    }

    // TODO copied from DotNodeVisitor
    private String getNodeName(Appendable param, Value node) {
        String name = visited.get(node);
        if (name == null) {
            name = node.accept(this, param);
        }
        return name;
    }

    // TODO copied from DotNodeVisitor
    private String getNodeName(Appendable param, ValueHandle node) {
        String name = visited.get(node);
        if (name == null) {
            name = node.accept(this, param);
        }
        return name;
    }

    // TODO copied from DotNodeVisitor
    private String getNodeName(Appendable param, Terminator node) {
        String name = visited.get(node);
        if (name == null) {
            name = node.accept(this, param);
        }
        return name;
    }

    // TODO copied from DotNodeVisitor
    private void attr(Appendable param, String name, String val) {
        if (! attr) {
            attr = true;
            appendTo(param, " [");
        }
        if (commaNeeded) {
            appendTo(param, ',');
        } else {
            commaNeeded = true;
        }
        appendTo(param, name);
        appendTo(param, '=');
        quote(param, val);
    }

    // TODO copied from DotNodeVisitor
    void quote(Appendable output, String orig) {
        appendTo(output, '"');
        int cp;
        for (int i = 0; i < orig.length(); i += Character.charCount(cp)) {
            cp = orig.codePointAt(i);
            if (cp == '"') {
                appendTo(output, '\\');
            } else if (cp == '\\') {
                if((i + 1) == orig.length() ||
                    "nlrGNTHE".indexOf(orig.codePointAt(i + 1)) == -1) {
                    appendTo(output, '\\');
                }
            }
            if (Character.charCount(cp) == 1) {
                appendTo(output, (char) cp);
            } else {
                appendTo(output, Character.highSurrogate(cp));
                appendTo(output, Character.lowSurrogate(cp));
            }
        }
        appendTo(output, '"');
    }

    // TODO copied from DotNodeVisitor
    private String register(final Node node) {
        String name = nextName();
        visited.put(node, name);
        return name;
    }

    // TODO copied from DotNodeVisitor
    private String nextName() {
        return "n" + counter++;
    }

    // TODO copied from DotNodeVisitor
    public void process(final Appendable param) {
        addToQueue(entryBlock);
        BasicBlock block;
        while ((block = blockQueue.poll()) != null) {
            String bbName = nextBBName();
            appendTo(param, "subgraph cluster_" + bbName + " {");
            nl(param);
            appendTo(param, "label = \"" + bbName + "\";");
            nl(param);
            getNodeName(param, block.getTerminator());
        }
        // connectBasicBlocks(param);
        // processPhiQueue(param);
    }

    // TODO copied from DotNodeVisitor
    private void nl(final Appendable param) {
        if (attr) {
            appendTo(param, ']');
            attr = false;
            commaNeeded = false;
        }
        appendTo(param, System.lineSeparator());
    }

    // TODO copied from DotNodeVisitor
    static void appendTo(Appendable param, Object obj) {
        try {
            param.append(obj.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    // TODO copied from DotNodeVisitor
    void addToQueue(final BasicBlock block) {
        if (blockQueued.add(block)) {
            blockQueue.add(block);
        }
    }

    // TODO copied from DotNodeVisitor
    private String nextBBName() {
        return "b" + bbCounter++;
    }

    private enum EdgeType {
        DEFERRED("black", "dashed"),
        POINTS_TO("black", "solid"),
        FIELD("black", "solid");

        final String color;
        final String style;
        final char label;

        EdgeType(String color, String style) {
            this.color = color;
            this.style = style;
            this.label = this.toString().charAt(0);
        }
    }

    private NodeType nodeType(EscapeValue value) {
        if (value == null)
            return NodeType.UNKNOWN;

        switch (value) {
            case GLOBAL_ESCAPE:
                return NodeType.GLOBAL_ESCAPE;
            case ARG_ESCAPE:
                return NodeType.ARG_ESCAPE;
            case NO_ESCAPE:
                return NodeType.NO_ESCAPE;
            default:
                throw new IllegalStateException("Unknown escape value: " + value);
        }
    }

    private enum NodeType {
        GLOBAL_ESCAPE(2),
        ARG_ESCAPE(3),
        NO_ESCAPE(1),
        UNKNOWN(4);

        final int fillColor;

        NodeType(int fillColor) {
            this.fillColor = fillColor;
        }
    }
    
}
