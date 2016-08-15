package org.mapleir.ir.analysis.dataflow;

import java.util.Iterator;
import java.util.Set;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;

public abstract class ForwardsFlowAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> extends DataFlowAnalyser<N, E, S> {
	
	public ForwardsFlowAnalyser(FlowGraph<N, E> graph, boolean commit) {
		super(graph, commit);
	}
	
	public ForwardsFlowAnalyser(FlowGraph<N, E> graph) {
		super(graph);
	}
	
	@Override
	protected void init() {
		Set<N> heads = provider.getHeads();
		Set<N> handlers = provider.getHandlers();
		
		for(N n : provider.getNodes()) {
			if(heads.contains(n) || handlers.contains(n)) {
				in.put(n, newEntryState());
				out.put(n, newState());
				appendQueue(n);
			} else {
				in.put(n, newState());
				out.put(n, newState());
			}
		}
	}
	
	@Override
	public void processImpl() {
		while(!queue.isEmpty()) {
			N n = queue.iterator().next();
			queue.remove(n);
						
			S oldOut = newState();
			S currentOut = out.get(n);
			copy(currentOut, oldOut);
			
			S currentIn = in.get(n);
			Set<E> preds = provider.getPredecessors(n);
			
			if(preds.size() == 1) {
				E edge = preds.iterator().next();
				// FIXME: in the future define the
				// exception in the state rather than
				// letting DFA discover the catch() statement.
				N src = edge.src;
				if(edge instanceof TryCatchEdge) {
					flowException(src, out.get(src), n, currentIn);
				} else {
					flowThrough(src, out.get(src), n, currentIn);
				}
				
				
			} else if(preds.size() > 1) {
				Iterator<E> it = preds.iterator();
				
				boolean exc = false;
				for(E p : preds) {
					if(p instanceof TryCatchEdge) {
						exc = true;
						break;
					}
				}
				
				E edge = it.next();
				if(edge instanceof TryCatchEdge) {
					N src = edge.src;
					flowException(src, out.get(src), n, currentIn);
				} else {
					if(exc) {
						throw new IllegalStateException("Natural flow into exception block?");
					}
					N src = edge.src;
					flowThrough(src, out.get(src), n, currentIn);
				}
				
				while(it.hasNext()) {
					edge = it.next();
					if(edge instanceof TryCatchEdge) {
						S newS = newState();
						N src = edge.src;
						flowException(src, out.get(src), n, newS);
						copy(newS, currentIn);
					} else {
						if(exc) {
							throw new IllegalStateException("Natural flow into exception block?");
						}
						N src = edge.src;
						S merging = out.get(src);
						merge(n, currentIn, src, merging);
					}
				}
			}
			
			execute(n, currentIn, currentOut);
			
			// if there was a change, enqueue the successors.
			if (!equals(currentOut, oldOut)) {
				for (E e : provider.getSuccessors(n)) {
					appendQueue(e.dst);
				}
			}
		}
	}
	
	@Override
	protected abstract S newState();

	@Override
	protected abstract S newEntryState();

	@Override
	protected abstract void merge(N nIn1, S in1, N nIn2, S in2, S out);
	
	@Override
	protected abstract boolean equals(S s1, S s2);
	
	@Override
	protected abstract void execute(N n, S in, S out);
}