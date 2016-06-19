package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.Map.Entry;
import java.util.Set;

import org.rsdeob.stdlib.cfg.ir.Local;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;

public class UsesAnalyser {

	private final RootStatement root;
	private final StatementGraph graph;
	private final DefinitionAnalyser definitions;
	private final NullPermeableHashMap<CopyVarStatement, Set<Statement>> uses;
	private final NullPermeableHashMap<Statement, Set<VarExpression>> used;
	
	public UsesAnalyser(RootStatement root, StatementGraph graph, DefinitionAnalyser defs) {
		this.root = root;
		this.graph = graph;
		definitions = defs;
		uses = new NullPermeableHashMap<>(new SetCreator<>());
		used = new NullPermeableHashMap<>(new SetCreator<>());
		build();
	}
	
	public Set<Statement> getUses(CopyVarStatement def) {
		return uses.get(def);
	}
	
	public Set<VarExpression> getVarsUsed(Statement stmt) {
		return used.get(stmt);
	}
	
	public void update(Statement stmt) {
		Set<VarExpression> set = used.getNonNull(stmt);
		
		StatementVisitor vis = new StatementVisitor(stmt) {
			@Override
			public Statement visit(Statement s) {
				if(s instanceof VarExpression) {
					VarExpression var = (VarExpression) s;
					set.add(var);
					
					Local local = var.getLocal();
					NullPermeableHashMap<Local, Set<CopyVarStatement>> defMaps = definitions.in(stmt);
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
	
	public void remove(Statement stmt) {
		uses.remove(stmt);
		used.remove(stmt);
		
		for(Entry<CopyVarStatement, Set<Statement>> e : uses.entrySet()) {
			e.getValue().remove(stmt);
		}
		
		if(stmt instanceof VarExpression) {
			for(Entry<Statement, Set<VarExpression>> e : used.entrySet()) {
				e.getValue().remove(stmt);
			}
		}
	}

	private void build() {
		for(Statement stmt : graph.vertices()) {
			Set<VarExpression> set = used.getNonNull(stmt);
			
			StatementVisitor vis = new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						VarExpression var = (VarExpression) s;
						set.add(var);
						
						Local local = var.getLocal();
						NullPermeableHashMap<Local, Set<CopyVarStatement>> defMaps = definitions.in(stmt);
						Set<CopyVarStatement> defs = defMaps.get(local);
						System.out.println("uses of " + stmt.getId() +".  + " + stmt + " var= " + local + " = " + defs);
						try {
							for(CopyVarStatement def : defs) {
								uses.getNonNull(def).add(stmt);
							}
						} catch(Exception e) {
							System.out.println(UsesAnalyser.this.root);
							throw e;
						}
					}
					return s;
				}
			};
			vis.visit();
			
		}
	}
}