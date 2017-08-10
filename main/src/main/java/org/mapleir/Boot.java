package org.mapleir;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarOutputStream;

import org.mapleir.app.client.SimpleApplicationContext;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.CompleteResolvingJarDumper;
import org.mapleir.app.service.InstalledRuntimeClassSource;
import org.mapleir.app.service.LibraryClassSource;
import org.mapleir.context.AnalysisContext;
import org.mapleir.context.BasicAnalysisContext;
import org.mapleir.context.IRCache;
import org.mapleir.deob.IPass;
import org.mapleir.deob.PassGroup;
import org.mapleir.deob.interproc.IRCallTracer;
import org.mapleir.deob.interproc.callgraph.CallGraphNode;
import org.mapleir.deob.interproc.callgraph.CallGraphNode.CallReceiverNode;
import org.mapleir.deob.interproc.callgraph.CallGraphReducer;
import org.mapleir.deob.interproc.callgraph.CallSiteSensitiveCallGraph;
import org.mapleir.deob.interproc.callgraph.SensitiveCallGraphBuilder;
import org.mapleir.deob.interproc.callgraph.SiteSensitiveCallDAG;
import org.mapleir.deob.interproc.exp2.BlockCallGraph;
import org.mapleir.deob.passes.ConstantExpressionReorderPass;
import org.mapleir.deob.passes.DeadCodeEliminationPass;
import org.mapleir.deob.passes.rename.ClassRenamerPass;
import org.mapleir.deob.util.RenamingHeuristic;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.ControlFlowGraphDumper;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.stdlib.collections.graph.algorithms.SimpleDfs;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanSCC;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

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
	
	private static LibraryClassSource rt(ApplicationClassSource app, File rtjar) throws IOException {
		section("Loading rt.jar from " + rtjar.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(rtjar));
		dl.download();
		
		return new LibraryClassSource(app, dl.getJarContents().getClassContents());
	}
	
	public static void main(String[] args) throws Exception {
		sections = new LinkedList<>();
		logging = true;
		
		File rtjar = new File("res/rt.jar");
		// Load input jar
		 File f = locateRevFile(135);
//		File f = new File("res/allatori6.1san.jar");
		section("Preparing to run on " + f.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		String name = f.getName().substring(0, f.getName().length() - 4);
		ApplicationClassSource app = new ApplicationClassSource(name, dl.getJarContents().getClassContents());
		
//		ApplicationClassSource app = new ApplicationClassSource("test", classes(CGExample.class));
		app.addLibraries(rt(app, rtjar), new InstalledRuntimeClassSource(app));
		section("Initialising context.");
		
		AnalysisContext cxt = new BasicAnalysisContext.BasicContextBuilder()
				.setApplication(app)
				.setInvocationResolver(new InvocationResolver2(app))
				.setCache(new IRCache(ControlFlowGraphBuilder::build))
				.setApplicationContext(new SimpleApplicationContext(app))
				.build();
		
		section("Expanding callgraph and generating cfgs.");
		
		IRCallTracer tracer = new IRCallTracer(cxt);
		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
			System.out.println(m);
			tracer.trace(m);
		}
		
		section0("...generated " + cxt.getIRCache().size() + " cfgs in %fs.%n", "Preparing to transform.");
		
		// do passes
		PassGroup masterGroup = new PassGroup("MasterController");
		for(IPass p : getTransformationPasses()) {
			masterGroup.add(p);
		}
		run(cxt, masterGroup);
		
		// for(MethodNode m : cxt.getIRCache().getActiveMethods()) {
		// 	if(m.instructions.size() > 100 && m.instructions.size() < 500) {
		// 		System.out.println(cxt.getIRCache().get(m));
		// 	}
		// }

		section("Preparing BlockGallGraphs.");
		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
				BlockCallGraph.prepareControlFlowGraph(e.getValue());
		}
		
		section("Performing IPA.");
		doIPAnalysis(cxt);
		
		section("Retranslating SSA IR to standard flavour.");
		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
			BoissinotDestructor.leaveSSA(cfg);
			cfg.getLocals().realloc(cfg);
			(new ControlFlowGraphDumper(cfg, mn)).dump();
		}
		
		section("Rewriting jar.");
		// dumpJar(app, dl, masterGroup, "out/osb5.jar");
		
		section("Finished.");
	}
	
	private static void doIPAnalysis(AnalysisContext cxt) {
		//		BlockCallGraphBuilder builder = new BlockCallGraphBuilder(cxt);

//		builder.init();
		
		SensitiveCallGraphBuilder builder = new SensitiveCallGraphBuilder(cxt);
		CallSiteSensitiveCallGraph cg = builder.build(cxt.getApplicationContext().getEntryPoints());
		
		TarjanSCC<CallGraphNode> sccComputor = new TarjanSCC<>(cg);
		
		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
			CallReceiverNode node = cg.getNode(m);

			// need to check for main method or clinit, since otherwise we throw on library inherited methods
			if(cg.getReverseEdges(node).size() > 0 && (SimpleApplicationContext.isMainMethod(m) || m.name.equals("<clinit>"))) {
				throw new RuntimeException("entry called?");
			}
			
			sccComputor.search(node);
		}
		
		for(List<CallGraphNode> c : sccComputor.getComponents()) {
			if(c.size() > 1) {
				System.out.println(c);
			}
		}

		CallGraphReducer sccEliminator = new CallGraphReducer(cxt);
		SiteSensitiveCallDAG reducedCallGraph = sccEliminator.eliminateSCCs(cg);
		SiteSensitiveCallDAG.MultiCallGraphNode entrySCC = reducedCallGraph.findSCCOf(cg.getNode(cxt.getApplicationContext().getEntryPoints().iterator().next()));
		SimpleDfs<SiteSensitiveCallDAG.MultiCallGraphNode> dfs = new SimpleDfs<>(reducedCallGraph, entrySCC, SimpleDfs.TOPO);
		System.out.println("\nScc DAG toposort:");
		for (SiteSensitiveCallDAG.MultiCallGraphNode scc : dfs.getTopoOrder()) {
			System.out.println(scc);
		}

		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
