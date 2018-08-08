package org.mapleir;

import com.google.common.collect.Iterables;
import org.apache.log4j.Logger;
import org.mapleir.app.client.SimpleApplicationContext;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.CompleteResolvingJarDumper;
import org.mapleir.app.service.LibraryClassSource;
import org.mapleir.context.AnalysisContext;
import org.mapleir.context.BasicAnalysisContext;
import org.mapleir.context.IRCache;
import org.mapleir.deob.IPass;
import org.mapleir.deob.PassGroup;
import org.mapleir.deob.passes.rename.ClassRenamerPass;
import org.mapleir.deob.util.RenamingHeuristic;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.FieldLoadExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.mapleir.ir.code.expr.invoke.StaticInvocationExpr;
import org.mapleir.ir.code.stmt.FieldStoreStmt;
import org.mapleir.ir.code.stmt.ReturnStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.impl.VersionedLocal;
import org.mapleir.stdlib.collections.CollectionUtils;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdgeImpl;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.util.Pair;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.JarOutputStream;

public class Boot {
	
	private static final Logger LOGGER = Logger.getLogger(Boot.class);
	
	public static boolean logging = false;
	private static long timer;
	private static Deque<String> sections;
	
	private static LibraryClassSource rt(ApplicationClassSource app, File rtjar) throws IOException {
		section("Loading " + rtjar.getName() + " from " + rtjar.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(rtjar));
		dl.download();
		
		return new LibraryClassSource(app, dl.getJarContents().getClassContents());
	}

