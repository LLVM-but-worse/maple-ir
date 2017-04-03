package org.mapleir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarOutputStream;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.deobimpl2.ClassRenamerPass;
import org.mapleir.deobimpl2.FieldRenamerPass;
import org.mapleir.deobimpl2.MethodRenamerPass;
import org.mapleir.deobimpl2.cxt.BasicContext;
import org.mapleir.deobimpl2.cxt.IContext;
import org.mapleir.deobimpl2.cxt.IRCache;
import org.mapleir.ir.ControlFlowGraphDumper;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.app.InstalledRuntimeClassSource;
import org.mapleir.stdlib.call.CallTracer;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.deob.PassGroup;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.mapleir.t.A;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.out.JarDumper;

public class Boot {
	
	public static boolean logging = false;
	private static long timer;
	private static Deque<String> sections;

	void a(A a) {
		System.out.println(a.m(0));
	}
	
	public static void main5(String[] args) throws Exception {
		ClassNode c1 = new ClassNode();
		c1.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
		c1.version = Opcodes.V1_7;
		c1.name = "r/Test";
		c1.superName = "java/lang/Object";

		{
			MethodNode m = new MethodNode(c1, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "m", "(I)Lorg/mapleir/t/I0;", null, null);
			c1.methods.add(m);
		}

		{
			MethodNode m = new MethodNode(c1, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "m", "(I)Lorg/mapleir/t/I1;", null, null);
			c1.methods.add(m);
		}
		
		{
			MethodNode m = new MethodNode(c1, Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
			InsnList list = new InsnList();
			list.add(new VarInsnNode(Opcodes.ALOAD, 0));
			list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
			list.add(new InsnNode(Opcodes.RETURN));
			m.instructions = list;
			c1.methods.add(m);
		}
		
		ClassNode c2 = new ClassNode();
		c2.access = Opcodes.ACC_PUBLIC;
		c2.version = Opcodes.V1_7;
		c2.name = "r/Test2";
		c2.superName = "r/Test";
		
		{
			MethodNode m = new MethodNode(c2, Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
			InsnList list = new InsnList();
			list.add(new VarInsnNode(Opcodes.ALOAD, 0));
			list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "r/Test", "<init>", "()V", false));
			list.add(new InsnNode(Opcodes.RETURN));
			m.instructions = list;
			c2.methods.add(m);
		}
		
		{
			MethodNode m = new MethodNode(c2, Opcodes.ACC_PUBLIC, "m", "(I)Lorg/mapleir/t/I1;", null, null);
			InsnList list = new InsnList();
			list.add(new InsnNode(Opcodes.ACONST_NULL));
			list.add(new InsnNode(Opcodes.ARETURN));
			m.instructions = list;
			c2.methods.add(m);
		}
		
		{
			MethodNode m = new MethodNode(c2, Opcodes.ACC_PUBLIC, "m", "(I)Lorg/mapleir/t/I0;", null, null);
			InsnList list = new InsnList();
			list.add(new InsnNode(Opcodes.ACONST_NULL));
			list.add(new InsnNode(Opcodes.ARETURN));
			m.instructions = list;
			c2.methods.add(m);
		}
		
		ClassLoader cl = new ClassLoader() {
			{
				{
					ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
					c1.accept(cw);
					byte[] bs = cw.toByteArray();
					defineClass(bs, 0, bs.length);
				}
				{
					ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
					c2.accept(cw);
					byte[] bs = cw.toByteArray();
					defineClass(bs, 0, bs.length);
				}
			}
		};

		System.out.println(cl.loadClass("r.Test"));
		Object o = cl.loadClass("r.Test2").newInstance();
		
		for(Method m : o.getClass().getMethods()) {
			if(m.getName().equals("m")) {
				System.out.println(m.invoke(o, 5));
			}
		}
	}
	
//	public static void main(String[] args) throws Exception {
//		Class<?>[] cls = new Class<?>[]{I0.class, I1.class, I3.class, I4.class, A.class, B.class, C.class, I1Impl.class};
//		
//		List<ClassNode> classes = new ArrayList<>();
//		for(Class<?> c : cls) {
//			ClassReader cr = new ClassReader(c.getName());
//			ClassNode cn = new ClassNode();
//			cr.accept(cn, 0);
//			classes.add(cn);
//		}
//		
//		ApplicationClassSource app = new ApplicationClassSource("test", classes);
//		InstalledRuntimeClassSource jre = new InstalledRuntimeClassSource(app);
//		app.addLibraries(jre);
//		
//		IContext cxt = new MapleDB(app);
//		System.out.println(cxt.getInvocationResolver().resolveVirtualCalls("org/mapleir/t/A", "m", "(I)Lorg/mapleir/t/I1;", true));
//		
//	}
	public static void main(String[] args) throws Exception {
		sections = new LinkedList<>();
		logging = true;
		/* if(args.length < 1) {
			System.err.println("Usage: <rev:int>");
			System.exit(1);
			return;
		} */
		
//		File f = locateRevFile(135);
		File f = new File("res/allatori6.1.jar");
		section("Preparing to run on " + f.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
//		
//		for(ClassNode cn : dl.getJarContents().getClassContents()) {
//			for(MethodNode m : cn.methods) {
//				if(m.toString().equals("com/allatori/IIiIiiiIII.IIiIiiIiII(Ljava/lang/String;)Ljava/lang/String;")) {
//					ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
//					System.out.println(cfg);
//				}
//			}
//		}
//		
//		if("".equals("")) {
//			return;
//		}
		String name = f.getName().substring(0, f.getName().length() - 4);
		
		ApplicationClassSource app = new ApplicationClassSource(name, dl.getJarContents().getClassContents());
		InstalledRuntimeClassSource jre = new InstalledRuntimeClassSource(app);
		app.addLibraries(jre);
		section("Initialising context.");
		
		IContext cxt = new BasicContext.BasicContextBuilder()
				.setApplication(app)
				.setInvocationResolver(new InvocationResolver(app))
				.setCache(new IRCache(ControlFlowGraphBuilder::build))
				.build();
		
		section("Expanding callgraph and generating cfgs.");
		
//		for(ClassNode cn : app.iterate()) {
//			for(MethodNode m : cn.methods) {
//				if(m.toString().equals("com/allatori/IIiIIIiIii.IIiIiiIiII(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")) {
//					ControlFlowGraph cfg = cxt.getIRCache().getFor(m);
//					
//					BoissinotDestructor.leaveSSA(cfg);
//					cfg.getLocals().realloc(cfg);
//					ControlFlowGraphDumper.dump(cfg, m);
//					
//					ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
//					cn.accept(cw);
//					byte[] bs = cw.toByteArray();
//					
//					FileOutputStream fos = new FileOutputStream(new File("out/testclass.class"));
//					fos.write(bs);
//					fos.close();
//				}
//			}
//		}
		
//		if("".equals("")) {
//			return;
//		}
		
		CallTracer tracer = new IRCallTracer(cxt);
		for(MethodNode m : findEntries(app)) {
			tracer.trace(m);
		}
		
		section0("...generated " + cxt.getIRCache().size() + " cfgs in %fs.%n", "Preparing to transform.");
		
		PassGroup masterGroup = new PassGroup("MasterController");
		for(IPass p : getTransformationPasses()) {
			masterGroup.add(p);
		}
		run(cxt, masterGroup);
		
		section("Retranslating SSA IR to standard flavour.");
		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
//			if(mn.owner.name.equals("brq") && mn.name.equals("adm")) {
//				InstructionPrinter.consolePrint(mn);
//			}
			
			BoissinotDestructor.leaveSSA(cfg);
			cfg.getLocals().realloc(cfg);
			ControlFlowGraphDumper.dump(cfg, mn);
		}
		
		section("Rewriting jar.");
		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents(), app) {
			@Override
			public int dumpResource(JarOutputStream out, String name, byte[] file) throws IOException {
				if(name.startsWith("META-INF")) {
					System.out.println(" ignore " + name);
					return 0;
				}
				return super.dumpResource(out, name, file);
			}
		};
		dumper.dump(new File("out/osb5.jar"));
		
