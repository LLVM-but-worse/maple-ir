package org.mapleir.stdlib.cfg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;
import org.objectweb.asm.tree.LabelNode;

public class FastBlockGraph extends FlowGraph<BasicBlock, FlowEdge<BasicBlock>> {

	private final Map<LabelNode, BasicBlock> blockLabels;
	
	public FastBlockGraph() {
		blockLabels = new HashMap<>();
	}
	
	public FastBlockGraph(FastBlockGraph g) {
		super(g);
		blockLabels = new HashMap<>(g.blockLabels);
	}
	
	public BasicBlock getBlock(LabelNode label) {
		return blockLabels.get(label);
	}
	
	@Override
	public void clear() {
		super.clear();
		blockLabels.clear();
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
		if(!containsVertex(n)) {
			return false;
		}
		for(ExceptionRange<BasicBlock> r : getRanges()) {
			if(r.getHandler() == n) {
				return false;
			}
		}
		Set<FlowEdge<BasicBlock>> predEdges = getReverseEdges(n);
		
		Set<BasicBlock> preds = new HashSet<>();
		for(FlowEdge<BasicBlock> e : predEdges) {
			preds.add(e.src);
		}
		
		Set<FlowEdge<BasicBlock>> succs = getEdges(n);
		
		Set<BasicBlock> realSuccs = new HashSet<>();
		Set<TryCatchEdge<BasicBlock>> tcs = new HashSet<>();
		for(FlowEdge<BasicBlock> p : succs) {
			if(!(p instanceof TryCatchEdge)) {
				realSuccs.add(p.dst);
			} else {
				tcs.add((TryCatchEdge<BasicBlock>) p);
			}
		}
		
		if(predEdges.size() >= 1 && realSuccs.size() == 1) {
			BasicBlock succ = realSuccs.iterator().next();
			
			// clone the real edges
			for(FlowEdge<BasicBlock> pe : predEdges) {
				BasicBlock pred = pe.src;
				if(!(pe instanceof TryCatchEdge)) {
					FlowEdge<BasicBlock> newEdge = clone(pe, n, succ);
					addEdge(pred, newEdge);
				}
				
				for(TryCatchEdge<BasicBlock> tce : tcs) {
					TryCatchEdge<BasicBlock> newTce = new TryCatchEdge<>(pred, tce.erange);
					addEdge(pred, newTce);
				}
			}
			
			removeVertex(n);
		} else if(predEdges.size() == 0 && realSuccs.size() == 1) {
			removeVertex(n);
		} else {
			throw new UnsupportedOperationException(n.toString() + "|||" + predEdges + "|||" + succs);
		}
		
		for(BasicBlock pred : preds) {
			Set<FlowEdge<BasicBlock>> predPreds = getReverseEdges(pred);
			if(predPreds.size() == 0) {
				getEntries().add(pred);
			}
		}
		

		return true;
	}

	@Override
	public boolean jam(BasicBlock prev, BasicBlock succ, BasicBlock n) {
		throw new UnsupportedOperationException();
	}

	@Override
	public FlowEdge<BasicBlock> invert(FlowEdge<BasicBlock> edge) {
		return edge.clone(edge.dst, edge.src);
	}

	@Override
	public FastBlockGraph copy() {
		return new FastBlockGraph(this);
	}
}