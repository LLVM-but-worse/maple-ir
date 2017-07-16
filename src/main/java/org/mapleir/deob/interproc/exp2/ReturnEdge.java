package org.mapleir.deob.interproc.exp2;

import org.mapleir.ir.cfg.edge.FlowEdge;

public class ReturnEdge extends FlowEdge<CallGraphBlock> {

	
	public ReturnEdge(CallGraphBlock src, CallGraphBlock dst) {
		super(9, src, dst);
	}

	@Override
	public String toGraphString() {
		return String.format("Return #%s -> #%s", src.getId(), dst.getId());
	}

	@Override
	public String toString() {
		return String.format("Return #%s -> #%s", src.getId(), dst.getId());
	}

	@Override
	public String toInverseString() {
		return String.format("Return #%s <- #%s", dst.getId(), src.getId());
	}

	@Override
	public FlowEdge<CallGraphBlock> clone(CallGraphBlock src, CallGraphBlock dst) {
		return new CallEdge(src, dst);
	}
}