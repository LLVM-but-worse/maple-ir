package org.mapleir.deob.interproc.exp2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.context.IRCache;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.stdlib.util.InvocationResolver;
import org.objectweb.asm.tree.MethodNode;

public class BlockCallGraphBuilder {
	
	private final AnalysisContext cxt;
	private final Set<MethodNode> visitedMethods;
	private final Map<MethodNode, LibraryStubCallGraphBlock> generatedStubs;
	private final Map<BasicBlock, ConcreteCallGraphBlock> concreteBlockMap;
	private int idCounter;
	
	public final BlockCallGraph callGraph;
	
	public BlockCallGraphBuilder(AnalysisContext cxt) {
		this.cxt = cxt;
		callGraph = new BlockCallGraph();
		visitedMethods = new HashSet<>();
		
		generatedStubs = new HashMap<>();
		concreteBlockMap = new HashMap<>();
	}
	
	private LibraryStubCallGraphBlock getLibraryStubNode(MethodNode method) {
		if(generatedStubs.containsKey(method)) {
			return generatedStubs.get(method);
		} else {
			LibraryStubCallGraphBlock libraryStubGraphBlock = new LibraryStubCallGraphBlock(method, ++idCounter);
			generatedStubs.put(method, libraryStubGraphBlock);
			return libraryStubGraphBlock;
		}
	}
	
	private ConcreteCallGraphBlock getConcreteBlockNode(BasicBlock basicBlock) {
		if(concreteBlockMap.containsKey(basicBlock)) {
			return concreteBlockMap.get(basicBlock);
		} else {
			ConcreteCallGraphBlock concreteCallGraphBlock = new ConcreteCallGraphBlock(basicBlock, ++idCounter);
			concreteBlockMap.put(basicBlock, concreteCallGraphBlock);
			return concreteCallGraphBlock;
		}
	}
	
	public void visit(MethodNode callerMethod) {
		ControlFlowGraph cfg = cxt.getIRCache().get(callerMethod);
		
		if(cfg == null) {
			return;
		}
		
		if(!visitedMethods.contains(callerMethod)) {
			visitedMethods.add(callerMethod);
			
			initControlFlowGraphForIPCallGraph(cfg);
		}
		
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		for(BasicBlock currentBlock : cfg.vertices()) {
			for(Stmt stmt : currentBlock) {
				for(Expr expr : stmt.enumerateOnlyChildren()) {
					if(expr instanceof Invocation) {
						Invocation invoke = (Invocation) expr;
						
						Set<MethodNode> targets = invoke.resolveTargets(resolver);

						if(targets.size() != 0) {
							for(MethodNode targetMethod : targets) {
								visit(targetMethod);
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
	
	private void linkConcreteInvocation(MethodNode caller, MethodNode callee, CallGraphBlock callerBlock) {
		System.out.println(caller + " -> " + callee);
		IRCache irCache = cxt.getIRCache();
		ControlFlowGraph callerControlFlowGraph = irCache.get(caller);
		ControlFlowGraph calleeControlFlowGraph = irCache.get(callee);
		
		if(calleeControlFlowGraph != null) {
			CallEdge outgoingCallEdge = new CallEdge(callerBlock, getConcreteBlockNode(callerControlFlowGraph.getEntries().iterator().next()));
			callGraph.addEdge(callerBlock, outgoingCallEdge);
			
			for(Stmt stmt : calleeControlFlowGraph.stmts()) {
				if(stmt.getOpcode() == Opcode.RETURN) {
					BasicBlock returnBlock = stmt.getBlock();
					CallGraphBlock graphNode = getConcreteBlockNode(returnBlock);
					CallEdge returnCallEdge = new CallEdge(graphNode, callerBlock);
					callGraph.addEdge(graphNode, returnCallEdge);
				}
			}
		} else {
			LibraryStubCallGraphBlock libraryNode = getLibraryStubNode(callee);
			
			CallEdge outgoingCallEdge = new CallEdge(callerBlock, libraryNode);
			callGraph.addEdge(callerBlock, outgoingCallEdge);
			
			CallEdge returnCallEdge = new CallEdge(libraryNode, callerBlock);
			
		}
	}
	
	private void initControlFlowGraphForIPCallGraph(ControlFlowGraph cfg) {
		for(BasicBlock b : cfg.vertices()) {
			CallGraphBlock graphNode = getConcreteBlockNode(b);
			callGraph.addVertex(graphNode);
			
			for(FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				CallGraphBasicBlockBridgeEdge newEdge = new CallGraphBasicBlockBridgeEdge(e, graphNode, getConcreteBlockNode(e.dst));
				callGraph.addEdge(graphNode, newEdge);
			}
		}
	}
}