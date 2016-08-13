package org.mapleir.stdlib.ir.transform.ssa;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.StatementVisitor;

public class SSALocalAccess {

	public final Map<VersionedLocal, CopyVarStatement> defs;
	public final NullPermeableHashMap<VersionedLocal, AtomicInteger> useCount;
	
	public SSALocalAccess(CodeBody code) {
		defs = new HashMap<>();
		useCount = new NullPermeableHashMap<>(new ValueCreator<AtomicInteger>() {
			@Override
			public AtomicInteger create() {
				return new AtomicInteger();
			}
		});
		
		for(Statement s : code) {
			boolean synth = false;
			
			if(s instanceof CopyVarStatement) {
				CopyVarStatement d = (CopyVarStatement) s;
				VersionedLocal local = (VersionedLocal) d.getVariable().getLocal();
				defs.put(local, d);
				// sometimes locals can be dead even without any transforms.
				// since they have no uses, they are never found by the below
				// visitor, so we touch the local in map here to mark it.
				useCount.getNonNull(local); 
				synth = d.isSynthetic();
			}
			
			if(!synth) {
				new StatementVisitor(s) {
					@Override
					public Statement visit(Statement stmt) {
						if(stmt instanceof VarExpression) {
							VersionedLocal l = (VersionedLocal) ((VarExpression) stmt).getLocal();
							useCount.getNonNull(l).incrementAndGet();
						} else if(stmt instanceof PhiExpression) {
							PhiExpression phi = (PhiExpression) stmt;
							for(Expression e : phi.getLocals().values()) {
								if(e instanceof VarExpression)  {
									useCount.getNonNull((VersionedLocal) ((VarExpression) e).getLocal()).incrementAndGet();
								} else {
									new StatementVisitor(e) {
										@Override
										public Statement visit(Statement stmt2) {
											if(stmt2 instanceof VarExpression) {
												VersionedLocal l = (VersionedLocal) ((VarExpression) stmt2).getLocal();
												useCount.getNonNull(l).incrementAndGet();
											}
											return stmt2;
										}
									}.visit();
								}
							}
						}
						return stmt;
					}
				}.visit();
			}
		}
	}
}