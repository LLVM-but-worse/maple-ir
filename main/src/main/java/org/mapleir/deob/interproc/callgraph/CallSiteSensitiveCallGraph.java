package org.mapleir.deob.interproc.callgraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.util.Worklist;
import org.mapleir.stdlib.util.Worklist.Worker;
import org.objectweb.asm.tree.MethodNode;

public class CallSiteSensitiveCallGraph extends FastDirectedGraph<CallGraphNode, CallGraphEdge> implements Worker<MethodNode> {
	
	private final AnalysisContext context;
	private final Map<MethodNode, CallGraphNode.CallReceiverNode> receiverCache;
	private final Worklist<MethodNode> worklist;
	
	public CallSiteSensitiveCallGraph(AnalysisContext context) {
		this.context = context;
		receiverCache = new HashMap<>();
		worklist = makeWorklist();
	}
	
	// Copy constructor.
	protected CallSiteSensitiveCallGraph(CallSiteSensitiveCallGraph g) {
		super(g);
		
		if(g.worklist.pending() != 0) {
			throw new IllegalStateException();
		}
		
		context = g.context;
		receiverCache = new HashMap<>(g.receiverCache);
		worklist = makeWorklist();
	}
	
	public Worklist<MethodNode> getWorklist() {
		return worklist;
	}
	
	public void processWorklist() {
		worklist.processQueue();
	}
	
	protected Worklist<MethodNode> makeWorklist() {
		Worklist<MethodNode> worklist = new Worklist<>();
		worklist.addWorker(this);
		return worklist;
	}

	@Override
	public void process(Worklist<MethodNode> worklist, MethodNode n) {
		if(worklist != this.worklist) {
			throw new IllegalStateException();
		}
		
		if(worklist.hasProcessed(n)) {
			throw new UnsupportedOperationException(String.format("Already processed %s", n));
		}
		
		/* this is not the same as getNode*/
		CallGraphNode.CallReceiverNode currentReceiverNode = createNode(n, false);
		
		ControlFlowGraph cfg = context.getIRCache().get(n);
		
		if(cfg == null) {
			return;
		}
		
		for(Stmt stmt : cfg.stmts()) {
			for(Expr e : stmt.enumerateOnlyChildren()) {
				if(e instanceof Invocation) {
					Invocation invoke = (Invocation) e;
					
					CallGraphNode.CallSiteNode thisCallSiteNode = new CallGraphNode.CallSiteNode(getNextNodeId(), invoke);
					addVertex(thisCallSiteNode);
					
					/* link the current receiver to this call site. */
					CallGraphEdge.FunctionOwnershipEdge foe = new CallGraphEdge.FunctionOwnershipEdge(currentReceiverNode, thisCallSiteNode);
					addEdge(currentReceiverNode, foe);
					
					Set<MethodNode> targets = invoke.resolveTargets(context.getInvocationResolver());
					
					for(MethodNode target : targets) {
						CallGraphNode.CallReceiverNode targetReceiverNode = createNode(target, true);
						
						/* link each target to the call site. */
						CallGraphEdge.SiteInvocationEdge sie = new CallGraphEdge.SiteInvocationEdge(thisCallSiteNode, targetReceiverNode);
						addEdge(thisCallSiteNode, sie);
					}
				}
			}
		}
	}
	
	private int getNextNodeId() {
		return size() + 1;
	}
	
	private CallGraphNode.CallReceiverNode makeNode(MethodNode m) {
		CallGraphNode.CallReceiverNode currentReceiverNode = new CallGraphNode.CallReceiverNode(getNextNodeId(), m);
		receiverCache.put(m, currentReceiverNode);
		addVertex(currentReceiverNode);
		return currentReceiverNode;
	}

	public CallGraphNode.CallReceiverNode getNode(MethodNode m) {
		return receiverCache.get(m);
	}
	
	/* either get a pre built node or
	 * make one and add it to the worklist. */
	protected CallGraphNode.CallReceiverNode createNode(MethodNode m, boolean queue) {
		if(receiverCache.containsKey(m)) {
			return receiverCache.get(m);
		} else {
			CallGraphNode.CallReceiverNode currentReceiverNode = makeNode(m);
			if(queue) {
				worklist.queueData(m);
			}
			return currentReceiverNode;
		}
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
