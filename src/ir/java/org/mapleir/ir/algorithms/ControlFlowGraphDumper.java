package org.mapleir.ir.algorithms;

import org.mapleir.deob.intraproc.ExceptionAnalysis;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.collections.IndexedList;
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
		return new IndexedList<>(new ArrayList<>(cfg.vertices()));
	}
}
