package cc.quarkus.qcc.graph;

/**
 *
 */
public interface WordType extends NativeObjectType {
    /**
     * Get the size of this type, in bytes.
     *
     * @return the size
     */
    int getSize();

    ConstantValue bitCast(ConstantValue other);
}