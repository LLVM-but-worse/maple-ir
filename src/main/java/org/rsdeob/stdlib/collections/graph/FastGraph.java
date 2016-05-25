package org.rsdeob.stdlib.collections.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public abstract class FastGraph<N, E extends FastGraphEdge<N>> {

	private final LinkedHashMap<N, Set<E>> map;
	private final LinkedHashMap<N, Set<E>> reverseMap;
	
	public FastGraph() {
		map = new LinkedHashMap<>();
		reverseMap = new LinkedHashMap<>();
	}
	
	public abstract N getEntry();
	
	public void clear() {
		map.clear();
		reverseMap.clear();
	}
	
	public Set<N> vertices() {
		return map.keySet();
	}
	
	public Set<E> getReverseEdges(N v) {
		return reverseMap.get(v);
	}
	
	public Collection<Set<E>> edges() {
		return Collections.unmodifiableCollection(map.values());
	}
	
	public Set<E> getEdges(N b) {
		return map.get(b);
	}
	
	public int size() {
		return map.size();
	}
	
	public int reverseSize() {
		return reverseMap.size();
	}
	
	public boolean containsVertex(N v) {
		return map.containsKey(v);
	}
	
	public boolean containsEdge(N v, E e) {
		return map.containsKey(v) && map.get(v).contains(e);
	}
	
	public boolean containsReverseVertex(N v) {
		return reverseMap.containsKey(v);
	}
	
	public boolean containsReverseEdge(N v, E e) {
		return reverseMap.containsKey(v) && reverseMap.get(v).contains(e);
	}
	
	public void addVertex(N v) {
		if(!map.containsKey(v)) {
			map.put(v, new HashSet<>());
		}
		
		if(!reverseMap.containsKey(v)) {
			reverseMap.put(v, new HashSet<>());
		}
	}
	
	// TODO: test
	public void removeEdge(N v, E e) {
		if(map.containsKey(v)) {
			map.get(v).remove(e);
		}
		// we need to remove the edge from dst <- src map
		N dst = getDestination(v, e);
		if(reverseMap.containsKey(dst)) {
			reverseMap.get(dst).remove(e);
		}
	}

	protected N getSource(N n, E e) {
		return e.src;
	}
	
	protected N getDestination(N n, E e) {
		return e.dst;
	}
	
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
			reverseMap.get(getDestination(v, e)).remove(e);
		}
		
		for(E e : reverseMap.remove(v)) {
			map.get(getSource(v, e)).remove(e);
		}
	}
	
	public void addEdge(N v, E e) {
		if(!map.containsKey(v)) {
			map.put(v, new HashSet<>());
		}
		map.get(v).add(e);
		
		N dst = getDestination(v, e);
		if(!reverseMap.containsKey(dst)) {
			reverseMap.put(dst, new HashSet<>());
		}
		
		reverseMap.get(dst).add(e);
	}
	
	public static <N, E extends FastGraphEdge<N>> List<N> computeSuccessors(FastGraph<N, E> graph, N n) {
		List<N> list = new ArrayList<>();
		for(E succ : graph.getEdges(n)) {
			list.add(graph.getDestination(n, succ));
		}
		return list;
	}
	
	public static <N, E extends FastGraphEdge<N>> List<N> computePredecessors(FastGraph<N, E> graph, N n) {
		List<N> list = new ArrayList<>();
		for(E pred : graph.getReverseEdges(n)) {
			list.add(graph.getSource(n, pred));
		}
		return list;
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