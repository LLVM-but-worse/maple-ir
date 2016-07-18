package org.rsdeob;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.collections.graph.util.GraphUtils;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementWriter;
import org.rsdeob.stdlib.ir.gen.SSAGenerator;
import org.rsdeob.stdlib.ir.gen.StatementGenerator;
import org.rsdeob.stdlib.ir.gen.StatementGraphBuilder;
import org.rsdeob.stdlib.ir.gen.SSADeconstructor;
import org.rsdeob.stdlib.ir.transform.SSATransformer;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;
import org.rsdeob.stdlib.ir.transform.impl.DefinitionAnalyser;
import org.rsdeob.stdlib.ir.transform.impl.LivenessAnalyser;
import org.rsdeob.stdlib.ir.transform.ssa.SSAInitialiserAggregator;
import org.rsdeob.stdlib.ir.transform.ssa.SSALocalAccess;
import org.rsdeob.stdlib.ir.transform.ssa.SSAPropagator;

public class AnalyticsTest {

	public static boolean debug = true;
	
	public static void main(String[] args) throws Throwable {
		InputStream i = new FileInputStream(new File("res/a.class"));
		ClassReader cr = new ClassReader(AnalyticsTest.class.getCanonicalName());
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
			if(!m.toString().equals("e/uc.a(Ljava/lang/String;)Ljava/lang/String;")) {
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
			
			System.out.println();
			System.out.println();
			System.out.println("Optimised SSA:");
			System.out.println(code);
			
			SSADeconstructor de = new SSADeconstructor(code, cfg);
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

		ClassWriter clazz = new ClassWriter(0);
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