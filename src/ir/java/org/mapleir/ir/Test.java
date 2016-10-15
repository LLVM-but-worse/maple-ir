package org.mapleir.ir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.cfg.builder.SSAGenPass;
import org.mapleir.stdlib.klass.ClassNodeUtil;
import org.mapleir.stdlib.klass.ClassTree;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.util.CheckClassAdapter;
import org.topdank.byteengineer.commons.data.JarContents;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.out.JarDumper;

public class Test {

	void testLoop() {
		int x = 1;
		do {
			if (x > 5)
				x--;
			else
				x++;
		} while (!p());
		System.out.println(x);
	}

	void test111() {
		int x = 1;
		int y = 2;
		do {
			int z = x;
			x = y;
			y = z;
		} while (!p());

		System.out.println(x);
		System.out.println(y);
	}

	void test112() {
		int x = 1, y = 2;
		do {
			int w = x;
			x = y; // y = p() ? x : y
			if (q())
				y = w;
		} while (!p());

		System.out.println(x + y);
	}

	void test113() {
		int x = 1;
		int y = 2;

		while (!p()) {
			int z = x;
			x = y;
			y = z;
		}

		System.out.println(x);
		System.out.println(y);
	}

	void test114() {
		Object o = null;

		while (!p()) {
			o = new Object();
		}

		System.out.println(o);
	}

	void test115() {
		Object o = null;

		do {
			o = new Object();
		} while (!p());

		System.out.println(o);
	}

	void test116() {
		Object o1 = new String("x");
		Object o2 = new String("d");

		do {
			Object o3 = o2;
			o2 = o1;
			o1 = o3;
		} while (!p());

		System.out.println(o1);
		System.out.println(o2);
	}

	void test117() { // i dedicate this test case to my friend revan114
		int lmao = v();
		int x = lmao;
		int y = lmao;

		while (!p()) {
			int z = x;
			x = y;
			y = z;
		}

		System.out.println(x);
		System.out.println(y);
	}

	void test118() {
		int x = 1;
		int y = 2;

		if (q())
			y = x;
		if (p())
			x = y;

		System.out.println(x);
		System.out.println(y);
	}

	void test119() {
		try {
			System.out.println("print");
		} catch (RuntimeException e) {

		}
	}

