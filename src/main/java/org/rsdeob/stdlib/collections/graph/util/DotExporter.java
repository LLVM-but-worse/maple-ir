package org.rsdeob.stdlib.collections.graph.util;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.TryCatchEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DotExporter<G extends FlowGraph<V, FlowEdge<V>>, V extends FastGraphVertex> {
	private static final String GRAPHVIZ_DOT_PATH = "dot\\dot.exe";
	private static final File GRAPH_FOLDER = new File("cfg testing");

	/**
	 * Reserved colors for highlighting structures such as SuperNodes
	 */
	protected static final String[] HIGHLIGHT_COLOURS = new String[] {
			"aliceblue", "antiquewhite", "aquamarine", "brown1", "cadetblue1",
			"chocolate1", "cornflowerblue", "cyan", "darkgoldenrod1",
			"darkolivegreen4", "darkorchid1", "darksalmon",
			"deeppink", "deepskyblue1", "firebrick1", "gold1", "hotpink1", "khaki1",
			"mediumseagreen", "orangered", "mediumpurple1", "magenta", "plum1",
			"royalblue1", "slateblue3", "turquoise2", "yellow2"
	};

	public static final int OPT_DEEP = 1;
	public static final int OPT_HIDE_HANDLER_EDGES = 2;

	protected final G graph;
	protected final List<V> order;
	protected final String name;
	private final String fileExt;
	private final Map<V, String> highlight = new HashMap<>();

	public DotExporter(G graph, List<V> order, String name, String fileExt) {
		this.graph = graph;
		this.order = order;
		this.name = name;
		this.fileExt = fileExt;

		for (V b : graph.getEntries())
			highlight.put(b, "red");
	}

	public DotExporter(G graph, List<V> order, String name) {
		this(graph, order, name, "");
	}

	public void addHighlight(V v, String color) {
		highlight.put(v, color);
	}

	public void addHighlights(Map<V, String> highlights) {
		highlight.putAll(highlights);
	}

	public void output(int options) {
		String fileName = escapeFileName(getFileName()) + fileExt;
		BufferedWriter bw = null;

		try {
			File dotFile = new File(GRAPH_FOLDER, fileName + ".gv");
			if(dotFile.exists())
				dotFile.delete();

			bw = new BufferedWriter(new FileWriter(dotFile));
			bw.write(createGraphString(options));
			bw.close();

			File gv = new File(GRAPHVIZ_DOT_PATH);
			File imgFile = new File(GRAPH_FOLDER, fileName + ".png");
			if (imgFile.exists())
				imgFile.delete();
			System.out.println(gv.getAbsolutePath() + " " + "-Tpng" + " " + '"' + dotFile.getAbsolutePath() + '"' + " " + "-o" + " " + '"' + imgFile.getAbsolutePath() + '"');
			ProcessBuilder builder = new ProcessBuilder(gv.getAbsolutePath(), "-Tpng", '"' + dotFile.getAbsolutePath() + '"', "-o", '"' + imgFile.getAbsolutePath() + '"');
			Process process = builder.start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			System.err.println("Exception while exporting graph " + fileName + ":");
			e.printStackTrace();;
		} finally {
			if (bw != null) {
				try {
					bw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private String createGraphString(int options) {
		StringBuilder sb = new StringBuilder();
		sb.append("digraph \"");
		sb.append(name);
		sb.append("\" {").append(System.lineSeparator());

		for(V b : order) {
			if (!filterBlock(b))
				continue;
			sb.append(b.getId()).append(" ");
			sb.append("[shape=box, label=").append('"');
			sb.append(order.indexOf(b)).append(". ").append(b.getId());
			if((options & OPT_DEEP) == OPT_DEEP)
				printBlock(b, sb);
			sb.append('"');

			if (highlight.containsKey(b))
				sb.append(", style=filled, fillcolor=").append(highlight.get(b));

			sb.append("]\n");
		}

		for(V b : order) {
			if (!filterBlock(b))
				continue;
			for(FlowEdge<V> e : graph.getEdges(b)) {
				if ((options & OPT_HIDE_HANDLER_EDGES) == OPT_HIDE_HANDLER_EDGES && e instanceof TryCatchEdge)
					continue;
				sb.append("").append(e.src.getId()).append(" -> ").append(e.dst.getId()).append(" ");
				if ((options & OPT_DEEP) == OPT_DEEP)
					sb.append("[label=").append('"').append(e.toGraphString()).append('"').append("]");
				sb.append(";\n");
			}
		}

		sb.append("\n}");

		return sb.toString();
	}

	protected String getFileName() {
		return name;
	}

	protected abstract boolean filterBlock(V b);

	protected abstract void printBlock(V b, StringBuilder sb);

	// '\', '/', ':', '*', '?', '<', '>', '|'
	private static String escapeFileName(String name) {
		return name.replaceAll("[/\\:*?\"<>|]", "");
	}
}

/*public class DotExporter {

	private static String createFileName(MethodNode m) {
		return createFileName(m.owner.name + " " + m.name + " " + m.desc);
	}

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

	public static String toGraphString(ControlFlowGraph cfg, List<BasicBlock> blocks, ControlFlowGraphDeobfuscator.SuperNodeList svList) {
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
						if(colourindex >= HIGHLIGHT_COLOURS.length) {
							colourindex = 0;
						}
						colour = HIGHLIGHT_COLOURS[colourindex++];
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

	public static String toGraphString(String name, StatementGraph sg, CodeBody stmts) {
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
*/