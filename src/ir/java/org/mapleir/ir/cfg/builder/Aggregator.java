package org.mapleir.ir.cfg.builder;

import java.util.ArrayList;
import java.util.Arrays;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.InitialisedObjectExpression;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.ir.code.expr.UninitialisedObjectExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.PopStatement;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.ir.transform.ssa.SSALocalAccess;
import org.objectweb.asm.Opcodes;

public class Aggregator extends OptimisationPass.Optimiser {

	public Aggregator(ControlFlowGraphBuilder builder, SSALocalAccess localAccess) {
		super(builder, localAccess);
	}

	@Override
	public int run(BasicBlock b) {
		int changes = 0;
		
		for(Statement stmt : new ArrayList<>(b)) {
			if (stmt instanceof PopStatement) {
				PopStatement pop = (PopStatement) stmt;
				Expression expr = pop.getExpression();
				if (expr instanceof InvocationExpression) {
					InvocationExpression invoke = (InvocationExpression) expr;
					if (invoke.getOpcode() == Opcodes.INVOKESPECIAL && invoke.getName().equals("<init>")) {
						Expression inst = invoke.getInstanceExpression();
						if (inst instanceof VarExpression) {
							VarExpression var = (VarExpression) inst;
							VersionedLocal local = (VersionedLocal) var.getLocal();

							AbstractCopyStatement def = localAccess.defs.get(local);

							Expression rhs = def.getExpression();
							if (rhs instanceof UninitialisedObjectExpression) {
								// replace pop(x.<init>()) with x := new Klass();
								// remove x := new Klass;
								
								// here we are assuming that the new object
								// can't be used until it is initialised.
								UninitialisedObjectExpression obj = (UninitialisedObjectExpression) rhs;
								Expression[] args = invoke.getParameterArguments();
								Expression[] newArgs = Arrays.copyOf(args, args.length);
								InitialisedObjectExpression newExpr = new InitialisedObjectExpression(obj.getType(), invoke.getOwner(), invoke.getDesc(), newArgs);
								// remove the old def
								// add a copy statement before the pop (x = newExpr)
								// remove the pop statement
								
								b.remove(def);
								
								CopyVarStatement newCvs = new CopyVarStatement(var, newExpr);
								localAccess.defs.put(local, newCvs);
								localAccess.useCount.get(local).decrementAndGet();
								
								int index = b.indexOf(pop);
								b.add(index, newCvs);
								b.remove(pop);
								
								changes++;
							}
						} else if(inst instanceof UninitialisedObjectExpression) {
							// replace pop(new Klass.<init>(args)) with pop(new Klass(args))
							UninitialisedObjectExpression obj = (UninitialisedObjectExpression) inst;
							Expression[] args = invoke.getParameterArguments();
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
							throw new RuntimeException("interesting1 " + inst.getClass());
						}
					}
				}
			}
		}
	
		return changes;
	}
}