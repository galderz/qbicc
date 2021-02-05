package cc.quarkus.qcc.type;

import java.util.Objects;

/**
 * An object type whose elements are primitive type values.
 */
public final class PrimitiveArrayObjectType extends ArrayObjectType {
    private final WordType elementType;

    PrimitiveArrayObjectType(final TypeSystem typeSystem, final boolean const_, final ClassObjectType objectClass, final WordType elementType) {
        super(typeSystem, Objects.hash(elementType), const_, objectClass);
        this.elementType = elementType;
    }

    PrimitiveArrayObjectType constructConst() {
        return new PrimitiveArrayObjectType(typeSystem, true, getSuperClassType(), elementType);
    }

    public long getSize() throws IllegalStateException {
        return 0;
    }

    public boolean isSubtypeOf(final ObjectType other) {
        return super.isSubtypeOf(other)
            || other instanceof PrimitiveArrayObjectType && isSubtypeOf((PrimitiveArrayObjectType) other);
    }

    public boolean isSubtypeOf(final PrimitiveArrayObjectType other) {
        return this == other;
    }

    public WordType getElementType() {
        return elementType;
    }

    public ObjectType getCommonSupertype(final ObjectType other) {
        return equals(other) ? this : super.getCommonSupertype(other);
    }

    public StringBuilder toFriendlyString(final StringBuilder b) {
        return elementType.toFriendlyString(b.append("prim_array").append('.'));
    }
}
