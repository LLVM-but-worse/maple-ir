package org.mapleir.deob.interproc.cxtsenscg;

import org.mapleir.deob.interproc.geompa.AllocNode;
import org.mapleir.deob.interproc.geompa.ArrayElement;
import org.mapleir.deob.interproc.geompa.PAG;
import org.mapleir.deob.interproc.geompa.PointsToAnalysis;
import org.mapleir.deob.interproc.geompa.PointsToNode;
import org.mapleir.deob.interproc.geompa.VarNode;
import org.mapleir.ir.TypeUtils;
import org.objectweb.asm.Type;

public class GlobalNodeFactory {

	private final PAG pag;

	public GlobalNodeFactory(PAG pag) {
		this.pag = pag;
	}

	public PointsToNode caseArgv() {
		Type stringArrayType = Type.getType("[Ljava/lang/String;");
		AllocNode argv = pag.makeAllocNode(PointsToAnalysis.STRING_ARRAY_NODE, stringArrayType, null);
		VarNode sanl = pag.makeGlobalVarNode(PointsToAnalysis.STRING_ARRAY_NODE_LOCAL, stringArrayType);
		AllocNode stringNode = pag.makeAllocNode(PointsToAnalysis.STRING_NODE, TypeUtils.STRING, null);
		VarNode stringNodeLocal = pag.makeGlobalVarNode(PointsToAnalysis.STRING_NODE_LOCAL, TypeUtils.STRING);
		pag.addEdge(argv, sanl);
		pag.addEdge(stringNode, stringNodeLocal);
		pag.addEdge(stringNodeLocal, pag.makeFieldRefNode(sanl, ArrayElement.INSTANCE));
		return sanl;
	}

	public PointsToNode caseMainThread() {
		Type threadType = Type.getType("Ljava/lang/Thread;");
		AllocNode threadNode = pag.makeAllocNode(PointsToAnalysis.MAIN_THREAD_GROUP_NODE, threadType, null);
		VarNode threadNodeLocal = pag.makeGlobalVarNode(PointsToAnalysis.MAIN_THREAD_NODE_LOCAL, threadType);
		pag.addEdge(threadNode, threadNodeLocal);
		return threadNodeLocal;
	}

	public PointsToNode caseMainThreadGroup() {
		Type threadGroupType = Type.getType("Ljava/lang/ThreadGroup;");
		AllocNode threadGroupNode = pag.makeAllocNode(PointsToAnalysis.MAIN_THREAD_GROUP_NODE, threadGroupType, null);
		VarNode threadGroupNodeLocal = pag.makeGlobalVarNode(PointsToAnalysis.MAIN_THREAD_GROUP_NODE_LOCAL,
				threadGroupType);
		pag.addEdge(threadGroupNode, threadGroupNodeLocal);
		return threadGroupNodeLocal;
	}
	
	public PointsToNode caseFinalizeQueue() {
		return pag.makeGlobalVarNode(PointsToAnalysis.FINALIZE_QUEUE, Type.getType("Ljava/lang/Object;"));
	}
	
	public PointsToNode caseDefaultClassLoader() {
//		AllocNode a = pag.makeAllocNode(PointsToAnalysis.DEFAULT_CLASS_LOADER,
//				AnySubType.v(RefType.v("java.lang.ClassLoader")), null);
//		VarNode v = pag.makeGlobalVarNode(PointsToAnalysis.DEFAULT_CLASS_LOADER_LOCAL,
//				RefType.v("java.lang.ClassLoader"));
//		pag.addEdge(a, v);
//		return v;
	}
}
