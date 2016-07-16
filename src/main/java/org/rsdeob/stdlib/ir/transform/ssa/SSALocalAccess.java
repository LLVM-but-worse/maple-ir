package org.rsdeob.stdlib.ir.transform.ssa;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.ValueCreator;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.PhiExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

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