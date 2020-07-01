package cc.quarkus.qcc.graph;

import cc.quarkus.qcc.constraint.Constraint;

/**
 * A type representing a string literal, before it is promoted to an object.
 */
public interface StringLiteralType extends Type {
    default boolean isAssignableFrom(Type otherType) {
        return otherType instanceof StringLiteralType;
    }

    default int getParameterCount() {
        return 0;
    }

    default String getParameterName(int index) throws IndexOutOfBoundsException {
        throw new IndexOutOfBoundsException(index);
    }

    default Constraint getParameterConstraint(int index) throws IndexOutOfBoundsException {
        throw new IndexOutOfBoundsException(index);
    }
}