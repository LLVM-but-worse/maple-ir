package org.rsdeob.stdlib.collections.graph.util;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		sb.append("\n");
		GraphUtils.printBlock(graph, graph.vertices(), sb, b, 0, false);
	}

	@Override
	protected void printEdge(FlowEdge<BasicBlock> e, StringBuilder sb) {
		sb.append(e.toGraphString());
	}
}
