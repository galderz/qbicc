package cc.quarkus.qcc.constraint;

import cc.quarkus.qcc.graph.Type;
import cc.quarkus.qcc.graph.Value;

public interface SymbolicValue extends Value {
    default Type getType() {
        return null;
    }
}