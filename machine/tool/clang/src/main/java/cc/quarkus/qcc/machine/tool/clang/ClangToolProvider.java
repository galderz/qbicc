package cc.quarkus.qcc.machine.tool.clang;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.quarkus.qcc.machine.arch.Platform;
import cc.quarkus.qcc.machine.tool.Tool;
import cc.quarkus.qcc.machine.tool.ToolProvider;
import cc.quarkus.qcc.machine.tool.ToolUtil;
import cc.quarkus.qcc.machine.tool.process.InputSource;
import cc.quarkus.qcc.machine.tool.process.OutputDestination;

/**
 *
 */
public class ClangToolProvider implements ToolProvider {
    public <T extends Tool> Iterable<T> findTools(final Class<T> type, final Platform platform) {
        final ArrayList<T> list = new ArrayList<>();
        if (type.isAssignableFrom(ClangCCompilerImpl.class)) {
            for (String name : List.of("clang", "cc", "gcc")) {
                tryOne(type, platform, list, name);
            }
        }
        return list;
    }

    static final Pattern VERSION_PATTERN = Pattern.compile("^(?:clang|Apple LLVM) version (\\S+)");

    private <T extends Tool> void tryOne(final Class<T> type, final Platform platform, final ArrayList<T> list, final String name) {
        final Path path = ToolUtil.findExecutable(name);
        if (path != null && Files.isExecutable(path)) {
            class Result {
                String version;
                boolean match;
            }
            Result res = new Result();
            OutputDestination dest = OutputDestination.of(r -> {
                try (BufferedReader br = new BufferedReader(r)) {
                    String line;
                    Matcher matcher;
                    while ((line = br.readLine()) != null) {
                        matcher = VERSION_PATTERN.matcher(line);
                        if (matcher.find()) {
                            res.version = matcher.group(1);
                            res.match = true;
                        }
                    }
                }
            }, StandardCharsets.UTF_8);
            ProcessBuilder pb = new ProcessBuilder(path.toString(), "-###");
            try {
                InputSource.empty().transferTo(OutputDestination.of(pb, dest, OutputDestination.discarding()));
            } catch (IOException e) {
                // ignore invalid compiler
                return;
            }
            if (res.match) {
                final ClangCCompilerImpl cc = new ClangCCompilerImpl(path, platform, res.version);
                list.add(type.cast(cc));
            }
        }
    }
}
