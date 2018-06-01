package org.mapleir;

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
import org.mapleir.deob.passes.ConstantParameterPass;
import org.mapleir.deob.passes.DeadCodeEliminationPass;
import org.mapleir.deob.passes.rename.ClassRenamerPass;
import org.mapleir.deob.util.RenamingHeuristic;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.ControlFlowGraphDumper;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
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
		section("Loading rt.jar from " + rtjar.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(rtjar));
		dl.download();
		
		return new LibraryClassSource(app, dl.getJarContents().getClassContents());
	}
	
	public static void main(String[] args) throws Exception {
		
		sections = new LinkedList<>();
		logging = true;
		
		// Load input jar
		//  File f = locateRevFile(135);
		File f = new File("res/jda-1.0.0.jar");
		
		section("Preparing to run on " + f.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		String name = f.getName().substring(0, f.getName().length() - 4);
		ApplicationClassSource app = new ApplicationClassSource(name, dl.getJarContents().getClassContents());
//		
// 		ApplicationClassSource app = new ApplicationClassSource("test", ClassHelper.parseClasses(CGExample.class));
//		app.addLibraries(new InstalledRuntimeClassSource(app));
		
		File rtjar = new File("res/rt.jar");
		app.addLibraries(rt(app, rtjar));
		section("Initialising context.");
		
		
		AnalysisContext cxt = new BasicAnalysisContext.BasicContextBuilder()
				.setApplication(app)
				.setInvocationResolver(new DefaultInvocationResolver(app))
				.setCache(new IRCache(ControlFlowGraphBuilder::build))
				.setApplicationContext(new SimpleApplicationContext(app))
				.build();
		
		// section("Expanding callgraph and generating cfgs.");
		// IRCallTracer tracer = new IRCallTracer(cxt);
		// for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
		// 	tracer.trace(m);
		// }
		// section0("...generated " + cxt.getIRCache().size() + " cfgs in %fs.%n", "Preparing to transform.");
		
		// do passes
		PassGroup masterGroup = new PassGroup("MasterController");
		for(IPass p : getTransformationPasses()) {
			masterGroup.add(p);
		}
		run(cxt, masterGroup);
		
		for (ClassNode cn : cxt.getApplication().iterate()) {
			// if (!cn.name.equals("Test"))
			// 	continue;
			for (MethodNode m : cn.methods) {
				// if (!m.name.equals("enterWhenUninterruptibly"))
				// 	continue;
				cxt.getIRCache().getFor(m);
			}
		}
		
		section("Retranslating SSA IR to standard flavour.");
		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
			MethodNode mn = e.getKey();
			// if (!mn.name.equals("openFiles"))
			// 	continue;
			ControlFlowGraph cfg = e.getValue();
			
			// System.out.println(cfg);
			// CFGUtils.easyDumpCFG(cfg, "pre-destruct");

			BoissinotDestructor.leaveSSA(cfg);

			// CFGUtils.easyDumpCFG(cfg, "pre-reaalloc");
			cfg.getLocals().realloc(cfg);
			// CFGUtils.easyDumpCFG(cfg, "post-reaalloc");
			// System.out.println(cfg);
			System.out.println("Rewriting " + mn.name);
			// System.exit(1);
			(new ControlFlowGraphDumper(cfg, mn)).dump();
			// System.out.println(InsnListUtils.insnListToString(mn.instructions));
		}
		
		section("Rewriting jar.");
		dumpJar(app, dl, masterGroup, "out/rewritten.jar");
		
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
