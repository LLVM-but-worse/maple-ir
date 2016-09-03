package org.mapleir.stdlib.ir.transform.impl;

//import org.mapleir.ir.analysis.StatementGraph;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.stdlib.ir.CodeBody;

public class CodeAnalytics {

	public final CodeBody code;
	public final ControlFlowGraph cfg;
	public final Object sgraph;
	public final Object liveness;
	public final Object definitions;

	public CodeAnalytics(CodeBody code, ControlFlowGraph cfg, Object sgraph, Object liveness,
			Object definitions) {
		this.code = code;
		this.cfg = cfg;
		this.sgraph = sgraph;
		this.liveness = liveness;
		this.definitions = definitions;
	}
}