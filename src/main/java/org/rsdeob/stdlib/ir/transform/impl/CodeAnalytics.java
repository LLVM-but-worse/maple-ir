package org.rsdeob.stdlib.ir.transform.impl;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.api.ICodeListener;
import org.rsdeob.stdlib.ir.stat.Statement;

public class CodeAnalytics implements ICodeListener<Statement> {
	public final ControlFlowGraph cfg;
	public final StatementGraph sgraph;

	public final DefinitionAnalyser definitions;
	public final LivenessAnalyser liveness;
	public final UsesAnalyserImpl uses;

	public CodeAnalytics(ControlFlowGraph cfg, StatementGraph sgraph, DefinitionAnalyser definitions, LivenessAnalyser liveness, UsesAnalyserImpl uses) {
		this.cfg = cfg;
		this.sgraph = sgraph;
		this.definitions = definitions;
		this.liveness = liveness;
		this.uses = uses;
	}

	@Override
	public void update(Statement stmt) {
		definitions.update(stmt);
		liveness.update(stmt);
		// update defs before uses.
		uses.update(stmt);
	}

	@Override
	public void replaced(Statement old, Statement n) {
		sgraph.replace(old, n);
		definitions.replaced(old, n);
		liveness.replaced(old, n);
		uses.replaced(old, n);
	}

	@Override
	public void preRemove(Statement n) {
		definitions.preRemove(n);
		liveness.preRemove(n);
		uses.preRemove(n);
	}
	
	@Override
	public void postRemove(Statement n) {
		sgraph.excavate(n);
		definitions.postRemove(n);
		liveness.postRemove(n);
		uses.postRemove(n);
	}

	@Override
	public void insert(Statement p, Statement s, Statement n) {
		sgraph.jam(p, s, n);
		definitions.insert(p, s, n);
		liveness.insert(p, s, n);
		liveness.commit();
		definitions.commit();
		uses.insert(p, s, n);
	}

	@Override
	public void commit() {
		definitions.commit();
		liveness.commit();
		uses.commit();
	}
}