package org.mapleir.ir.analysis;

import java.util.HashMap;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.stmt.ConditionalJumpStatement;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.SwitchStatement;
import org.mapleir.ir.code.stmt.UnconditionalJumpStatement;
import org.mapleir.stdlib.cfg.edge.ConditionalJumpEdge;
import org.mapleir.stdlib.cfg.edge.DefaultSwitchEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.cfg.edge.SwitchEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.cfg.edge.UnconditionalJumpEdge;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class StatementGraphBuilder {
	
	public static StatementGraph create(ControlFlowGraph cfg) {
		HashMap<String, ExceptionRange<Statement>> ranges = new HashMap<>();

		StatementGraph sg = new StatementGraph();
		
		for(BasicBlock b : cfg.getEntries()) {
			sg.getEntries().add(b.getAt(0));
		}

		for (BasicBlock b : cfg.vertices()) {
			for (Statement stmt : b) {
				sg.addVertex(stmt);
			}
			for (int i = 0; i < b.size() - 1; i++) {
				Statement stmt = b.getAt(i);
				Statement next = b.getAt(i + 1);
				sg.addEdge(stmt, new ImmediateEdge<>(stmt, next));
			}
			Statement last = b.getAt(b.size() - 1);
			for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				Statement targetStmt = e.dst.getAt(0);
				FlowEdge<Statement> edge;
				switch (e.getClass().getSimpleName()) {
					case "ConditionalJumpEdge":
						if (!(last instanceof ConditionalJumpStatement))
							throw new IllegalArgumentException("Last statement of block with ConditionalJumpEdge is not a ConditionalJumpStatement");
						edge = new ConditionalJumpEdge<>(last, targetStmt, ((ConditionalJumpEdge<BasicBlock>) e).opcode);
						break;
					case "UnconditionalJumpEdge":
						if (!(last instanceof UnconditionalJumpStatement))
							throw new IllegalArgumentException("Last statement of block with UnconditionalJumpEdge is not a UnconditionalJumpStatement");
						edge = new UnconditionalJumpEdge<>(last, targetStmt, ((UnconditionalJumpEdge<BasicBlock>) e).opcode);
						break;
					case "ImmediateEdge":
						edge = new ImmediateEdge<>(last, targetStmt);
						break;
					case "TryCatchEdge":
						ExceptionRange<BasicBlock> bRange = ((TryCatchEdge<BasicBlock>) e).erange;
						TryCatchBlockNode tc = bRange.getNode();
						
						// System.out.println("Range: " + bRange);
						// System.out.println("start: " + tc.start.hashCode() + " , end "+ tc.end.hashCode());
						
						int start = BasicBlock.numeric(cfg.getBlock(tc.start).getId());
						// int end = LabelHelper.numeric(cfg.getBlock(tc.end).getId()) - 1;
						int end = BasicBlock.numeric(bRange.get().get(bRange.get().size() - 1).getId());
						BasicBlock handler = cfg.getBlock(tc.handler);
						String key = String.format("%s:%s:%s", BasicBlock.createBlockName(start), BasicBlock.createBlockName(end), handler.getId());
						if (!ranges.containsKey(key)) {
							ExceptionRange<Statement> sRange = new ExceptionRange<>(tc);
							sRange.setHandler(handler.getAt(0));
							for (BasicBlock protectedBlock : bRange.get())
								for (Statement protectedStmt : protectedBlock)
									sRange.addVertex(protectedStmt);
							ranges.put(key, sRange);
						}
						
						edge = null;
						
						ExceptionRange<Statement> range = ranges.get(key);
						for(Statement stmt : b) {
							TryCatchEdge<Statement> e1 = new TryCatchEdge<>(stmt, range);
							sg.addEdge(stmt, e1);
						}
						break;
					case "SwitchEdge":
						if (!(last instanceof SwitchStatement))
							throw new IllegalArgumentException("Last statement of block with SwitchEdge is not a SwitchStatement");
						SwitchEdge<BasicBlock> se = (SwitchEdge<BasicBlock>) e;
						edge = new SwitchEdge<>(last, targetStmt, se.insn, se.value);
						break;
					case "DefaultSwitchEdge":
						if (!(last instanceof SwitchStatement))
							throw new IllegalArgumentException("Last statement of block with DefaultSwitchEdge is not a SwitchStatement");
						edge = new DefaultSwitchEdge<>(last, targetStmt, ((DefaultSwitchEdge<BasicBlock>) e).insn);
						break;
					default:
						throw new IllegalArgumentException("Illegal edge type " + e.getClass().getSimpleName());
				}
				if(edge != null) {
					sg.addEdge(last, edge);
				}
			}
			
			for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
				ExceptionRange<Statement> newRange = new ExceptionRange<>(er.getNode());
				newRange.setHandler(er.getHandler().getAt(0));
				for(String type : er.getTypes()) {
					newRange.addType(type);
				}
				for(BasicBlock block : er.get()) {
					for(Statement stmt : block) {
						newRange.addVertex(stmt);
					}
				}
				newRange.hashCode(); // recalc
				sg.addRange(newRange);
			}
		}

		return sg;
	}
}
