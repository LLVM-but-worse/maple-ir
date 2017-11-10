package org.mapleir.ir.antlr;

import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.log4j.Logger;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.flowgraph.edges.ConditionalJumpEdge;
import org.mapleir.flowgraph.edges.ImmediateEdge;
import org.mapleir.ir.antlr.mapleirParser.*;
import org.mapleir.ir.antlr.analysis.TypeAnalysis;
import org.mapleir.ir.antlr.directive.DirectiveDictValue;
import org.mapleir.ir.antlr.directive.DirectiveToken;
import org.mapleir.ir.antlr.directive.DirectiveValue;
import org.mapleir.ir.antlr.directive.DirectiveValueList;
import org.mapleir.ir.antlr.error.CompilationException;
import org.mapleir.ir.antlr.error.CompilationProblem;
import org.mapleir.ir.antlr.error.CompilationWarning;
import org.mapleir.ir.antlr.error.ErrorReporter;
import org.mapleir.ir.antlr.error.ForwardingErrorReporter;
import org.mapleir.ir.antlr.model.FieldDeclaration;
import org.mapleir.ir.antlr.model.MethodDeclaration;
import org.mapleir.ir.antlr.model.MethodDeclaration.HandlerTableEntry;
import org.mapleir.ir.antlr.scope.ClassScope;
import org.mapleir.ir.antlr.scope.CodeScope;
import org.mapleir.ir.antlr.scope.FieldScope;
import org.mapleir.ir.antlr.scope.MethodScope;
import org.mapleir.ir.antlr.scope.Scope;
import org.mapleir.ir.antlr.source.ExpectsSourcePosition;
import org.mapleir.ir.antlr.source.SourcePosition;
import org.mapleir.ir.antlr.util.LexerUtil;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.*;
import org.mapleir.ir.code.expr.ArithmeticExpr.Operator;
import org.mapleir.ir.code.expr.ComparisonExpr.ValueComparisonType;
import org.mapleir.ir.code.expr.invoke.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr.CallType;
import org.mapleir.ir.code.stmt.*;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt.ComparisonType;
import org.mapleir.ir.code.stmt.MonitorStmt.MonitorMode;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.impl.VersionedLocal;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class CompilationDriver extends mapleirBaseListener {
	
	private static final boolean BAIL_FAST = false;
	
	private static final Logger LOGGER = Logger.getLogger(CompilationDriver.class);
	
	private final ApplicationClassSource classPath;
	private Deque<Scope> scopes;
	public CompilationUnitContext unit;
	private ErrorReporter errorReporter;
	private List<CompilationException> exceptions;
	private List<CompilationWarning> warnings;
	
	public CompilationDriver(ApplicationClassSource classPath) {
		this.classPath = classPath;
		scopes = new LinkedList<>();
		
		Consumer<CompilationException> exceptionConsumer = new Consumer<CompilationException>() {
			@Override
			public void accept(CompilationException e) {
				if(BAIL_FAST) {
					throw new RuntimeException(e);
				} else {
					synchronized (exceptions) {
						exceptions.add(e);	
					}
				}
			}
		};
		Consumer<CompilationWarning> warningConsumer = new Consumer<CompilationWarning>() {
			@Override
			public void accept(CompilationWarning w) {
				synchronized (warnings) {
					warnings.add(w);
				}
			}
		};
		
		errorReporter = new ForwardingErrorReporter(exceptionConsumer, warningConsumer);
		
		exceptions = new ArrayList<>();
		warnings = new ArrayList<>();
	}

	public void process(mapleirParser parser) throws CompilationException {
		parser.addParseListener(this);
		unit = parser.compilationUnit();
		
		if (unit == null) {
			throw new CompilationException("No compilation unit to process", new SourcePosition(0, 0, 0) {
				@Override
				public String getText() {
					return null;
				}
				
				@Override
				public SourcePosition clone(int line, int column, int offset) {
					throw new UnsupportedOperationException();
				}
			});
		}

		/* push our global scope and process global directives */
		scopes.push(new Scope(this) {}); // TODO:
		
		parseDirectives(unit.setDirective());
		processClassDecl(unit.classDeclaration());
		
		parser.removeParseListener(this);
		
		if(!exceptions.isEmpty()) {
			Consumer<String> errorConsumer = (s -> LOGGER.error(s));
			
			LOGGER.error("Compilation errors occured while processing file");
			for(CompilationException e : exceptions) {
				logProblem(e, errorConsumer);
			}
		}
		
		if(!warnings.isEmpty()) {
			Consumer<String> warnConsumer = (s -> LOGGER.warn(s));
			
			LOGGER.error("Compilation warnings occured while processing file");
			
			for(CompilationWarning e : warnings) {
				logProblem(e, warnConsumer);
			}
		}
	}
	
	private void logProblem(CompilationProblem p,
			Consumer<String> printConsumer) {
		SourcePosition pos = p.getPosition();

		String ptext = pos.getText();

		String pointer = "(ツ)_/";
		int spacerWidth;

		/* if the pointer is shorter than the token, move the token
		 * over, so the pointer can fit and point to the correct
		 * character. */
		if (ptext.length() < (pointer.length() - pos.tokenOffset)) {
			spacerWidth = pointer.length();
		} else {
			spacerWidth = 0;
		}
		
		/* move away from the lhs */
		spacerWidth += 3;

		printConsumer.accept(String.format("%s at line %d (col:%d)",
				p.getMessage(), pos.line, pos.column));

		/* autoformatter wth */
		if (ptext != null) {
			printConsumer.accept(
					String.format("%s%s", makeSpacer(spacerWidth, ' '), ptext));
			printConsumer.accept(String.format("%s%s",
					makeSpacer(spacerWidth + pos.tokenOffset - pointer.length(),
							' '),
					pointer));
		}
	}
	
	private static String makeSpacer(int n, char c) {
		StringBuilder sb = new StringBuilder();
		for(int i=0; i < n; i++) {
			sb.append(c);
		}
		return sb.toString();
	}
	
	private void processClassDecl(ClassDeclarationContext cdecl) {
		SourcePosition p = errorReporter.newSourcePosition(cdecl);
		ClassScope scope = new ClassScope(scopes.peek());
		scopes.push(scope);
		
		checkClassName(cdecl.jclass());
		scope.getClassDecl().setName(cdecl.jclass().getText());
		parseDirectives(cdecl.setDirective());
		
		List<DeclarationsContext> decls = cdecl.declarations();
		if(decls != null && !decls.isEmpty()) {
			for(DeclarationsContext ctx : decls) {
				if(ctx.classDeclaration() != null) {
					processClassDecl(ctx.classDeclaration());
				} else if(ctx.memberDeclaration() != null) {
					MemberDeclarationContext memberDecl = ctx.memberDeclaration();
					
					if(memberDecl.fieldDeclaration() != null) {
						processFieldDecl(memberDecl.fieldDeclaration());
					} else if(memberDecl.methodDeclaration() != null) {
						processMethodDecl(memberDecl.methodDeclaration());
					} else {
						throw new IllegalStateException("No decl in member decl?");
					}
				} else {
					throw new IllegalStateException("No decl in class decl?");
				}
			}
		}
		
		scopes.pop();
		errorReporter.popSourcePosition(p);
	}
	
	private void processMethodDecl(MethodDeclarationContext methodDeclCtx) {
		MethodScope methodScope = new MethodScope((ClassScope) scopes.peek());
		scopes.push(methodScope);
		
		SourcePosition methodDeclCtxPos = errorReporter.newSourcePosition(methodDeclCtx);
		MethodDeclaration methodDecl = methodScope.getDeclaration();
		
		/* these desc tokens are a complete mess, we need to
		 * concat them and parse it ourselves (therefore fk antlr) */
		
		MethoddescContext methodDescCtx = methodDeclCtx.methoddesc();
		SourcePosition methodDescCtxPos = errorReporter.newSourcePosition(methodDescCtx);
		
		String desc = methodDescCtx.getText();
		checkMethodDec(desc);
		
		errorReporter.popSourcePosition(methodDescCtxPos);
		
		methodDecl.setName(methodDeclCtx.Identifier().getText());
		methodDecl.setDesc(desc);
		
		/* parse/process property directives */
		parseDirectives(methodDeclCtx.setDirective());
		processMethodDirectives(methodScope);
		
		// TODO: parse code
		CodebodyContext codeBodyCtx = methodDeclCtx.codebody();
		SourcePosition codeBodyCtxPos = errorReporter.newSourcePosition(codeBodyCtx);
		
		CodeScope codeScope = new CodeScope(methodScope);
		scopes.push(codeScope);
		processCodeBody(codeBodyCtx, codeScope);
		scopes.pop();
		
		errorReporter.popSourcePosition(codeBodyCtxPos);
		
		errorReporter.popSourcePosition(methodDeclCtxPos);
		scopes.pop();
	}

	private void processCodeBody(CodebodyContext codeBodyCtx,
			CodeScope codeScope) {
		
		ControlFlowGraph cfg = codeScope.getCfg();
		
		for (BlockContext blockCtx : codeBodyCtx.block()) {
			String displayName = blockCtx.Identifier().getText();
			codeScope.createBlock(displayName);
		}
		
		LocalsPool pool = cfg.getLocals();
		
		List<BlockContext> blockContexts = codeBodyCtx.block();
		
		for (int i=0; i < blockContexts.size(); i++) {
			BlockContext blockCtx = blockContexts.get(i);
			SourcePosition blockSourcePos = errorReporter.newSourcePosition(blockCtx);
			
			String displayName = blockCtx.Identifier().getText();
			BasicBlock block = codeScope.findBlock(displayName);
			
			if(block == null) {
				errorReporter.error("internal error, no block in context for \"" + displayName + "\"");
				continue;
			}
			
			boolean shouldAddImmediate = true;
			
			for(StatementContext stmtCtx : blockCtx.statement()) {
				if(stmtCtx.copyStatement() != null) {
					CopyStatementContext copyStmtCtx = stmtCtx.copyStatement();

					VersionedLocal dst = getLocal(codeScope, copyStmtCtx.Identifier().getText());
					VarExpr dstVarExpr = new VarExpr(dst, null);
					
					Expr expr = processExpr(codeScope, copyStmtCtx.expr());
					CopyVarStmt copyVarStmt = new CopyVarStmt(dstVarExpr, expr);
					
					block.add(copyVarStmt);
					
					pool.defs.put(dst, copyVarStmt);
				} else if(stmtCtx.phiCopyStatement() != null) {
					PhiCopyStatementContext phiCopyStmtCtx = stmtCtx.phiCopyStatement();
					
					VersionedLocal dst = getLocal(codeScope, phiCopyStmtCtx.Identifier().getText());
					VarExpr dstVarExpr = new VarExpr(dst, null);
					
					PhiExpr phiExpr = new PhiExpr();
					
					for(PhiPairContext phiPairCtx : phiCopyStmtCtx.phiPair()) {
						TerminalNode sourceBlockIdentifier = phiPairCtx.Identifier(0);
						
						BasicBlock sourceBlock = lookupBlock(codeScope, sourceBlockIdentifier, "phi source");
						if(sourceBlock == null) {
							continue;
						}

						TerminalNode phiArgumentLocalIdentifier = phiPairCtx.Identifier(1);
						
						SourcePosition phiArgumentLocalPos = errorReporter.newSourcePosition(phiArgumentLocalIdentifier);
						Local phiArgumentLocal = getLocal(codeScope, phiArgumentLocalIdentifier.getText());
						errorReporter.popSourcePosition(phiArgumentLocalPos);
						
						phiExpr.setArgument(sourceBlock, new VarExpr(phiArgumentLocal, null));
					}
					
					CopyPhiStmt copyPhiStmt = new CopyPhiStmt(dstVarExpr, phiExpr);
					
					block.add(copyPhiStmt);
					
					pool.defs.put(dst, copyPhiStmt);
				} else if(stmtCtx.arrayStoreStatement() != null) {
					ArrayStoreStatementContext arrayStoreStmtCtx = stmtCtx.arrayStoreStatement();
					
					Expr arrayExpr = processExpr(codeScope, arrayStoreStmtCtx.expr(0));
					Expr indexExpr = processExpr(codeScope, arrayStoreStmtCtx.expr(1));
					Expr valueExpr = processExpr(codeScope, arrayStoreStmtCtx.expr(2));
					ArrayStoreStmt arrayStoreStmt = new ArrayStoreStmt(arrayExpr, indexExpr, valueExpr, null);

					block.add(arrayStoreStmt);
				} else if(stmtCtx.fieldStoreStatement() != null) {
					FieldStoreStatementContext fieldStoreStmtCtx = stmtCtx.fieldStoreStatement();
					
					if(fieldStoreStmtCtx.virtualFieldStoreStatement() != null) {
						VirtualFieldStoreStatementContext virtualFieldStoreStmtCtx = fieldStoreStmtCtx.virtualFieldStoreStatement();
						
						Expr objectExpr = processExpr(codeScope, virtualFieldStoreStmtCtx.expr(0));
						Expr valueExpr = processExpr(codeScope, virtualFieldStoreStmtCtx.expr(1));
						
						FieldStoreStmt fieldStoreStmt = new FieldStoreStmt(objectExpr, valueExpr, null, virtualFieldStoreStmtCtx.Identifier().getText(), null);
						
						block.add(fieldStoreStmt);
					} else if(fieldStoreStmtCtx.staticFieldStoreStatement() != null) {
						StaticFieldStoreStatementContext staticFieldStoreStmtCtx = fieldStoreStmtCtx.staticFieldStoreStatement();
						JclassContext ownerCtx = staticFieldStoreStmtCtx.jclass();
						checkClassName(ownerCtx);
						
						Expr valueExpr = processExpr(codeScope, staticFieldStoreStmtCtx.expr());
						FieldStoreStmt fieldStoreStmt = new FieldStoreStmt(null, valueExpr, ownerCtx.getText(), staticFieldStoreStmtCtx.Identifier().getText(), null);
						
						block.add(fieldStoreStmt);
					} else {
						throw new IllegalStateException();
					}
				} else if(stmtCtx.ifStatement() != null) {
					IfStatementContext ifStmtCtx = stmtCtx.ifStatement();
					
					Expr leftExpr = processExpr(codeScope, ifStmtCtx.expr(0));
					Expr rightExpr = processExpr(codeScope, ifStmtCtx.expr(1));
					
					BasicBlock trueSuccessorBlock = lookupBlock(codeScope, ifStmtCtx.Identifier(), "true successor of conditional jump");
					ConditionalJumpStmt.ComparisonType cmpType;
					
					if(ifStmtCtx.LE() != null) {
						cmpType = ComparisonType.LE;
					} else if(ifStmtCtx.GE() != null) {
						cmpType = ComparisonType.GE;
					} else if(ifStmtCtx.LT() != null) {
						cmpType = ComparisonType.LE;
					} else if(ifStmtCtx.GT() != null) {
						cmpType = ComparisonType.GT;
					} else if(ifStmtCtx.EQ() != null) {
						cmpType = ComparisonType.EQ;
					} else if(ifStmtCtx.NOTEQ() != null) {
						cmpType = ComparisonType.NE;
					} else {
						throw new IllegalStateException("no cmp type");
					}
					
					if(trueSuccessorBlock != null) {
						if((i+1) >= blockContexts.size()) {
							SourcePosition gotoIdentifierSourcePos = errorReporter.newSourcePosition(ifStmtCtx.Identifier());
							errorReporter.error("false successor of if statement falls out of cfg bounds");
							errorReporter.popSourcePosition(gotoIdentifierSourcePos);
						} else {
							BasicBlock falseSuccessorBlock = codeScope.findBlock(blockContexts.get(i+1).Identifier().getText());
							
							if(falseSuccessorBlock != null) {
								cfg.addEdge(block, new ConditionalJumpEdge<>(block, trueSuccessorBlock, Opcodes.IFEQ /* TODO: remove opcode field? */));
								cfg.addEdge(block, new ImmediateEdge<>(block, falseSuccessorBlock));
							} else {
								SourcePosition ifStmtCtxSourcePos = errorReporter.newSourcePosition(ifStmtCtx);
								errorReporter.error("no block for false successor of if statement");
								errorReporter.popSourcePosition(ifStmtCtxSourcePos);
							}
						}
					}
					
					ConditionalJumpStmt condJumpStmt = new ConditionalJumpStmt(leftExpr, rightExpr, trueSuccessorBlock, cmpType);
					block.add(condJumpStmt);
					
					shouldAddImmediate = false;
				} else if(stmtCtx.gotoStatement() != null) {
					GotoStatementContext gotoStmtCtx = stmtCtx.gotoStatement();
					
					BasicBlock targetBlock = lookupBlock(codeScope, gotoStmtCtx.Identifier(), "unconditional jump target");
					UnconditionalJumpStmt uncondJumpStmt = new UnconditionalJumpStmt(targetBlock);
					
					block.add(uncondJumpStmt);
					
					shouldAddImmediate = false;
				} else if(stmtCtx.consumeStatement() != null) {
					ConsumeStatementContext consumeStmtCtx = stmtCtx.consumeStatement();
					
					Expr innerExpr = processExpr(codeScope, consumeStmtCtx.expr());
					PopStmt popStmt = new PopStmt(innerExpr);
					block.add(popStmt);
				} else if(stmtCtx.returnStatement() != null) {
					ReturnStatementContext returnStmtCtx = stmtCtx.returnStatement();

					ReturnStmt returnStmt;
					
					if(returnStmtCtx.expr() != null) {
						Expr innerExpr = processExpr(codeScope, returnStmtCtx.expr());
						returnStmt = new ReturnStmt(null, innerExpr);
					} else {
						returnStmt = new ReturnStmt();
					}
					block.add(returnStmt);
					
					shouldAddImmediate = false;
				}  else if(stmtCtx.throwStatement() != null) {
					ThrowStatementContext throwStmtCtx = stmtCtx.throwStatement();
					
					Expr innerExpr = processExpr(codeScope, throwStmtCtx.expr());
					ThrowStmt throwStmt = new ThrowStmt(innerExpr);
					
					block.add(throwStmt);
					
					shouldAddImmediate = false;
				} else if(stmtCtx.monitorStatement() != null) {
					MonitorStatementContext monitorStmtCtx = stmtCtx.monitorStatement();
					
					MonitorStmt.MonitorMode mode;
					
					if(monitorStmtCtx.MONENTER() != null) {
						mode = MonitorMode.ENTER;
					} else if(monitorStmtCtx.MONEXIT() != null) {
						mode = MonitorMode.EXIT;
					} else {
						throw new IllegalStateException("no monitor mode");
					}
					
					Expr innerExpr = processExpr(codeScope, monitorStmtCtx.expr());
					MonitorStmt monitorStmt = new MonitorStmt(innerExpr, mode);
					
					block.add(monitorStmt);
				} else if(stmtCtx.switchStatement() != null) {
					SwitchStatementContext switchStmtCtx = stmtCtx.switchStatement();
					
					Expr innerExpr = processExpr(codeScope, switchStmtCtx.expr());
					
					// TODO: constant expr eval
					SwitchStmt switchStmt = new SwitchStmt(innerExpr);
					
					for(SwitchCaseStatementContext switchCaseStmtCtx : switchStmtCtx.switchCaseStatement()) {
						// Expr valueExpr = processExpr(codeScope, switchCaseStmtCtx.expr());
						BasicBlock targetBlock = lookupBlock(codeScope, switchCaseStmtCtx.Identifier(), "switch case target");
						if(targetBlock == null) {
							continue;
						}
						
						if(switchCaseStmtCtx.CASE() != null) {
							TerminalNode literal = switchCaseStmtCtx.IntegerLiteral();
							
							SourcePosition literalSourcePos = errorReporter.newSourcePosition(literal);
							try {
								switchStmt.addCase((int)decodeLiteral(literal.getText()), targetBlock);
							} catch (ParseException e) {
								errorReporter.error(e, String.format("Malformed input: \"%s\"", literal.getText()), e.getErrorOffset() + 1);
							}
							errorReporter.popSourcePosition(literalSourcePos);
						} else if(switchCaseStmtCtx.DEFAULT() != null) {
							switchStmt.setDefaultTarget(targetBlock);
						} else {
							throw new IllegalStateException("illegal switch case type");
						}
					}
					
					shouldAddImmediate = false;
					throw new UnsupportedOperationException("TODO");
				} else {
					throw new IllegalStateException("unknown statement type");
				}
			}
			
			if(shouldAddImmediate && (i+1) < blockContexts.size()) {
				BasicBlock next = codeScope.findBlock(blockContexts.get(i+1).Identifier().getText());
				cfg.addEdge(block, new ImmediateEdge<>(block, next));
			}
			errorReporter.popSourcePosition(blockSourcePos);
		}
		
		for(HandlerTableEntry hte : codeScope.getParent().getDeclaration().getHandlerEntries()) {
			BasicBlock startBlock = lookupBlock(codeScope, hte.start, "range start");
			BasicBlock endBlock = lookupBlock(codeScope, hte.start, "range end");
			BasicBlock handlerBlock = lookupBlock(codeScope, hte.start, "handler");
			if(startBlock == null || endBlock == null || handlerBlock == null) {
				continue;
			}
			
			ExceptionRange<BasicBlock> range = new ExceptionRange<>();
			cfg.addRange(range);
		}
		
		{
			if(blockContexts.size() > 0) {
				BlockContext firstBlockCtx = blockContexts.get(0);
				String displayName = firstBlockCtx.Identifier().getText();
				BasicBlock block = codeScope.findBlock(displayName);
				cfg.getEntries().add(block);
			}
		}
		
		System.out.println(cfg);

		Map<VersionedLocal, Type> argTypes = new HashMap<>();
		Type[] rawArgTypes = Type.getArgumentTypes(
				codeScope.getParent().getDeclaration().getDesc());
		boolean isStatic = Modifier
				.isStatic(codeScope.getParent().getDeclaration().getAccess());
		if (!isStatic) {
			argTypes.put(pool.get(0, 0, false), Type.getObjectType(codeScope
					.getParent().getParent().getClassDecl().getName()));
		}
		
		for(int i=0; i < rawArgTypes.length; i++) {
			Type type = rawArgTypes[i];
			VersionedLocal local = pool.get(i + (isStatic ? 0 : 1), 0, false);
			argTypes.put(local, type);
		}
		System.out.println(argTypes);
		TypeAnalysis.analyse(classPath, cfg, argTypes);
	}
	
	private BasicBlock lookupBlock(CodeScope codeScope, DirectiveValue idVal, String errorForUsage) {
		String sourceBlockName = idVal.getValueUnsafe();
		BasicBlock sourceBlock = codeScope.findBlock(sourceBlockName);
		
		if(sourceBlock == null) {
			errorReporter.pushSourcePosition(idVal.getValueSoucePosition());
			errorReporter.error(String.format("no block for %s with id: \"%s\"", errorForUsage, sourceBlockName));
			errorReporter.popSourcePosition(idVal.getValueSoucePosition());
		}
		
		return sourceBlock;
	}
	
	private BasicBlock lookupBlock(CodeScope codeScope, ParseTree sourceBlockIdentifier, String errorForUsage) {
		String sourceBlockName = sourceBlockIdentifier.getText();
		BasicBlock sourceBlock = codeScope.findBlock(sourceBlockName);
		
		if(sourceBlock == null) {
			SourcePosition sourceBlockIdentifierSourcePos = errorReporter.newSourcePosition(sourceBlockIdentifier);
			errorReporter.error(String.format("no block for %s with id: \"%s\"", errorForUsage, sourceBlockName));
			errorReporter.popSourcePosition(sourceBlockIdentifierSourcePos);
		}
		
		return sourceBlock;
	}
	
	private Expr processExpr(CodeScope codeScope, final ExprContext _exprCtx) {
		return processExpr(codeScope, _exprCtx, false);
	}
	
	private Expr processExpr(CodeScope codeScope, final ExprContext _exprCtx, boolean allowCatch) {
		ExprContext exprCtx = _exprCtx;
		
		while(exprCtx.primary() != null) {
			PrimaryContext primaryExprCtx = exprCtx.primary();

			/* since primary can contain another expr in the form
			 * LPAREN expr RPAREN, or some sort of identifier or literal,
			 * we don't create a new source position scope until we
			 * find the base expression and instead if anything goes wrong
			 * we then create the source pos temporarily for the error. */
			if (primaryExprCtx.LITERAL() != null) {
				TerminalNode literal = primaryExprCtx.LITERAL();
				String literalText = literal.getText();
				
				SourcePosition literalSourcePos = errorReporter.newSourcePosition(literal);
				try {
					Object constObj = decodeLiteral(literalText);
					return new ConstantExpr(constObj);
				} catch (ParseException e) {
					errorReporter.error(e, String.format("Malformed input: \"%s\"", literalText), e.getErrorOffset() + 1);
					return null;
				} finally {
					errorReporter.popSourcePosition(literalSourcePos);
				}
			} else if(primaryExprCtx.Identifier() != null) {
				TerminalNode identifier = primaryExprCtx.Identifier();
				SourcePosition identifierPos = errorReporter.newSourcePosition(identifier);
				Local l = getLocal(codeScope, identifier.getText());
				errorReporter.popSourcePosition(identifierPos);
				return new VarExpr(l, null);
			} else if(primaryExprCtx.expr() != null) {
				exprCtx = primaryExprCtx.expr();
			} else {
				throw new IllegalStateException("token has no expr: " + _exprCtx);
			}
		}
		
		switch(exprCtx.type) {
			case ARITH: 
			case BIT:
			case SHIFT: {
				Expr left = processExpr(codeScope, exprCtx.expr(0));
				Expr right = processExpr(codeScope, exprCtx.expr(1));
				
				ArithmeticExpr.Operator op;
				
				if(exprCtx.MUL() != null) {
					op = Operator.MUL;
				} else if(exprCtx.DIV() != null) {
					op = Operator.DIV;
				} else if(exprCtx.MOD() != null) {
					op = Operator.REM;
				} else if(exprCtx.ADD() != null) {
					op = Operator.ADD;
				} else if(exprCtx.SUB() != null) {
					op = Operator.SUB;
				} else if(exprCtx.BITAND() != null) {
					op = Operator.AND;
				} else if(exprCtx.CARET() != null) {
					op = Operator.XOR;
				} else if(exprCtx.BITOR() != null) {
					op = Operator.OR;
				} else {
					if(exprCtx.GT().size() == 3) {
						op = Operator.USHR;
					} else if(exprCtx.GT().size() == 2) {
						op = Operator.SHR;
					} else if(exprCtx.LT().size() == 2) {
						op = Operator.SHL;
					} else {
						throw new IllegalStateException("illegal arithmetic operation");
					}
				}
				
				return new ArithmeticExpr(right, left, op);
			}
			case ARRGET: {
				Expr arrayExpr = processExpr(codeScope, exprCtx.expr(0));
				Expr indexExpr = processExpr(codeScope, exprCtx.expr(1));
			
				return new ArrayLoadExpr(arrayExpr, indexExpr, null);
			}
			case CAST: {
				Expr expr = processExpr(codeScope, exprCtx.expr(0));
				JclassContext classNameCtx = exprCtx.jclass();
				checkClassName(classNameCtx);
				
				return new CastExpr(expr, Type.getType(classNameCtx.getText()));
			}
			case CATCH: {
				if(allowCatch) {
					return new CaughtExceptionExpr(null);
				} else {
					return new ConstantExpr(null);
				}
			}
			case FIELDGET_OR_ARRLEN: {
				Expr objExpr = processExpr(codeScope, exprCtx.expr(0));
				return new FieldLoadExpr(objExpr, null, exprCtx.Identifier(0).getText(), null);
			}
			case INSTANCEOF: {
				JclassContext classNameCtx = exprCtx.jclass();
				checkClassName(classNameCtx);
				
				Expr expr = processExpr(codeScope, exprCtx.expr(0));
				return new InstanceofExpr(expr, Type.getType(classNameCtx.getText()));
			}
			case LOW_COMPARE: {
				Expr left = processExpr(codeScope, exprCtx.expr(0));
				Expr right = processExpr(codeScope, exprCtx.expr(1));
				
				ComparisonExpr.ValueComparisonType type;
				
				if(exprCtx.EQ() != null) {
					type = ValueComparisonType.CMP;
				} else if(exprCtx.LT().size() == 1) {
					type = ValueComparisonType.LT;
				} else if(exprCtx.GT().size() == 1) {
					type = ValueComparisonType.GT;
				} else {
					throw new IllegalStateException();
				}
				
				return new ComparisonExpr(left, right, type);
			}
			case NEW_OR_ALLOC: {
				CreatorContext creatorCtx = exprCtx.creator();
				JclassContext jclassCtx = creatorCtx.jclass();
				checkClassName(jclassCtx);
				
				String owner = jclassCtx.getText();
				
				if(creatorCtx.arguments() != null) {
					ArgumentsContext argumentsCtx = creatorCtx.arguments();
					ExprlistContext exprListCtx = argumentsCtx.exprlist();
					
					Expr[] args = new Expr[exprListCtx.expr().size()];
					
					for(int i=0; i < args.length; i++) {
						args[i] = processExpr(codeScope, exprListCtx.expr(i));
					}
					
					return new InitialisedObjectExpr(owner, null, args);
				} else if(creatorCtx.arrayCreator() != null) {
					ArrayCreatorContext arrayCreatorCtx = creatorCtx.arrayCreator();
					
					Expr[] bounds = new Expr[arrayCreatorCtx.expr().size()];
					
					StringBuilder sb = new StringBuilder();
					
					for(int i=0; i < bounds.length; i++) {
						sb.append('[');
						bounds[i] = processExpr(codeScope, arrayCreatorCtx.expr(i));
					}
					sb.append('L').append(owner).append(';');
					
					return new NewArrayExpr(bounds, Type.getType(sb.toString()));
				} else {
					return new AllocObjectExpr(Type.getType(owner));
				}
			}
			case NOT: {
				// ~x in java is actually x^(-1)
				
				Expr expr = processExpr(codeScope, exprCtx.expr(0));
				return new ArithmeticExpr(new ConstantExpr(-1, Type.INT_TYPE), expr, Operator.XOR);
			}
			case NEG: {
				return new NegationExpr(processExpr(codeScope, exprCtx.expr(0)));
			}
			case STATIC_INVOKE: {
				ExprlistContext exprListCtx = exprCtx.arguments().exprlist();
				Expr[] args = new Expr[exprListCtx.expr().size()];
				
				for(int i=0; i < args.length; i++) {
					args[i] = processExpr(codeScope, exprListCtx.expr(i));
				}
				
				JclassContext className = exprCtx.jclass();
				checkClassName(className);
				
				return new InvocationExpr(CallType.STATIC, args, className.getText(), exprCtx.Identifier().get(1).getText(), null);
			}
			case EXPR_VIRTUAL_INVOKE: {
				ExprlistContext exprListCtx = exprCtx.arguments().exprlist();
				Expr[] args = new Expr[exprListCtx.expr().size()+1];
				
				args[0] = processExpr(codeScope, exprCtx.expr(0));
				
				for(int i=0; i < args.length; i++) {
					args[i+1] = processExpr(codeScope, exprListCtx.expr(i));
				}
				
				return new InvocationExpr(CallType.VIRTUAL, args, null, exprCtx.Identifier().get(1).getText(), null);
			}
			case VAR_VIRTUAL_INVOKE: {
				Local dst = getLocal(codeScope, exprCtx.Identifier().get(0).getText());
				VarExpr objVar = new VarExpr(dst, null);
				
				ExprlistContext exprListCtx = exprCtx.arguments().exprlist();
				Expr[] args = new Expr[exprListCtx.expr().size()+1];
				
				args[0] = objVar;
				
				for(int i=0; i < args.length; i++) {
					args[i+1] = processExpr(codeScope, exprListCtx.expr(i));
				}
				
				return new InvocationExpr(CallType.VIRTUAL, args, null, exprCtx.Identifier().get(1).getText(), null);
			}
			default:
				throw new UnsupportedOperationException("expr type: " + exprCtx.type);
		}
	}

	private VersionedLocal getLocal(CodeScope codeScope, String identifier) {
		if (codeScope.isLocalMapped(identifier)) {
			return codeScope.findLocal(identifier);
		}

		char[] chars = identifier.toCharArray();

		/* rough: lvar1 */
		if (chars.length > 4) {
			boolean possible = chars[1] == 'v' && chars[2] == 'a'
					&& chars[3] == 'r';

			if (possible) {

				char ch0 = chars[0];
				if (!(ch0 == 'l' || ch0 == 's')) {
					errorReporter.warn("expecting 's' or 'l' for local types, got: '" + ch0
							+ "'");
					return codeScope.getOrFindLocal(identifier);
				}

				StringBuilder buffer = new StringBuilder(chars.length - 4);

				int i = 4;
				char ch = chars[i];
				/* get the var index number */
				while (Character.isDigit(ch = chars[i++])) {
					buffer.append(ch);
				}

				int index;
				try {
					index = Integer.parseInt(buffer.toString());
					buffer.setLength(0);
				} catch (NumberFormatException e) {
					errorReporter.warn("iinvalid local index \"" + buffer.toString() + "\"",
							i - buffer.length()/* start of index str */);
					return codeScope.getOrFindLocal(identifier);
				}

				if (ch != '_') {
					errorReporter.warn("expecting '_' for local version, got: " + ch, i);
					return codeScope.getOrFindLocal(identifier);
				}

				while (i < chars.length) {
					buffer.append(ch = chars[i++]);
				}

				int version;
				try {
					version = Integer.parseInt(buffer.toString());
				} catch (NumberFormatException e) {
					errorReporter.warn("invalid local version: \"" + buffer.toString() + "\"",
							i - buffer.length()/* start of version str */);
					return codeScope.getOrFindLocal(identifier);
				}

				LocalsPool pool = codeScope.getLocalPool();
				VersionedLocal l = pool.get(index, version, ch0 == 's');
				codeScope.mapLocal(identifier, l);
				return l;
			}
		}

		return codeScope.getOrFindLocal(identifier);
	}

	private void processMethodDirectives(MethodScope methodScope) {
		/*for (DirectiveToken curDirectiveToken : methodScope.getProperties()) {
			String directiveKey = curDirectiveToken.getKey();

			if (directiveKey.equals("access")) {
				// TODO: when constant expr eval is done
				throw new UnsupportedOperationException("TODO");
			} else {
				// memberDirectiveError(curDirectiveToken);
			}
		}*/
	}
	
	private void processFieldDecl(FieldDeclarationContext fieldDeclCtx) {
		FieldScope scope = new FieldScope((ClassScope) scopes.peek());
		scopes.push(scope);

		SourcePosition fieldDeclCtxPos = errorReporter.newSourcePosition(fieldDeclCtx);
		FieldDeclaration fieldDecl = scope.getDeclaration();

		/* check descriptor */
		DescContext descCtx = fieldDeclCtx.desc();
		SourcePosition descCtxPos = errorReporter.newSourcePosition(descCtx);
		checkFieldDesc(descCtx.getText());
		errorReporter.popSourcePosition(descCtxPos);

		fieldDecl.setName(fieldDeclCtx.Identifier().getText());
		fieldDecl.setDesc(descCtx.getText());

		if (fieldDeclCtx.constant() != null) {
			TerminalNode literal = fieldDeclCtx.constant().LITERAL();
			String literalText = literal.getText();
			
			SourcePosition literalSourcePos = errorReporter.newSourcePosition(literal);
			try {
				Object decodedLiteral = decodeLiteral(fieldDeclCtx.constant().LITERAL().getText());
				fieldDecl.setDefaultValue(decodedLiteral);
			} catch (ParseException e) {
				errorReporter.error(e, String.format("Malformed input: \"%s\"", literalText), e.getErrorOffset() + 1);
			}
			errorReporter.popSourcePosition(literalSourcePos);
		}

		/* parse/process property directives */
		parseDirectives(fieldDeclCtx.setDirective());
		processFieldDirectives(scope);

		errorReporter.popSourcePosition(fieldDeclCtxPos);
		scopes.pop();
	}
	
	private void processFieldDirectives(FieldScope fieldScope) {		
		/*for (DirectiveToken curDirectiveToken : fieldScope.getProperties()) {
			String directiveKey = curDirectiveToken.getKey();

			if (directiveKey.equals("access")) {
				// TODO: when constant expr eval is done
				throw new UnsupportedOperationException("TODO");
			} else {
				// memberDirectiveError(curDirectiveToken);
			}
		}*/
	}
	
	/*private void memberDirectiveError(DirectiveToken curDirectiveToken) {
		SourcePosition keySourcePosition = curDirectiveToken.getKeySourcePosition();
		pushSourcePosition(keySourcePosition);
		error(String.format("unknown parameter: %s (=%s)", curDirectiveToken.getKey(), curDirectiveToken.getValue()));
		errorReporter.popSourcePosition(keySourcePosition);
	}*/
	
	private void parseDirectives(List<SetDirectiveContext> directives) {
		if(directives != null && !directives.isEmpty()) {
			for(SetDirectiveContext sdctx : directives) {
				parseDirective(sdctx);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private DirectiveValue makeDirectiveValue(SourcePosition commandValueSourcePos, Object o) {
		if(o instanceof Map) {
			return new DirectiveDictValue(commandValueSourcePos, (Map<String, DirectiveValue>) o);
		} else {
			return new DirectiveValue(commandValueSourcePos, o);
		}
	}
	
	private void parseDirective(SetDirectiveContext setDirectiveCtx) {
		SourcePosition setDirectiveSourcePosition = errorReporter.newSourcePosition(setDirectiveCtx);

		Scope currentScope = scopes.peek();

		if (currentScope == null) {
			errorReporter.error("Tried to use set directive outside of an active scope");
			errorReporter.popSourcePosition(setDirectiveSourcePosition);
			return;
		}

		List<DirectiveValue> worklist = new ArrayList<>();

		SetCommandValueListContext setCommandValueListCtx = setDirectiveCtx.setCommandValueList();
		
		for (SetCommandValueContext scvCtx : setCommandValueListCtx.setCommandValue()) {
			SourcePosition commandValueSourcePos = errorReporter.newSourcePosition(scvCtx);

			try {
				Object decodedObject = decodeSetCommandValue(scvCtx);
				worklist.add(makeDirectiveValue(commandValueSourcePos, decodedObject));
			} catch (ParseException e) {
				errorReporter.error(e, String.format("Malformed input: \"%s\"", scvCtx.getText()), e.getErrorOffset() + 1);
			}

			errorReporter.popSourcePosition(commandValueSourcePos);
		}

		DirectiveValue entryPropertyValue;

		boolean forceList = setCommandValueListCtx.LBRACK() != null;
		
		if (worklist.size() == 1 && !forceList) {
			entryPropertyValue = worklist.iterator().next();
		} else {
			entryPropertyValue = new DirectiveValueList(errorReporter.makeSourcePositionOnly(setDirectiveCtx.setCommandValueList()),
					worklist);
		}

		String key = setDirectiveCtx.Identifier().getText();
		DirectiveToken newToken = new DirectiveToken(errorReporter.makeSourcePositionOnly(setDirectiveCtx.Identifier()), key,
				entryPropertyValue);
		currentScope.addDirective(newToken);
		
		errorReporter.popSourcePosition(setDirectiveSourcePosition);
	}

	/**
	 * Parses the given String input as if it were a method descriptor, checking for
	 * inconsistencies and errors within the argument and return types.
	 * 
	 * <p>
	 * <b>Note: This method will report compilation exceptions if it encounters
	 * invalid inputs and tokens. These are relative to the start of the input
	 * string's token. Therefore a {@link SourcePosition} must be pushed as the
	 * current context before this method is invoked. </b>
	 * 
	 * @param input
	 *            The method descriptor to check
	 */
	@ExpectsSourcePosition
	private void checkMethodDec(String input) {
		if (input == null || input.isEmpty()) {
			errorReporter.error("missing descriptor");
			return;
		}

		char[] chars = input.toCharArray();
		int i = 0;
		char ch = chars[i++];

		if (ch != '(') {
			errorReporter.error("method descriptor must begin with '('", 1);
			return;
		}

		/* Doing charwise checking here would be too much work to do
		 * here, technically this should've been in the lexer, but for
		 * some reason it is too difficult to define the grammar fully
		 * and properly using ANTLR, therefore the strategy is, we try
		 * to break down the input into tokens using rough guesses and
		 * then check whether these guesses are proper tokens.
		 * 
		 * 1: If we detect a primitive type, we will skip it. 2: If we
		 * detect an L then we search for an ';' 3. If we detect a '['
		 * we will try to match the full array dimension descriptor
		 * and then look for the matches defined by the other rules.
		 * 
		 * We fill the buffer with the current guess at the
		 * descriptor. When we reach the end of the desc, we "commit"
		 * it, i.e. accept it. Therefore if there are any remaining
		 * characters in the buffer at the end there was a problem. */

		List<String> identifiedTokens = new ArrayList<>();

		boolean inRefType = false;
		boolean searchingReturn = false;
		StringBuilder buffer = new StringBuilder();

		/* could move into a static method; still messy. */
		class Helper {
			void commit() {
				identifiedTokens.add(buffer.toString());
				buffer.setLength(0);
			}
		}

		Helper helper = new Helper();

		for (; i < chars.length; i++) {
			ch = chars[i];

			if (ch == ')') {
				searchingReturn = true;
				/* check hanging chars */
				if (buffer.length() > 0) {
					errorReporter.error(String.format("illegal arg type \"%s\"", buffer.toString()), i+1);
					buffer.setLength(0);
					inRefType = true;
				}
				continue;
			}

			if (ch != '.' && ch != '/' && ch != ';' && ch != '['
					&& !Character.isJavaIdentifierPart(ch)) {
				buffer.setLength(0);
				inRefType = false;
				errorReporter.error(String.format("illegal char '%c' in descriptor", ch), i+1);
				continue;
			}

			buffer.append(ch);

			if (!inRefType && isPrimitiveType(ch, searchingReturn)) {
				helper.commit();
				continue;
			}

			if (ch == 'L') {
				inRefType = true;
			} else if (ch == ';') {
				helper.commit();
				inRefType = false;
				continue;
			}
		}

		if (buffer.length() > 0) {
			errorReporter.error(String.format("illegal return type \"%s\"", buffer.toString()), i+1);
		}

		for (String singleDesc : identifiedTokens) {
			checkFieldDesc(singleDesc);
		}
	}
	
	@ExpectsSourcePosition
	private boolean isArrayDescBroken(char[] chars, int offset, int end) {
		int i = offset;
		char ch = chars[i++];
		
		boolean broken = false;
		boolean inArray = ch == '[';
		
		/* check that all of the array ['s are at the start */
		for(; i < end; i++) {
			ch = chars[i];
			
			if(ch == '[') {
				if(!inArray) {
					errorReporter.error("misplaced '[' in descriptor", i+1);
					broken = true;
				}
			} else {
				if(inArray) {
					inArray = false;
				}
			}
		}
		
		return broken;
	}
	
	private static boolean isPrimitiveType(char ch, boolean allowVoid) {
		switch(ch) {
			case 'I':
			case 'B':
			case 'S':
			case 'J':
			case 'Z':
			case 'F':
			case 'D':
				return true;
			case 'V':
				return allowVoid;
			default:
				return false;
		}
	}
	
	@ExpectsSourcePosition
	private void checkFieldDesc(String input) {
		if(input == null || input.isEmpty()) {
			errorReporter.error("missing descriptor");
			return;
		}
		
		char[] chars = input.toCharArray();
		boolean broken = isArrayDescBroken(chars, 0, chars.length);
		
		if(!broken) {
			String noArrayInput = input.replace("[", "");
			/* offset past the '['s in the descriptor */
			int startOffset = input.length()-noArrayInput.length()+1;
			
			if(noArrayInput.isEmpty()) {
				errorReporter.error("missing descriptor", startOffset);
				return;
			}
			
			/* prim */
			if(noArrayInput.length() == 1) {
				char ch = noArrayInput.charAt(0);
				
				if(!isPrimitiveType(ch, false)) {
					errorReporter.error(String.format("invalid primitive: '%c'", ch), startOffset);
				}
			} else {
				if(noArrayInput.startsWith("L") && noArrayInput.endsWith(";")) {
					String suggestedType = noArrayInput.substring(1, noArrayInput.length()-1);
					/* create a new source pos at the start of the reference types class (i.e. in
					 * Ljava/lang/String; this would be at the first j) and check the class */
					// TODO: check this offset is right
					SourcePosition classNameSourcePos = errorReporter.newSourcePosition(startOffset);
					checkClassName(suggestedType);
					errorReporter.popSourcePosition(classNameSourcePos);
				} else {
					errorReporter.error(String.format("invalid base type \"%s\", ref tpyes must be in the form L*;", noArrayInput), startOffset);
				}
			}
		}
	}
	
	@ExpectsSourcePosition
	private void checkClassNameChar(char ch, char prevCh, int i) {
		if(ch == '.') {
			errorReporter.error("'.' instead of '/'", i+1);
		} else if(ch == '/') {
			if(prevCh == '/' || prevCh == '.') {
				errorReporter.error("no package name inbetween separators", i+1);
			}
		} else if(!Character.isUnicodeIdentifierPart(ch)) {
			errorReporter.error("invalid character: " + ch, i+1);
		}
	}
	
	@ExpectsSourcePosition
	private void checkClassName(String input) {
		if(input == null || input.isEmpty()) {
			errorReporter.error("missing identifier");
		}
		
		char[] chars = input.toCharArray();
		char prevCh = 0;
		int i = 0;
		
		char ch = chars[i++];
		checkClassNameChar(ch, prevCh, 0);
		if(Character.isDigit(ch)) {
			errorReporter.error("class names cannot start with a number");
		}
		/* input starts with '/' or '.' */
		if(ch == '/' || ch == '.') {
			errorReporter.error("leading '/'", 1);
		}
		
		prevCh = ch;
		
		for(;i < chars.length; i++, prevCh = ch) {
			checkClassNameChar(ch = chars[i], prevCh, i);
		}
		
		if(ch == '/' || ch == '.') {
			/* input ends with '/' or '.' */
			errorReporter.error("no class name (only packages declared)", input.length()/*-1+1*/);
		}
	}
	
	private void checkClassName(JclassContext jclass) {
		SourcePosition p = errorReporter.newSourcePosition(jclass);
		checkClassName(jclass.getText());
		errorReporter.popSourcePosition(p);
	}

	private Object decodeSetCommandValue(SetCommandValueContext v) throws ParseException {
		if (v.LITERAL() != null) {
			return decodeLiteral(v.LITERAL().getText());
		} else if(v.Identifier() != null) {
			return v.Identifier().getText();
		} else if(v.expr() != null) {
			// TODO;
			throw new UnsupportedOperationException("TODO: " + v.expr().getText());
		} else if(v.dict() != null) {
			DictContext dictCtx = v.dict();
			
			Map<String, Object> map = new HashMap<>();
			
			for(DictKeyValPairContext pairCtx : dictCtx.dictKeyValPair()) {
				String key = pairCtx.Identifier().getText();
				Object value = decodeSetCommandValue(pairCtx.setCommandValue());
				
				map.put(key, makeDirectiveValue(errorReporter.makeSourcePositionOnly(pairCtx), value));
			}
			
			return map;
		} else {
			throw new IllegalStateException("no value?");
		}
	}
	
	private Object decodeLiteral(String t) throws ParseException {
		if(t.equals("null")) {
			return null;
		} else if(t.equals("true") || t.equals("false")) {
			return Boolean.parseBoolean(t);
		} else {
			return LexerUtil.decodeNumericLiteral(t);
		}
	}
}
