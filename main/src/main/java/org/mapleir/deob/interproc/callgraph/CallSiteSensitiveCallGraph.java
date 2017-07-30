package org.mapleir.deob.interproc.callgraph;

import org.mapleir.deob.interproc.callgraph.CallGraphNode.CallReceiverNode;
import org.mapleir.deob.interproc.callgraph.CallGraphNode.CallSiteNode;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CallSiteSensitiveCallGraph extends FastDirectedGraph<CallGraphNode, CallGraphEdge> {
	
	private final Map<MethodNode, CallReceiverNode> receiverCache;
	
	public CallSiteSensitiveCallGraph() {
		receiverCache = new HashMap<>();
	}
	
	// Copy constructor.
	protected CallSiteSensitiveCallGraph(CallSiteSensitiveCallGraph g) {
		super(g);
		
		receiverCache = new HashMap<>(g.receiverCache);
	}
	
	// Used for builder.
	private int getNextNodeId() {
		return size() + 1;
	}
	
	// Used for builder.
	CallReceiverNode addMethod(MethodNode m) {
		CallReceiverNode currentReceiverNode = new CallReceiverNode(getNextNodeId(), m);
		receiverCache.put(m, currentReceiverNode);
		addVertex(currentReceiverNode);
		return currentReceiverNode;
	}
	
	// Used for builder.
	CallSiteNode addInvocation(Invocation invoke) {
		CallSiteNode thisCallSiteNode = new CallSiteNode(getNextNodeId(), invoke);
		addVertex(thisCallSiteNode);
		return thisCallSiteNode;
	}
	
	public CallReceiverNode getNode(MethodNode m) {
		return receiverCache.get(m);
	}
	
	public boolean containsMethod(MethodNode m) {
		return receiverCache.containsKey(m);
	}
	
	/*private int encodeId(Expr e) {
		BasicBlock block = e.getBlock();
		
		int blockId = block.getNumericId();
		int stmtId = block.indexOf(e.getRootParent());
		int exprId = e.getParent().indexOf(e);
		
		if(intBitLen(blockId) > 16) {
			throw new UnsupportedOperationException(String.format("Block id too large: %d (id: %s)", blockId, block.getId()));
		} else if(intBitLen(stmtId) > 13) {
			throw new UnsupportedOperationException(String.format("Stmt id too large: %d (blocksize:%d)", stmtId, block.size()));
		} else if(intBitLen(exprId) > 3) {
			throw new UnsupportedOperationException(String.format("Expr id too large: %d (parent child count:%d)", exprId, e.getParent().size()));
		}
		
		return ((blockId << 16) |(stmtId << 3)) | exprId;
	}
	private int intBitLen(int val) {
		return Integer.SIZE - Integer.numberOfLeadingZeros(val);
	}*/

	@Override
	public Set<CallGraphEdge> createSet() {
		return new LinkedHashSet<>();
	}

	@Override
	public Set<CallGraphEdge> createSet(Set<CallGraphEdge> set) {
		return new LinkedHashSet<>(set);
	}
	
	@Override
	public boolean excavate(CallGraphNode n) {
		throw new UnsupportedOperationException("Induced subgraph not supported.");
	}

	@Override
	public boolean jam(CallGraphNode pred, CallGraphNode succ, CallGraphNode n) {
		throw new UnsupportedOperationException("Edge splitting not supported.");
	}

	@Override
	public CallGraphEdge clone(CallGraphEdge edge, CallGraphNode oldN, CallGraphNode newN) {
		CallGraphNode src = edge.src;
		CallGraphNode dst = edge.dst;

		if (src == oldN) {
			src = newN;
		}

		if (dst == oldN) {
			dst = newN;
		}

		if (edge.canClone(src, dst)) {
			return edge.clone(src, dst);
		} else {
			throw new UnsupportedOperationException(String.format("Cannot clone %s for %s and %s", edge, src, dst));
		}
	}

	@Override
	public CallGraphEdge invert(CallGraphEdge edge) {
		CallGraphNode src = edge.dst;
		CallGraphNode dst = edge.src;

		if (edge.canClone(src, dst)) {
			return edge.clone(src, dst);
		} else {
			throw new UnsupportedOperationException(String.format("Cannot invert %s", edge));
		}
	}

	@Override
	public CallSiteSensitiveCallGraph copy() {
		return new CallSiteSensitiveCallGraph(this);
	}
}
