package org.mapleir.ir.cfg;

import org.mapleir.ir.analysis.SSADefUseMap;
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
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import static org.mapleir.ir.code.Opcode.LOCAL_LOAD;

public class SreedharDestructor {

	private final ControlFlowGraph cfg;
	private final LocalsHandler locals;
	private SSABlockLivenessAnalyser liveness;
	private SSADefUseMap defuse;

	private final NullPermeableHashMap<Local, GenericBitSet<Local>> interfere;
	private final NullPermeableHashMap<Local, GenericBitSet<Local>> pccs;
	private final NullPermeableHashMap<Local, GenericBitSet<Local>> unresolvedNeighborsMap;
	private final NullPermeableHashMap<BasicBlock, GenericBitSet<BasicBlock>> succsCache;
	private final Map<Local, BasicBlock> candidateResourceSet;

	public SreedharDestructor(ControlFlowGraph cfg) {
		this.cfg = cfg;
		locals = cfg.getLocals();
		interfere = new NullPermeableHashMap<>(locals);
		pccs = new NullPermeableHashMap<>(locals);
		unresolvedNeighborsMap = new NullPermeableHashMap<>(locals);
		defuse = new SSADefUseMap(cfg);
		defuse.compute();
		succsCache = new NullPermeableHashMap<>(key -> {
			GenericBitSet<BasicBlock> succs = cfg.createBitSet();
			cfg.getEdges(key).stream().map(e -> e.dst).forEach(succs::add);
			return succs;
		});
		candidateResourceSet = new HashMap<>();

		init();

		csaa_iii();

		coalesce();

		leaveSSA();
	}

	private void init() {
		// init pccs
		for (Local l : defuse.phis.keySet())
			pccs.getNonNull(l).add(l);

		// compute liveness
		(liveness = new SSABlockLivenessAnalyser(cfg)).compute();

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
						}
					}
				}
			}
		}
	}

	private void csaa_iii() {
		// iterate over each phi expression
		for (Entry<Local, CopyPhiStatement> entry : defuse.phis.entrySet()) {
			Local phiTarget = entry.getKey(); // x0
			CopyPhiStatement copy = entry.getValue();
			if (phiTarget != copy.getVariable().getLocal()) // one iteration per phi expression. 1-1 correspondence
				continue;

			BasicBlock defBlock = defuse.defs.get(phiTarget); // l0
			PhiExpression phi = copy.getExpression();
			candidateResourceSet.clear();
			unresolvedNeighborsMap.clear();

			final Map<BasicBlock, Local> phiLocals = new HashMap<>();
			phiLocals.put(defBlock, phiTarget);
			for (Entry<BasicBlock, Expression> phiEntry : phi.getArguments().entrySet()) {
				if (phiEntry.getValue().getOpcode() != LOCAL_LOAD)
					throw new IllegalArgumentException("Phi arg is not local; instead is " + phiEntry.getValue().getClass().getSimpleName());
				phiLocals.put(phiEntry.getKey(), ((VarExpression) phiEntry.getValue()).getLocal());
			}

			handleInterference(phiLocals, phiTarget, phi);
		}
	}

	// TODO: convert <BasicBlock, Local> into some sort of a phi resource struct
	private void handleInterference(Map<BasicBlock, Local> phiLocals, Local phiTarget, PhiExpression phi) {
		for (Entry<BasicBlock, Local> phiLocalI : phiLocals.entrySet()) {
			BasicBlock li = phiLocalI.getKey();
			Local xi = phiLocalI.getValue();
			GenericBitSet<Local> liveOutI = liveness.out(li);
			GenericBitSet<Local> pccI = pccs.get(xi);
			for (Entry<BasicBlock, Local> phiLocalJ : phiLocals.entrySet()) {
				BasicBlock lj = phiLocalJ.getKey();
				Local xj = phiLocalJ.getValue();
				GenericBitSet<Local> pccJ = pccs.get(xj);
				if (!intersects(pccI, pccJ))
					continue;

				GenericBitSet<Local> liveOutJ = liveness.out(lj);
				boolean piljEmpty = pccI.intersect(liveOutJ).isEmpty();
				boolean pjliEmpty = pccJ.intersect(liveOutI).isEmpty();

				if (piljEmpty ^ pjliEmpty) {
					// case 1 and 2 - handle it asymetrically for the necessary local
					candidateResourceSet.put(piljEmpty ? xj : xi, piljEmpty ? lj : li);
				} else if (piljEmpty & pjliEmpty) {
					// case 4 - reflexively update unresolvedNeighborsMap
					unresolvedNeighborsMap.getNonNull(xi).add(xj);
					unresolvedNeighborsMap.getNonNull(xj).add(xi);
				} else {
					// case 3 - handle it symetrically for both locals
					candidateResourceSet.put(xi, li);
					candidateResourceSet.put(xj, lj);
				}
			}
		}

		resolveDeferred();

		// Resolve the candidate resources
		for (Entry<Local, BasicBlock> toResolve : candidateResourceSet.entrySet()) {
			if (toResolve == phiTarget)
				resolveTarget(toResolve.getKey(), toResolve.getValue(), phi);
			else for (Entry<BasicBlock, Expression> phiArg : phi.getArguments().entrySet()) {
				VarExpression phiVar = (VarExpression) phiArg.getValue();
				if (phiVar.getLocal() == toResolve.getKey())
					resolvePhiSource(toResolve.getKey(), phiArg.getKey(), phiVar, phi);
			}
		}
	}

	private void resolveDeferred() {
		// TODO
	}

	private boolean intersects(GenericBitSet<Local> pccI, GenericBitSet<Local> pccJ) {
		for (Local yi : pccI)
			for (Local yj : pccJ) // this right here is the reason mr. boissinot roasted you 10 years later
				if (interfere.get(yi).contains(yj))
					return true;
		return false;
	}

	private void resolveTarget(Local x0, BasicBlock l0, PhiExpression phi) {
		Local spill = insertStart(x0, l0, phi.getType()); // Insert spill copy

		// Update liveness
		GenericBitSet<Local> liveIn = liveness.in(l0);
		liveIn.remove(x0);
		liveIn.add(spill);

		// Reflexively update interference
		interfere.getNonNull(spill).addAll(liveIn);
		for (Local l : liveIn)
			interfere.get(l).add(spill);
	}

	// replace the phi target xi with xi' and place a temp copy xi = xi' after all phi statements.
	private Local insertStart(Local xi, BasicBlock li, Type type) {
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

	private void resolvePhiSource(Local xi, BasicBlock lk, VarExpression phiVar, PhiExpression phi) {
		// Insert spill copy
		Local spill = insertEnd(xi, lk, phi);
		phiVar.setLocal(spill);

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
	}

	Local insertEnd(Local xi, BasicBlock lk, PhiExpression phi) {
		Type type = phi.getType();
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
}