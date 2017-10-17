grammar mapleir;

compilationUnit
	:	setDirective* classDeclaration EOF
	;

setDirective
	:	SET Identifier setCommandValueList
	;

setCommandValue
	:	(LITERAL|Identifier)
	;

setCommandValueList
	:	setCommandValue (COMMA setCommandValue)*?
	;

classDeclaration
	: CLASS jclass LBRACE setDirective* (declarations)* RBRACE
	;

declarations
	:
	(classDeclaration
	| memberDeclaration
	)
	;

memberDeclaration
	:	fieldDeclaration
	|	methodDeclaration
	;

fieldDeclaration
	:	FIELD desc Identifier (ASSIGN constant)? (LBRACE setDirective* RBRACE)?
	;

methodDeclaration
	:	METHOD methoddesc Identifier LBRACE setDirective* codebody? RBRACE
	;

// this needs to be parsed in the compiler
desc
	:	'['*? jclass ';'?
	;

// this needs to be parsed in the compiler
methoddesc
	// want to match the whole descriptor so we can parse it later
	// as antlr is too crap to handle this nicely. therefore pretty
	// much everything is optional until later.
	:	LPAREN (desc)* RPAREN (~WS|(desc))
	;
	
jclass
	// DIV? because they might end with / and we need it to match
	// catch class names with dots instead of slashes, handle later
	:	(DIV|DOT|Identifier) (DIV|DOT|Identifier)*
	;

codebody
	:	CODE LBRACE block+ RBRACE
	;
	

/* blocks may be inside brackets */
block
	:	Identifier COLON LBRACE statement* RBRACE
	|	Identifier COLON statement*
	;

phiPair
	:	Identifier COLON Identifier
	;
	
statement
	/* Copy statement */
	:	Identifier ASSIGN expr
	|	Identifier ASSIGN PHI LBRACE (phiPair (COMMA phiPair)*)? RBRACE
	/* If statement: .if (x OP Y) .goto B */
	|	IF LPAREN expr (LE|GE|LT|GT|EQ|NOTEQ) expr RPAREN GOTO Identifier
	/* Unconditional jump: .goto B*/
	|	GOTO Identifier
	/* Pop/consume statement: .consume expr */
	|	CONSUME expr
	/* Return statement: .return expr*/
	|   RETURN expr

	/* virtual/static field store statements:
	 *	expr1.f = expr2
	 *	Klass.f = expr */
	|
		(	expr DOT Identifier ASSIGN expr
		|	jclass DOT Identifier ASSIGN expr
		)

	/* Throw statement: .throw expr*/
	|	THROW expr
	/* Monitor state statements:
			.monitor_enter expr
			.monitor_exit expr */
	|	MONENTER LPAREN expr
	|	MONEXIT  expr
	
	/* Array store statement:
	 *	expr1[expr2] = expr3 */
	|	expr LBRACK expr RBRACK ASSIGN expr
	
	/* Switch statement: .switch ((expr))
	 *					 	.case expr: goto B
	 *		 				.default: goto B
	 *					 .end
	 */
	|	SWITCH LPAREN expr RPAREN LBRACE switchCaseStatement* RBRACE
	;

switchCaseStatement
	:	CASE expr COLON GOTO Identifier
	|	DEFAULT COLON GOTO Identifier
	;

constant
	:	LITERAL
	;

exprlist
	:	expr (COMMA expr)*
	;

expr
	:	primary
	/* Field get/ array length*/
	|	expr DOT Identifier
	/* Array get */
	|	expr LBRACK expr RBRACK
	/* alloc/new obj/array*/
	|	NEW creator
	|	CMP arguments
	|	LPAREN jclass RPAREN expr
	/* invokes */
	|
	(	Identifier DOT Identifier arguments
	|	jclass DOT Identifier arguments
	)
	|	TILDE expr
	|	(ADD|SUB) expr // neg/pos
	|	expr (MUL|DIV|MOD|ADD|SUB) expr
	|	expr (LT LT |GT GT GT| GT GT) expr
	|	expr (BITAND|CARET|BITOR) expr
	|	expr INSTANCEOF jclass
	|	CATCH
	;
	
