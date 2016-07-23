package org.mapleir.stdlib.ir.transform;

import java.util.Iterator;
import java.util.Set;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;

public abstract class BackwardsFlowAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> extends DataAnalyser<N, E, S> {
	
	public BackwardsFlowAnalyser(FlowGraph<N, E> graph) {
		super(graph);
	}
	
	public BackwardsFlowAnalyser(FlowGraph<N, E> graph, boolean commit) {
		super(graph, commit);
	}
	
	@Override
	protected void init() {
		// since this is backwards analysis, we
		// set the initial flow states after the
		// exit points of the graph.
		
		// to increase efficiency, instead of
		// calling super.init(), we compute the
		// exits of the graph while inserting
		// the default flow states into our tables.
		
		for(N n : graph.vertices()) {
			queue.add(0, n);
			in.put(n, newState());
			out.put(n, newState());
			
			if(graph.getEdges(n).size() == 0) {
				out.put(n, newEntryState());
			}
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

			// stored for checking if a change of state
			// happens during the analysis of this
			// instruction. (in because it's backwards).
			S oldIn = newState();
			S currentIn = in.get(n);
			copy(currentIn, oldIn);
			
			S currentOut = out.get(n);
			Set<E> succs = graph.getEdges(n);
						
			if(succs.size() == 1) {
				N succ = succs.iterator().next().dst;
				S succIn = in.get(succ);
				flowThrough(succ, succIn, n, currentOut);
			} else if(succs.size() > 1) {
				Iterator<E> it = succs.iterator();

				while(it.hasNext()) {
					E e = it.next();
					N dst = e.dst;
					if(e instanceof ImmediateEdge) {
						flowThrough(dst, in.get(dst), n, currentOut);
					} else {
						S merging = in.get(dst);
						merge(n, currentOut, dst, merging);
					}
				}
			}
			
			execute(n, currentOut, currentIn);
			
			// if there was a change, enqueue the predecessors.
			if(!equals(currentIn, oldIn)) {
				for(E e : graph.getReverseEdges(n)) {
					appendQueue(e.src);
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
	protected abstract void execute(N n, S out, S in);
}