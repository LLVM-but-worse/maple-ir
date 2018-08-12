package org.mapleir.ir.cfg;

import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.flowgraph.FlowGraph;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.flowgraph.edges.FlowEdges;
import org.mapleir.flowgraph.edges.TryCatchEdge;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.impl.VersionedLocal;
import org.mapleir.ir.utils.CFGUtils;
import org.mapleir.ir.utils.dot.ControlFlowGraphDecorator;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.collections.itertools.ChainIterator;
import org.mapleir.stdlib.util.IHasJavaDesc;
import org.mapleir.stdlib.util.JavaDesc;
import org.mapleir.stdlib.util.TabbedStringWriter;

import java.util.*;
import java.util.Map.Entry;

import static org.mapleir.ir.code.Opcode.PHI_STORE;

public class ControlFlowGraph extends FlowGraph<BasicBlock, FlowEdge<BasicBlock>> implements IHasJavaDesc {
	
	private final LocalsPool locals;
	private final JavaDesc javaDesc;

	public ControlFlowGraph(LocalsPool locals, JavaDesc javaDesc) {
		this.locals = locals;
		this.javaDesc = javaDesc;
	}
	
	public ControlFlowGraph(ControlFlowGraph cfg) {
		super(cfg);
		locals = cfg.locals;
		javaDesc = cfg.javaDesc;
	}
	
	/**
	 * Properly removes the edge, and cleans up phi uses in fe.dst of phi arguments from fe.src.
	 * @param fe Edge to excise phi uses.
	 */
	public void exciseEdge(FlowEdge<BasicBlock> fe) {
		if (!this.containsEdge(fe.src(), fe))
			throw new IllegalArgumentException("Graph does not contain the specified edge");
		
		removeEdge(fe);
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
	public void writeAt(CodeUnit parent, Expr from, Expr to) {
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
		
		parent.writeAt(to, parent.indexOf(from));
	}
	
	@Override
	public String toString() {
		TabbedStringWriter sw = new TabbedStringWriter();
		
		for(ExceptionRange<BasicBlock> r : getRanges()) {
			sw.print(r.toString() + "\n");
		}
		
		int insn = 0;
		
		for(BasicBlock b : vertices()) {
			CFGUtils.blockToString(sw, this, b, insn);
		}
		return sw.toString();
	}

	public LocalsPool getLocals() {
		return locals;
	}

	@Override
	public JavaDesc getJavaDesc() {
		return javaDesc;
	}

	@Override
	public String getOwner() {
		return javaDesc.owner;
	}

	@Override
	public String getName() {
		return javaDesc.name;
	}

	@Override
	public String getDesc() {
		return javaDesc.desc;
	}

	@Override
	public JavaDesc.DescType getDescType() {
		return JavaDesc.DescType.METHOD;
	}

	@Override
	public ControlFlowGraph copy() {
		return new ControlFlowGraph(this);
	}

	@Override
	public FlowEdge<BasicBlock> clone(FlowEdge<BasicBlock> edge, BasicBlock old, BasicBlock newN) {
		BasicBlock src = edge.src();
		BasicBlock dst = edge.dst();

		// remap edges
		if(src == old) {
			src = newN;
		}
		if(dst == old) {
			dst = newN;
		}

		return edge.clone(src, dst);
	}
	
	public Iterable<Stmt> stmts() {
		return () -> new ChainIterator.CollectionChainIterator<>(vertices());
	}
	
	public void relabel(List<BasicBlock> order) {
		if (order.size() != size())
			throw new IllegalArgumentException("order is wrong length");
		// copy edge sets
		Map<BasicBlock, Set<FlowEdge<BasicBlock>>> edges = new HashMap<>();
		for(BasicBlock b : order) {
			if (!containsVertex(b))
				throw new IllegalArgumentException("order has missing vertex " + b);
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
				addEdge(fe);
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

	/**
	 * Runs sanity checking on this graph, useful for debugging purposes.
	 */
	public void verify() {
		if (getEntries().size() != 1)
			throw new IllegalStateException("Wrong number of entries: " + getEntries());

		int maxId = 0;
		Set<Integer> usedIds = new HashSet<>();
		for (BasicBlock b : vertices()) {
			if (!usedIds.add(b.getNumericId()))
				throw new IllegalStateException("Id collision: " + b);
			if (b.getNumericId() > maxId)
				maxId = b.getNumericId();

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

			b.checkConsistency();
		}
		if (maxId != size())
			throw new IllegalStateException("Bad id numbering: " + size() + " vertices total, but max id is " + maxId);

		for (ExceptionRange<BasicBlock> er : getRanges()) {
			if (er.getNodes().size() == 0) {
				throw new RuntimeException("empty range: " + er);
			}

			if (!containsVertex(er.getHandler()) || !containsReverseVertex(er.getHandler())) {
				throw new RuntimeException("invalid handler: " + er.getHandler() + " in " + er);
			}

			for (BasicBlock b : er.getNodes()) {
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
