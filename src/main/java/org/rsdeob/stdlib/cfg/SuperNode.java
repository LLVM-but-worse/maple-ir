package org.rsdeob.stdlib.cfg;

import java.util.ArrayList;
import java.util.List;

import org.rsdeob.stdlib.cfg.util.GraphUtils;

public class SuperNode {
	public final BasicBlock entry;
	private final int hashCode;
	public final List<BasicBlock> vertices;
	public final List<SuperNode> successors;
	public final List<BasicBlock> predecessors;

	public SuperNode(BasicBlock entry) {
		this.entry = entry;
		hashCode = entry.hashCode();
		vertices = new ArrayList<>();
		successors = new ArrayList<>();
		predecessors = new ArrayList<>();
		vertices.add(entry);
	}
	
	public void addEdge(SuperNode n) {
		successors.add(n);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("start=").append(entry.getId());
		sb.append(",\n");
		sb.append("   blocks=").append(GraphUtils.toBlockArray(vertices));
		
		sb.append(",\n   ");
		List<BasicBlock> succs = new ArrayList<>();
		for(SuperNode s : successors) {
			succs.addAll(s.vertices);
		}
		sb.append("succs=").append(GraphUtils.toBlockArray(succs));
		
		sb.append(",\n   ");
		sb.append("preds=").append(GraphUtils.toBlockArray(predecessors));
		
		return sb.toString();
		
		/* sb.append("{start=").append(entry.getId());
		sb.append(", blocks=[");
		ListIterator<BasicBlock> lit = vertices.listIterator();
		while(lit.hasNext()) {
			sb.append(lit.next().getId());
			if(lit.hasNext()) {
				sb.append(", ");
			}
		}

		sb.append("], preds=[");
		ListIterator<SuperNode> lit2 = predecessors.listIterator();
		while(lit2.hasNext()) {
			sb.append(lit2.next().entry.getId());
			if(lit2.hasNext()) {
				sb.append(", ");
			}
		}
		
		sb.append("], succs=[");
		lit2 = successors.listIterator();
		while(lit2.hasNext()) {
			sb.append(lit2.next().entry.getId());
			if(lit2.hasNext()) {
				sb.append(", ");
			}
		}
		
		sb.append("]}"); */
	}

	@Override
	public int hashCode() {
//		int result = 1;
//		result = 31 * result + ((entry == null) ? 0 : entry.hashCode());
		// result = prime * result + ((vertices == null) ? 0 : vertices.hashCode());
//		return result;
//		return entry.hashCode();
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof SuperNode))
			return false;
		SuperNode other = (SuperNode) obj;
		if (entry == null) {
			if (other.entry != null)
				return false;
		} else if (!entry.equals(other.entry))
			return false;
		if (vertices == null) {
			if (other.vertices != null)
				return false;
		} else if (!vertices.equals(other.vertices))
			return false;
		return true;
	}
}