package org.rsdeob.stdlib.ir.transform.impl;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementList;
import org.rsdeob.stdlib.ir.api.ICodeListener;
import org.rsdeob.stdlib.ir.stat.Statement;

public class CodeAnalytics implements ICodeListener<Statement> {

	public final StatementList root;
	public final ControlFlowGraph blockGraph;

	public final StatementGraph statementGraph;
	public final DefinitionAnalyser definitions;
	public final LivenessAnalyser liveness;
	public final UsesAnalyser uses;

	public CodeAnalytics(StatementList root, ControlFlowGraph blockGraph, StatementGraph statementGraph, DefinitionAnalyser definitions, LivenessAnalyser liveness, UsesAnalyser uses) {
		this.root = root;
		this.blockGraph = blockGraph;
		this.statementGraph = statementGraph;
		this.definitions = definitions;
		this.liveness = liveness;
		this.uses = uses;
	}

	@Override
	public void updated(Statement stmt) {
		definitions.updated(stmt);
		liveness.updated(stmt);
		uses.updated(stmt);
	}

	@Override
	public void replaced(Statement old, Statement n) {
		definitions.replaced(old, n);
		liveness.replaced(old, n);
		uses.replaced(old, n);
	}

	@Override
	public void added(Statement n) {
		definitions.added(n);
		liveness.added(n);
		uses.added(n);
	}

	@Override
	public void removed(Statement n) {
		definitions.removed(n);
		liveness.removed(n);
		uses.removed(n);
	}

	@Override
	public void commit() {
		definitions.commit();
		liveness.commit();
		uses.commit();
	}
}