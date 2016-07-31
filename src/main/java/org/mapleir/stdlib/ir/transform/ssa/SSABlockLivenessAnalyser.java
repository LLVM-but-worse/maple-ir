package org.mapleir.stdlib.ir.transform.ssa;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.header.BlockHeaderStatement;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.Liveness;

public class SSABlockLivenessAnalyser implements Liveness<BasicBlock> {
	private final NullPermeableHashMap<BasicBlock, Set<Local>> use;
	private final NullPermeableHashMap<BasicBlock, Set<Local>> def;
	private final NullPermeableHashMap<BasicBlock, NullPermeableHashMap<BasicBlock, Set<Local>>> phiUse;
	private final NullPermeableHashMap<BasicBlock, Set<Local>> phiDef;
	
	private final NullPermeableHashMap<BasicBlock, Set<Local>> out;
	private final NullPermeableHashMap<BasicBlock, Set<Local>> in;
	
	private final Queue<BasicBlock> queue;
	
	private final ControlFlowGraph cfg;
	
	public SSABlockLivenessAnalyser(ControlFlowGraph cfg) {
		use = new NullPermeableHashMap<>(HashSet::new);
		def = new NullPermeableHashMap<>(HashSet::new);
		phiUse = new NullPermeableHashMap<>(() -> new NullPermeableHashMap<>(HashSet::new));
		phiDef = new NullPermeableHashMap<>(HashSet::new);
		
		out = new NullPermeableHashMap<>(HashSet::new);
		in = new NullPermeableHashMap<>(HashSet::new);
		
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
		List<Statement> stmts = b.getStatements();
		for (int i = stmts.size() - 1; i >= 0; i--) {
			Statement stmt = stmts.get(i);
			if (stmt instanceof CopyVarStatement) {
				CopyVarStatement cvs = (CopyVarStatement) stmt;
				VarExpression var = cvs.getVariable();
				Local defLocal = var.getLocal();
				Expression rhs = cvs.getExpression();
				if (rhs instanceof PhiExpression) {
					phiDef.get(b).add(defLocal);
					PhiExpression phi = (PhiExpression) rhs;
					for (Map.Entry<HeaderStatement, Expression> e : phi.getLocals().entrySet()) {
						if (!(e.getKey() instanceof BlockHeaderStatement))
							throw new IllegalArgumentException("Illegal phi expression source header: " + e.getClass().getSimpleName());
						BasicBlock exprSource = ((BlockHeaderStatement) e.getKey()).getBlock();
						Expression phiExpr = e.getValue();
						phiUse.get(b).getNonNull(exprSource).addAll(phiExpr.getUsedLocals());
					}
					continue; // do this to avoid adding used locals of phi copies
				}
				
				def.get(b).add(defLocal);
				use.get(b).remove(defLocal);
			}
			use.get(b).addAll(stmt.getUsedLocals());
		}
	}
	
	@Override
	public Set<Local> in(BasicBlock b) {
		return new HashSet<>(in.get(b));
	}
	
	@Override
	public Set<Local> out(BasicBlock b) {
		return new HashSet<>(out.get(b));
	}
	
	public void compute() {
		// +use and -def affect out
		// -use and +def affect in
		// negative handling always goes after positive and any adds
		while (!queue.isEmpty()) {
			BasicBlock b = queue.remove();
//			System.out.println("\n\nProcessing " + b.getId());
			
			Set<Local> oldIn = new HashSet<>(in.get(b));
			Set<Local> curIn = new HashSet<>(use.get(b));
			Set<Local> curOut = new HashSet<>();
			
			// out[n] = U(s in succ[n])(in[s])
			for (FlowEdge<BasicBlock> succEdge : cfg.getEdges(b))
				curOut.addAll(in.get(succEdge.dst));
			
			// positive phi handling for uses, see §5.4.2 "Meaning of copy statements in Sreedhar's method"
			for (FlowEdge<BasicBlock> succEdge : cfg.getEdges(b))
				curOut.addAll(phiUse.get(succEdge.dst).getNonNull(b));
			
			// negative phi handling for defs
			for (FlowEdge<BasicBlock> succEdge : cfg.getEdges(b))
				curOut.removeAll(phiDef.get(succEdge.dst));
			
			// positive phi handling for defs
			curIn.addAll(phiDef.get(b));
			oldIn.addAll(phiDef.get(b));
			
			// in[n] = use[n] U(out[n] - def[n])
			HashSet<Local> toAdd = new HashSet<>(curOut);
			toAdd.removeAll(def.get(b));
			curIn.addAll(toAdd);
			
			// negative phi handling for uses
			for (FlowEdge<BasicBlock> predEdge : cfg.getReverseEdges(b))
				curIn.removeAll(phiUse.get(b).getNonNull(predEdge.src));
			
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
