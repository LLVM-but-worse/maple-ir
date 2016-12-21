package org.mapleir.ir.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;

public class SSADefUseMap implements Opcode {
	
	private final ControlFlowGraph cfg;
	public final Map<Local, BasicBlock> defs;
	public final NullPermeableHashMap<Local, GenericBitSet<BasicBlock>> uses;
	public final Map<Local, CopyPhiStatement> phiDefs;
	public final NullPermeableHashMap<BasicBlock, GenericBitSet<Local>> phiUses;

	public final NullPermeableHashMap<Local, HashMap<BasicBlock, Integer>> lastUseIndex;
	public final HashMap<Local, Integer> defIndex;

	public SSADefUseMap(ControlFlowGraph cfg) {
		this.cfg = cfg;
		defs = new HashMap<>();
		uses = new NullPermeableHashMap<>(cfg);
		phiDefs = new HashMap<>();
		phiUses = new NullPermeableHashMap<>(cfg.getLocals());

		lastUseIndex = new NullPermeableHashMap<>(new ValueCreator<HashMap<BasicBlock, Integer>>() {
			@Override
			public HashMap<BasicBlock, Integer> create() {
				return new HashMap<>();
			}
		});
		defIndex = new HashMap<>();
	}

	public void compute() {
		defs.clear();
		uses.clear();
		phiDefs.clear();
		phiUses.clear();
		Set<Local> usedLocals = new HashSet<>();
		for(BasicBlock b : cfg.vertices()) {
			for(Stmt stmt : b) {
				phiUses.getNonNull(b);

				usedLocals.clear();
				for (Expr s : stmt.enumerateOnlyChildren())
					if(s.getOpcode() == Opcode.LOCAL_LOAD)
						usedLocals.add(((VarExpression) s).getLocal());

				build(b, stmt, usedLocals);
			}
		}
	}

	public void computeWithIndices(List<BasicBlock> preorder) {
		defs.clear();
		uses.clear();
		phiDefs.clear();
		phiUses.clear();
		lastUseIndex.clear();
		defIndex.clear();
		int index = 0;
		Set<Local> usedLocals = new HashSet<>();
		for (BasicBlock b : preorder) {
			for (Stmt stmt : b) {
				phiUses.getNonNull(b);

				usedLocals.clear();
				for(Expr s : stmt.enumerateOnlyChildren())
					if(s.getOpcode() == Opcode.LOCAL_LOAD)
						usedLocals.add(((VarExpression) s).getLocal());

				buildIndex(b, stmt, index++, usedLocals);
				build(b, stmt, usedLocals);
			}
		}
	}

	protected void build(BasicBlock b, Stmt stmt, Set<Local> usedLocals) {
		if(stmt instanceof AbstractCopyStatement) {
			AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
			Local l = copy.getVariable().getLocal();
			defs.put(l, b);

			if(copy instanceof CopyPhiStatement) {
				phiDefs.put(l, (CopyPhiStatement) copy);
				PhiExpression phi = (PhiExpression) copy.getExpression();
				for(Entry<BasicBlock, Expr> en : phi.getArguments().entrySet()) {
					Local ul = ((VarExpression) en.getValue()).getLocal();
					uses.getNonNull(ul).add(en.getKey());
					phiUses.get(b).add(ul);
				}
				return;
			}
		}

		for (Local usedLocal : usedLocals)
			uses.getNonNull(usedLocal).add(b);
	}

	protected void buildIndex(BasicBlock b, Stmt stmt, int index, Set<Local> usedLocals) {
		if (stmt instanceof AbstractCopyStatement) {
			AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
			defIndex.put(copy.getVariable().getLocal(), index);

			if (copy instanceof CopyPhiStatement) {
				PhiExpression phi = ((CopyPhiStatement) copy).getExpression();
				for (Entry<BasicBlock, Expr> en : phi.getArguments().entrySet()) {
					lastUseIndex.getNonNull(((VarExpression) en.getValue()).getLocal()).put(en.getKey(), en.getKey().size());
//					lastUseIndex.get(ul).put(b, -1);
				}
				return;
			}
		}

		for (Local usedLocal : usedLocals)
			lastUseIndex.getNonNull(usedLocal).put(b, index);
	}
}