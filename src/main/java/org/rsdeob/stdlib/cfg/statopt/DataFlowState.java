package org.rsdeob.stdlib.cfg.statopt;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;

import java.util.HashSet;

public class DataFlowState {
	private final ControlFlowGraph cfg;
	private final BasicBlock block;

	public final HashSet<Assignment> in;
	public final HashSet<Assignment> out;
	public final HashSet<Assignment> gen;
	public final HashSet<Assignment> kill;

	public DataFlowState(ControlFlowGraph cfg, BasicBlock b) {
		this.cfg = cfg;
		this.block = b;

		in = new HashSet<>();
		out = new HashSet<>();
		gen = new HashSet<>();
		kill = new HashSet<>();

		// compute sets
	}
}
