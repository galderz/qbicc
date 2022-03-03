
package org.qbicc.plugin.opt.ea;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Objects;

import org.qbicc.graph.Action;
import org.qbicc.graph.InstanceFieldOf;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.NodeVisitor;
import org.qbicc.graph.ReferenceHandle;
import org.qbicc.graph.Terminator;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.plugin.dot.DotGenerationContext;
import org.qbicc.plugin.dot.TooBigException;

public final class EscapeAnalysisDotVisitor implements NodeVisitor.Delegating<Appendable, String, String, String, String> {
    private final DotGenerationContext dtxt;
    private final NodeVisitor<Appendable, String, String, String, String> delegate;
    private final ConnectionGraph connectionGraph;
    private boolean attr;
    private boolean commaNeeded;
    private int counter = 500;

    public EscapeAnalysisDotVisitor(DotGenerationContext dtxt, NodeVisitor<Appendable, String, String, String, String> delegate) {
        this.dtxt = dtxt;
        this.delegate = delegate;
        this.connectionGraph = EscapeAnalysisState.get(dtxt.ctxt).getConnectionGraph(dtxt.element);
    }

    @Override
    public NodeVisitor<Appendable, String, String, String, String> getDelegateNodeVisitor() {
        return delegate;
    }

    @Override
    public String visit(Appendable param, New node) {
        final String name = dtxt.visited.get(node);
        appendTo(param, name);
        attr(param, "style", "filled");
        attr(param, "fillcolor", nodeType(connectionGraph.getEscapeValue(node)).fillColor);
        nl(param);
        final Collection<InstanceFieldOf> fields = connectionGraph.getFieldEdges(node);
        for (InstanceFieldOf field : fields) {
            addEdge(param, node, field, EdgeType.FIELD, "F");
        }
        return name;
    }

    @Override
    public String visit(Appendable param, ReferenceHandle node) {
        final String name = dtxt.visited.get(node);
        final Node pointsTo = connectionGraph.getPointsToEdge(node);
        if (Objects.nonNull(pointsTo)) {
            addEdge(param, node, pointsTo, EdgeType.POINTS_TO, "P");
        }
        nl(param);
        return name;
    }

    public String visit(Appendable param, Phantom node) {
        String name = register(node);
        appendTo(param, name);
        attr(param, "label", "phantom");
        nl(param);
        return name;
    }

    private NodeType nodeType(EscapeValue value) {
        return switch (value) {
            case GLOBAL_ESCAPE -> NodeType.GLOBAL_ESCAPE;
            case ARG_ESCAPE -> NodeType.ARG_ESCAPE;
            case NO_ESCAPE -> NodeType.NO_ESCAPE;
            case UNKNOWN -> NodeType.UNKNOWN;
        };
    }

    private void addEdge(Appendable param, Node from, Node to, EdgeType edge, String label) {
        String fromName = getNodeName(param, from);
        String toName = getNodeName(param, to);
        appendTo(param, fromName);
        appendTo(param, " -> ");
        appendTo(param, toName);
        attr(param, "label", label);
        attr(param, "style", edge.style);
        attr(param, "color", edge.color);
        attr(param, "fontcolor", edge.color);
        nl(param);
    }

    private String getNodeName(Appendable param, Node node) {
        if (node instanceof Value) {
            return getNodeName(param, (Value) node);
        } else if (node instanceof ValueHandle) {
            return getNodeName(param, (ValueHandle)node);
        } else if (node instanceof Action) {
            return getNodeName(param, (Action) node);
        } else if (node instanceof Terminator){
            return getNodeName(param, (Terminator) node);
        } else {
            assert node instanceof Phantom;
            return visit(param, (Phantom) node);
        }
    }

    private String nextName() {
        int id = counter++;
        if (id > 1000) {
            throw new TooBigException();
        }
        return "n" + id;
    }

    // TODO copied from DotNodeVisitor
    private String register(final Node node) {
        String name = nextName();
        dtxt.visited.put(node, name);
        return name;
    }

    // TODO copied from DotNodeVisitor
    private String getNodeName(Appendable param, Action node) {
        String name = dtxt.visited.get(node);
        if (name == null) {
            name = node.accept(dtxt.dotNodeVisitor, param);
            //name = node.accept(this, param);
        }
        return name;
    }

    // TODO copied from DotNodeVisitor
    private String getNodeName(Appendable param, Value node) {
        String name = dtxt.visited.get(node);
        if (name == null) {
            name = node.accept(dtxt.dotNodeVisitor, param);
            //name = node.accept(this, param);
        }
        return name;
    }

    // TODO copied from DotNodeVisitor
    private String getNodeName(Appendable param, ValueHandle node) {
        String name = dtxt.visited.get(node);
        if (name == null) {
            name = node.accept(dtxt.dotNodeVisitor, param);
            //name = node.accept(this, param);
        }
        return name;
    }

    // TODO copied from DotNodeVisitor
    private String getNodeName(Appendable param, Terminator node) {
        String name = dtxt.visited.get(node);
        if (name == null) {
            name = node.accept(dtxt.dotNodeVisitor, param);
            //name = node.accept(this, param);
        }
        return name;
    }

    // TODO copied from DotNodeVisitor
    private void attr(Appendable param, String name, String val) {
        if (!attr) {
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
    static void quote(Appendable output, String orig) {
        appendTo(output, '"');
        int cp;
        for (int i = 0; i < orig.length(); i += Character.charCount(cp)) {
            cp = orig.codePointAt(i);
            if (cp == '"') {
                appendTo(output, '\\');
            } else if (cp == '\\') {
                if ((i + 1) == orig.length() ||
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

    private enum NodeType {
        GLOBAL_ESCAPE("lightsalmon"),
        ARG_ESCAPE("lightcyan3"),
        NO_ESCAPE("lightblue1"),
        UNKNOWN("lightpink1");

        final String fillColor;

        NodeType(String fillColor) {
            this.fillColor = fillColor;
        }
    }

    private enum EdgeType {
        DEFERRED("gray", "dashed"),
        POINTS_TO("gray", "solid"),
        FIELD("gray", "solid");

        final String color;
        final String style;
        final char label;

        EdgeType(String color, String style) {
            this.color = color;
            this.style = style;
            this.label = this.toString().charAt(0);
        }
    }
}