//			System.out.println(m);
//			System.out.println(cxt.getIRCache().get(m));
//			builder.visit(m);

//			if(m.name.equals("main")) {
//				cxt.getIRCache().get(m).makeDotWriter().setName("main").export();
//			}
		}

//		TarjanSCC<CallGraphNode> scc = new TarjanSCC<>(csscg);
//		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
//			scc.search(csscg.getNode(m));
//		}
//
//		for(List<CallGraphNode> c : scc.getComponents()) {
//			System.out.println("   "+ c);
//		}


//		DotWriter<FastGraph<CallGraphNode, CallGraphEdge>, CallGraphNode, CallGraphEdge> writer = cg.makeDotWriter();
//		writer.add(new DotPropertyDecorator<FastGraph<CallGraphNode,CallGraphEdge>, CallSiteSensitiveCallGraph.CallGraphNode, CallSiteSensitiveCallGraph.CallGraphEdge>() {
//
//			@Override
//			public void decorateNodeProperties(FastGraph<CallGraphNode, CallGraphEdge> g, CallGraphNode n,
//					Map<String, Object> nprops) {
//				nprops.put("label", n.toString());
//			}
//		});
//		writer.setName("cg22 5").export();

//		builder.callGraph.makeDotWriter().add(new DotPropertyDecorator<FastGraph<CallGraphBlock,FlowEdge<CallGraphBlock>>, CallGraphBlock, FlowEdge<CallGraphBlock>>() {
//
//			@Override
//			public void decorateNodeProperties(FastGraph<CallGraphBlock, FlowEdge<CallGraphBlock>> g, CallGraphBlock n,
//					Map<String, Object> nprops) {
//
//				if(n instanceof ConcreteCallGraphBlock) {
//					BasicBlock bb = ((ConcreteCallGraphBlock) n).block;
//
//					nprops.put("shape", "box");
////					nprops.put("labeljust", "l");
//
//					if(bb.getGraph().getEntries().contains(n)) {
//						nprops.put("style", "filled");
//						nprops.put("fillcolor", "red");
//					} else if(g.getEdges(n).size() == 0) {
//						nprops.put("style", "filled");
//						nprops.put("fillcolor", "green");
//					}
//
//					StringBuilder sb = new StringBuilder();
//					sb.append(((ConcreteCallGraphBlock) n).block.getId());
//					sb.append("\\l\\l");
//
//					if(((ConcreteCallGraphBlock) n).block instanceof ReturnBlock) {
//						sb.append("RETURN_TARG\\l");
//					} else {
//						StringBuilder sb2 = new StringBuilder();
//						{
//							Iterator<Stmt> it = (((ConcreteCallGraphBlock) n).block).iterator();
//							TabbedStringWriter sw = new TabbedStringWriter();
//							int insn = 0;
//							while(it.hasNext()) {
//								Stmt stmt = it.next();
//								sw.print(insn++ + ". ");
//								stmt.toString(sw);
//								sw.print("\n");
//							}
//							sb2.append(sw.toString());
//						}
//						sb.append(sb2.toString().replace("\n", "\\l"));
//					}
//
//					nprops.put("label", sb.toString());
//				}
//			}
//		}).setName("nam6").export();
		
		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
			System.out.println(m);
			
