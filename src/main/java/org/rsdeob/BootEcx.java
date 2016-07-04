package org.rsdeob;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.byteio.CompleteResolvingJarDumper;
import org.rsdeob.deobimpl.ControlFlowFixerPhase;
import org.rsdeob.deobimpl.DummyMethodPhase;
import org.rsdeob.deobimpl.RTECatchBlockRemoverPhase;
import org.rsdeob.deobimpl.UnusedFieldsPhase;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.collections.NodeTable;
import org.rsdeob.stdlib.deob.IPhase;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementGenerator;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.ir.export.StatementsDumper;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;
import org.rsdeob.stdlib.ir.transform.impl.DefinitionAnalyser;
import org.rsdeob.stdlib.ir.transform.impl.LivenessAnalyser;
import org.rsdeob.stdlib.ir.transform.impl.UsesAnalyser;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarOutputStream;

@SuppressWarnings("Duplicates")
public class BootEcx implements Opcodes {
	public static final File GRAPH_FOLDER = new File("cfg testing");

	public static void main(String[] args) throws Exception {
		InputStream i = new FileInputStream(new File("res/a.class"));
		ClassReader cr = new ClassReader(i);
		ClassNode cn = new ClassNode();
		cr.accept(new ClassVisitor(Opcodes.ASM5, cn) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
				return new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
			}
		}, 0);

		Iterator<MethodNode> it = cn.methods.listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();

			if(!m.toString().equals("a/a/f/a.<init>()V")) {
				continue;
			}

			System.out.println("\n\n\nProcessing " + m + ": ");

			ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(m);
			ControlFlowGraph cfg = builder.build();

			ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
			List<BasicBlock> blocks = deobber.deobfuscate(cfg);
			deobber.removeEmptyBlocks(cfg, blocks);
			GraphUtils.naturaliseGraph(cfg, blocks);

			System.out.println("Cfg:");
			System.out.println(cfg);
			System.out.println();
			GraphUtils.output(cfg, blocks, GRAPH_FOLDER, "-cfg");

			System.out.println("Execution log of " + m + ":");
			StatementGenerator gen = new StatementGenerator(cfg);
			gen.init(m.maxLocals);
			gen.createExpressions();
			CodeBody stmtList = gen.buildRoot();

			System.out.println("IR representation of " + m + ":");
			System.out.println(stmtList);
			System.out.println();

			StatementGraph sgraph = StatementGraphBuilder.create(cfg);
			GraphUtils.output(m.name, sgraph, stmtList, GRAPH_FOLDER, "-sg");

