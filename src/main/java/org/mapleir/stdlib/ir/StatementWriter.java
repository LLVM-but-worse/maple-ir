package org.mapleir.stdlib.ir;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.DummyEdge;
import org.mapleir.stdlib.cfg.util.LabelHelper;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.ir.header.BlockHeaderStatement;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

public class StatementWriter {
	
	private final CodeBody stmts;
	private final ControlFlowGraph cfg;
	private final Map<BasicBlock, HeaderStatement> headers;

	public StatementWriter(CodeBody stmts, ControlFlowGraph cfg) {
		this.stmts = stmts;
		this.cfg = cfg;

		this.headers = new HashMap<>();
		for (Statement stmt : stmts) {
			if (stmt instanceof BlockHeaderStatement) {
				headers.put(((BlockHeaderStatement) stmt).getBlock(), (HeaderStatement) stmt);
			}
		}
	}

	public void dump(MethodNode m, CodeAnalytics analytics) {
		m.visitCode();
		m.instructions.clear();
		m.tryCatchBlocks.clear();
		stmts.getLocals().realloc(stmts);
		
		for(HeaderStatement header : headers.values())
			header.resetLabel();
		for (Statement stmt : stmts) {
			stmt.toCode(m, analytics);
		}

		for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			String type = null;
			Set<String> typeSet = er.getTypes();
			if(typeSet.size() == 0 || typeSet.size() > 1) {
				// TODO: fix base exception
				type = Throwable.class.getCanonicalName().replace(".", "/");
			} else {
				// size == 1
				type = typeSet.iterator().next();
			}
			List<BasicBlock> range = er.get();
			Label start = headers.get(range.get(0)).getLabel();
			Label end = null;
			BasicBlock endBlock = range.get(range.size() - 1);
			BasicBlock im = endBlock.getImmediate();
			if (im == null) {
				BasicBlock nextBlock = cfg.getBlock(LabelHelper.createBlockName(LabelHelper.numeric(endBlock.getId()) + 1));
				if (nextBlock != null) {
					end = headers.get(nextBlock).getLabel();
				} else {
					LabelNode label = new LabelNode();
					m.visitLabel(label.getLabel());
					BasicBlock newExit = new BasicBlock(cfg, LabelHelper.createBlockName(LabelHelper.numeric(endBlock.getId()) + 1), label);
					cfg.addVertex(newExit);
					cfg.addEdge(endBlock, new DummyEdge<BasicBlock>(endBlock, newExit));
					end = label.getLabel();
				}
			} else {
				end = headers.get(im).getLabel();
			}
			Label handler = headers.get(er.getHandler()).getLabel();
			m.visitTryCatchBlock(start, end, handler, type);
		}
		m.visitEnd();
	}
}
