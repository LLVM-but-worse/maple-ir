package org.mapleir.ir.cfg.builder;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.impl.StaticMethodLocalsPool;
import org.mapleir.ir.locals.impl.VirtualMethodLocalsPool;
import org.mapleir.stdlib.collections.map.NullPermeableHashMap;
import org.mapleir.asm.MethodNode;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public class ControlFlowGraphBuilder {

	protected final MethodNode method;
	protected final ControlFlowGraph graph;
	protected final Set<Local> locals;
	protected final NullPermeableHashMap<Local, Set<BasicBlock>> assigns;
	protected BasicBlock head;
	protected final boolean optimise;

	public ControlFlowGraphBuilder(MethodNode method) {
		this(method, true);
	}

	public ControlFlowGraphBuilder(MethodNode method, boolean optimise) {
		this.optimise = optimise;
		this.method = method;
		if(Modifier.isStatic(method.node.access)) {
			graph = new ControlFlowGraph(new StaticMethodLocalsPool(), method.getJavaDesc());
		} else {
			graph = new ControlFlowGraph(new VirtualMethodLocalsPool(), method.getJavaDesc());
		}
		locals = new HashSet<>();
		assigns = new NullPermeableHashMap<>(HashSet::new);
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
				new NaturalisationPass(this),
				new SSAGenPass(this, optimise),
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
		return builder.buildImpl();
	}
}
