package org.mapleir.stdlib.ir.transform.ssa;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.BackwardsFlowAnalyser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class SSALivenessAnalyser extends BackwardsFlowAnalyser<BasicBlock, FlowEdge<BasicBlock>, Map<Local, Boolean>> {

	private Map<Local, Boolean> initial;
	private NullPermeableHashMap<BasicBlock, Set<Local>> def;
	private NullPermeableHashMap<BasicBlock, Set<Local>> phiDef;
	private NullPermeableHashMap<BasicBlock, Set<Local>> phiUse;
	
	
	public SSALivenessAnalyser(FlowGraph<BasicBlock, FlowEdge<BasicBlock>> graph, boolean commit) {
		super(graph, commit);
	}
	
	public SSALivenessAnalyser(FlowGraph<BasicBlock, FlowEdge<BasicBlock>> graph) {
		this(graph, true);
	}
	
	@Override
	protected void init() {
		initial = new HashMap<>();
		def = new NullPermeableHashMap<>(new SetCreator<>());
		phiDef = new NullPermeableHashMap<>(new SetCreator<>());
		phiUse = new NullPermeableHashMap<>(new SetCreator<>());

		for (BasicBlock b : graph.vertices()) {
			for (Statement stmt : b.getStatements()) {
				for (Statement s : Statement.enumerate(stmt)) {
					if (s instanceof CopyVarStatement) {
						CopyVarStatement copy = (CopyVarStatement) s;
						
						Local l = copy.getVariable().getLocal();
						initial.put(l, Boolean.valueOf(false));
						Expression expr = copy.getExpression();
						if(expr instanceof PhiExpression) {
							phiDef.getNonNull(b).add(l);
							Set<Local> set = phiUse.getNonNull(b);
							for(Expression e : ((PhiExpression) expr).getLocals().values()) {
								for(Statement s1 : Statement.enumerate(e)) {
									if(s1 instanceof VarExpression) {
										VarExpression v = (VarExpression) s1;
										set.add(v.getLocal());
									}
								}
							}
						} else {
							def.getNonNull(b).add(l);
						}
					} else if (s instanceof VarExpression) {
						initial.put(((VarExpression) s).getLocal(), Boolean.valueOf(false));
					}
				}
			}
		}

		super.init();
	}

	@Override
	protected Map<Local, Boolean> newState() {
		return new HashMap<>(initial);
	}

	@Override
	protected Map<Local, Boolean> newEntryState() {
		return new HashMap<>(initial);
	}

	@Override
	protected void merge(BasicBlock srcB, Map<Local, Boolean> srcOut, BasicBlock dstB, Map<Local, Boolean> dstIn, Map<Local, Boolean> out) {
		for(Entry<Local, Boolean> e : srcOut.entrySet()) {
			out.put(e.getKey(), e.getValue());
		}
		flowThrough(dstB, dstIn, srcB, out);
		for(Entry<Local, Boolean> e : srcOut.entrySet()) {
			out.put(e.getKey(), out.get(e.getKey()) || e.getValue());
		}
	}
	
	@Override
	protected void flowThrough(BasicBlock dstB, Map<Local, Boolean> dstIn, BasicBlock srcB, Map<Local, Boolean> srcOut) {
		// propagate upwards simple flow.

		Set<Local> defs = def.getNonNull(srcB);
		Set<Local> phiDefs = phiDef.getNonNull(dstB);
		for(Entry<Local, Boolean> e : dstIn.entrySet()) {
			// upwards propagation cases:
			
			// dst-live-in: {var}
			//  this could be because var is the target of a phi
			//  in which case it is considered live-in to the dst
			//  but dead-out to the src block.
			// or
			//  if the var isn't the target of a phi, then it means
			//  that the local is genuinely live-in and so we can
			//  just propagate it across the block boundary.
			Local l = e.getKey();
			if(phiDefs.contains(l)) {
				srcOut.put(l, false);
			} else {
				srcOut.put(l, srcOut.get(l) || e.getValue());
			}
		}
		
		// phi uses are considered live-out for the src and semi
		// live-in for the dst.
		for(Local l : phiUse.getNonNull(dstB)) {
			if(defs.contains(l)) {
				srcOut.put(l, true);
			}
		}
	}
	
	@Override
	protected void execute(BasicBlock b, Map<Local, Boolean> out, Map<Local, Boolean> in) {
		for(Entry<Local, Boolean> e : out.entrySet()) {
			Local l = e.getKey();
			in.put(l, e.getValue());
		}
		Set<Local> defs = def.getNonNull(b);
		
		for(Local l : defs) {
			in.put(l, false);
		}
		
		for(Statement stmt : b.getStatements()) {
			if(stmt instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				if(copy.getExpression() instanceof PhiExpression) {
					in.put(copy.getVariable().getLocal(), true);
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
						in.put(l, true);
					}
				}
			}
		}
	}

	@Override
	protected boolean equals(Map<Local, Boolean> s1, Map<Local, Boolean> s2) {
		Set<Local> keys = new HashSet<>();
		keys.addAll(s1.keySet());
		keys.addAll(s2.keySet());
		
		for(Local key : keys) {
			if(s1.get(key).booleanValue() != s2.get(key).booleanValue()) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	protected void copy(Map<Local, Boolean> src, Map<Local, Boolean> dst) {
		for(Entry<Local, Boolean> e : src.entrySet()) {
			dst.put(e.getKey(), e.getValue());
		}
	}

	@Override
	protected void flowException(BasicBlock srcB, Map<Local, Boolean> src, BasicBlock dstB, Map<Local, Boolean> dst) {
		throw new UnsupportedOperationException();
//		for(Entry<Local, Boolean> e : src.entrySet()) {
//			Local l = e.getKey();
//			if(l.isStack()) {
//				dst.put(l, false);
//			} else {
//				dst.put(l, e.getValue());
//			}
//		}
	}
}