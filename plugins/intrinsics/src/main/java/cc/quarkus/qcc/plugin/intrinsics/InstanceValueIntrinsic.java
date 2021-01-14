package cc.quarkus.qcc.plugin.intrinsics;

import java.util.List;

import cc.quarkus.qcc.graph.BasicBlockBuilder;
import cc.quarkus.qcc.graph.DispatchInvocation;
import cc.quarkus.qcc.graph.Value;
import cc.quarkus.qcc.type.descriptor.MethodDescriptor;
import cc.quarkus.qcc.type.descriptor.TypeDescriptor;

/**
 * An instance intrinsic method which returns a value.
 */
public interface InstanceValueIntrinsic {
    //TODO: There's another form of invokeValueInstance that isn't covered by this substitution.

    Value emitIntrinsic(BasicBlockBuilder builder, DispatchInvocation.Kind kind, Value instance, TypeDescriptor owner, String name, MethodDescriptor descriptor, List<Value> arguments);
}
