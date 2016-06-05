package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.Set;

import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;

public class CopyDefinitionAnalyser extends DefinitionAnalyser {

	public CopyDefinitionAnalyser(StatementGraph graph, MethodNode m) {
		super(graph, m);
	}
	
	@Override
	protected void propagate(Statement n, NullPermeableHashMap<String, Set<CopyVarStatement>> in, NullPermeableHashMap<String, Set<CopyVarStatement>> out) {
		super.propagate(n, in, out);
		// if there is a copy
		//  var1 = var1 + 1;
		// then it is obvious that the out set should
		// contain var1 even though it is redefined.
		final String lhs;
		if(n instanceof CopyVarStatement) {
			lhs = ((CopyVarStatement) n).getVariable().toString();
		} else {
			lhs = "";
		}
		StatementVisitor vis = new StatementVisitor(n) {
			@Override
			public Statement visit(Statement s) {
				if(s instanceof VarExpression && !s.toString().equals(lhs)) {
					out.getNonNull(s.toString()).clear();
				}
				return s;
			}
		};
		vis.visit();
	}
}