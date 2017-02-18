package org.mapleir.deobimpl2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.cfg.edge.ImmediateEdge;
import org.mapleir.ir.cfg.edge.UnconditionalJumpEdge;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.IPass;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class DeadCodeEliminationPass implements IPass {

	public static void safeKill(FlowEdge<BasicBlock> fe) {
		for (Stmt stmt : fe.dst) {
			if (stmt.getOpcode() == Stmt.PHI_STORE) {
				CopyPhiStmt phs = (CopyPhiStmt) stmt;
				phs.getExpression().removeArgument(fe.src);
			} else {
				return;
			}
		}
	}

	int i = 0;
	int j = 0;
	
	public void process(ControlFlowGraph cfg) {

		boolean c;
		
		do {
			c = false;
			for (BasicBlock b : new HashSet<>(cfg.vertices())) {
				if (cfg.getReverseEdges(b).size() == 0 && !cfg.getEntries().contains(b)) {
					
					for (FlowEdge<BasicBlock> fe : cfg.getEdges(b)) {
						safeKill(fe);
					}
					cfg.removeVertex(b);
					
					i++;
					c = true;
				} else {
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
				}
			}
		} while (c);
	}

	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		i = 0;
		j = 0;
		
		for (ClassNode cn : cxt.getClassTree().getClasses().values()) {
			for (MethodNode m : cn.methods) {
				ControlFlowGraph cfg = cxt.getIR(m);

				/* dead blocks */

				process(cfg);
			}
		}

		System.out.printf("  removed %d dead blocks.%n", i);
		System.out.printf("  converted %d immediate jumps.%n", j);
		
		return i + j;
	}
}