//			ExtendedDfs<CallGraphBlock> dfs = new ExtendedDfs<CallGraphBlock>(builder.callGraph, ExtendedDfs.PRE){
//				@Override
//				protected Iterable<? extends FastGraphEdge<CallGraphBlock>> order(Set<? extends FastGraphEdge<CallGraphBlock>> edges) {
//					List<FastGraphEdge<CallGraphBlock>> list = new ArrayList<>();
//					list.addAll(edges);
//					Collections.sort(list, new Comparator<FastGraphEdge<CallGraphBlock>>() {
//						@Override
//						public int compare(FastGraphEdge<CallGraphBlock> o1, FastGraphEdge<CallGraphBlock> o2) {
//							boolean l1 = o1 instanceof CallEdge;
//							boolean l2 = o2 instanceof CallEdge;
//
//							if(l1 && l2) {
//								System.err.println(o1);
//								System.err.println(o2);
//								throw new UnsupportedOperationException();
//							} else if(l1) {
//								return -1;
//							} else if(l2) {
//								return 1;
//							} else {
//								return 0;
//							}
//						}
//					});
//					return list;
//				}
//			};
			ControlFlowGraph cfg = cxt.getIRCache().get(m);
			
//			dfs.run(builder.getConcreteBlockNode(cfg.getEntries().iterator().next()));

//			TarjanSCC<CallGraphBlock> scc = new TarjanSCC<CallGraphBlock>(builder.callGraph) {
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
//			};
//			IPTarjanSCC scc = new IPTarjanSCC(builder.callGraph);
//			TabbedStringWriter sw = new TabbedStringWriter();
//			try {
//				scc.search(builder.getConcreteBlockNode(cfg.getEntries().iterator().next()), sw, null);
//			} catch(Throwable t) {
//				System.out.println(sw.toString());
//				System.err.flush();
//				System.out.flush();
//				throw t;
//			}
//
//			for (List<CallGraphBlock> c : scc.comps) {
//				System.out.println("   " + c);
//			}

//			scc.search(builder.getConcreteBlockNode(cfg.getEntries().iterator().next()));
//			System.out.println("sccs:");
//			for(List<CallGraphBlock> c : scc.comps) {
//				System.out.println("   "+ c);
//			}
//			System.out.println("trace:");
//			for(CallGraphBlock cgb : dfs.getPreOrder()) {
//				System.out.println("  " + cgb);
//			}
//			BlockCallGraph.prepareControlFlowGraph(cxt.getIRCache().get(m));
		}
//		new ContextSensitiveIPAnalysis(cxt, new ExpressionEvaluator(new ReflectiveFunctorFactory()));
	}
	
	private static void dumpJar(ApplicationClassSource app, SingleJarDownloader<ClassNode> dl, PassGroup masterGroup, String outputFile) throws IOException {
		(new CompleteResolvingJarDumper(dl.getJarContents(), app) {
			@Override
			public int dumpResource(JarOutputStream out, String name, byte[] file) throws IOException {
//				if(name.startsWith("META-INF")) {
//					System.out.println(" ignore " + name);
//					return 0;
//				}
				if(name.equals("META-INF/MANIFEST.MF")) {
					ClassRenamerPass renamer = (ClassRenamerPass) masterGroup.getPass(e -> e.is(ClassRenamerPass.class));

					if(renamer != null) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(baos));
						BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(file)));

						String line;
						while((line = br.readLine()) != null) {
							String[] parts = line.split(": ", 2);
							if(parts.length != 2) {
								bw.write(line);
								continue;
							}

							if(parts[0].equals("Main-Class")) {
								String newMain = renamer.getRemappedName(parts[1].replace(".", "/")).replace("/", ".");
								System.out.printf("%s -> %s%n", parts[1], newMain);
								parts[1] = newMain;
							}

							bw.write(parts[0]);
							bw.write(": ");
							bw.write(parts[1]);
							bw.write(System.lineSeparator());
						}

						br.close();
						bw.close();

						file = baos.toByteArray();
					}
				}
				return super.dumpResource(out, name, file);
			}
		}).dump(new File(outputFile));
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
