package org.mapleir.stdlib.collections.graph;

import java.util.*;

public class OrderedNode implements FastGraphVertex {

	public final int time;
	
	public OrderedNode(int time) {
		this.time = time;
	}

	@Override
	public int getNumericId() {
		return time;
	}

	@Override
	public String getDisplayName() {
		return String.valueOf(time);
	}
	
	@Override
	public int hashCode() {
		return time;
	}
	
	@Override
	public String toString() {
		return getDisplayName();
	}
	
	public static class ONEdge extends FastGraphEdgeImpl<OrderedNode> {
		public ONEdge(OrderedNode src, OrderedNode dst) {
			super(src, dst);
		}
		
		@Override
		public int hashCode() {
			return src.hashCode() | dst.hashCode() << 8;
		}
		
		@Override
		public String toString() {
			return src.time + ", " + dst.time + ", " + hashCode();
		}
	}
	
	public interface OGraph extends FastGraph<OrderedNode, ONEdge> {
		@Override
		default Map<OrderedNode, Set<ONEdge>> createMap() {
			return new LinkedHashMap<>();
		}

		@Override
		default Set<ONEdge> createSet() {
			return new LinkedHashSet<>();
		}
	}
	
	public static class ODirectedGraph extends FastDirectedGraph<OrderedNode, ONEdge> implements OGraph {
	}
	
	public static class OUndirectedGraph extends FastUndirectedGraph<OrderedNode, ONEdge> implements OGraph {
	}
}
