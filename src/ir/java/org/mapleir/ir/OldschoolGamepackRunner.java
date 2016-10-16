package org.mapleir.ir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.call.CallGraph;
import org.mapleir.stdlib.call.CallGraph.CallgraphAdapter;
import org.mapleir.stdlib.collections.NodeTable;
import org.mapleir.stdlib.klass.ClassNodeUtil;
import org.mapleir.stdlib.klass.ClassTree;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

public class OldschoolGamepackRunner {

	private final IContext cxt;
	private final CallGraph cg;
	
	public OldschoolGamepackRunner(String name) throws IOException {
		File f = new File("res/" + name);
		System.out.println("Using " + f.getAbsolutePath());
		
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		
		NodeTable<ClassNode> nt = new NodeTable<>();
		nt.putAll(dl.getJarContents().getClassContents().namedMap());
		
		cxt = new IContext() {
			@Override
			public NodeTable<ClassNode> getNodes() {
				return nt;
			}
		};

		long start = System.nanoTime();
		
		cg = pruneCallGraph();
		
		ExecutorService exec = Executors.newFixedThreadPool(4);
		int mcount = 0;
		
		System.out.println();
		for(ClassNode cn : nt) {
			for(MethodNode m : cn.methods) {
				if(m.toString().equals("dx.<clinit>()V")) {
//					exec.submit(makeJob(m));
					makeJob(m).run();
					
					cn.superName = "java/lang/Object";
					
					ClassLoader cl = new ClassLoader(){
						{
							ClassWriter cw = new ClassWriter(0);
		        			cn.accept(cw);
		        			byte[] b = cw.toByteArray();
							defineClass(b, 0, b.length);
						}
					};
					try {
						System.out.println(cl.loadClass("dx"));;
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					out(dl.getJarContents().getClassContents(), cn, m);
					break;
				}
			}
			mcount += cn.methods.size();
		}
		
		try {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		System.out.println("Processed " + mcount + " methods.");
		System.out.printf("That took %d seconds.%n", (System.nanoTime() - start) / 1000000000);

//		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents());
//		dumper.dump(new File("out/" + name));
	}
	
	private void out(Collection<ClassNode> cc, ClassNode cn, MethodNode m) throws IOException {
		ClassTree classTree = new ClassTree(cc);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {

			// this method in ClassWriter uses the systemclassloader as
			// a stream location to load the super class, however, most of
			// the time the class is loaded/read and parsed by us so it
			// isn't defined in the system classloader. in certain cases
			// we may not even want it to be loaded/resolved and we can
			// bypass this by implementing the hierarchy scanning algorithm
			// with ClassNodes rather than Classes.
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				ClassNode ccn = classTree.getClass(type1);
				ClassNode dcn = classTree.getClass(type2);

				if (ccn == null) {
					ClassNode c = ClassNodeUtil.create(type1);
					if (c == null) {
						return "java/lang/Object";
					}
					classTree.build(c);
					return getCommonSuperClass(type1, type2);
				}

				if (dcn == null) {
					ClassNode c = ClassNodeUtil.create(type2);
					if (c == null) {
						return "java/lang/Object";
					}
					classTree.build(c);
					return getCommonSuperClass(type1, type2);
				}

				Set<ClassNode> c = classTree.getSupers(ccn);
				Set<ClassNode> d = classTree.getSupers(dcn);

				if (c.contains(dcn))
					return type1;

				if (d.contains(ccn))
					return type2;

				if (Modifier.isInterface(ccn.access) || Modifier.isInterface(dcn.access)) {
					// enums as well?
					return "java/lang/Object";
				} else {
					do {
						ClassNode nccn = classTree.getClass(ccn.superName);
						if (nccn == null)
							break;
						ccn = nccn;
						c = classTree.getSupers(ccn);
					} while (!c.contains(dcn));
					return ccn.name;
				}
			}

		};
		cn.methods.clear();
		cn.methods.add(m);
		cn.accept(cw);
		byte[] bs = cw.toByteArray();
		FileOutputStream out = new FileOutputStream(new File("out/work.class"));
		out.write(bs, 0, bs.length);
		out.close();
	}
	
	private CallGraph pruneCallGraph() {
		if("".equals(""))
			return null;
		
		CallGraph callgraph = new CallGraph(new CallgraphAdapter() {
			@Override
			public boolean shouldMap(CallGraph graph, MethodNode m) {
				return protectedMethod(graph.getTree(), m);
			}
		}, new ClassTree(cxt.getNodes().values()));
		
		return callgraph;
	}
	
	private Runnable makeJob(MethodNode m) {
		return new Runnable() {
			@Override
			public void run() {
				// System.out.println(m.toString() + " (" + m.instructions.size() + ")");
				ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
				BoissinotDestructor.leaveSSA(cfg);
				cfg.getLocals().realloc(cfg);
				ControlFlowGraphDumper.dump(cfg, m);
//				System.out.println(cfg);
//				InstructionPrinter.consolePrint(m);
			}
		};
	}
	
	private boolean protectedMethod(ClassTree tree, MethodNode mn) {
		return mn.instructions.size() == 0 || mn.name.length() > 2 || isInherited(tree, mn.owner, mn);
	}
	
	private MethodNode getMethodFromSuper(ClassTree tree, ClassNode cn, String name, String desc, boolean isStatic) {
		for (ClassNode super_ : tree.getSupers(cn)) {
			for (MethodNode mn : super_.methods) {
				if (mn.name.equals(name) && mn.desc.equals(desc) && ((mn.access & Opcodes.ACC_STATIC) != 0) == isStatic) {
					return mn;
				}
			}
		}
		return null;
	}
	
	private boolean isInherited(ClassTree tree, ClassNode cn, String name, String desc, boolean isStatic) {
		return getMethodFromSuper(tree, cn, name, desc, isStatic) != null;
	}

	private boolean isInherited(ClassTree tree, ClassNode owner, MethodNode mn) {
		if(owner == null) {
			throw new NullPointerException();
		}
		return mn.owner.name.equals(owner.name) && isInherited(tree, owner, mn.name, mn.desc, (mn.access & Opcodes.ACC_STATIC) != 0);
	}
	
	public static void main(String[] args) throws IOException {
		OldschoolGamepackRunner runner = new OldschoolGamepackRunner("gamepack107.jar");
	}
}