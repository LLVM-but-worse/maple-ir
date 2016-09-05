package org.mapleir.ir.cfg.builder;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.dot.ControlFlowGraphDecorator;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.dot.BasicDotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.mapleir.ir.dot.ControlFlowGraphDecorator.OPT_DEEP;

public class ControlFlowGraphBuilder {

	protected final MethodNode method;
	protected final ControlFlowGraph graph;
	protected final Set<Local> locals;
	protected final NullPermeableHashMap<Local, Set<BasicBlock>> assigns;
	protected final Map<VersionedLocal, AbstractCopyStatement> defs;
	protected int count = 0;
	protected BasicBlock exit;
	
	private ControlFlowGraphBuilder(MethodNode method) {
		this.method = method;
		graph = new ControlFlowGraph(method, method.maxLocals);
		
		locals = new HashSet<>();
		assigns = new NullPermeableHashMap<>(new SetCreator<>());
		defs = new HashMap<>();
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
//				new NaturalisationPass2(this),
				new SSAGenPass(this),
				new OptimisationPass(this)
		};
	}
	
	public static ControlFlowGraph build(MethodNode method) {
		ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(method);
		try {
			for(BuilderPass p : builder.resolvePasses()) {
//				System.out.println();
//				System.out.println("BEFORE: " + p.getClass());
//				System.out.println(builder.graph);
//				System.out.println();
//				System.out.println();
				
				p.run();
				
//				System.out.println();
//				System.out.println("AFTER " + p.getClass().getSimpleName() + ":");
//				System.out.println(builder.graph);
//				System.out.println();
//				System.out.println();

				BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
				DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, builder.graph);
				writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP)).setName("post-" + p.getClass().getSimpleName()).export();
			}
			return builder.graph;
		} catch(RuntimeException e) {
			System.err.println(builder.graph);
			throw e;
		}
	}
}