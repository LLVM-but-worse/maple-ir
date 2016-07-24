package org.mapleir.stdlib.collections.graph.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;

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
	
	public static final int LABEL_START = 0;
	public static final int LABEL_END = 1;

	protected final G graph;
	protected final List<V> order;
	protected final String name;
	private final String fileExt;
	private final Map<V, String> highlight = new HashMap<>();
	private final NullPermeableHashMap<V, Set<String>> startLabels = new NullPermeableHashMap<>(HashSet::new);
	private final NullPermeableHashMap<V, Set<String>> endLabels = new NullPermeableHashMap<>(HashSet::new);
	
	private String fontName;
	private double fontSize;

	public DotExporter(G graph, List<V> order, String name, String fileExt) {
		this.graph = graph;
		this.order = order;
		this.name = name;
		this.fileExt = fileExt;
		fontName = "consolas bold";
		fontSize = 8.0;

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

	public void addLabel(V v, String label, int position) {
		(position == LABEL_START? startLabels : endLabels).getNonNull(v).add(label.replaceAll("\"", "\\\\\""));
	}
	
	public void setFont(String fontName) {
		this.fontName = fontName;
	}
	
	public void setFontSize(double newSize) {
		fontSize = newSize;
	}

	public void export(int options) {
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
		sb.append("graph [fontname = \"").append(fontName).append("\", fontsize = ").append(fontSize).append(", dpi=200.0").append("];\n");
		sb.append("node [fontname = \"").append(fontName).append("\", fontsize = ").append(fontSize).append(", dpi=200.0").append("];\n");
		sb.append("edge [fontname = \"").append(fontName).append("\", fontsize = ").append(fontSize).append(", dpi=200.0").append("];\n");

		for(V b : graph.vertices()) {
			if (!filterBlock(b))
				continue;
			sb.append(b.getId()).append(" ");
			sb.append("[shape=box, labeljust=\"l\", label=").append('"');
			sb.append(order.indexOf(b)).append(". ").append(b.getId());
			for (String label : startLabels.getNonNull(b))
				sb.append("\\l// ").append(label);
			if((options & OPT_DEEP) == OPT_DEEP)
				printBlock(b, sb);
			for (String label : endLabels.getNonNull(b))
				sb.append("// ").append(label).append("\\l");
			sb.append('"');

			if (highlight.containsKey(b))
				sb.append(", style=filled, fillcolor=").append(highlight.get(b));

			sb.append("]\n");
		}

		for(V b : graph.vertices()) {
			if (!filterBlock(b))
				continue;
			for(FlowEdge<V> e : graph.getEdges(b)) {
				if ((options & OPT_HIDE_HANDLER_EDGES) == OPT_HIDE_HANDLER_EDGES && e instanceof TryCatchEdge)
					continue;
				sb.append("").append(e.src.getId()).append(" -> ").append(e.dst.getId()).append(" ");
				if ((options & OPT_DEEP) == OPT_DEEP) {
					sb.append("[label=").append('"');
					printEdge(e, sb);
					sb.append('"').append("]");
				}
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

	protected abstract void printEdge(FlowEdge<V> b, StringBuilder sb);

	// '\', '/', ':', '*', '?', '<', '>', '|'
	private static String escapeFileName(String name) {
		return name.replaceAll("[/\\:*?\"<>|]", "");
	}
}