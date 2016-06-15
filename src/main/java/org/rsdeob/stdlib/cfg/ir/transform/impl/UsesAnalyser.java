package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.HashSet;
import java.util.Set;

import org.rsdeob.stdlib.cfg.ir.Local;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.ValueCreator;

public class UsesAnalyser {

	private final NullPermeableHashMap<CopyVarStatement, Set<Statement>> uses;
	
	public UsesAnalyser(StatementGraph graph, DefinitionAnalyser defs) {
		uses = new NullPermeableHashMap<>(new ValueCreator<Set<Statement>>(){
			@Override
			public Set<Statement> create() {
				return new HashSet<>();
			}
		});
		build(graph, defs);
	}
	
	public Set<Statement> getUses(CopyVarStatement def) {
		return uses.get(def);
	}

	private void build(StatementGraph graph, DefinitionAnalyser da) {
		for(Statement stmt : graph.vertices()) {
			StatementVisitor vis = new StatementVisitor(stmt) {
				
				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						VarExpression var = (VarExpression) s;
						Local local = var.getLocal();
						NullPermeableHashMap<Local, Set<CopyVarStatement>> defMaps = da.in(stmt);
						Set<CopyVarStatement> defs = defMaps.get(local);
						for(CopyVarStatement def : defs) {
							uses.getNonNull(def).add(stmt);
						}
					}
					return s;
				}
			};
			vis.visit();
			
		}
	}
}