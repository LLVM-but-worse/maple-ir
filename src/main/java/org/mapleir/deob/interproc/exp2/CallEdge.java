package org.mapleir.deob.interproc.exp2;

import org.mapleir.ir.cfg.edge.FlowEdge;

public class CallEdge extends FlowEdge<CallGraphBlock> {
	
	public CallEdge(CallGraphBlock src, CallGraphBlock dst) {
		super(8, src, dst);
	}

	@Override
	public String toGraphString() {
		return String.format("Call #%s -> #%s", src.getId(), dst.getId());
	}

	@Override
	public String toString() {
		return String.format("Call #%s -> #%s", src.getId(), dst.getId());
	}

	@Override
	public String toInverseString() {
		return String.format("Call #%s <- #%s", dst.getId(), src.getId());
	}

	@Override
	public FlowEdge<CallGraphBlock> clone(CallGraphBlock src, CallGraphBlock dst) {
		return new CallEdge(src, dst);
	}
}