	void test120() {
		try {
			System.out.println("print");
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}

	void test121() {
		int x;

		try {
			System.gc();
			x = 5;
		} catch (RuntimeException e) {
			x = 10;
		}

		System.out.println(x);
	}

	void trap() {
	}

	void test122() {
		int x = 5;
		int y = 10;

		try {
			trap();
			int z = x;
			trap();
			x = y;
			trap();
			y = z;
			trap();
		} catch (RuntimeException e) {
			trap();
			int z = y;
			trap();
			y = x;
			trap();
			x = z;
			trap();
		}

		System.out.println(x);
		System.out.println(y);
	}

	void test123() {
		int x = 5;
		int y = 10;

		try {
			int z = x;
			x = y;
			y = z;
		} finally {
			int z = y;
			y = x;
			x = z;
		}

		System.out.println(x);
		System.out.println(y);
	}

	void test124() {
		int x = 5;
		int y = 10;

		try {
			int z = x;
			x = y;
			trap();
			y = z;
		} finally {
			int z = y;
			y = x;
			x = z;
		}

		System.out.println(x);
		System.out.println(y);
	}

	void trap(int x, int y) {
	}

	void test125() {
		int x = 5;
		int y = 10;

		try {
			trap(x, y);
			int z = x;
			trap(x, y);
			x = y;
			trap(x, y);
			y = z;
			trap(x, y);
		} catch (RuntimeException e) {
			trap(x, y);
			int z = y;
			trap(x, y);
			y = x;
			trap(x, y);
			x = z;
			trap(x, y);
		}

		System.out.println(x);
		System.out.println(y);
	}

	void test128() {
		int x = 5;
		int y = 10;

		do {
			try {
				trap(x, y);
				y = x;
				trap(x, y);
				y = 123;
			} catch (RuntimeException e) {
				trap(x, y);
				int z = y;
				trap(x, y);
				y = x;
				trap(x, y);
				x = z;
				trap(x, y);
			}
		} while (p());

		System.out.println(x);
		System.out.println(y);
	}

	void test129() {
		int x = 5;
		int y = 10;

		do {
			try {
				trap(x, y);
				y = x;
				trap(x, y);
				y = 123;
			} catch (RuntimeException e) {
				while (!p()) {
					trap(x, y);
					int z = y;
					trap(x, y);
					y = x;
					trap(x, y);
					x = z;
					trap(x, y);
				}
			}
		} while (p());

		System.out.println(x);
		System.out.println(y);
	}

	void test130() {
		int x = 5;
		int y = 10;

		do {
			try {
				trap(x, y);
				y = x;
				trap(x, y);
				y = 123;
			} catch (RuntimeException e) {
				do {
					trap(x, y);
					int z = y;
					trap(x, y);
					y = x;
					trap(x, y);
					x = z;
					trap(x, y);
				} while (!p());
			}
		} while (p());

		System.out.println(x);
		System.out.println(y);
	}

	void test131() {
		int x = 5;
		int y = 10;

		do {
			if (q()) {
				do {
					int z = y;
					y = x;
					x = z;
				} while (!q());
			}
		} while (p());

		System.out.println(x);
		System.out.println(y);
	}

	void test141() {
		int x = 5;
		int y = 10;
		try {
			trap(x, y);
			int z = x;
			x = y;
			trap(x, y);
			y = z;
		} catch (RuntimeException e) {
			System.out.println(x + 2 * y);
		} finally {
			System.out.println(x + y);
		}
	}

	void test011() {
		int x = v();
		int y = u();

		if (p())
			System.out.println(x);
		else
			System.out.println(x + 5);
		System.out.println(y);
	}

	void lla() {
		Runnable r = () -> {
			test011();
		};
		r.run();
	}

	static void test151(int lvar0, int lvar1) {
		try {
			lvar0 = lvar0;
			lvar1 = lvar1;
			System.out.println(lvar1);
			lvar1 = lvar0;
		} catch (Exception e) {
			System.out.println(lvar1);
		}
		System.out.println(lvar1);
	}

	boolean p() {
		return true;
	}

	boolean q() {
		return true;
	}

	int u() {
		return 114;
	}

	int v() {
		return 114;
	}

	public static void main1(String[] args) throws IOException {
		ClassNode cn = new ClassNode();
		cn.access = Opcodes.ACC_PUBLIC;
		cn.name = "test/Klass";
		cn.superName = "java/lang/Object";
		MethodNode m = new MethodNode(cn, Opcodes.ACC_PUBLIC, "test", "()V", null, null);
		InsnList insns = m.instructions;
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "Klass", "method1", "()I", false));
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "Klass", "method2", "()F", false));
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "Klass", "method3", "()I", false));
		insns.add(new InsnNode(Opcodes.DUP_X2));
		insns.add(new InsnNode(Opcodes.POP));
		insns.add(new InsnNode(Opcodes.SWAP));

		ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
		System.out.println(cfg);
	}
	
