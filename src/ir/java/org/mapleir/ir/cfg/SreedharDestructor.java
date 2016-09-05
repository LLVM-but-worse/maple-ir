package org.mapleir.ir.cfg;

import org.mapleir.ir.analysis.SSADefUseMap;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.dot.ControlFlowGraphDecorator;
import org.mapleir.ir.dot.LivenessDecorator;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsHandler;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.collections.graph.dot.BasicDotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;

import static org.mapleir.ir.code.Opcode.LOCAL_LOAD;
import static org.mapleir.ir.dot.ControlFlowGraphDecorator.OPT_DEEP;

public class SreedharDestructor {

	private final ControlFlowGraph cfg;
	private final LocalsHandler locals;
	private SSABlockLivenessAnalyser liveness;
	private SSADefUseMap defuse;

	private final NullPermeableHashMap<Local, GenericBitSet<Local>> interfere;
	private final NullPermeableHashMap<Local, GenericBitSet<Local>> pccs;
	private final NullPermeableHashMap<PhiResource, Set<PhiResource>> unresolvedNeighborsMap;
	private final NullPermeableHashMap<BasicBlock, GenericBitSet<BasicBlock>> succsCache;
	private final Set<PhiResource> candidateResourceSet;

	public SreedharDestructor(ControlFlowGraph cfg) {
		this.cfg = cfg;
		locals = cfg.getLocals();
		interfere = new NullPermeableHashMap<>(locals);
		pccs = new NullPermeableHashMap<>(locals);
		unresolvedNeighborsMap = new NullPermeableHashMap<>(new SetCreator<>());
		defuse = new SSADefUseMap(cfg);
		defuse.compute();
		succsCache = new NullPermeableHashMap<>(key -> {
			GenericBitSet<BasicBlock> succs = cfg.createBitSet();
			cfg.getEdges(key).stream().map(e -> e.dst).forEach(succs::add);
			return succs;
		});
		candidateResourceSet = new HashSet<>();

		init();

		csaa_iii();

		coalesce();

		leaveSSA();
	}

	private void init() {
		// init pccs
		for (CopyPhiStatement copyPhi : defuse.phis.values()) {
			Local phiTarget = copyPhi.getVariable().getLocal();
			pccs.getNonNull(phiTarget).add(phiTarget);
			System.out.println("Initphi " + phiTarget);
			for (Entry<BasicBlock, Expression> phiEntry : copyPhi.getExpression().getArguments().entrySet()) {
				if (phiEntry.getValue().getOpcode() != LOCAL_LOAD)
					throw new IllegalArgumentException("Phi arg is not local; instead is " + phiEntry.getValue().getClass().getSimpleName());
				Local phiSource = ((VarExpression) phiEntry.getValue()).getLocal();
				pccs.getNonNull(phiSource).add(phiSource);
				System.out.println("Initphi " + phiSource);
			}
		}
		System.out.println();

		// compute liveness
		(liveness = new SSABlockLivenessAnalyser(cfg)).compute();
		BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
		DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, cfg);
		writer.removeAll()
				.add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.add("liveness", new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(liveness))
				.setName("destruct-init").export();

