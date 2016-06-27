package org.rsdeob.stdlib.ir;

import java.util.HashSet;
import java.util.Set;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.TryCatchEdge;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;
import org.rsdeob.stdlib.ir.stat.Statement;

public class StatementGraph extends FlowGraph<Statement, FlowEdge<Statement>>  {
	
	@Override
	public void addEdge(Statement stmt, FlowEdge<Statement> edge) {
		super.addEdge(stmt, edge);
	}

	@Override
	public String toString() {
		return GraphUtils.toString(this, vertices());
	}

	@Override
	public FlowEdge<Statement> clone(FlowEdge<Statement> edge, Statement old, Statement newN) {
		Statement src = edge.src;
		Statement dst = edge.dst;
		
		// remap edges
		if(src == old) {
			src = newN;
		}
		if(dst == old) {
			dst = newN;
		}
		
		return edge.clone(src, dst);
	}
	
	protected Set<ExceptionRange<Statement>> getRangesFor(Statement stmt) {
		Set<ExceptionRange<Statement>> set = new HashSet<>();
		for(ExceptionRange<Statement> r : getRanges()) {
			if(r.getHandler() == stmt || r.get().contains(stmt)) {
				set.add(r);
			}
		}
		return set;
	}
	
	@Override
	public boolean excavate(Statement n) {
		for(ExceptionRange<Statement> r : getRanges()) {
			if(r.getHandler() == n) {
				return false;
			}
		}
		Set<FlowEdge<Statement>> predEdges = getReverseEdges(n);
		
//		System.out.println("Excavating " + n);
		Set<Statement> preds = new HashSet<>();
		for(FlowEdge<Statement> e : predEdges) {
			preds.add(e.src);
		}
		
		Set<FlowEdge<Statement>> succs = getEdges(n);
		
		Set<Statement> realSuccs = new HashSet<>();
		Set<TryCatchEdge<Statement>> tcs = new HashSet<>();
		for(FlowEdge<Statement> p : succs) {
			if(!(p instanceof TryCatchEdge)) {
				realSuccs.add(p.dst);
			} else {
				tcs.add((TryCatchEdge<Statement>) p);
			}
		}
		
		if(predEdges.size() >= 1 && realSuccs.size() == 1) {
			Statement succ = realSuccs.iterator().next();
			
			// clone the real edges
			for(FlowEdge<Statement> pe : predEdges) {
				Statement pred = pe.src;
				if(!(pe instanceof TryCatchEdge)) {
					FlowEdge<Statement> newEdge = clone(pe, n, succ);
					addEdge(pred, newEdge);
//					System.out.println("  " + newEdge);
				}
				
				for(TryCatchEdge<Statement> tce : tcs) {
					TryCatchEdge<Statement> newTce = new TryCatchEdge<Statement>(pred, tce.erange);
					addEdge(pred, newTce);
//					System.out.println("  " + newTce);
				}
			}
			
			removeVertex(n);
		} else if(predEdges.size() == 0 && realSuccs.size() == 1) {
			removeVertex(n);
		} else {
			throw new UnsupportedOperationException(n.toString() + "|||" + predEdges + "|||" + succs);
		}
		
		for(Statement pred : preds) {
			Set<FlowEdge<Statement>> predPreds = getReverseEdges(pred);
			if(predPreds.size() == 0) {
				getEntries().add(pred);
			}
		}
		

		return true;
	}
}