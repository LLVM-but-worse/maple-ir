package org.rsdeob.stdlib.cfg.ir.transform.impl;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;

public class CodeAnalytics {

	public final ControlFlowGraph blockGraph;
	public final StatementGraph statementGraph;
	public final DefinitionAnalyser definitions;
	public final UsesAnalyser uses;
	
	public CodeAnalytics(ControlFlowGraph blockGraph, StatementGraph statementGraph, DefinitionAnalyser definitions, UsesAnalyser uses) {
		this.blockGraph = blockGraph;
		this.statementGraph = statementGraph;
		this.definitions = definitions;
		this.uses = uses;
	}
}