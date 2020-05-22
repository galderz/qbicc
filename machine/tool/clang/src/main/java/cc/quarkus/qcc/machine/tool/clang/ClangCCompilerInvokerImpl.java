package cc.quarkus.qcc.machine.tool.clang;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cc.quarkus.qcc.machine.tool.process.InputSource;
import io.smallrye.common.constraint.Assert;

/**
 *
 */
final class ClangCCompilerInvokerImpl extends AbstractClangInvoker implements ClangCCompilerInvoker {
    private final List<Path> includePaths = new ArrayList<>(1);
    private final List<String> definedSymbols = new ArrayList<>(2);
    private InputSource inputSource = InputSource.empty();
    private Path outputPath = TMP.resolve("qcc-output." + getTool().getPlatform().getObjectType().objectSuffix());

    ClangCCompilerInvokerImpl(final ClangCCompilerImpl tool) {
        super(tool);
    }

    public void addIncludePath(final Path path) {
        includePaths.add(Assert.checkNotNullParam("path", path));
    }

    public int getIncludePathCount() {
        return includePaths.size();
    }

    public Path getIncludePath(final int index) throws IndexOutOfBoundsException {
        return includePaths.get(index);
    }

    public void addDefinedSymbol(final String name, final String value) {
        Assert.checkNotNullParam("name", name);
        Assert.checkNotNullParam("value", value);
        definedSymbols.add(name);
        definedSymbols.add(value);
    }

    public int getDefinedSymbolCount() {
        return definedSymbols.size() >>> 1;
    }

    public String getDefinedSymbol(final int index) throws IndexOutOfBoundsException {
        return definedSymbols.get(index << 1);
    }

    public String getDefinedSymbolValue(final int index) throws IndexOutOfBoundsException {
        return definedSymbols.get((index << 1) + 1);
    }

    public void setSource(final InputSource source) {
        inputSource = Assert.checkNotNullParam("source", source);
    }

    public InputSource getSource() {
        return inputSource;
    }

    public void setOutputPath(final Path path) {
        outputPath = Assert.checkNotNullParam("path", path);
    }

    public Path getOutputPath() throws IllegalArgumentException {
        return outputPath;
    }

    void addArguments(final List<String> cmd) {
        Collections.addAll(cmd, "-std=gnu11", "-f" + "input-charset=UTF-8", "-pipe");
        for (Path includePath : includePaths) {
            cmd.add("-I" + includePath.toString());
        }
        for (int i = 0; i < definedSymbols.size(); i += 2) {
            String key = definedSymbols.get(i);
            String val = definedSymbols.get(i + 1);
            if (val.equals("1")) {
                cmd.add("-D" + key);
            } else {
                cmd.add("-D" + key + "=" + val);
            }
        }
        Collections.addAll(cmd, "-c", "-x", "c", "-o", getOutputPath().toString(), "-");
    }
}
