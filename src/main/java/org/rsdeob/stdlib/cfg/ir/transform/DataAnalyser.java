package org.rsdeob.stdlib.cfg.ir.transform;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

public abstract class DataAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> {
	
	protected final FlowGraph<N, E> graph;
	protected final Map<N, S> in;
	protected final Map<N, S> out;
	protected final LinkedList<N> queue;
	
	public DataAnalyser(FlowGraph<N, E> graph) {
		this.graph = graph;
		this.queue = new LinkedList<>();
		in = new HashMap<>();
		out = new HashMap<>();
	}
	
	public void run() {
		init();
		processQueue();
	}
	
//	protected abstract void removeImpl(N n);
//	
	public void remove(N n) {
		in.remove(n);
		out.remove(n);
		// removeImpl(n);
	}
	
	public S in(N n) {
		return in.get(n);
	}
	
	public S out(N n) {
		return out.get(n);
	}
	
	public FlowGraph<N, E> getGraph() {
		return graph;
	}

	protected abstract void updateImpl(N n);
	
	public void update(N n) {
		queue.add(n);
		updateImpl(n);
	}
	
	protected abstract void replaceImpl(N old, N n);
	
	public void replace(N old, N n) {
		queue.add(n);
		replaceImpl(old, n);
		remove(old);
	}
	
	protected void init() {
		// set initial data states
		for(N n : graph.vertices()) {
			in.put(n, newState());
			out.put(n, newState());
		}
	}
	
	protected abstract void processQueue();
	
	protected abstract S newState();

	protected abstract S newEntryState();

	protected abstract void copy(S src, S dst);
	
	protected abstract boolean equals(S s1, S s2);
	
	protected abstract void merge(S in1, S in2, S out);
	
	protected void merge(S mainBranch, S mergingBranch) {
		S result = newState();
		merge(mainBranch, mergingBranch, result);
		copy(result, mainBranch);
	}
	
	protected abstract void propagate(N n, S currentOut, S currentIn);
}