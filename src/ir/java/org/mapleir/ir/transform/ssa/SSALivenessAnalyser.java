package org.mapleir.ir.transform.ssa;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.ir.analysis.dataflow.BackwardsFlowAnalyser;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;
import org.mapleir.stdlib.ir.header.BlockHeaderStatement;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.ir.transform.Liveness;

public class SSALivenessAnalyser extends BackwardsFlowAnalyser<BasicBlock, FlowEdge<BasicBlock>, Set<Local>> implements Liveness<BasicBlock> {

	private NullPermeableHashMap<BasicBlock, Set<Local>> def;
	private NullPermeableHashMap<BasicBlock, Set<Local>> phiDef;
	private NullPermeableHashMap<BasicBlock, NullPermeableHashMap<BasicBlock, Set<Local>>> phiUse;
	
	
	public SSALivenessAnalyser(FlowGraph<BasicBlock, FlowEdge<BasicBlock>> graph, boolean commit) {
		super(graph, commit);
	}
	
	public SSALivenessAnalyser(FlowGraph<BasicBlock, FlowEdge<BasicBlock>> graph) {
		this(graph, true);
	}
	
	@Override
	protected void init() {
		def = new NullPermeableHashMap<>(new SetCreator<>());
		phiDef = new NullPermeableHashMap<>(new SetCreator<>());
		phiUse = new NullPermeableHashMap<>(new ValueCreator<NullPermeableHashMap<BasicBlock, Set<Local>>>() { // (smh)
			@Override
			public NullPermeableHashMap<BasicBlock, Set<Local>> create() {
				return new NullPermeableHashMap<>(new SetCreator<>());
			}
		});

		for (BasicBlock b : graph.vertices()) {
			for (Statement stmt : b.getStatements()) {
				for (Statement s : Statement.enumerate(stmt)) {
					if (s instanceof CopyVarStatement) {
						CopyVarStatement copy = (CopyVarStatement) s;
						Local l = copy.getVariable().getLocal();
						Expression expr = copy.getExpression();
						if(expr instanceof PhiExpression) {
							phiDef.getNonNull(b).add(l);
							NullPermeableHashMap<BasicBlock, Set<Local>> map = phiUse.getNonNull(b);
							for(Entry<HeaderStatement, Expression> e : ((PhiExpression) expr).getArguments().entrySet()) {
								for(Statement s1 : Statement.enumerate(e.getValue())) {
									if(s1 instanceof VarExpression) {
										VarExpression v = (VarExpression) s1;
										map.getNonNull(((BlockHeaderStatement)e.getKey()).getBlock()).add(v.getLocal());
									}
								}
							}
						} else {
							def.getNonNull(b).add(l);
						}
					}
				}
			}
		}

		super.init();
	}

	@Override
	protected Set<Local> newState() {
		return new HashSet<>();
	}

	@Override
	protected Set<Local> newEntryState() {
		return new HashSet<>();
	}

	@Override
	protected void merge(BasicBlock srcB, Set<Local> srcOut, BasicBlock dstB, Set<Local> dstIn, Set<Local> out) {
		out.addAll(srcOut);
		flowThrough(dstB, dstIn, srcB, out);
		out.addAll(srcOut);
	}
	
	@Override
	protected void flowThrough(BasicBlock dstB, Set<Local> dstIn, BasicBlock srcB, Set<Local> srcOut) {
		// propagate upwards simple flow.

		Set<Local> defs = def.getNonNull(srcB);
		Set<Local> phiDefs = phiDef.getNonNull(dstB);
		for(Local l : dstIn) {
			if(phiDefs.contains(l)) {
				srcOut.remove(l);
			} else {
				srcOut.add(l);
			}
		}
//		for(Entry<Local, Boolean> e : dstIn.entrySet()) {
//			// upwards propagation cases:
//			
//			// dst-live-in: {var}
//			//  this could be because var is the target of a phi
//			//  in which case it is considered live-in to the dst
//			//  but dead-out to the src block.
//			// or
//			//  if the var isn't the target of a phi, then it means
//			//  that the local is genuinely live-in and so we can
//			//  just propagate it across the block boundary.
//			Local l = e.getKey();
//			if(phiDefs.contains(l)) {
//				srcOut.put(l, false);
//			} else {
//				srcOut.put(l, srcOut.get(l) || e.getValue());
//			}
//		}
		
		// phi uses are considered live-out for the src and semi
		// live-in for the dst.
		
		for(Local l : phiUse.getNonNull(dstB).getNonNull(srcB))
			srcOut.add(l);
	}
	
	@Override
	protected void execute(BasicBlock b, Set<Local> out, Set<Local> in) {
//		for(Entry<Local, Boolean> e : out.entrySet()) {
//			Local l = e.getKey();
//			in.put(l, e.getValue());
//		}
		in.addAll(out);
		
		Set<Local> defs = def.getNonNull(b);
		
		in.removeAll(defs);
//		for(Local l : defs) {
//			in.put(l, false);
//		}
		
		for(Statement stmt : b.getStatements()) {
			if(stmt instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				if(copy.getExpression() instanceof PhiExpression) {
					in.add(copy.getVariable().getLocal());
					// in.put(copy.getVariable().getLocal(), true);
					continue;
				}
			}
			
			// since we are skipping phis, the phi argument variables are
			// considered dead-in unless they are used further on in the block
			// in a non phi statement. this is because the phis are on the
			// edges and not in the actual block.
			
			for(Statement s : Statement.enumerate(stmt)) {
				if(s instanceof VarExpression) {
					VarExpression var = (VarExpression) s;
					Local l = var.getLocal();
					// if it was defined in this block, then it can't be live-in,
					//    UNLESS: it was defined by a phi, in which case it is
					//            in fact live-in.
					if(!defs.contains(l)) {
						in.add(l);
//						in.put(l, true);
					}
				}
			}
		}
	}

	@Override
	protected boolean equals(Set<Local> s1, Set<Local> s2) {
		return s1.equals(s2);
	}
	
	@Override
	protected void copy(Set<Local> src, Set<Local> dst) {
		dst.addAll(src);
	}

	@Override
	protected void flowException(BasicBlock srcB, Set<Local> src, BasicBlock dstB, Set<Local> dst) {
		throw new UnsupportedOperationException();

	}
}