package org.mapleir.ir.cfg.builder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.ir.analysis.SSALocalAccess;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.objectweb.asm.tree.MethodNode;

public class ControlFlowGraphBuilder {

	protected final MethodNode method;
	protected final ControlFlowGraph graph;
	protected final Set<Local> locals;
	protected final NullPermeableHashMap<Local, Set<BasicBlock>> assigns;
	protected SSALocalAccess localAccess;
	protected int count = 0;
	protected BasicBlock head;
	
	private ControlFlowGraphBuilder(MethodNode method) {
		this.method = method;
		graph = new ControlFlowGraph(method, method.maxLocals);
		
		locals = new HashSet<>();
		assigns = new NullPermeableHashMap<>(new SetCreator<>());
	}
	
	protected void naturaliseGraph(List<BasicBlock> order) {
		// copy edge sets
		Map<BasicBlock, Set<FlowEdge<BasicBlock>>> edges = new HashMap<>();
		for(BasicBlock b : order) {
			edges.put(b, graph.getEdges(b));
		}
		// clean graph
		graph.clear();
		
		// rename and add blocks
		int label = 1;
		for(BasicBlock b : order) {
			b.setId(label++);
			graph.addVertex(b);
		}
		
		for(Entry<BasicBlock, Set<FlowEdge<BasicBlock>>> e : edges.entrySet()) {
			BasicBlock b = e.getKey();
			for(FlowEdge<BasicBlock> fe : e.getValue()) {
				graph.addEdge(b, fe);
			}
		}
	}
	
	public static abstract class BuilderPass {
		protected final ControlFlowGraphBuilder builder;
		
		public BuilderPass(ControlFlowGraphBuilder builder) {
			this.builder = builder;
		}
		
		public abstract void run();
	}
	
	private BuilderPass[] resolvePasses() {
		return new BuilderPass[] {
				new GenerationPass(this),
				new NaturalisationPass1(this),
				// new NaturalisationPass2(this),
				new SSAGenPass(this),
				// new OptimisationPass(this),
				// new DeadRangesPass(this)
		};
	}
	
	/* private BuilderPass[] interlace(BuilderPass[] passes) {
		if (Test.temp) {
			BuilderPass[] res = new BuilderPass[passes.length * 2];
			for (int i = 0; i < passes.length; i++) {
				BuilderPass p = passes[i];
				res[i * 2] = p;
				res[(i * 2) + 1] = new VerificationPass(this, p.getClass().getSimpleName());
			}
			return res;
		} else {
			return passes;
		}
	} */
	
	public static ControlFlowGraph build(MethodNode method) {
		ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(method);
		try {
			for(BuilderPass p : builder.resolvePasses()) {
				p.run();			}
			return builder.graph;
		} catch(RuntimeException e) {
			throw e;
		}
	}
}