		section("Finished.");
	}
	
	private static void run(IContext cxt, PassGroup group) {
		group.accept(cxt, null, new ArrayList<>());
	}
	
	private static IPass[] getTransformationPasses() {
		return new IPass[] {
//				new CallgraphPruningPass(),
//				new ConcreteStaticInvocationPass(),
				new ClassRenamerPass(),
				new MethodRenamerPass(),
//				new ConstantParameterPass()
//				new ClassRenamerPass(),
				new FieldRenamerPass(),
//				new ConstantExpressionReorderPass(),
//				new FieldRSADecryptionPass(),
//				new PassGroup("Interprocedural Optimisations")
//					.add(new ConstantParameterPass())
//				new ConstantExpressionReorderPass(),
//				new FieldRSADecryptionPass(),
//				new ConstantParameterPass(),
//				new ConstantExpressionEvaluatorPass(),
//				new DeadCodeEliminationPass()
//				new PassGroup("Interprocedural Optimisations")
				
		};
	}
	
	static File locateRevFile(int rev) {
		return new File("res/gamepack" + rev + ".jar");
	}
	
	private static Set<MethodNode> findEntries(ApplicationClassSource source) {
		Set<MethodNode> set = new HashSet<>();
		/* searches only app classes. */
		for(ClassNode cn : source.iterate())  {
			for(MethodNode m : cn.methods) {
//				if((m.name.length() > 2 && !m.name.equals("<init>")) || m.instructions.size() == 0) {
					set.add(m);
//				}
			}
		}
		return set;
	}
	
	private static double lap() {
		long now = System.nanoTime();
		long delta = now - timer;
		timer = now;
		return (double)delta / 1_000_000_000L;
	}
	
	public static void section0(String endText, String sectionText, boolean quiet) {
		if(sections.isEmpty()) {
			lap();
			if(!quiet)
				System.out.println(sectionText);
		} else {
			/* remove last section. */
			sections.pop();
			if(!quiet) {
				System.out.printf(endText, lap());
				System.out.println("\n" + sectionText);
			} else {
				lap();
			}
		}

		/* push the new one. */
		sections.push(sectionText);
	}
	
	public static void section0(String endText, String sectionText) {
		if(sections.isEmpty()) {
			lap();
			System.out.println(sectionText);
		} else {
			/* remove last section. */
			sections.pop();
			System.out.printf(endText, lap());
			System.out.println("\n" + sectionText);
		}

		/* push the new one. */
		sections.push(sectionText);
	}
	
	private static void section(String text) {
		section0("...took %fs.%n", text);
	}
}