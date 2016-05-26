package org.rsdeob.stdlib.cfg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.objectweb.asm.tree.TryCatchBlockNode;
import org.rsdeob.stdlib.cfg.util.LabelHelper;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;

public class ExceptionRange<N extends FastGraphVertex> {

	private final TryCatchBlockNode node;
	private final List<N> blocks;
	private final Set<String> types;
	private N handler;
	private int hashcode;
	
	public ExceptionRange(TryCatchBlockNode node) {
		this.node = node;
		blocks = new ArrayList<>();
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
	
	public List<N> getBlocks() {
		return new ArrayList<>(blocks);
	}
	
	public boolean containsBlock(N b) {
		return blocks.contains(b);
	}
	
	public void addBlock(N b) {
		blocks.add(b);
		invalidate();
	}
	
	public void addBlocks(Collection<N> col) {
		blocks.addAll(col);
		invalidate();
	}
	
	public void removeBlock(N b) {
		blocks.remove(b);
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
	
	public void reset() {
		blocks.clear();
		types.clear();
		handler = null;
		invalidate();
	}
	
	private void invalidate() {
		hashcode = 0;
	}
	
	public boolean isCircular() {
		return blocks.contains(handler);
	}
	
	public boolean isContiguous() {
		ListIterator<N> lit = blocks.listIterator();
		while(lit.hasNext()) {
			N prev = lit.next();
			if(lit.hasNext()) {
				N b = lit.next();
				if(LabelHelper.numeric(prev.getId()) >= LabelHelper.numeric(b.getId())) {
					return false;
				}
			}
		}
		return true;
	}
	
	@Override
	public String toString() {
		return String.format("handler=%s, types=%s, range=%s", handler, types, blocks);
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
		int last = LabelHelper.numeric(set.get(0).getId()) - 1;
		for(int i=0; i < set.size(); i++) {
			int num = LabelHelper.numeric(set.get(i).getId());
			if((last + 1) == num) {
				last++;
				continue;
			} else {
				return set.toString();
			}
		}
		
		return String.format("[#%s...#%s]", set.get(0).getId(), LabelHelper.createBlockName(last));
	}
}