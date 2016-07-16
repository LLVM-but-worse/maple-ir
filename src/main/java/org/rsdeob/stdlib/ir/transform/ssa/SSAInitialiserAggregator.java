package org.rsdeob.stdlib.ir.transform.ssa;

import org.objectweb.asm.Opcodes;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.expr.*;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.PopStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.SSATransformer;

import java.util.Arrays;
import java.util.HashSet;

public class SSAInitialiserAggregator extends SSATransformer {

	public SSAInitialiserAggregator(CodeBody code, SSALocalAccess localAccess) {
		super(code, localAccess);
	}

	@Override
	public int run() {
		for(Statement stmt : new HashSet<>(code)) {
			if (stmt instanceof PopStatement) {
				PopStatement pop = (PopStatement) stmt;
				Expression expr = pop.getExpression();
				if (expr instanceof InvocationExpression) {
					InvocationExpression invoke = (InvocationExpression) expr;
					if (invoke.getOpcode() == Opcodes.INVOKESPECIAL && invoke.getName().equals("<init>")) {
						Expression inst = invoke.getInstanceExpression();
						if (inst instanceof VarExpression) {
							VarExpression var = (VarExpression) inst;
							Local local = var.getLocal();

							CopyVarStatement def = localAccess.defs.get(local);

							Expression rhs = def.getExpression();
							if (rhs instanceof UninitialisedObjectExpression) {
								// here we are assuming that the new object
								// can't be used until it is initialised.
								UninitialisedObjectExpression obj = (UninitialisedObjectExpression) rhs;
								Expression[] args = invoke.getParameterArguments();
								Expression[] newArgs = Arrays.copyOf(args, args.length);
								InitialisedObjectExpression newExpr = new InitialisedObjectExpression(obj.getType(), invoke.getOwner(), invoke.getDesc(), newArgs);
								// remove the old def
								// add a copy statement before the pop (x = newExpr)
								// remove the pop statement
								
								code.remove(def);
								
								CopyVarStatement newCvs = new CopyVarStatement(var, newExpr);
								int index = code.indexOf(pop);
								code.add(index, newCvs);
								code.remove(pop);
								
								// replace pop(x.<init>()) with x := new Klass();
								// remove x := new Klass;
							}
						} else if (inst instanceof MethodArgExpression && ((MethodArgExpression) inst).isThisRef()) {
							// it should be acceptable to do nothing here
						} else {
							System.err.println(code);
							System.err.println(stmt.getId() + ". " + stmt);
							throw new RuntimeException("interesting1 " + inst.getClass());
						}
					}
				}
			}
		
		}
		return 0;
	}
}