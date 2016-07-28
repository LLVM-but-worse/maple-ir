package org.mapleir.stdlib.collections.graph.dot.impl;

import org.mapleir.stdlib.collections.graph.dot.DotPropertyDecorator;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.stdlib.ir.gen.interference.ColourableNode;
import org.mapleir.stdlib.ir.gen.interference.InterferenceEdge;
import org.mapleir.stdlib.ir.gen.interference.InterferenceGraph;

import java.util.Map;

public class InterferenceGraphDecorator implements DotPropertyDecorator<InterferenceGraph, ColourableNode, InterferenceEdge> {
	@Override
	public void decorateNodeProperties(InterferenceGraph g, ColourableNode n, Map<String, Object> nprops) {
		nprops.put("shape", "box");
		nprops.put("style", "filled");
		nprops.put("fillcolor", GraphUtils.HIGHLIGHT_COLOURS[n.getColour()]);
		nprops.put("label", n.getLocal().toString());
	}
}