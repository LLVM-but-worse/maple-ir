package org.mapleir.deob.interproc.geompa.cg;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.interproc.geompa.MapleMethod;
import org.mapleir.deob.interproc.geompa.MapleMethodOrMethodContext;
import org.mapleir.deob.interproc.geompa.util.ChunkedQueue;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.AllocObjectExpr;
import org.mapleir.ir.code.expr.FieldLoadExpr;
import org.mapleir.ir.code.expr.NewArrayExpr;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.ir.code.stmt.FieldStoreStmt;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class OnFlyCallGraphBuilder {

	public static boolean VERBOSE = true;
	public static boolean SPARK = true;
	public static boolean TYPES_FOR_INVOKE = false;
	
	private final AnalysisContext cxt;
	private final ApplicationClassSource app;
	private final CallGraph cicg = new CallGraph();
	private final HashSet<MapleMethod> analyzedMethods = new HashSet<>();

	private boolean appOnly;

	private ReachableMethods rm;
	private QueueReader<MapleMethodOrMethodContext> worklist;

	private ContextManager cm;
	private ReflectionModel reflectionModel;
	
	private final ChunkedQueue<MapleMethod> targetsQueue = new ChunkedQueue<>();
	private final QueueReader<MapleMethod> targets = targetsQueue.reader();

	public OnFlyCallGraphBuilder(AnalysisContext cxt, ContextManager cm, ReachableMethods rm) {
		this.cxt = cxt;
		app = cxt.getApplication();
		this.cm = cm;
		this.rm = rm;
		worklist = rm.listener();

		if(SPARK && TYPES_FOR_INVOKE) {
			throw new UnsupportedOperationException("type based not supported");
		} else {
			reflectionModel = new DefaultReflectionModel(app, this);
		}
		/*if (options.reflection_log() == null || options.reflection_log().length() == 0) {
			if (options.types_for_invoke()
					&& new SparkOptions(PhaseOptions.v().getPhaseOptions("cg.spark")).enabled()) {
				reflectionModel = new TypeBasedReflectionModel();
			} else {
				reflectionModel = new DefaultReflectionModel();
			}
		} else {
			reflectionModel = new TraceBasedReflectionModel();
		}*/
		// fh = Scene.v().getOrMakeFastHierarchy();
	}
	
	public void processReachables() {
		while (true) {
			if (!worklist.hasNext()) {
				rm.update();
				if (!worklist.hasNext())
					break;
			}
			MapleMethodOrMethodContext momc = worklist.next();
			MapleMethod m = momc.method();
			if (appOnly && !app.isApplicationClass(m.getMethodNode().owner.name))
				continue;
			if (analyzedMethods.add(m))
				processNewMethod(m.getMethodNode());
			processNewMethodContext(momc);
		}
	}
	
	private void processNewMethodContext(MapleMethodOrMethodContext momc) {
		Iterator<Edge> it = cicg.edgesOutOf(momc);
		while (it.hasNext()) {
			Edge e = it.next();
			cm.addStaticEdge(momc, e.srcUnit(), e.tgt(), e.kind());
		}
	}

	private void processNewMethod(MethodNode m) {
		if (Modifier.isNative(m.access) /* || phantom? */) {
			return;
		}
		ControlFlowGraph cfg = cxt.getIRCache().get(m);
		getImplicitTargets(m, cfg);
		findReceivers(m, cfg);
	}
	
	private void findReceivers(MethodNode m, ControlFlowGraph cfg) {
		for(Stmt s : cfg.stmts()) {
			for(Expr e : s.enumerateOnlyChildren()) {
				if(e.getOpcode() == Opcode.DYNAMIC_INVOKE) {
					throw new UnsupportedOperationException();
				} else if(e instanceof Invocation) {
					Invocation ie = (Invocation) e;
					String owner = ie.getOwner(), name = ie.getName(), desc = ie.getDesc();
					
					if(!ie.isStatic()) {
						Expr receiver = ie.getPhysicalReceiver();
						addVirtualCallSite(s, m, receiver, ie, owner, name, desc, Edge.ieToKind(ie));
					
						if(name.equals("start") && desc.equals("()V")) {
							addVirtualCallSite(s, m, receiver, ie, null, "run", "()V", Kind.THREAD);
						} else if((name.equals("execute") && desc.equals("(Ljava/lang/Runnable;)V"))) {
							Expr runnable = ie.getArgumentExprs()[0];
							addVirtualCallSite(s, m, runnable, ie, null, "run", "()V", Kind.EXECUTOR);
						}
					} else {
						MethodNode tgt = ie.resolveTargets(cxt.getInvocationResolver()).iterator().next();
						addEdge(m, s, tgt, Edge.ieToKind(ie));
						
						if(owner.equals("java/security/AccessController") && name.equals("doPrivileged")) {
							Expr receiver = ie.getArgumentExprs()[0];
							addVirtualCallSite(s, m, receiver, null, null, "run", "()Ljava/lang/Object;", Kind.PRIVILEGED);
						}
					}
				}
			}
		}
	}
	
	private final Map<Expr, List<VirtualCallSite>> receiverToSites = new HashMap<>();
	private final Map<MethodNode, List<Expr>> methodToReceivers = new HashMap<>();
	
	public Map<MethodNode, List<Expr>> methodToReceivers() {
		return methodToReceivers;
	}
	
	private void addVirtualCallSite(Stmt s, MethodNode m, Expr receiver, Invocation iie, String owner, String name, String desc,
			Kind kind) {
		List<VirtualCallSite> sites = receiverToSites.get(receiver);
		if (sites == null) {
			receiverToSites.put(receiver, sites = new ArrayList<>());
			List<Expr> receivers = methodToReceivers.get(m);
			if (receivers == null)
				methodToReceivers.put(m, receivers = new ArrayList<>());
			receivers.add(receiver);
		}
		sites.add(new VirtualCallSite(s, m, iie, owner, name, desc, kind));
}

	private void handleInit(MethodNode source) {
		for(MethodNode fin : source.owner.methods) {
			if(fin.name.equals("finalize") && fin.desc.equals("()V")) {
				addEdge(source, null, fin, Kind.FINALIZE);
				return;
			}
		}
	}

	private void getImplicitTargets(MethodNode source, ControlFlowGraph cfg) {
		if (Modifier.isNative(source.access) /* || phantom? */)
			return;
		if (source.name.equals("<init>")) {
			handleInit(source);
		}
		
		for(Stmt stmt : cfg.stmts()) {
			
			if(stmt.getOpcode() == Opcode.FIELD_STORE) {
				FieldStoreStmt fsf = (FieldStoreStmt) stmt;
				
				String owner = fsf.getOwner();
				ClassNode ownerNode = app.findClassNode(owner);
				for(MethodNode m : clinitsOf(ownerNode)) {
					addEdge(source, stmt, m, Kind.CLINIT);
				}
			}
			
			for(Expr e : stmt.enumerateOnlyChildren()) {
				if(e.getOpcode() == Opcode.ALLOC_OBJ) {
					AllocObjectExpr aoe = (AllocObjectExpr) e;
					for (MethodNode clinit : clinitsOf(app.findClassNode(aoe.getType().getInternalName()))) {
						addEdge(source, stmt, clinit, Kind.CLINIT);
					}
				} else if(e instanceof Invocation) {
					Invocation ie = (Invocation) e;
					
					String owner = ie.getOwner(), name = ie.getName();
					
					if(owner.equals("java/lang/reflect/Method") && name.equals("invoke")) {
						reflectionModel.methodInvoke(source, ie);
					} else if(owner.equals("java/lang/Class")) {
						if(name.equals("newInstance")) {
							reflectionModel.classNewInstance(source, ie);
						} else if(name.equals("forName")) {
							reflectionModel.classForName(source, ie);
						}
					} else if(owner.equals("java/lang/reflect/Constructor") && name.equals("newInstance")) {
						reflectionModel.contructorNewInstance(source, ie);
					}
					
					if(ie.isStatic()) {
						ClassNode ownerNode = app.findClassNode(owner);
						for(MethodNode m : clinitsOf(ownerNode)) {
							addEdge(source, stmt, m, Kind.CLINIT);
						}
					}
				} else if(e.getOpcode() == Opcode.FIELD_LOAD) {
					FieldLoadExpr fle = (FieldLoadExpr) e;
					
					String owner = fle.getOwner();
					ClassNode ownerNode = app.findClassNode(owner);
					for(MethodNode m : clinitsOf(ownerNode)) {
						addEdge(source, stmt, m, Kind.CLINIT);
					}
				} else if(e.getOpcode() == Opcode.NEW_ARRAY) {
					NewArrayExpr nae = (NewArrayExpr) e;
					
					for (MethodNode clinit : clinitsOf(app.findClassNode(nae.getType().getInternalName()))) {
						addEdge(source, stmt, clinit, Kind.CLINIT);
					}
				}
			}
		}
	}
	
	public List<MethodNode> clinitsOf(ClassNode cl) {
		List<MethodNode> ret = new ArrayList<>();
		while (true) {
			if (cl == null || cl.name.equals("java/lang/Object")) {
				break;
			}
			for (MethodNode m : cl.methods) {
				if (m.name.equals("<clinit>")) {
					ret.add(m);
				}
				cl = app.findClassNode(cl.superName);
			}
		}
		return ret;
	}
	
	public void addEdge(MethodNode src, Stmt stmt, MethodNode tgt, Kind kind) {
		cicg.addEdge(new Edge(MapleMethod.get(src), stmt, MapleMethod.get(tgt), kind));
	}

//	private void addEdge(MethodNode src, Stmt stmt, SootClass cls, NumberedString methodSubSig, Kind kind) {
//		SootMethod sm = cls.getMethodUnsafe(methodSubSig);
//		if (sm != null) {
//			addEdge(src, stmt, sm, kind);
//		}
//	}
//
//	private void addEdge(SootMethod src, Stmt stmt, SootMethod tgt) {
//		InvokeExpr ie = stmt.getInvokeExpr();
//		addEdge(src, stmt, tgt, Edge.ieToKind(ie));
//	}
}