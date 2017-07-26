package org.mapleir.deob.interproc.exp2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.context.IRCache;
import org.mapleir.context.InvocationResolver;
import org.mapleir.deob.interproc.exp2.context.CallingContext;
import org.mapleir.flowgraph.edges.DummyEdge;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.stdlib.util.Worklist;
import org.mapleir.stdlib.util.Worklist.Worker;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

public class BlockCallGraphBuilder implements Worker<MethodNode> {
	
	private final AnalysisContext cxt;
	private final Map<MethodNode, LibraryStubCallGraphBlock> generatedStubs;
	private final Map<BasicBlock, ConcreteCallGraphBlock> concreteBlockMap;
	private int idCounter;
	private final Set<ControlFlowGraph> initialised;
	
	public final BlockCallGraph callGraph;
	private final Worklist<MethodNode> worklist;
	
	public BlockCallGraphBuilder(AnalysisContext cxt) {
		this.cxt = cxt;
		callGraph = new BlockCallGraph();
		
		generatedStubs = new HashMap<>();
		concreteBlockMap = new HashMap<>();
		initialised = new HashSet<>();
		
		worklist = new Worklist<>();
		worklist.addWorker(this);
	}
	
	public void init() {
		worklist.queueData(cxt.getApplicationContext().getEntryPoints());
		worklist.update();
	}
	
	public LibraryStubCallGraphBlock getLibraryStubNode(MethodNode method) {
		if(generatedStubs.containsKey(method)) {
			return generatedStubs.get(method);
		} else {
			LibraryStubCallGraphBlock libraryStubGraphBlock = new LibraryStubCallGraphBlock(method, ++idCounter);
			generatedStubs.put(method, libraryStubGraphBlock);
			return libraryStubGraphBlock;
		}
	}
	
	public ConcreteCallGraphBlock getConcreteBlockNode(BasicBlock basicBlock) {
		if(concreteBlockMap.containsKey(basicBlock)) {
			return concreteBlockMap.get(basicBlock);
		} else {
			ConcreteCallGraphBlock concreteCallGraphBlock = new ConcreteCallGraphBlock(basicBlock, ++idCounter);
			concreteBlockMap.put(basicBlock, concreteCallGraphBlock);
			return concreteCallGraphBlock;
		}
	}

	@Override
	public void process(Worklist<MethodNode> worklist, MethodNode callerMethod) {
		if(worklist != this.worklist) {
			throw new IllegalStateException();
		}
		
		if(worklist.hasProcessed(callerMethod)) {
			throw new UnsupportedOperationException(String.format("Already processed %s", callerMethod));
		}
		
		ControlFlowGraph cfg = cxt.getIRCache().get(callerMethod);
		
		if(cfg == null) {
			return;
		}
		
		initControlFlowGraphForIPCallGraph(cfg);
		
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		for(BasicBlock currentBlock : cfg.vertices()) {
			for(Stmt stmt : currentBlock) {
				for(Expr expr : stmt.enumerateOnlyChildren()) {
					if(expr instanceof Invocation) {
						Invocation invoke = (Invocation) expr;
						
						Set<MethodNode> targets = invoke.resolveTargets(resolver);

						if(targets.size() != 0) {
							for(MethodNode targetMethod : targets) {
								worklist.queueData(targetMethod);
								
								/* initialise if we have to so we can
								 * link it now (before it's officially
								 * processed). */
								ControlFlowGraph targetMethodCfg = cxt.getIRCache().get(targetMethod);
								if(targetMethodCfg != null) {
									initControlFlowGraphForIPCallGraph(targetMethodCfg);
								}
								
								linkConcreteInvocation(callerMethod, targetMethod, getConcreteBlockNode(currentBlock));
							}
						} else {
							String owner = invoke.getOwner(), name = invoke.getName(), desc = invoke.getDesc();
							//FIXME
							if(owner.equals("<init>")) {
								System.err.printf("(warn): can't resolve constructor: %s.<init> %s.%n", owner, desc);
							} else if(!invoke.isStatic()) {
								if(!owner.contains("java")) {
									System.err.printf("(warn): can't resolve vcall: %s.%s %s.%n", owner, name, desc);
									System.err.println("  call from " + callerMethod);
									System.err.println(cxt.getApplication().findClassNode(owner).methods);
								}
							}
						}
					}
				}
			}
		}
	}
	
