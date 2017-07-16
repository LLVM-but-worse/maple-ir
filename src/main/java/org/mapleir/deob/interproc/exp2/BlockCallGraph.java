package org.mapleir.deob.interproc.exp2;

import java.util.*;
import java.util.Map.Entry;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.cfg.edge.ImmediateEdge;
import org.mapleir.ir.cfg.edge.TryCatchEdge;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.SwitchStmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.BasicLocal;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.tree.LabelNode;

public class BlockCallGraph extends FastDirectedGraph<CallGraphBlock, FlowEdge<CallGraphBlock>> {

	private static boolean isContainerFlowFunction(Stmt stmt) {
		int opcode = stmt.getOpcode();
		return opcode == Opcode.SWITCH_JUMP || opcode == Opcode.COND_JUMP || opcode == Opcode.THROW
				|| opcode == Opcode.RETURN;
	}
	
	public static void prepareControlFlowGraph(ControlFlowGraph cfg) {
		LocalsPool locals = cfg.getLocals();
		
		List<BasicBlock> order = new ArrayList<>();
		Map<BasicBlock, BasicBlock> remap = new HashMap<>();
		
		for(BasicBlock originalBlock : new TreeSet<>(cfg.vertices())) {
			order.add(originalBlock);
			
			List<List<Stmt>> subBlockStmts = new ArrayList<>();
			List<Stmt> currentSubBlock = new ArrayList<>();
			int numInsertedCopies = 0;
			
			for(Stmt stmt : originalBlock) {
				currentSubBlock.add(stmt);
				
				int numInvocationsInStatement = countStatementInvocations(stmt, false);
				
				if(numInvocationsInStatement > 0) {
					/* split block::
					 * 
					 * later, when we add the call edge to the
					 * call site and the return edge from it, we
					 * need a suitable place for the edge to come
					 * into. the most natural and probably
					 * accurate way is to have the return edge
					 * feed into the bottom of the block, after
					 * the invocation, but obviously we cannot do
					 * this traditionally as the edges can only
					 * enter at the top of the block.
					 * 
					 * so instead we add an extra block in the
					 * block list (whenever we split due to an
					 * invocation) and it will end up being
					 * linked in the region as normal.
					 * 
					 * the problem we face is regarding flow
					 * functions which contain invocations. in
					 * this case the block will be split here
					 * with the flow function (eg conditional
					 * jump) as the last statement. since there
					 * can be multiple different jump targets, we
					 * have to change the graph so that we have a
					 * single return site for the call. we can
					 * choose to either lift the invocation
					 * expression itself into a spill or
					 * temporary local so that the flow function
					 * references it remotely in the second block
					 * or we can push the entire statement down
					 * into the next block and insert the empty
					 * block into the space between the naturally
					 * ending block and the flow function block.
					 * 
					 * A1: upon further reflection, pushing the
					 * offending statement down won't fix the
					 * problem as the invocation call edge would
					 * have to be moved with it, making the move
					 * redundant. connecting the return edge to
					 * the successors also wouldn't work in the
					 * case of return statements (and possibly
					 * throws as well).
					 * 
					 * A2: also it is important to note that in
					 * order to retain the edge and flow ordering
					 * of the graph we have to maintain ordering
					 * within the block with respect to
					 * invocations. therefore all invocations
					 * must be lifted into temporary locals and
					 * kept in the block, with the use statement
					 * pushed down into another block.
					 * 
					 * refining this further, the affected flow
					 * functions are return, switch, conditional
					 * jump and throw statements, however, any
					 * statement or expression that holds more
					 * than one invocation could potentially
					 * offend here (by A2), so we must spill.
					 * 
					 * spilling is required. */

//					/* this block stays here as normal. */
					
					if(numInvocationsInStatement > 1 || isContainerFlowFunction(stmt)) {
						/* in order to efficiently spill
						 * expressions into locals, we require
						 * more analysis information. for now,
						 * therefore, we will spill every sub
						 * expression of the statement in a new
						 * temporary local so that the runtime
						 * semantics stay unaffected. */
						
						/* remove the statement we added, we're
						 * going to move it into the next block
						 * but first we will store the invoke
						 * value in temporaries. */
						currentSubBlock.remove(currentSubBlock.size() - 1);
						
						BasicLocal baseLocal = locals.getNextFreeLocal(false);
						
						List<Expr> stmtDirectChildren = stmt.getChildren();

						/* create a new block for each spill copy
						 * that stores an invocation value. */
						for(int i=0; i < stmtDirectChildren.size(); i++) {
							VersionedLocal spillLocal = locals.get(baseLocal.getIndex(), i, false);
							Expr expr1 = stmtDirectChildren.get(i);
							Expr expr2 = stmt.overwrite(new VarExpr(spillLocal, expr1.getType()), i);
							
							if(expr1 != expr2) {
								System.err.println(expr1);
								System.err.println(expr2);
								throw new RuntimeException();
							}
							
							CopyVarStmt copyStmt = new CopyVarStmt(new VarExpr(spillLocal, expr1.getType()), expr1);
							currentSubBlock.add(copyStmt);
							numInsertedCopies++;
							
							if(countStatementInvocations(expr1, true) > 0) {
								subBlockStmts.add(currentSubBlock);
								currentSubBlock = new ArrayList<>();
							}
						}
						
						if(!currentSubBlock.isEmpty()) {
							subBlockStmts.add(currentSubBlock);
						}
						
						/* create and add the flowfunc block. */
						List<Stmt> flowFunctionBlock = new ArrayList<>();
						flowFunctionBlock.add(stmt);
						subBlockStmts.add(flowFunctionBlock);
					} else {
						subBlockStmts.add(currentSubBlock);
					}
					
					/* refresh for next block. */
					currentSubBlock = new ArrayList<>();
					
					/* empty block for clean return edge */
					subBlockStmts.add(new ArrayList<>());
				}
			}
			
			/* we don't have to consider any splitting mechanism
			 * here as if the current sub block contained any
			 * invocations, it would've been split above. */
			if(!currentSubBlock.isEmpty() && !subBlockStmts.contains(currentSubBlock)) {
				subBlockStmts.add(currentSubBlock);
			}

			/* if we have no subBlocks, it means the
			 * original block was empty.
			 * if we have 1 subBlock, there was no
			 * invoke in the block.
			 * in both cases we don't have to do anything. */
			
			int blockIDCounter = cfg.size() + 2;
			
			if(subBlockStmts.size() > 1) {
				/* just need to transfer edges. have
				 * incoming edges into the first block
				 * in the list and link them by a single
				 * immediate. also need to clone exception
				 * edges. */
				
				List<BasicBlock> subBlocks = new ArrayList<>();
				
				int totalInstructions = originalBlock.size() + numInsertedCopies;
				
				List<Stmt> firstList = subBlockStmts.get(0);
				
				BasicBlock firstBlock = new BasicBlock(cfg, blockIDCounter++, new LabelNode());
				remap.put(originalBlock, firstBlock);
				
				/* copy flags in case of special block (such as entry). */
				firstBlock.setFlags(originalBlock.getFlags());
				originalBlock.removeAll(firstList);
				firstBlock.addAll(firstList);
				subBlocks.add(firstBlock);
				
				BasicBlock lastBlock = firstBlock;
				
				int accountedForInstructions = firstList.size();
				
				/* link region via immediate edges. */
				Iterator<List<Stmt>> subBlockIterator = subBlockStmts.iterator();
				subBlockIterator.next(); // skip first (already done).
				while(subBlockIterator.hasNext()) {
					List<Stmt> subBlock = subBlockIterator.next();
					
					BasicBlock currentBlock = new BasicBlock(cfg, blockIDCounter++, new LabelNode());
					originalBlock.removeAll(subBlock);
					currentBlock.addAll(subBlock);
					subBlocks.add(currentBlock);
					
					accountedForInstructions += subBlock.size();
					
					cfg.addEdge(lastBlock, new ImmediateEdge<>(lastBlock, currentBlock));
					lastBlock = currentBlock;
				}
				
				if(originalBlock.size() != 0) {
					throw new RuntimeException(Integer.toString(originalBlock.size()));
				} else if(accountedForInstructions != totalInstructions) {
					throw new RuntimeException(String.format("subBlocks:%n%sremaining: %d, total: %d, accountedFor: %d", ControlFlowGraph.printBlocks(subBlocks), originalBlock.size(), totalInstructions, accountedForInstructions));
				}
				
				/* lastBlock contains the last block now. */
				
				/* list of ranges this block was a participant in.
				 * we need to create between each block in the new
				 * region to the handler of this range. */
				Set<ExceptionRange<BasicBlock>> originalBlockRanges = new HashSet<>();
				
				/* clone predecessor edges, src stays the same but 
				 * leads to the first block of the new region. 
				 * if the original block was a handler, we need to
				 * set the new first block as the handler as well
				 * as cloning the edges. */
				Iterator<FlowEdge<BasicBlock>> predEdgesIterator = new HashSet<>(cfg.getReverseEdges(originalBlock)).iterator();
				while(predEdgesIterator.hasNext()) {
					FlowEdge<BasicBlock> predEdge = predEdgesIterator.next();
					BasicBlock src = predEdge.src;
					
					boolean isExceptionEdge = predEdge.getType() == FlowEdges.TRYCATCH;
					
					/* special case (HAARD): self looping
					 * blocks. */
					if(src == originalBlock) {
						src = lastBlock;
					}
					
					/* FIXED: we need to do this here because
					 * TryCatchEdge.clone is very unintuitive.
					 * it uses the erange handler as it's dst
					 * node. if we don't set the handler of the 
					 * erange first, the dst stays the same. */
					if(isExceptionEdge) {
						TryCatchEdge<BasicBlock> tce = (TryCatchEdge<BasicBlock>) predEdge;
						ExceptionRange<BasicBlock> owningRange = tce.erange;
						
						owningRange.setHandler(firstBlock);
					}
					
					FlowEdge<BasicBlock> clonedPredEdge = predEdge.clone(src, firstBlock);
					cfg.addEdge(src, clonedPredEdge);
					
					if(!isExceptionEdge) {
						/* correct the flow function in the predecessor.
						 * (no flow function for exception edge) */
						if(src.size() > 0) {
							Stmt lastStmt = src.get(src.size() - 1);
							int lastStmtOpcode = lastStmt.getOpcode();
							
							switch(lastStmtOpcode) {
								case Opcode.COND_JUMP: {
									ConditionalJumpStmt j = (ConditionalJumpStmt) lastStmt;
									if(j.getTrueSuccessor() == originalBlock) {
										j.setTrueSuccessor(firstBlock);
									}
									break;
								}
								case Opcode.UNCOND_JUMP: {
									UnconditionalJumpStmt j = (UnconditionalJumpStmt) lastStmt;
									if(j.getTarget() != originalBlock) {
										throw new IllegalStateException();
									} else {
										j.setTarget(firstBlock);
									}
									break;
								}
								case Opcode.SWITCH_JUMP: {
									SwitchStmt s = (SwitchStmt) lastStmt;
									for (Entry<Integer, BasicBlock> en : s.getTargets().entrySet()) {
										BasicBlock t = en.getValue();
										if (t == originalBlock) {
											en.setValue(firstBlock);
										}
									}
									break;
								}
							}
						}
					}
				}
				
				/* clone only non exceptional successor edges, dst
				 * is the same but comes from the new last block
				 * of the new region. */
				Iterator<FlowEdge<BasicBlock>> succEdgesIterator = new HashSet<>(cfg.getEdges(originalBlock)).iterator();
				while(succEdgesIterator.hasNext()) {
					FlowEdge<BasicBlock> succEdge = succEdgesIterator.next();

					BasicBlock dst = succEdge.dst;
					
					if(succEdge.getType() != FlowEdges.TRYCATCH) {
						if(dst == firstBlock) {
							throw new RuntimeException("??");
						}
						
						/* if it's a self loop, we have already added
						 * the self loop edge above when dealing with
						 * predecessors. */
						if(dst != originalBlock) {
							FlowEdge<BasicBlock> clonedSuccEdge = succEdge.clone(lastBlock, dst);
							cfg.addEdge(lastBlock, clonedSuccEdge);
						}
					} else {
						TryCatchEdge<BasicBlock> tce = (TryCatchEdge<BasicBlock>) succEdge;
						originalBlockRanges.add(tce.erange);
					}
				}
				
				/* create edges from each block in the region
				 * to the handlers and add each of the new blocks
				 * to the range. */
				for(ExceptionRange<BasicBlock> er : originalBlockRanges) {
					for(BasicBlock b : subBlocks) {
						cfg.addEdge(b, new TryCatchEdge<>(b, er));
					}
					
					/* remove the original block from each of the
					 * ranges and add the new ones. */
					er.addVertices(originalBlock, subBlocks);
					er.removeVertex(originalBlock);
				}
				
				/* finally remove the original block completely. */
				cfg.removeVertex(originalBlock);
				order.addAll(subBlocks);
				order.remove(originalBlock);
				
			}
		}
		
		/* fix phi operand sources. */
		remapPhiOperandSources(cfg, remap);
		
		cfg.naturalise(order);
	}
	
