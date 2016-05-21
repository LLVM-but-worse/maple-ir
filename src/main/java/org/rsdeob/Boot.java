package org.rsdeob;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
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
import org.rsdeob.stdlib.cfg.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.cfg.RootStatement;
import org.rsdeob.stdlib.cfg.StatementGenerator;
import org.rsdeob.stdlib.cfg.VarVersionsMap;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.statopt.ConstantPropagator;
import org.rsdeob.stdlib.cfg.statopt.ConstantPropagator.Variable;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.collections.NodeTable;
import org.rsdeob.stdlib.deob.IPhase;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

public class Boot implements Opcodes {
	public static final File GRAPH_FOLDER = new File("C://Users//Bibl//Desktop//cfg testing");

	public static void main(String[] args) throws Exception {
		InputStream i = new FileInputStream(new File("res/DupTestEasy.class"));
		ClassReader cr = new ClassReader(i);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
				
		Iterator<MethodNode> it = cn.methods.listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();

			if(!m.toString().equals("DupTestEasy.main([Ljava/lang/String;)V")) {
				continue;
			}
			
			System.out.println("\n\n\nProcessing " + m + ": ");

			// System.out.println("Instruction listing for " + m + ": ");
			// InstructionPrinter.consolePrint(m);

			ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(m);
			ControlFlowGraph cfg = builder.build();

			ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
			List<BasicBlock> blocks = deobber.deobfuscate(cfg);
			deobber.removeEmptyBlocks(cfg, blocks);
			GraphUtils.naturaliseGraph(cfg, blocks);

			System.out.println("Execution log of " + m + ":");
			StatementGenerator gen = new StatementGenerator(cfg);
			gen.init(m.maxLocals);
			gen.createExpressions();
			RootStatement root = gen.buildRoot();

			System.out.println("IR representation of " + m + ":");
			System.out.println(root);
			System.out.println();
			
			VarVersionsMap map = root.getVariables();
			map.build();
			System.out.println(map);
			
			ConstantPropagator prop = new ConstantPropagator(cfg);
			prop.compute();
			
			
			for(BasicBlock b : cfg.blocks()) {			
				for(Statement stmt : b.getStatements()) {
					Set<Variable> in = prop.getIn(stmt);
					Set<Variable> out = prop.getOut(stmt);
					
					if(in.size() > 0 || out.size() > 0) {
						System.out.println("  in:");
						for(Variable var : in) {
							System.out.println("    " + var);
						}
						System.out.println(stmt);
						System.out.println("  out:");
						for(Variable var : out) {
							System.out.println("    " + var);
						}	
					}
					
					System.out.println("\n\n\n");
				}
			}
			/*
			ControlFlowGraph cfg = ControlFlowGraphBuilder.create(m);
			GraphUtils.output(cfg, new ArrayList<>(cfg.blocks()), GRAPH_FOLDER, "");
			System.out.println(cfg.getRoot());
			System.out.println(cfg);
			RootStatement root = cfg.getRoot();
			System.out.println(root);
			StatementVisitor vis = new StatementVisitor(root) {
				@Override
				public void visit(Statement stmt) {
					if(stmt instanceof ConstantExpression) {
						Object obj = ((ConstantExpression) stmt).getConstant();
						if(obj instanceof String) {
							if(obj.toString().equals("=============")) {
								((ConstantExpression) stmt).setConstant("hiiiiiiiiiiiii");
							}
						}
					} else if(stmt instanceof ConditionalJumpStatement) {
						System.out.println(stmt);
					}
				}
			};
			vis.visit();
			root.dump(m);
			System.out.println(cfg);
			StatementGenerator gen = new StatementGenerator(cfg);
			gen.init(m.maxLocals);
			gen.createExpressions();
			RootStatement root = gen.buildRoot();
			root.getVariables().build();

			System.out.println(root);
			System.out.println(root.getVariables());

			for(BasicBlock b : cfg.blocks()) {
				System.out.println();
				System.out.println(b);
				System.out.println(b.getState());
				for(Statement stmt : b.getStatements()) {
					if(stmt instanceof IStackDumpNode) {
						if(((IStackDumpNode) stmt).isRedundant()) {
							continue;
						}
					} else if (stmt instanceof StackLoadExpression) {
						if(((StackLoadExpression) stmt).isStackVariable()) {
							System.out.println("   st: [STACKVAR]" + stmt);
							continue;
						}
					}
					System.out.println("   st: " + stmt);
				}
			}
			*/

			System.out.println("End of processing log for " + m);
			System.out.println("============================================================");
			System.out.println("============================================================\n\n");
			break;
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
		Boot newBoot = new Boot();
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
		Boot boot = new Boot();
		boot.DVAL = 5;
	}
	
	static class C2 extends Boot {
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
		
		NodeTable<ClassNode> nt = new NodeTable<ClassNode>();
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<ClassNode>(new JarInfo(new File(String.format("res/gamepack%s.jar", rev))));
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
		
		List<IPhase> completed = new ArrayList<IPhase>();
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
		ClassReader cr= new ClassReader(Boot.class.getCanonicalName());
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		
		for(MethodNode m : cn.methods) {
			if(m.name.equals("cfg")) {
				ControlFlowGraph cfg = ControlFlowGraphBuilder.create(m);
				System.out.println(cfg);

				ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
				List<BasicBlock> blocks = deobber.deobfuscate(cfg);
//				List<BasicBlock> blocks = new ArrayList<>(cfg.blocks());
				System.out.println(GraphUtils.toBlockArray(blocks));
				deobber.removeEmptyBlocks(cfg, blocks);
				GraphUtils.output(cfg, new ArrayList<>(blocks), GRAPH_FOLDER, "1");
				System.out.println(cfg);
				m.instructions = GraphUtils.recreate(cfg, blocks, true);
			}
		}
		
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cn.accept(cw);
		byte[] bytes = cw.toByteArray();
		
		File out = new File(GRAPH_FOLDER, "test.class");
		FileOutputStream fos = new FileOutputStream(out);
		fos.write(bytes);
		fos.close();
		
		Runtime.getRuntime().exec(new String[]{"java", "-jar", "F:/bcv.jar", out.getAbsolutePath()});

	}
}