package org.rsdeob.stdlib.ir;

import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.PhiExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.header.BlockHeaderStatement;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.locals.LocalsHandler;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

public class UnSSA {

	final LocalsHandler locals;
	final CodeBody body;
	final ControlFlowGraph cfg;
	
	public UnSSA(CodeBody body, ControlFlowGraph cfg) {
		this.body = body;
		this.cfg = cfg;
		locals = body.getLocals();
		
		init_blocks();
	}
	
	void init_blocks() {
		for(BasicBlock b : cfg.vertices()) {
			b.getStatements().clear();
		}
		
		BasicBlock currentHeader = null;
		for(Statement stmt : body) {
			if(stmt instanceof HeaderStatement) {
				currentHeader = cfg.getBlock(((HeaderStatement) stmt).getHeaderId());
			} else {
				if(currentHeader == null) {
					throw new IllegalStateException();
				} else {
					if(!(stmt instanceof PhiExpression)) {
						currentHeader.getStatements().add(stmt);
					}
				}
			}
		}
	}
	
	void unroll(PhiExpression phi, Local l) {
		for(Entry<HeaderStatement, Expression> e : phi.getLocals().entrySet()) {
			HeaderStatement header = e.getKey();
			if(header instanceof BlockHeaderStatement) {
				BlockHeaderStatement bh = (BlockHeaderStatement) header;
				BasicBlock block = bh.getBlock();
				List<Statement> stmts = block.getStatements();
				int index = -1;
				if(stmts.isEmpty()) {
					index = body.indexOf(bh) +1;
				} else {
					Statement last = stmts.get(stmts.size() - 1);
					index = body.indexOf(last);
				}
				
				CopyVarStatement copy = new CopyVarStatement(new VarExpression(l, phi.getType()), e.getValue());
				body.add(index, copy);
				stmts.add(copy);
			} else {
				throw new UnsupportedOperationException(header.toString() + ", " + header.getClass().getCanonicalName());
			}
		}
	}
	
	void unroll_phis() {
		for(Statement stmt : new HashSet<>(body)) {
			if(stmt instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				Expression expr = copy.getExpression();
				if(expr instanceof PhiExpression) {
					PhiExpression phi = (PhiExpression) expr;
					unroll(phi, copy.getVariable().getLocal());
					body.remove(copy);
				}
			}
		}
	}
	
	void drop_subscripts() {
		for(Statement stmt : new HashSet<>(body)) {
			for(Statement s : Statement.enumerate(stmt)) {
				if(s instanceof VarExpression) {
					VarExpression var = (VarExpression) s;
					Local local = var.getLocal();
					if(local instanceof VersionedLocal) {
						Local unsubscript = locals.get(local.getIndex(), local.isStack());
						var.setLocal(unsubscript);
					}
				} else if(s instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) s;
					VarExpression var = copy.getVariable();
					Local local = var.getLocal();
					if(local instanceof VersionedLocal) {
						Local unsubscript = locals.get(local.getIndex(), local.isStack());
						var.setLocal(unsubscript);
					}
				} else if(s instanceof PhiExpression) {
					throw new IllegalStateException(s.toString());
				}
			}
		}
	}
	
	public void run() {
		unroll_phis();
		drop_subscripts();
	}
}