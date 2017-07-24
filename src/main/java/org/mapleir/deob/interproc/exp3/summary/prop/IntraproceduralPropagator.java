package org.mapleir.deob.interproc.exp3.summary.prop;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.mapleir.deob.interproc.exp3.summary.Heap;
import org.mapleir.deob.interproc.exp3.summary.LocalSummary;
import org.mapleir.deob.interproc.exp3.summary.LocalSummary.SSALocalSummary;
import org.mapleir.deob.interproc.exp3.summary.Value;
import org.mapleir.deob.interproc.exp3.summary.Value.ValueSet;
import org.mapleir.deob.intraproc.eval.ExpressionEvaluator;
import org.mapleir.deob.intraproc.eval.impl.ReflectiveFunctorFactory;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.LocalsPool;

public class IntraproceduralPropagator {

	private LinkedList<BasicBlock> worklist;
	private Set<BasicBlock> visited = new HashSet<>();
	private Map<BasicBlock, LocalSummary> summaries = new HashMap<>();
	private ExpressionEvaluator eval = new ExpressionEvaluator(new ReflectiveFunctorFactory());
	
	public LocalSummary computeInitialSummary(ControlFlowGraph cfg) {
		LocalsPool pool = cfg.getLocals();

		Heap heap = new Heap();
		LocalSummary summary = newSummary();
		
		if(!Modifier.isStatic(cfg.getMethod().access)) {
			summary.setLocalValue(pool.get(0, 0, false), ValueSet.make(Value.SELF_OBJECT));
		}
		
		BasicBlock entry = cfg.getEntries().iterator().next();
		summary = summariseBlock(entry, summary, heap);
		
	}
	
	private LocalSummary summariseBlock(BasicBlock block, LocalSummary inputSummary, Heap heap) {
		LocalSummary summary = newSummary(inputSummary);
		
		Iterator<Stmt> stmtsIterator = block.iterator();
		while(stmtsIterator.hasNext()) {
			Stmt stmt = stmtsIterator.next();
			int opcode = stmt.getOpcode();
			
			if(opcode == Opcode.LOCAL_STORE) {
				CopyVarStmt copyVarStmt = (CopyVarStmt) stmt;
				
				summary.setLocalValue(copyVarStmt.getVariable().getLocal(), createValue(copyVarStmt.getExpression()));
			} else if(opcode == Opcode.PHI_STORE) {
				CopyPhiStmt copyPhiStmt = (CopyPhiStmt) stmt;
				
				summary.setLocalValue(copyPhiStmt.getVariable().getLocal(), createValueSet(copyPhiStmt.getExpression()));
			}
		}
		
		return summary;
	}
	
	private LocalSummary newSummary() {
		return new SSALocalSummary();
	}
	
	private LocalSummary newSummary(LocalSummary summary) {
		return new SSALocalSummary(summary);
	}

	private ValueSet createValueSet(PhiExpr phi) {
		ValueSet res = new ValueSet();
		for(Expr e : phi.getArguments().values()) {
			res = res.merge(createValue(e));
		}
		return res;
	}
	
	private ValueSet createValue(Expr e) {
		ConstantExpr val = eval.eval(e.getBlock().getGraph().getLocals(), e);
		
		if(val != null) {
			return ValueSet.make(new Value.ConstExprValue(val));
		} else {
			return Value.TOPSET;
		}
	}
}