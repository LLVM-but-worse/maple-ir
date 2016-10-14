package org.mapleir.ir.analysis;

import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.collections.graph.flow.TarjanDominanceComputor;

public class DominanceLivenessAnalyser {

	private final NullPermeableHashMap<BasicBlock, GenericBitSet<BasicBlock>> rv;
	private final NullPermeableHashMap<BasicBlock, GenericBitSet<BasicBlock>> tq;
	private final NullPermeableHashMap<BasicBlock, GenericBitSet<BasicBlock>> sdoms;

	public final ControlFlowGraph cfg;
	private SSADefUseMap defuse;
	public final ControlFlowGraph red_cfg;
	public final ExtendedDfs<BasicBlock> cfg_dfs;
	public final NullPermeableHashMap<BasicBlock, GenericBitSet<BasicBlock>> backEdges;
	public final GenericBitSet<BasicBlock> btargs;
	public final SimpleDfs<BasicBlock> reduced_dfs;
	public final TarjanDominanceComputor<BasicBlock> domc;

	public DominanceLivenessAnalyser(ControlFlowGraph cfg, BasicBlock entry, SSADefUseMap defuse) {
		this.cfg = cfg;
		this.defuse = defuse;

		rv = new NullPermeableHashMap<>(cfg);
		tq = new NullPermeableHashMap<>(cfg);
		sdoms = new NullPermeableHashMap<>(cfg);

		cfg_dfs = new ExtendedDfs<>(cfg, entry, ExtendedDfs.EDGES);
		backEdges = new NullPermeableHashMap<>(cfg);
		btargs = cfg.createBitSet();
		red_cfg = reduce(cfg, cfg_dfs.getEdges(ExtendedDfs.BACK));
		reduced_dfs = new SimpleDfs<>(red_cfg, entry, true, true);

		computeReducedReachability();
		computeTargetReachability();

		domc = new TarjanDominanceComputor<>(cfg, reduced_dfs.getPreOrder());

		computeStrictDominators();
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
		for (BasicBlock b : reduced_dfs.getPostOrder()) {
			BasicBlock idom = domc.idom(b);
			if (idom != null) {
				GenericBitSet<BasicBlock> set = sdoms.getNonNull(idom);
				set.add(b);
				set.addAll(sdoms.getNonNull(b));
			}
		}
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
		for (BasicBlock b : reduced_dfs.getPreOrder()) {
			tq.getNonNull(b).add(b);

			// Tup(t) = set of unreachable backedge targets from reachable sources
			GenericBitSet<BasicBlock> tup = backEdges.getNonNull(b).relativeComplement(rv.get(b));
			for (BasicBlock w : tup)
				tq.get(b).addAll(tq.get(w));
		}
	}

	private ControlFlowGraph reduce(ControlFlowGraph cfg, Set<FlowEdge<BasicBlock>> back) {
		ControlFlowGraph reducedCfg = cfg.copy();
		for (FlowEdge<BasicBlock> e : back) {
			reducedCfg.removeEdge(e.src, e);
			btargs.add(e.dst);
			backEdges.getNonNull(e.src).add(e.dst);
		}
		return reducedCfg;
	}

	public boolean isLiveIn(BasicBlock b, Local l) {
		BasicBlock defBlock = defuse.defs.get(l);

		if (defuse.phiDefs.containsKey(l) && defBlock == b) {
			return true;
		}

		GenericBitSet<BasicBlock> tqa = tq.get(b).intersect(sdoms.getNonNull(defBlock));
		for (BasicBlock t : tqa) {
			GenericBitSet<BasicBlock> rt = rv.get(t).intersect(defuse.uses.get(l));
			if (!rt.isEmpty())
				return true;
		}

		return false;
	}

	public boolean isLiveOut(BasicBlock q, Local a) {
		BasicBlock defBlock = defuse.defs.get(a);

		GenericBitSet<BasicBlock> uses = defuse.uses.getNonNull(a);
		if (defBlock == q) {
			return !uses.relativeComplement(defBlock).isEmpty() || defuse.phiUses.get(defBlock).contains(a);
		}

		boolean targ = !btargs.contains(q);

		GenericBitSet<BasicBlock> sdomdef = sdoms.getNonNull(defBlock);
		if (sdomdef.contains(q)) {
			GenericBitSet<BasicBlock> tqa = tq.get(q).intersect(sdomdef);

			for (BasicBlock t : tqa) {
				GenericBitSet<BasicBlock> u = uses.copy();
				if (t == q && targ)
					u.remove(q);

				GenericBitSet<BasicBlock> rtt = rv.getNonNull(t).intersect(u);
				if (!rtt.isEmpty())
					return true;
			}
		}

		return false;
	}
}