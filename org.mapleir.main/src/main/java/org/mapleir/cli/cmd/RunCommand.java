package org.mapleir.cli.cmd;

import org.apache.log4j.Logger;
import org.mapleir.Boot;
import org.mapleir.DefaultInvocationResolver;
import org.mapleir.Main;
import org.mapleir.app.client.SimpleApplicationContext;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.CompleteResolvingJarDumper;
import org.mapleir.app.service.LibraryClassSource;
import org.mapleir.asm.ClassNode;
import org.mapleir.asm.MethodNode;
import org.mapleir.cli.CliLog;
import org.mapleir.context.AnalysisContext;
import org.mapleir.context.BasicAnalysisContext;
import org.mapleir.context.IRCache;
import org.mapleir.deob.IPass;
import org.mapleir.deob.PassContext;
import org.mapleir.deob.PassGroup;
import org.mapleir.deob.PassResult;
import org.mapleir.deob.dataflow.LiveDataFlowAnalysisImpl;
import org.mapleir.deob.passes.fixer.ExceptionFixerPass;
import org.mapleir.deob.passes.rename.ClassRenamerPass;
import org.mapleir.deob.util.RenamingHeuristic;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.LocalsReallocator;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.codegen.ControlFlowGraphDumper;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import picocli.CommandLine;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.jar.JarOutputStream;

@CommandLine.Command(
        name = "run",
        mixinStandardHelpOptions = true,
        version = "run 1.0",
        description = "Compiles back and forth from SSA to optimize code."
)
public class RunCommand implements Callable<Integer> {
    @CommandLine.Parameters(
            index = "0",
            description = "The file which will be optimized."
    )
    private File input;

    @CommandLine.Option(
            names = {"-rt", "--runtime"},
            description = "Path to the runtime jar"
    )
    private File runtime;

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "Path to the output jar location"
    )
    private File output;

    private final CliLog logger = new CliLog();

    @Override
    public Integer call() throws Exception {


        if (input == null) {
            logger.print("Fatal! Failed to find input jar!");
            return 1;
        }

        // Initialization
        logger.section("Preparing to run on " + input.getAbsolutePath());
        SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(input));
        dl.download();
        String appName = input.getName().substring(0, input.getName().length() - 4);
        ApplicationClassSource app = new ApplicationClassSource(appName, dl.getJarContents().getClassContents());

        if (output == null) {
            output = new File(appName + "-out.jar");
        }

        logger.section("Importing runtime...");
        if (runtime == null) {
            runtime = new File(System.getProperty("java.home"), "lib/rt.jar");
        }
        app.addLibraries(rt(app, runtime));


        logger.section("Initialising context.");
        IRCache irFactory = new IRCache(ControlFlowGraphBuilder::build);
        AnalysisContext cxt = new BasicAnalysisContext.BasicContextBuilder()
                .setApplication(app)
                .setInvocationResolver(new DefaultInvocationResolver(app))
                .setCache(irFactory)
                .setApplicationContext(new SimpleApplicationContext(app))
                .setDataFlowAnalysis(new LiveDataFlowAnalysisImpl(irFactory))
                .build();

        logger.section("Expanding callgraph and generating cfgs.");
        for (ClassNode cn : cxt.getApplication().iterate()) {
            for (MethodNode m : cn.getMethods()) {
                cxt.getIRCache().getFor(m);
            }
        }
        logger.section0("...generated " + cxt.getIRCache().size() + " cfgs in %fs.%n", "Preparing to transform.");

        // do passes
        PassGroup masterGroup = new PassGroup("MasterController");
        for (IPass p : getTransformationPasses()) {
            masterGroup.add(p);
        }
        run(cxt, masterGroup);
        logger.section0("...done transforming in %fs.%n", "Preparing to transform.");


        for(Map.Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
            MethodNode mn = e.getKey();
            ControlFlowGraph cfg = e.getValue();
            cfg.verify();
        }

        logger.section("Retranslating SSA IR to standard flavour.");
        for(Map.Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
            MethodNode mn = e.getKey();
            // if (!mn.getName().equals("openFiles"))
            // 	continue;
            ControlFlowGraph cfg = e.getValue();

            // System.out.println(cfg);
            //  CFGUtils.easyDumpCFG(cfg, "pre-destruct");
            cfg.verify();

            BoissinotDestructor.leaveSSA(cfg);

            // CFGUtils.easyDumpCFG(cfg, "pre-reaalloc");
            LocalsReallocator.realloc(cfg);
            // CFGUtils.easyDumpCFG(cfg, "post-reaalloc");
            // System.out.println(cfg);
            cfg.verify();
            // System.out.println("Rewriting " + mn.getName());
            (new ControlFlowGraphDumper(cfg, mn)).dump();
            // System.out.println(InsnListUtils.insnListToString(mn.instructions));
        }

        logger.section("Rewriting jar.");
        dumpJar(app, dl, masterGroup, output.getPath());

        logger.section("Finished.");

        return 0;
    }

    private LibraryClassSource rt(ApplicationClassSource app, File rtjar) throws IOException {
        logger.section("Loading " + rtjar.getName() + " from " + rtjar.getAbsolutePath());
        SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(rtjar));
        dl.download();

        return new LibraryClassSource(app, dl.getJarContents().getClassContents());
    }

    private void dumpJar(ApplicationClassSource app, SingleJarDownloader<ClassNode> dl, PassGroup masterGroup, String outputFile) throws IOException {
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
                                logger.print(String.format("%s -> %s%n", parts[1], newMain));
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
        PassContext pcxt = new PassContext(cxt, null, new ArrayList<>());
        PassResult result = group.accept(pcxt);

        if(result.getError() != null) {
            throw new RuntimeException(result.getError());
        }
    }

    private static IPass[] getTransformationPasses() {
        RenamingHeuristic heuristic = RenamingHeuristic.RENAME_ALL;
        return new IPass[] {
                new ExceptionFixerPass()
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
            for(MethodNode m : cn.getMethods()) {
                if((m.getName().length() > 2 && !m.getName().equals("<init>")) || m.node.instructions.size() == 0) {
                    set.add(m);
                }
            }
        }
        return set;
    }


}
