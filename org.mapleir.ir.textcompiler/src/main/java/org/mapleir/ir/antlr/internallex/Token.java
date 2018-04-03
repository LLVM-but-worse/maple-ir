package org.mapleir.ir.antlr.internallex;

public enum Token {
    IDENTIFIER,
    NEWLINE,
    EOF,
    ERROR,
    
    FLOATLIT,
    DOUBLELIT,
    LONGLIT,
    INTLIT,
    CHARLIT,
    STRLIT,
    TYPELIT,
    
    CLASS("kdef"),
    FIELD("fdef"),
    METHOD("mdef"),
    
    CASE("case"),
    DEFAULT("default"),
    GOTO("goto"),
    IF("if"),
    MON_ENTER("montior_enter"),
    MON_EXIT("monitor_exit"),
    RETURN("return"),
    SET("set"),
    SWITCH("switch"),
    THROW("throw"),
    CONSUME("consume"),
    
    DOT("."),
    ELLIPSIS("..."),
    COMMA(","),
    SEMI(";"),
    LPAREN("("),
    RPAREN(")"),
    LBRACKET("["),
    RBRACKET("]"),
    LBRACE("{"),
    RBRACE("}"),
    EQ("="),
    GT(">"),
    LT("<"),
    BANG("!"),
    TILDE("~"),
    QUES("?"),
    COLON(":"),
    EQEQ("=="),
    LTEQ("<="),
    GTEQ(">="),
    BANGEQ("!="),
    AMPAMP("&&"),
    BARBAR("||"),
    PLUSPLUS("++"),
    SUBSUB("--"),
    PLUS("+"),
    SUB("-"),
    STAR("*"),
    SLASH("/"),
    AMP("&"),
    BAR("|"),
    CARET("^"),
    PERCENT("%"),
    LTLT("<<"),
    GTGT(">>"),
    GTGTGT(">>>"),
    PLUSEQ("+="),
    SUBEQ("-="),
    STAREQ("*="),
    SLASHEQ("/="),
    AMPEQ("&="),
    BAREQ("|="),
    CARETEQ("^="),
    PERCENTEQ("%="),
    LTLTEQ("<<="),
    GTGTEQ(">>="),
    GTGTGTEQ(">>>="),
    MONKEYS_AT("@");
	
	public final String name;
	
	Token() {
		this(null);
	}
	
	Token(String name) {
		this.name = name;
	}
}
