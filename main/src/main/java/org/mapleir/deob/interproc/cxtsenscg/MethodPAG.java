package org.mapleir.deob.interproc.cxtsenscg;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.mapleir.deob.interproc.geompa.PAG;
import org.mapleir.deob.interproc.geompa.PointsToNode;
import org.mapleir.deob.interproc.geompa.util.ChunkedQueue;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Stmt;
import org.objectweb.asm.tree.MethodNode;

public class MethodPAG {
	private static final Map<MethodNode, MethodPAG> cache = new HashMap<>();
	
	public static MethodPAG get(PAG pag, MethodNode m) {
		MethodPAG ret = cache.get(m);
		
		if(ret == null) {
			ret = new MethodPAG(pag, m);
			cache.put(m, ret);
		}
		
		return ret;
	}
	
	private final PAG pag;
	private final MethodNode method;
	private final MethodPAGNodeFactory nodeFactory;
	
	private final ChunkedQueue<PointsToNode> internalEdges;
	private final ChunkedQueue<PointsToNode> inEdges;
	private final ChunkedQueue<PointsToNode> outEdges;
	private final QueueReader<PointsToNode> internalReader;
	private final QueueReader<PointsToNode> inReader;
	private final QueueReader<PointsToNode> outReader;

	protected boolean hasBeenAdded = false;
	protected boolean hasBeenBuilt = false;
	
	protected MethodPAG(PAG pag, MethodNode method) {
		this.pag = pag;
		this.method = method;

		internalEdges = new ChunkedQueue<>();
		inEdges = new ChunkedQueue<>();
		outEdges = new ChunkedQueue<>();

		internalReader = internalEdges.reader();
		inReader = inEdges.reader();
		outReader = outEdges.reader();

		nodeFactory = new MethodPAGNodeFactory(this);
	}
	
	public PAG getPAG() {
		return pag;
	}

	public void addToPag() {
		if (!hasBeenBuilt) {
			throw new IllegalStateException(method.toString());
		}

		if (hasBeenAdded) {
			return;
		}
		hasBeenAdded = true;

		QueueReader<PointsToNode> reader = internalReader.clone();
		while (reader.hasNext()) {
			pag.addEdge(reader.next(), reader.next());
		}

		reader = inReader.clone();
		while (reader.hasNext()) {
			pag.addEdge(reader.next(), reader.next());
		}

		reader = outReader.clone();
		while (reader.hasNext()) {
			pag.addEdge(reader.next(), reader.next());
		}
	}
	
	public void build() {
		if(hasBeenBuilt) {
			return;
		}
		hasBeenBuilt = true;
		
		if(Modifier.isNative(method.access)) {
			if(PAG.SIMULATE_NATIVES) {
				buildNative();
			}
		} else {
			if(!Modifier.isAbstract(method.access)) {
				buildNormal();
			}
		}
		
		addMiscEdges();
	}
	
	private void buildNative() {
		throw new UnsupportedOperationException();
	}
	
	private void buildNormal() {
		ControlFlowGraph cfg = cxt.getIRCache().get(method);
		for(Stmt stmt : cfg.stmts()) {
			nodeFactory.handleStmt(stmt);
		}
	}
	
	private void addMiscEdges() {
		
	}
	
	public void addInternalEdge(PointsToNode src, PointsToNode dst) {
		addEdge(internalEdges, src, dst);
	}

	public void addInEdge(PointsToNode src, PointsToNode dst) {
		addEdge(inEdges, src, dst);
	}
	
	public void addOutEdge(PointsToNode src, PointsToNode dst) {
		addEdge(outEdges, src, dst);
	}

	private void addEdge(ChunkedQueue<PointsToNode> queue, PointsToNode src, PointsToNode dst) {
		if(src == null) {
			return;
		}
		
		queue.add(src);
		queue.add(dst);
		
		if(hasBeenAdded) {
			pag.addEdge(src, dst);
		}
	}
	
	public MethodNode getMethod() {
		return method;
	}
}