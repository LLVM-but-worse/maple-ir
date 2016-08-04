package org.mapleir.stdlib.ir.gen.interference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.header.BlockHeaderStatement;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.Liveness;

public class InterferenceGraphBuilder {

	private final Map<Local, ColourableNode> localMap;
	private final InterferenceGraph graph;
	
	private InterferenceGraphBuilder() {
		localMap = new HashMap<>();
		graph = new InterferenceGraph();
	}
	
	private ColourableNode getVertex(Local l) {
		return localMap.get(l);
	}
	
	private ColourableNode getVertexIf(Local l) {
		ColourableNode n = getVertex(l);
		if(n == null) {
			n = new ColourableNode(l, 0);
			graph.addVertex(n);
		}
		return n;
	}
	
	private InterferenceGraph build(Map<Local, Set<Local>> map) {
		for(Entry<Local, Set<Local>> e : map.entrySet()) {
			Local l = e.getKey();
			Set<Local> set = e.getValue();
			
			ColourableNode n1 = getVertexIf(l);
			
			for(Local l2 : set) {
				ColourableNode n2 = getVertexIf(l2);
				InterferenceEdge edge = new InterferenceEdge(n1, n2);
				graph.addEdge(n1, edge);
			}
		}
		return graph;
	}
	
	public static InterferenceGraph build(ControlFlowGraph cfg, Liveness<BasicBlock> liveness) {
		return new InterferenceGraphBuilder().build(buildMap(cfg, liveness));
	}
	
	public static NullPermeableHashMap<Local, Set<Local>> buildMap(ControlFlowGraph cfg, Liveness<BasicBlock> liveness) {
		NullPermeableHashMap<Local, Set<Local>> interfere = new NullPermeableHashMap<>(new SetCreator<>());
		
		for(BasicBlock b : cfg.vertices()) {
			Set<Local> alive = liveness.out(b);
			for (Local l1 : alive) {
				for (Local l2 : alive) {
					interfere.getNonNull(l1).add(l2);
					interfere.getNonNull(l2).add(l1);
				}
			}
			
			for (int i = b.getStatements().size() - 1; i >= 0; i--) {
				Statement stmt = b.getStatements().get(i);
				if (stmt instanceof CopyVarStatement) {
					CopyVarStatement cvs = (CopyVarStatement) stmt;
					alive.remove(cvs.getVariable().getLocal());
					if (cvs.getExpression() instanceof PhiExpression)
						continue; // do not add uses for phi copy
				}
				Set<Local> usedLocals = stmt.getUsedLocals();
				for (Local useLocal : usedLocals) {
					for (Local liveLocal : alive) {
						interfere.getNonNull(useLocal).add(liveLocal);
						interfere.getNonNull(liveLocal).add(useLocal);
					}
				}
				alive.addAll(usedLocals);
			}
		}
		
		return interfere;
	}
}