		buildInterference();
	}

	private void buildInterference() {
		for (BasicBlock b : cfg.vertices()) {
			GenericBitSet<Local> in = liveness.in(b); // not a copy!
			GenericBitSet<Local> out = liveness.out(b); // not a copy!

			// in interfere in
			for (Local l : in)
				interfere.getNonNull(l).addAll(in);

			// out interfere out
			for (Local l : out)
				interfere.getNonNull(l).addAll(out);

			// backwards traverse for dealing with variables that are defined and used in the same block
			GenericBitSet<Local> intraLive = out.copy();
			ListIterator<Statement> it = b.listIterator(b.size());
			while (it.hasPrevious()) {
				Statement stmt = it.previous();
				if (stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					Local defLocal = copy.getVariable().getLocal();
					intraLive.remove(defLocal);
				}
				for (Statement child : stmt) {
					if (stmt.getOpcode() == LOCAL_LOAD) {
						Local usedLocal = ((VarExpression) child).getLocal();
						if (intraLive.add(usedLocal)) {
							interfere.getNonNull(usedLocal).addAll(intraLive);
							for (Local l : intraLive)
								interfere.get(l).add(usedLocal);
						}
					}
				}
			}
		}

		System.out.println("Interference:");
		for (Entry<Local, GenericBitSet<Local>> entry : interfere.entrySet())
			System.out.println("  " + entry.getKey() + " : " + entry.getValue());
		System.out.println();
	}

	private void csaa_iii() {
		// iterate over each phi expression
		for (Entry<Local, CopyPhiStatement> entry : defuse.phis.entrySet()) {
			Local phiTarget = entry.getKey(); // x0
			CopyPhiStatement copy = entry.getValue();
			BasicBlock defBlock = defuse.defs.get(phiTarget); // l0
			PhiExpression phi = copy.getExpression();
			candidateResourceSet.clear();
			unresolvedNeighborsMap.clear();

			final Set<PhiResource> phiLocals = new HashSet<>();
			phiLocals.add(new PhiResource(defBlock, phiTarget, true));
			for (Entry<BasicBlock, Expression> phiEntry : phi.getArguments().entrySet())
				phiLocals.add(new PhiResource(phiEntry.getKey(), ((VarExpression) phiEntry.getValue()).getLocal(), false));

			handleInterference(phiLocals, phi);
		}
	}

	// TODO: convert <BasicBlock, Local> into some sort of a phi resource struct
	private void handleInterference(Set<PhiResource> phiLocals, PhiExpression phi) {
		for (PhiResource resI : phiLocals) {
			GenericBitSet<Local> liveOutI = liveness.out(resI.block);
			GenericBitSet<Local> pccI = pccs.get(resI.local);
			for (PhiResource resJ : phiLocals) {
				GenericBitSet<Local> pccJ = pccs.get(resJ.local);
				if (pccJ == null)
					throw new IllegalStateException("pccJ is null " + resJ);
				if (!intersects(pccI, pccJ))
					continue;
				GenericBitSet<Local> liveOutJ = liveness.out(resJ.block);

				boolean piljEmpty = pccI.intersect(liveOutJ).isEmpty();
				boolean pjliEmpty = pccJ.intersect(liveOutI).isEmpty();
				if (piljEmpty ^ pjliEmpty) {
					// case 1 and 2 - handle it asymetrically for the necessary local
					candidateResourceSet.add(piljEmpty ? resJ : resI);
				} else if (piljEmpty & pjliEmpty) {
					// case 4 - reflexively update unresolvedNeighborsMap
					unresolvedNeighborsMap.getNonNull(resI).add(resJ);
					unresolvedNeighborsMap.getNonNull(resJ).add(resI);
				} else {
					// case 3 - handle it symetrically for both locals
					candidateResourceSet.add(resI);
					candidateResourceSet.add(resJ);
				}
			}
		}

		resolveDeferred();
		System.out.println("Cand: " + candidateResourceSet);

		// Resolve the candidate resources
		Type phiType = phi.getType();
		for (PhiResource toResolve : candidateResourceSet) {
			if (toResolve.isTarget)
				resolveTarget(toResolve, phiType);
			else for (Entry<BasicBlock, Expression> phiArg : phi.getArguments().entrySet()) {
				VarExpression phiVar = (VarExpression) phiArg.getValue();
				if (phiVar.getLocal() == toResolve.local)
					phiVar.setLocal(resolvePhiSource(toResolve.local, phiArg.getKey(), phiType));
			}
		}
	}

	private void resolveDeferred() {
		while (!unresolvedNeighborsMap.isEmpty()) {
			// Pick up resources in value of decreasing size
			PhiResource largest = null;
			int largestCount = 0;
			for (Entry<PhiResource, Set<PhiResource>> entry : unresolvedNeighborsMap.entrySet()) {
				PhiResource x = entry.getKey();
				Set<PhiResource> neighbors = entry.getValue();
				int size = neighbors.size();
				if (size > largestCount) {
					if (!candidateResourceSet.contains(x) && neighbors.containsAll(candidateResourceSet))
					largestCount = size;
					largest = x;
				}
			}

			if (largestCount > 0) {
				System.out.println("  Add " + largest + " by case 4");
				candidateResourceSet.add(largest);
				unresolvedNeighborsMap.remove(largest);
			}
		}
	}

	private boolean intersects(GenericBitSet<Local> pccI, GenericBitSet<Local> pccJ) {
		for (Local yi : pccI)
			for (Local yj : pccJ) { // this right here is the reason mr. boissinot roasted you 10 years later
				if (interfere.get(yi) == null)
					throw new IllegalStateException("yi igraph null, pccI=" + pccI + " ; pccJ=" + pccJ + " ; yi=" + yi);
				if (interfere.get(yi).contains(yj))
					return true;
			}
		return false;
	}

	private void resolveTarget(PhiResource res, Type phiType) {
		Local spill = insertStart(res, phiType); // Insert spill copy

		// Update liveness
		GenericBitSet<Local> liveIn = liveness.in(res.block);
		liveIn.remove(res.local);
		liveIn.add(spill);

		// Reflexively update interference
		interfere.getNonNull(spill).addAll(liveIn);
		for (Local l : liveIn)
			interfere.get(l).add(spill);
	}

	// replace the phi target xi with xi' and place a temp copy xi = xi' after all phi statements.
	private Local insertStart(PhiResource res, Type type) {
		BasicBlock li = res.block;
		Local xi = res.local;
		if(li.isEmpty())
			throw new IllegalStateException("Trying to resolve phi target interference in empty block " + li);

		Local spill = locals.makeLatestVersion(xi);
		int i;
		for (i = 0; i < li.size() && li.get(i).getOpcode() == Opcode.PHI_STORE; i++) {
			CopyPhiStatement copyPhi = (CopyPhiStatement) li.get(i);
			VarExpression copyTarget = copyPhi.getVariable();
			if (copyTarget.getLocal() == xi)
				copyTarget.setLocal(spill);
		}
		li.add(i, new CopyVarStatement(new VarExpression(xi, type), new VarExpression(spill, type)));
		return spill;
	}

	private Local resolvePhiSource(Local xi, BasicBlock lk, Type phiType) {
		// Insert spill copy
		Local spill = insertEnd(xi, lk, phiType);

		// Update liveness
		GenericBitSet<Local> liveOut = liveness.out(lk);
		liveOut.add(spill);
		removeFromOut: { // xi can be removed from liveOut iff it isn't live into any succ or used in any succ phi.
			for (BasicBlock lj : succsCache.getNonNull(lk))
				if (liveness.in(lj).contains(xi))
					break removeFromOut;
				else for (int i = 0; i < lj.size() && lj.get(i).getOpcode() == Opcode.PHI_STORE; i++)
					if (((VarExpression) ((CopyPhiStatement) lj.get(i)).getExpression().getArguments().get(lk)).getLocal() == xi)
						break removeFromOut;
			liveOut.remove(xi); // poor man's for-else loop
		}

		// Reflexively update interference
		interfere.getNonNull(spill).addAll(liveOut);
		for (Local l : liveOut)
			interfere.get(l).add(spill);

		return spill;
	}

	Local insertEnd(Local xi, BasicBlock lk, Type type) {
		Local spill = locals.makeLatestVersion(xi);
		CopyVarStatement newCopy = new CopyVarStatement(new VarExpression(spill, type), new VarExpression(xi, type));
		if(lk.isEmpty())
			lk.add(newCopy);
		else if(!lk.get(lk.size() - 1).canChangeFlow())
			lk.add(newCopy);
		else
			lk.add(lk.size() - 1, newCopy);
		return spill;
	}

	private void coalesce() {
		// TODO
	}

	private void leaveSSA() {
		// TODO
	}

	private class PhiResource {
		BasicBlock block;
		Local local;
		boolean isTarget;

		public PhiResource(BasicBlock block, Local local, boolean isTarget) {
			this.local = local;
			this.block = block;
			this.isTarget = isTarget;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;

			PhiResource that = (PhiResource) o;

			if (!local.equals(that.local))
				return false;
			return block.equals(that.block);

		}

		@Override
		public int hashCode() {
			int result = local.hashCode();
			result = 31 * result + block.hashCode();
			return result;
		}

		@Override
		public String toString() {
			return block.toString() + ":" + local + (isTarget? " (target) " : "");
		}
	}
}