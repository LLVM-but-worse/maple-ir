package org.mapleir.ir.analysis;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsHandler;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;

public class SSABlockLivenessAnalyser implements Liveness<BasicBlock> {
	private final NullPermeableHashMap<BasicBlock, GenericBitSet<Local>> use;
	private final NullPermeableHashMap<BasicBlock, GenericBitSet<Local>> def;
	private final NullPermeableHashMap<BasicBlock, NullPermeableHashMap<BasicBlock, GenericBitSet<Local>>> phiUse;
	private final NullPermeableHashMap<BasicBlock, GenericBitSet<Local>> phiDef;

	private final NullPermeableHashMap<BasicBlock, GenericBitSet<Local>> out;
	private final NullPermeableHashMap<BasicBlock, GenericBitSet<Local>> in;

	private final Queue<BasicBlock> queue;
	private final LocalsHandler locals;

	private final ControlFlowGraph cfg;

	public SSABlockLivenessAnalyser(ControlFlowGraph cfg) {
		locals = cfg.getLocals();
		use = new NullPermeableHashMap<>(locals);
		def = new NullPermeableHashMap<>(locals);
		phiUse = new NullPermeableHashMap<>(new ValueCreator<NullPermeableHashMap<BasicBlock, GenericBitSet<Local>>>() {
			@Override
			public NullPermeableHashMap<BasicBlock, GenericBitSet<Local>> create() {
				return new NullPermeableHashMap<>(locals);
			}
		});
		phiDef = new NullPermeableHashMap<>(locals);

		out = new NullPermeableHashMap<>(locals);
		in = new NullPermeableHashMap<>(locals);

		queue = new LinkedList<>();

		this.cfg = cfg;

		init();
	}

	private void enqueue(BasicBlock b) {
		if (!queue.contains(b)) {
			// System.out.println("Enqueue " + b);
			queue.add(b);
		}
	}

	private void init() {
		// initialize in and out
		for (BasicBlock b : cfg.vertices()) {
			in.getNonNull(b);
			out.getNonNull(b);
		}

		// compute def, use, and phi for each block
		for (BasicBlock b : cfg.vertices())
			precomputeBlock(b);

		// enqueue every block
		for (BasicBlock b : cfg.vertices())
			enqueue(b);

		// System.out.println();
		// System.out.println();
		// for (BasicBlock b : cfg.vertices())
		// System.out.println(b.getId() + " |||| DEF: " + def.get(b) + " ||||| USE: " + use.get(b));
		// System.out.println();
		// for (BasicBlock b : cfg.vertices())
		// System.out.println(b.getId() + " |||| \u0278DEF: " + phiDef.get(b) + " ||||| \u0278USE: " + phiUse.get(b));
	}

	// compute def, use, and phi for given block
	private void precomputeBlock(BasicBlock b) {
		def.getNonNull(b);
		use.getNonNull(b);
		phiUse.getNonNull(b);
		phiDef.getNonNull(b);

		// we have to iterate in reverse order because a definition will kill a use in the current block
		// this is so that uses do not escape a block if its def is in the same block. this is basically
		// simulating a statement graph analysis
		ListIterator<Statement> it = b.listIterator(b.size());
		while (it.hasPrevious()) {
			Statement stmt = it.previous();
			int opcode = stmt.getOpcode();
			if (opcode == Opcode.PHI_STORE) {
				CopyPhiStatement copy = (CopyPhiStatement) stmt;
				phiDef.get(b).add(copy.getVariable().getLocal());
				PhiExpression phi = copy.getExpression();
				for (Map.Entry<BasicBlock, Expression> e : phi.getArguments().entrySet()) {
					BasicBlock exprSource = e.getKey();
					Expression phiExpr = e.getValue();
					GenericBitSet<Local> useSet = phiUse.get(b).getNonNull(exprSource);
					if (phiExpr.getOpcode() == Opcode.LOCAL_LOAD) {
						useSet.add(((VarExpression) phiExpr).getLocal());
					} else
						for (Statement child : phiExpr.enumerate()) {
							if (child.getOpcode() == Opcode.LOCAL_LOAD) {
								useSet.add(((VarExpression) child).getLocal());
							}
						}
				}
			} else {
				if (opcode == Opcode.LOCAL_STORE) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					Local l = copy.getVariable().getLocal();
					def.get(b).add(l);
					use.get(b).remove(l);

					Expression e = copy.getExpression();
					if (e.getOpcode() == Opcode.CATCH) {
						use.get(b).add(l);
					}
				}
				for (Statement c : stmt.enumerate()) {
					if (c.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpression v = (VarExpression) c;
						use.get(b).add(v.getLocal());
					}
				}
			}
		}
	}

	@Override
	public GenericBitSet<Local> in(BasicBlock b) {
		return in.get(b);
	}

	@Override
	public GenericBitSet<Local> out(BasicBlock b) {
		return out.get(b);
	}

	public void compute() {
		// +use and -def affect out
		// -use and +def affect in
		// negative handling always goes after positive and any adds
		while (!queue.isEmpty()) {
			BasicBlock b = queue.remove();
			// System.out.println("\n\nProcessing " + b.getId());

			GenericBitSet<Local> oldIn = new GenericBitSet<>(in.get(b));
			GenericBitSet<Local> curIn = new GenericBitSet<>(use.get(b));
			GenericBitSet<Local> curOut = locals.createBitSet();

			// out[n] = U(s in succ[n])(in[s])
			for (FlowEdge<BasicBlock> succEdge : cfg.getEdges(b))
				curOut.addAll(in.get(succEdge.dst));

			// negative phi handling for defs
			for (FlowEdge<BasicBlock> succEdge : cfg.getEdges(b))
				curOut.removeAll(phiDef.get(succEdge.dst));

			// positive phi handling for uses, see §5.4.2 "Meaning of copy statements in Sreedhar's method"
			for (FlowEdge<BasicBlock> succEdge : cfg.getEdges(b))
				curOut.addAll(phiUse.get(succEdge.dst).getNonNull(b));

			// negative phi handling for uses
			for (FlowEdge<BasicBlock> predEdge : cfg.getReverseEdges(b))
				curIn.removeAll(phiUse.get(b).getNonNull(predEdge.src).relativeComplement(use.get(b)));

			// positive phi handling for defs
			curIn.addAll(phiDef.get(b));
			oldIn.addAll(phiDef.get(b));

			// in[n] = use[n] U(out[n] - def[n])
			curIn.addAll(curOut.relativeComplement(def.get(b)));

			in.put(b, curIn);
			out.put(b, curOut);

			// queue preds if dataflow state changed
			if (!oldIn.equals(curIn)) {
				cfg.getReverseEdges(b).stream().map(e -> e.src).forEach(this::enqueue);

				// for (BasicBlock b2 : cfg.vertices())
				// System.out.println(b2.getId() + " |||| IN: " + in.get(b2) + " ||||| OUT: " + out.get(b2));
			}
		}
	}

	public ControlFlowGraph getGraph() {
		return cfg;
	}
}
