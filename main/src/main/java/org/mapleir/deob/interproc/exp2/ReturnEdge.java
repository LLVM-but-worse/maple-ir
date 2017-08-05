package org.mapleir.deob.interproc.exp2;

import org.mapleir.deob.interproc.exp2.context.CallingContext;
import org.mapleir.flowgraph.edges.AbstractFlowEdge;
import org.mapleir.flowgraph.edges.FlowEdge;

public class ReturnEdge extends AbstractFlowEdge<CallGraphBlock> {

	public static final int TYPE_ID = 9;
	
	private final CallingContext context;
	
	public ReturnEdge(CallGraphBlock src, CallGraphBlock dst, CallingContext context) {
		super(TYPE_ID, src, dst);
		
		this.context = context;
	}
	
	public CallingContext getContext() {
		return context;
	}

	@Override
	public String toGraphString() {
		return String.format("Return #%s -> #%s", src.getId(), dst.getId());
	}

	@Override
	public String toString() {
		return String.format("Return %s -> %s", src.toString(), dst.toString());
	}

	@Override
	public String toInverseString() {
		return String.format("Return #%s <- #%s", dst.getId(), src.getId());
	}

	@Override
	public FlowEdge<CallGraphBlock> clone(CallGraphBlock src, CallGraphBlock dst) {
		return new ReturnEdge(src, dst, context);
	}
}
