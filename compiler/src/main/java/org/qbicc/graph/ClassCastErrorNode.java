package org.qbicc.graph;

import java.util.Objects;

import org.qbicc.type.definition.element.ExecutableElement;

/**
 *
 */
public final class ClassCastErrorNode extends AbstractTerminator implements Error {
    private final Node dependency;
    private final BasicBlock terminatedBlock;
    private final Value fromType;
    private final Value toType;

    ClassCastErrorNode(final Node callSite, final ExecutableElement element, final int line, final int bci, final BlockEntry blockEntry, final Node dependency, final Value fromType, final Value toType) {
        super(callSite, element, line, bci);
        this.dependency = dependency;
        this.fromType = fromType;
        this.toType = toType;
        terminatedBlock = new BasicBlock(blockEntry, this);
    }

    public BasicBlock getTerminatedBlock() {
        return terminatedBlock;
    }

    public Value getFromType() {
        return fromType;
    }

    public Value getToType() {
        return toType;
    }

    @Override
    public Node getDependency() {
        return dependency;
    }

    public int getValueDependencyCount() {
        return 2;
    }

    public Value getValueDependency(final int index) throws IndexOutOfBoundsException {
        return index == 0 ? fromType : index == 1 ? toType : Util.throwIndexOutOfBounds(index);
    }

    public <T, R> R accept(final TerminatorVisitor<T, R> visitor, final T param) {
        return visitor.visit(param, this);
    }

    int calcHashCode() {
        return Objects.hash(dependency, fromType, toType);
    }

    @Override
    String getNodeName() {
        return "ClassCastError";
    }

    public boolean equals(final Object other) {
        return other instanceof ClassCastErrorNode && equals((ClassCastErrorNode) other);
    }

    public boolean equals(final ClassCastErrorNode other) {
        return this == other || other != null
            && dependency.equals(other.dependency)
            && fromType.equals(other.fromType)
            && toType.equals(other.toType);
    }
}
