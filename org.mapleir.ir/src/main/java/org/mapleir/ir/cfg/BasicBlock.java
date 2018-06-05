package org.mapleir.ir.cfg;

import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.flowgraph.edges.ImmediateEdge;
import org.mapleir.flowgraph.edges.TryCatchEdge;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.list.NotifiedList;

import java.util.*;
import java.util.function.Predicate;

import static org.mapleir.stdlib.util.StringHelper.createBlockName;

public class BasicBlock implements FastGraphVertex, Collection<Stmt> {

	/**
	 * Specifies that this block should not be merged in later passes.
	 */
	public static final int FLAG_NO_MERGE = 0x1;

	/**
	 * Two blocks A, B, must have A.id == B.id IFF A == B
	 * Very important!
	 */
	private int id;
	private final ControlFlowGraph cfg;
	private final NotifiedList<Stmt> statements;
	private int flags = 0;

	// for debugging purposes. the number of times the label was changed
	private int relabelCount = 0;

	public BasicBlock(ControlFlowGraph cfg) {
		this.cfg = cfg;
		this.id = cfg.size() + 1;
		statements = new NotifiedList<>(
				(s) -> s.setBlock(this),
				(s) -> {
					if (s.getBlock() == this)
						s.setBlock(null);
				}
		);
	}
	
	public boolean isFlagSet(int flag) {
		return (flags & flag) == flag;
	}
	
	public void setFlag(int flag, boolean b) {
		if(b) {
			flags |= flag;
		} else {
			flags ^= flag;
		}
	}
	
	public void setFlags(int flags) {
		this.flags = flags;
	}

	public int getFlags() {
		return flags;
	}
	
	public ControlFlowGraph getGraph() {
		return cfg;
	}

	public void transfer(BasicBlock dst) {
		Iterator<Stmt> it = statements.iterator();
		while(it.hasNext()) {
			Stmt s = it.next();
			it.remove();
			dst.add(s);
			assert (s.getBlock() == dst);
		}
	}

	/**
	 * Transfers statements up to index `to`, exclusively, to block `dst`.
	 */
	public void transferUpto(BasicBlock dst, int to) {
		// FIXME: faster
		for(int i=to - 1; i >= 0; i--) {
			Stmt s = remove(0);
			dst.add(s);
			assert (s.getBlock() == dst);
		}
	}

	@Override
	public String getDisplayName() {
		return createBlockName(id);
	}

	/**
	 * If you call me you better know what you are doing.
	 * If you use me in any collections, they must be entirely rebuilt from scratch
	 * ESPECIALLY indexed or hash-based ones.
	 * This includes collections of edges too.
	 * @param i newId
	 */
	public void setId(int i) {
		relabelCount++;
		id = i;
	}
	
	@Override
	public int getNumericId() {
		return id;
	}
	
	public List<ExceptionRange<BasicBlock>> getProtectingRanges() {
		List<ExceptionRange<BasicBlock>> ranges = new ArrayList<>();
		for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			if(er.containsVertex(this)) {
				ranges.add(er);
			}
		}
		return ranges;
	}
	
	public boolean isHandler() {
		for(FlowEdge<BasicBlock> e : cfg.getReverseEdges(this)) {
			if(e instanceof TryCatchEdge) {
				if(e.dst() == this) {
					return true;
				} else {
					throw new IllegalStateException("incoming throw edge for " + getDisplayName() + " with dst " + e.dst().getDisplayName());
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
				jes.add(e.dst());
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
			return e.dst();
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
			return e.src();
		} else {
			return null;
		}
	}

	@Override
	public String toString() {
		return String.format("Block #%s", createBlockName(id)/* (%s), label != null ? label.hashCode() : "dummy"*/);
	}

	// This implementation of equals doesn't really do anything, it's just for sanity-checking purposes.
	// NOTE: we can't change equals or hashCode because the id can change from ControlFlowGraph#relabel.
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		BasicBlock bb = (BasicBlock) o;

		if (id == bb.id) {
			assert (relabelCount == bb.relabelCount);
			assert (this == bb);
		}
		return id == bb.id;
	}
	
	public void checkConsistency() {
		for (Stmt stmt : statements)
			if (stmt.getBlock() != this)
				throw new IllegalStateException("Orphaned child " + stmt);
	}

	// List functions
	@Override
	public boolean add(Stmt stmt) {
		return statements.add(stmt);
	}

	public void add(int index, Stmt stmt) {
		statements.add(index, stmt);
	}

	@Override
	public boolean remove(Object o) {
		return statements.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return statements.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends Stmt> c) {
		return statements.addAll(c);
	}

	public boolean addAll(int index, Collection<? extends Stmt> c) {
		return statements.addAll(index, c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return statements.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return statements.retainAll(c);
	}

	public Stmt remove(int index) {
		return statements.remove(index);
	}

	@Override
	public boolean contains(Object o) {
		return statements.contains(o);
	}

	@Override
	public boolean isEmpty() {
		return statements.isEmpty();
	}

	public int indexOf(Object o) {
		return statements.indexOf(o);
	}

	public int lastIndexOf(Object o) {
		return statements.lastIndexOf(o);
	}

	public Stmt get(int index) {
		return statements.get(index);
	}

	public Stmt set(int index, Stmt stmt) {
		return statements.set(index, stmt);
	}

	@Override
	public int size() {
		return statements.size();
	}

	@Override
	public void clear() {
		statements.clear();
	}

	@Override
	public Iterator<Stmt> iterator() {
		return statements.iterator();
	}

	public ListIterator<Stmt> listIterator() {
		return statements.listIterator();
	}

	public ListIterator<Stmt> listIterator(int index) {
		return statements.listIterator(index);
	}

	@Override
	public Object[] toArray() {
		return statements.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return statements.toArray(a);
	}
	// End list functions
}
