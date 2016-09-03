package org.mapleir.ir.cfg;

import org.mapleir.ir.analysis.SSADefUseMap;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsHandler;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.Map;

public class SreedharDestructor {

	private final ControlFlowGraph cfg;
	private SSABlockLivenessAnalyser liveness;
	private SSADefUseMap defuse;
	private final NullPermeableHashMap<Local, GenericBitSet<Local>> interfere;
	private final LocalsHandler locals;
	private final Map<Local, GenericBitSet<Local>> pccs;

	public SreedharDestructor(ControlFlowGraph cfg) {
		this.cfg = cfg;
		locals = cfg.getLocals();
		interfere = new NullPermeableHashMap<>(locals);
		defuse = new SSADefUseMap(cfg);
		defuse.compute();
		pccs = new HashMap<>();

		init();
		csaa_iii();

		coalesce();

		leaveSSA();
	}

	private void recomputeLiveness() {
		(liveness = new SSABlockLivenessAnalyser(cfg)).compute();
	}

	private void init() {}

	void csaa_iii() {
	}

	private void coalesce() {

	}

	private void leaveSSA() {

	}

	private boolean intersects(GenericBitSet<Local> a, GenericBitSet<Local> b) {
		return false;
	}

	private boolean interfere(Local a, Local b) {
		return false;
	}

	class ResolverFrame {
		final PhiResource ri;
		final PhiResource rj;
		final GenericBitSet<Local> ipcc;
		final GenericBitSet<Local> jpcc;
		final GenericBitSet<Local> iLive;
		final GenericBitSet<Local> jLive;

		// only xi can be the target of a phi where l0 is the definition block
		ResolverFrame(BasicBlock l0, PhiResource ri, PhiResource rj, boolean target) {
			this.ri = ri;
			this.rj = rj;

			ipcc = pccs.get(ri.local);
			jpcc = pccs.get(rj.local);

			if(target) {
				iLive = liveness.in(l0);
			} else {
				iLive = liveness.in(ri.block);
			}

			jLive = liveness.out(rj.block);
		}

		boolean check_interference() {
			if(ipcc == null || jpcc == null) {
				return true;
			}
			// such that there exists yi in phiCongruenceClass[xi], yj in phiCongruenceClass[xj],
			// and yi and yj interfere with each other,

			for(Local i : ipcc){
				for(Local j : jpcc)  {
					if(i != j && interfere(i, j)) {
						return true;
					}
				}
			}

			return false;
		}

		public Case find_case() {
			boolean b1 = intersects(ipcc, jLive);
			boolean b2 = intersects(jpcc, iLive);
			if(b1 && !b2) {
				return Case.FIRST;
			} else if(!b1 && b2) {
				return Case.SECOND;
			} else if(b1 && b2) {
				return Case.THIRD;
			} else {
				return Case.FOURTH;
			}
		}
	}

	enum Case {
		FIRST,
		SECOND,
		THIRD,
		FOURTH;
	}

	static class PhiResource {
		final BasicBlock block;
		final Local local;
		final boolean target;
		final Type type;

		PhiResource(BasicBlock block, Local local, boolean target, Type type) {
			this.block = block;
			this.local = local;
			this.target = target;
			this.type = type;
		}

		@Override
		public String toString() {
			return block.getId() + ":" + local + "(" + type + ")" + " (targ=" + Boolean.toString(target).substring(0, 1) + ")";
		}
	}
}