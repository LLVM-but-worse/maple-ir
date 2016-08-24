package org.mapleir.ir.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.BoissinotDestructor;
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
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;

public class SSADefUseMap implements Opcode {
	
	private final ControlFlowGraph cfg;
	public final Map<Local, BasicBlock> defs;
	public final NullPermeableHashMap<Local, Set<BasicBlock>> uses;
	public final HashMap<Local, PhiExpression> phis;

	public NullPermeableHashMap<Local, HashMap<BasicBlock, Integer>> lastUseIndex;
	public HashMap<Local, Integer> defIndex;

	public SSADefUseMap(ControlFlowGraph cfg, boolean compute) {
		this.cfg = cfg;
		defs = new HashMap<>();
		uses = new NullPermeableHashMap<>(new SetCreator<>());
		phis = new HashMap<>();

		if(compute) {
			build(cfg);
			verify();
		}
	}

	private void build(ControlFlowGraph cfg) {
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b) {
				build(b, stmt);
			}
		}
	}

	private void build(BasicBlock b, Statement stmt) {
		int opcode = stmt.getOpcode();
		boolean isPhi = opcode == PHI_STORE;
		boolean isCopy = isPhi || opcode == LOCAL_STORE;

		if(isCopy) {
			AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
			Local l = copy.getVariable().getLocal();
			defs.put(l, b);

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

	public void verify() {
		Map<Local, BasicBlock> defs = new HashMap<>();
		NullPermeableHashMap<Local, Set<BasicBlock>> uses = new NullPermeableHashMap<>(new SetCreator<>());

		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b) {
				int opcode = stmt.getOpcode();
				boolean isPhi = opcode == PHI_STORE;
				boolean isCopy = isPhi || opcode == LOCAL_STORE;

				if(isCopy) {
					AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
					Local l = copy.getVariable().getLocal();
					defs.put(l, b);

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
		}
		
		Set<Local> set = new HashSet<>();
		set.addAll(defs.keySet());
		set.addAll(this.defs.keySet());
		
		for(Local l : set) {
			BasicBlock b1 = defs.get(l);
			BasicBlock b2 = this.defs.get(l);
			
			if(b1 != b2) {
				System.err.println(cfg);
				System.err.println("Defs:");
				System.err.println(b1 + ", " + b2 + ", " + l);
				throw new RuntimeException();
			}
		}
		
		set.clear();
		set.addAll(uses.keySet());
		set.addAll(this.uses.keySet());
		
		for(Local l : set) {
			Set<BasicBlock> s1 = uses.getNonNull(l);
			Set<BasicBlock> s2 = this.uses.getNonNull(l);
			if(!s1.equals(s2)) {
				System.err.println(cfg);
				System.err.println("Uses:");
				System.err.println(GraphUtils.toBlockArray(s1));
				System.err.println(GraphUtils.toBlockArray(s2));
				System.err.println(l);
				throw new RuntimeException();
			}
		}
	}
}