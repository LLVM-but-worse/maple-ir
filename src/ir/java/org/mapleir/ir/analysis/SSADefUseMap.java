package org.mapleir.ir.analysis;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class SSADefUseMap implements Opcode {
	
	private final ControlFlowGraph cfg;
	public final Map<Local, BasicBlock> defs;
	public final NullPermeableHashMap<Local, GenericBitSet<BasicBlock>> uses;
	public final HashMap<Local, PhiExpression> phis;

	public NullPermeableHashMap<Local, HashMap<BasicBlock, Integer>> lastUseIndex;
	public HashMap<Local, Integer> defIndex;

	public SSADefUseMap(ControlFlowGraph cfg) {
		this.cfg = cfg;
		defs = new HashMap<>();
		uses = new NullPermeableHashMap<>(cfg);
		phis = new HashMap<>();
	}

	public void compute() {
		defs.clear();
		uses.clear();
		phis.clear();
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b) {
				build(b, stmt);
			}
		}
	}

	protected void build(BasicBlock b, Statement stmt) {
		int opcode = stmt.getOpcode();
		boolean isPhi = opcode == PHI_STORE;
		boolean isCopy = isPhi || opcode == LOCAL_STORE;

		if(isCopy) {
			AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
			Local l = copy.getVariable().getLocal();
			defs.put(l, b);
			uses.getNonNull(l);

			if(isPhi) {
				PhiExpression phi = (PhiExpression) copy.getExpression();
				for(Entry<BasicBlock, Expression> en : phi.getArguments().entrySet()) {
					Local ul = ((VarExpression) en.getValue()).getLocal();
					uses.getNonNull(ul).add(en.getKey());
				}
				phis.put(l, phi);
			}
		}

		if(!isPhi) {
			for(Statement s : stmt) {
				if(s.getOpcode() == LOCAL_LOAD) {
					Local l = ((VarExpression) s).getLocal();
					uses.getNonNull(l).add(b);
				}
			}
		}
	}

	public void buildIndices(List<BasicBlock> preorder) {
		lastUseIndex = new NullPermeableHashMap<>(new ValueCreator<HashMap<BasicBlock, Integer>>() {
			@Override
			public HashMap<BasicBlock, Integer> create() {
				return new HashMap<>();
			}
		});
		defIndex = new HashMap<>();

		int index = 0;
		for (BasicBlock b : preorder) {
			for (Statement stmt : b)
				index = buildIndex(b, stmt, index);
		}
	}

	protected int buildIndex(BasicBlock b, Statement stmt, int index) {
		if (stmt instanceof AbstractCopyStatement) {
			AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
			defIndex.put(copy.getVariable().getLocal(), index);
			if (copy instanceof CopyPhiStatement) {
				CopyPhiStatement copyPhi = (CopyPhiStatement) copy;
				PhiExpression phi = copyPhi.getExpression();
				for (Entry<BasicBlock, Expression> en : phi.getArguments().entrySet()) {
					Local ul = ((VarExpression) en.getValue()).getLocal();
					lastUseIndex.getNonNull(ul).put(en.getKey(), en.getKey().size());
//					lastUseIndex.getNonNull(ul).put(b, -1);
				}
			}
		}
		for (Statement child : stmt)
			if (child.getOpcode() == Opcode.LOCAL_LOAD)
				lastUseIndex.getNonNull(((VarExpression) child).getLocal()).put(b, index);
		return ++index;
	}
}