package org.mapleir.deob.interproc.callgraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.interproc.callgraph.CallSiteSensitiveCallGraph.CallGraphEdge;
import org.mapleir.deob.interproc.callgraph.CallSiteSensitiveCallGraph.CallGraphNode;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.util.Worklist;
import org.mapleir.stdlib.util.Worklist.Worker;
import org.objectweb.asm.tree.MethodNode;

public class CallSiteSensitiveCallGraph extends FastDirectedGraph<CallGraphNode, CallGraphEdge> implements Worker<MethodNode> {
	
	private final AnalysisContext context;
	private final Map<MethodNode, CallReceiverNode> receiverCache;
	private final Worklist<MethodNode> worklist;
	
	public CallSiteSensitiveCallGraph(AnalysisContext context) {
		this.context = context;
		receiverCache = new HashMap<>();
		worklist = makeWorklist();
	}
	
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
	
	public void updateWorklist() {
		worklist.update();
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
		CallReceiverNode currentReceiverNode = getNode(n, false);
		
		ControlFlowGraph cfg = context.getIRCache().get(n);
		
		if(cfg == null) {
			return;
		}
		
		for(Stmt stmt : cfg.stmts()) {
			for(Expr e : stmt.enumerateOnlyChildren()) {
				if(e instanceof Invocation) {
					Invocation invoke = (Invocation) e;
					
					CallSiteNode thisCallSiteNode = new CallSiteNode(getNextNodeId(), invoke);
					addVertex(thisCallSiteNode);
					
					/* link the current receiver to this call site. */
					FunctionOwnershipEdge foe = new FunctionOwnershipEdge(currentReceiverNode, thisCallSiteNode);
					addEdge(currentReceiverNode, foe);
					
					Set<MethodNode> targets = invoke.resolveTargets(context.getInvocationResolver());
					
					for(MethodNode tgt : targets) {
						CallReceiverNode targetReceiverNode = getNode(tgt, true);
						
						/* link each target to the call site. */
						SiteInvocationEdge sie = new SiteInvocationEdge(thisCallSiteNode, targetReceiverNode);
						addEdge(thisCallSiteNode, sie);
					}
				}
			}
		}
	}
	
	private int getNextNodeId() {
		return size() + 1;
	}
	
	private CallReceiverNode makeNode(MethodNode m) {
		CallReceiverNode currentReceiverNode = new CallReceiverNode(getNextNodeId(), m);
		receiverCache.put(m, currentReceiverNode);
		addVertex(currentReceiverNode);
		return currentReceiverNode;
	}

	public CallReceiverNode getNode(MethodNode m) {
		return receiverCache.get(m);
	}
	
	/* either get a pre built node or
	 * make one and add it to the worklist. */
	protected CallReceiverNode getNode(MethodNode m, boolean queue) {
		if(receiverCache.containsKey(m)) {
			return receiverCache.get(m);
		} else {
			CallReceiverNode currentReceiverNode = makeNode(m);
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

	public static class CallReceiverNode extends CallGraphNode {

		private final MethodNode method;
		
		public CallReceiverNode(int id, MethodNode method) {
			super(id);
			this.method = method;
		}

		@Override
		public String toString() {
			return method.toString();
		}
	}

	public static class CallSiteNode extends CallGraphNode {

		private final Expr invoke;
		
		public CallSiteNode(int id, Expr invoke) {
			super(id);
			this.invoke = invoke;
		}

		@Override
		public String toString() {
			MethodNode m = invoke.getBlock().getGraph().getMethod();
			return m.owner + "." + m.name + "@" + invoke.getBlock().indexOf(invoke.getRootParent()) + ":" + invoke.getParent().indexOf(invoke);
		}
	}

	public abstract static class CallGraphNode implements FastGraphVertex {

		private final int id;

		public CallGraphNode(int id) {
			this.id = id;
		}

		@Override
		public String getId() {
			return Integer.toString(id);
		}

		@Override
		public int getNumericId() {
			return id;
		}

		@Override
		public abstract String toString();
	}

	public abstract static class CallGraphEdge extends FastGraphEdge<CallGraphNode> {
		public CallGraphEdge(CallGraphNode src, CallGraphNode dst) {
			super(src, dst);
		}

		public abstract boolean canClone(CallGraphNode src, CallGraphNode dst);

		public abstract CallGraphEdge clone(CallGraphNode src, CallGraphNode dst);
	}

	public static class FunctionOwnershipEdge extends CallGraphEdge {
		public FunctionOwnershipEdge(CallReceiverNode src, CallSiteNode dst) {
			super(src, dst);
		}

		@Override
		public boolean canClone(CallGraphNode src, CallGraphNode dst) {
			return src instanceof CallReceiverNode && dst instanceof CallSiteNode;
		}

		@Override
		public CallGraphEdge clone(CallGraphNode src, CallGraphNode dst) {
			return new FunctionOwnershipEdge((CallReceiverNode) src, (CallSiteNode) dst);
		}
	}
	
	public static class SiteInvocationEdge extends CallGraphEdge {

		public SiteInvocationEdge(CallSiteNode src, CallReceiverNode dst) {
			super(src, dst);
		}

		@Override
		public boolean canClone(CallGraphNode src, CallGraphNode dst) {
			return src instanceof CallSiteNode && dst instanceof CallReceiverNode;
		}

		@Override
		public CallGraphEdge clone(CallGraphNode src, CallGraphNode dst) {
			return new SiteInvocationEdge((CallSiteNode) src, (CallReceiverNode) dst);
		}
		
	}

	@Override
	public boolean excavate(CallGraphNode n) {
		throw new UnsupportedOperationException();
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