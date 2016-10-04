package org.mapleir.ir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.cfg.builder.SSAGenPass;
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
		} while(p());

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
				while(!p()) {
					trap(x, y);
					int z = y;
					trap(x, y);
					y = x;
					trap(x, y);
					x = z;
					trap(x, y);
				}
			}
		} while(p());

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
				} while(!p());
			}
		} while(p());

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
		} while(p());

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
		} catch(RuntimeException e) {
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
		Runnable r = () -> {test011();};
		r.run();
	}
	
	static void test151(int lvar0, int lvar1) {
		try {
			lvar0 = lvar0;
			lvar1 = lvar1;
			System.out.println(lvar1);
			lvar1 = lvar0;
		} catch(Exception e) {
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
	
	public static void main(String[] args) throws IOException {
		JarInfo jar = new JarInfo(new File("res/allatori.jar"));
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(jar);
		dl.download();
		JarContents<ClassNode> contents = dl.getJarContents();
		
		for (ClassNode cn : contents.getClassContents()) {
			for(MethodNode m : cn.methods) {
				
				if(!m.toString().startsWith("com/allatori/IiIIIiiIii.IIIIIIiIII(Ljava/io/File;Ljava/io/File;)V")) {
					continue;
				}
				
				if(m.instructions.size() > 0) {
					System.out.printf("%s  %d.%n", m, m.instructions.size());
					ControlFlowGraph cfg = null;
					{
						ClassWriter cw = new ClassWriter(0);
						cn.accept(cw);
						byte[] bs = cw.toByteArray();
						FileOutputStream out = new FileOutputStream(new File("out/pre.class"));
						out.write(bs, 0, bs.length);
						out.close();
					}
					try {
						m.localVariables.clear();
						cfg = ControlFlowGraphBuilder.build(m);
						m.access ^= Opcodes.ACC_SYNTHETIC;
						new BoissinotDestructor(cfg, 0); // ungay this
						cfg.getLocals().realloc(cfg);
						System.out.println(cfg);
						ControlFlowGraphDumper.dump(cfg, m);
						
						ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
						cn.accept(cw);
						byte[] bs = cw.toByteArray();
						FileOutputStream out = new FileOutputStream(new File("out/work.class"));
						out.write(bs, 0, bs.length);
						out.close();
						
//						System.out.println(cfg);
						
//						cfg = ControlFlowGraphBuilder.build(m);
					} catch(RuntimeException e) {
						ClassWriter cw = new ClassWriter(0);
						cn.accept(cw);
						byte[] bs = cw.toByteArray();
						FileOutputStream out = new FileOutputStream(new File("out/err.class"));
						out.write(bs, 0, bs.length);
						out.close();
//						System.err.println();
//						System.err.println(cfg);
						throw new RuntimeException(m.toString(), e);
					}
				}
			}
		}
		
		JarDumper dumper = new CompleteResolvingJarDumper(contents);
		dumper.dump(new File("out/osb.jar"));
	}
	
	public static void main3(String[] args) throws IOException, AnalyzerException {
		InputStream i = new FileInputStream(new File("res/DateTimeFormatterBuilder$LocalizedOffsetIdPrinterParser.class"));
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
			new BoissinotDestructor(cfg, 0);
			m.maxLocals = cfg.getLocals().realloc(cfg);
			
//			System.out.println(cfg);
//			MethodNode m2 = new MethodNode(m.owner, m.access, m.name, m.desc, m.signature, m.exceptions.toArray(new String[0]));
			ControlFlowGraphDumper.dump(cfg, m);
			
			System.out.println(cfg);
			
//			System.out.println();
//			int j = 0;
//			for(String s : InstructionPrinter.getLines(m)) {
//				System.out.printf("%03d. %s.%n", j++, s);
//			}
//
//			Analyzer<BasicValue> a = new Analyzer<>(new BasicInterpreter());
//			a.analyze(cn.name, m);
			
//			new ClassLoader() {
//				JarContents<ClassNode> contents;
//				{
//					AbstractJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(new File("res/rt.jar")));
//					dl.download();
//					contents = dl.getJarContents();
//					ClassWriter clazz = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
//					cn.accept(clazz);
//					byte[] b = clazz.toByteArray();
//					defineClass(b, 0, b.length);
//				}
//				
//				@Override
//				public Class<?> loadClass(String name) throws ClassNotFoundException {
//					if(name.contains("DateTimeFormatterBuilder")) {
//						ClassNode cn = contents.getClassContents().namedMap().get(name.replace(".", "/"));
//						cn.access ^= Opcodes.ACC_PRIVATE;
//						cn.access &= Opcodes.ACC_PUBLIC;
//						ClassWriter clazz = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
//						cn.accept(clazz);
//						byte[] b = clazz.toByteArray();
//						return defineClass(b, 0, b.length);
//					} else {
//						return super.loadClass(name);
//					}
//				}
//			};
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