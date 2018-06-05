package org.mapleir.stdlib.collections.graph;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.stdlib.collections.graph.dot.BasicDotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration.GraphType;

public abstract class FastDirectedGraph<N extends FastGraphVertex, E extends FastGraphEdge<N>> implements FastGraph<N, E>{

	private final Map<N, Set<E>> map;
	private final Map<N, Set<E>> reverseMap;
	
	public FastDirectedGraph() {
		map = createMap();
		reverseMap = createMap();
	}
	
	public FastDirectedGraph(FastDirectedGraph<N, E> g) {
		map = createMap(g.map);
		reverseMap = createMap(g.reverseMap);
	}
	
	@Override
	public Set<N> vertices() {
		return Collections.unmodifiableSet(map.keySet());
	}

	@Override
	public boolean addVertex(N v) {
		boolean ret = false;
		if(!map.containsKey(v)) {
			map.put(v, createSet());
			ret = true;
		}
		
		if(!reverseMap.containsKey(v)) {
			
			if(!ret) {
				throw new IllegalStateException(v.toString());
			}
			
			reverseMap.put(v, createSet());
			ret = true;
		}
		return ret;
	}

	@Override
	public void removeVertex(N v) {
		// A = {(A->B), (A->C)}
		// B = {(B->D)}
		// C = {(C->D)}
		// D = {}
		//  reverse
		// A = {}
		// B = {(A->B)}
		// C = {(A->C)}
		// D = {(B->D), (C->D)}
		
		// if we remove B, the map should
		// now be:
		
		// A = {(A->B), (A->C)}
		//   for(E e : map.remove(B) = {(B->D)}) {
		//      reverseMap.get(e.dst).remove(e);
		//   }
		// C = {(C->D)}
		// D = {}
		//  reverse
		// A = {}
		//   for(E e : reverseMap.remove(B) = {(A->B)}) {
		//      map.get(e.src).remove(e);
		//   }
		// C = {(A->C)}
		// D = {(B->D), (C->D)}
		
		// so now, B has been completely removed
		// A = {(A->C)}
		// 
		// C = {(C->D)}
		// D = {}
		//  reverse
		// A = {}
		// 
		// C = {(A->C)}
		// D = {(C->D)}

		for(E e : map.remove(v)) {
			reverseMap.get(e.dst()).remove(e);
		}
		
		for(E e : reverseMap.remove(v)) {
			map.get(e.src()).remove(e);
		}
	}

	@Override
	public boolean containsVertex(N v) {
		return map.containsKey(v);
	}
	
	public boolean containsReverseVertex(N v) {
		return reverseMap.containsKey(v);
	}

	// Call addEdge(E) instead!
	@Deprecated
	@Override
	public void addEdge(N v, E e) {
		throw new UnsupportedOperationException();
	}

	public void addEdge(E e) {
		N src = e.src();
		addVertex(src);
		map.get(src).add(e);
		
		N dst = e.dst();
		addVertex(dst);
		reverseMap.get(dst).add(e);
	}

	// Call removeEdge(E) instead!
	@Deprecated
	@Override
	public void removeEdge(N v, E e) {
		throw new UnsupportedOperationException();
	}

	public void removeEdge(E e) {
		N src = e.src();
		if(map.containsKey(src)) {
			map.get(src).remove(e);
		}
		// we need to remove the edge from dst <- src map
		N dst = e.dst();
		if(reverseMap.containsKey(dst)) {
			reverseMap.get(dst).remove(e);
		}
	}

	// Call containsEdge(E) instead!
	@Deprecated
	@Override
	public boolean containsEdge(N v, E e) {
		throw new UnsupportedOperationException();
	}

	public boolean containsEdge(E e) {
		N src = e.src();
		return map.containsKey(src) && map.get(src).contains(e);
	}

	public boolean containsReverseEdge(E e) {
		N dst = e.dst();
		return reverseMap.containsKey(dst) && reverseMap.get(dst).contains(e);
	}

	@Override
	public Set<E> getEdges(N b) {
		return map.get(b);
	}
	
	public Set<E> getReverseEdges(N v) {
		return reverseMap.get(v);
	}

	@Override
	public int size() {
		return map.size();
	}
	
	public int reverseSize() {
		return reverseMap.size();
	}
	
	// TODO: entries
	@Override
	public void replace(N old, N n) {
		// A = {(A->B), (A->C)}
		// B = {(B->D)}
		// C = {(C->D)}
		// D = {}
		//  reverse
		// A = {}
		// B = {(A->B)}
		// C = {(A->C)}
		// D = {(B->D), (C->D)}
		
		// replacing B with E
		
		// A = {(A->E), (A->C)}
		// E = {(E->D)}
		// C = {(C->D)}
		// D = {}
		//  reverse
		// A = {}
		// E = {(A->E)}
		// C = {(A->C)}
		// D = {(E->D), (C->D)}
		
		Set<E> succs = getEdges(old);
		Set<E> preds = getReverseEdges(old);
		
		addVertex(n);
		
		for(E succ : new HashSet<>(succs)) {
			E newEdge = clone(succ, old, n);
			removeEdge(succ);
			addEdge(newEdge);
		}
		
		for(E pred : new HashSet<>(preds)) {
			E newEdge = clone(pred, old, n);
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
		sb.append("}\n");
		
		sb.append("reverse {\n");
		for(Entry<N, Set<E>> e : reverseMap.entrySet()) {
			sb.append("   ").append(e.getKey()).append("  ").append(e.getValue()).append("\n");
		}
		sb.append("}");
		return sb.toString();
	}
	

	@Override
	public DotConfiguration<FastGraph<N,E>, N, E> makeDotConfiguration() {
		return new BasicDotConfiguration<>(GraphType.DIRECTED);
	}
}
