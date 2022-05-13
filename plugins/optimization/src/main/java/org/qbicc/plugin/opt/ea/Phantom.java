package org.qbicc.plugin.opt.ea;

import org.qbicc.graph.Node;
import org.qbicc.type.definition.element.ExecutableElement;

final class Phantom implements Node {
    final String name;

    Phantom(String name) {
        this.name = name;
    }

    @Override
    public Node getCallSite() {
        return null;  // TODO: Customise this generated block
    }

    @Override
    public ExecutableElement getElement() {
        return null;  // TODO: Customise this generated block
    }

    @Override
    public int getSourceLine() {
        return 0;  // TODO: Customise this generated block
    }

    @Override
    public int getBytecodeIndex() {
        return 0;  // TODO: Customise this generated block
    }

    @Override
    public StringBuilder toString(StringBuilder b) {
        return null;  // TODO: Customise this generated block
    }

    @Override
    public String toString() {
        return "Phantom=" + name + "@" + Integer.toHexString(this.hashCode());
    }
}
