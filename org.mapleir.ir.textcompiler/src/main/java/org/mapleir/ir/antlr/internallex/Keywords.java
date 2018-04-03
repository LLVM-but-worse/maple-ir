package org.mapleir.ir.antlr.internallex;

public class Keywords {

	private final Name.Table names;
	private final Token[] key;
	private int maxKey = 0;
	private Name[] tokenName = new Name[Token.values().length];

	protected Keywords(Name.Table names) {
		this.names = names;

		for (Token t : Token.values()) {
			if (t.name != null) {
				enterKeyword(t.name, t);
			} else {
				tokenName[t.ordinal()] = null;
			}
		}

		key = new Token[maxKey + 1];
		for (int i = 0; i <= maxKey; i++) {
			key[i] = Token.IDENTIFIER;
		}
		for (Token t : Token.values()) {
			if (t.name != null) {
				key[tokenName[t.ordinal()].index] = t;
			}
		}
	}

	private void enterKeyword(String s, Token token) {
		Name n = names.fromString(s);
		tokenName[token.ordinal()] = n;
		if (n.index > maxKey) {
			maxKey = n.index;
		}
	}
	
    public Token key(Name name) {
        return (name.index > maxKey) ? Token.IDENTIFIER : key[name.index];
    }
}
