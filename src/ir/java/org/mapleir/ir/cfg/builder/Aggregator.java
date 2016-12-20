package org.mapleir.ir.cfg.builder;

import java.util.ArrayList;
import java.util.Arrays;

import org.mapleir.ir.analysis.SSALocalAccess;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InitialisedObjectExpression;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.ir.code.expr.UninitialisedObjectExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.PopStatement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.VersionedLocal;
import org.objectweb.asm.Opcodes;

public class Aggregator extends OptimisationPass.Optimiser implements Opcode {

	public Aggregator(ControlFlowGraphBuilder builder, SSALocalAccess localAccess) {
		super(builder, localAccess);
	}

	@Override
	public int run(BasicBlock b) {
		int changes = 0;
		
		for(Stmt stmt : new ArrayList<>(b)) {
			if (stmt.getOpcode() == POP) {
				PopStatement pop = (PopStatement) stmt;
				Expression expr = pop.getExpression();
				if (expr.getOpcode() == INVOKE) {
					InvocationExpression invoke = (InvocationExpression) expr;
					if (invoke.getCallType() == Opcodes.INVOKESPECIAL && invoke.getName().equals("<init>")) {
						Expression inst = invoke.getInstanceExpression();
						if (inst.getOpcode() == LOCAL_LOAD) {
							VarExpression var = (VarExpression) inst;
							VersionedLocal local = (VersionedLocal) var.getLocal();

							AbstractCopyStatement def = localAccess.defs.get(local);

							Expression rhs = def.getExpression();
							if (rhs.getOpcode() == UNINIT_OBJ) {
								// replace pop(x.<init>()) with x := new Klass();
								// remove x := new Klass;
								
								// here we are assuming that the new object
								// can't be used until it is initialised.
								UninitialisedObjectExpression obj = (UninitialisedObjectExpression) rhs;
								Expression[] args = invoke.getParameterArguments();

//								System.out.println(pop);
								
								// we want to reuse the exprs, so free it first.
								pop.deleteAt(0);
//								invoke.unlink();
//								System.out.println(Arrays.toString(invoke.children));
//								System.out.println(Arrays.toString(args));
								Expression[] newArgs = Arrays.copyOf(args, args.length);
								for(int i=args.length-1; i >= 0; i--) {
//									System.out.println(i);
//									invoke.deleteAt(i);
									args[i].unlink();
								}
//								System.out.println(Arrays.toString(args));
								
								// remove the old def
								def.delete();
								
								int index = b.indexOf(pop);
								
								// add a copy statement before the pop (x = newExpr)
//								System.out.println(Arrays.toString(newArgs));
								InitialisedObjectExpression newExpr = new InitialisedObjectExpression(obj.getType(), invoke.getOwner(), invoke.getDesc(), newArgs);
								CopyVarStatement newCvs = new CopyVarStatement(var, newExpr);
								localAccess.defs.put(local, newCvs);
								localAccess.useCount.get(local).decrementAndGet();
								b.add(index, newCvs);

								// remove the pop statement
								b.remove(pop);
								
//								newCvs.checkConsistency();
								
								changes++;
							}
						} else if(inst.getOpcode() == UNINIT_OBJ) {
							// replace pop(new Klass.<init>(args)) with pop(new Klass(args))
							UninitialisedObjectExpression obj = (UninitialisedObjectExpression) inst;
							
							Expression[] args = invoke.getParameterArguments();
							// we want to reuse the exprs, so free it first.
							invoke.unlink();
							for(Expression e : args) {
								e.unlink();
							}
							
							Expression[] newArgs = Arrays.copyOf(args, args.length);
							InitialisedObjectExpression newExpr = new InitialisedObjectExpression(obj.getType(), invoke.getOwner(), invoke.getDesc(), newArgs);
							// replace pop contents
							// no changes to defs or uses
							
							pop.setExpression(newExpr);
							
							changes++;
						} else {
							System.err.println(b);
							System.err.println("Stmt: " + stmt.getId() + ". " + stmt);
							System.err.println("Inst: " + inst);
							System.err.println(builder.graph);
							throw new RuntimeException("interesting1 " + inst.getClass());
						}
					}
				}
			}
		}
	
		return changes;
	}
}