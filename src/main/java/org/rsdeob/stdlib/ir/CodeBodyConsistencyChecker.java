package org.rsdeob.stdlib.ir;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.TryCatchEdge;
import org.rsdeob.stdlib.cfg.edge.UnconditionalJumpEdge;
import org.rsdeob.stdlib.ir.stat.ConditionalJumpStatement;
import org.rsdeob.stdlib.ir.stat.ReturnStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.stat.SwitchStatement;
import org.rsdeob.stdlib.ir.stat.ThrowStatement;
import org.rsdeob.stdlib.ir.stat.UnconditionalJumpStatement;

public class CodeBodyConsistencyChecker {

	private final CodeBody body;
	private final StatementGraph graph;
	
	public Set<Statement> cFaulty;
	public Set<Statement> gFaulty;
	
	public CodeBodyConsistencyChecker(CodeBody body, StatementGraph graph) {
		this.body = body;
		this.graph = graph;
	}
	
	private boolean checkContents() {
		cFaulty = new HashSet<>();
		gFaulty = new HashSet<>();
		
		for(Statement c : body.stmts()) {
			if(!graph.containsVertex(c)) {
				gFaulty.add(c);
			}
		}
		
		for(Statement g : graph.vertices()) {
			if(body.indexOf(g) == -1) {
				cFaulty.add(g);
			}
		}
		
		return !(cFaulty.isEmpty() && gFaulty.isEmpty());
	}
	
	private Set<FlowEdge<Statement>> getNonCatchEdges(Statement src) {
		Set<FlowEdge<Statement>> edges = new HashSet<>(graph.getEdges(src));
		Iterator<FlowEdge<Statement>> it = edges.iterator();
		while(it.hasNext()) {
			FlowEdge<Statement> e = it.next();
			if(e instanceof TryCatchEdge) {
				it.remove();
			}
		}
		return edges;
	}
	
	private void checkEdges() {
		int size = body.size();
		for(int i=0; i < size; i++) {
			Statement stmt = body.getAt(i);
			if(stmt instanceof UnconditionalJumpStatement) {
				UnconditionalJumpStatement jump = (UnconditionalJumpStatement) stmt;
				Statement target = body.getAt(body.indexOf(jump.getTarget()) + 1);
				Set<FlowEdge<Statement>> edges = getNonCatchEdges(jump);
				if(edges.size() != 1) {
					throw new RuntimeException();
				} else {
					FlowEdge<Statement> edge = edges.iterator().next();
					if(edge instanceof UnconditionalJumpEdge) {
						if(edge.dst != target) {
							throw new RuntimeException();
						}
					} else {
						throw new RuntimeException();
					}
				}
			} else if(stmt instanceof ReturnStatement) {
				
			} else if(stmt instanceof ConditionalJumpStatement) {
				
			} else if(stmt instanceof SwitchStatement) {
				
			} else if(stmt instanceof ThrowStatement) {
				throw new RuntimeException("TODO");
			}
		}
	}
	
	public void compute() {
		if(!checkContents()) {
			throw new RuntimeException();
		}
		checkEdges();
	}
}