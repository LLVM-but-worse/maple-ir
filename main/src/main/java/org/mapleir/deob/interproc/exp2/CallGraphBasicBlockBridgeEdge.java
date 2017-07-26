package org.mapleir.deob.interproc.exp2;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.edge.FlowEdge;

public class CallGraphBasicBlockBridgeEdge extends FlowEdge<CallGraphBlock> {

	private final FlowEdge<BasicBlock> basicBlockEdge;
	
	public CallGraphBasicBlockBridgeEdge(FlowEdge<BasicBlock> basicBlockEdge, CallGraphBlock src, CallGraphBlock dst) {
		super(basicBlockEdge.getType(), src, dst);
		
		this.basicBlockEdge = basicBlockEdge;
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
}