package org.mapleir.ir.analysis;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.collections.graph.flow.TarjanDominanceComputor;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class DominanceLivenessAnalyser {

	private final NullPermeableHashMap<BasicBlock, GenericBitSet<BasicBlock>> rv;
	private final NullPermeableHashMap<BasicBlock, GenericBitSet<BasicBlock>> tq;
	public final NullPermeableHashMap<BasicBlock, GenericBitSet<BasicBlock>> sdoms;
	
	public final ControlFlowGraph cfg;
	private SSADefUseMap defuse;
	public final BasicBlock entry;
	public final ControlFlowGraph red_cfg;
	public final ExtendedDfs<BasicBlock> cfg_dfs;
	public final Set<FlowEdge<BasicBlock>> back;
	public final GenericBitSet<BasicBlock> btargs;
	public final ExtendedDfs<BasicBlock> reduced_dfs;
	public final TarjanDominanceComputor<BasicBlock> domc;
	
	public DominanceLivenessAnalyser(ControlFlowGraph cfg, SSADefUseMap defuse) {
		this.cfg = cfg;
		this.defuse = defuse;
		
		rv = new NullPermeableHashMap<>(cfg);
		tq = new NullPermeableHashMap<>(cfg);
		sdoms = new NullPermeableHashMap<>(cfg);
		
		if(cfg.getEntries().size() != 1) {
			throw new IllegalStateException(cfg.getEntries().toString());
		}
		
		entry = cfg.getEntries().iterator().next();
		
		cfg_dfs = new ExtendedDfs<>(cfg, entry, ExtendedDfs.EDGES | ExtendedDfs.PRE /* for sdoms*/ );
		back = cfg_dfs.getEdges(ExtendedDfs.BACK);
		btargs = cfg.createBitSet();
		
		red_cfg = reduce(cfg, back);
		reduced_dfs = new ExtendedDfs<>(red_cfg, entry, ExtendedDfs.POST | ExtendedDfs.PRE);
		
		computeReducedReachability();
		computeTargetReachability();
		
		domc = new TarjanDominanceComputor<>(cfg);
		
		computeStrictDominators();
		
//		System.out.println("Backedge Targets: " + GraphUtils.toBlockArray(btargs));
	}

	public void setDefuse(SSADefUseMap defuse) {
		this.defuse = defuse;
	}

	public boolean sdoms(BasicBlock x, BasicBlock y) {
		return sdoms.getNonNull(x).contains(y);
	}
	
	public boolean doms(BasicBlock x, BasicBlock y) {
		return x == y || sdoms.getNonNull(x).contains(y);
	}

	private void computeStrictDominators() {
		NullPermeableHashMap<BasicBlock, GenericBitSet<BasicBlock>> sdoms = new NullPermeableHashMap<>(cfg);
		// i think this is how you do it..
		for(BasicBlock b : cfg_dfs.getPreOrder()) {
			BasicBlock idom = domc.idom(b);
			if(idom != null) {
				sdoms.getNonNull(b).add(idom);
				sdoms.getNonNull(b).addAll(sdoms.getNonNull(idom));
//				System.out.println(b.getId() + " idom " + idom.getId());
				// System.out.println("  sdominators: " + sdoms.getNonNull(b));
			}
		}
		
		for(Entry<BasicBlock, GenericBitSet<BasicBlock>> e : sdoms.entrySet()) {
			for(BasicBlock b : e.getValue()) {
				this.sdoms.getNonNull(b).add(e.getKey());
			}
		}
		
//		for(Entry<BasicBlock, Set<BasicBlock>> e : this.sdoms.entrySet()) {
//			System.out.println(e.getKey() + " sdom " + GraphUtils.toBlockArray(e.getValue()));
//		}
	}

	private void computeReducedReachability() {
		for (BasicBlock b : reduced_dfs.getPostOrder()) {
			rv.getNonNull(b).add(b);
			for (FlowEdge<BasicBlock> e : red_cfg.getReverseEdges(b)) {
				rv.getNonNull(e.src).addAll(rv.get(b));
			}
		}
	}
	
	private void computeTargetReachability() {
		Map<BasicBlock, GenericBitSet<BasicBlock>> tups = new HashMap<>();
		
		for (BasicBlock b : cfg.vertices()) {
			tups.put(b, tup(b));
		}
		
		for (BasicBlock b : reduced_dfs.getPreOrder()) {
			tq.getNonNull(b).add(b);
			for (BasicBlock w : tups.get(b)) {
				tq.get(b).addAll(tq.get(w));
			}
		}
	}
	
	// Tup(t) = set of unreachable backedge targets from reachable sources
	private GenericBitSet<BasicBlock> tup(BasicBlock t) {
		GenericBitSet<BasicBlock> rt = rv.get(t);
		
		// t' in {V - r(t)}
		GenericBitSet<BasicBlock> set = cfg.createBitSet();
		set.addAll(cfg.vertices());
		set.removeAll(rt);
		
		// all s' where (s', t') is a backedge and s'
		//  is in rt.
		
		// set of s'
		GenericBitSet<BasicBlock> res = cfg.createBitSet();
		
		for(BasicBlock tdash : set) {
			for(FlowEdge<BasicBlock> pred : cfg.getReverseEdges(tdash)) {
				BasicBlock src = pred.src;
				// s' = src, t' = dst
				if(back.contains(pred) && rt.contains(src)) {
					res.add(pred.dst);
				}
			}
		}
		
		return res;
	}

	private ControlFlowGraph reduce(ControlFlowGraph cfg, Set<FlowEdge<BasicBlock>> back) {
		ControlFlowGraph reducedCfg = cfg.copy();
		for (FlowEdge<BasicBlock> e : back) {
			reducedCfg.removeEdge(e.src, e);
			
			btargs.add(e.dst);
		}
		return reducedCfg;
	}
	
	public boolean isLiveIn(BasicBlock b, Local l) {
		BasicBlock defBlock = defuse.defs.get(l);
		
		// System.out.println("l: " + l);
		// System.out.println("b: " + b.getId());
		// System.out.println("d: " + defBlock.getId());
		// System.out.println("p: " + defuse.phis.contains(l));
		// System.out.println();
		
		if(defuse.phiDefs.containsKey(l) && defBlock == b) {
			return true;
		}
		
		GenericBitSet<BasicBlock> tqa = tq.get(b).intersect(sdoms.getNonNull(defBlock));
		for(BasicBlock t : tqa) {
			GenericBitSet<BasicBlock> rt = rv.get(t).intersect(defuse.uses.get(l));
			if(!rt.isEmpty())
				return true;
		}
		
		return false;
	}
	
	public boolean isLiveOut(BasicBlock q, Local a) {
		BasicBlock defBlock = defuse.defs.get(a);

		GenericBitSet<BasicBlock> uses = defuse.uses.get(a);
		if(defBlock == q) {
			return !uses.relativeComplement(defBlock).isEmpty() || defuse.phiUses.get(defBlock).contains(a);
		}
		
		boolean targ = !btargs.contains(q);
		
		GenericBitSet<BasicBlock> sdomdef = sdoms.getNonNull(defBlock);
		if(sdomdef.contains(q)) {
			GenericBitSet<BasicBlock> tqa = tq.get(q).intersect(sdomdef);

//			System.out.printf("sdoms: %s, tqa: %s%n", GraphUtils.toBlockArray(sdomdef), GraphUtils.toBlockArray(tqa));
//			System.out.printf("b: %s(%b), l: %s, db: %s%n", q.getId(), targ, a, defBlock.getId());

			for(BasicBlock t : tqa) {
				GenericBitSet<BasicBlock> u = uses.copy();
				if(t == q && targ)
					u.remove(q);

				GenericBitSet<BasicBlock> rtt = rv.getNonNull(t).intersect(u);
//				System.out.printf(" t:%s, u:%s, rt:%s%n", t.getId(), GraphUtils.toBlockArray(u), GraphUtils.toBlockArray(rtt));
				if(!rtt.isEmpty())
					return true;
			}
//			System.out.println();
		}
		
		return false;
	}
}