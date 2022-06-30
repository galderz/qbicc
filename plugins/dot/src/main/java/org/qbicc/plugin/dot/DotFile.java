package org.qbicc.plugin.dot;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

final class DotFile {
    final Disassembler disassembler;

    DotFile(Disassembler disassembler) {
        this.disassembler = disassembler;
    }

    void writeTo(Appendable out) throws IOException {
        out.append("digraph {").append(System.lineSeparator());
        out.append("fontname = \"Helvetica,Arial,sans-serif\"").append(System.lineSeparator());
        out.append("node [fontname = \"Helvetica,Arial,sans-serif\"]").append(System.lineSeparator());
        out.append("edge [fontname = \"Helvetica,Arial,sans-serif\"]").append(System.lineSeparator());
        // out.append("graph [rankdir = LR];").append(System.lineSeparator());
        out.append("graph [rankdir = TB];").append(System.lineSeparator());
        out.append("edge [splines = true];").append(System.lineSeparator());
        out.append(System.lineSeparator());

        final Collection<Disassembler.BlockInfo> blocks = disassembler.getBlocks().values();
        for (Disassembler.BlockInfo block : blocks) {
            out.append(String.format("b%d [", block.id())).append(System.lineSeparator());
            out.append("shape = plaintext").append(System.lineSeparator());
            out.append("label = <").append(System.lineSeparator());
            out.append("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">").append(System.lineSeparator());

            for (String line : block.lines()) {
                out.append("<tr><td align=\"text\">"); // TODO add escape state via bgcolor="..." to each line

                String escaped = line
                    .replaceAll(">", "&gt;")
                    .replaceAll("<", "&lt;");
                out.append(escaped);

                // Add extra space for proper formatting
                for (int i = 0; i < line.length() / 10; i++) {
                    out.append("&nbsp;");
                }
                out.append("<br align=\"left\"/></td></tr>").append(System.lineSeparator());
            }

            out.append("</table>").append(System.lineSeparator());
            out.append(">").append(System.lineSeparator());
            out.append("]").append(System.lineSeparator());
        }

        final List<Disassembler.BlockEdge> blockEdges = disassembler.getBlockEdges();
        for (Disassembler.BlockEdge blockEdge : blockEdges) {
            out.append(String.format(
                "b%d -> b%d [label = %s, style = %s, color = %s];"
                , disassembler.findBlockId(blockEdge.from())
                , disassembler.findBlockId(blockEdge.to())
                , blockEdge.label()
                , blockEdge.edgeType().style()
                , blockEdge.edgeType().color()
            )).append(System.lineSeparator());
        }

        out.append("}").append(System.lineSeparator());
    }
}
