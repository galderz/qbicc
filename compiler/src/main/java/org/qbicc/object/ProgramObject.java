package org.qbicc.object;

import org.qbicc.graph.literal.SymbolLiteral;
import org.qbicc.type.ValueType;
import io.smallrye.common.constraint.Assert;

/**
 * An object which will be emitted to the final program.
 */
public abstract class ProgramObject {
    final String name;
    final SymbolLiteral literal;
    volatile Linkage linkage = Linkage.EXTERNAL;
    volatile ThreadLocalMode threadLocalMode;
    volatile int addrspace = 0;

    ProgramObject(final String name, final SymbolLiteral literal) {
        this.name = name;
        this.literal = literal;
    }

    public String getName() {
        return name;
    }

    public SymbolLiteral getLiteral() {
        return literal;
    }

    public ValueType getType() {
        return literal.getType();
    }

    public Linkage getLinkage() {
        return linkage;
    }

    public void setLinkage(final Linkage linkage) {
        this.linkage = Assert.checkNotNullParam("linkage", linkage);
    }

    public ThreadLocalMode getThreadLocalMode() {
        return threadLocalMode;
    }

    public void setThreadLocalMode(ThreadLocalMode threadLocalMode) {
        this.threadLocalMode = threadLocalMode;
    }

    public int getAddrspace() {
        return addrspace;
    }

    public void setAddrspace(int as) {
        this.addrspace = as;
    }
}
