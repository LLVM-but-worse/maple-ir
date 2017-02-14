package org.mapleir.ir.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.CodeUnitVisitor;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;

public class SSALocalAccess {

	public final Map<VersionedLocal, AbstractCopyStmt> defs;
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
					AbstractCopyStmt d = (AbstractCopyStmt) s;
					VersionedLocal local = (VersionedLocal) d.getVariable().getLocal();
					defs.put(local, d);
					// sometimes locals can be dead even without any transforms.
					// since they have no uses, they are never found by the below
					// visitor, so we touch the local in map here to mark it.
					useCount.getNonNull(local); 
					synth = d.isSynthetic();
				}
				
				if(!synth) {
					new CodeUnitVisitor(s) {
						@Override
						public Expr visit(Expr stmt) {
							if(stmt instanceof VarExpr) {
								VersionedLocal l = (VersionedLocal) ((VarExpr) stmt).getLocal();
								useCount.getNonNull(l).incrementAndGet();
							} else if(stmt instanceof PhiExpr) {
								PhiExpr phi = (PhiExpr) stmt;
								for(Expr e : phi.getArguments().values()) {
									if(e instanceof VarExpr)  {
										useCount.getNonNull((VersionedLocal) ((VarExpr) e).getLocal()).incrementAndGet();
									} else {
										new CodeUnitVisitor(e) {
											@Override
											public Expr visit(Expr stmt2) {
												if(stmt2 instanceof VarExpr) {
													VersionedLocal l = (VersionedLocal) ((VarExpr) stmt2).getLocal();
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