arguments
	:	LPAREN exprlist? RPAREN
	;

creator
	:	jclass
	|	jclass (arguments | arrayCreator)
	|	
	;

arrayCreator
	:	LBRACK
	( expr RBRACK (LBRACK expr RBRACK)* (LBRACK RBRACK)*
	)
	;
	
primary
	: LITERAL
	| Identifier
	| LPAREN expr RPAREN
	;

FIELD	:	'.field' ;
METHOD  :	'.method' ;

SET		:	'.set' ;

CLASS	:	'.class' ;
CODE 	:	'.code' ;

CONSUME :	'.consume' ;
IF 		:	'.if' ;
GOTO 	:	'.goto' ;
SWITCH	:	'.switch' ;
CASE	:	'.case' ;
DEFAULT	:	'.default' ;
RETURN	:	'.return' ;
THROW	:	'.throw' ;
MONENTER:	'.monitor_enter' ;
MONEXIT :	'.monitor_exit' ;

NEW		:	'.new' ;
INSTANCEOF:	'.instanceof' ;
CATCH	:	'.catch' ;
CMP		:	'.compare' ;
PHI		:	'\u0278' ;

LITERAL
    :   IntegerLiteral
    |   FloatingPointLiteral
    |   CharacterLiteral
    |   StringLiteral
    |   BooleanLiteral
    |   'null'
	;
    
// §3.10.1 Integer Literals

IntegerLiteral
    :   DecimalIntegerLiteral
    |   HexIntegerLiteral
    |   OctalIntegerLiteral
    |   BinaryIntegerLiteral
    ;

fragment
DecimalIntegerLiteral
    :   DecimalNumeral IntegerTypeSuffix?
    ;

fragment
HexIntegerLiteral
    :   HexNumeral IntegerTypeSuffix?
    ;

fragment
OctalIntegerLiteral
    :   OctalNumeral IntegerTypeSuffix?
    ;

fragment
BinaryIntegerLiteral
    :   BinaryNumeral IntegerTypeSuffix?
    ;

fragment
IntegerTypeSuffix
    :   [lL]
    ;

fragment
DecimalNumeral
    :   '0'
    |   NonZeroDigit (Digits? | Underscores Digits)
    ;

fragment
Digits
    :   Digit (DigitOrUnderscore* Digit)?
    ;

fragment
Digit
    :   '0'
    |   NonZeroDigit
    ;

fragment
NonZeroDigit
    :   [1-9]
    ;

fragment
DigitOrUnderscore
    :   Digit
    |   '_'
    ;

fragment
Underscores
    :   '_'+
    ;

fragment
HexNumeral
    :   '0' [xX] HexDigits
    ;

fragment
HexDigits
    :   HexDigit (HexDigitOrUnderscore* HexDigit)?
    ;

fragment
HexDigit
    :   [0-9a-fA-F]
    ;

fragment
HexDigitOrUnderscore
    :   HexDigit
    |   '_'
    ;

fragment
OctalNumeral
    :   '0' Underscores? OctalDigits
    ;

fragment
OctalDigits
    :   OctalDigit (OctalDigitOrUnderscore* OctalDigit)?
    ;

fragment
OctalDigit
    :   [0-7]
    ;

fragment
OctalDigitOrUnderscore
    :   OctalDigit
    |   '_'
    ;

fragment
BinaryNumeral
    :   '0' [bB] BinaryDigits
    ;

fragment
BinaryDigits
    :   BinaryDigit (BinaryDigitOrUnderscore* BinaryDigit)?
    ;

fragment
BinaryDigit
    :   [01]
    ;

fragment
BinaryDigitOrUnderscore
    :   BinaryDigit
    |   '_'
;

FloatingPointLiteral
    :   DecimalFloatingPointLiteral
    |   HexadecimalFloatingPointLiteral
    ;

