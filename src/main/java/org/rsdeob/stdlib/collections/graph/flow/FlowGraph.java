package org.rsdeob.stdlib.collections.graph.flow;

import java.util.*;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.TryCatchEdge;
import org.rsdeob.stdlib.collections.graph.FastGraph;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;

public abstract class FlowGraph<N extends FastGraphVertex, E extends FlowEdge<N>> extends FastGraph<N, E> {

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
		ranges.remove(range);
	}
	
	public List<ExceptionRange<N>> getRanges() {
		return new ArrayList<>(ranges);
	}
	
	@Override
	public void clear() {
		super.clear();
		vertexIds.clear();
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
			r.removeVertex(v);
			if(r.get().isEmpty()) {
				it.remove();
			}
		}
		
		entries.remove(v);
		vertexIds.remove(v.getId());
		super.removeVertex(v);
	}
	
	public Set<N> wanderAllTrails(N from, N to) {
		Set<N> visited = new HashSet<>();
		LinkedList<N> stack = new LinkedList<>();
		stack.add(from);
		
		while(!stack.isEmpty()) {
			N s = stack.pop();
			
			for(FlowEdge<N> e : getEdges(s)) {
				if(e instanceof TryCatchEdge)
					continue;
				N succ = e.dst;
				if(succ != to && !visited.contains(succ)) {
					stack.add(succ);
					visited.add(succ);
				}
			}
		}
		return visited;
	}
}