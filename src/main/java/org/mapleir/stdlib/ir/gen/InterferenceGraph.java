package org.mapleir.stdlib.ir.gen;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.ssa.SSALivenessAnalyser;

public class InterferenceGraph {

	private final NullPermeableHashMap<Local, Set<Local>> map;
	
	private InterferenceGraph() {
		map = new NullPermeableHashMap<>(new SetCreator<>());
	}
	
	private void interference(Local l, Local l2) {
		if(l != l2) {
			map.getNonNull(l).add(l2);
			map.getNonNull(l2).add(l);
		}
	}
	
	public Set<Local> getInterferingVariables(Local l) {
		return map.getNonNull(l);
	}
	
	public static InterferenceGraph build(SSALivenessAnalyser liveness) {
		InterferenceGraph ig = new InterferenceGraph();
		for(BasicBlock b : liveness.getGraph().vertices()) {
			Map<Local, Boolean> out = liveness.out(b);
			
			for(Statement stmt : b.getStatements()) {
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					Local def = copy.getVariable().getLocal();
					Expression e = copy.getExpression();
					if(out.containsKey(def)) {
						for(Entry<Local, Boolean> entry : out.entrySet()) {
							if(entry.getValue()) {
								ig.interference(def, entry.getKey());
							}
						}
					}
					if(e instanceof PhiExpression) {
						
					} else if(!(e instanceof VarExpression)) {
						for(Statement s : Statement.enumerate(e)) {
							if(s instanceof VarExpression) {
								VarExpression v = (VarExpression) s;
							}
						}
					}
				}
			}
		}
		return ig;
	}
}