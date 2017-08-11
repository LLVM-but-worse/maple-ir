package org.mapleir.ir.cfg;

import static org.mapleir.ir.code.Opcode.PHI_STORE;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.flowgraph.edges.DummyEdge;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.flowgraph.edges.FlowEdges;
import org.mapleir.flowgraph.edges.ImmediateEdge;
import org.mapleir.flowgraph.edges.TryCatchEdge;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.ir.utils.dot.ControlFlowGraphDecorator;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.GraphUtils;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.collections.itertools.ChainIterator;
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
	
	/**
	 * Properly removes the edge, and cleans up phi uses in fe.dst of phi arguments from fe.src.
	 * @param fe Edge to excise phi uses.
	 */
	public void exciseEdge(FlowEdge<BasicBlock> fe) {
		if (!this.containsEdge(fe.src(), fe))
			throw new IllegalArgumentException("Graph does not contain the specified edge");
		
		removeEdge(fe.src(), fe);
		for (Stmt stmt : fe.dst()) {
			if (stmt.getOpcode() == PHI_STORE) {
				CopyPhiStmt phs = (CopyPhiStmt) stmt;
				PhiExpr phi = phs.getExpression();
				
				BasicBlock pred = fe.src();
				VarExpr arg = (VarExpr) phi.getArgument(pred);
				
				VersionedLocal l = (VersionedLocal) arg.getLocal();
				locals.uses.get(l).remove(arg);
				
				phi.removeArgument(pred);
			} else {
				return;
			}
		}
	}
	
	/**
	 * Excises uses of a removed statement.
	 * @param c Removed statement to update def/use information with respect to.
	 */
	public void exciseStmt(Stmt c) {
		// delete uses
		for(Expr e : c.enumerateOnlyChildren()) {
			if(e.getOpcode() == Opcode.LOCAL_LOAD) {
				VarExpr v = (VarExpr) e;
				
				VersionedLocal l = (VersionedLocal) v.getLocal();
				locals.uses.get(l).remove(v);
			}
		}
		
		c.getBlock().remove(c);
	}
	
	/**
	 * Replaces an expression and updates def/use information accordingly.
	 * @param parent Statement containing expression to be replaced.
	 * @param from Statement to be replaced.
	 * @param to Statement to replace old statement with.
	 */
	public void overwrite(CodeUnit parent, Expr from, Expr to) {
		// remove uses in from
		for(Expr e : from.enumerateWithSelf()) {
			if (e.getOpcode() == Opcode.LOCAL_LOAD) {
				VersionedLocal l = (VersionedLocal) ((VarExpr) e).getLocal();
				locals.uses.get(l).remove(e);
			}
		}
		
		// add uses in to
		for(Expr e : to.enumerateWithSelf()) {
			if (e.getOpcode() == Opcode.LOCAL_LOAD) {
				VarExpr var = (VarExpr) e;
				locals.uses.get((VersionedLocal) var.getLocal()).add(var);
			}
		}
		
		parent.overwrite(to, parent.indexOf(from));
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
	
	public static String printBlocks(Collection<BasicBlock> bbs) {
		TabbedStringWriter sw = new TabbedStringWriter();
		int insn = 1;
		for(BasicBlock bb : bbs) {
			blockToString(sw, bb.getGraph(), bb, insn);
			insn += bb.size();
		}
		return sw.toString();
	}
	
	public static String printBlock(BasicBlock b) {
		TabbedStringWriter sw = new TabbedStringWriter();
		blockToString(sw, b.getGraph(), b, 1);
		return sw.toString();
	}
	
	public static void blockToString(TabbedStringWriter sw, ControlFlowGraph cfg, BasicBlock b, int insn) {
		// sw.print("===#Block " + b.getId() + "(size=" + (b.size()) + ")===");
		sw.print(String.format("===#Block %s(size=%d, ident=%s, flags=%s)===", b.getId(), b.size(),
				/*(b.getLabelNode() != null && b.getLabel() != null ? b.getLabel().hashCode() : "null")*/ "x", Integer.toBinaryString(b.getFlags())));
		sw.tab();
		
		Iterator<Stmt> it = b.iterator();
		if(!it.hasNext()) {
			sw.untab();
		} else {
			sw.print("\n");
		}
		while(it.hasNext()) {
			Stmt stmt = it.next();
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
		
		if(cfg.containsVertex(b)) {
			for(FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
//				if(e.getType() != FlowEdges.TRYCATCH) {
					sw.print("\n-> " + e.toString());
//				}
			}

			for(FlowEdge<BasicBlock> p : cfg.getReverseEdges(b)) {
//				if(p.getType() != FlowEdges.TRYCATCH) {
					sw.print("\n<- " + p.toString());
//				}
			}
		}

		sw.untab();
		sw.untab();
		
		sw.print("\n");
	}

	public LocalsPool getLocals() {
		return locals;
	}

	@Override
	public ControlFlowGraph copy() {
		return new ControlFlowGraph(this);
	}
	
	public Iterable<Stmt> stmts() {
		return () -> new ChainIterator.CollectionChainIterator<>(vertices());
	}
	
	public void naturalise(List<BasicBlock> order) {
		// copy edge sets
		Map<BasicBlock, Set<FlowEdge<BasicBlock>>> edges = new HashMap<>();
		for(BasicBlock b : order) {
			assert (containsVertex(b));
			edges.put(b, getEdges(b));
		}
		// clean graph
		clear();
		
		// rename and add blocks
		int label = 1;
		for(BasicBlock b : order) {
			b.setId(label++);
			addVertex(b);
		}
		
		for(Entry<BasicBlock, Set<FlowEdge<BasicBlock>>> e : edges.entrySet()) {
			BasicBlock b = e.getKey();
			for(FlowEdge<BasicBlock> fe : e.getValue()) {
				addEdge(b, fe);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	// TODO what the heck
	public DotWriter<FastGraph<BasicBlock, FlowEdge<BasicBlock>>, BasicBlock, FlowEdge<BasicBlock>> makeDotWriter() {
		return (DotWriter<FastGraph<BasicBlock, FlowEdge<BasicBlock>>, BasicBlock, FlowEdge<BasicBlock>>)(Object)((DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>)(Object)super.makeDotWriter())
				.add(new ControlFlowGraphDecorator().setFlags(ControlFlowGraphDecorator.OPT_EDGES | ControlFlowGraphDecorator.OPT_STMTS));
	}
	
	public BasicBlock connectHead() {
		return connectHead(new BasicBlock(this, GraphUtils.FAKEHEAD_ID, null) {
			@Override
			public String getId() {
				return "fakehead";
			}
		});
	}
	
	public BasicBlock connectHead(BasicBlock head) {
		addVertex(head);
		
		for(BasicBlock b : vertices()) {
			if(getReverseEdges(b).size() == 0 && b != head) {
				FlowEdge<BasicBlock> e = null;
				if(getEntries().contains(b)) {
					e = new ImmediateEdge<>(head, b);
				} else {
					e = new DummyEdge<>(head, b);
				}
				addEdge(head, e);
			}
		}
		
		return head;
	}
	
	public void disconnectHead(BasicBlock head) {
		removeVertex(head);
	}	
	
	public void verify() {
		for (BasicBlock b : vertices()) {

			if (getReverseEdges(b).size() == 0 && !getEntries().contains(b)) {
				throw new IllegalStateException("dead incoming: " + b);
			}

			for (FlowEdge<BasicBlock> fe : getEdges(b)) {
				if (fe.src() != b) {
					throw new RuntimeException(fe + " from " + b);
				}

				BasicBlock dst = fe.dst();

				if (!containsVertex(dst) || !containsReverseVertex(dst)) {
					throw new RuntimeException(
							fe + "; dst invalid: " + containsVertex(dst) + " : " + containsReverseVertex(dst));
				}

				boolean found = getReverseEdges(dst).contains(fe);

				if (!found) {
					throw new RuntimeException("no reverse: " + fe);
				}

				if (fe.getType() == FlowEdges.TRYCATCH) {
					TryCatchEdge<BasicBlock> tce = (TryCatchEdge<BasicBlock>) fe;
					if (!tce.erange.containsVertex(b)) {
						throw new RuntimeException("no contains: " + b + " in " + tce.erange + " for " + tce);
					}
				}
			}
		}

		for (ExceptionRange<BasicBlock> er : getRanges()) {
			if (er.get().size() == 0) {
				throw new RuntimeException("empty range: " + er);
			}

			if (!containsVertex(er.getHandler()) || !containsReverseVertex(er.getHandler())) {
				throw new RuntimeException("invalid handler: " + er.getHandler() + " in " + er);
			}

			for (BasicBlock b : er.get()) {
				if (!containsVertex(b) || !containsReverseVertex(b)) {
					throw new RuntimeException("invalid b: " + b + " to " + er);
				}

				boolean found = false;

				for (FlowEdge<BasicBlock> fe : getEdges(b)) {
					if (fe.getType() == FlowEdges.TRYCATCH) {
						TryCatchEdge<BasicBlock> tce = (TryCatchEdge<BasicBlock>) fe;

						if (tce.erange == er) {
							if (tce.dst() != er.getHandler()) {
								throw new RuntimeException("false tce: " + tce + ", er: " + er);
							} else {
								found = true;
							}
						}
					}
				}

				if (!found) {
					throw new RuntimeException("mismatch: " + b + " to " + er + " ; " + getEdges(b));
				}
			}
		}
	}
}
