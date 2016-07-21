package org.mapleir.stdlib.ir.gen;

import java.util.HashMap;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.ConditionalJumpEdge;
import org.mapleir.stdlib.cfg.edge.DefaultSwitchEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.cfg.edge.SwitchEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.cfg.edge.UnconditionalJumpEdge;
import org.mapleir.stdlib.cfg.util.LabelHelper;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.ir.StatementGraph;
import org.mapleir.stdlib.ir.stat.Statement;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class StatementGraphBuilder {
	
	public static StatementGraph create(ControlFlowGraph cfg) {
		HashMap<String, ExceptionRange<Statement>> ranges = new HashMap<>();

		StatementGraph sg = new StatementGraph();
		
		for(BasicBlock b : cfg.getEntries()) {
			sg.getEntries().add(b.getStatements().get(0));
		}

		for (BasicBlock b : cfg.vertices()) {
			for (Statement stmt : b.getStatements()) {
				sg.addVertex(stmt);
			}
			for (int i = 0; i < b.getStatements().size() - 1; i++) {
				Statement stmt = b.getStatements().get(i);
				Statement next = b.getStatements().get(i + 1);
				sg.addEdge(stmt, new ImmediateEdge<>(stmt, next));
			}
			Statement last = b.getStatements().get(b.getStatements().size() - 1);
			for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				Statement targetStmt = e.dst.getStatements().get(0);
				FlowEdge<Statement> edge;
				switch (e.getClass().getSimpleName()) {
					case "ConditionalJumpEdge":
						edge = new ConditionalJumpEdge<>(last, targetStmt, ((ConditionalJumpEdge<BasicBlock>) e).opcode);
						break;
					case "UnconditionalJumpEdge":
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
						
						int start = LabelHelper.numeric(cfg.getBlock(tc.start).getId());
						// int end = LabelHelper.numeric(cfg.getBlock(tc.end).getId()) - 1;
						int end = LabelHelper.numeric(bRange.get().get(bRange.get().size() - 1).getId());
						BasicBlock handler = cfg.getBlock(tc.handler);
						String key = String.format("%s:%s:%s", LabelHelper.createBlockName(start), LabelHelper.createBlockName(end), handler.getId());
						if (!ranges.containsKey(key)) {
							ExceptionRange<Statement> sRange = new ExceptionRange<>(tc);
							sRange.setHandler(handler.getStatements().get(0));
							for (BasicBlock protectedBlock : bRange.get())
								for (Statement protectedStmt : protectedBlock.getStatements())
									sRange.addVertex(protectedStmt);
							ranges.put(key, sRange);
						}
						
						edge = null;
						
						ExceptionRange<Statement> range = ranges.get(key);
						for(Statement stmt : b.getStatements()) {
							TryCatchEdge<Statement> e1 = new TryCatchEdge<>(stmt, range);
							sg.addEdge(stmt, e1);
						}
						break;
					case "SwitchEdge":
						SwitchEdge<BasicBlock> se = (SwitchEdge<BasicBlock>) e;
						edge = new SwitchEdge<>(last, targetStmt, se.insn, se.value);
						break;
					case "DefaultSwitchEdge":
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
				newRange.setHandler(er.getHandler().getStatements().get(0));
				for(String type : er.getTypes()) {
					newRange.addType(type);
				}
				for(BasicBlock block : er.get()) {
					newRange.addVertices(block.getStatements());
				}
				newRange.hashCode(); // recalc
				sg.addRange(newRange);
			}
		}

		return sg;
	}
}
