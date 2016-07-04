package org.rsdeob.stdlib.ir.transform.impl;

import java.util.Set;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.api.ICodeListener;
import org.rsdeob.stdlib.ir.stat.Statement;

public class CodeAnalytics implements ICodeListener<Statement> {
	public final ControlFlowGraph cfg;
	public final StatementGraph sgraph;

	public final DefinitionAnalyser definitions;
	public final LivenessAnalyser liveness;
	public final UsesAnalyser uses;

	public CodeAnalytics(ControlFlowGraph cfg, StatementGraph sgraph, DefinitionAnalyser definitions, LivenessAnalyser liveness, UsesAnalyser uses) {
		this.cfg = cfg;
		this.sgraph = sgraph;
		this.definitions = definitions;
		this.liveness = liveness;
		this.uses = uses;
	}

	@Override
	public void update(Statement stmt) {
//		System.out.println("CodeAnalytics.update(" + stmt + ")");
		definitions.update(stmt);
		liveness.update(stmt);
		definitions.commit();
		liveness.commit();
		// update defs before uses.
		uses.update(stmt);
	}

	@Override
	public void replaced(Statement old, Statement n) {
//		System.out.println("CodeAnalytics.replaced(" + old + ", " + n +  ")");
		sgraph.replace(old, n);
		definitions.replaced(old, n);
		liveness.replaced(old, n);
		definitions.commit();
		uses.replaced(old, n);
	}

	@Override
	public void removed(Statement n) {
//		System.out.println("CodeAnalytics.remove(" + n + ")");
		definitions.removed(n);
		liveness.removed(n);
		Set<FlowEdge<Statement>> preds = sgraph.getReverseEdges(n);
		Set<FlowEdge<Statement>> succs = sgraph.getEdges(n);
		if (sgraph.excavate(n)) {
			definitions.commit();
			liveness.commit();
			uses.removed(n);
			for(FlowEdge<Statement> p : preds) {
				if(p.src != n)
					definitions.appendQueue(p.src);
			}
			for(FlowEdge<Statement> s : succs) {
				if(s.dst != n)
					liveness.appendQueue(s.dst);
			}
		}
	}

	@Override
	public void insert(Statement p, Statement s, Statement n) {
//		System.out.println("CodeAnalytics.insert(" + p + ", " +s + ", " + n +")");
		sgraph.jam(p, s, n);
		definitions.insert(p, s, n);
		liveness.insert(p, s, n);
		liveness.commit();
		definitions.commit();
		uses.insert(p, s, n);
	}

	@Override
	public void commit() {
//		System.out.println("CodeAnalytics.commit()");
		definitions.commit();
		liveness.commit();
		uses.commit();
	}
}