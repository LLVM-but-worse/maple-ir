package org.mapleir.ir.analysis.dataflow;

import java.util.HashSet;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdges;
import org.mapleir.stdlib.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;

public class StatementBlockDataFlowProvider implements DataFlowProvider<Statement, FlowEdge<Statement>>{

	private final ControlFlowGraph cfg;
	
	public StatementBlockDataFlowProvider(ControlFlowGraph cfg) {
		this.cfg = cfg;
	}
	
	Statement first(FlowEdge<BasicBlock> e) {
		return e.dst.getAt(0);
	}

	@Override
	public Set<FlowEdge<Statement>> getSuccessors(Statement n) {
		Set<FlowEdge<Statement>> set = new HashSet<>();
		BasicBlock b = n.getBlock();
		int idx = b.indexOf(n);
		if(idx != (b.size() - 1)) {
			Statement s = b.getAt(idx + 1);
			set.add(new ImmediateEdge<>(n, s));
			
			for(FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				if(e.getType() == FlowEdges.TRYCATCH) {
					// TODO: handle empty blocks.
					set.add(new TryCatchEdge<>(n, first(e)));
				}
			}
		} else {
			// all edges
			for(FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				set.add(FlowEdges.mock(e, n, first(e)));
			}
		}
		
		
		return set;
	}

	@Override
	public Set<FlowEdge<Statement>> getPredecessors(Statement n) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<Statement> getNodes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<Statement> getHandlers() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<Statement> getHeads() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<Statement> getTails() {
		// TODO Auto-generated method stub
		return null;
	}
}