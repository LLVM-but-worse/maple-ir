package org.mapleir.ir.cfg.builder;

import java.util.*;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.BasicLocal;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsHandler;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.cfg.edge.DummyEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.collections.graph.flow.TarjanDominanceComputor;
import org.mapleir.stdlib.ir.transform.Liveness;
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.objectweb.asm.Type;

public class SSAGenPass extends ControlFlowGraphBuilder.BuilderPass {

	private final Map<Local, Integer> counters;
	private final Map<Local, Stack<Integer>> stacks;
	private final Map<BasicBlock, Integer> insertion;
	private final Map<BasicBlock, Integer> process;
	private TarjanDominanceComputor<BasicBlock> doms;
	private Liveness<BasicBlock> liveness;

	public SSAGenPass(ControlFlowGraphBuilder builder) {
		super(builder);

		counters = new HashMap<>();
		stacks = new HashMap<>();

		insertion = new HashMap<>();
		process = new HashMap<>();
	}

	private void insertPhis() {
		int i = 0;
		for(Local l : builder.locals) {
			i++;

			LinkedList<BasicBlock> queue = new LinkedList<>();
			for(BasicBlock b : builder.assigns.get(l)) {
				process.put(b, i);
				queue.add(b);
			}
			while(!queue.isEmpty()) {
				insertPhis(queue.poll(), l, i, queue);
			}
		}
	}

	private void insertPhis(BasicBlock b, Local l, int i, LinkedList<BasicBlock> queue) {
		if(b == builder.exit) {
			return; // exit
		}

		Local newl = builder.graph.getLocals().get(l.getIndex(), 0, l.isStack());

		for(BasicBlock x : doms.iteratedFrontier(b)) {
			if(insertion.get(x) < i) {
				if(x.size() > 0 && builder.graph.getReverseEdges(x).size() > 1) {
					// pruned SSA
					if(liveness.in(x).contains(l)) {
						Map<BasicBlock, Expression> vls = new HashMap<>();
						for(FlowEdge<BasicBlock> fe : builder.graph.getReverseEdges(x)) {
							vls.put(fe.src, new VarExpression(newl, null));
						}
						PhiExpression phi = new PhiExpression(vls);
						CopyPhiStatement assign = new CopyPhiStatement(new VarExpression(l, null), phi);

						x.add(0, assign);
					}
				}

				insertion.put(x, i);
				if(process.get(x) < i) {
					process.put(x, i);
					queue.add(x);
				}
			}
		}
	}

	private void rename() {
		for(Local l : builder.locals) {
			counters.put(l, 0);
			stacks.put(l, new Stack<>());
		}

		Set<BasicBlock> vis = new HashSet<>();
		for(BasicBlock e : builder.graph.getEntries()) {
			search(e, vis);
		}
	}

	private void search(BasicBlock b, Set<BasicBlock> vis) {
		if(vis.contains(b)) {
			return;
		}
		vis.add(b);

		renamePhis(b);
		renameNonPhis(b);

		List<FlowEdge<BasicBlock>> succs = new ArrayList<>();
		for(FlowEdge<BasicBlock> succE : builder.graph.getEdges(b)) {
			succs.add(succE);
		}

		Collections.sort(succs, new Comparator<FlowEdge<BasicBlock>>() {
			@Override
			public int compare(FlowEdge<BasicBlock> o1, FlowEdge<BasicBlock> o2) {
				return o1.dst.compareTo(o2.dst);
			}
		});

		for(FlowEdge<BasicBlock> succE : succs) {
			BasicBlock succ = succE.dst;
			fixPhiArgs(b, succ);
		}

		for(FlowEdge<BasicBlock> succE : succs) {
			BasicBlock succ = succE.dst;
			search(succ, vis);
		}

		for (Statement s : b) {
			if (s.getOpcode() == Opcode.PHI_STORE || s.getOpcode() == Opcode.LOCAL_STORE) {
				AbstractCopyStatement cvs = (AbstractCopyStatement) s;
				Local l = cvs.getVariable().getLocal();
				l = builder.graph.getLocals().get(l.getIndex(), l.isStack());
				stacks.get(l).pop();
			}
		}
	}

	private void renamePhis(BasicBlock b) {
		for(Statement stmt : b) {
			if(stmt.getOpcode() == Opcode.PHI_STORE) {
				CopyPhiStatement copy = (CopyPhiStatement) stmt;
				VarExpression var = copy.getVariable();
				Local lhs = var.getLocal();
				VersionedLocal vl = _gen_name(lhs.getIndex(), lhs.isStack());
				var.setLocal(vl);;
				builder.defs.put(vl, copy);
			}
		}
	}

