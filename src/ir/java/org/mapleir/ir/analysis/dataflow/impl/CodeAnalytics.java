package org.mapleir.ir.analysis.dataflow.impl;

import org.mapleir.ir.analysis.StatementGraph;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeBody;

public class CodeAnalytics {

	public final CodeBody code;
	public final ControlFlowGraph cfg;
	public final StatementGraph sgraph;
	public final LivenessAnalyser liveness;
	public final DefinitionAnalyser definitions;
	
	public CodeAnalytics(CodeBody code, ControlFlowGraph cfg, StatementGraph sgraph, LivenessAnalyser liveness,
			DefinitionAnalyser definitions) {
		this.code = code;
		this.cfg = cfg;
		this.sgraph = sgraph;
		this.liveness = liveness;
		this.definitions = definitions;
	}
}