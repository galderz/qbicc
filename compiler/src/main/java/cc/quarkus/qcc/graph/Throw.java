package cc.quarkus.qcc.graph;

/**
 * An instruction which throws an exception <em>to the caller</em>.  To throw an exception to a catch block,
 * use {@link Goto}.
 */
public final class Throw extends AbstractNode implements Terminator {
    private final Node dependency;
    private final Value thrownValue;

    Throw(final int line, final int bci, final Node dependency, final Value thrownValue) {
        super(line, bci);
        this.dependency = dependency;
        this.thrownValue = thrownValue;
    }

    public Value getThrownValue() {
        return thrownValue;
    }

    public int getValueDependencyCount() {
        return 1;
    }

    public Value getValueDependency(int index) throws IndexOutOfBoundsException {
        return index == 0 ? thrownValue : Util.throwIndexOutOfBounds(index);
    }

    public int getBasicDependencyCount() {
        return 1;
    }

    public Node getBasicDependency(final int index) throws IndexOutOfBoundsException {
        return index == 0 ? dependency : Util.throwIndexOutOfBounds(index);
    }

    public <T, R> R accept(final TerminatorVisitor<T, R> visitor, final T param) {
        return visitor.visit(param, this);
    }
}