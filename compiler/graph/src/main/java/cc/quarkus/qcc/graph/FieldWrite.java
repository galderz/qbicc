package cc.quarkus.qcc.graph;

/**
 * A field write.
 */
public interface FieldWrite extends FieldOperation {
    Value getWriteValue();
    void setWriteValue(Value value);
}
