package org.rsdeob.stdlib.collections.graph.flow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.rsdeob.stdlib.collections.graph.FastGraph;
import org.rsdeob.stdlib.collections.graph.FastGraphEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;

public class FlowGraph<N extends FastGraphVertex, E extends FastGraphEdge<N>> extends FastGraph<N, E>{

	private final List<ExceptionRange<N>> ranges;
	private final Set<N> entries;
	
	public FlowGraph() {
		ranges = new ArrayList<>();
		entries = new HashSet<>();
	}
	
	public Set<N> getEntries() {
		return entries;
	}
	
	public void addRange(ExceptionRange<N> range) {
		if(!ranges.contains(range)) {
			ranges.add(range);
		}
	}
	
	public void removeRange(ExceptionRange<N> range) {
		ranges.add(range);
	}
	
	public List<ExceptionRange<N>> getRanges() {
		return new ArrayList<>(ranges);
	}
	
	@Override
	public void removeVertex(N v) {
		ListIterator<ExceptionRange<N>> it = ranges.listIterator();
		while(it.hasNext()) {
			ExceptionRange<N> r = it.next();
			if(r.containsBlock(v)) {
				r.removeBlock(v);
			}
			if(r.getHandler() == v || r.getBlocks().isEmpty()) {
				it.remove();
			}
		}
		
		super.removeVertex(v);
	}
}