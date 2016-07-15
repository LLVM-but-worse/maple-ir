package org.rsdeob.stdlib.ir.transform.ssa;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public class SSAPropagator extends Transformer {

	private final Map<VersionedLocal, CopyVarStatement> defs;
	
	public SSAPropagator(CodeBody code, CodeAnalytics analytics) {
		super(code, analytics);
		defs = new HashMap<>();
		
		for(Statement s : code) {
			if(s instanceof CopyVarStatement) {
				CopyVarStatement d = (CopyVarStatement) s;
				defs.put((VersionedLocal) d.getVariable().getLocal(), d);
			}
		}
	}

	@Override
	public int run() {
		AtomicInteger i = new AtomicInteger();
		for(Statement stmt : code) {
			new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						Local l = ((VarExpression) s).getLocal();
						if(l instanceof VersionedLocal) {
							CopyVarStatement def = defs.get(l);
							Expression expr = def.getExpression();
							if(expr instanceof PhiExpression) {
								System.out.println("skipping phi at " + def);
								return s;
							}
							
							if(expr instanceof ConstantExpression) {
								i.incrementAndGet();
								return expr;
							}
									
						} else {
							throw new UnsupportedOperationException("Only SSA body allowed.");
						}
					}
					return s;
				}
			}.visit();
		}

		return i.get();
	}
	
	private Expression transform(Statement stmt, Statement tail, CopyVarStatement def) {
		Expression rhs = def.getExpression();
		if(rhs instanceof ConstantExpression) {
			return rhs.copy();
		} else if(rhs instanceof VarExpression) {
			if(stmt instanceof CopyVarStatement && ((CopyVarStatement) stmt).getExpression() instanceof VarExpression) {
				return rhs.copy();
			}
		}
		return null;
		
		// current scenario:
		//    var2 = rhs;
		//    ...
		//    use(var2);
		
		// here we go through rhs and collect
		// all types of variables that are used 
		// in the expression. this includes
		// VarExpressions, FieldLoadExpression,
		// ArrayLoadExpressions and InvokeExpressions.
	}
}