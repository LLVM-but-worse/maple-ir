package org.rsdeob.stdlib.ir.transform.ssa;

import java.util.Set;

import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.PhiExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.Transformer;
import org.

import com.sun.corba.se.impl.orbutil.closure.Constant;rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public class SSAPropagator extends Transformer {

	public SSAPropagator(CodeBody code, CodeAnalytics analytics) {
		super(code, analytics);
	}

	@Override
	public int run() {
		for(Statement stmt : code) {
			new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						Local l = ((VarExpression) s).getLocal();
						if(l instanceof VersionedLocal) {
							Set<CopyVarStatement> defs = analytics.definitions.in(stmt).get(l);
							if(defs.size() == 1) {
								CopyVarStatement def = defs.iterator().next();
								if(def.getExpression() instanceof PhiExpression) {
									System.out.println("skipping phi at " + def);
									return s;
								}
								transform(stmt, s, def);
							} else {
								throw new UnsupportedOperationException("Only SSA body allowed: " + defs + " at " + stmt);
							}
						} else {
							throw new UnsupportedOperationException("Only SSA body allowed.");
						}
					}
					return s;
				}
			}.visit();
		}
		return 0;
	}
	
	private Expression transform(Statement stmt, Statement tail, CopyVarStatement def) {
		Expression rhs = def.getExpression();
		if(rhs instanceof ConstantExpression) {
			return rhs.copy();
		}
		
		
	}
}