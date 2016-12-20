package org.mapleir.ir.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.StatementVisitor;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;

public class SSALocalAccess {

	public final Map<VersionedLocal, AbstractCopyStatement> defs;
	public final NullPermeableHashMap<VersionedLocal, AtomicInteger> useCount;
	
	public SSALocalAccess(ControlFlowGraph cfg) {
		defs = new HashMap<>();
		useCount = new NullPermeableHashMap<>(new ValueCreator<AtomicInteger>() {
			@Override
			public AtomicInteger create() {
				return new AtomicInteger();
			}
		});
		
		for(BasicBlock b : cfg.vertices()) {
			for(Stmt s : b) {
				boolean synth = false;
				
				int op = s.getOpcode();
				if(op == Opcode.LOCAL_STORE || op == Opcode.PHI_STORE) {
					AbstractCopyStatement d = (AbstractCopyStatement) s;
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
						public Expr visit(Expr stmt) {
							if(stmt instanceof VarExpression) {
								VersionedLocal l = (VersionedLocal) ((VarExpression) stmt).getLocal();
								useCount.getNonNull(l).incrementAndGet();
							} else if(stmt instanceof PhiExpression) {
								PhiExpression phi = (PhiExpression) stmt;
								for(Expr e : phi.getArguments().values()) {
									if(e instanceof VarExpression)  {
										useCount.getNonNull((VersionedLocal) ((VarExpression) e).getLocal()).incrementAndGet();
									} else {
										new StatementVisitor(e) {
											@Override
											public Expr visit(Expr stmt2) {
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
}