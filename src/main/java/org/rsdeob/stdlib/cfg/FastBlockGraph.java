package org.rsdeob.stdlib.cfg;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.tree.LabelNode;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

public class FastBlockGraph extends FlowGraph<BasicBlock, FlowEdge<BasicBlock>> {

	private final Map<LabelNode, BasicBlock> blockLabels;
	
	public FastBlockGraph() {
		blockLabels = new HashMap<>();
	}
	
	public BasicBlock getBlock(LabelNode label) {
		return blockLabels.get(label);
	}
	
	@Override
	public void addVertex(BasicBlock v) {
		super.addVertex(v);
		
		blockLabels.put(v.getLabel(), v);
	}
	
	@Override
	public void removeVertex(BasicBlock v) {
		super.removeVertex(v);
		
		blockLabels.remove(v.getLabel());
		
		if(v.getId().equals("A")) {
			new Exception().printStackTrace();
			System.exit(3);
		}
	}
	
	@Override
	public void addEdge(BasicBlock v, FlowEdge<BasicBlock> e) {
		blockLabels.put(v.getLabel(), v);
		
		super.addEdge(v, e);
	}


//	@Override
//	protected BasicBlock getSource(BasicBlock n, FlowEdge<BasicBlock> e) {
//		return e.src;
//	}
//
//
//	@Override
//	protected BasicBlock getDestination(BasicBlock n, FlowEdge<BasicBlock> e) {
//		return e.dst;
//	}

	@Override
	public FlowEdge<BasicBlock> clone(FlowEdge<BasicBlock> edge, BasicBlock old, BasicBlock newN) {
		BasicBlock src = edge.src;
		BasicBlock dst = edge.dst;
		
		// remap edges
		if(src == old) {
			src = newN;
		}
		if(dst == old) {
			dst = newN;
		}
		
		return edge.clone(src, dst);
	}

	@Override
	public boolean excavate(BasicBlock n) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean jam(BasicBlock prev, BasicBlock succ, BasicBlock n) {
		throw new UnsupportedOperationException();
	}
}