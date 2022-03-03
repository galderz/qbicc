package org.qbicc.plugin.dot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.qbicc.context.CompilationContext;
import org.qbicc.context.Diagnostic;
import org.qbicc.driver.GraphGenConfig;
import org.qbicc.driver.GraphGenFilter;
import org.qbicc.driver.Phase;
import org.qbicc.graph.Action;
import org.qbicc.graph.BasicBlock;
import org.qbicc.graph.Node;
import org.qbicc.graph.NodeVisitor;
import org.qbicc.graph.Terminator;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.object.Function;
import org.qbicc.object.ProgramModule;
import org.qbicc.object.ProgramObject;
import org.qbicc.object.Section;
import org.qbicc.type.definition.DefinedTypeDefinition;
import org.qbicc.type.definition.MethodBody;
import org.qbicc.type.definition.classfile.ClassFile;
import org.qbicc.type.definition.element.BasicElement;
import org.qbicc.type.definition.element.ElementVisitor;
import org.qbicc.type.definition.element.ExecutableElement;

/**
 *
 */
public class DotGenerator implements ElementVisitor<CompilationContext, Void>, Consumer<CompilationContext> {
    private static final Logger log = Logger.getLogger("org.qbicc.plugin.dot");

    private final Phase phase;
    private final String name;
    private final GraphGenFilter filter;
    private final List<BiFunction<DotGenerationContext, NodeVisitor<Appendable, String, String, String, String>, NodeVisitor<Appendable, String, String, String, String>>> visitorFactories = new ArrayList<>();

    public DotGenerator(Phase p, GraphGenConfig graphGenConfig) {
        this(p, p.toString(), graphGenConfig);
    }

    public DotGenerator(Phase p, String name, GraphGenConfig graphGenConfig) {
        this.phase = p;
        this.name = name;
        if (graphGenConfig != null) {
            filter = graphGenConfig.getFilter();
        } else {
            filter = null;
        }
    }

    public DotGenerator addVisitorFactory(BiFunction<DotGenerationContext, NodeVisitor<Appendable, String, String, String, String>, NodeVisitor<Appendable, String, String, String, String>> factory) {
        visitorFactories.add(factory);
        return this;
    }

    static final class Producer {
        private final Iterator<ProgramModule> pmIter;
        private Iterator<Section> sectionIter;
        private Iterator<ProgramObject> fnIter;

        Producer(CompilationContext ctxt) {
            pmIter = ctxt.getAllProgramModules().iterator();
        }

        Function next() {
            synchronized (this) {
                ProgramObject item;
                do {
                    while (fnIter == null || ! fnIter.hasNext()) {
                        while (sectionIter == null || ! sectionIter.hasNext()) {
                            if (! pmIter.hasNext()) {
                                return null;
                            }
                            sectionIter = pmIter.next().sections().iterator();
                        }
                        fnIter = sectionIter.next().contents().iterator();
                    }
                    item = fnIter.next();
                } while (! (item instanceof Function));
                return (Function) item;
            }
        }
    }

    public void accept(final CompilationContext compilationContext) {
        Producer producer = new Producer(compilationContext);
        compilationContext.runParallelTask(ctxt -> {
            Function fn;
            for (;;) {
                fn = producer.next();
                if (fn == null) {
                    return;
                }
                ExecutableElement element = fn.getOriginalElement();
                MethodBody body = fn.getBody();
                if (body != null && filter != null && filter.accept(element, phase)) {
                    process(element, body);
                }
            }
        });
    }

    public Void visitUnknown(final CompilationContext param, final BasicElement basicElement) {
        if (basicElement instanceof ExecutableElement) {
            ExecutableElement element = (ExecutableElement) basicElement;
            if (element.hasMethodBody()) {
                MethodBody methodBody = element.getMethodBody();
                if (filter != null && filter.accept(element, phase)) {
                    process(element, methodBody);
                }
            }
        }
        return null;
    }

    private void process(final ExecutableElement element, MethodBody methodBody) {
        if (element.hasAllModifiersOf(ClassFile.ACC_ABSTRACT)) return;
        DefinedTypeDefinition def = element.getEnclosingType();
        CompilationContext ctxt = def.getContext().getCompilationContext();
        BasicBlock entryBlock = methodBody.getEntryBlock();
        Path dir = ctxt.getOutputDirectory(element);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            failedToWrite(ctxt, dir, e);
            return;
        }
        Path path = dir.resolve(name + ".dot");
        try {
            try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                bw.write("digraph {");
                bw.newLine();
                bw.write("graph [ rankdir = BT ];");
                bw.newLine();
                bw.write("edge [ splines = true ];");
                bw.newLine();
                bw.newLine();
                Map<Node, String> visited = new HashMap<>();
                var decorators = constructDecorators();
                final Terminus terminus = new Terminus(visited);
                final DotGenerationContext dctxt = new DotGenerationContext(ctxt, element, visited);
                DotNodeVisitor visitor = new DotNodeVisitor(entryBlock, visited, decorators.apply(dctxt, terminus));
                // DotNodeVisitor visitor = new DotNodeVisitor(entryBlock, visited, decorators.apply(dctxt, terminus));
                dctxt.dotNodeVisitor = visitor;
                visitor.process(bw);
                bw.write("}");
            } catch (IOException e) {
                failedToWrite(ctxt, path, e);
            } catch (UncheckedIOException e) {
                IOException cause = e.getCause();
                failedToWrite(ctxt, path, cause);
            } catch (TooBigException e) {
                log.debugf("Element \"%s\" is too big to graph", element);
                throw e;
            }
        } catch (TooBigException e) {
            try {
                // Some operating systems will not let you delete a file while it is open.
                // So, if the graph is too big, attempt to delete the file when the original file has been closed.
                Files.delete(path);
            } catch (IOException ex) {
                // Ignore
            }
        }
    }

    private static Diagnostic failedToWrite(final CompilationContext ctxt, final Path path, final IOException cause) {
        return ctxt.warning("Failed to write \"%s\": %s", path, cause);
    }

    private BiFunction<DotGenerationContext, NodeVisitor<Appendable, String, String, String, String>, NodeVisitor<Appendable, String, String, String, String>> constructDecorators() {
        if (visitorFactories.isEmpty()) {
            return (dtxt, v) -> v;
        }
        if (visitorFactories.size() == 1) {
            return visitorFactories.get(0);
        }
        // `var` because the type is absurdly long
        var copy = new ArrayList<>(visitorFactories);
        Collections.reverse(copy);
        return (dtxt, v) -> {
            // `var` because the type is absurdly long
            for (var fn : copy) {
                v = fn.apply(dtxt, v);
            }
            return v;
        };
    }

    private static final class Terminus implements NodeVisitor<Appendable, String, String, String, String>
    {
        private final Map<Node, String> visited;

        private Terminus(Map<Node, String> visited) {
            this.visited = visited;
        }

        @Override
        public String visitUnknown(Appendable param, Action node) {
            return visited.get(node);
        }

        @Override
        public String visitUnknown(Appendable param, Terminator node) {
            return visited.get(node);
        }

        @Override
        public String visitUnknown(Appendable param, ValueHandle node) {
            return visited.get(node);
        }

        @Override
        public String visitUnknown(Appendable param, Value node) {
            return visited.get(node);
        }
    }
}
