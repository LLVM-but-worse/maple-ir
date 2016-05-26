package org.rsdeob.stdlib.cfg.ir;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.ConditionalJumpEdge;
import org.rsdeob.stdlib.cfg.edge.DefaultSwitchEdge;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.ImmediateEdge;
import org.rsdeob.stdlib.cfg.edge.SwitchEdge;
import org.rsdeob.stdlib.cfg.edge.TryCatchEdge;
import org.rsdeob.stdlib.cfg.edge.UnconditionalJumpEdge;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;

public class StatementGraphBuilder {

	private final ControlFlowGraph cfg;
	private final StatementGraph sgraph;
	private final LinkedList<BasicBlock> queue;
	private final BitSet finished;
	private final Map<ExceptionRange<BasicBlock>, ExceptionRange<Statement>> rangeMap;
	
	public StatementGraphBuilder(ControlFlowGraph cfg) {
		this.cfg = cfg;
		sgraph = new StatementGraph();
		queue = new LinkedList<>();
		rangeMap = new HashMap<>();
		finished = new BitSet();
	}
	
	void init() {
		for(BasicBlock entry : cfg.getEntries()) {
			queue.add(entry);
		}
		
		// rebuild all ranges
		for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			ExceptionRange<Statement> statementRange = new ExceptionRange<>(null);
			statementRange.setHandler(er.getHandler().getStatements().get(0));

			for(BasicBlock b : er.get()) {
				ListIterator<Statement> it = b.getStatements().listIterator();
				while(it.hasNext()) {
					statementRange.addVertex(it.next());
				}
			}
			
			statementRange.getTypes().addAll(er.getTypes());
			statementRange.hashCode(); // calc and store
			rangeMap.put(er, statementRange);
		}
	}
	
	void processQueue() {
		while(!queue.isEmpty()) {
			process(queue.pop());
		}
	}
	
	void process(BasicBlock block) {
		ListIterator<Statement> it = block.getStatements().listIterator();
		Statement prev = null;
		while(it.hasNext()) {
			Statement newStmt = it.next();
			sgraph.addVertex(newStmt);
			
			if(prev != null) {
				sgraph.addEdge(prev, new ImmediateEdge<Statement>(prev, newStmt));
			}
			
			prev = newStmt;
		}
		
		for(FlowEdge<BasicBlock> succEdge : block.getSuccessors()) {
			BasicBlock succ = succEdge.dst;
			queue.add(succ);
			
			Statement first = succ.getStatements().get(0);
			sgraph.addEdge(prev, createEdge(succEdge, prev, first));
		}
	}
	
	FlowEdge<Statement> createEdge(FlowEdge<BasicBlock> edge, Statement src, Statement dst) {
		FlowEdge<Statement> newEdge = null;
		if(edge instanceof ConditionalJumpEdge) {
			newEdge = new ConditionalJumpEdge<Statement>(src, dst, ((ConditionalJumpEdge<BasicBlock>) edge).opcode);
		} else if(edge instanceof UnconditionalJumpEdge) {
			newEdge = new UnconditionalJumpEdge<Statement>(src, dst, ((UnconditionalJumpEdge<BasicBlock>) edge).opcode);
		} else if(edge instanceof DefaultSwitchEdge) {
			newEdge = new DefaultSwitchEdge<Statement>(src, dst, ((DefaultSwitchEdge<BasicBlock>) edge).insn);
		} else if(edge instanceof SwitchEdge) {
			newEdge = new SwitchEdge<Statement>(src, dst, ((SwitchEdge<BasicBlock>) edge).insn, ((SwitchEdge<BasicBlock>) edge).value);
		} else if(edge instanceof ImmediateEdge) {
			newEdge = new ImmediateEdge<Statement>(src, dst);
		} else if(edge instanceof TryCatchEdge) {
			newEdge = new TryCatchEdge<Statement>(src, rangeMap.get(((TryCatchEdge<BasicBlock>) edge).erange));
		} else {
			throw new UnsupportedOperationException(String.format("%s", edge));
		}
		
		return newEdge;
	}
	
	public StatementGraph build() {
		if(sgraph.size() == 0) {
			init();
			processQueue();
		}
		return sgraph;
	}
}