	private static void remapPhiOperandSources(ControlFlowGraph cfg, Map<BasicBlock, BasicBlock> remap) {
		for(BasicBlock basicBlock : cfg.vertices()) {
			for(Stmt stmt : basicBlock) {
				if(stmt.getOpcode() == Opcode.PHI_STORE) {
					CopyPhiStmt copyPhiStmt = (CopyPhiStmt) stmt;
					PhiExpr phi = copyPhiStmt.getExpression();
					
					Map<BasicBlock, Expr> phiArgumentMap = phi.getArguments();
					
					for(BasicBlock originalSource : new TreeSet<>(phiArgumentMap.keySet())) {
						if(remap.containsKey(originalSource)) {
							phiArgumentMap.put(remap.get(originalSource), phiArgumentMap.remove(originalSource));
						}
					}
				} else {
					break;
				}
			}
		}
	}
	
	private static int countStatementInvocations(CodeUnit u, boolean includeSelf) {
		if(includeSelf && (u.isFlagSet(CodeUnit.FLAG_STMT))) {
			throw new RuntimeException();
		}
		
		int count = 0;
		
		for(Expr e : includeSelf ? ((Expr) u).enumerateWithSelf() : u.enumerateOnlyChildren()) {
			int opcode = e.getOpcode();
			
			if(opcode == Opcode.INIT_OBJ || opcode == Opcode.INVOKE || opcode == Opcode.DYNAMIC_INVOKE) {
				count++;
			}
		}
		
		return count;
	}

	@Override
	public boolean excavate(CallGraphBlock n) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean jam(CallGraphBlock pred, CallGraphBlock succ, CallGraphBlock n) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public FlowEdge<CallGraphBlock> clone(FlowEdge<CallGraphBlock> edge, CallGraphBlock oldN, CallGraphBlock newN) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FlowEdge<CallGraphBlock> invert(FlowEdge<CallGraphBlock> edge) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FastGraph<CallGraphBlock, FlowEdge<CallGraphBlock>> copy() {
		// TODO Auto-generated method stub
		return null;
	}
}