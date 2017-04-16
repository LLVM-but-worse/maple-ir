package org.mapleir.ir.algorithms;

import org.mapleir.deob.intraproc.ExceptionAnalysis;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.DummyEdge;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.code.Stmt;
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
		List<BasicBlock> blocks = linearize(cfg);
		
		// Dump code
		for (BasicBlock b : blocks) {
			m.visitLabel(b.getLabel());
			for (Stmt stmt : b) {
				stmt.toCode(m, null);
			}
		}
		
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
			dumpRange(cfg, m, blocks, er);
		}
		m.visitEnd();
	}
	
	private static void dumpRange(ControlFlowGraph cfg, MethodNode m, List<BasicBlock> order, ExceptionRange<BasicBlock> er) {
		// Fixup exception type if there are multiple possible exceptions thrown
		Type type = null;
		Set<Type> typeSet = er.getTypes();
		if (typeSet.size() != 1) {
			// TODO: fix base exception
			type = ExceptionAnalysis.THROWABLE;
		} else {
			// size == 1
			type = typeSet.iterator().next();
		}
		
		List<BasicBlock> range = er.get();
		range.sort(Comparator.comparing(order::indexOf));
		
		// Fixup exception if last block is at the end of the CFG (no next label)
		Label end;
		BasicBlock endBlock = range.get(range.size() - 1);
		BasicBlock im = endBlock.getImmediate();
		if (im == null) {
			int endIndex = order.indexOf(endBlock);
			if (endIndex + 1 < order.size()) {
				end = order.get(order.indexOf(endBlock) + 1).getLabel();
			} else {
				LabelNode label = new LabelNode();
				m.visitLabel(label.getLabel());
				BasicBlock newExit = new BasicBlock(cfg, endBlock.getNumericId() + 1, label);
				cfg.addVertex(newExit);
				cfg.addEdge(endBlock, new DummyEdge<>(endBlock, newExit));
				end = label.getLabel();
			}
		} else {
			end = im.getLabel();
		}
		Label start = range.get(0).getLabel();
		Label handler = er.getHandler().getLabel();
		m.visitTryCatchBlock(start, end, handler, type.getInternalName());
	}
	
	private static ArrayList<BasicBlock> linearize(ControlFlowGraph cfg) {
		return new ArrayList<>(cfg.vertices());
	}
}
