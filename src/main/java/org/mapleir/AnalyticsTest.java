package org.mapleir;

import static org.mapleir.stdlib.collections.graph.dot.impl.ControlFlowGraphDecorator.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarOutputStream;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.ControlFlowGraphBuilder;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.mapleir.stdlib.collections.NodeTable;
import org.mapleir.stdlib.collections.graph.dot.BasicDotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration.GraphType;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.collections.graph.dot.impl.ControlFlowGraphDecorator;
import org.mapleir.stdlib.collections.graph.dot.impl.LivenessDecorator;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.StatementGraph;
import org.mapleir.stdlib.ir.StatementWriter;
import org.mapleir.stdlib.ir.gen.SSADestructor;
import org.mapleir.stdlib.ir.gen.SSAGenerator;
import org.mapleir.stdlib.ir.gen.SreedharDestructor;
import org.mapleir.stdlib.ir.gen.StatementGenerator;
import org.mapleir.stdlib.ir.gen.StatementGraphBuilder;
import org.mapleir.stdlib.ir.transform.SSATransformer;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.mapleir.stdlib.ir.transform.impl.DefinitionAnalyser;
import org.mapleir.stdlib.ir.transform.impl.LivenessAnalyser;
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.mapleir.stdlib.ir.transform.ssa.SSAInitialiserAggregator;
import org.mapleir.stdlib.ir.transform.ssa.SSALocalAccess;
import org.mapleir.stdlib.ir.transform.ssa.SSAPropagator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

public class AnalyticsTest {

	public static boolean debug = true;
	
	void test111() {
		int x = 1;
		int y = 2;
		while(!p()) {
			int z = x;
			x = y;
			y = z;
		}
		
		System.out.println(x);
		System.out.println(y);
	}
	
	boolean p() {
		return true;
	}
	
//	for(BasicBlock b : cfg.vertices()) {
//		Set<Local> in1 = l1.in(b);
//		Set<Local> in2 = l2.in(b);
//		
//		Set<Local> out1 = l1.out(b);
//		Set<Local> out2 = l2.out(b);
//		
//		if(!in1.equals(in2)) {
//			System.err.println("IN1: " + in1);
//			System.err.println("IN2: " + in2);
//			throw new RuntimeException(b.getId());
//		}
//		if(!out1.equals(out2)) {
//			System.err.println("OUT1: " + out1);
//			System.err.println("OUT2: " + out2);
//			throw new RuntimeException(b.getId());
//		}
//	}
	
