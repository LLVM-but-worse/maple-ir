package org.mapleir.ir.antlr.internallex;

public enum TokenKind {
    FLOATLIT, DOUBLELIT, LONGLIT, INTLIT, CHARLIT(false), STRLIT(false), TYPELIT(false);

    private final boolean numeric;

    private TokenKind() {
        this(true);
    }

    private TokenKind(boolean numeric) {
        this.numeric = numeric;
    }

    public boolean isNumeric() {
        return numeric;
    }
}
