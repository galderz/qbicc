package cc.quarkus.qcc.graph.literal;

import cc.quarkus.qcc.constraint.Constraint;
import cc.quarkus.qcc.graph.ValueVisitor;
import cc.quarkus.qcc.type.FloatType;
import cc.quarkus.qcc.type.ValueType;

public final class FloatLiteral extends Literal {
    private final FloatType type;
    private final double value;

    FloatLiteral(final FloatType type, final double value) {
        this.type = type.asConst();
        this.value = value;
    }

    public ValueType getType() {
        return type;
    }

    public Constraint getConstraint() {
        return Constraint.equalTo(this);
    }

    public float floatValue() {
        return (float) value;
    }

    public double doubleValue() {
        return value;
    }

    public boolean equals(final Literal other) {
        return other instanceof FloatLiteral && equals((FloatLiteral) other);
    }

    public boolean equals(final FloatLiteral other) {
        return this == other || other != null && Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(other.value) && type.equals(other.type);
    }

    public <T, R> R accept(final ValueVisitor<T, R> visitor, final T param) {
        return visitor.visit(param, this);
    }
}
