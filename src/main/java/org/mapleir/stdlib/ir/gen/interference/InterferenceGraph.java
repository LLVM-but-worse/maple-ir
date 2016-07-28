package org.mapleir.stdlib.ir.gen.interference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.collections.graph.FastUndirectedGraph;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.ssa.SSALivenessAnalyser;

public class InterferenceGraph extends FastUndirectedGraph<ColourableNode, InterferenceEdge> {

	private final Map<Local, ColourableNode> localMap;
	
	public InterferenceGraph() {
		localMap = new HashMap<>();
	}
	
	public ColourableNode getVertex(Local l) {
		return localMap.get(l);
	}
	
	public ColourableNode getVertexIf(Local l) {
		ColourableNode n = getVertex(l);
		if(n == null) {
			n = new ColourableNode(l, 0);
			addVertex(n);
		}
		return n;
	}
	
	@Override
	public void addVertex(ColourableNode n) {
		super.addVertex(n);
		localMap.put(n.getLocal(), n);
	}
	
	@Override
	public void removeVertex(ColourableNode n) {
		super.removeVertex(n);
		localMap.remove(n.getLocal());
	}
	
	@Override
	public boolean excavate(ColourableNode n) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean jam(ColourableNode pred, ColourableNode succ, ColourableNode n) {
		throw new UnsupportedOperationException();
	}

	@Override
	public InterferenceEdge clone(InterferenceEdge edge, ColourableNode oldN, ColourableNode newN) {
		return new InterferenceEdge(oldN, newN);
	}
	
	private void interference(Local l, Local l2) {
		if(l != l2) {
			ColourableNode n = getVertexIf(l);
			ColourableNode n2 = getVertexIf(l2);
			addEdge(n, new InterferenceEdge(n, n2));
		}
	}
	
	public static InterferenceGraph build(SSALivenessAnalyser liveness) {
		InterferenceGraph ig = new InterferenceGraph();
		for(BasicBlock b : liveness.getGraph().vertices()) {
			Map<Local, Boolean> out = liveness.out(b);
			
			for(Statement stmt : b.getStatements()) {
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					Local def = copy.getVariable().getLocal();
					Expression e = copy.getExpression();
					
					if(out.containsKey(def)) {
						for(Entry<Local, Boolean> entry : out.entrySet()) {
							if(entry.getValue()) {
								ig.interference(def, entry.getKey());
							}
						}
					}
					
					if(e instanceof PhiExpression) {
						
					} else if(!(e instanceof VarExpression)) {
						for(Statement s : Statement.enumerate(e)) {
							if(s instanceof VarExpression) {
								VarExpression v = (VarExpression) s;
							}
						}
					}
				}
			}
		}
		return ig;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("map {\n");
		for(Entry<ColourableNode, Set<InterferenceEdge>> e : new HashSet<>(map.entrySet())) {
			sb.append("   ").append(e.getKey()).append(" interferes with ").append(e.getValue()).append("\n");
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public InterferenceEdge invert(InterferenceEdge edge) {
		return new InterferenceEdge(edge.dst, edge.src);
	}
}