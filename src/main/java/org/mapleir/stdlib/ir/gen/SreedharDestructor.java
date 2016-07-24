package org.mapleir.stdlib.ir.gen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;

public class SreedharDestructor {

	final CodeBody code;
	final ControlFlowGraph cfg;
	final NullPermeableHashMap<BasicBlock, Set<CopyVarStatement>> phis;
	final Map<Local, CopyVarStatement> phiDefs;
	final Map<Local, Set<Local>> resources;
	final Map<Local, Set<Local>> pcc;
	final Map<Local, Set<Local>> unresolved;

	public SreedharDestructor(CodeBody code, ControlFlowGraph cfg) {
		this.code = code;
		this.cfg = cfg;
		phis = new NullPermeableHashMap<>(new SetCreator<>());
		phiDefs = new HashMap<>();
		pcc = new HashMap<>();
		unresolved = new HashMap<>();
		resources = new HashMap<>();

		init();
		for(Entry<Local, Set<Local>> e : pcc.entrySet()) {
			System.out.println(e.getKey() + " = " + e.getValue());
		}
	}
	
	void handle(BasicBlock li, CopyVarStatement copy) {
		Local _l = copy.getVariable().getLocal();
		Set<Local> res = resources.get(_l);
		
		for(Local xi : res) {
			for(Local xj : res) {
				if(xi == xj) {
					continue;
				}
			}
		}
	}

	void handle() {
		for(Entry<BasicBlock, Set<CopyVarStatement>> e : phis.entrySet()) {
			BasicBlock b = e.getKey();
			Set<CopyVarStatement> copies = e.getValue();
			for(CopyVarStatement cp : copies) {
				handle(b, cp);
			}
		}
	}
	
	void map_phi(VarExpression v, Set<Local> srcs) {
		Local l = v.getLocal();
		srcs.add(l);
		if(!pcc.containsKey(l)) {
			Set<Local> set = new HashSet<>();
			set.add(l);
			pcc.put(l, set);
		}
		
		if(!unresolved.containsKey(l)) {
			unresolved.put(l, new HashSet<>());
		}
	}
	
	void init_phi(BasicBlock b, CopyVarStatement copy) {
		PhiExpression phi = (PhiExpression) copy.getExpression();
		Set<Local> srcs = new HashSet<>();
		
		for(Expression e1 : phi.getLocals().values()) {
			if(!(e1 instanceof VarExpression)) {
				throw new IllegalStateException("Cannot perform Sreedhar 3 with propagated phis: " + copy);
			}

			// collect phis
			phis.getNonNull(b).add(copy);
			
			// phiCongruenceClass
			map_phi(copy.getVariable(), srcs);
			for(Statement s : Statement.enumerate_deep(e1)) {
				if(s instanceof VarExpression) {
					map_phi((VarExpression) s, srcs);
				}
			}
		}
		
		Local l = copy.getVariable().getLocal();
		resources.put(l, srcs);
		phiDefs.put(l, copy);
	}

	void init() {
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b.getStatements()) {
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					if(copy.getExpression() instanceof PhiExpression) {
						init_phi(b, copy);
					}
				}
			}
		}
	}
}