package org.rsdeob.stdlib.ir.transform.impl;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.api.ICodeListener;
import org.rsdeob.stdlib.ir.stat.Statement;

public class CodeAnalytics implements ICodeListener<Statement> {
	public final ControlFlowGraph blockGraph;
	public final StatementGraph stmtGraph;

	public final DefinitionAnalyser definitions;
	public final LivenessAnalyser liveness;
	public final UsesAnalyser uses;

	public CodeAnalytics(ControlFlowGraph blockGraph, StatementGraph statementGraph, DefinitionAnalyser definitions, LivenessAnalyser liveness, UsesAnalyser uses) {
		this.blockGraph = blockGraph;
		this.stmtGraph = statementGraph;
		this.definitions = definitions;
		this.liveness = liveness;
		this.uses = uses;
	}

	@Override
	public void updated(Statement stmt) {
		definitions.updated(stmt);
		liveness.updated(stmt);
		definitions.commit(); // DefinitionsAnalyzer has to be committed for uses to be updated
		uses.updated(stmt);
	}

	@Override
	public void replaced(Statement old, Statement n) {
		definitions.replaced(old, n);
		liveness.replaced(old, n);
		uses.replaced(old, n);
		stmtGraph.replace(old, n);
	}

	@Override
	public void added(Statement n) {
		definitions.added(n);
		liveness.added(n);
		uses.added(n);
		stmtGraph.addVertex(n);
	}

	@Override
	public void removed(Statement n) {
		definitions.removed(n);
		liveness.removed(n);
		uses.removed(n);
		if (!stmtGraph.excavate(n)) {
			definitions.updated(n);
			liveness.updated(n);
		}
	}

	@Override
	public void commit() {
		definitions.commit();
		liveness.commit();
		uses.commit();
	}
}