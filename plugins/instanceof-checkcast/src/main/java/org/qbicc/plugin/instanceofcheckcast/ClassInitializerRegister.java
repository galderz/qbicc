package org.qbicc.plugin.instanceofcheckcast;

import java.util.function.Consumer;

import org.qbicc.context.ClassContext;
import org.qbicc.context.CompilationContext;
import org.qbicc.plugin.reachability.RTAInfo;
import org.qbicc.type.definition.DefinedTypeDefinition;
import org.qbicc.type.definition.LoadedTypeDefinition;
import org.qbicc.type.definition.element.InitializerElement;

/**
 * Register all reachable class's class initalizer methods as entrypoints
 * so they survive to be lowered into functions.
 * This is required as we lose info after ReachabilityBlockBuilder required
 * to otherwise keep them alive.
 * 
 * Eventually, this will need to skip an class initializer that was run as
 * part of the build process.
 */
public class ClassInitializerRegister implements Consumer<CompilationContext>  {

    @Override
    public void accept(CompilationContext ctxt) {
        RTAInfo rtaInfo = RTAInfo.get(ctxt);

        // Code below uses #hasMethodBody() to filter out the empty
        // class initializers and to avoid processing internal arrays
        rtaInfo.visitInitializedTypes(sc -> {
            InitializerElement initializer = sc.getInitializer();
            if (initializer != null && initializer.hasMethodBody()) {
                ctxt.registerEntryPoint(initializer);
            }
        });
    }
}