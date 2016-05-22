package org.rsdeob.stdlib.cfg.statopt;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.RootStatement;
import org.rsdeob.stdlib.cfg.StatementVisitor;
import org.rsdeob.stdlib.cfg.expr.StackLoadExpression;
import org.rsdeob.stdlib.cfg.stat.StackDumpStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;

import java.util.*;

public class CopyPropagator {

	private final ControlFlowGraph cfg;
	private final RootStatement root;

	public CopyPropagator(ControlFlowGraph cfg){
		this.cfg = cfg;
		root = cfg.getRoot();

	}

	public void compute() {
	}
	
}