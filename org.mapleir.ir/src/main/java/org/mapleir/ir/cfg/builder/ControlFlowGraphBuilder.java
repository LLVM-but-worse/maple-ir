package org.mapleir.ir.cfg.builder;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.impl.StaticMethodLocalsPool;
import org.mapleir.ir.locals.impl.VirtualMethodLocalsPool;
import org.mapleir.ir.utils.CFGUtils;
import org.mapleir.stdlib.collections.map.NullPermeableHashMap;
import org.mapleir.stdlib.collections.map.SetCreator;
import org.objectweb.asm.tree.MethodNode;

public class ControlFlowGraphBuilder {

	private static final Logger LOGGER = Logger.getLogger(ControlFlowGraph.class);
	
	protected final MethodNode method;
	protected final ControlFlowGraph graph;
	protected final Set<Local> locals;
	protected final NullPermeableHashMap<Local, Set<BasicBlock>> assigns;
	protected int count = 0;
	protected BasicBlock head;
	
	public ControlFlowGraphBuilder(MethodNode method) {
		this.method = method;
		if(Modifier.isStatic(method.access)) {
			graph = new ControlFlowGraph(new StaticMethodLocalsPool());
		} else {
			graph = new ControlFlowGraph(new VirtualMethodLocalsPool());

		}
		locals = new HashSet<>();
		assigns = new NullPermeableHashMap<>(SetCreator.getInstance());
	}
	
	public static abstract class BuilderPass {
		protected final ControlFlowGraphBuilder builder;
		
		public BuilderPass(ControlFlowGraphBuilder builder) {
			this.builder = builder;
		}
		
		public abstract void run();
	}
	
	protected BuilderPass[] resolvePasses() {
		return new BuilderPass[] {
				new GenerationPass(this),
				new DeadBlocksPass(this),
				new NaturalisationPass1(this),
				// new NaturalisationPass2(this),
				new SSAGenPass(this),
		};
	}
	
	public ControlFlowGraph buildImpl() {
		for(BuilderPass p : resolvePasses()) {
			p.run();
			// CFGUtils.easyDumpCFG(graph, "post-" + p.getClass().getSimpleName());
		}
		return graph;
	}
	
	public static ControlFlowGraph build(MethodNode method) {
		ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(method);
		// try {
			return builder.buildImpl();
		// } catch (RuntimeException e) {
		// 	LOGGER.error(String.format("Error processing %s", builder.method));
		// 	LOGGER.error(String.format("Current state of cfg (%d blocks):\n%s",
		// 			builder.count, builder.graph));
		// 	LOGGER.error("Failed with error", e);
		// 	throw e;
		// }
	}
}
