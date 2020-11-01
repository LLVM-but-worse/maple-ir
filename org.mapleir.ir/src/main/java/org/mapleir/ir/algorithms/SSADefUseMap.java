package org.mapleir.ir.algorithms;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.collections.map.NullPermeableHashMap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class SSADefUseMap implements Opcode {
	
	private final ControlFlowGraph cfg;
	public final Map<Local, BasicBlock> defs;
	public final NullPermeableHashMap<Local, GenericBitSet<BasicBlock>> uses;
	public final Map<Local, CopyPhiStmt> phiDefs;
	public final NullPermeableHashMap<BasicBlock, GenericBitSet<Local>> phiUses;

	public final NullPermeableHashMap<Local, LinkedHashMap<BasicBlock, Integer>> lastUseIndex;
	public final LinkedHashMap<Local, Integer> defIndex;

	public SSADefUseMap(ControlFlowGraph cfg) {
		this.cfg = cfg;
		defs = new LinkedHashMap<>();
		uses = new NullPermeableHashMap<>(cfg);
		phiDefs = new LinkedHashMap<>();
		phiUses = new NullPermeableHashMap<>(cfg.getLocals());

		lastUseIndex = new NullPermeableHashMap<>(LinkedHashMap::new);
		defIndex = new LinkedHashMap<>();
	}

	public void compute() {
		defs.clear();
		uses.clear();
		phiDefs.clear();
		phiUses.clear();
		Set<Local> usedLocals = new LinkedHashSet<>();
		for(BasicBlock b : cfg.verticesInOrder()) {
			for(Stmt stmt : b) {
				phiUses.getNonNull(b);

				usedLocals.clear();
				for (Expr s : stmt.enumerateOnlyChildren())
					if(s.getOpcode() == Opcode.LOCAL_LOAD)
						usedLocals.add(((VarExpr) s).getLocal());

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
		Set<Local> usedLocals = new LinkedHashSet<>();
		for (BasicBlock b : preorder) {
			for (Stmt stmt : b) {
				phiUses.getNonNull(b);

				usedLocals.clear();
				for(Expr s : stmt.enumerateOnlyChildren())
					if(s.getOpcode() == Opcode.LOCAL_LOAD)
						usedLocals.add(((VarExpr) s).getLocal());

				buildIndex(b, stmt, index++, usedLocals);
				build(b, stmt, usedLocals);
			}
		}
	}

	protected void build(BasicBlock b, Stmt stmt, Set<Local> usedLocals) {
		if(stmt instanceof AbstractCopyStmt) {
			AbstractCopyStmt copy = (AbstractCopyStmt) stmt;
			Local l = copy.getVariable().getLocal();
			defs.put(l, b);

			if(copy instanceof CopyPhiStmt) {
				phiDefs.put(l, (CopyPhiStmt) copy);
				PhiExpr phi = (PhiExpr) copy.getExpression();
				for(Entry<BasicBlock, Expr> en : phi.getArguments().entrySet()) {
					Local ul = ((VarExpr) en.getValue()).getLocal();
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
		if (stmt instanceof AbstractCopyStmt) {
			AbstractCopyStmt copy = (AbstractCopyStmt) stmt;
			defIndex.put(copy.getVariable().getLocal(), index);

			if (copy instanceof CopyPhiStmt) {
				PhiExpr phi = ((CopyPhiStmt) copy).getExpression();
				for (Entry<BasicBlock, Expr> en : phi.getArguments().entrySet()) {
					lastUseIndex.getNonNull(((VarExpr) en.getValue()).getLocal()).put(en.getKey(), en.getKey().size());
//					lastUseIndex.get(ul).put(b, -1);
				}
				return;
			}
		}

		for (Local usedLocal : usedLocals)
			lastUseIndex.getNonNull(usedLocal).put(b, index);
	}
}