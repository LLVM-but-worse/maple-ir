package org.rsdeob.stdlib.cfg;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.ValueCreator;

public class DominanceComputor {

	// TODO: tarjans solution
	// for a block b, map.get(b) == dominators of/dom(b)
	public static Map<BasicBlock, Set<BasicBlock>> compute(ControlFlowGraph cfg) {
		NullPermeableHashMap<BasicBlock, Set<BasicBlock>> map = new NullPermeableHashMap<>(new ValueCreator<Set<BasicBlock>>() {
			@Override
			public Set<BasicBlock> create() {
				return new HashSet<>();
			}
		});
		
		Set<BasicBlock> blocks = new HashSet<BasicBlock>(cfg.blocks());
		for(BasicBlock b : blocks) {
			map.put(b, blocks);
		}
		
		LinkedList<BasicBlock> queue = new LinkedList<>();
		queue.add(cfg.getEntry());
		
		while(!queue.isEmpty()) {
			BasicBlock b = queue.pop();
			
			Set<BasicBlock> n = new HashSet<>();
			for(FlowEdge fe : b.getPredecessors()) {
				BasicBlock pred = fe.src;
				if(n.isEmpty()) {
					n.addAll(map.get(pred));
				} else {
					n.retainAll(map.get(pred));
				}
			}
			n.add(b);
			
			Set<BasicBlock> old = map.get(b);
			
			if(!old.equals(n)) {
				map.put(b, n);
				for(FlowEdge se : b.getSuccessors()) {
					BasicBlock succ = se.dst;
					if(!queue.contains(succ)) {
						queue.addLast(succ);
					}
				}
			}
		}
		return map;
	}
}