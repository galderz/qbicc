package org.qbicc.plugin.dot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class DotFile {
    final Disassembler disassembler;

    DotFile(Disassembler disassembler) {
        this.disassembler = disassembler;
    }

    void writeTo(Appendable out) throws IOException {
        out.append(
            """
            digraph {
            fontname = "Helvetica,Arial,sans-serif"
            node [fontname = "Helvetica,Arial,sans-serif"]
            edge [fontname = "Helvetica,Arial,sans-serif"]
            graph [rankdir = TB];
            edge [splines = true];
            """
        );

        disassembler.getBlocks().values().stream()
            .sorted(Comparator.comparing(Disassembler.BlockInfo::id))
            .forEach(b -> writeBlock(out, b));

        for (Disassembler.BlockEdge blockEdge : disassembler.getBlockEdges()) {
            out.append(
                """
                b%d -> b%d [label = %s, style = %s, color = %s];
                """
                .formatted(
                    disassembler.findBlockId(blockEdge.from())
                    , disassembler.findBlockId(blockEdge.to())
                    , blockEdge.label()
                    , blockEdge.edgeType().style()
                    , blockEdge.edgeType().color()
                )
            );
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

        out.append(
            """
            }
            """
        );
    }

    private static void writeBlock(Appendable out, Disassembler.BlockInfo block) {
        try {
            out.append(
                """
                b%d [
                shape = plaintext
                label = <
                <table border="0" cellborder="1" cellspacing="0">
                """
                .formatted(block.id())
            );

            final List<String> lines = block.lines();
            for (int i = 0; i < lines.size(); i++) {
                final String line = formatLine(lines.get(i));
                final String lineColor = block.lineColors().get(i);

                out.append(
                    """
                    <tr><td align="text" port="%d" bgcolor="%s">%s<br align="left"/></td></tr>
                    """
                    .formatted(i, Objects.nonNull(lineColor) ? lineColor : "white", line)
                );
            }

            out.append(
                """
                </table>
                >
                ]
                """
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String formatLine(String line) {
        String escaped = line
            .replaceAll(">", "&gt;")
            .replaceAll("<", "&lt;")
            .replaceAll("&", "&amp;");

        // Add extra space for proper formatting
        return escaped + "&nbsp;".repeat(line.length() / 10);
    }
}
