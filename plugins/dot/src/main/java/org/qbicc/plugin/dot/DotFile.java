package org.qbicc.plugin.dot;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
        out.append("graph [rankdir = TB];").append(System.lineSeparator());
        out.append("edge [splines = true];").append(System.lineSeparator());
        out.append(System.lineSeparator());

        disassembler.getBlocks().values().stream()
            .sorted(Comparator.comparing(Disassembler.BlockInfo::id))
            .forEach(b -> writeBlock(out, b));

        for (Disassembler.BlockEdge blockEdge : disassembler.getBlockEdges()) {
            out.append(String.format(
                "b%d -> b%d [label = %s, style = %s, color = %s];"
                , disassembler.findBlockId(blockEdge.from())
                , disassembler.findBlockId(blockEdge.to())
                , blockEdge.label()
                , blockEdge.edgeType().style()
                , blockEdge.edgeType().color()
            )).append(System.lineSeparator());
        }

        for (Disassembler.CellEdge cellEdge : disassembler.getCellEdges()) {
            Disassembler.CellId fromId = disassembler.getCellId(cellEdge.from());
            Disassembler.CellId toId = disassembler.getCellId(cellEdge.to());
            final DotAttributes edgeType = cellEdge.edgeType();
            final String portPos = edgeType.portPos();
            out.append(
                """
                %s:%s -> %s:%s [label="&nbsp;%s",fontcolor="gray",style="%s",color="%s"]
                """
                .formatted(fromId, portPos, toId, portPos, cellEdge.label(), edgeType.style(), edgeType.color())
            );
        }

        out.append("}").append(System.lineSeparator());
    }

    private static void writeBlock(Appendable out, Disassembler.BlockInfo block) {
        try {
            // TODO Consider using java text blocks to make things cleaner
            out.append(String.format("b%d [", block.id())).append(System.lineSeparator());
            out.append("shape = plaintext").append(System.lineSeparator());
            out.append("label = <").append(System.lineSeparator());
            out.append("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">").append(System.lineSeparator());

            final List<String> lines = block.lines();
            for (int i = 0; i < lines.size(); i++) {
                final String line = lines.get(i);

                final String lineColor = block.lineColors().get(i);
                out.append("<tr><td align=\"text\"");
                out.append(" port=\"").append(String.valueOf(i)).append("\"");
                if (Objects.nonNull(lineColor)) {
                    out.append(" bgcolor=\"").append(lineColor).append("\"");
                }
                out.append(">");

                String escaped = line
                    .replaceAll(">", "&gt;")
                    .replaceAll("<", "&lt;")
                    .replaceAll("&", "&amp;");
                out.append(escaped);

                // Add extra space for proper formatting
                for (int j = 0; j < line.length() / 10; j++) {
                    out.append("&nbsp;");
                }
                out.append("<br align=\"left\"/></td></tr>").append(System.lineSeparator());
            }

            out.append("</table>").append(System.lineSeparator());
            out.append(">").append(System.lineSeparator());
            out.append("]").append(System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();  // TODO: Customise this generated block
        }
    }
}
