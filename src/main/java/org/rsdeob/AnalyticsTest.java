package org.rsdeob;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.collections.graph.util.DotExporter;
import org.rsdeob.stdlib.collections.graph.util.GraphUtils;
import org.rsdeob.stdlib.collections.graph.util.SGDotExporter;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.gen.SSAGenerator;
import org.rsdeob.stdlib.ir.gen.StatementGenerator;
import org.rsdeob.stdlib.ir.gen.StatementGraphBuilder;
import org.rsdeob.stdlib.ir.gen.UnSSA;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.SSATransformer;
import org.rsdeob.stdlib.ir.transform.Transformer;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;
import org.rsdeob.stdlib.ir.transform.impl.DefinitionAnalyser;
import org.rsdeob.stdlib.ir.transform.impl.LivenessAnalyser;
import org.rsdeob.stdlib.ir.transform.ssa.SSAInitialiserAggregator;
import org.rsdeob.stdlib.ir.transform.ssa.SSALocalAccess;
import org.rsdeob.stdlib.ir.transform.ssa.SSAPropagator;

public class AnalyticsTest {

	public static boolean debug = true;
	
	public static void main(String[] args) throws Throwable {
		InputStream i = new FileInputStream(new File("res/a2.class"));
		ClassReader cr = new ClassReader(i);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		//
		Iterator<MethodNode> it = cn.methods.listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();

//			a.f(I)Z 194
//			a.u(I)V 149
//			a.<clinit>()V 7
//			a.bm(Le;I)V 238
//			a.<init>()V 16
//			a.di(Lb;ZI)V 268
//			a.n(Ljava/lang/String;I)I 18
			if(!m.toString().equals("a.<init>()V")) {
				continue;
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
			
			// GraphUtils.output(cfg, blocks, new File("C:/Users/Bibl/Desktop/cfg testing"), "test11");
			
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
			
//			System.out.println("SSA:");
//			System.out.println(code);
//			System.out.println();
//			System.out.println();

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
			
//			System.out.println();
//			System.out.println();
//			System.out.println("Optimised SSA:");
//			System.out.println(code);
			
			UnSSA de = new UnSSA(code, cfg);
			de.run();

			System.out.println();
			System.out.println();
			System.out.println("Optimised Code:");
			System.out.println(code);
		}
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