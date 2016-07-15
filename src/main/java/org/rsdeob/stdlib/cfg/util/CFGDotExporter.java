package org.rsdeob.stdlib.cfg.util;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.collections.graph.util.DotExporter;

import java.util.HashMap;
import java.util.Map;

public class CFGDotExporter extends DotExporter<ControlFlowGraph, BasicBlock> {
	public CFGDotExporter(ControlFlowGraph cfg, String fileExt, Map<BasicBlock, String> highlight) {
		super(cfg, fileExt);
		addHighlights(highlight);
	}

	public CFGDotExporter(ControlFlowGraph cfg, String fileExt, ControlFlowGraphDeobfuscator.SuperNodeList svList) {
		this(cfg, fileExt, highlightComputeSuperNodeList(svList));
	}

	public CFGDotExporter(ControlFlowGraph cfg, String fileExt, Map<BasicBlock, String> highlight, ControlFlowGraphDeobfuscator.SuperNodeList svList) {
		this(cfg, fileExt, highlight);
		addHighlights(highlightComputeSuperNodeList(svList));
	}

	private static Map<BasicBlock, String> highlightComputeSuperNodeList(ControlFlowGraphDeobfuscator.SuperNodeList svList) {
		Map<BasicBlock, String> highlight = new HashMap<>();
		for (BasicBlock v : svList.entryNodes)
			highlight.put(v, "green");
		for (int i = 0; i < svList.size(); i++)
			for (BasicBlock v : svList.get(i).vertices)
				highlight.put(v, HIGHLIGHT_COLOURS[i]);
		return highlight;
	}

	@Override
	protected String createGraphString() {

	}

	@Override
	protected String getFileName() {

	}
}
