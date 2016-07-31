package org.mapleir.test;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.ControlFlowGraphBuilder;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.mapleir.stdlib.collections.graph.dot.BasicDotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.collections.graph.dot.impl.ControlFlowGraphDecorator;
import org.mapleir.stdlib.collections.graph.dot.impl.InterferenceGraphDecorator;
import org.mapleir.stdlib.collections.graph.dot.impl.LivenessDecorator;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.StatementGraph;
import org.mapleir.stdlib.ir.gen.SSAGenerator;
import org.mapleir.stdlib.ir.gen.StatementGenerator;
import org.mapleir.stdlib.ir.gen.StatementGraphBuilder;
import org.mapleir.stdlib.ir.gen.interference.ColourableNode;
import org.mapleir.stdlib.ir.gen.interference.InterferenceEdge;
import org.mapleir.stdlib.ir.gen.interference.InterferenceGraph;
import org.mapleir.stdlib.ir.gen.interference.InterferenceGraphBuilder;
import org.mapleir.stdlib.ir.transform.SSATransformer;
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.mapleir.stdlib.ir.transform.ssa.SSAInitialiserAggregator;
import org.mapleir.stdlib.ir.transform.ssa.SSALivenessAnalyser;
import org.mapleir.stdlib.ir.transform.ssa.SSALocalAccess;
import org.mapleir.stdlib.ir.transform.ssa.SSAPropagator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

import static org.mapleir.stdlib.collections.graph.dot.impl.ControlFlowGraphDecorator.OPT_DEEP;
import static org.mapleir.stdlib.collections.graph.dot.impl.ControlFlowGraphDecorator.OPT_STMTS;

@SuppressWarnings({"Duplicates", "ConstantConditions"})
public class LivenessTest implements Opcodes {
	private static boolean predicate() {
		return true;
	}
	
	private static int consumer(int a, int b, int c) {
		if (a <= 0)
			throw new IllegalArgumentException("a must be a counting number (was " + a + ")");
		return a * b + c;
	}
	
	private static void testSwap() {
		int x = 1;
		int y = 2;
		while(!predicate()) {
			int z = x;
			x = y;
			y = z;
		}
		
		System.out.println(x);
		System.out.println(y);
	}
	
	private static void testHandler() {
		try {
			int x = 1;
			int y = predicate() ? Integer.parseInt("2") : 2;
			System.out.println(consumer(x - y, x, y));
		} catch (IllegalArgumentException e) {
			System.err.println("o no :(");
		}
	}
	
	public static void main(String[] args) throws Exception {
		ClassReader cr = new ClassReader(LivenessTest.class.getCanonicalName());
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		
		for (MethodNode m : new ArrayList<>(cn.methods)) {
			if (!m.name.equals("testSwap"))
				continue;
			System.out.println("Processing " + m + "\n");
			
			// Build CFG
			ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(m);
			ControlFlowGraph cfg = builder.build();
			ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
			List<BasicBlock> blocks = deobber.deobfuscate(cfg);
			deobber.removeEmptyBlocks(cfg, blocks);
			GraphUtils.naturaliseGraph(cfg, blocks);
//			CFGStatementDecorator ex = new CFGStatementDecorator(cfg, new ArrayList<>(cfg.vertices()), "graph", "");
//			ex.export(DotExporter.OPT_DEEP);
			
			// Build statements
			StatementGenerator gen = new StatementGenerator(cfg);
			gen.init(m.maxLocals);
			gen.createExpressions();
			CodeBody code = gen.buildRoot();
			SSAGenerator ssagen = new SSAGenerator(code, cfg, gen.getHeaders());
			ssagen.run();
			
			// Transform
			StatementGraph sgraph = StatementGraphBuilder.create(cfg);
			SSALocalAccess localAccess = new SSALocalAccess(code);
			SSATransformer[] transforms = initTransforms(code, localAccess, sgraph, gen);
			while (true) {
				int change = 0;
				for (SSATransformer t : transforms) {
					change += t.run();
				}
				if (change <= 0) {
					break;
				}
			}
			GraphUtils.rewriteCfg(cfg, code);
			
			SSALivenessAnalyser liveness = new SSALivenessAnalyser(cfg);
			InterferenceGraph ig = InterferenceGraphBuilder.build(liveness);
			BasicDotConfiguration<InterferenceGraph, ColourableNode, InterferenceEdge> config2 = new BasicDotConfiguration<>(DotConfiguration.GraphType.UNDIRECTED);
			DotWriter<InterferenceGraph, ColourableNode, InterferenceEdge> w2 = new DotWriter<>(config2, ig);
			w2.add(new InterferenceGraphDecorator()).setName(m.name + "-ig").export();
			
			BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
			DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> w = new DotWriter<>(config, cfg);
			w.add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
					.setName(m.name + "-stmts")
					.export();
			
			SSABlockLivenessAnalyser blockLiveness = new SSABlockLivenessAnalyser(cfg);
			GraphUtils.rewriteCfg(cfg, code);
			blockLiveness.compute();
			
			w.removeAll()
					.setName(m.name + "-bliveness")
					.add("liveness", new LivenessDecorator().setBlockLiveness(blockLiveness).applyComments(cfg))
					.addBefore("liveness", "cfg", new ControlFlowGraphDecorator().setFlags(OPT_DEEP | OPT_STMTS))
					.export();
			
			w.removeAll()
					.setName(m.name + "-liveness")
					.add("liveness", new LivenessDecorator().setLiveness(liveness).applyComments(cfg))
					.addBefore("liveness", "cfg", new ControlFlowGraphDecorator().setFlags(OPT_DEEP | OPT_STMTS))
					.export();
		}
	}
	
	private static SSATransformer[] initTransforms(CodeBody code, SSALocalAccess localAccess, StatementGraph sgraph, StatementGenerator gen) {
		return new SSATransformer[] {
				new SSAPropagator(code, localAccess, sgraph, gen.getHeaders().values()),
				new SSAInitialiserAggregator(code, localAccess, sgraph)
			};
	}
}
