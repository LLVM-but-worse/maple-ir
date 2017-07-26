package org.mapleir.deob.intraproc.eval;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.stdlib.collections.taint.TaintableSet;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Provides possible values a local could hold in a CFG.
 */
public interface LocalValueResolver {
	/**
	 *
	 * @param cfg Method to provide value relevant for
	 * @param l Local to provide value for
	 * @return Taintable set of possible values the local could represent
	 */
	TaintableSet<Expr> getValues(ControlFlowGraph cfg, Local l);
}
