package org.mapleir.stdlib.ir.transform.ssa;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsHandler;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.ir.transform.Liveness;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;

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
		phiUse = new NullPermeableHashMap<>(() -> new NullPermeableHashMap<>(locals));
		phiDef = new NullPermeableHashMap<>(locals);
		
		out = new NullPermeableHashMap<>(locals);
		in = new NullPermeableHashMap<>(locals);
		
		queue = new LinkedList<>();
		
		this.cfg = cfg;
		
		init();
	}
	
	private void enqueue(BasicBlock b) {
		if (!queue.contains(b)) {
//			System.out.println("Enqueue " + b);
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
		
//		System.out.println();
//		System.out.println();
//		for (BasicBlock b : cfg.vertices())
//			System.out.println(b.getId() + "    ||||    DEF: " + def.get(b) + "    |||||    USE: " + use.get(b));
//		System.out.println();
//		for (BasicBlock b : cfg.vertices())
//			System.out.println(b.getId() + "    ||||    \u0278DEF: " + phiDef.get(b) + "    |||||    \u0278USE: " + phiUse.get(b));
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
			if (stmt instanceof CopyVarStatement) {
				CopyVarStatement cvs = (CopyVarStatement) stmt;
				VarExpression var = cvs.getVariable();
				Local defLocal = var.getLocal();
				Expression rhs = cvs.getExpression();
				if (rhs instanceof PhiExpression) {
					phiDef.get(b).add(defLocal);
					PhiExpression phi = (PhiExpression) rhs;
					for (Map.Entry<BasicBlock, Expression> e : phi.getArguments().entrySet()) {
						BasicBlock exprSource = e.getKey();
						Expression phiExpr = e.getValue();
						GenericBitSet<Local> useSet = phiUse.get(b).getNonNull(exprSource);
						for (Statement child : phiExpr)
							if (child.getOpcode() == Opcode.LOCAL_LOAD)
								useSet.add(((VarExpression) child).getLocal());
					}
					continue; // do this to avoid adding used locals of phi copies
				}
				
				def.get(b).add(defLocal);
				use.get(b).remove(defLocal);
			}
			for (Statement child : stmt)
				if (child.getOpcode() == Opcode.LOCAL_LOAD)
					use.get(b).add(((VarExpression) child).getLocal());
		}
	}
	
	@Override
	public GenericBitSet<Local> in(BasicBlock b) {
		return in.get(b).copy();
	}
	
	@Override
	public GenericBitSet<Local> out(BasicBlock b) {
		return out.get(b).copy();
	}
	
	public void compute() {
		// +use and -def affect out
		// -use and +def affect in
		// negative handling always goes after positive and any adds
		while (!queue.isEmpty()) {
			BasicBlock b = queue.remove();
//			System.out.println("\n\nProcessing " + b.getId());
			
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
				curIn.removeAll(phiUse.get(b).getNonNull(predEdge.src));
			
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
				
//				for (BasicBlock b2 : cfg.vertices())
//					System.out.println(b2.getId() + "   ||||    IN: " + in.get(b2) + "   |||||   OUT: " + out.get(b2));
			}
		}
	}
	
	public ControlFlowGraph getGraph() {
		return cfg;
	}
}
