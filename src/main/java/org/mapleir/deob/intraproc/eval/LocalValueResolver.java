package org.mapleir.deob.intraproc.eval;

import java.util.HashSet;
import java.util.Set;

import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;

public interface LocalValueResolver {
	Set<Expr> getValues(Local l);
	
	public static class PoolLocalValueResolver implements LocalValueResolver {
		
		final LocalsPool pool;
		
		public PoolLocalValueResolver(LocalsPool pool) {
			this.pool = pool;
		}
		
		@Override
		public Set<Expr> getValues(Local l) {
			AbstractCopyStmt copy = pool.defs.get(l);
			
			Set<Expr> set = new HashSet<>();
			if(copy.getOpcode() == Opcode.PHI_STORE) {
				PhiExpr phi = ((CopyPhiStmt) copy).getExpression();
				for(Expr e : phi.getArguments().values()) {
					if(e.getOpcode() == Opcode.LOCAL_LOAD) {
						Local l2 = ((VarExpr) e).getLocal();
						
						if(l2 == l) {
							throw new RuntimeException(copy.toString());
						}
					}
				}
				set.addAll(phi.getArguments().values());
			} else {
				set.add(copy.getExpression());
			}
			return set;
		}
	}
}
