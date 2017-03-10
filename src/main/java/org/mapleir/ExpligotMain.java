package org.mapleir;

import org.mapleir.deobimpl2.CallgraphPruningPass;
import org.mapleir.deobimpl2.ConstantExpressionEvaluatorPass;
import org.mapleir.deobimpl2.ConstantExpressionReorderPass;
import org.mapleir.deobimpl2.ConstantParameterPass;
import org.mapleir.deobimpl2.DeadCodeEliminationPass;
import org.mapleir.deobimpl2.cxt.IContext;
import org.mapleir.deobimpl2.cxt.MapleDB;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.app.InstalledRuntimeClassSource;
import org.mapleir.stdlib.app.LibraryClassSource;
import org.mapleir.stdlib.call.CallTracer;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.deob.PassGroup;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExpligotMain {
	public static String sha1(File file) throws Exception  {
	    MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
	    InputStream fis = new FileInputStream(file);
	    int n = 0;
	    byte[] buffer = new byte[8192];
	    while (n != -1) {
	        n = fis.read(buffer);
	        if (n > 0) {
	            sha1.update(buffer, 0, n);
	        }
	    }
		return new HexBinaryAdapter().marshal(sha1.digest());
	}
	
	public static String version = "1.0";
	
	public static void main(String[] args) throws Exception {
		if(args.length < 1) {
			System.err.println("expligot " + version);
			System.err.println("Usage: expligot <input.jar> [libs...]");
			System.exit(1);
			return;
		}
		
		File f = new File(args[0]);
		if (!f.exists()) {
			System.err.println("Target " + f + " doesn't exist");
			System.exit(1);
			return;
		}
		
		System.out.println("expligot " + version);
		System.out.println(new Date());
		System.out.println();
		
		System.out.println(f.getName());
		System.out.println("SHA1: " + sha1(f));
		 
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		
		// Extract plugin main
		if (!dl.getJarContents().getResourceContents().namedMap().containsKey("plugin.yml")) {
			System.out.println("ERROR: No plugin.yml");
			System.exit(2);
			return;
		}
		String mainClassName = null;
		String content = new String(dl.getJarContents().getResourceContents().namedMap().get("plugin.yml").getData());
		for (Scanner sc = new Scanner(content); sc.hasNextLine();)
		{
			String line = sc.nextLine().replaceAll("\\s+","");
			if (line.startsWith("main:"))
			{
				mainClassName = line.split(":")[1].replace('.', '/');
				break;
			}
		}
		if (mainClassName == null) {
			System.out.println("ERROR: Failed to parse plugin.yml; main class property not found");
			System.exit(2);
			return;
		}
		System.out.println("Main class: " + mainClassName);
		
		String appName = f.getName().substring(0, f.getName().length() - 4);
		ApplicationClassSource app = new ApplicationClassSource(appName, dl.getJarContents().getClassContents());
		ClassNode mainClass = app.findClassNode(mainClassName);
		if (mainClass == null) {
			System.out.println("ERROR: Main class not found in jar");
			System.exit(2);
			return;
		}
		
		// Kill JSRs
		for(ClassNode cn : app.iterate()) {
			List<MethodNode> newMethods = new ArrayList<>();
			for (MethodNode m : cn.methods) {
				MethodNode nodeWithoutJsr = new MethodNode(m.owner, m.access, m.name, m.desc, m.signature, m.exceptions.toArray(new String[0]));
				JSRInlinerAdapter adapter = new JSRInlinerAdapter(nodeWithoutJsr, m.access, m.name, m.desc, m.signature, m.exceptions.toArray(new String[0]));
				m.accept(adapter);
				newMethods.add(nodeWithoutJsr);
				if (adapter.dirty) {
					System.out.println("WARNING: jsr detected; highly likely jar is obfuscated");
				}
			}
			cn.methods = newMethods;
		}
		
		int totalSize = countMethods(app);
		System.out.println(totalSize + " methods total");

		InstalledRuntimeClassSource jre = new InstalledRuntimeClassSource(app);
		app.addLibraries(jre);
		ClassNode jreClass = jre.findClass("java/lang/Class").node;
		
		for (int i = 1; i < args.length; i++) {
			File libFile = new File(args[i]);
			SingleJarDownloader<ClassNode> libDl = new SingleJarDownloader<>(new JarInfo(libFile));
			libDl.download();
			LibraryClassSource lib = new LibraryClassSource(app, libDl.getJarContents().getClassContents());
			app.addLibraries(lib);
		}
		app.rebuildTable();
		
		IContext cxt = new MapleDB(app);
		AtomicBoolean abort = new AtomicBoolean(false);
		CallTracer tracer = new IRCallTracer(cxt) {
			@Override
			protected void processedInvocation(MethodNode caller, MethodNode callee, Expr call) {
				if (!abort.get() && callee.owner == jreClass && callee.name.equals("forName")) {
					System.out.println("WARNING: Found Class.forName; aborting callgraph analysis (caller " + caller + ")");
					abort.set(true);
				}
			}
		};
		
		for(MethodNode m : mainClass.methods) {
			if (abort.get())
				break;
			tracer.trace(m);
		}
		int presize = cxt.getCFGS().size();
		int delta = totalSize - presize;
		float percent = 100.f * delta / (float)totalSize;
		percent = (float)Math.floor(percent * 100) / 100.f;
		if (!abort.get() && percent >= 10) {
			System.out.printf("WARNING: A large portion of the code (%.1f%%) was skipped during callgraph analysis; reanalyzing fully\n", percent);
			abort.set(true);
		}
		if (abort.get()) {
			for(ClassNode cn : app.iterate())
				for (MethodNode m : cn.methods)
					tracer.trace(m);
			presize = cxt.getCFGS().size();
		} else {
			System.out.println(presize + " methods analysed (delta -" + delta + " = " + percent + "%)");
		}
		
		PassGroup masterGroup = new PassGroup("MasterController");
		for(IPass p : getTransformationPasses()) {
			if (abort.get() && p instanceof CallgraphPruningPass) {
				continue;
			}
			masterGroup.add(p);
		}
		run(cxt, masterGroup);
		
		int postsize = cxt.getCFGS().size();
		delta = postsize - presize;
		percent = 100.f * delta / (float)presize;
		percent = (float)Math.floor(percent * 100) / 100.f;
		if (percent >= 5)
			System.out.println("WARNING: A large portion of the code was marked dead during optimization.");
		System.out.println(presize + " non-dead methods post-optimisation (delta -" + delta + " = " + percent + "%)\n");
		
		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getCFGS().entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			for (BasicBlock b : cfg.vertices()) {
				for (Stmt stmt : b) {
					for(Expr c : stmt.enumerateOnlyChildren()) {
						if (c.getOpcode() == Opcode.INVOKE) {
							InvocationExpr invoke = (InvocationExpr) c;
							boolean isStatic = (invoke.getCallType() == Opcodes.INVOKESTATIC);
							String owner = invoke.getOwner();
							String name = invoke.getName();
							
							if (owner.equals("java/lang/Runtime") && name.equals("exec")) {
								System.out.println("Call to Runtime.exec in " + mn);
								System.out.println("    Context: " + invoke);
							}
							if (owner.equals("java/lang/ProcessBuilder")) {
								System.out.println("Call to ProcessBuilder in " + mn);
								System.out.println("    Context: " + invoke);
							}
						}
					}
				}
			}
		}
		
		System.out.println("\n---");
		
//		section("Finished.");
	}
	
	private static void run(IContext cxt, PassGroup group) {
		group.accept(cxt, null, new ArrayList<>());
	}
	
	private static IPass[] getTransformationPasses() {
		return new IPass[] {
				new CallgraphPruningPass(),
//				new ConcreteStaticInvocationPass(),
//				new MethodRenamerPass(),
//				new ConstantParameterPass(),
//				new ClassRenamerPass(),
//				new FieldRenamerPass(),
//				new ConstantExpressionReorderPass(),
//				new FieldRSADecryptionPass(),
//				new PassGroup("Interprocedural Optimisations")
//					.add(new ConstantParameterPass())
				new ConstantExpressionReorderPass(),
//				new FieldRSADecryptionPass(),
				new ConstantParameterPass(),
				new ConstantExpressionEvaluatorPass(),
				new DeadCodeEliminationPass()
//				new PassGroup("Interprocedural Optimisations")
				
		};
	}
	
	private static int countMethods(ApplicationClassSource source) {
		int count = 0;
		for(ClassNode cn : source.iterate())
			count += cn.methods.size();
		return count;
	}
}