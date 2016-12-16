package org.mapleir.ir.cfg;

import java.util.Iterator;

import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.tree.MethodNode;

public class ControlFlowGraph extends FastBlockGraph {
	
	private final MethodNode method;
	private final LocalsPool locals;
	
	public ControlFlowGraph(MethodNode method, int base) {
		this.method = method;
		locals = new LocalsPool(base);
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
		
		for(ExceptionRange<BasicBlock> r : getRanges()) {
			sw.print(r.toString() + "\n");
		}
		
		int insn = 0;
		
		for(BasicBlock b : vertices()) {
			blockToString(sw, this, b, insn);
		}
		return sw.toString();
	}
	
	public static void blockToString(TabbedStringWriter sw, ControlFlowGraph cfg, BasicBlock b, int insn) {
		// sw.print("===#Block " + b.getId() + "(size=" + (b.size()) + ")===");
		sw.print("===#Block " + b.getId() + "(size=" + (b.size()) + ", ident=" + (b.getLabel() != null ? b.getLabel().hashCode() : "null") + ")===");
		sw.tab();
		
		Iterator<Statement> it = b.iterator();
		if(!it.hasNext()) {
			sw.untab();
		} else {
			sw.print("\n");
		}
		while(it.hasNext()) {
			Statement stmt = it.next();
//			sw.print(stmt.getId() + ". ");
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
		
		for(FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
			sw.print("\n-> " + e.toString());
		}

		for(FlowEdge<BasicBlock> p : cfg.getReverseEdges(b)) {
			sw.print("\n<- " + p.toString());
		}

		sw.untab();
		sw.untab();
		
		sw.print("\n");
	}

	@Override
	public ControlFlowGraph copy() {
		return new ControlFlowGraph(this);
	}

	public LocalsPool getLocals() {
		return locals;
	}
}