	private void renameNonPhis(BasicBlock b) {
		for(Statement stmt : b) {
			int opcode = stmt.getOpcode();

			if(opcode != Opcode.PHI_STORE) {
				for(Statement s : stmt) {
					if(s.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpression var = (VarExpression) s;
						Local l = var.getLocal();
						var.setLocal(_top(s, l.getIndex(), l.isStack()));
					}
				}
			}

			if(opcode == Opcode.LOCAL_STORE) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				VarExpression var = copy.getVariable();
				Local lhs = var.getLocal();
				VersionedLocal vl = _gen_name(lhs.getIndex(), lhs.isStack());
				var.setLocal(vl);
				builder.defs.put(vl, copy);
			}
		}
	}

	private void fixPhiArgs(BasicBlock b, BasicBlock succ) {
		for(Statement stmt : succ) {
			if(stmt.getOpcode() == Opcode.PHI_STORE) {
				CopyPhiStatement copy = (CopyPhiStatement) stmt;
				PhiExpression phi = copy.getExpression();
				Expression e = phi.getArgument(b);
				if(e.getOpcode() == Opcode.LOCAL_LOAD) {
					Local l = (VersionedLocal) ((VarExpression) e).getLocal();
					l = _top(stmt, l.getIndex(), l.isStack());
					try {
						AbstractCopyStatement varDef = builder.defs.get(l);
						if(copy.getType() == null) {
							Type t = TypeUtils.asSimpleType(varDef.getType());
							copy.getVariable().setType(t);
							phi.setType(t);
						} else {
							Type t = varDef.getType();
							Type oldT = copy.getType();
							// TODO: common supertypes
							if(!oldT.equals(TypeUtils.asSimpleType(t))) {
								throw new IllegalStateException(l + " " + copy + " " + t + " " + copy.getType());
							}
						}
						VarExpression var = new VarExpression(l, varDef.getType());
						phi.setArgument(b, var);
					} catch (IllegalStateException eg) {
						System.err.println(builder.graph);
						System.err.println(succ.getId() + ": " + phi.getId() + ". " + phi);
						throw eg;
					}
				} else {
					throw new UnsupportedOperationException(String.valueOf(e));
				}
			}
		}
	}

	private VersionedLocal _gen_name(int index, boolean isStack) {
		LocalsHandler handler = builder.graph.getLocals();
		Local l = handler.get(index, isStack);
		int subscript = counters.get(l);
		stacks.get(l).push(subscript);
		counters.put(l, subscript+1);
		return handler.get(index, subscript, isStack);
	}

	private VersionedLocal _top(Statement root, int index, boolean isStack) {
		LocalsHandler handler = builder.graph.getLocals();
		Local l = handler.get(index, isStack);
		Stack<Integer> stack = stacks.get(l);
		if(stack == null) {
			System.err.println(builder.graph);
			System.err.println(stacks);
			throw new NullPointerException(root.toString() + ", " +  l.toString());
		} else if(stack.isEmpty()) {
			System.err.println(builder.graph);
			System.err.println(stacks);
			throw new IllegalStateException(root.toString() + ", " +  l.toString());
		}
		int subscript = stack.peek();
		return handler.get(index, subscript, isStack);
	}

	private void connectExit() {
		builder.naturaliseGraph(new ArrayList<>(builder.graph.vertices()));

		builder.exit = new BasicBlock(builder.graph, builder.graph.size() * 2, null);
		builder.graph.addVertex(builder.exit);

		for(BasicBlock b : builder.graph.vertices()) {
			if(builder.graph.getEdges(b).size() == 0) {
				builder.graph.addEdge(b, new DummyEdge<>(b, builder.exit));
			}

			insertion.put(b, 0);
			process.put(b, 0);
		}
	}

	private void disconnectExit() {
		builder.graph.removeVertex(builder.exit);
	}

	private AbstractCopyStatement findDefSimple(BasicLocal l, BasicBlock b) {
		LocalsHandler locals = builder.graph.getLocals();
		ListIterator<Statement> it = b.listIterator(b.size());
		while (it.hasPrevious()) {
			Statement stmt = it.previous();
			if (stmt instanceof AbstractCopyStatement) {
				AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
				if (locals.asSimpleLocal(copy.getVariable().getLocal()) == l)
					return copy;
			}
		}
		return null;
	}

	private AbstractCopyStatement findDef(BasicLocal l, BasicBlock protectStart) {
		while (true) {
			Set<FlowEdge<BasicBlock>> preds = builder.graph.getReverseEdges(protectStart);
			AbstractCopyStatement result = findDefSimple(l, protectStart);
			if (preds.size() != 1 || result != null) // if there are 0 or 2+ incoming, there MUST be a phi def.
				return result;
			else
				protectStart = preds.iterator().next().src;
		}
	}

	// start and end inclusive
	private BasicBlock makeBlock(int start, int end, boolean first, BasicBlock oldBlock) {
		if (first)
			return null; // special processing for first... TODO
		BasicBlock newBlock = new BasicBlock(builder.graph, ++builder.count, null);
		// TODO
		return newBlock;
	}

	private void splitProtectedBlocks() {
		LocalsHandler localsHandler = builder.graph.getLocals();
		for (ExceptionRange<BasicBlock> r : builder.graph.getRanges()) {
			// determine necessary phi args
			Set<BasicLocal> liveOut = new HashSet<>();
			for (Local l : liveness.in(r.getHandler()))
				liveOut.add(localsHandler.asSimpleLocal(l));

			// split
			List<BasicBlock> oldNodes = r.get();
			r.clearNodes();
			for (BasicBlock b : oldNodes) {
				int start = 0;
				boolean first = true; // first block has to be the original block with the rest trimmed
				// find split point
				for (int i = 0; i < b.size(); i++) {
					Statement stmt = b.get(i);
					if (stmt instanceof AbstractCopyStatement) {
						AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
						if (liveOut.contains(localsHandler.asSimpleLocal(copy.getVariable().getLocal()))) {
							// create new block
							r.addVertex(makeBlock(start, i, first, b));
							start = i + 1;
							first = false;
						}
					}

					if (i == b.size() - 1) { // final block needs special processing to fix edges...

					}
				}

				// update assigns
			}

			// update handler's phi
		}
	}

	@Override
	public void run() {
		connectExit();

		SSABlockLivenessAnalyser liveness = new SSABlockLivenessAnalyser(builder.graph);
		liveness.compute();
		this.liveness = liveness;

		doms = new TarjanDominanceComputor<>(builder.graph);
		insertPhis();
		rename();
		splitProtectedBlocks();


		disconnectExit();
	}
}