package org.rsdeob.stdlib.cfg.util;

import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class DotExporter {
	public static final String[] GRAPHVIZ_COLOURS = new String[]{
			"aliceblue", "antiquewhite", "aquamarine", "brown1", "cadetblue1",
			"chocolate1", "cornflowerblue", "cyan", "darkgoldenrod1",
			"darkolivegreen4", "darkorchid1", "darksalmon",
			"deeppink", "deepskyblue1", "firebrick1", "gold1", "hotpink1", "khaki1",
			"mediumseagreen", "orangered", "mediumpurple1", "magenta", "plum1",
			"royalblue1", "slateblue3", "turquoise2", "yellow2"
	};

	private static final String GRAPHVIZ_DOT_PATH = "dot\\dot.exe";

	public static void output(ControlFlowGraph cfg, List<BasicBlock> blocks, File graphFolder, String type) throws Exception {
		File dotFile = new File(graphFolder, createFileName(cfg.getMethod()) + type + ".gv");
		if(dotFile.exists()) {
			dotFile.delete();
		}
		BufferedWriter bw = new BufferedWriter(new FileWriter(dotFile));
		bw.write(toGraphString(cfg, blocks, null, true));
		bw.close();

		File gv = new File(GRAPHVIZ_DOT_PATH);
		File imgFile = new File(graphFolder, createFileName(cfg.getMethod()) + type + ".png");
		if(imgFile.exists()) {
			imgFile.delete();
		}
		ProcessBuilder builder = new ProcessBuilder(gv.getAbsolutePath(), "-Tpng", dotFile.getAbsolutePath(), "-o", imgFile.getAbsolutePath());
		Process process = builder.start();
		process.waitFor();

	}

	public static void output(ControlFlowGraph cfg, List<BasicBlock> blocks, ControlFlowGraphDeobfuscator.SuperNodeList svList, File graphFolder, String type) throws Exception {
		File dotFile = new File(graphFolder, createFileName(cfg.getMethod()) + type + ".gv");
		if(dotFile.exists()) {
			dotFile.delete();
		}
		BufferedWriter bw = new BufferedWriter(new FileWriter(dotFile));
		bw.write(toGraphString(cfg, blocks, svList, true));
		bw.close();

		File gv = new File(GRAPHVIZ_DOT_PATH);
		File imgFile = new File(graphFolder, createFileName(cfg.getMethod()) + type + ".png");
		if(imgFile.exists()) {
			imgFile.delete();
		}
		ProcessBuilder builder = new ProcessBuilder(gv.getAbsolutePath(), "-Tpng", dotFile.getAbsolutePath(), "-o", imgFile.getAbsolutePath());
		Process process = builder.start();
		process.waitFor();
	}

	public static void output(String name, StatementGraph sg, CodeBody stmts, File graphFolder, String type) throws Exception {
		File dotFile = new File(graphFolder, createFileName(name) + type + ".gv");
		if(dotFile.exists()) {
			dotFile.delete();
		}
		BufferedWriter bw = new BufferedWriter(new FileWriter(dotFile));
		bw.write(toGraphString(name, sg, stmts, null, true));
		bw.close();

		File gv = new File(GRAPHVIZ_DOT_PATH);
		File imgFile = new File(graphFolder, createFileName(name) + type + ".png");
		if(imgFile.exists()) {
			imgFile.delete();
		}
		ProcessBuilder builder = new ProcessBuilder(gv.getAbsolutePath(), "-Tpng", '"' + dotFile.getAbsolutePath() + '"', "-o", '"' + imgFile.getAbsolutePath() + '"');
		Process process = builder.start();
		process.waitFor();

	}

	public static String createFileName(MethodNode m) {
		return createFileName(m.owner.name + " " + m.name + " " + m.desc);
	}

	// '\', '/', ':', '*', '?', '<', '>', '|'
	public static String createFileName(String name) {
		return name.replaceAll("[/\\:*?\"<>|]", "");
	}

	public static String toGraphString(ControlFlowGraph cfg, List<BasicBlock> blocks, ControlFlowGraphDeobfuscator.SuperNodeList svList, boolean deep) {
		StringBuilder sb = new StringBuilder();
		sb.append("digraph ");
		sb.append(createFileName(cfg.getMethod().name));
		sb.append(" {").append(System.lineSeparator());
		//		sb.append("rate=\"fill\";\nsize=\"8.3,11.7!\";\n\nmargin=0;\n");

		String colour = null;
		int colourindex = 0;
		ControlFlowGraphDeobfuscator.SuperNode lastSuperVertex = null;

		for(BasicBlock b : blocks) {
			if(b.isDummy())
				continue;
			sb.append(b.getId()).append(" ");
			sb.append("[shape=box, label=").append('"');
			sb.append(blocks.indexOf(b)).append(". ").append(b.getId());
			if(deep) {
				sb.append("\n");
				GraphUtils.printBlock(cfg, blocks, sb, b, 0, false);
			}
			sb.append('"');

			ControlFlowGraphDeobfuscator.SuperNode sv;
			if(cfg.getEntries().contains(b)) {
				sb.append(", style=filled, fillcolor=red");
			} else if(svList != null) {
				if(svList.entryNodes.contains(b)) {
					sb.append(", style=filled, fillcolor=green");
				} else if((sv = svList.find(b)) != null) {
					if(lastSuperVertex != sv) {
						if(colourindex >= GRAPHVIZ_COLOURS.length) {
							colourindex = 0;
						}
						colour = GRAPHVIZ_COLOURS[colourindex++];
						lastSuperVertex = sv;
					}
					sb.append(", style=filled, fillcolor=").append(colour);
				}
			}
			sb.append("]\n");
		}

		for(BasicBlock b : blocks) {
			for(FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
//				if(e instanceof TryCatchEdge && !deep)
//					continue;
//				if(e instanceof TryCatchEdge)
//					continue;
				sb.append("").append(e.src.getId()).append(" -> ").append(e.dst.getId()).append(" ");
				if(deep) {
					sb.append("[label=").append('"').append(e.toGraphString()).append('"').append("]");
				}
				sb.append(";\n");
			}
		}

		sb.append("\n}");

		return sb.toString();
	}

	public static String toGraphString(String name, StatementGraph sg, CodeBody stmts, ControlFlowGraphDeobfuscator.SuperNodeList svList, boolean deep) {
		StringBuilder sb = new StringBuilder();
		sb.append("digraph ");
		sb.append(createFileName(name));
		sb.append(" {").append(System.lineSeparator());
		//		sb.append("rate=\"fill\";\nsize=\"8.3,11.7!\";\n\nmargin=0;\n");

		String colour = null;
		int colourindex = 0;

		for(Statement stmt : stmts) {
			if (stmt instanceof HeaderStatement)
				continue;
			sb.append(stmt.getId()).append(" ");
			sb.append("[shape=box, label=").append('"');
			sb.append(stmts.indexOf(stmt)).append(". #").append(stmt.getId());
			if(deep) {
				sb.append(": ");
				sb.append(stmt.toString().replaceAll("\"", "\\\\\""));
			}
			sb.append('"');

			if(sg.getEntries().contains(stmt)) {
				sb.append(", style=filled, fillcolor=red");
			} else if(svList != null) {
				if(svList.entryNodes.contains(stmt)) {
					sb.append(", style=filled, fillcolor=green");
				}
			}
			sb.append("]\n");
		}

		for(Statement stmt : stmts) {
			if (stmt instanceof HeaderStatement)
				continue;
			for(FlowEdge<Statement> e : sg.getEdges(stmt)) {
//				if(e instanceof TryCatchEdge && !deep)
//					continue;
//				if(e instanceof TryCatchEdge)
//					continue;
				sb.append("").append(e.src.getId()).append(" -> ").append(e.dst.getId()).append(" ");
				if(deep) {
					sb.append("[label=").append('"').append(e.toGraphString()).append('"').append("]");
				}
				sb.append(";\n");
			}
		}

		sb.append("\n}");

		return sb.toString();
	}
}
