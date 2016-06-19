package org.rsdeob.stdlib.cfg.ir.transform;

import java.util.Iterator;
import java.util.Set;

import org.rsdeob.stdlib.collections.graph.FastGraphEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

public abstract class ForwardsFlowAnalyser<N extends FastGraphVertex, E extends FastGraphEdge<N>, S> extends DataAnalyser<N, E, S>{

	public ForwardsFlowAnalyser(FlowGraph<N, E> graph) {
		super(graph);
	}
	
//	@Override
//	public void removeImpl(N n) {
//		for(E e : graph.getEdges(n)) {
//			N succ = e.dst;
//			queue.add(succ);
//			updateImpl(succ);
//		}
//	}
	
	@Override
	public void remove(N n) {
		super.remove(n);
		
		for(E predEdge : graph.getReverseEdges(n)) {
			queue.add(predEdge.src);
		}
	}
	
	@Override
	public void updateImpl(N n) {
		replaceImpl(n, n);
	}
	
	@Override
	public void replaceImpl(N old, N n) {
		if(graph.getEntries().contains(n)) {
			in.put(n, newEntryState());
			out.put(n, newState());
		} else {
			in.put(n, newState());
			out.put(n, newState());
						
			for(E predEdge : graph.getReverseEdges(old)) {
				queue.add(predEdge.src);
			}
		}
	}
	
	@Override
	protected void init() {
		super.init();
		
		for(N entry : graph.getEntries()) {
			in.put(entry, newEntryState());
			queue.add(entry);
		}
	}

	@Override
	public void processQueue() {
		while(!queue.isEmpty()) {
			N n = queue.iterator().next();
			queue.remove(n);

			S oldOut = newState();
			S currentOut = out.get(n);
			copy(currentOut, oldOut);
			
			S currentIn = in.get(n);
			Set<E> preds = graph.getReverseEdges(n);
			
			if(preds.size() == 1) {
				N pred = preds.iterator().next().src;
				S predOut = out.get(pred);
				copy(predOut, currentIn);
			} else if(preds.size() > 1) {
				Iterator<E> it = preds.iterator();
				
				N firstPred = it.next().src;
				copy(out.get(firstPred), currentIn);
				
				while(it.hasNext()) {
					S merging = out.get(it.next().src);
					merge(currentIn, merging);
				}
			}
			
			// System.out.println("in: " + currentIn);
			propagate(n, currentIn, currentOut);
			// System.out.println("out: " + currentOut);
			
			// if there was a change, queue the successors.
			if (!equals(currentOut, oldOut)) {
				for (E e : graph.getEdges(n)) {
					queue.add(e.dst);
				}
			}
		}
	}
	
	@Override
	protected abstract S newState();

	@Override
	protected abstract S newEntryState();

	@Override
	protected abstract void merge(S in1, S in2, S out);
	
	@Override
	protected abstract void copy(S src, S dst);
	
	@Override
	protected abstract boolean equals(S s1, S s2);
	
	@Override
	protected abstract void propagate(N n, S in, S out);
}