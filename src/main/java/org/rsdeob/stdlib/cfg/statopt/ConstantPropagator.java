package org.rsdeob.stdlib.cfg.statopt;

import java.util.HashMap;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ir.RootStatement;

public class ConstantPropagator {
	private final ControlFlowGraph cfg;
	private final HashMap<BasicBlock, DataFlowState> dataFlow;
	private final RootStatement root;

	public ConstantPropagator(ControlFlowGraph cfg){
		this.cfg = cfg;
		root = cfg.getRoot();
		dataFlow = new HashMap<>();
		
		// compute data flow
	}

	// rewrite this later
}