fragment
DecimalFloatingPointLiteral
    :   Digits '.' Digits? ExponentPart? FloatTypeSuffix?
    |   '.' Digits ExponentPart? FloatTypeSuffix?
    |   Digits ExponentPart FloatTypeSuffix?
    |   Digits FloatTypeSuffix
    ;

fragment
ExponentPart
    :   ExponentIndicator SignedInteger
    ;

fragment
ExponentIndicator
    :   [eE]
    ;

fragment
SignedInteger
    :   Sign? Digits
    ;

fragment
Sign
    :   [+-]
    ;

fragment
FloatTypeSuffix
    :   [fFdD]
    ;

fragment
HexadecimalFloatingPointLiteral
    :   HexSignificand BinaryExponent FloatTypeSuffix?
    ;

fragment
HexSignificand
    :   HexNumeral '.'?
    |   '0' [xX] HexDigits? '.' HexDigits
    ;

fragment
BinaryExponent
    :   BinaryExponentIndicator SignedInteger
    ;

fragment
BinaryExponentIndicator
    :   [pP]
;

// §3.10.3 Boolean Literals

BooleanLiteral
	:	'true'
	|	'false'
	;


// §3.10.4 Character Literals

CharacterLiteral
	:	'\'' SingleCharacter '\''
	|	'\'' EscapeSequence '\''
	;

fragment
SingleCharacter
	:	~['\\\r\n]
	;

// §3.10.5 String Literals

StringLiteral
	:	'"' StringCharacters? '"'
	;
fragment
StringCharacters
	:	StringCharacter+
	;
fragment
StringCharacter
	:	~["\\\r\n]
	|	EscapeSequence
	;


// §3.10.6 Escape Sequences for Character and String Literals
fragment
EscapeSequence
	:	'\\' [btnfr"'\\]
	|	OctalEscape
    |   UnicodeEscape // This is not in the spec but prevents having to preprocess the input
	;

fragment
OctalEscape
	:	'\\' OctalDigit
	|	'\\' OctalDigit OctalDigit
	|	'\\' ZeroToThree OctalDigit OctalDigit
	;

fragment
ZeroToThree
	:	[0-3]
;

// This is not in the spec but prevents having to preprocess the input

fragment
UnicodeEscape
    :   '\\' 'u' HexDigit HexDigit HexDigit HexDigit
;

// The Null Literal

NullLiteral
	:	'null'
	;

// Separators

LPAREN : '(';
RPAREN : ')';
LBRACE : '{';
RBRACE : '}';
LBRACK : '[';
RBRACK : ']';
SEMI : ';';
COMMA : ',';
DOT : '.';

// Operators, subset of Java operators

ASSIGN : '=';
GT : '>';
LT : '<';
BANG : '!';
TILDE : '~';
COLON : ':';
EQ : '==';
LE : '<=';
GE : '>=';
NOTEQ : '!=';
ADD : '+';
SUB : '-';
MUL : '*';
DIV : '/';
BITAND : '&';
BITOR : '|';
CARET : '^';
MOD : '%';

// Identifers after keywords and numbers
	 
Identifier
    :  JavaLetter JavaLetterOrDigit*
    ;
	
fragment
JavaLetter
    :   [a-zA-Z$_] // these are the "java letters" below 0x7F
    |   // covers all characters above 0x7F which are not a surrogate
        ~[\u0000-\u007F\uD800-\uDBFF]
    |   // covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
        [\uD800-\uDBFF] [\uDC00-\uDFFF]
    ;

fragment
JavaLetterOrDigit
    :   [a-zA-Z0-9$_] // these are the "java letters or digits" below 0x7F
    |   // covers all characters above 0x7F which are not a surrogate
        ~[\u0000-\u007F\uD800-\uDBFF]
    |   // covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
        [\uD800-\uDBFF] [\uDC00-\uDFFF]
	;

WS     : [ \n\t\r]+ -> skip;

COMMENT
    :   '/*' .*? '*/' -> channel(2)
    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> channel(2)
	;