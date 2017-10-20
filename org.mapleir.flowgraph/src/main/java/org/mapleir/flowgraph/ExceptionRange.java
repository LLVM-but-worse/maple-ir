package org.mapleir.flowgraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.util.StringHelper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class ExceptionRange<N extends FastGraphVertex> {

	private final TryCatchBlockNode node;
	private final List<N> nodes;
	private final Set<Type> types;
	private N handler;
	private int hashcode;
	
	public ExceptionRange(TryCatchBlockNode node) {
		this.node = node;
		nodes = new ArrayList<>();
		types = new HashSet<>();

		hashCode();
	}
	
	public TryCatchBlockNode getNode() {
		return node;
	}
	
	public void setHandler(N b) {
		handler = b;
		invalidate();
	}
	
	public N getHandler() {
		return handler;
	}
	
	public List<N> get() {
		return new ArrayList<>(nodes);
	}
	
	public boolean containsVertex(N b) {
		return nodes.contains(b);
	}
	
	public void addVertex(N b) {
		nodes.add(b);
		invalidate();
	}
	
	public void addVertexAfter(N b, N s) {
		nodes.add(nodes.indexOf(b), s);
		invalidate();
	}
	
	public void addVertexBefore(N b, N s) {
		nodes.add(nodes.indexOf(b), s);
		invalidate();
	}
	
	public void addVertices(Collection<N> col) {
		nodes.addAll(col);
		invalidate();
	}
	
	public void addVertices(N pos, Collection<N> col) {
		nodes.addAll(nodes.indexOf(pos), col);
		invalidate();
	}
	
	public void removeVertex(N b) {
		nodes.remove(b);
		invalidate();
	}
	
	public Set<Type> getTypes() {
		return new HashSet<>(types);
	}
	
	public void addType(Type b) {
		types.add(b);
		invalidate();
	}
	
	public void removeType(Type b) {
		types.remove(b);
		invalidate();
	}
	
	public void setTypes(Set<Type> types) {
		this.types.clear();
		this.types.addAll(types);
		invalidate();
	}

	public void clearNodes() {
		nodes.clear();
	}

	public void reset() {
		nodes.clear();
		types.clear();
		handler = null;
		invalidate();
	}
	
	private void invalidate() {
		hashcode = 0;
	}
	
	public boolean isCircular() {
		return nodes.contains(handler);
	}

	// FIXME: can't rely on numeric for random graphs
	public boolean isContiguous() {
		ListIterator<N> lit = nodes.listIterator();
		while(lit.hasNext()) {
			N prev = lit.next();
			if(lit.hasNext()) {
				N b = lit.next();
				if(prev.getNumericId() >= b.getNumericId()) {
					return false;
				}
			}
		}
		return true;
	}
	
	@Override
	public String toString() {
		return String.format("handler=%s, types=%s, range=%s", handler, types, rangetoString(nodes));
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == this) {
			return true;
		} else if(o instanceof ExceptionRange) {
			ExceptionRange<?> other = (ExceptionRange<?>) o;
			return other.hashCode() == hashCode();
		} else {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		if(hashcode == 0) {
			hashcode = toString().hashCode();
		}
		
		return hashcode;
	}

	// FIXME: can't rely on numeric for random graphs
	public static <N extends FastGraphVertex> String rangetoString(List<N> set) {
		if(set.size() == 0) {
			return set.toString();
		}
		
		// FIXME: verify this works as intended after getId removed
		int last = set.get(0).getNumericId() - 1;
		for(int i=0; i < set.size(); i++) {
			int num = set.get(i).getNumericId();
			if((last + 1) == num) {
				last++;
				continue;
			} else {
				return set.toString();
			}
		}
		
		return String.format("[#%s...#%s]", set.get(0).getDisplayName(), StringHelper.createBlockName(last));
	}
	
	public List<N> getNodes() {
		return nodes;
	}
}