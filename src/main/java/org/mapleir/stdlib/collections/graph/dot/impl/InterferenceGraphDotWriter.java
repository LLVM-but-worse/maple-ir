package org.mapleir.stdlib.collections.graph.dot.impl;

import java.util.HashMap;
import java.util.Map;

import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.stdlib.ir.gen.interference.ColourableNode;
import org.mapleir.stdlib.ir.gen.interference.InterferenceEdge;
import org.mapleir.stdlib.ir.gen.interference.InterferenceGraph;

public class InterferenceGraphDotWriter extends DotWriter<InterferenceGraph, ColourableNode, InterferenceEdge>{

	public InterferenceGraphDotWriter(DotConfiguration<InterferenceGraph, ColourableNode, InterferenceEdge> config, InterferenceGraph graph) {
		super(config, graph);
	}

	@Override
	public Map<String, Object> getNodeProperties(ColourableNode n) {
		Map<String, Object> map = new HashMap<>();
		map.put("shape", "box");
		map.put("style", "filled");
		map.put("fillcolor", GraphUtils.HIGHLIGHT_COLOURS[n.getColour()]);
		map.put("label", n.getLocal().toString());
		return map;
	}
}