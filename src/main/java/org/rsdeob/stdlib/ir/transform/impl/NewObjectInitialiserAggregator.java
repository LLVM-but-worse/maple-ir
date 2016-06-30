package org.rsdeob.stdlib.ir.transform.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.Local;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.InitialisedObjectExpression;
import org.rsdeob.stdlib.ir.expr.InvocationExpression;
import org.rsdeob.stdlib.ir.expr.UninitialisedObjectExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.PopStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

public class NewObjectInitialiserAggregator extends Transformer {

	public NewObjectInitialiserAggregator(CodeBody code, CodeAnalytics analytics) {
		super(code, analytics);
	}

	@Override
	public int run() {
		StatementGraph graph = analytics.sgraph;
		DefinitionAnalyser definitions = analytics.definitions;

		int totalChange = 0;
		while (true) {
			int passChange = 0;

			// x = new Klass
			// pop(x.<init>())
			// ...
			// use(x)

			// to
			// x = new Klass()
			// ...
			// use(x)

			List<Statement> list = new ArrayList<>(graph.vertices());
			Collections.sort(list, new Comparator<Statement>() {
				@Override
				public int compare(Statement o1, Statement o2) {
					return Long.compare(o1._getId(), o2._getId());
				}
			});

			for (Statement stmt : list) {
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

								Set<CopyVarStatement> defs = definitions.in(stmt).get(local);
								if (defs.size() == 1) {
									CopyVarStatement def = defs.iterator().next();
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

										CopyVarStatement newCvs = new CopyVarStatement(var, newExpr);
										code.remove(def);
										code.commit();
										
										int index = code.indexOf(pop);
										Statement prev = code.getAt(index - 1);
										Statement next = code.getAt(index);
										code.insert(prev, next, newCvs);
										code.remove(pop);
										code.forceUpdate(newCvs);
										code.commit();
										
										// replace pop(x.<init>()) with x := new Klass();
										// remove x := new Klass;
									}
								} else {
									throw new RuntimeException("interesting2");
								}
							} else {
								throw new RuntimeException("interesting1 " + inst.getClass());
							}
						}
					}
				}
			}

			totalChange += passChange;
			if (passChange <= 0) {
				break;
			}
		}
		return totalChange;
	}
}