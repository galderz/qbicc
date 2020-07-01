package cc.quarkus.qcc.graph;

final class ConstantValue32 extends ValueImpl implements ConstantValue {
    private final int value;
    private final Type type;

    ConstantValue32(final int value, final Type type) {
        this.value = value;
        this.type = type;
    }

    int getValue() {
        return value;
    }

    public String getLabelForGraph() {
        return "Int:" + value;
    }

    public Type getType() {
        return type;
    }

    public long longValue() {
        return type instanceof SignedIntegerType ? value : value & 0xFFFF_FFFFL;
    }

    public int intValue() {
        return value;
    }

    public short shortValue() {
        return (short) value;
    }

    public byte byteValue() {
        return (byte) value;
    }

    public char charValue() {
        return (char) value;
    }

    public boolean isZero() {
        return type.isZero(value);
    }

    public boolean isOne() {
        return type.isOne(value);
    }

    public boolean isNegative() {
        return type instanceof ComparableWordType && ((ComparableWordType) type).isNegative(value);
    }

    public boolean isNotNegative() {
        return type instanceof ComparableWordType && ((ComparableWordType) type).isNotNegative(value);
    }

    public boolean isPositive() {
        return type instanceof ComparableWordType && ((ComparableWordType) type).isPositive(value);
    }

    public boolean isNotPositive() {
        return type instanceof ComparableWordType && ((ComparableWordType) type).isNotPositive(value);
    }

    public ConstantValue withTypeRaw(final Type type) {
        return new ConstantValue32(value, type);
    }

    public int compareTo(final ConstantValue other) throws IllegalArgumentException {
        if (other.getType() != type) {
            throw new IllegalArgumentException("Type mismatch");
        }
        if (type instanceof ComparableWordType) {
            return ((ComparableWordType) type).compare(value, other.intValue());
        } else {
            throw new IllegalArgumentException("Type is not comparable");
        }
    }
}