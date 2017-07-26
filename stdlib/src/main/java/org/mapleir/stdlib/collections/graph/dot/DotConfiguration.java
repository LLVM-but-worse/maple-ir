package org.mapleir.stdlib.collections.graph.dot;

import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public abstract class DotConfiguration<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> {

	public enum GraphType {
		DIRECTED("digraph", "->"), UNDIRECTED("graph", "--");
		
		private final String printType;
		private final String edgeArrow;
		
		private GraphType(String printType, String edgeArrow) {
			this.printType = printType;
			this.edgeArrow = " " + edgeArrow + " ";
		}
		
		public String getPrintType()  {
			return printType;
		}
		
		public String getEdgeArrow() {
			return edgeArrow;
		}
	}
	
	public static final String[] BASIC_GRAPH_PROPERTIES = new String[] {"graph", "node", "edge"};

	private final GraphType type;
	private final Map<String, Map<String, Object>> globalProperties;
	
	public DotConfiguration(GraphType type) {
		this.type = type;
		globalProperties = new HashMap<>();
	}
	
	public GraphType getType() {
		return type;
	}
	
	public Map<String, Map<String, Object>> getGlobalProperties() {
		return globalProperties;
	}
	
	public void addGlobalProperties(String section, Map<String, ? extends Object> map) {
		if(section == null) 
			return;
		
		if(globalProperties.containsKey(section)) {
			Map<String, Object> currentMap = globalProperties.get(section);
			for(Entry<String, ? extends Object> e : map.entrySet()) {
				currentMap.put(e.getKey(), e.getValue());
			}
		} else {
			globalProperties.put(section, new HashMap<>(map));
		}
	}
}