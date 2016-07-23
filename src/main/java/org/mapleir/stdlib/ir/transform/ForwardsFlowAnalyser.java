package org.mapleir.stdlib.ir.transform;

import java.util.Iterator;
import java.util.Set;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;

public abstract class ForwardsFlowAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> extends DataAnalyser<N, E, S> {
	
	public ForwardsFlowAnalyser(FlowGraph<N, E> graph, boolean commit) {
		super(graph, commit);
	}
	
	public ForwardsFlowAnalyser(FlowGraph<N, E> graph) {
		super(graph);
	}
	
	protected void reset(N n) {
		if(graph.getEntries().contains(n)) {
			in.put(n, newEntryState());
			out.put(n, newState());
		} else {
			in.put(n, newState());
			out.put(n, newState());
		}
	}

	protected boolean queue(N n, boolean reset) {
		for(N u : graph.wanderAllTrails(graph.getEntries().iterator().next(), n)) {
			appendQueue(u);
			if(reset) reset(u);
		}
		return true;
	}
	
	@Override
	protected void init() {
		super.init();
		
		for(N entry : graph.getEntries()) {
			in.put(entry, newEntryState());
			out.put(entry, newState());
			appendQueue(entry);
		}
		
		for(ExceptionRange<N> er : graph.getRanges()) {
			N h = er.getHandler();
			in.put(h, newEntryState());
			out.put(h, newState());
			appendQueue(h);
		}
	}
	
	@Override
	public void processImpl() {
		while(!queue.isEmpty()) {
			N n = queue.iterator().next();
			queue.remove(n);
			
			if(!graph.containsVertex(n)) {
				continue;
			}
						
			S oldOut = newState();
			S currentOut = out.get(n);
			copy(currentOut, oldOut);
			
			S currentIn = in.get(n);
			Set<E> preds = graph.getReverseEdges(n);
			
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
				for (E e : graph.getEdges(n)) {
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