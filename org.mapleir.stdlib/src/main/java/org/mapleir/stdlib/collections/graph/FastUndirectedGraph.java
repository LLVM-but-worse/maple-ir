package org.mapleir.stdlib.collections.graph;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import org.mapleir.dot4j.model.Graph;
import org.mapleir.propertyframework.api.IPropertyDictionary;

import java.util.Set;

// TODO: redo this.
public abstract class FastUndirectedGraph<N extends FastGraphVertex, E extends FastGraphEdge<N>> implements FastGraph<N, E>{

	private final Map<N, Set<E>> map;
	private final Map<N, Set<E>> reverseMap;
	
	public FastUndirectedGraph() {
		map = createMap();
		reverseMap = createMap();
	}
	
	public FastUndirectedGraph(FastUndirectedGraph<N, E> g) {
		map = createMap(g.map);
		reverseMap = createMap(g.reverseMap);
	}

	@Override
	public Set<N> vertices() {
		return map.keySet();
	}

	@Override
	public boolean addVertex(N n) {
		boolean ret = false;
		if(!map.containsKey(n)) {
			map.put(n, createSet());
			ret = true;
		}
		if(!reverseMap.containsKey(n)) {
			reverseMap.put(n, createSet());
			ret = true;
		}
		return ret;
	}

	@Override
	public void removeVertex(N n) {
		for(E e : map.remove(n)) {
			reverseMap.get(/*getDestination(v, e)*/ e.dst()).remove(e);
		}
		
		for(E e : reverseMap.remove(n)) {
			map.get(/*getSource(v, e)*/ e.src()).remove(e);
		}
	}

	@Override
	public boolean containsVertex(N n) {
		return map.containsKey(n);
	}

	@Override
	public void addEdge(E e) {
		N n = e.src();
		if(!map.containsKey(n)) {
			map.put(n, createSet());
		}
		map.get(n).add(e);
		
		N dst = e.dst();
		if(!reverseMap.containsKey(dst)) {
			reverseMap.put(dst, createSet());
		}
		
		reverseMap.get(dst).add(e);
	}

	@Override
	public void removeEdge(E e) {
		N n = e.src();
		if(map.containsKey(n)) {
			map.get(n).remove(e);
		}
		N dst = e.dst();
		if(reverseMap.containsKey(dst)) {
			reverseMap.get(dst).remove(e);
		}
	}

	@Override
	public boolean containsEdge(E e) {
		N n = e.src();
		return map.containsKey(n) && map.get(n).contains(e);
	}

	@Override
	public Set<E> getEdges(N n) {
		return map.get(n);
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public void replace(N old, N n) {
		Set<E> succs = getEdges(old);
		Set<E> preds = reverseMap.get(old);
		
		addVertex(n);
		
		for(E succ : new HashSet<>(succs)) {
			/* 'old' is the 'src' here, change 'n' to the new 'src' */
			assert succ.src() == old;
			E newEdge = clone(succ, n, succ.dst());
			removeEdge(succ);
			addEdge(newEdge);
		}
		
		for(E pred : new HashSet<>(preds)) {
			/* 'old' is the 'dst' here, change 'n' to the new 'dst' */
			assert pred.dst() == old;
			E newEdge = clone(pred, pred.src(), n);
			removeEdge(pred);
			addEdge(newEdge);
		}
		
		removeVertex(old);
	}

	@Override
	public void clear() {
		map.clear();
		reverseMap.clear();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("map {\n");
		for(Entry<N, Set<E>> e : map.entrySet()) {
			sb.append("   ").append(e.getKey()).append("  ").append(e.getValue()).append("\n");
		}
		sb.append("}");
		return sb.toString();
	}
	
	@Override
	public Graph makeDotGraph(IPropertyDictionary properties) { 
		return GraphUtils.makeGraphSkeleton(this).setDirected(false);
	}
}
