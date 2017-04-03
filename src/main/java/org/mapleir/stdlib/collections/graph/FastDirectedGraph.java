package org.mapleir.stdlib.collections.graph;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
		return map.keySet();
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
			reverseMap.get(/*getDestination(v, e)*/ e.dst).remove(e);
		}
		
		for(E e : reverseMap.remove(v)) {
			map.get(/*getSource(v, e)*/ e.src).remove(e);
		}
	}

	@Override
	public boolean containsVertex(N v) {
		return map.containsKey(v);
	}
	
	public boolean containsReverseVertex(N v) {
		return reverseMap.containsKey(v);
	}
	
	@Override
	public void addEdge(N v, E e) {
		addVertex(v);
		map.get(v).add(e);
		
		N dst = /*getDestination(v, e)*/ e.dst;
		addVertex(dst);
		
		reverseMap.get(dst).add(e);
	}

	@Override
	public void removeEdge(N v, E e) {
		if(map.containsKey(v)) {
			map.get(v).remove(e);
		}
		// we need to remove the edge from dst <- src map
		N dst = e.dst /*getDestination(v, e)*/;
		if(reverseMap.containsKey(dst)) {
			reverseMap.get(dst).remove(e);
		}
	}

	@Override
	public boolean containsEdge(N v, E e) {
		return map.containsKey(v) && map.get(v).contains(e);
	}
	
	public boolean containsReverseEdge(N v, E e) {
		return reverseMap.containsKey(v) && reverseMap.get(v).contains(e);
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
			removeEdge(old, succ);
			addEdge(n, newEdge);
		}
		
		for(E pred : new HashSet<>(preds)) {
			E newEdge = clone(pred, old, n);
			removeEdge(pred.src, pred);
			addEdge(pred.src, newEdge);
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
}