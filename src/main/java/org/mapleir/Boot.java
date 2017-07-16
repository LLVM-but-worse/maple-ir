package org.mapleir;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import org.mapleir.context.AnalysisContext;
import org.mapleir.context.BasicAnalysisContext;
import org.mapleir.context.IRCache;
import org.mapleir.context.SimpleApplicationContext;
import org.mapleir.context.app.ApplicationClassSource;
import org.mapleir.context.app.InstalledRuntimeClassSource;
import org.mapleir.deob.IPass;
import org.mapleir.deob.PassGroup;
import org.mapleir.deob.interproc.IRCallTracer;
import org.mapleir.deob.interproc.exp2.BlockCallGraph;
import org.mapleir.deob.interproc.exp2.BlockCallGraphBuilder;
import org.mapleir.deob.interproc.exp2.CallEdge;
import org.mapleir.deob.interproc.exp2.CallGraphBlock;
import org.mapleir.deob.interproc.exp2.ConcreteCallGraphBlock;
import org.mapleir.deob.passes.ConstantExpressionReorderPass;
import org.mapleir.deob.passes.DeadCodeEliminationPass;
import org.mapleir.deob.util.RenamingHeuristic;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.ControlFlowGraphDumper;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.algorithms.ExtendedDfs;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanSCC;
import org.mapleir.stdlib.util.InvocationResolver;
import org.mapleir.stdlib.util.dot.DotPropertyDecorator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Boot {
	
	public static boolean logging = false;
	private static long timer;
	private static Deque<String> sections;
	
	private static Collection<ClassNode> classes(Class<?>... a) throws IOException {
		List<ClassNode> list = new ArrayList<>();
		for(int i=0; i < a.length; i++) {
			ClassNode cn = new ClassNode();
			ClassReader cr = new ClassReader(a[i].getName());
			cr.accept(cn, 0);
			list.add(cn);
		}
		return list;
	}
	
//	public static void main(String[] args) {
//		b2();
//	}
//	
//	static void b2() {
//		System.out.println(new RuntimeException().getStackTrace()[1]);
//	}
	
	public static void main5(String[] args) {
//		TarjanSCC<MethodNode> sccComputor = new TarjanSCC<>(graph);
//
//		for(MethodNode m : graph.vertices()) {
//			if(sccComputor.low(m) == -1) {
//				sccComputor.search(m);
//			}
//		}	
	}
	
	public static void main(String[] args) throws Exception {
		sections = new LinkedList<>();
		logging = true;
		/* if(args.length < 1) {
			System.err.println("Usage: <rev:int>");
			System.exit(1);
			return;
		} */
		
		// File f = locateRevFile(135);
//		File f = new File("res/allatori6.1san.jar");
//		section("Preparing to run on " + f.getAbsolutePath());
//		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
//		dl.download();
//		
//		String name = f.getName().substring(0, f.getName().length() - 4);
//		
//		ApplicationClassSource app = new ApplicationClassSource(name, dl.getJarContents().getClassContents());
		ApplicationClassSource app = new ApplicationClassSource("tes", classes(CGExample3.class));
		InstalledRuntimeClassSource jre = new InstalledRuntimeClassSource(app);
		app.addLibraries(jre);
		section("Initialising context.");
		
		AnalysisContext cxt = new BasicAnalysisContext.BasicContextBuilder()
				.setApplication(app)
				.setInvocationResolver(new InvocationResolver(app))
				.setCache(new IRCache(ControlFlowGraphBuilder::build))
				.setApplicationContext(new SimpleApplicationContext(app))
				.build();
		
		section("Expanding callgraph and generating cfgs.");
		
		IRCallTracer tracer = new IRCallTracer(cxt);
		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
			tracer.trace(m);
		}
		
		section0("...generated " + cxt.getIRCache().size() + " cfgs in %fs.%n", "Preparing to transform.");
		
		PassGroup masterGroup = new PassGroup("MasterController");
		for(IPass p : getTransformationPasses()) {
			masterGroup.add(p);
		}
		run(cxt, masterGroup);
		

		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
//			if(e.getKey().toString().equals("com/allatori/iiIiIiiIii.clone()Ljava/lang/Object;"))
				BlockCallGraph.prepareControlFlowGraph(e.getValue());
		}
		
		BlockCallGraphBuilder builder = new BlockCallGraphBuilder(cxt);

		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
//			System.out.println(m);
//			System.out.println(cxt.getIRCache().get(m));
			builder.visit(m);
			
