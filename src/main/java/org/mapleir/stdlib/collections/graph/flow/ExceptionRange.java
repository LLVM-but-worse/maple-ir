package org.mapleir.stdlib.collections.graph.flow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class ExceptionRange<N extends FastGraphVertex> {

	private final TryCatchBlockNode node;
	private final List<N> nodes;
	private final Set<String> types;
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
	
	public void addVertices(Collection<N> col) {
		nodes.addAll(col);
		invalidate();
	}
	
	public void removeVertex(N b) {
		nodes.remove(b);
		invalidate();
	}
	
	public Set<String> getTypes() {
		return new HashSet<>(types);
	}
	
	public void addType(String b) {
		types.add(b);
		invalidate();
	}
	
	public void removeType(String b) {
		types.remove(b);
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
	
	public boolean isContiguous() {
		ListIterator<N> lit = nodes.listIterator();
		while(lit.hasNext()) {
			N prev = lit.next();
			if(lit.hasNext()) {
				N b = lit.next();
				if(BasicBlock.numeric(prev.getId()) >= BasicBlock.numeric(b.getId())) {
					return false;
				}
			}
		}
		return true;
	}
	
	@Override
	public String toString() {
		return String.format("handler=%s, types=%s, range=%s", handler, types, nodes);
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

	public static <N extends FastGraphVertex> String rangetoString(List<N> set) {
		int last = BasicBlock.numeric(set.get(0).getId()) - 1;
		for(int i=0; i < set.size(); i++) {
			int num = BasicBlock.numeric(set.get(i).getId());
			if((last + 1) == num) {
				last++;
				continue;
			} else {
				return set.toString();
			}
		}
		
		return String.format("[#%s...#%s]", set.get(0).getId(), BasicBlock.createBlockName(last));
	}
}