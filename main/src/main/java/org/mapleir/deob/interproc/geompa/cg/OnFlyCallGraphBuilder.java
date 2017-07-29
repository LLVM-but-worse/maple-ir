package org.mapleir.deob.interproc.geompa.cg;

import org.mapleir.deob.interproc.geompa.MapleMethod;
import org.mapleir.deob.interproc.geompa.MapleMethodOrMethodContext;
import org.mapleir.deob.interproc.geompa.util.ChunkedQueue;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.objectweb.asm.tree.MethodNode;

public class OnFlyCallGraphBuilder {

	public static boolean VERBOSE = true;

	private final CallGraph cicg = new CallGraph();

	private ReachableMethods rm;
	private QueueReader<MapleMethodOrMethodContext> worklist;

	private ContextManager cm;

	private final ChunkedQueue<MapleMethod> targetsQueue = new ChunkedQueue<>();
	private final QueueReader<MapleMethod> targets = targetsQueue.reader();

	public OnFlyCallGraphBuilder(ContextManager cm, ReachableMethods rm) {
		this.cm = cm;
		this.rm = rm;
		worklist = rm.listener();

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
	
	public void addEdge(MethodNode src, Invocation stmt, MethodNode tgt, Kind kind) {
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