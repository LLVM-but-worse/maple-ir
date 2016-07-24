package org.mapleir.stdlib.cfg.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.graph.util.DotExporter;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;

public class CFGDotExporter extends DotExporter<ControlFlowGraph, BasicBlock> {
	public CFGDotExporter(ControlFlowGraph cfg, List<BasicBlock> order, String name, String fileExt) {
		super(cfg, order, name, fileExt);
	}

	public CFGDotExporter(ControlFlowGraph cfg, List<BasicBlock> order, String name, String fileExt, ControlFlowGraphDeobfuscator.SuperNodeList svList) {
		this(cfg, order, name, fileExt);
		addHighlights(highlightComputeSuperNodeList(svList));
	}

	private static Map<BasicBlock, String> highlightComputeSuperNodeList(ControlFlowGraphDeobfuscator.SuperNodeList svList) {
		Map<BasicBlock, String> highlight = new HashMap<>();
		for (BasicBlock v : svList.entryNodes)
			highlight.put(v, "green");
		for (int i = 0; i < svList.size(); i++)
			for (BasicBlock v : svList.get(i).vertices)
				highlight.put(v, HIGHLIGHT_COLOURS[i % HIGHLIGHT_COLOURS.length]);
		return highlight;
	}

	@Override
	protected boolean filterBlock(BasicBlock b) {
		return !b.isDummy();
	}

	@Override
	protected void printBlock(BasicBlock b, StringBuilder sb) {
		sb.append("\\l");
		StringBuilder buf = new StringBuilder();
		GraphUtils.printBlock(graph, graph.vertices(), buf, b, 0, false);
		sb.append(buf.toString().replaceAll("\n", "\\\\l"));
	}

	@Override
	protected void printEdge(FlowEdge<BasicBlock> e, StringBuilder sb) {
		sb.append(e.toGraphString());
	}
}