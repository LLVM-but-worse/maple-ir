package org.mapleir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.ControlFlowGraphBuilder;
import org.mapleir.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.StatementGraph;
import org.mapleir.stdlib.ir.StatementWriter;
import org.mapleir.stdlib.ir.gen.SSADestructor;
import org.mapleir.stdlib.ir.gen.SSAGenerator;
import org.mapleir.stdlib.ir.gen.StatementGenerator;
import org.mapleir.stdlib.ir.gen.StatementGraphBuilder;
import org.mapleir.stdlib.ir.transform.SSATransformer;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.mapleir.stdlib.ir.transform.impl.DefinitionAnalyser;
import org.mapleir.stdlib.ir.transform.impl.LivenessAnalyser;
import org.mapleir.stdlib.ir.transform.ssa.SSAInitialiserAggregator;
import org.mapleir.stdlib.ir.transform.ssa.SSALocalAccess;
import org.mapleir.stdlib.ir.transform.ssa.SSAPropagator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class AnalyticsTest {

	public static boolean debug = true;
	
	public static void main(String[] args) throws Throwable {
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