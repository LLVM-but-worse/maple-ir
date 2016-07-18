package org.rsdeob.deobimpl;

import java.util.List;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.collections.graph.util.GraphUtils;
import org.rsdeob.stdlib.deob.IPhase;

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