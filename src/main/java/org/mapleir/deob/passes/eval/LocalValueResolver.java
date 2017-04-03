package org.mapleir.deob.passes.eval;

import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public interface LocalValueResolver {
	Set<Expr> getValues(Local l);
	
	class PooledLocalValueResolver implements LocalValueResolver {
		
		final LocalsPool pool;
		
		public PooledLocalValueResolver(LocalsPool pool) {
			this.pool = pool;
		}
		
		@Override
		public Set<Expr> getValues(Local l) {
			AbstractCopyStmt copy = pool.defs.get(l);
			
			Set<Expr> set = new HashSet<>();
			set.add(copy.getExpression());
			return set;
		}
	}
	
	class SemiConstantLocalValueResolver implements LocalValueResolver {
		private final MethodNode method;
		private final LocalsPool pool;
		private final IPConstAnalysisVisitor vis;
		
		public SemiConstantLocalValueResolver(MethodNode method, LocalsPool pool, IPConstAnalysisVisitor vis) {
			this.method = method;
			this.pool = pool;
			this.vis = vis;
		}

		@Override
		public Set<Expr> getValues(Local l) {
			Set<Expr> set = new HashSet<>();
			
			AbstractCopyStmt copy = pool.defs.get(l);
			if(copy.isSynthetic()) {
				VarExpr vE = (VarExpr) copy.getExpression();
				if(vE.getLocal() != l) {
					throw new IllegalStateException(copy + " : " + l);
				}
				
				int paramNum = copy.getBlock().indexOf(copy);
				if(!Modifier.isStatic(method.access)) {
					/* for a virtual call, the implicit
					 * this object isn't considered a
					 * parameter, so the current computed
					 * paramNum is off by +1 (as it is
					 * including the lvar0_0 synth def). */
					paramNum -= 1;
				}
				
				if(!vis.unconst.get(method)[paramNum]) {
					set.addAll(vis.constParams.get(method).get(paramNum));
				}
			} else {
				set.add(copy.getExpression());
			}
			
			return set;
		}
	}
}
