package org.mapleir.deob.interproc.exp2;

import org.mapleir.flowgraph.edges.AbstractFlowEdge;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.ir.cfg.BasicBlock;

public class CallGraphBasicBlockBridgeEdge implements FlowEdge<CallGraphBlock> {
	private final FlowEdge<BasicBlock> basicBlockEdge;
	protected final CallGraphBlock src, dst;

	public CallGraphBasicBlockBridgeEdge(FlowEdge<BasicBlock> basicBlockEdge, CallGraphBlock src, CallGraphBlock dst) {
		this.basicBlockEdge = basicBlockEdge;
		this.src = src;
		this.dst = dst;
	}

	@Override
	public int getType() {
		return basicBlockEdge.getType();
	}

	@Override
	public String toGraphString() {
		return basicBlockEdge.toGraphString();
	}

	@Override
	public String toString() {
		return basicBlockEdge.toString();
	}

	@Override
	public String toInverseString() {
		return basicBlockEdge.toInverseString();
	}

	@Override
	public FlowEdge<CallGraphBlock> clone(CallGraphBlock src, CallGraphBlock dst) {
		if(!(src instanceof ConcreteCallGraphBlock && dst instanceof ConcreteCallGraphBlock)) {
			throw new UnsupportedOperationException(src + " : " + dst);
		}
		
		ConcreteCallGraphBlock srcBlockNode = (ConcreteCallGraphBlock) src;
		ConcreteCallGraphBlock dstBlockNode = (ConcreteCallGraphBlock) dst;
		
		FlowEdge<BasicBlock> clonedEdge = basicBlockEdge.clone(srcBlockNode.block, dstBlockNode.block);
		
		return new CallGraphBasicBlockBridgeEdge(clonedEdge, src, dst);
	}

	@Override
	public CallGraphBlock src() {
		return src;
	}

	@Override
	public CallGraphBlock dst() {
		return dst;
	}
}
