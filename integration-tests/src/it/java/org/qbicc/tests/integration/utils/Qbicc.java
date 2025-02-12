package org.qbicc.tests.integration.utils;

import org.qbicc.context.DiagnosticContext;
import org.qbicc.main.Main;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.List;

import static org.qbicc.tests.integration.utils.TestConstants.*;

public class Qbicc {
    public static DiagnosticContext build(Path outputPath, Path nativeOutputPath, String mainClass, Logger logger) {
        return Main.builder()
            .addBootModulePaths(List.of(
                Path.of(QBICC_RT_JAVA_BASE_JAR),
                Path.of(QBICC_RT_UNWIND_JAR),
                Path.of(QBICC_RT_POSIX_JAR),
                Path.of(QBICC_RT_LINUX_JAR),
                Path.of(QBICC_RUNTIME_API_JAR),
                Path.of(QBICC_RUNTIME_MAIN_JAR),
                Path.of(QBICC_RUNTIME_NOGC_JAR),
                outputPath))
            .setOutputPath(nativeOutputPath)
            .setDiagnosticsHandler(new QbiccDiagnosticLogger(logger))
            .setMainClass(mainClass)
            .build()
            .call();
    }
}