	public static void main(String[] args) throws IOException {
		ClassReader cr = new ClassReader(AnalyticsTest.class.getCanonicalName());
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		
		Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();

			if(!m.toString().equals("org/mapleir/AnalyticsTest.test111()V")) {
				continue;
			}
			System.out.println("Processing " + m + "\n");
			ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(m);
			ControlFlowGraph cfg = builder.build();
			ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
			List<BasicBlock> blocks = deobber.deobfuscate(cfg);
			deobber.removeEmptyBlocks(cfg, blocks);
			GraphUtils.naturaliseGraph(cfg, blocks);
						
			StatementGenerator gen = new StatementGenerator(cfg);
			gen.init(m.maxLocals);
			gen.createExpressions();
			CodeBody code = gen.buildRoot();
			SSAGenerator ssagen = new SSAGenerator(code, cfg, gen.getHeaders());
			ssagen.run();
			
			System.out.println("Unopt SSA:");
			System.out.println(code);
			System.out.println();
			System.out.println();
			System.out.println();
			
			StatementGraph sgraph = StatementGraphBuilder.create(cfg);
			SSALocalAccess localAccess = new SSALocalAccess(code);
			
			SSATransformer[] transforms = initTransforms(code, localAccess, sgraph, gen);
			
			while(true) {
				int change = 0;
				for(SSATransformer t : transforms) {
					change += t.run();
				}
				if(change <= 0) {
					break;
				}
			}
			
			GraphUtils.rewriteCfg(cfg, code);
			
			System.out.println("SSA:");
			System.out.println(code);
			System.out.println();
			System.out.println();
			
//			CFGStatementDecorator ex = new CFGStatementDecorator(cfg, new ArrayList<>(cfg.vertices()), "graph", "");
//			ex.export(DotExporter.OPT_DEEP);
//			SSALivenessAnalyser liveness = new SSALivenessAnalyser(cfg);
//			InterferenceGraph ig = InterferenceGraphBuilder.build(liveness);
//			System.out.println(ig);
//			
//			BasicDotConfiguration<InterferenceGraph, ColourableNode, InterferenceEdge> config2 = new BasicDotConfiguration<>(GraphType.UNDIRECTED);
//			DotWriter<InterferenceGraph, ColourableNode, InterferenceEdge> w2 = new DotWriter<>(config2, ig);
//			w2.add(new InterferenceGraphDecorator()).setName("ig1").export();
			
			BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(GraphType.DIRECTED);
			DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> w = new DotWriter<>(config, cfg);
			w.add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.setName("graph111")
				.export();

			SSABlockLivenessAnalyser blockLiveness = new SSABlockLivenessAnalyser(cfg);
			GraphUtils.rewriteCfg(cfg, code);
			blockLiveness.compute();
			
			w.removeAll()
				.setName("graph-liveness")
				.add("cfg", new ControlFlowGraphDecorator().setFlags(OPT_DEEP | OPT_STMTS | OPT_SIMPLE_EDGES))
				.addAfter("cfg", "liveness", new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(blockLiveness))
				.export();

			
//			for(BasicBlock b : cfg.vertices()) {
//				StringBuilder sb = new StringBuilder();
//				GraphUtils.printBlock(cfg, cfg.vertices(), sb, b, 0, true);
//				System.out.print(sb);
//				System.out.println("IN:");
//				for(Entry<Local, Boolean> e : liveness.in(b).entrySet()) {
//					System.out.println("  " + e.getKey() + " is " + (e.getValue() ? "live" : "dead"));
//				}
//				System.out.println("OUT:");
//				for(Entry<Local, Boolean> e : liveness.out(b).entrySet()) {
//					System.out.println("  " + e.getKey() + " is " + (e.getValue() ? "live" : "dead"));
//				}
//				System.out.println();
//			}
			
			SreedharDestructor dest = new SreedharDestructor(code, cfg);
			
			System.out.println("Norm SSA:");
			System.out.println(code);
			System.out.println();
			System.out.println();
		}
	}
	
	public static void main2(String[] args) throws IOException {
		int rev = 107;
		if(args.length > 0) {
			rev = Integer.parseInt(args[0]);
		}
		
		NodeTable<ClassNode> nt = new NodeTable<>();
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(new File(String.format("res/gamepack%s.jar", rev))));
		dl.download();
		nt.putAll(dl.getJarContents().getClassContents().namedMap());
		
		for(ClassNode cn : nt) {
			for(MethodNode m : new HashSet<>(cn.methods)) {
				int msize = m.instructions.size();
				if(msize == 0 || msize > 5000) {
					continue;
				}
				
				if(!m.toString().equals("aa.ac(II[[IIII)Lck;"))
					continue;
				System.out.println(m);
				
				ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(m);
				ControlFlowGraph cfg = builder.build();
				ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
				List<BasicBlock> blocks = deobber.deobfuscate(cfg);
				deobber.removeEmptyBlocks(cfg, blocks);
				GraphUtils.naturaliseGraph(cfg, blocks);
							
				StatementGenerator gen = new StatementGenerator(cfg);
				gen.init(m.maxLocals);
				gen.createExpressions();
				CodeBody code = gen.buildRoot();

//				System.out.println("Unoptimised Code:");
//				System.out.println(code);
//				System.out.println();
//				System.out.println();

				SSAGenerator ssagen = new SSAGenerator(code, cfg, gen.getHeaders());
				ssagen.run();
				
//				System.out.println("SSA:");
//				System.out.println(code);
//				System.out.println();
//				System.out.println();

				StatementGraph sgraph = StatementGraphBuilder.create(cfg);
				SSALocalAccess localAccess = new SSALocalAccess(code);
				
				SSATransformer[] transforms = initTransforms(code, localAccess, sgraph, gen);
				
				while(true) {
					int change = 0;
					for(SSATransformer t : transforms) {
						change += t.run();
					}
					if(change <= 0) {
						break;
					}
				}
				
//				System.out.println();
//				System.out.println();
//				System.out.println("Optimised SSA:");
//				System.out.println(code);
				

				SreedharDestructor dest = new SreedharDestructor(code, cfg);

				System.out.println();
				System.out.println();
				System.out.println();
				System.out.println();
				System.out.println(code);
				System.out.println();
				System.out.println();
				System.out.println();
				System.out.println();
				SSADestructor de = new SSADestructor(code, cfg);
				de.run();

				System.out.println();
				System.out.println();
				System.out.println(code);
				System.out.println();
				System.out.println();
				System.out.println();

//				System.out.println();
//				System.out.println();
//				System.out.println("Optimised Code:");
//				System.out.println(code);
				
				sgraph = StatementGraphBuilder.create(cfg);
				LivenessAnalyser liveness = new LivenessAnalyser(sgraph);
				DefinitionAnalyser definitions = new DefinitionAnalyser(sgraph);
				CodeAnalytics analytics = new CodeAnalytics(code, cfg, sgraph, liveness, definitions);
				StatementWriter writer = new StatementWriter(code, cfg);
				MethodNode m2 = new MethodNode(m.owner, m.access, m.name, m.desc, m.signature, m.exceptions.toArray(new String[0]));
				writer.dump(m2, analytics);
				cn.methods.add(m2);
				cn.methods.remove(m);
			}
		}
		
		CompleteResolvingJarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents()) {
			@Override
			public int dumpResource(JarOutputStream out, String name, byte[] file) throws IOException {
				if(name.startsWith("META-INF")) {
					return 0;
				} else {
					return super.dumpResource(out, name, file);
				}
			}
		};
		File outFile = new File(String.format("out/%d/%d.jar", rev, rev));
		outFile.mkdirs();
		dumper.dump(outFile);
	}
	
	public static void main1(String[] args) throws Throwable {
		InputStream i = new FileInputStream(new File("res/a.class"));
		ClassReader cr = new ClassReader(i);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		//
		Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();

//			e/uc.<init>()V 5
//			e/uc.a(Ljava/util/Hashtable;Ljava/security/MessageDigest;)V 111
//			e/uc.<clinit>()V 6
			
//			a.f(I)Z 194
//			a.u(I)V 149
//			a.<clinit>()V 7
//			a.bm(Le;I)V 238
//			a.<init>()V 16
//			a.di(Lb;ZI)V 268
//			a.n(Ljava/lang/String;I)I 18
			System.out.println(m + " " + m.instructions.size());
			if(!m.toString().equals("a/a/f/a.<init>()V")) {
//				continue;
			}
//			LocalsTest.main([Ljava/lang/String;)V
//			org/rsdeob/AnalyticsTest.tryidiots(I)V
//			a/a/f/a.<init>()V
//			a/a/f/a.H(J)La/a/f/o;
//			a/a/f/a.H(La/a/f/o;J)V
			System.out.println("Processing " + m + "\n");
			ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(m);
			ControlFlowGraph cfg = builder.build();
			ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
			List<BasicBlock> blocks = deobber.deobfuscate(cfg);
			deobber.removeEmptyBlocks(cfg, blocks);
			GraphUtils.naturaliseGraph(cfg, blocks);
						
			StatementGenerator gen = new StatementGenerator(cfg);
			gen.init(m.maxLocals);
			gen.createExpressions();
			CodeBody code = gen.buildRoot();

			System.out.println("Unoptimised Code:");
			System.out.println(code);
			System.out.println();
			System.out.println();

			SSAGenerator ssagen = new SSAGenerator(code, cfg, gen.getHeaders());
			ssagen.run();
			
			System.out.println("SSA:");
			System.out.println(code);
			System.out.println();
			System.out.println();

			StatementGraph sgraph = StatementGraphBuilder.create(cfg);
			SSALocalAccess localAccess = new SSALocalAccess(code);
			
			SSATransformer[] transforms = initTransforms(code, localAccess, sgraph, gen);
			
			while(true) {
				int change = 0;
				for(SSATransformer t : transforms) {
					change += t.run();
				}
				if(change <= 0) {
					break;
				}
			}
			
			System.out.println();
			System.out.println();
			System.out.println("Optimised SSA:");
			System.out.println(code);
			
			SSADestructor de = new SSADestructor(code, cfg);
			de.run();

			System.out.println();
			System.out.println();
			System.out.println("Optimised Code:");
			System.out.println(code);
			
			sgraph = StatementGraphBuilder.create(cfg);
			LivenessAnalyser liveness = new LivenessAnalyser(sgraph);
			DefinitionAnalyser definitions = new DefinitionAnalyser(sgraph);
			CodeAnalytics analytics = new CodeAnalytics(code, cfg, sgraph, liveness, definitions);
			StatementWriter writer = new StatementWriter(code, cfg);
			MethodNode m2 = new MethodNode(m.owner, m.access, m.name, m.desc, m.signature, m.exceptions.toArray(new String[0]));
			writer.dump(m2, analytics);
			it.remove();
			cn.methods.add(m2);
			cn.methods.remove(m);
		}

		ClassWriter clazz = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cn.accept(clazz);
		byte[] saved = clazz.toByteArray();
		FileOutputStream out = new FileOutputStream(new File("out/testclass.class"));
		out.write(saved, 0, saved.length);
		out.close();
	}
	
	private static SSATransformer[] initTransforms(CodeBody code, SSALocalAccess localAccess, StatementGraph sgraph, StatementGenerator gen) {
		return new SSATransformer[] {
				new SSAPropagator(code, localAccess, sgraph, gen.getHeaders().values()),
				new SSAInitialiserAggregator(code, localAccess, sgraph)
			};
	}

	public void tryidiots(int x) {
		int y = 0;
		try {
			if(x == 5) {
				y = 2;
			} else {
				y = 3;
			}
		} catch(Exception e) {
			System.out.println(e.getMessage() + " " + y);
		}
	}
}