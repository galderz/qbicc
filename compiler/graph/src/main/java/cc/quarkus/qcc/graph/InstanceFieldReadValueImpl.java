package cc.quarkus.qcc.graph;

import cc.quarkus.qcc.constraint.Constraint;

/**
 *
 */
class InstanceFieldReadValueImpl extends InstanceFieldOperationImpl implements InstanceFieldReadValue {
    Constraint constraint;

    public <P> void accept(GraphVisitor<P> visitor, P param) {
        visitor.visit(param, this);
    }

    public String getLabelForGraph() {
        return "get-instance-field";
    }

    public Constraint getConstraint() {
        return constraint;
    }

    public void setConstraint(final Constraint constraint) {
        this.constraint = constraint;
    }
}
