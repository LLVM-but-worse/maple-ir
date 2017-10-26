package org.mapleir.ir.antlr.error;

import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Consumer;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.mapleir.ir.antlr.source.ParseTreeSourcePosition;
import org.mapleir.ir.antlr.source.SourcePosition;
import org.mapleir.ir.antlr.source.TokenSourcePosition;
import org.mapleir.ir.antlr.util.NullCheck;

public class ForwardingErrorReporter implements ErrorReporter {

	private final Deque<SourcePosition> positionStack;
	private final Consumer<CompilationException> exceptionConsumer;
	private final Consumer<CompilationWarning> warningConsumer;

	public ForwardingErrorReporter(Consumer<CompilationException> exceptionConsumer,
			Consumer<CompilationWarning> warningConsumer) {
		this.exceptionConsumer = exceptionConsumer;
		this.warningConsumer = warningConsumer;

		positionStack = new LinkedList<>();
	}

	@Override
	public void pushSourcePosition(SourcePosition pos) {
		synchronized (positionStack) {
			positionStack.push(pos);
		}
	}

	@Override
	public SourcePosition makeSourcePositionOnly(Token token) {
		NullCheck.nonNull(token, "token");

		return new TokenSourcePosition(token.getLine(), token.getCharPositionInLine(), 0, token);
	}

	@Override
	public SourcePosition makeSourcePositionOnly(ParseTree parseTree) {
		NullCheck.nonNull(parseTree, "parseTree");

		Token token;

		if (parseTree instanceof ParserRuleContext) {
			ParserRuleContext parserRuleContext = (ParserRuleContext) parseTree;
			token = parserRuleContext.getStart();
		} else if (parseTree instanceof TerminalNode) {
			TerminalNode terminalNode = (TerminalNode) parseTree;
			token = terminalNode.getSymbol();
		} else {
			throw new UnsupportedOperationException("parseTree with no token");
		}

		return new ParseTreeSourcePosition(token.getLine(), token.getCharPositionInLine(), 0, parseTree);
	}

	@Override
	public SourcePosition newSourcePosition(Token token) {
		NullCheck.nonNull(token, "token");

		SourcePosition pos = makeSourcePositionOnly(token);
		pushSourcePosition(pos);

		return pos;
	}

	@Override
	public SourcePosition newSourcePosition(ParseTree parseTree) {
		NullCheck.nonNull(parseTree, "parseTree");

		SourcePosition pos = makeSourcePositionOnly(parseTree);
		pushSourcePosition(pos);

		return pos;

	}

	@Override
	public SourcePosition newSourcePosition(int charOffset) {
		synchronized (positionStack) {
			SourcePosition cur = positionStack.peek();
			SourcePosition newPos = cur.clone(cur.line, cur.column, charOffset);

			positionStack.push(newPos);
			return newPos;
		}
	}

	@Override
	public void popSourcePosition(SourcePosition expected) {
		synchronized (expected) {
			if (positionStack.isEmpty()) {
				throw new IllegalStateException("Empty tokenstack on exit");
			} else {
				if (!positionStack.pop().equals(expected)) {
					throw new IllegalStateException(String.format("Expected %s on tokenstack", expected));
				}
			}
		}
	}

	private SourcePosition getCurrentPos() {
		if (positionStack.isEmpty()) {
			throw new IllegalStateException("internal erorr on no token");
		}

		return positionStack.peek();
	}

	private void handleException(CompilationException e) {
		if (exceptionConsumer != null) {
			exceptionConsumer.accept(e);
		}
	}

	protected static SourcePosition computePosAtOffset(SourcePosition pos, int columnOffset) {
		NullCheck.nonNull(pos, "pos");

		return pos.clone(pos.line, pos.column, columnOffset);
	}

	protected SourcePosition computePosAtOffset(int columnOffset) {
		SourcePosition pos = getCurrentPos();

		if (columnOffset == 0) {
			return pos;
		} else {
			return pos.clone(pos.line, pos.column, columnOffset);
		}
	}

	@Override
	public void error(String msg, int charOffset) {
		handleException(new CompilationException(msg, computePosAtOffset(charOffset)));
	}

	@Override
	public void error(Throwable cause, int charOffset) {
		handleException(new CompilationException(cause, computePosAtOffset(charOffset)));
	}

	@Override
	public void error(Throwable cause, String msg, int charOffset) {
		handleException(new CompilationException(msg, cause, computePosAtOffset(charOffset)));
	}

	private void handleWarning(CompilationWarning warning) {
		if (warningConsumer != null) {
			warningConsumer.accept(warning);
		}
	}

	@Override
	public void warn(String msg, int charOffset) {
		handleWarning(new CompilationWarning(computePosAtOffset(charOffset), msg));
	}
}