package org.rsdeob.stdlib.cfg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.objectweb.asm.tree.TryCatchBlockNode;
import org.rsdeob.stdlib.cfg.util.LabelHelper;

public class ExceptionRange {

	private final TryCatchBlockNode node;
	private final List<BasicBlock> blocks;
	private final Set<String> types;
	private BasicBlock handler;
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
	
	public void setHandler(BasicBlock b) {
		handler = b;
		invalidate();
	}
	
	public BasicBlock getHandler() {
		return handler;
	}
	
	public List<BasicBlock> getBlocks() {
		return new ArrayList<>(blocks);
	}
	
	public boolean containsBlock(BasicBlock b) {
		return blocks.contains(b);
	}
	
	public void addBlock(BasicBlock b) {
		blocks.add(b);
		invalidate();
	}
	
	public void addBlocks(Collection<BasicBlock> col) {
		blocks.addAll(col);
		invalidate();
	}
	
	public void removeBlock(BasicBlock b) {
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
		ListIterator<BasicBlock> lit = blocks.listIterator();
		while(lit.hasNext()) {
			BasicBlock prev = lit.next();
			if(lit.hasNext()) {
				BasicBlock b = lit.next();
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
			ExceptionRange other = (ExceptionRange) o;
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
}