//			if(m.name.equals("main")) {
//				cxt.getIRCache().get(m).makeDotWriter().setName("main").export();
//			}
		}
		
		
		builder.callGraph.makeDotWriter().add(new DotPropertyDecorator<FastGraph<CallGraphBlock,FlowEdge<CallGraphBlock>>, CallGraphBlock, FlowEdge<CallGraphBlock>>() {
			
			@Override
			public void decorateNodeProperties(FastGraph<CallGraphBlock, FlowEdge<CallGraphBlock>> g, CallGraphBlock n,
					Map<String, Object> nprops) {
				
				if(n instanceof ConcreteCallGraphBlock) {
					BasicBlock bb = ((ConcreteCallGraphBlock) n).block;
					
					nprops.put("shape", "box");
//					nprops.put("labeljust", "l");
					
					if(bb.getGraph().getEntries().contains(n)) {
						nprops.put("style", "filled");
						nprops.put("fillcolor", "red");
					} else if(g.getEdges(n).size() == 0) {
						nprops.put("style", "filled");
						nprops.put("fillcolor", "green");
					}
					
					StringBuilder sb = new StringBuilder();
					sb.append(n.getId());
					sb.append("\\l");
					nprops.put("label", sb.toString());
				}
			}
		}).setName("nam4").export();
		
		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
			System.out.println(m);
			
			ExtendedDfs<CallGraphBlock> dfs = new ExtendedDfs<CallGraphBlock>(builder.callGraph, ExtendedDfs.PRE){
				@Override
				protected Iterable<? extends FastGraphEdge<CallGraphBlock>> order(Set<? extends FastGraphEdge<CallGraphBlock>> edges) {
					List<FastGraphEdge<CallGraphBlock>> list = new ArrayList<>();
					list.addAll(edges);
					Collections.sort(list, new Comparator<FastGraphEdge<CallGraphBlock>>() {
						@Override
						public int compare(FastGraphEdge<CallGraphBlock> o1, FastGraphEdge<CallGraphBlock> o2) {
							boolean l1 = o1 instanceof CallEdge;
							boolean l2 = o2 instanceof CallEdge;
							
							if(l1 && l2) {
								System.err.println(o1);
								System.err.println(o2);
								throw new UnsupportedOperationException();
							} else if(l1) {
								return -1;
							} else if(l2) {
								return 1;
							} else {
								return 0;
							}
						}
					});
					return list;
				}
			};
			ControlFlowGraph cfg = cxt.getIRCache().get(m);
			
			dfs.run(builder.getConcreteBlockNode(cfg.getEntries().iterator().next()));
			
			TarjanSCC<CallGraphBlock> scc = new TarjanSCC<CallGraphBlock>(builder.callGraph) {
//				@Override
//				protected Iterable<? extends FastGraphEdge<CallGraphBlock>> filter(Set<? extends FastGraphEdge<CallGraphBlock>> edges) {
//					Set<FastGraphEdge<CallGraphBlock>> set = new HashSet<>();
//					for(FastGraphEdge<CallGraphBlock> e : edges) {
//						if(e instanceof ReturnEdge) {
//							ReturnEdge re = (ReturnEdge) e;
//						}
//					}
//					return set;
//				}
			};
			scc.search(builder.getConcreteBlockNode(cfg.getEntries().iterator().next()));
			System.out.println("sccs:");
			for(List<CallGraphBlock> c : scc.getComponents()) {
				System.out.println("   "+ c);
			}
//			System.out.println("trace:");
//			for(CallGraphBlock cgb : dfs.getPreOrder()) {
//				System.out.println("  " + cgb);
//			}
//			BlockCallGraph.prepareControlFlowGraph(cxt.getIRCache().get(m));
		}
//		new ContextSensitiveIPAnalysis(cxt, new ExpressionEvaluator(new ReflectiveFunctorFactory()));
		
		section("Retranslating SSA IR to standard flavour.");
		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
			BoissinotDestructor.leaveSSA(cfg);
			cfg.getLocals().realloc(cfg);
			(new ControlFlowGraphDumper(cfg, mn)).dump();
		}
		
		section("Rewriting jar.");
//		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents(), app) {
//			@Override
//			public int dumpResource(JarOutputStream out, String name, byte[] file) throws IOException {
////				if(name.startsWith("META-INF")) {
////					System.out.println(" ignore " + name);
////					return 0;
////				}
//				if(name.equals("META-INF/MANIFEST.MF")) {
//					ClassRenamerPass renamer = (ClassRenamerPass) masterGroup.getPass(e -> e.is(ClassRenamerPass.class));
//					
//					if(renamer != null) {
//						ByteArrayOutputStream baos = new ByteArrayOutputStream();
//						BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(baos));
//						BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(file)));
//						
//						String line;
//						while((line = br.readLine()) != null) {
//							String[] parts = line.split(": ", 2);
//							if(parts.length != 2) {
//								bw.write(line);
//								continue;
//							}
//							
//							if(parts[0].equals("Main-Class")) {
//								String newMain = renamer.getRemappedName(parts[1].replace(".", "/")).replace("/", ".");
//								System.out.printf("%s -> %s%n", parts[1], newMain);
//								parts[1] = newMain;
//							}
//
//							bw.write(parts[0]);
//							bw.write(": ");
//							bw.write(parts[1]);
//							bw.write(System.lineSeparator());
//						}
//						
//						br.close();
//						bw.close();
//						
//						file = baos.toByteArray();
//					}
//				}
//				return super.dumpResource(out, name, file);
//			}
//		};
//		dumper.dump(new File("out/osb5.jar"));
		
		section("Finished.");
	}
	
	private static void run(AnalysisContext cxt, PassGroup group) {
		group.accept(cxt, null, new ArrayList<>());
	}
	
	private static IPass[] getTransformationPasses() {
		RenamingHeuristic heuristic = RenamingHeuristic.RENAME_ALL;
		return new IPass[] {
//				new ConcreteStaticInvocationPass(),
//				new ClassRenamerPass(heuristic),
//				new MethodRenamerPass(heuristic),
//				new FieldRenamerPass(),
//				new CallgraphPruningPass(),
				// new ConstantParameterPass()
				// new ClassRenamerPass(),
				// new ConstantExpressionReorderPass(),
				// new FieldRSADecryptionPass(),
				// new PassGroup("Interprocedural Optimisations")
				// 	.add(new ConstantParameterPass())
				// new LiftConstructorCallsPass(),
//				 new DemoteRangesPass(),
				new ConstantExpressionReorderPass(),
				// new FieldRSADecryptionPass(),
				// new ConstantParameterPass(),
//				new ConstantExpressionEvaluatorPass(),
				new DeadCodeEliminationPass()
				
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
				if((m.name.length() > 2 && !m.name.equals("<init>")) || m.instructions.size() == 0) {
					set.add(m);
				}
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