package org.rsdeob.stdlib.collections.graph.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.rsdeob.stdlib.collections.graph.FastGraph;
import org.rsdeob.stdlib.collections.graph.FastGraphEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;

public abstract class FlowGraph<N extends FastGraphVertex, E extends FastGraphEdge<N>> extends FastGraph<N, E>{

	private final List<ExceptionRange<N>> ranges;
	private final Set<N> entries;
	private final Map<String, N> vertexIds;
	
	public FlowGraph() {
		ranges = new ArrayList<>();
		entries = new HashSet<>();
		vertexIds = new HashMap<>();
	}
	
	public N getBlock(String id) {
		return vertexIds.get(id);
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
	public void addVertex(N v) {
		vertexIds.put(v.getId(), v);
		super.addVertex(v);
	}	
	
	@Override
	public void addEdge(N v, E e) {
		vertexIds.put(v.getId(), v);
		super.addEdge(v, e);
	}
	
	@Override
	public void replace(N old, N n) {
		if(entries.contains(old)) {
			entries.add(n);
		}
		super.replace(old, n);
	}
	
	@Override
	public void removeVertex(N v) {
		ListIterator<ExceptionRange<N>> it = ranges.listIterator();
		while(it.hasNext()) {
			ExceptionRange<N> r = it.next();
			if(r.containsVertex(v)) {
				r.removeVertex(v);
			}
			if(r.getHandler() == v || r.get().isEmpty()) {
				it.remove();
			}
		}
		
		entries.remove(v);
		vertexIds.remove(v.getId());
		super.removeVertex(v);
	}
	
	@Override
	public void excavate(N n) {
		Set<E> predEdges = getReverseEdges(n);
		Set<N> preds = new HashSet<>();
		for(E e : predEdges) {
			preds.add(e.src);
		}
		super.excavate(n);
		for(N pred : preds) {
			Set<E> predPreds = getReverseEdges(pred);
			if(predPreds.size() == 0) {
				entries.add(pred);
			}
		}
	}
}