package org.mapleir.ir.analysis.dataflow;

import java.util.Set;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public interface DataFlowProvider<N extends FastGraphVertex, E extends FlowEdge<N>> {

	Set<E> getSuccessors(N n);
	
	Set<E> getPredecessors(N n);
	
	Set<N> getNodes();
	
	Set<N> getHandlers();
	
	Set<N> getHeads();
	
	Set<N> getTails();
}