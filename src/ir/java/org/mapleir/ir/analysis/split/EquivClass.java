package org.mapleir.ir.analysis.split;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.mapleir.ir.analysis.FlowGraphUtils.IdSource;

public class EquivClass {
	
	public int id;
	/** Size of the bracket list. */
	public int size;
	/** Edges of the equivalence class, in DFS order. Pairwise, these form the canonical SESE regions */
	public List<UDEdge> edges;
	public Collection<UDNode> nodes;

	public EquivClass(IdSource idSource) {
		edges = new ArrayList<>();
		nodes = new ArrayList<>();
		id = idSource.getNew();
	}

	public void addEdge(UDEdge edge) {
		edges.add(edge);
		if (edge.represented != null) {
			nodes.add(edge.represented);
		}
	}

	@Override
	public String toString() {
		return "<" + id + ">";
	}
}