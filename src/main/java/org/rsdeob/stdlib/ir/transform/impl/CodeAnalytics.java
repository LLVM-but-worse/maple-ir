package org.rsdeob.stdlib.ir.transform.impl;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.ir.RootStatement;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.stat.Statement;

public class CodeAnalytics {

	public final RootStatement root;
	public final ControlFlowGraph blockGraph;
	public final StatementGraph statementGraph;
	public final DefinitionAnalyser definitions;
	public final LivenessAnalyser liveness;
	public final UsesAnalyser uses;

	public CodeAnalytics(RootStatement root, ControlFlowGraph blockGraph, StatementGraph statementGraph, DefinitionAnalyser definitions, LivenessAnalyser liveness, UsesAnalyser uses) {
		this.root = root;
		this.blockGraph = blockGraph;
		this.statementGraph = statementGraph;
		this.definitions = definitions;
		this.liveness = liveness;
		this.uses = uses;
	}

	public void update(Statement stmt) {
		definitions.update(root);
		liveness.update(root);
		definitions.processQueue();
		liveness.processQueue();
		uses.update(root);
	}
}