package org.rsdeob.stdlib.ir.transform;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;
import org.rsdeob.stdlib.ir.api.ICodeListener;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public abstract class DataAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> implements ICodeListener<N> {
	
	protected final FlowGraph<N, E> graph;
	protected final Map<N, S> in;
	protected final Map<N, S> out;
	protected final LinkedList<N> queue;
	
	public DataAnalyser(FlowGraph<N, E> graph) {
		this.graph = graph;
		this.queue = new LinkedList<>();
		in = new HashMap<>();
		out = new HashMap<>();
		run();
	}
	
	private void run() {
		init();
		commit();
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

	public abstract void enqueue(N n);
	
	protected void init() {
		// set initial data states
		for(N n : graph.vertices()) {
			in.put(n, newState());
			out.put(n, newState());
		}
	}
	
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
	
	protected abstract void apply(N n, S currentOut, S currentIn);

	// ICodeListener overrides
	@Override
	public void updated(N n) {
		queue.add(n);
	}

	@Override
	public void replaced(N old, N n) {
		removed(old);
		added(n);
	}

	@Override
	public void added(N n) {
		queue.add(n);
	}

	@Override
	public void removed(N n) {
		in.remove(n);
		out.remove(n);
	}
}