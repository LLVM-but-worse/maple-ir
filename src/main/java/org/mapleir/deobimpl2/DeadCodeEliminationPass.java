package org.mapleir.deobimpl2;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class DeadCodeEliminationPass implements ICompilerPass {

	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		int i = 0;
		
		for(ClassNode cn : cxt.getClassTree().getClasses().values()) {
			for(MethodNode m : cn.methods) {
				ControlFlowGraph cfg = cxt.getIR(m);
				
				/* dead blocks */
				
				Iterator<BasicBlock> it = new HashSet<>(cfg.vertices()).iterator();
				while(it.hasNext()) {
					BasicBlock b = it.next();
					
					if(cfg.getReverseEdges(b).size() == 0 && !cfg.getEntries().contains(b)) {
						cfg.removeVertex(b);
					
						i++;
					}
				}
			}
		}
		
		System.out.printf("  removed %d dead blocks.%n", i);
	}
}