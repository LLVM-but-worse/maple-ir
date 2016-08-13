package org.mapleir.deobimpl;

import java.util.List;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.ControlFlowGraphBuilder;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.stdlib.deob.IPhase;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ControlFlowFixerPhase implements IPhase {
	public static final String KEY_ID = ControlFlowFixerPhase.class.getCanonicalName();
	
	private ControlFlowGraphDeobfuscator impl = new ControlFlowGraphDeobfuscator();
	
	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
		for(ClassNode cn : cxt.getNodes().values()) {
			for(MethodNode m : cn.methods) {
				if(m.instructions.size() > 0) {
					ControlFlowGraph cfg = ControlFlowGraphBuilder.create(m);
					List<BasicBlock> reordered = impl.deobfuscate(cfg);
					impl.removeEmptyBlocks(cfg, reordered);
					m.instructions = GraphUtils.recreate(cfg, reordered, true);
				}
			}
		}
		

		
		System.out.printf("   Pruned %d gotos.%n", impl.prunedGotos);
		System.out.printf("   Removed %d empty blocks.%n", impl.removedEmpty);
		System.out.printf("   Inlined %d strongly connected components.%n", impl.inlinedComponents);
	}
}