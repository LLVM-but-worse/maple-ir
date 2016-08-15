package org.mapleir.ir.cfg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.mapleir.ir.code.ExpressionStack;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.LabelNode;

public class BasicBlock implements FastGraphVertex, Comparable<BasicBlock>, Iterable<Statement> {

	private int id;
	private final ControlFlowGraph cfg;
	private final LabelNode label;
	private final List<Statement> statements;
	private ExpressionStack inputStack;
	private List<ExceptionRange<BasicBlock>> ranges;
	
	public BasicBlock(ControlFlowGraph cfg, int id, LabelNode label) {
		this.cfg = cfg;
		this.id = id;
		this.label = label;
		
		statements = new ArrayList<>();
	}
	
	public ControlFlowGraph getGraph() {
		return cfg;
	}
	
	public void add(Statement stmt) {
		statements.add(stmt);
		stmt.setBlock(this);
	}

	public void add(int index, Statement stmt) {
		statements.add(index, stmt);
		stmt.setBlock(this);
	}
	
	public void remove(Statement stmt) {
		statements.remove(stmt);
		stmt.setBlock(null);
	}
	
	public void remove(int index) {
		statements.remove(index).setBlock(null);
	}
	
	public boolean contains(Statement stmt) {
		return statements.remove(stmt);
	}
	
	public boolean isEmpty() {
		return statements.isEmpty();
	}
	
	public int indexOf(Statement stmt) {
		return statements.indexOf(stmt);
	}
	
	public Statement getAt(int index) {
		return statements.get(index);
	}
	
	public int size() {
		return statements.size();
	}
	
	public void clear() {
		Iterator<Statement> it = statements.iterator();
		while(it.hasNext()) {
			Statement s = it.next();
			s.setBlock(null);
			it.remove();
		}
	}
	
	public void transfer(BasicBlock to) {
		Iterator<Statement> it = statements.iterator();
		while(it.hasNext()) {
			Statement s = it.next();
			to.statements.add(s);
			s.setBlock(to);
			it.remove();
		}
	}
	
	public ExpressionStack getInputStack() {
		return inputStack;
	}
	
	public void setInputStack(ExpressionStack inputStack) {
		this.inputStack = inputStack;
	}

	@Override
	public String getId() {
		return createBlockName(id);
	}

	public void setId(int i) {
		id = i;
	}
	
	public int getNumericId() {
		return id;
	}
	
	public List<ExceptionRange<BasicBlock>> getProtectingRanges() {
		if(ranges != null) {
			return ranges;
		}
		
		List<ExceptionRange<BasicBlock>> ranges = new ArrayList<>();
		for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			if(er.containsVertex(this)) {
				ranges.add(er);
			}
		}
		return (this.ranges = ranges);
	}
	
	public boolean isHandler() {
		for(FlowEdge<BasicBlock> e : cfg.getReverseEdges(this)) {
			if(e instanceof TryCatchEdge) {
				if(e.dst == this) {
					return true;
				} else {
					throw new IllegalStateException("incoming throw edge for " + getId() + " with dst " + e.dst.getId());
				}
			}
		}
		return false;
	}
	
	public Set<FlowEdge<BasicBlock>> getPredecessors() {
		return new HashSet<>(cfg.getReverseEdges(this));
	}

	public Set<FlowEdge<BasicBlock>> getPredecessors(Predicate<? super FlowEdge<BasicBlock>> e) {
		Set<FlowEdge<BasicBlock>> set = getPredecessors();
		set.removeIf(e.negate());
		return set;
	}

	public Set<FlowEdge<BasicBlock>> getSuccessors() {
		return new HashSet<>(cfg.getEdges(this));
	}

	public Set<FlowEdge<BasicBlock>> getSuccessors(Predicate<? super FlowEdge<BasicBlock>> e) {
		Set<FlowEdge<BasicBlock>> set = getSuccessors();
		set.removeIf(e.negate());
		return set;
	}

	public List<BasicBlock> getJumpEdges() {
		List<BasicBlock> jes = new ArrayList<>();
		for (FlowEdge<BasicBlock> e : cfg.getEdges(this)) {
			if (!(e instanceof ImmediateEdge)) {
				jes.add(e.dst);
			}
		}
		return jes;
	}
	
	private Set<FlowEdge<BasicBlock>> findImmediatesImpl(Set<FlowEdge<BasicBlock>> set) {
		Set<FlowEdge<BasicBlock>> iset = new HashSet<>();
		for(FlowEdge<BasicBlock> e : set) {
			if(e instanceof ImmediateEdge) {
				iset.add(e);
			}
		}
		return iset;
	}
	
	private FlowEdge<BasicBlock> findSingleImmediateImpl(Set<FlowEdge<BasicBlock>> _set) {
		Set<FlowEdge<BasicBlock>> set = findImmediatesImpl(_set);
		int size = set.size();
		if(size == 0) {
			return null;
		} else if(size > 1) {
			throw new IllegalStateException(set.toString());
		} else {
			return set.iterator().next();
		}
	}

	public ImmediateEdge<BasicBlock> getImmediateEdge() {
		return (ImmediateEdge<BasicBlock>) findSingleImmediateImpl(cfg.getEdges(this));
	}
	
	public BasicBlock getImmediate() {
		FlowEdge<BasicBlock> e =  findSingleImmediateImpl(cfg.getEdges(this));
		if(e != null) {
			return e.dst;
		} else {
			return null;
		}
	}
	
	public ImmediateEdge<BasicBlock> getIncomingImmediateEdge() {
		return (ImmediateEdge<BasicBlock>) findSingleImmediateImpl(cfg.getReverseEdges(this));
	}

	public BasicBlock getIncomingImmediate() {
		FlowEdge<BasicBlock> e =  findSingleImmediateImpl(cfg.getReverseEdges(this));
		if(e != null) {
			return e.src;
		} else {
			return null;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		} else if (obj instanceof BasicBlock) {
			BasicBlock other = (BasicBlock) obj;
			return other.id == id;
			// return hashcode == other.hashcode;
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		return String.format("Block #%s", createBlockName(id)/* (%s), label != null ? label.hashCode() : "dummy"*/);
	}

	@Override
	public int compareTo(BasicBlock o) {
		return Integer.compare(id, o.id);
	}
	
	public static String createBlockName(int n) {
		char[] buf = new char[(int) Math.floor(Math.log(25 * (n + 1)) / Math.log(26))];
		for (int i = buf.length - 1; i >= 0; i--) {
			buf[i] = (char) ('A' + (--n) % 26);
			n /= 26;
		}
		return new String(buf);
	}

	public Label getLabel() {
		return label.getLabel();
	}
	
	public LabelNode getLabelNode() {
		return label;
	}
	
	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public Iterator<Statement> iterator() {
		return statements.iterator();
	}
	
	public static int numeric(String label) {
		int result = 0;
		for (int i = label.length() - 1; i >= 0; i--)
			result = result + (label.charAt(i) - 64) * (int) Math.pow(26, label.length() - (i + 1));
		return result;
	}
}