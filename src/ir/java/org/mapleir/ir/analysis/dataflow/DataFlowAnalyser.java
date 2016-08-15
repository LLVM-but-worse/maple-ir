package org.mapleir.ir.analysis.dataflow;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;

public abstract class DataFlowAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> {
	
	protected final DataFlowProvider<N, E> provider;
	protected final Map<N, S> in;
	protected final Map<N, S> out;
	protected final LinkedList<N> queue;
	
	public DataFlowAnalyser(FlowGraph<N, E> graph) {
		this(graph, true);
	}

	public DataFlowAnalyser(FlowGraph<N, E> graph, boolean commit) {
		this(new GraphDataFlowProvider<>(graph), commit);
	}
	
	public DataFlowAnalyser(DataFlowProvider<N, E> provider) {
		this(provider, true);
	}
	
	public DataFlowAnalyser(DataFlowProvider<N, E> provider, boolean commit) {
		this.provider = provider;
		this.queue = new LinkedList<>();
		in = new HashMap<>();
		out = new HashMap<>();
		
		run(commit);
	}
	
	private void run(boolean commit) {
		init();
		if(commit) analyse();
	}
	
	public void analyse() {
		processImpl();
	}
	
	protected void init() {
		// set initial data states
		for(N n : provider.getNodes()) {
			in.put(n, newState());
			out.put(n, newState());
		}
	}
	
	public S in(N n) {
		return in.get(n);
	}
	
	public S out(N n) {
		return out.get(n);
	}
	
	protected void merge(N mainBranch, S mainBranchS, N mergingBranch, S mergingBranchS) {
		S result = newState();
		merge(mainBranch, mainBranchS, mergingBranch, mergingBranchS, result);
		copy(result, mainBranchS);
	}
	
	public void appendQueue(N n) {
		if(!queue.contains(n)) {
			queue.add(n);
		}
	}

	public void remove(N n) {
		in.remove(n);
		out.remove(n);
		while(queue.remove(n));
	}	
	
	protected abstract S newState();

	protected abstract S newEntryState();

	protected abstract void copy(S src, S dst);
	
	protected abstract boolean equals(S s1, S s2);
	
	protected void flowThrough(N src, S srcS, N dst, S dstS) {
		copy(srcS, dstS);
	}
	
	protected void flowException(N src, S srcS, N dst, S dstS) {
		copy(srcS, dstS);
	}
	
	protected abstract void merge(N nIn1, S in1, N nIn2, S in2, S out);
	
	protected abstract void execute(N n, S currentOut, S currentIn);
	
	protected abstract void processImpl();
}