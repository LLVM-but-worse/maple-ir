package org.mapleir.stdlib.ir.transform.impl;

import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.StatementGraph;

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