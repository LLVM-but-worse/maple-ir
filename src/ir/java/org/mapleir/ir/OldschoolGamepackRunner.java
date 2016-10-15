package org.mapleir.ir;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.call.CallGraph;
import org.mapleir.stdlib.call.CallGraph.CallgraphAdapter;
import org.mapleir.stdlib.collections.NodeTable;
import org.mapleir.stdlib.klass.ClassTree;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.out.JarDumper;

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
					exec.submit(makeJob(m));
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

		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents());
		dumper.dump(new File("out/" + name));
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