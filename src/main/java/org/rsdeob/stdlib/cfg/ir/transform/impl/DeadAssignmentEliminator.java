package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.ArrayStoreStatement;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.FieldStoreStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class DeadAssignmentEliminator {

	public static void run(RootStatement root, StatementGraph graph, LivenessAnalyser la) {
		for(Statement stmt : graph.vertices()) {

			if(stmt instanceof CopyVarStatement) {
				Map<String, Boolean> out = la.out(stmt);
				
				CopyVarStatement copy = (CopyVarStatement) stmt;
				VarExpression var = copy.getVariable();
				
				if(!out.get(var.toString())) {
					root.delete(root.indexOf(copy));
				}
			}
			
//			else if(stmt instanceof PopStatement) {
//				Expression pop = ((PopStatement) stmt).getExpression();
//				if(pop instanceof VarExpression || pop instanceof ConstantExpression) {
//					root.delete(root.indexOf(stmt));
//				}
//			}
		}
	}
	
	public static Statement findTail(RootStatement root, Statement stmt) {
		AtomicReference<Statement> tail = new AtomicReference<Statement>();
		StatementVisitor vis = new StatementVisitor(root) {
			
			@Override
			public Statement visit(Statement s) {
				if(stmt == s) {
					Statement root = null;
					if(getDepth() >= 2) {
						root = getCurrent(2);
					} else {
						root = s;
					}
					
					for(Statement t=root;;) {
						if(t == stmt) {
							tail.set(root);
							break;
						}
						
						if(t instanceof CopyVarStatement) {
							t = ((CopyVarStatement) t).getExpression();
							continue;
						} else if(t instanceof FieldStoreStatement) {
							t = ((FieldStoreStatement) t).getValueExpression();
							continue;
						} else if(t instanceof ArrayStoreStatement) {
							t = ((ArrayStoreStatement) t).getArrayExpression();
							continue;
						}
						
						break;
					}
					
					_break();
				}
				return s;
			}
		};
		vis.visit();
		return tail.get();
	}
}