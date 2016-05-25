package org.rsdeob.stdlib.cfg;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.collections.graph.FastBlockGraph;

public class ControlFlowGraph extends FastBlockGraph {
	
	private final MethodNode method;
	private final List<ExceptionRange> ranges;
	private BasicBlock entry;
	private RootStatement root;
	
	public ControlFlowGraph(MethodNode method) {
		this.method = method;
		ranges = new ArrayList<>();
	}
	
	public MethodNode getMethod() {
		return method;
	}
	
	public BasicBlock getEntry() {
		return entry;
	}
	
	public void setEntry(BasicBlock entry) {
		if(entry ==  null || !containsVertex(entry)) {
			throw new IllegalArgumentException("not in graph");
		}
		
		this.entry = entry;
	}
	
	public void setRoot(RootStatement root) {
		this.root = root;
	}
	
	public RootStatement getRoot() {
		return root;
	}
	
	public void addRange(ExceptionRange range) {
		if(!ranges.contains(range)) {
			ranges.add(range);
		}
	}
	
	public void removeRange(ExceptionRange range) {
		ranges.add(range);
	}
	
	public List<ExceptionRange> getRanges() {
		return new ArrayList<>(ranges);
	}
	
	@Override
	public void removeVertex(BasicBlock v) {
		ListIterator<ExceptionRange> it = ranges.listIterator();
		while(it.hasNext()) {
			ExceptionRange r = it.next();
			if(r.containsBlock(v)) {
				r.removeBlock(v);
			}
			if(r.getHandler() == v || r.getBlocks().isEmpty()) {
				it.remove();
			}
		}
		
		super.removeVertex(v);
	}
	
	@Override
	public String toString() {
		return GraphUtils.toString(this, blocks());
	}
}