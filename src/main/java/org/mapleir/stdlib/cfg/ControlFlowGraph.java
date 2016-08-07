package org.mapleir.stdlib.cfg;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.objectweb.asm.tree.MethodNode;

public class ControlFlowGraph extends FastBlockGraph {
	
	private final MethodNode method;
	
	public ControlFlowGraph(MethodNode method) {
		this.method = method;
	}
	
	public MethodNode getMethod() {
		return method;
	}
	
	@Override
	public String toString() {
		return GraphUtils.toString(this, vertices());
	}
	
	public ControlFlowGraph copy() {
		ControlFlowGraph cfg = new ControlFlowGraph(method);
		copy(map, cfg.map);
		copy(reverseMap, cfg.reverseMap);
		cfg.blockLabels.putAll(blockLabels);
		cfg.ranges.addAll(ranges);
		cfg.vertexIds.putAll(vertexIds);
		cfg.entries.addAll(entries);
		return cfg;
	}
	
	private static <K, V> void copy(Map<K, Set<V>> src, Map<K, Set<V>> dst) {
		for(Entry<K, Set<V>> e : src.entrySet()) {
			dst.put(e.getKey(), new HashSet<>(e.getValue()));
		}
	}
}