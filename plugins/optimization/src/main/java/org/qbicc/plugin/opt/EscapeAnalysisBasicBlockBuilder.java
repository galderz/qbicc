package org.qbicc.plugin.opt;

import org.qbicc.context.CompilationContext;
import org.qbicc.graph.BasicBlockBuilder;
import org.qbicc.graph.ConstructorElementHandle;
import org.qbicc.graph.DelegatingBasicBlockBuilder;
import org.qbicc.graph.MemoryAtomicityMode;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.ParameterValue;
import org.qbicc.graph.ReferenceHandle;
import org.qbicc.graph.StaticField;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.type.definition.element.FieldElement;

import java.util.List;

public class EscapeAnalysisBasicBlockBuilder extends DelegatingBasicBlockBuilder {
    private final CompilationContext ctxt;

    public EscapeAnalysisBasicBlockBuilder(final CompilationContext ctxt, final BasicBlockBuilder delegate) {
        super(delegate);
        this.ctxt = ctxt;
    }

    @Override
    public Value call(ValueHandle target, List<Value> arguments) {
        if (target instanceof ConstructorElementHandle && ((ConstructorElementHandle) target).getInstance() instanceof New) {
            return invokeConstructor(target, ((ConstructorElementHandle) target).getInstance(), arguments);
        }

        return super.call(target, arguments);
    }

    /**
     * CG:
     *     new T(...); // <--
     */
    private Value invokeConstructor(ValueHandle handle, Value value, List<Value> arguments) {
        EscapeAnalysis.get(ctxt).newObject(value, arguments, getCurrentElement());
        return super.call(handle, arguments);
    }

    /**
     * CG:
     *     static T a;
     *     a = new T(); // <--
     */
    public Node store(ValueHandle handle, Value value, MemoryAtomicityMode mode) {
        final Node result = super.store(handle, value, mode);

        if (handle instanceof StaticField) {
            EscapeAnalysis.get(ctxt).staticStore(value, getCurrentElement());
        }

        return result;
    }

    /**
     * Workaround lack of local variables.
     *
     * To get represent the GC from 'a' to 'new T(...)',
     * we hijack future 'a' references to fix the pointer.
     * When 'a.x' is accessed, we fix the pointer from 'a' to 'new T(...)'.
     *
     * CG:
     *     T a = new T(...); // <--
     */
    @Override
    public ValueHandle instanceFieldOf(ValueHandle handle, FieldElement field) {
        final ValueHandle result = super.instanceFieldOf(handle, field);

        if (handle instanceof ReferenceHandle) {
            final ReferenceHandle refHandle = (ReferenceHandle) handle;
            EscapeAnalysis.get(ctxt).fixPointsToIfNeeded(refHandle.getReferenceValue(), handle, getCurrentElement());
        }

        return result;
    }

    @Override
    public void startMethod(List<ParameterValue> arguments) {
        arguments.forEach(this::trackArgument);
        super.startMethod(arguments);
    }

    void trackArgument(ParameterValue argument) {
        final ValueHandle phantomReference = pointerHandle(argument);
        EscapeAnalysis.get(ctxt).methodEntry(argument, phantomReference, getCurrentElement());
    }

    @Override
    public void finish() {
        // TODO: summarise method cg
        super.finish();
    }
}
