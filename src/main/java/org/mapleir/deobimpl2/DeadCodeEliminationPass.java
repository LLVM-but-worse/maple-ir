package org.mapleir.deobimpl2;

import java.util.HashSet;
import java.util.List;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
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
	
	public static int process(ControlFlowGraph cfg) {
		int i = 0;

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
				}
			}
		} while (c);

		return i;
	}

	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		int i = 0;

		for (ClassNode cn : cxt.getClassTree().getClasses().values()) {
			for (MethodNode m : cn.methods) {
				ControlFlowGraph cfg = cxt.getIR(m);

				/* dead blocks */

				i += process(cfg);
			}
		}

		System.out.printf("  removed %d dead blocks.%n", i);
		
		return i;
	}
}