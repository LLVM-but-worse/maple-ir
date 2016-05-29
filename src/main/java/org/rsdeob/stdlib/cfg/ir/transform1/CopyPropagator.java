package org.rsdeob.stdlib.cfg.ir.transform1;

import java.util.Map;
import java.util.Set;

import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class CopyPropagator {

	private final StatementGraph sgraph;
	private final VariableStateComputer states;
	
	public CopyPropagator(StatementGraph sgraph, VariableStateComputer states) {
		this.sgraph = sgraph;
		this.states = states;
		buildUseMap();
	}
	
	private void buildUseMap() {
		
		for(Statement stmt : sgraph.vertices()) {
			final VarExpression lhs;
			if(stmt instanceof CopyVarStatement) {
				lhs = ((CopyVarStatement) stmt).getVariable();
			} else {
				lhs = null;
			}
			
			StatementVisitor impl = new StatementVisitor(stmt) {
				@Override
				public void visit(Statement s) {
					// i.e. it's not the lhs of a copy statement
					// and its a var usage statement.
					//  != is used here as we need to check
					//  the references and not the values, since
					//  an  x := x; statement combination is possible.
					if(lhs != s && s instanceof VarExpression) {
						VarExpression v2 = (VarExpression) s;
						String name = VariableStateComputer.createVariableName(v2);
					}
				}
			};
			impl.visit();
		}
	}
	
	private void propagate() {
		for(Statement stmt : sgraph.vertices()) {
			Map<String, Set<CopyVarStatement>> in = states.in(stmt);
			
			StatementVisitor impl = new StatementVisitor(stmt) {
				@Override
				public void visit(Statement stmt) {
					if(stmt instanceof VarExpression) {
						VarExpression var = (VarExpression) stmt;
						String name = VariableStateComputer.createVariableName(var);
						Set<CopyVarStatement> defs = in.get(name);
						
						if(defs.size() == 1) {
							Expression def = defs.iterator().next().getExpression();
							if(def instanceof ConstantExpression || def instanceof VarExpression) {
								int d = getDepth();
								getCurrent(d).overwrite(def, getCurrentPtr(d));
							} else {
								
							}
						}
					}
				}
			};
			impl.visit();
		}
	}
	
	private void eliminateDeadStatements() {
		
	}
	
	public void run() {
		propagate();
	}
}