//	void call1(int i, int j) {
//	}
//
//	void call2(int i, int j) {
//	}
//	
//	void test(int i, int j) {
//		call1(i, j);
//		call2(j, i);
//	}
//	
//	public static void main(String[] args) throws IOException {
//		ClassNode cn = new ClassNode();
//		ClassReader cr = new ClassReader(Test.class.getCanonicalName());
//		cr.accept(cn, 0);
//		
//		for(MethodNode m : cn.methods) {
//			if(m.name.equals("test")) {
//				System.out.println(ControlFlowGraphBuilder.build(m));
//			}
//		}
//	}

	public static void main(String[] args) throws IOException {
		JarInfo jar = new JarInfo(new File("res/allatori.jar"));
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(jar);
		dl.download();
		JarContents<ClassNode> contents = dl.getJarContents();

		System.out.println("davai");
		long start = System.nanoTime();
		
		int index = 0;
		for (ClassNode cn : contents.getClassContents()) {

			// if(cn.name.equals("com/allatori/IIiIIiIiIi")) {
			// ClassWriter cw = new ClassWriter(0);
			// cn.accept(cw);
			// byte[] bs = cw.toByteArray();
			// FileOutputStream out = new FileOutputStream(new File("out/dmp.class"));
			// out.write(bs, 0, bs.length);
			// out.close();
			// }

			ArrayList<MethodNode> methodNodes = new ArrayList<>(cn.methods);
			for (MethodNode m : methodNodes) {
//				if (!m.toString().startsWith("com/allatori/IiiIiiIIiI.IIIIIIiIII(Ljava/lang/String;)Ljava/lang/String;")) {
//					continue;
//				}
//				if (index != 546) {
//					continue;
//				}

				if (m.instructions.size() > 0) {
					index++;
					System.out.printf("#%d: %s  [%d]%n", index, m, m.instructions.size());
					if (index % 100 == 0) {
						System.out.println(index + " done.");
					}
					
					ControlFlowGraph cfg = null;
					
//					{
//						List<MethodNode> methods = new ArrayList<>(cn.methods);
//						cn.methods.clear();
//						cn.methods.add(m);
//
//						ClassWriter cw = new ClassWriter(0);
//						cn.accept(cw);
//						byte[] bs = cw.toByteArray();
//						FileOutputStream out = new FileOutputStream(new File("out/pre.class"));
//						out.write(bs, 0, bs.length);
//						out.close();
//
//						cn.methods.addAll(methods);
//					}
					
					try {
						m.localVariables.clear();
						cfg = ControlFlowGraphBuilder.build(m);
						m.access ^= Opcodes.ACC_SYNTHETIC;

//						BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
//						DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, cfg);
//						writer.removeAll().add(new ControlFlowGraphDecorator()).setName("irreducible").export();

//						System.out.println(cfg);

						BoissinotDestructor.leaveSSA(cfg);
						cfg.getLocals().realloc(cfg);
//						System.out.println(cfg);
						ControlFlowGraphDumper.dump(cfg, m);
						
//						ClassTree classTree = new ClassTree(contents.getClassContents());
//						ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
//
//							// this method in ClassWriter uses the systemclassloader as
//							// a stream location to load the super class, however, most of
//							// the time the class is loaded/read and parsed by us so it
//							// isn't defined in the system classloader. in certain cases
//							// we may not even want it to be loaded/resolved and we can
//							// bypass this by implementing the hierarchy scanning algorithm
//							// with ClassNodes rather than Classes.
//							@Override
//							protected String getCommonSuperClass(String type1, String type2) {
//								ClassNode ccn = classTree.getClass(type1);
//								ClassNode dcn = classTree.getClass(type2);
//
//								if (ccn == null) {
//									ClassNode c = ClassNodeUtil.create(type1);
//									if (c == null) {
//										return "java/lang/Object";
//									}
//									classTree.build(c);
//									return getCommonSuperClass(type1, type2);
//								}
//
//								if (dcn == null) {
//									ClassNode c = ClassNodeUtil.create(type2);
//									if (c == null) {
//										return "java/lang/Object";
//									}
//									classTree.build(c);
//									return getCommonSuperClass(type1, type2);
//								}
//
//								Set<ClassNode> c = classTree.getSupers(ccn);
//								Set<ClassNode> d = classTree.getSupers(dcn);
//
//								if (c.contains(dcn))
//									return type1;
//
//								if (d.contains(ccn))
//									return type2;
//
//								if (Modifier.isInterface(ccn.access) || Modifier.isInterface(dcn.access)) {
//									// enums as well?
//									return "java/lang/Object";
//								} else {
//									do {
//										ClassNode nccn = classTree.getClass(ccn.superName);
//										if (nccn == null)
//											break;
//										ccn = nccn;
//										c = classTree.getSupers(ccn);
//									} while (!c.contains(dcn));
//									return ccn.name;
//								}
//							}
//
//						};
//						cn.methods.clear();
//						cn.methods.add(m);
//						cn.accept(cw);
//						byte[] bs = cw.toByteArray();
//						FileOutputStream out = new FileOutputStream(new File("out/work.class"));
//						out.write(bs, 0, bs.length);
//						out.close();
//
//						return;
						
					} catch (RuntimeException e) {
						cn.methods.clear();
						cn.methods.add(m);
						ClassWriter cw = new ClassWriter(0);
						cn.accept(cw);
						byte[] bs = cw.toByteArray();
						FileOutputStream out = new FileOutputStream(new File("out/err.class"));
						out.write(bs, 0, bs.length);
						out.close();
						System.err.println();
						System.err.println(cfg);
						throw new RuntimeException(m.toString(), e);
					}
				}
				System.gc(); // ayy lmao
			}
		}
		
		System.out.println("did " + index + " methods.");
		System.out.printf("that shit took %d seconds.%n", (System.nanoTime() - start) / 1000000000);

		JarDumper dumper = new CompleteResolvingJarDumper(contents);
		dumper.dump(new File("out/osb.jar"));
	}

	public static boolean temp = false;
	
	public static void main3(String[] args) throws IOException, AnalyzerException {
		InputStream i = new FileInputStream(
				new File("res/DateTimeFormatterBuilder$LocalizedOffsetIdPrinterParser.class"));
		ClassReader cr = new ClassReader(i);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);

		Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
		while (it.hasNext()) {
			MethodNode m = it.next();
			if (!m.toString().contains(".parse(Ljava/time/format/DateTimeParseContext;Ljava/lang/CharSequence;I)I")) {
				continue;
			}

			System.out.println("Processing " + m + "\n");

			SSAGenPass.DO_SPLIT = true;
			SSAGenPass.ULTRANAIVE = false;
			SSAGenPass.SKIP_SIMPLE_COPY_SPLIT = true;
			SSAGenPass.PRUNE_EDGES = true;

			ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
			BoissinotDestructor.leaveSSA(cfg);
			m.maxLocals = cfg.getLocals().realloc(cfg);

			// System.out.println(cfg);
			// MethodNode m2 = new MethodNode(m.owner, m.access, m.name, m.desc, m.signature, m.exceptions.toArray(new String[0]));
			ControlFlowGraphDumper.dump(cfg, m);

			System.out.println(cfg);

			// System.out.println();
			// int j = 0;
			// for(String s : InstructionPrinter.getLines(m)) {
			// System.out.printf("%03d. %s.%n", j++, s);
			// }
			//
			// Analyzer<BasicValue> a = new Analyzer<>(new BasicInterpreter());
			// a.analyze(cn.name, m);

			// new ClassLoader() {
			// JarContents<ClassNode> contents;
			// {
			// AbstractJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(new File("res/rt.jar")));
			// dl.download();
			// contents = dl.getJarContents();
			// ClassWriter clazz = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
			// cn.accept(clazz);
			// byte[] b = clazz.toByteArray();
			// defineClass(b, 0, b.length);
			// }
			//
			// @Override
			// public Class<?> loadClass(String name) throws ClassNotFoundException {
			// if(name.contains("DateTimeFormatterBuilder")) {
			// ClassNode cn = contents.getClassContents().namedMap().get(name.replace(".", "/"));
			// cn.access ^= Opcodes.ACC_PRIVATE;
			// cn.access &= Opcodes.ACC_PUBLIC;
			// ClassWriter clazz = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
			// cn.accept(clazz);
			// byte[] b = clazz.toByteArray();
			// return defineClass(b, 0, b.length);
			// } else {
			// return super.loadClass(name);
			// }
			// }
			// };
		}

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cn.accept(cw);
		byte[] bs = cw.toByteArray();

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		CheckClassAdapter.verify(new ClassReader(bs), false, pw);

		FileOutputStream out = new FileOutputStream(new File("out/testclass.class"));
		out.write(bs, 0, bs.length);
		out.close();
	}
}