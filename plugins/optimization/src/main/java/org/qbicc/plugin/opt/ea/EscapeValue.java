package org.qbicc.plugin.opt.ea;

enum EscapeValue {

    GLOBAL_ESCAPE, ARG_ESCAPE, NO_ESCAPE;

    boolean isArgEscape() {
        return this == ARG_ESCAPE;
    }

    boolean isGlobalEscape() {
        return this == GLOBAL_ESCAPE;
    }

    boolean isNoEscape() {
        return this == NO_ESCAPE;
    }

    EscapeValue merge(EscapeValue other) {
        if (other.isGlobalEscape())
            return GLOBAL_ESCAPE;

        if (this.isNoEscape())
            return other;

        return this;
    }

    static boolean isNoEscape(EscapeValue escapeState) {
        return escapeState != null && escapeState.isNoEscape();
    }

}
