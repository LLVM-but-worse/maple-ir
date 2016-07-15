package org.rsdeob;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.byteio.CompleteResolvingJarDumper;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.util.CFGDotExporter;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.collections.NodeTable;
import org.rsdeob.stdlib.collections.graph.util.DotExporter;
import org.topdank.banalysis.asm.insn.InstructionPrinter;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.out.JarDumper;

import java.io.File;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;

public class CfgTest {

	public void test(ControlFlowGraph cfg) {
		try {
			try {
				cfg.vertices();
				//cfg.verifyEdgeSet();
			} catch(UnsupportedOperationException e) {
				try {
					e.printStackTrace();
				} catch(UnsupportedCharsetException e2) {
					throw e2;
				}
				throw e;
			}
		} catch(IllegalStateException e2) {
			throw e2;
		}
	}
	
	public static void main(String[] args) throws Exception {
//		Thread.sleep(10000);
//		int len = 5;
//		for(int i=0; i < len; i++) {
//			System.out.println("starting in " + (((len + 1) * len) - ((i + 1) * len)));
//			try {
//				Thread.sleep(len * 1000);
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		}
		System.out.println("starting");
//		System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream("C:/Users/Bibl/Desktop/out.txt"))));
		NodeTable<ClassNode> nt = new NodeTable<ClassNode>();
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<ClassNode>(new JarInfo(new File(String.format("res/gamepack%s.jar", 107))));
		dl.download();
		nt.putAll(dl.getJarContents().getClassContents().namedMap());
//		IContext cxt = new IContext() {
//			@Override
//			public NodeTable<ClassNode> getNodes() {
//				return nt;
//			}
//		};
//		ClassNode cn = cxt.getNodes().get("ad");
//		ClassNode cn = new ClassNode();
//		ClassReader cr = new ClassReader(ControlFlowGraph.class.getCanonicalName());
//		cr.accept(cn, ClassReader.SKIP_FRAMES);
		
		File graphFolder = new File("C://Users//Bibl//Desktop//cfg testing");
		
		for(ClassNode cn : dl.getJarContents().getClassContents()) {
			for(MethodNode m : cn.methods) {
				if(m.instructions.size() < 1)
					continue;
				
//				if(!m.name.equals("v") && !cn.name.equals("ac"))
//					continue;
//				if(m.tryCatchBlocks.size() > 1) {
//					System.out.println("ignoring " + m);
//					continue;
//				}

				try {
//					ControlFlowGraph cfg = new ControlFlowGraph(m);
//					
//					File f = new File(graphFolder, createFileName(m) + "pre.gv");
//					BufferedWriter bw = new BufferedWriter(new FileWriter(f));
//					bw.write(GraphUtils.toGraphString(cfg, cfg.blocks()));
//					bw.close();
//					
//					List<BasicBlock> blocks = new TarjanSCCSorterImpl().sort(cfg);
//					f = new File(graphFolder, createFileName(m) + "post.gv");
//					bw = new BufferedWriter(new FileWriter(f));
//					bw.write(GraphUtils.toGraphString(cfg, blocks));
//					bw.close();
//					
//					m.instructions = GraphUtils.recreate(cfg, blocks, 0);
				} catch(RuntimeException e ) {
					e.printStackTrace();
				}

				if(m.instructions.size() <= 5000) {
//					 System.out.println(GraphUtils.toCfgHeader(cfg.blocks()));
//					List<BasicBlock> blocks = new TarjanSCCSorterImpl().sort(cfg);
//					m.instructions = GraphUtils.recreate(cfg, blocks, 0);
//					 System.out.println(GraphUtils.toCfgHeader(blocks));
				} else {
//					System.out.println("Method too long: " + m);
				}

				if(m.toString().equals("ay.ar(IIIB)V")) {					
//					System.out.println(m);
//					InstructionPrinter.consolePrint(m);
					InstructionPrinter.consolePrint(m);
					m.localVariables.clear();
					ControlFlowGraph cfg = ControlFlowGraphBuilder.create(m);
					(new CFGDotExporter(cfg, new ArrayList<>(cfg.vertices()), m.toString(), "post")).output(DotExporter.OPT_DEEP);
					System.out.println(cfg);
					
//					System.out.println(cfg);
					ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();

					List<BasicBlock> reordered = deobber.deobfuscate(cfg);
					System.out.println(GraphUtils.toBlockArray(reordered));
					m.instructions = GraphUtils.recreate(cfg, reordered, true);
//					GraphUtils.output(cfg, new ArrayList<>(cfg.blocks()), graphFolder, "post");
//					System.exit(1);
//					output(cfg, new ArrayList<>(cfg.blocks()), graphFolder, "pre");
					
//					List<BasicBlock> blocks = new TarjanSCCSorterImpl().sort(cfg);

//					System.out.println(cfg.method.instructions.size());
//					m.instructions = GraphUtils.recreate(cfg, blocks, 0);
//					System.out.println(cfg.method.instructions.size());
//					ControlFlowGraph cfg2 = new ControlFlowGraph(m);

					
//					ControlFlowGraph cfg = new ControlFlowGraph(m);
//					System.out.println(cfg);
//					List<BasicBlock> blocks = new TarjanSCCSorterImpl().sort(cfg);
//					System.out.println(cfg);
//					m.instructions = GraphUtils.recreate(cfg, blocks, 0);
//					System.out.println(GraphUtils.toString(cfg, blocks));
//					Analyzer<BasicValue> analyser = new Analyzer<BasicValue>(new BasicInterpreter());
//					analyser.analyze(cn.name, m);
					
//					ClassNode cn2 = new ClassNode();
//					cn2.name = cn.name;
//					cn2.access = cn.access;
//					cn2.methods.add(m);
//					
//					ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
//					cn2.accept(cw);
//
//					byte[] bytes = cw.toByteArray();
//					File out = new File("C:/Users/Bibl/Desktop/cfg testing/ad2.class");
//					FileOutputStream fos = new FileOutputStream(out);
//					fos.write(bytes);
//					fos.close();
//					
//					Runtime.getRuntime().exec(new String[]{"java", "-jar", "F:/bcv.jar", out.getAbsolutePath()});
				}
			}
		}
		
		System.out.println("ending");
//		Thread.sleep(10000);
		
		File out = null;
		
		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents());
		dumper.dump(out = new File("C:/Users/Bibl/Desktop/107.jar"));
		
//		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
//		cn.accept(cw);
//		byte[] bytes = cw.toByteArray();
//		out = new File("C:/Users/Bibl/Desktop/cfg testing/ad.class");
//		if(out.exists()) {
//			out.delete();
//		}
//		FileOutputStream fos = new FileOutputStream(out);
//		fos.write(bytes);
//		fos.close();
		
		Runtime.getRuntime().exec(new String[]{"java", "-jar", "F:/bcv.jar", out.getAbsolutePath()});

		
//		FileInputStream fis = new FileInputStream(new File("C:/Users/Bibl/Desktop/ad.class"));
//		cr = new ClassReader(fis);
//		cn = new ClassNode();
//		cr.accept(cn, 0);
//		
//		for(MethodNode m : cn.methods) {
//			if(m.toString().equals("org/rsdeob/stdlib/cfg/ControlFlowGraph.test(Lorg/rsdeob/stdlib/cfg/ControlFlowGraph;)V")) {
//				InstructionPrinter.consolePrint(m);
//			}
//		}
	}
}