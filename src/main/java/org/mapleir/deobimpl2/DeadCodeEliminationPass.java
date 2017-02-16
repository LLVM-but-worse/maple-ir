package org.mapleir.deobimpl2;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.cfg.edge.TryCatchEdge;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class DeadCodeEliminationPass implements ICompilerPass {

	public static int process(ControlFlowGraph cfg) {
		int i = 0;
		
		boolean changed;
		do {
			changed = false;
			for (BasicBlock b : new HashSet<>(cfg.vertices())) {
				if (cfg.getReverseEdges(b).size() == 0 && !cfg.getEntries().contains(b)) {
					for (FlowEdge<BasicBlock> fe : cfg.getEdges(b))
						for (Stmt stmt : fe.dst)
							if (stmt.getOpcode() == Stmt.PHI_STORE)
								((CopyPhiStmt) stmt).getExpression().getSources().remove(b);
							else break;
					cfg.removeVertex(b);
					
					i++;
					changed = true;
				}
			}
		} while (changed);
		
		return i;
	}
	
	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		int i = 0;
		
		for(ClassNode cn : cxt.getClassTree().getClasses().values()) {
			for(MethodNode m : cn.methods) {
				ControlFlowGraph cfg = cxt.getIR(m);
				
				/* dead blocks */
				
				i += process(cfg);
			}
		}
		
		System.out.printf("  removed %d dead blocks.%n", i);
	}
}