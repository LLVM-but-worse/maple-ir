package org.rsdeob.stdlib.cfg.algo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.FlowEdge;
import org.rsdeob.stdlib.cfg.util.LabelHelper;

public class DepthFirstSorter {

	private final ControlFlowGraph cfg;
	private final boolean[] visited;
	private final LinkedList<BasicBlock> preOrder;
	private final LinkedList<BasicBlock> postOrder;
	
	public DepthFirstSorter(ControlFlowGraph cfg) {
		this.cfg = cfg;
		visited = new boolean[cfg.blocks().size()];
		preOrder = new LinkedList<>();
		postOrder = new LinkedList<>();
		
		visit();
	}
	
	public List<BasicBlock> getPreOrder() {
		return new ArrayList<>(preOrder);
	}
	
	public List<BasicBlock> getPostOrder() {
		return new ArrayList<>(postOrder);
	}
	
	public List<BasicBlock> getReversePostOrder() {
		LinkedList<BasicBlock> list = new LinkedList<>();
		for(BasicBlock b : postOrder) {
			list.push(b);
		}
		return list;
	}
	
	private void visit() {
		for(BasicBlock b : cfg.blocks()) {
			if(!visited[id(b)]) {
				visit(b);
			}
		}
	}
	
	private void visit(BasicBlock node) {
		visited[id(node)] = true;
		preOrder.add(node);
		for(FlowEdge se : node.getSuccessors()) {
			BasicBlock succ = se.dst;
			if(!visited[id(succ)]) {
				visit(succ);
			}
		}
		postOrder.add(node);
	}
	
	private static int id(BasicBlock b) {
		return LabelHelper.numeric(b.getId()) - 1;
	}
}