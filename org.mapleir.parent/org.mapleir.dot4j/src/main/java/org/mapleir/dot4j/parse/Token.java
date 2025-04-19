package org.mapleir.dot4j.parse;

final class Token {
    public static final int
            EOF = 0,
            SEMICOLON = 1,
            COMMA = 2,
            BRACE_OPEN = 3,
            BRACE_CLOSE = 4,
            EQUAL = 5,
            BRACKET_OPEN = 6,
            BRACKET_CLOSE = 7,
            COLON = 8,
            STRICT = 9,
            GRAPH = 10,
            DIGRAPH = 11,
            NODE = 12,
            EDGE = 13,
            SUBGRAPH = 14,
            ID = 16,
            MINUS_MINUS = 18,
            ARROW = 19,
            SUB_SIMPLE = 1,
            SUB_NUMERAL = 2,
            SUB_QUOTED = 3,
            SUB_HTML = 4;
    public final int type;
    public final int subtype;
    public final String value;

    public Token(int type, String value) {
        this(type, -1, value);
    }

    public Token(int type, char value) {
        this(type, -1, Character.toString(value));
    }

    public Token(int type, int subtype, String value) {
        this.type = type;
        this.subtype = subtype;
        this.value = value;
    }

    public static String desc(int type) {
        switch (type) {
            case ID:
                return "identifier";
            case EQUAL:
                return "=";
            case EOF:
                return "end of file";
            case BRACKET_OPEN:
                return "[";
            case BRACKET_CLOSE:
                return "]";
            case BRACE_OPEN:
                return "{";
            case BRACE_CLOSE:
                return "}";
            default:
                return "";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final Token token = (Token) o;

        if (type != token.type) {
            return false;
        }
        if (subtype != token.subtype) {
            return false;
        }
        return value.equals(token.value);

    }

    @Override
    public int hashCode() {
        int result = type;
        result = 31 * result + subtype;
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return type + (subtype >= 0 ? "(" + subtype + ")" : "") + "`" + value + "`";
    }
}
