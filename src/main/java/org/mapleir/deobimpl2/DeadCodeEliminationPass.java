package org.mapleir.deobimpl2;

import org.mapleir.deobimpl2.cxt.IContext;
import org.mapleir.stdlib.collections.graph.algorithms.SimpleDfs;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ssaopt.ConstraintUtil;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.cfg.edge.ImmediateEdge;
import org.mapleir.ir.cfg.edge.UnconditionalJumpEdge;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.stdlib.deob.IPass;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class DeadCodeEliminationPass implements IPass {

	public static void safeKill(LocalsPool pool, FlowEdge<BasicBlock> fe) {
		for (Stmt stmt : fe.dst) {
			if (stmt.getOpcode() == Stmt.PHI_STORE) {
				CopyPhiStmt phs = (CopyPhiStmt) stmt;
				PhiExpr phi = phs.getExpression();
				
				BasicBlock pred = fe.src;
				VarExpr arg = (VarExpr) phi.getArgument(pred);
				
				Local l = arg.getLocal();
				pool.uses.get(l).remove(arg);
				
				phi.removeArgument(pred);
			} else {
				return;
			}
		}
	}

	int i = 0;
	int j = 0;
	int k = 0;
	
	public void process(ControlFlowGraph cfg) {
		LocalsPool lp = cfg.getLocals();
		
		boolean c;
		
		do {
			c = false;
			
			SimpleDfs<BasicBlock> dfs = new SimpleDfs<>(cfg, cfg.getEntries().iterator().next(), SimpleDfs.PRE);
			
			List<BasicBlock> pre = dfs.getPreOrder();
			for(BasicBlock b : new HashSet<>(cfg.vertices())) {
				if(!pre.contains(b)) {
//					System.out.println("proc1: " + b);
					LocalsPool pool = cfg.getLocals();
					for(FlowEdge<BasicBlock> fe : cfg.getEdges(b)) {
						safeKill(pool, fe);
					}
//					System.out.println("removed: ");
					for(Stmt stmt : b) {
//						System.out.println(" " + (b.indexOf(stmt)) + ". " + stmt);
						if(stmt instanceof AbstractCopyStmt) {
							AbstractCopyStmt copy = (AbstractCopyStmt) stmt;
							lp.defs.remove(copy.getVariable().getLocal());
//							System.out.println("  kill1 " + copy.getVariable().getLocal());
						}
						
						for(Expr e : stmt.enumerateOnlyChildren()) {
							if(e.getOpcode() == Opcode.LOCAL_LOAD) {
								VarExpr v = (VarExpr) e;
								lp.uses.get(v.getLocal()).remove(v);
//								System.out.println("  kill2 " + v.getLocal());
							}
						}
					}
					cfg.removeVertex(b);
					
					i++;
					c = true;
				} else {
//					System.out.println("proc2: " + b);
					UnconditionalJumpEdge<BasicBlock> uncond = null;
					
					for(FlowEdge<BasicBlock> fe : cfg.getEdges(b)) {
						if(fe.getType() == FlowEdges.UNCOND) {
							uncond = (UnconditionalJumpEdge<BasicBlock>) fe;
						}
					}
					
					if(uncond != null) {
						BasicBlock dst = uncond.dst;
						
						List<BasicBlock> verts = new ArrayList<>(cfg.vertices());
						
						if(verts.indexOf(b) + 1 == verts.indexOf(dst)) {
							ImmediateEdge<BasicBlock> im = new ImmediateEdge<>(b, dst);
							cfg.removeEdge(b, uncond);
							cfg.addEdge(b, im);
							
							Stmt stmt = b.remove(b.size() - 1);
							
							if(stmt.getOpcode() != Opcode.UNCOND_JUMP) {
								throw new IllegalStateException(b + " : " + stmt);
							}
							
							j++;
							c = true;
						}
					}
					
					// if(cfg.getMethod().toString().equals("cf.k(IIIIII)V")) {}
					
					Iterator<Stmt> it = b.iterator();
					while(it.hasNext()) {
						Stmt stmt = it.next();
						
						if(stmt.getOpcode() == Opcode.LOCAL_STORE) {
							AbstractCopyStmt copy = (AbstractCopyStmt) stmt;
							
							if(copy.isSynthetic()) {
								continue;
							}
							
							Local l = copy.getVariable().getLocal();
							LocalsPool pool = cfg.getLocals();
							// System.out.println("copy: "+ copy);
							if(!ConstraintUtil.isUncopyable(copy.getExpression()) && pool.uses.get(l).size() == 0) {
								
								for(Expr e : copy.getExpression().enumerateWithSelf()) {
									if(e.getOpcode() == Opcode.LOCAL_LOAD) {
										VarExpr v = (VarExpr) e;
										
										Local l2 = v.getLocal();
										pool.uses.remove(l2);
									}
								}
								
								pool.uses.remove(l);
								pool.defs.remove(l);
								it.remove();
								
								k++;
								c = true;
							}
						}

					}
				}
			}
			
			// for now
		} while (c);
	}

	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		i = 0;
		j = 0;
		k = 0;
		
		for (ClassNode cn : cxt.getApplication().iterate()) {
			for (MethodNode m : cn.methods) {
				ControlFlowGraph cfg = cxt.getCFGS().getIR(m);

				/* dead blocks */

 				process(cfg);
			}
		}

		System.out.printf("  removed %d dead blocks.%n", i);
		System.out.printf("  converted %d immediate jumps.%n", j);
		System.out.printf("  eliminated %d dead locals.%n", k);
		
		return i + j;
	}
}