package org.mapleir.ir.cfg;

import java.util.Iterator;

import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.locals.LocalsHandler;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.objectweb.asm.tree.MethodNode;

public class ControlFlowGraph extends FastBlockGraph {
	
	private final MethodNode method;
	private final LocalsHandler locals;
	
	public ControlFlowGraph(MethodNode method, int base) {
		this.method = method;
		locals = new LocalsHandler(base);
	}
	
	public ControlFlowGraph(ControlFlowGraph cfg) {
		super(cfg);
		method = cfg.method;
		locals = cfg.locals;
	}
	
	public MethodNode getMethod() {
		return method;
	}
	
	@Override
	public String toString() {
		TabbedStringWriter sw = new TabbedStringWriter();
		int insn = 0;
		
		for(BasicBlock b : vertices()) {
			sw.print("===#Block " + b.getId() + "(size=" + (b.size()) + ")===");
			sw.tab();
			sw.print("\n");
			
			
			Iterator<Statement> it = b.iterator();
			if(!it.hasNext()) {
				sw.untab();
			}
			while(it.hasNext()) {
				Statement stmt = it.next();
				sw.print(insn++ + ". ");
				stmt.toString(sw);
				
				if(!it.hasNext()) {
					sw.untab();
				} else {
					sw.print("\n");
				}
			}

			sw.tab();
			sw.tab();
			
			for(FlowEdge<BasicBlock> e : getEdges(b)) {
				sw.print("\n-> " + e.toString());
			}

			for(FlowEdge<BasicBlock> p : getReverseEdges(b)) {
				sw.print("\n<- " + p.toString());
			}

			sw.untab();
			sw.untab();
			
			sw.print("\n");
			
		}
		return sw.toString();
	}
	
	@Override
	public ControlFlowGraph copy() {
		return new ControlFlowGraph(this);
	}

	public LocalsHandler getLocals() {
		return locals;
	}
}