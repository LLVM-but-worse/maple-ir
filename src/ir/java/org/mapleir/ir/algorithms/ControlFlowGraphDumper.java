package org.mapleir.ir.algorithms;

import org.mapleir.deob.intraproc.ExceptionAnalysis;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.cfg.edge.ImmediateEdge;
import org.mapleir.ir.cfg.edge.UnconditionalJumpEdge;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.stdlib.collections.IndexedList;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.algorithms.SimpleDfs;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanSCC;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class ControlFlowGraphDumper {
	public static void dump(ControlFlowGraph cfg, MethodNode m) {
		// Clear methodnode
		m.instructions.removeAll(true);
		m.tryCatchBlocks.clear();
		m.visitCode();
		for (BasicBlock b : cfg.vertices()) {
			b.resetLabel();
		}

		// Linearize
		IndexedList<BasicBlock> blocks = linearize(cfg);
		
		// Dump code
		for (BasicBlock b : blocks) {
			m.visitLabel(b.getLabel());
			for (Stmt stmt : b) {
				stmt.toCode(m, null);
			}
		}
		LabelNode terminalLabel = new LabelNode();
		m.visitLabel(terminalLabel.getLabel());
		
		// Verify
		ListIterator<BasicBlock> it = blocks.listIterator();
		while(it.hasNext()) {
			BasicBlock b = it.next();
			
			for(FlowEdge<BasicBlock> e: cfg.getEdges(b)) {
				if(e.getType() == FlowEdges.IMMEDIATE) {
					if(it.hasNext()) {
						BasicBlock n = it.next();
						it.previous();
						
						if(n != e.dst) {
							throw new IllegalStateException("Illegal flow " + e + " > " + n);
						}
					} else {
						throw new IllegalStateException("Trailing " + e);
					}
				}
			}
		}

		for (ExceptionRange<BasicBlock> er : cfg.getRanges()) {
//			System.out.println("RANGE: " + er);
			dumpRange(cfg, m, blocks, er, terminalLabel.getLabel());
		}
		m.visitEnd();
	}
	
	private static void dumpRange(ControlFlowGraph cfg, MethodNode m, IndexedList<BasicBlock> order, ExceptionRange<BasicBlock> er, Label terminalLabel) {
		// Determine exception type
		Type type = null;
		Set<Type> typeSet = er.getTypes();
		if (typeSet.size() != 1) {
			// TODO: fix base exception
			type = ExceptionAnalysis.THROWABLE;
		} else {
			type = typeSet.iterator().next();
		}
		
		final Label handler = er.getHandler().getLabel();
		List<BasicBlock> range = er.get();
		range.sort(Comparator.comparing(order::indexOf));
		
		Label start = range.get(0).getLabel();
		int rangeIdx = 0, orderIdx = order.indexOf(range.get(rangeIdx));
		for (;;) {
			// check for endpoints
			if (orderIdx + 1 == order.size()) { // end of method
				m.visitTryCatchBlock(start, terminalLabel, handler, type.getInternalName());
				break;
			} else if (rangeIdx + 1 == range.size()) { // end of range
				Label end = order.get(orderIdx + 1).getLabel();
				m.visitTryCatchBlock(start, end, handler, type.getInternalName());
				break;
			}
			
			// check for discontinuity
			BasicBlock nextBlock = range.get(rangeIdx + 1);
			int nextOrderIdx = order.indexOf(nextBlock);
			if (nextOrderIdx - orderIdx > 1) { // blocks in-between, end the handler and begin anew
				System.err.println("[warn] Had to split up a range: " + m);
				// System.err.println(cfg);
				// System.err.println(m);
				// System.err.println(er);
				// System.err.println("Range: " + range);
				// System.err.println("Order: " + order);
				// System.err.println("range, order, next: " + rangeIdx + " " + orderIdx + " " + nextOrderIdx);
				// System.err.println("corresponding blocks: " + range.get(rangeIdx) + " " + nextBlock);
				// for (int i = 0; i < 20; i++)
				// 	System.err.println();
				Label end = order.get(orderIdx + 1).getLabel();
				m.visitTryCatchBlock(start, end, handler, type.getInternalName());
				start = nextBlock.getLabel();
			}

			// next
			rangeIdx++;
			if (nextOrderIdx != -1)
				orderIdx = nextOrderIdx;
		}
	}
	
	private static IndexedList<BasicBlock> linearize(ControlFlowGraph cfg) {
		if (cfg.getEntries().size() != 1)
			throw new IllegalStateException("CFG doesn't have exactly 1 entry");
		BasicBlock entry = cfg.getEntries().iterator().next();
		
		// Build bundle graph
		Map<BasicBlock, BlockBundle> bundles = new HashMap<>();
		List<BasicBlock> postorder = new SimpleDfs<>(cfg, entry, SimpleDfs.POST).getPostOrder();
		for (BasicBlock b : postorder) {
			if (bundles.containsKey(b))
				continue;
			
			if (b.getIncomingImmediateEdge() != null)
				continue;
			
			BlockBundle bundle = new BlockBundle();
			while (b != null) {
				bundle.add(b);
				bundles.put(b, bundle);
				b = b.getImmediate();
			}
		}
		BundleGraph bundleGraph = new BundleGraph();
		BlockBundle entryBundle = bundles.get(entry);
		bundleGraph.addVertex(entryBundle);
		for (BasicBlock b : postorder) {
			for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				if (e instanceof ImmediateEdge)
					continue;
				BlockBundle src = bundles.get(b);
				bundleGraph.addEdge(src, new FastGraphEdge<>(src, bundles.get(e.dst)));
			}
		}
		
		TarjanSCC<BlockBundle> sccComputor = new TarjanSCC<>(bundleGraph);
		sccComputor.search(entryBundle);
		for(BlockBundle b : bundles.values()) {
			if(sccComputor.low(b) == -1) {
				sccComputor.search(b);
			}
		}
		
		IndexedList<BasicBlock> order = new IndexedList<>();
		
		// Add entry bundles to order first
		List<BlockBundle> entrySCC = null;
		for (List<BlockBundle> scc : sccComputor.getComponents()) {
			int i = scc.indexOf(entryBundle);
			if (i != -1) {
				int stop = i;
				do {
					entrySCC = scc;
					BlockBundle bundle = scc.get(i);
					order.addAll(bundle);
					i = ++i % scc.size();
				} while (i != stop);
				break;
			}
		}
		
		// Add other bundles
		for (List<BlockBundle> scc : sccComputor.getComponents()) {
			if (scc == entrySCC)
				continue;
			scc.forEach(order::addAll);
		}
		
		// Fix immediates
		for (int i = 0; i < order.size(); i++) {
			BasicBlock b = order.get(i);
			for (FlowEdge<BasicBlock> e : new HashSet<>(cfg.getEdges(b))) {
				BasicBlock dst = e.dst;
				if (e instanceof ImmediateEdge && order.indexOf(dst) != i + 1) {
					b.add(new UnconditionalJumpStmt(dst));
					cfg.removeEdge(b, e);
					cfg.addEdge(b, new UnconditionalJumpEdge<>(b, dst));
				}
			}
		}
		
		return order;
	}
	
	private static class BundleGraph extends FastDirectedGraph<BlockBundle, FastGraphEdge<BlockBundle>> {
		@Override
		public boolean excavate(BlockBundle basicBlocks) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public boolean jam(BlockBundle pred, BlockBundle succ, BlockBundle basicBlocks) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public FastGraphEdge<BlockBundle> clone(FastGraphEdge<BlockBundle> edge, BlockBundle oldN, BlockBundle newN) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public FastGraphEdge<BlockBundle> invert(FastGraphEdge<BlockBundle> edge) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public FastGraph<BlockBundle, FastGraphEdge<BlockBundle>> copy() {
			throw new UnsupportedOperationException();
		}
	}
	
	private static class BlockBundle implements FastGraphVertex, Collection<BasicBlock> {
		private List<BasicBlock> blocks = new ArrayList<>();
		
		@Override
		public String getId() {
			return blocks.get(0).getId();
		}
		
		@Override
		public int getNumericId() {
			return blocks.get(0).getNumericId();
		}
		
		@Override
		public boolean add(BasicBlock b) {
			return blocks.add(b);
		}
		
		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public boolean containsAll(Collection<?> c) {
			return blocks.contains(c);
		}
		
		@Override
		public boolean addAll(Collection<? extends BasicBlock> c) {
			return blocks.addAll(c);
		}
		
		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int size() {
			return blocks.size();
		}
		
		@Override
		public boolean isEmpty() {
			return blocks.isEmpty();
		}
		
		@Override
		public boolean contains(Object o) {
			return blocks.contains(o);
		}
		
		@Override
		public Iterator<BasicBlock> iterator() {
			return blocks.iterator();
		}
		
		@Override
		public Object[] toArray() {
			return blocks.toArray();
		}
		
		@Override
		public <T> T[] toArray(T[] a) {
			return blocks.toArray(a);
		}
	}
}