	private static int fieldId = 0;
	public static void main(String[] args) throws Exception {

		sections = new LinkedList<>();
		logging = true;

		// Load input jar
		//  File f = locateRevFile(135);
		File f = new File("res/jump.jar");

		section("Preparing to run on " + f.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		String name = f.getName().substring(0, f.getName().length() - 4);
		ApplicationClassSource app = new ApplicationClassSource(name, dl.getJarContents().getClassContents());
//		
// 		ApplicationClassSource app = new ApplicationClassSource("test", ClassHelper.parseClasses(CGExample.class));
//		app.addLibraries(new InstalledRuntimeClassSource(app));

		File rtjar = new File("res/rt.jar");
		File androidjar = new File("res/android.jar");
		app.addLibraries(rt(app, rtjar), rt(app, androidjar));
		section("Initialising context.");


		AnalysisContext cxt = new BasicAnalysisContext.BasicContextBuilder()
				.setApplication(app)
				.setInvocationResolver(new DefaultInvocationResolver(app))
				.setCache(new IRCache(ControlFlowGraphBuilder::build))
				.setApplicationContext(new SimpleApplicationContext(app))
				.build();

		section("Expanding callgraph and generating cfgs.");
		// IRCallTracer tracer = new IRCallTracer(cxt);
		// for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
		// 	tracer.trace(m);
		// }

		for (ClassNode cn : cxt.getApplication().iterate()) {
			// if (!cn.name.equals("Test"))
			// 	continue;
			for (MethodNode m : cn.methods) {
				// if (!m.name.equals("func"))
				// 	continue;
				cxt.getIRCache().getFor(m);
			}
		}
		section0("...generated " + cxt.getIRCache().size() + " cfgs in %fs.%n", "Preparing to transform.");

		// do passes
		PassGroup masterGroup = new PassGroup("MasterController");
		for (IPass p : getTransformationPasses()) {
			masterGroup.add(p);
		}
		run(cxt, masterGroup);

		section("Retranslating SSA IR to standard flavour.");

		class JavaDesc implements FastGraphVertex {
			final String owner, name, desc, descType; // FIELD or METHOD -- METHOD=argument flow or return value flow
			final Object extraData;

			public JavaDesc(String owner, String name, String desc, String descType) {
				this(owner, name, desc, descType, null);
				assert (descType.equals("FIELD"));
			}

			public JavaDesc(String owner, String name, String desc, String descType, Object extraData) {
				this.owner = owner;
				this.name = name;
				this.desc = desc;
				this.descType = descType;
				this.extraData = extraData;
			}

			@Override
			public int getNumericId() {
				return fieldId++;
			}

			@Override
			public String getDisplayName() {
				return "(" + descType + ")" + owner + "#" + name + desc + (extraData != null ? "[" + extraData + "]" : "");
			}

			@Override
			public String toString() {
				return getDisplayName();
			}
		}

//		if (cu instanceof ConstantExpr) {
//			ConstantExpr ce = (ConstantExpr)cu;
//			if (ce.getConstant() != null && ce.getConstant() instanceof String) {
//				System.out.println(mn.owner + "#" + mn.name + mn.desc + ", " + ce.getConstant());
//			}
//		}

		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			cfg.verify();
		}

		FastDirectedGraph<JavaDesc, FastGraphEdgeImpl<JavaDesc>> dataFlowgraph = new FastDirectedGraph<JavaDesc, FastGraphEdgeImpl<JavaDesc>>() {};
		ArrayDeque<JavaDesc> analysisQueue = new ArrayDeque<>();
		analysisQueue.add(new JavaDesc("com/socialbicycles/app/d", "a", "[Ljava/lang/String;", "FIELD"));
		analysisQueue.add(new JavaDesc("com/socialbicycles/app/d", "b", "[Ljava/lang/String;", "FIELD"));
		analysisQueue.add(new JavaDesc("com/socialbicycles/app/d", "c", "[Ljava/lang/Integer;", "FIELD"));
		analysisQueue.add(new JavaDesc("com/socialbicycles/app/d", "d", "[Ljava/lang/Integer;", "FIELD"));
		while (!analysisQueue.isEmpty()) {
			JavaDesc curDesc = analysisQueue.pop();
			for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
				MethodNode mn = e.getKey();
				ControlFlowGraph cfg = e.getValue();
				ArrayDeque<Pair<Stmt, Expr>> traceQueue = new ArrayDeque<>();
				if (curDesc.descType.equals("METHOD")) {
					if (mn.owner.name.equals(curDesc.owner) && mn.name.equals(curDesc.name) && mn.desc.equals(curDesc.desc)) {
						if (cxt.getApplication().isLibraryClass(mn.owner.name)) {
							System.out.println("! Avoiding tracing library method " + curDesc);
						} else {
							int tracedLocal = (int) curDesc.extraData;
							boolean found = false;
							for (BasicBlock b : cfg.vertices()) {
								for (Stmt s : b) {
									for (CodeUnit cu : s.enumerateWithSelf()) {
										if (cu instanceof VarExpr) {
											VarExpr var = (VarExpr) cu;
											Local l = var.getLocal();
											if (!l.isStack() && l.getIndex() == tracedLocal) {
												traceQueue.add(new Pair<>(s, var));
												found = true;
											}
										}
									}
								}
							}
							if (found)
								System.out.println("! " + mn.owner + "#" + mn.name + mn.desc + " receives arg " + tracedLocal);
						}
					}
				} else if (curDesc.descType.equals("FIELD")) {
					for (BasicBlock b : cfg.vertices()) {
						for (Stmt s : b) {
							for (CodeUnit cu : s.enumerateWithSelf()) {
								if (cu instanceof FieldLoadExpr) {
									FieldLoadExpr fle = (FieldLoadExpr) cu;
									if (fle.getOwner().equals(curDesc.owner) && fle.getName().equals(curDesc.name) && fle.getDesc().equals(curDesc.desc)) {
										System.out.println(cfg);
										traceQueue.add(new Pair<>(s, fle));
										System.out.println("! " + mn.owner + "#" + mn.name + mn.desc + " reads " + curDesc);
									}
								}
							}
						}
					}
				}
				while (!traceQueue.isEmpty()) {
					Pair<Stmt, Expr> traceTuple = traceQueue.pop();
					Stmt traceStmt = traceTuple.getKey(); // data dst
					Expr traceSrc = traceTuple.getValue(); // data src
					if (traceStmt instanceof CopyVarStmt) {
						Local toLocal = ((CopyVarStmt) traceStmt).getVariable().getLocal();
						assert (toLocal instanceof VersionedLocal);
						System.out.println(toLocal + " <- " + traceSrc);
						for (VarExpr use : cfg.getLocals().uses.get((VersionedLocal) toLocal)) {
							traceQueue.add(new Pair<>(use.getRootParent(), use)); // data flows from local to stmt
						}
					} else if (traceStmt instanceof FieldStoreStmt) {
						FieldStoreStmt fss = (FieldStoreStmt) traceStmt;
						JavaDesc jd = new JavaDesc(fss.getOwner(), fss.getName(), fss.getDesc(), "FIELD");
						analysisQueue.add(jd);
						dataFlowgraph.addEdge(new FastGraphEdgeImpl<>(jd, curDesc));
						System.out.println(jd + " <- " + traceSrc);
					} else if (traceStmt instanceof ReturnStmt) {
						JavaDesc jd = new JavaDesc(mn.owner.name, mn.name, mn.desc, "METHOD");
						System.out.println(jd + " returns " + traceSrc);
						// todo, data leaves function to callees. need to xref func for callees
					}

					for (Expr traceChild : traceStmt.enumerateOnlyChildren()) {
						if (traceChild instanceof InvocationExpr) {
							InvocationExpr ie = (InvocationExpr) traceChild;
							System.out.println(ie.getOwner() + "#" + ie.getName() + ie.getDesc() + "() <- " + traceSrc);
							for (MethodNode callTarg : ie.resolveTargets(cxt.getInvocationResolver())) {
								Expr[] argumentExprs = ie.getArgumentExprs();
								for (int i = 0; i < argumentExprs.length; i++) {
									Expr argExpr = argumentExprs[i];
									for (Expr argSubExpr : argExpr.enumerateWithSelf()) {
										if (argSubExpr == traceSrc) {
											JavaDesc jd = new JavaDesc(callTarg.owner.name, callTarg.name, callTarg.desc, "METHOD", i);
											dataFlowgraph.addEdge(new FastGraphEdgeImpl<>(jd, curDesc));
											analysisQueue.add(jd);
											System.out.println("* possible callee " + jd);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		dataFlowgraph.makeDotWriter().setName("fuckmedaddy6969").export();
//		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
//			MethodNode mn = e.getKey();
//			// if (!mn.name.equals("openFiles"))
//			// 	continue;
//			ControlFlowGraph cfg = e.getValue();
//
//			// System.out.println(cfg);
//			// CFGUtils.easyDumpCFG(cfg, "pre-destruct");
//			cfg.verify();
//
//			BoissinotDestructor.leaveSSA(cfg);
//
//			// CFGUtils.easyDumpCFG(cfg, "pre-reaalloc");
//			cfg.getLocals().realloc(cfg);
//			// CFGUtils.easyDumpCFG(cfg, "post-reaalloc");
//			// System.out.println(cfg);
//			cfg.verify();
//			// System.out.println("Rewriting " + mn.name);
//			// System.exit(1);
//			(new ControlFlowGraphDumper(cfg, mn)).dump();
//			// System.out.println(InsnListUtils.insnListToString(mn.instructions));
//		}
		
//		section("Rewriting jar.");
//		dumpJar(app, dl, masterGroup, "out/rewritten.jar");
		
		section("Finished.");
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
								LOGGER.info(String.format("%s -> %s%n", parts[1], newMain));
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
				
				// new ConstantExpressionReorderPass(),
				// new FieldRSADecryptionPass(),
				// new ConstantParameterPass(),
//				new ConstantExpressionEvaluatorPass(),
// 				new DeadCodeEliminationPass()
				
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
				LOGGER.info(sectionText);
		} else {
			/* remove last section. */
			sections.pop();
			if(!quiet) {
				LOGGER.info(String.format(endText, lap()));
				LOGGER.info(sectionText);
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
			LOGGER.info(sectionText);
		} else {
			/* remove last section. */
			sections.pop();
			LOGGER.info(String.format(endText, lap()));
			LOGGER.info(sectionText);
		}

		/* push the new one. */
		sections.push(sectionText);
	}
	
	private static void section(String text) {
		section0("...took %fs.", text);
	}
}
