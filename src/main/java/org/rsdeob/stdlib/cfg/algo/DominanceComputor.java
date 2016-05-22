package org.rsdeob.stdlib.cfg.algo;

import java.util.*;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.FlowEdge;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.ValueCreator;

// TODO: tarjans solution
// for a block b, map.get(b) == dominators of/dom(b)
public class DominanceComputor {

	private final NullPermeableHashMap<BasicBlock, Set<BasicBlock>> dominators = 
			new NullPermeableHashMap<>(new ValueCreator<Set<BasicBlock>>() {
		@Override public Set<BasicBlock> create() {return new HashSet<>();}});
	
	private final Map<BasicBlock, Set<BasicBlock>> strictDominators = new HashMap<>();
	private final Map<BasicBlock, BasicBlock> immediateDomintors = new HashMap<>();
	private final Map<BasicBlock, Set<BasicBlock>> dominanceFrontiers = new HashMap<>();
	private final Map<BasicBlock, Integer> postOrderNumbers = new HashMap<>();
	
	public DominanceComputor(ControlFlowGraph cfg) {
		computeDominators(cfg);
		computeImmediateDominators(cfg);
		computeFrontiers(cfg);
	}
	
	public Set<BasicBlock> doms(BasicBlock b) {
		return dominators.get(b);
	}
	
	public BasicBlock idom(BasicBlock b) {
		return immediateDomintors.get(b);
	}
	
	public Set<BasicBlock> sdoms(BasicBlock b) {
		if(strictDominators.containsKey(b)) {
			return strictDominators.get(b);
		} else {
			Set<BasicBlock> sdom = new HashSet<>(doms(b));
			sdom.remove(b);
			strictDominators.put(b, sdom);
			return sdom;
		}
	}
	
	private void computeImmediateDominators(ControlFlowGraph cfg) {
		Set<BasicBlock> eset = new HashSet<>();
		BasicBlock entry = cfg.getEntry();
		eset.add(entry);
		dominators.put(entry, eset);
		
		List<BasicBlock> rpo = new ArrayList<>(new DepthFirstSorter(cfg).getReversePostOrder());
		int i = 0;
		for(BasicBlock b : rpo) {
			postOrderNumbers.put(b, i++);
		}
		rpo.remove(entry);
		
		boolean changed = true;
		while(changed) {
			changed = false;
			
			for(BasicBlock b : rpo) {
				BasicBlock oldIdom = immediateDomintors.get(b);
				BasicBlock idom = null;
				
				Iterator<FlowEdge> it = b.getPredecessors().iterator();
				while(it.hasNext()) {
					FlowEdge fe = it.next();
					BasicBlock p = fe.src;
					
					if(idom(p) != null) {
						idom = intersect(idom, p);
						if(idom == null) {
							break;
						}
					}
				}
				
				if(idom == null) {
					idom = b;
				}
				
				if(oldIdom != idom) {
					immediateDomintors.put(b, idom);
					changed = true;
				}
			}
			
			if(!changed) {
				break;
			}
		}
	}
	
	private BasicBlock intersect(BasicBlock n1, BasicBlock n2) {
		if(n1 == null) {
			return n2;
		} else if(n2 == null) {
			return n1;
		}
		
		int f1 = postOrderNumbers.get(n1);
		int f2 = postOrderNumbers.get(n1);
		
		while(f1 != f2) {
			if(f1 > f2) {
				BasicBlock f3 = n1;
				n1 = immediateDomintors.get(n1);
				if(f3 == n1) {
					return null;
				}
				f1 = postOrderNumbers.get(n1);
			} else {
				BasicBlock f3 = n2;
				n2 = immediateDomintors.get(n2);
				if(f3 == n2) {
					return null;
				}
				f2 = postOrderNumbers.get(n2);
			}
		}
		
		
		return n1;
	}
	
	private void computeDominators(ControlFlowGraph cfg) {
		Set<BasicBlock> blocks = new HashSet<BasicBlock>(cfg.blocks());
		for(BasicBlock b : blocks) {
			dominators.put(b, blocks);
		}
		
		LinkedList<BasicBlock> queue = new LinkedList<>();
		queue.add(cfg.getEntry());
		
		while(!queue.isEmpty()) {
			BasicBlock b = queue.pop();
			
			Set<BasicBlock> n = new HashSet<>();
			for(FlowEdge fe : b.getPredecessors()) {
				BasicBlock pred = fe.src;
				if(n.isEmpty()) {
					n.addAll(dominators.get(pred));
				} else {
					n.retainAll(dominators.get(pred));
				}
			}
			n.add(b);
			
			Set<BasicBlock> old = dominators.get(b);
			
			if(!old.equals(n)) {
				dominators.put(b, n);
				for(FlowEdge se : b.getSuccessors()) {
					BasicBlock succ = se.dst;
					if(!queue.contains(succ)) {
						queue.addLast(succ);
					}
				}
			}
		}
	}
	
	private void computeFrontiers(ControlFlowGraph cfg) {
		for(BasicBlock b : cfg.blocks()) {
			BasicBlock bIdom = immediateDomintors.get(b);
			if(b.getPredecessors().size() >= 2) {
				for(FlowEdge fe : b.getPredecessors()) {
					BasicBlock pred = fe.src;
					
					BasicBlock runnerIdom = pred;
					Set<BasicBlock> runnerSet = new HashSet<>();
					
					while(runnerIdom != bIdom) {
						runnerSet.add(b);
						runnerIdom = immediateDomintors.get(runnerIdom);
					}
					
					dominanceFrontiers.put(b, runnerSet);
				}
			}
		}
	}	
}