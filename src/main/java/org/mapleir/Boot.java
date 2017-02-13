package org.mapleir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.Map.Entry;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.deobimpl2.CallgraphPruningPass;
import org.mapleir.deobimpl2.FieldRSADecryptionPass;
import org.mapleir.deobimpl2.MethodRenamerPass;
import org.mapleir.ir.ControlFlowGraphDumper;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.call.CallTracer;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.out.JarDumper;

public class Boot {

	private static Map<MethodNode, ControlFlowGraph> cfgs;
	private static long timer;
	private static Deque<String> sections;
	
	private static double lap() {
		long now = System.nanoTime();
		long delta = now - timer;
		timer = now;
		return (double)delta / 1_000_000_000L;
	}
	
	private static void section0(String endText, String sectionText) {
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
	
	
	static BigInteger inverse(BigInteger v) {
		return v.modInverse(BigInteger.ONE.shiftLeft(32));
	}
	
	public static void main55(String[] args) {
		// for an int field.
		Set<Number> encs = new HashSet<>();
		encs.add(-1091305141);
		Set<Number> decs = new HashSet<>();
		decs.add(339378275);
		decs.add(309047477);
		
		Number[] arr = FieldRSADecryptionPass.get_pair(encs, decs, false);
		System.out.println(Arrays.toString(arr));
		
		System.out.println(BigInteger.valueOf(-1091305141).divide(BigInteger.valueOf(339378275)).intValue());
		System.out.println(BigInteger.valueOf(-1091305141).multiply(BigInteger.valueOf(339378275)).intValue());
	}
	
	public static void main22(String[] args) throws Exception {
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(new File("res/allatori.jar")));
		dl.download();
		
		int i = 0;
		for(ClassNode cn : dl.getJarContents().getClassContents()) {
			for(MethodNode m : cn.methods) {
				if(!m.toString().equals("com/allatori/IIIIiIIiIi.IIIIIIiIII(Lcom/allatori/IIIiiiIiii;)V"))
					continue;
				ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
//				System.out.println(i++ + ". " + m + " [" + m.instructions.size() + "]");
//				BoissinotDestructor.leaveSSA(cfg);
//				cfg.getLocals().realloc(cfg);
//				ControlFlowGraphDumper.dump(cfg, m);
			}
		}
		

		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents());
		dumper.dump(new File("out/osb.jar"));
		
//		BigInteger i = BigInteger.valueOf(-1021538271);
//		System.out.println(i.intValue());
//		BigInteger inv = FieldRSADecryptionPass.inverse(i, false);
//		
//		System.out.println(inv.intValue());
//		System.out.println(inv.multiply(i).intValue());
//		BigInteger benc = BigInteger.valueOf(29);
//		int e1 = benc.intValue();
//		BigInteger bdec = BigInteger.valueOf(1332920885);
//		int d1 = bdec.intValue();
//		
//		BigInteger benc2 = BigInteger.valueOf(101);
//		int e2 = benc2.intValue();
//		BigInteger bdec2 = FieldRSADecryptionPass.inverse(benc2, false);
//		int d2 = bdec2.intValue();
//		
//		int k = 10;
//		int f1 = 6 * e1;
//		int f2 = 7 * e2;
//		
//		f1 = (f2 * (d2 * e1)) + (k * e1);
//		
//		System.out.println(f1 * d1);
		
		
		if("".equals("")) {
			return;
		}
	}
	
	public static void main4(String[] args) {
		System.out.println(String.format("e = %h", Math.E));
	}
	
	public static void main5(String[] args) {
		int a = 1615910351;
		int b = -1794810141;
//		System.out.println((long)a * b);
//		int a = -986047255;
//		int b = -89621671;
		System.out.println((int) a  * b);

		BigInteger i = BigInteger.valueOf(-5223297472920518199L);
//		BigInteger j = BigInteger.valueOf(-89621671);
//		
//		System.out.println(i.multiply(j).intValue());
		System.out.println(i.modInverse(BigInteger.ONE.shiftLeft(64)).longValue());
//		System.out.println(i.modInverse(BigInteger.ONE.shiftLeft(32)));
//		System.out.println(i.modInverse(BigInteger.ONE.shiftLeft(32)).multiply(j.modInverse(BigInteger.ONE.shiftLeft(32))).intValue());
	}
	
	public static void main(String[] args) throws IOException {
		cfgs = new HashMap<>();
		sections = new LinkedList<>();
		/* if(args.length < 1) {
			System.err.println("Usage: <rev:int>");
			System.exit(1);
			return;
		} */
		
		File f = locateRevFile(129);
		
		section("Preparing to run on " + f.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		
//		for(ClassNode cn : dl.getJarContents().getClassContents()) {
//			for(MethodNode m : cn.methods) {
////				System.out.println(m + " @" + m.instructions.size());
//				if(m.toString().startsWith("cr.r(IIIIIIIILct;IZII)Z")) {
//					ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
//					System.out.println(cfg);
//					
//					Set<UDEdge> terminalEdges = new HashSet<>();
//					UDNode start = FlowGraphUtils.computeUndigraph(cfg, terminalEdges);
//					
//					
//					List<UDNode> nodes = FlowGraphUtils.compute(start);
//					Map<BasicBlock, TCNode> map = new HashMap<>();
//					Set<StrongComponent> sccs = FlowGraphUtils.computeTransitiveClosures(cfg, map);
//					
//					Set<BasicBlock> handlers = new HashSet<>();
//					for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
//						handlers.add(er.getHandler());
//					}
//					Collection<TCNode> splitPoints = FlowGraphUtils.computeSplitPoints(map, handlers, terminalEdges);
//					
//					map.get(cfg.getEntries().iterator().next()).computeSplitPointSuccessors();
//					
//					System.out.println(splitPoints);
//				}
//			}
//		}
//		if("".equals(""))
//			return;
		
		section("Building jar class hierarchy.");
		ClassTree tree = new ClassTree(dl.getJarContents().getClassContents());
		
		section("Initialising context.");

		InvocationResolver resolver = new InvocationResolver(tree);
		IContext cxt = new IContext() {
			@Override
			public ClassTree getClassTree() {
				return tree;
			}

			@Override
			public ControlFlowGraph getIR(MethodNode m) {
				if(cfgs.containsKey(m)) {
					return cfgs.get(m);
				} else {
					ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
					cfgs.put(m, cfg);
					return cfg;
				}
			}

			@Override
			public Set<MethodNode> getActiveMethods() {
				return cfgs.keySet();
			}

			@Override
			public InvocationResolver getInvocationResolver() {
				return resolver;
			}
		};
		
		section("Expanding callgraph and generating cfgs.");
		CallTracer tracer = new IRCallTracer(cxt) {
			@Override
			protected void processedInvocation(MethodNode caller, MethodNode callee, Expr call) {
				/* the cfgs are generated by calling IContext.getIR()
				 * in IRCallTracer.traceImpl(). */
			}
		};
		for(MethodNode m : findEntries(tree)) {
			tracer.trace(m);
		}
		
		section0("...generated " + cfgs.size() + " cfgs in %fs.%n", "Preparing to transform.");
		
		runPasses(cxt, getTransformationPasses());
			

		for(Entry<MethodNode, ControlFlowGraph> e : cfgs.entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
			if(mn.toString().equals("a.akt(Lx;I)V")) {
				BufferedWriter bw = new BufferedWriter(new FileWriter(new File("C:/Users/Bibl/Desktop/test224.txt")));
				bw.write(cfg.toString());
				bw.close();
			}
			
		}
		
		section("Retranslating SSA IR to standard flavour.");
		for(Entry<MethodNode, ControlFlowGraph> e : cfgs.entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
			BoissinotDestructor.leaveSSA(cfg);
			cfg.getLocals().realloc(cfg);
			ControlFlowGraphDumper.dump(cfg, mn);
		}
		
		section("Rewriting jar.");
		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents());
		dumper.dump(new File("out/osb.jar"));
		
		section("Finished.");
	}
	
	private static void runPasses(IContext cxt, ICompilerPass[] passes) {
		List<ICompilerPass> completed = new ArrayList<>();
		ICompilerPass last = null;
		
		for(int i=0; i < passes.length; i++) {
			ICompilerPass p = passes[i];
			section0("...took %fs." + (i == 0 ? "%n" : ""), "Running " + p.getId());
			p.accept(cxt, last, completed);
			
			completed.add(p);
			last = p;
		}
	}
	
	private static ICompilerPass[] getTransformationPasses() {
		return new ICompilerPass[] {
				new CallgraphPruningPass(),
//				new ConcreteStaticInvocationPass(),
				new MethodRenamerPass(),
//				new ConstantParameterPass(),
//				new ConstantExpressionReorderPass(),
//				new FieldRSADecryptionPass()
		};
	}
	
	private static File locateRevFile(int rev) {
		return new File("res/gamepack" + rev + ".jar");
	}
	
	private static Set<MethodNode> findEntries(ClassTree tree) {
		Set<MethodNode> set = new HashSet<>();
		for(ClassNode cn : tree.getClasses().values())  {
			for(MethodNode m : cn.methods) {
				if(m.name.length() > 2) {
					set.add(m);
				}
			}
		}
		return set;
	}
}