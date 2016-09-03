package org.mapleir.ir;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.cfg.edge.DummyEdge;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Set;

public class ControlFlowGraphDumper {
	public static void dump(ControlFlowGraph cfg, MethodNode m) {
		m.visitCode();
		m.instructions.clear();
		m.tryCatchBlocks.clear();

		for (BasicBlock b : cfg.vertices()) {
			b.resetLabel();
		}

		for (BasicBlock b : cfg.vertices()) {
			m.visitLabel(b.getLabel());
			for (Statement stmt : b) {
				stmt.toCode(m, null);
			}
		}

		for (ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			System.out.println("RANGE: " + er);
			String type = null;
			Set<String> typeSet = er.getTypes();
			if (typeSet.size() == 0 || typeSet.size() > 1) {
				// TODO: fix base exception
				type = Throwable.class.getCanonicalName().replace(".", "/");
			} else {
				// size == 1
				type = typeSet.iterator().next();
			}
			List<BasicBlock> range = er.get();
			Label start = range.get(0).getLabel();
			Label end = null;
			BasicBlock endBlock = range.get(range.size() - 1);
			BasicBlock im = endBlock.getImmediate();
			if (im == null) {
				BasicBlock nextBlock = cfg.getBlock(BasicBlock.createBlockName(BasicBlock.numeric(endBlock.getId()) + 1));
				if (nextBlock != null) {
					end = nextBlock.getLabel();
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
			Label handler = er.getHandler().getLabel();
			m.visitTryCatchBlock(start, end, handler, type);
		}
		m.visitEnd();
	}
}
