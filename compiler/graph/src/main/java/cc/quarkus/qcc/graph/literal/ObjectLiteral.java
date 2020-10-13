package cc.quarkus.qcc.graph.literal;

import cc.quarkus.qcc.constraint.Constraint;
import cc.quarkus.qcc.graph.ValueVisitor;
import cc.quarkus.qcc.interpreter.JavaObject;
import cc.quarkus.qcc.type.ReferenceType;
import cc.quarkus.qcc.type.ValueType;

/**
 *
 */
public final class ObjectLiteral extends Literal {
    private final ReferenceType type;
    private final JavaObject value;

    ObjectLiteral(final ReferenceType type, final JavaObject value) {
        this.type = type;
        this.value = value;
    }

    public ValueType getType() {
        return type;
    }

    public TypeIdLiteral getObjectTypeId() {
        return value.getObjectType();
    }

    public JavaObject getValue() {
        return value;
    }

    public Constraint getConstraint() {
        return Constraint.equalTo(value.getObjectType());
    }

    public <T, R> R accept(final ValueVisitor<T, R> visitor, final T param) {
        return visitor.visit(param, this);
    }

    public boolean equals(final Literal other) {
        return other instanceof ObjectLiteral && equals((ObjectLiteral) other);
    }

    public boolean equals(final ObjectLiteral other) {
        return this == other || other != null && type.equals(other.type) && value.equals(other.value);
    }
}
