package org.mapleir.deob.interproc.cxtsenscg;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.interproc.geompa.cg.ReflectionModel;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.res.InvocationResolver4;
import org.mapleir.stdlib.collections.map.NullPermeableHashMap;
import org.objectweb.asm.tree.MethodNode;

public class OFCGB {

	public static final int FINALIZE = 8;
	
	private final AnalysisContext cxt;
	private final InvocationResolver4 resolver;
	
	private final ContextInsensitiveCallGraph cicg;
	private final ReachabilityMatrix rm;
    private final QueueReader<MethodNode> worklist;
    private final HashSet<MethodNode> analyzedMethods;
	
	private final NullPermeableHashMap<ReceiverKey, List<VCS>> receiverToSites;
	private final NullPermeableHashMap<MethodNode, List<ReceiverKey>> methodToReceivers;
	
	public OFCGB(AnalysisContext cxt, InvocationResolver4 resolver, ReachabilityMatrix rm) {
		this.cxt = cxt;
		this.resolver = resolver;
		this.rm = rm;
		worklist = rm.listener();
		
		cicg = new ContextInsensitiveCallGraph();
		
		receiverToSites = new NullPermeableHashMap<>(ArrayList::new);
		methodToReceivers = new NullPermeableHashMap<>(ArrayList::new);
		
		analyzedMethods = new HashSet<>();
		
	}
	
	public void processReachables() {
		while(true) {
			if(!worklist.hasNext()) {
				rm.update();
				if(!worklist.hasNext()) {
					break;
				}
			}
			
			MethodNode next = worklist.next();
			if(analyzedMethods.add(next)) {
				process(next);
			}
		}
	}
	
	private void process(MethodNode m) {
		if(Modifier.isNative(m.access)) {
			return;
		}
		
		processImplicitTargets(m);
	}
	
	private void processImplicitTargets(MethodNode m) {
		assert !(Modifier.isAbstract(m.access) || Modifier.isNative(m.access));
		
		if(m.name.equals("<init>")) {
			processInit(m);
		}
		
		ControlFlowGraph cfg = cxt.getIRCache().get(m);
		for(Stmt stmt : cfg.stmts()) {
			for(Expr e : stmt.enumerateOnlyChildren()) {
				if(e instanceof Invocation) {
					Invocation invoke = (Invocation) e;
					
					String owner = invoke.getOwner(), name = invoke.getName(), desc = invoke.getDesc();
					
					if(owner.equals("java/lang/reflect/Method") && name.equals("invoke") && desc.equals("(Ljava/lang/Object;[Ljava/lang/Object;)V")) {
						ReflectionModel
					}
				}
			}
		}
	}
	
	private void processInit(MethodNode m) {
		assert m.name.equals("<init>");
		
		MethodNode finaliser = resolver.resolve(m.owner, "finalize", "()V", false);
		cicg.addEdge(m, new CallEdge(m, finaliser, null, FINALIZE));
	}
	
	private void addVirtualCallSite(MethodNode callerMethod, Invocation invoke, ReceiverKey receiver, String name, String desc, int type) {
		receiverToSites.getNonNull(receiver).add(new VCS(callerMethod, invoke, name, desc, type));
		methodToReceivers.getNonNull(callerMethod).add(receiver);
	}
}