package cc.quarkus.qcc.graph;

import cc.quarkus.qcc.type.BooleanType;

/**
 *
 */
public final class CmpLe extends AbstractCmp implements NonCommutativeBinaryValue {
    CmpLe(final Value v1, final Value v2, final BooleanType booleanType) {
        super(v1, v2, booleanType);
    }

    public <T, R> R accept(final ValueVisitor<T, R> visitor, final T param) {
        return visitor.visit(param, this);
    }
}
