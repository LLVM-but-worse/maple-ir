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
import org.mapleir.deobimpl2.ConstantExpressionEvaluatorPass;
import org.mapleir.deobimpl2.ConstantExpressionReorderPass;
import org.mapleir.deobimpl2.DeadCodeEliminationPass;
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
import org.mapleir.stdlib.collections.KeyedValueCreator;
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
		dumper.dump(new File("out/osb4.jar"));
		
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
//				new FieldRenamerPass(),
//				new ConstantExpressionReorderPass(),
//				new FieldRSADecryptionPass(),
//				new PassGroup("Interprocedural Optimisations")
//					.add(new ConstantParameterPass())
				new ConstantExpressionReorderPass(),
//				new FieldRSADecryptionPass(),
//				new ConstantParameterPass(),
				new ConstantExpressionEvaluatorPass(),
				new DeadCodeEliminationPass()
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