	private void linkConcreteInvocation(MethodNode caller, MethodNode callee, ConcreteCallGraphBlock callerBlock) {
//		System.out.println(caller + " -> " + callee);
		IRCache irCache = cxt.getIRCache();
		ControlFlowGraph callerControlFlowGraph = irCache.get(caller);
		ControlFlowGraph calleeControlFlowGraph = irCache.get(callee);
		
		Set<CallGraphBlock> returnSites = new HashSet<>();
		
		CallingContext callingContext = new CallingContext();
		
		if(calleeControlFlowGraph != null) {
			CallEdge outgoingCallEdge = new CallEdge(callerBlock, getConcreteBlockNode(calleeControlFlowGraph.getEntries().iterator().next()), callingContext);
			callGraph.addEdge(callerBlock, outgoingCallEdge);
			
			for(Stmt stmt : calleeControlFlowGraph.stmts()) {
				if(stmt.getOpcode() == Opcode.RETURN) {
					BasicBlock returnBlock = stmt.getBlock();
					CallGraphBlock graphNode = getConcreteBlockNode(returnBlock);
					
					returnSites.add(graphNode);
				}
			}
		} else {
			LibraryStubCallGraphBlock libraryNode = getLibraryStubNode(callee);
			
			CallEdge outgoingCallEdge = new CallEdge(callerBlock, libraryNode, callingContext);
			callGraph.addEdge(callerBlock, outgoingCallEdge);
			
			returnSites.add(libraryNode);
		}
		
		if(returnSites.size() != 1) {
			throw new UnsupportedOperationException();
		}
		
		/* The ControlFlowGraph successors of the caller block
		 * are the target sites for the ReturnCallEdge from the
		 * return sites of the callee. */
		for(FlowEdge<BasicBlock> e : callerControlFlowGraph.getEdges(callerBlock.block)) {
			ConcreteCallGraphBlock returnTargetBlock = concreteBlockMap.get(e.dst);
			if(returnTargetBlock == null) {
				throw new IllegalStateException(String.format("No block for dst:> %s", e.toString()));
			}
			
			for(CallGraphBlock returnSiteBlock : returnSites) {
				callGraph.addEdge(returnSiteBlock, new ReturnEdge(returnSiteBlock, returnTargetBlock, callingContext));
			}
		}
	}
	
	private void initControlFlowGraphForIPCallGraph(ControlFlowGraph cfg) {
		if(initialised.contains(cfg)) {
			return;
		}
		initialised.add(cfg);
		
		/* we need to generate the wrapper for the
		 * dummy block before we generate the wrappers
		 * for the real method blocks, but i want to 
		 * preserve the id ordering of the entire cfg
		 * with the dummy as the last block, so the
		 * id is precomputed. */
		
		BasicBlock dummyExitBasicBlock = new ReturnBlock(cfg, cfg.size() + 1, new LabelNode());
		ConcreteCallGraphBlock dummyExitCallGraphBlock = new ConcreteCallGraphBlock(dummyExitBasicBlock, idCounter + cfg.vertices().size() + 1);
		concreteBlockMap.put(dummyExitBasicBlock, dummyExitCallGraphBlock);
		
		cfg.addVertex(dummyExitBasicBlock);

		for(BasicBlock b : cfg.vertices()) {
			if(b != dummyExitBasicBlock && cfg.getEdges(b).size() == 0) {
				cfg.addEdge(b, new DummyEdge<>(b, dummyExitBasicBlock));
			}
		}
		
		for(BasicBlock b : cfg.vertices()) {
			callGraph.addVertex(getConcreteBlockNode(b));
		}
		
		callGraph.addVertex(dummyExitCallGraphBlock);
		

		for(BasicBlock b : cfg.vertices()) {
			CallGraphBlock graphNode = getConcreteBlockNode(b);
			
			for(FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				CallGraphBasicBlockBridgeEdge newEdge = new CallGraphBasicBlockBridgeEdge(e, graphNode, getConcreteBlockNode(e.dst));
				callGraph.addEdge(graphNode, newEdge);
			}
		}
	}
	
	public static class ReturnBlock extends BasicBlock {
		public ReturnBlock(ControlFlowGraph cfg, int id, LabelNode label) {
			super(cfg, id, label);
		}
		
		@Override
		public String toString() {
			return "RETURN_TARGET";
		}
	}
}