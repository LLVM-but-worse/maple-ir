package org.rsdeob.stdlib.cfg.ir;

import java.util.HashMap;

import org.objectweb.asm.tree.TryCatchBlockNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.ConditionalJumpEdge;
import org.rsdeob.stdlib.cfg.edge.DefaultSwitchEdge;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.ImmediateEdge;
import org.rsdeob.stdlib.cfg.edge.SwitchEdge;
import org.rsdeob.stdlib.cfg.edge.TryCatchEdge;
import org.rsdeob.stdlib.cfg.edge.UnconditionalJumpEdge;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.LabelHelper;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;

public class StatementGraphBuilder {
	
//	public static StatementGraph create(ControlFlowGraph cfg) {
//		HashMap<String, ExceptionRange<Statement>> ranges = new HashMap<>();
//		StatementGraph sg = new StatementGraph();
//		
//		Set<BasicBlock> entries = cfg.getEntries();
//		for(BasicBlock b : cfg.vertices()) {
//			List<Statement> statements = b.getStatements();
//			if(entries.contains(b)) {
//				sg.getEntries().add(statements.get(0));
//			}
//			
//			int size = statements.size();
//			for(int i=0; i < size - 1; i++) {
//				Statement s = statements.get(i);
//				Statement n = statements.get(i + 1);
//				sg.addVertex(s);
//				sg.addVertex(n);
//				sg.addEdge(s, new ImmediateEdge<>(s, n));
//			}
//			
//			Statement last = statements.get(size - 1);
//			sg.addVertex(last);
//		
//			for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
//				BasicBlock targetBlock = e.dst;
//				List<Statement> targetBlockStatements = targetBlock.getStatements();
//				if(e instanceof TryCatchEdge) {
//					ExceptionRange<BasicBlock> bRange = ((TryCatchEdge<BasicBlock>) e).erange;
//					TryCatchBlockNode tc = bRange.getNode();
//					int start = LabelHelper.numeric(cfg.getBlock(tc.start).getId());
//					int end = LabelHelper.numeric(cfg.getBlock(tc.end).getId()) - 1;
//					BasicBlock handler = cfg.getBlock(tc.handler);
//					String key = String.format("%s:%s:%s", LabelHelper.createBlockName(start), LabelHelper.createBlockName(end), handler.getId());
//					if (!ranges.containsKey(key)) {
//						ExceptionRange<Statement> sRange = new ExceptionRange<>(tc);
//						sRange.setHandler(handler.getStatements().get(0));
//						for (BasicBlock protectedBlock : bRange.get())
//							for (Statement protectedStmt : protectedBlock.getStatements())
//								sRange.addVertex(protectedStmt);
//						ranges.put(key, sRange);
//					}
//					ExceptionRange<Statement> range = ranges.get(key);
//					// all statements in the current block can throw and
//					// jump to the handler
//					for(Statement stmt : statements) {
//						sg.addEdge(stmt, new TryCatchEdge<Statement>(stmt, range));
//					}
//				} else {
//					Statement targetStatement = targetBlockStatements.get(0);
//					sg.addVertex(targetStatement);
//					FlowEdge<Statement> newEdge = null;
//					
//					if(e instanceof ConditionalJumpEdge) {
//						newEdge = new ConditionalJumpEdge<>(last, targetStatement, ((ConditionalJumpEdge<BasicBlock>) e).opcode);
//					} else if(e instanceof UnconditionalJumpEdge) {
//						newEdge = new UnconditionalJumpEdge<>(last, targetStatement, ((UnconditionalJumpEdge<BasicBlock>) e).opcode);
//					} else if(e instanceof ImmediateEdge) {
//						newEdge = new ImmediateEdge<>(last, targetStatement);
//					} else if(e instanceof SwitchEdge) {
//						SwitchEdge<BasicBlock> se = (SwitchEdge<BasicBlock>) e;
//						newEdge = new SwitchEdge<>(last, targetStatement, se.insn, se.value);
//					} else if(e instanceof DefaultSwitchEdge) {
//						newEdge = new DefaultSwitchEdge<>(last, targetStatement, ((DefaultSwitchEdge<BasicBlock>) e).insn);
//					} else {
//						throw new IllegalArgumentException("Illegal edge type " + e.getClass().getSimpleName());
//					}
//
//					sg.addEdge(last, newEdge);
//				}
//			}
//			
//			for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
//				ExceptionRange<Statement> newRange = new ExceptionRange<>(er.getNode());
//				for(String type : er.getTypes()) {
//					newRange.addType(type);
//				}
//				for(BasicBlock block : er.get()) {
//					newRange.addVertices(block.getStatements());
//				}
//				newRange.hashCode(); // recalc
//			}
//		}
//		
//		return sg;
//	}
	
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
						int start = LabelHelper.numeric(cfg.getBlock(tc.start).getId());
						int end = LabelHelper.numeric(cfg.getBlock(tc.end).getId()) - 1;
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
							TryCatchEdge<Statement> e1 = new TryCatchEdge<Statement>(stmt, range);
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