//			LivenessTest.optimise(cfg, stmtList, sgraph);
//
//			System.out.println("Optimised IR " + m + ":");
//			System.out.println(stmtList);
//			System.out.println();
//
			DefinitionAnalyser defs = new DefinitionAnalyser(sgraph);
			LivenessAnalyser liveness = new LivenessAnalyser(sgraph);
			UsesAnalyser uses = new UsesAnalyser(sgraph, defs);
			CodeAnalytics analytics = new CodeAnalytics(cfg, sgraph, defs, liveness, uses);
			StatementsDumper dumper = new StatementsDumper(stmtList, cfg);
			dumper.dump(m, analytics);

			System.out.println("End of processing log for " + m);
			System.out.println("============================================================");
			System.out.println("============================================================\n\n");
		}

		ClassWriter clazz = new ClassWriter(0);
		cn.accept(clazz);
		byte[] saved = clazz.toByteArray();
		FileOutputStream out = new FileOutputStream(new File("out/testclass.class"));
		out.write(saved, 0, saved.length);
		out.close();
	}

	public void t3(Object o) {
		synchronized (o == null ? this : o) {
			System.out.println(o);
			System.out.println(this);
			t2(5, 7, 0);
		}
	}

	public void t2(int i, int j, int k) {
		t1(5);
		if(i > 0) {
			t2(i - 1, j, k);
		}
		t1(16);
	}

	public void t1(int i) {
		System.out.println(i);
		System.out.println(i + " hi " + i);
	}

	public void test4() {
		BootBibl newBoot = new BootBibl();
		newBoot.DVAL += new C2().FVAL;
		new C2().FVAL = (float) newBoot.DVAL;

		System.out.println(newBoot);
	}

	public void test6() {
		for(int i=0; i < 3; i++) {
			test5();
		}
	}

	public void test5() {
		BootBibl boot = new BootBibl();
		boot.DVAL = 5;
	}

	static class C2 extends BootBibl {
	}

	double DVAL = 5D;
	float FVAL = 10F;
	String SVAL = "";

	public void test3() {
		double d = DVAL * 847545D;
		float f = FVAL * 8573845743F;
		double c = 0;
		if(d > f) {
			c = (d + f);
		} else {
			c = (d - f);
		}

		System.out.println(c);
	}

	public void test2(int i, int j, int k) {
		int s = 283472 * i;
		int d = j * 4334857;
		int f = 345345 * (34784 * k);

		System.out.println(s);
		System.out.println(d);
		System.out.println(f);
	}

	public void test(int i) {
		System.out.println("k " + i);
	}

	public float ternTest2(int i) {
		return normalReturnTest(i == 8 ? 5 : 10);
	}

	public float normalReturnTest(int i) {
		if(i == 1) {
			return 5F;
		} else {
			return 10F;
		}
	}

	public float ternTest(int i) {
		return i == 1 ? normalReturnTest(i) : normalReturnTest(i);
	}

	public static void main111(String[] args) throws Exception {
//		Thread.sleep(15000);
		System.out.println("starting");
		IPhase[] phases = loadPhases();
		if(phases.length <= 0) {
			System.err.println("No passes to complete.");
			return;
		}

		int rev = 107;
		if(args.length > 0) {
			rev = Integer.parseInt(args[0]);
		}

		NodeTable<ClassNode> nt = new NodeTable<>();
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(new File(String.format("res/gamepack%s.jar", rev))));
		dl.download();
		nt.putAll(dl.getJarContents().getClassContents().namedMap());
		IContext cxt = new IContext() {
			@Override
			public NodeTable<ClassNode> getNodes() {
				return nt;
			}

			@Override
			public ControlFlowGraph createControlFlowGraph(MethodNode m) {
				return ControlFlowGraphBuilder.create(m);
			}
		};

		List<IPhase> completed = new ArrayList<>();
		IPhase prev = null;
		for(IPhase p : phases) {
			System.out.println("Running " + p.getId());
			try {
				p.accept(cxt, prev, Collections.unmodifiableList(completed));
				prev = p;
				completed.add(p);
				System.out.println("Completed " + p.getId());
			} catch(RuntimeException e) {
				System.err.println("Error: " + p.getId());
				System.err.flush();
				e.printStackTrace(System.err);
				prev = null;
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


//		URLClassLoader cl = new URLClassLoader(new URL[]{outFile.toURI().toURL()});
//		Class<?> c = cl.loadClass("aa");
//		Constructor<?> cs = c.getDeclaredConstructors()[0];
//		cs.setAccessible(true);
//		Object o = cs.newInstance();
//		Method m = c.getDeclaredMethod("h", new Class<?>[]{int.class, int.class, byte.class});
//		m.setAccessible(true);
//		m.invoke(null, new Object[]{1, 1, 1});
//		System.out.println(o);
		Runtime.getRuntime().exec(new String[]{"java", "-jar", "F:/bcv.jar", outFile.getAbsolutePath()});
	}

	private static IPhase[] loadPhases() {
		return new IPhase[] { new DummyMethodPhase(), new UnusedFieldsPhase(), new RTECatchBlockRemoverPhase(),  /* new OpaquePredicateRemoverPhase(),*/ new ControlFlowFixerPhase() /*new ConstantComparisonReordererPhase(),new EmptyParameterFixerPhase(), new ConstantOperationReordererPhase()*/ };
	}

	public void cfg(int x, int y) {
		int s = 0;
		int d = 0;
		while(x < y) {
			x+=3;
			y+=2;
			if(x + y < 100) {
				s+= (x + y);
			} else {
				d+= (x + y);
			}
		}
	}

	public static void main2(String[] args) throws Exception {
		ClassReader cr= new ClassReader(BootBibl.class.getCanonicalName());
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);

		for(MethodNode m : cn.methods) {
			ControlFlowGraph cfg = ControlFlowGraphBuilder.create(m);
			ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
			List<BasicBlock> blocks = deobber.deobfuscate(cfg);
			System.out.println(GraphUtils.toBlockArray(blocks));
			deobber.removeEmptyBlocks(cfg, blocks);

			System.out.println("CFG:");
			System.out.println(cfg);

			StatementGenerator gen = new StatementGenerator(cfg);
			gen.init(m.maxLocals);
			gen.createExpressions();
			CodeBody stmtList = gen.buildRoot();

			System.out.println("IR representation of " + m + ":");
			System.out.println(stmtList);
			System.out.println();

			StatementGraph sgraph = StatementGraphBuilder.create(cfg);
			System.out.println("SG: ");
			System.out.println(GraphUtils.toString(sgraph, stmtList));

			GraphUtils.output(m.name, sgraph, stmtList, GRAPH_FOLDER, "1");

			break;
		}
	}
}