package org.mapleir.stdlib.ir.gen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.header.BlockHeaderStatement;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.stdlib.ir.locals.BasicLocal;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.locals.LocalsHandler;
import org.mapleir.stdlib.ir.locals.VersionedLocal;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.ssa.SSALocalAccess;
import org.objectweb.asm.Type;

public class SSADestructor {

	final CodeBody body;
	final ControlFlowGraph cfg;
	final LocalsHandler locals;
	
	public SSADestructor(CodeBody body, ControlFlowGraph cfg) {
		this.body = body;
		this.cfg = cfg;
		locals = body.getLocals();
		
		GraphUtils.rewriteCfg(cfg, body);
	}
	
	int maxl(Set<VersionedLocal> defs) {
		int res = 0;
		for(VersionedLocal l : defs) {
			res = Math.max(res, l.getIndex());
		}
		return res;
	}
	
	void unroll_phis() {
		for(Statement stmt : new HashSet<>(body)) {
			if(stmt instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				Expression expr = copy.getExpression();
				if(expr instanceof PhiExpression) {
					PhiExpression phi = (PhiExpression) expr;
					Local l = copy.getVariable().getLocal();
					unroll(phi, l);
					body.remove(copy);
				}
			}
		}
	}

	void unroll(PhiExpression phi, Local l) {
		for(Entry<HeaderStatement, Expression> e : phi.getLocals().entrySet()) {
			Expression expr = e.getValue();
			if(expr instanceof VarExpression) {
				Local l2 = ((VarExpression) expr).getLocal();
				if(l2.getIndex() == l.getIndex() && l2.isStack() == l.isStack()) {
					continue;
				}
			}
			HeaderStatement header = e.getKey();
			if(header instanceof BlockHeaderStatement) {
				BlockHeaderStatement bh = (BlockHeaderStatement) header;
				BasicBlock block = bh.getBlock();
				List<Statement> stmts = block.getStatements();
				int index = -1;
				if(stmts.isEmpty()) {
					index = body.indexOf(bh) + 1;
				} else {
					Statement last = stmts.get(stmts.size() - 1);
					index = body.indexOf(last);
					if(!last.canChangeFlow()) {
						index += 1;
					}
				}

				CopyVarStatement copy = new CopyVarStatement(new VarExpression(l, phi.getType()), expr);
				body.add(index, copy);
				stmts.add(copy);
			} else {
				throw new UnsupportedOperationException(header.toString() + ", " + header.getClass().getCanonicalName());
			}
		}
	}
	
	void resolve_interference() {
		LocalsHandler.remap(body, build_interferences());
	}
	
	Map<VersionedLocal, VersionedLocal> build_interferences() {
		int max = maxl(new SSALocalAccess(body).defs.keySet());
		Map<VersionedLocal, VersionedLocal> res = new HashMap<>();
		
		for(BasicBlock b : cfg.vertices()) {
			Map<BasicLocal, Integer> subscripted_uses = new HashMap<>();
			
			for(Statement stmt : b.getStatements()) {
				for(Statement s : Statement.enumerate(stmt)) {
					if(s instanceof VarExpression) {
						VarExpression v = (VarExpression) s;
						if(v.getLocal() instanceof VersionedLocal) {
							VersionedLocal vl = (VersionedLocal) v.getLocal();
							BasicLocal bl = locals.get(vl.getIndex(), vl.isStack());
							if(!subscripted_uses.containsKey(bl)) {
								subscripted_uses.put(bl, vl.getSubscript());
							} else if(subscripted_uses.get(bl).intValue() != vl.getSubscript()) {
								if(!res.containsKey(vl)) {
									res.put(vl, locals.get(++max, 0, false));
								}
							}
						}
					}
				}
			}
		}
		
		for(Entry<VersionedLocal, VersionedLocal> e : res.entrySet()) {
			System.out.println(e.getKey() + "  ===  " + e.getValue());
		}
		
		return res;
	}
	
	Map<VersionedLocal, Type> var_types() {
		Map<VersionedLocal, Type> res = new HashMap<>();
		for(Statement stmt : body) {
			for(Statement s : Statement.enumerate(stmt)) {
				VarExpression v = null;
				if(s instanceof VarExpression) {
					v = (VarExpression) s;
				} else if(s instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) s;
					if(copy.isSynthetic()) {
						v = ((CopyVarStatement) s).getVariable();
					}
				}
				
				if(v != null) {
					Local l = v.getLocal();
					if(!(l instanceof VersionedLocal)) {
						throw new IllegalStateException(l.toString());
					}
					res.put((VersionedLocal) l, v.getType());
				}
			}
		}
		return res;
	}
	
	void type_vars() {
		 NullPermeableHashMap<BasicLocal, Set<Type>> collated = new NullPermeableHashMap<>(new SetCreator<>());
		 
		 Map<VersionedLocal, Type> mapped = var_types();
		 for(Entry<VersionedLocal, Type> e : mapped.entrySet()){
			 VersionedLocal vl = e.getKey();
			 BasicLocal bl = locals.get(vl.getIndex(), vl.isStack());
			 collated.getNonNull(bl).add(TypeUtils.asSimpleType(e.getValue()));
		 }
		 
		 SSALocalAccess localsAccess = new SSALocalAccess(body);
		 int max = maxl(localsAccess.defs.keySet());
		 for(Entry<BasicLocal, Set<Type>> e : collated.entrySet()) {
			 Set<Type> set = e.getValue();
			 if(set.size() > 1) {
				 split(localsAccess, e.getKey(), max, set,mapped);
				 // account for the newly created locals.
				 max += set.size();
			 }
		 }
	}
	
	void split(SSALocalAccess localsAccess, BasicLocal l, int max, Set<Type> types, Map<VersionedLocal, Type> mapped) {
		Map<Type, BasicLocal> spindle = new HashMap<>();
		for(Type t : types) {
			spindle.put(t, locals.get(++max, l.isStack()));
		}
			
		Map<Local, Local> remap = new HashMap<>();
		for(VersionedLocal vl : localsAccess.defs.keySet()) {
			if(l.getIndex() == vl.getIndex() && l.isStack() == vl.isStack()) {
				Type ct = mapped.get(vl);
				BasicLocal bl = spindle.get(TypeUtils.asSimpleType(ct));
				remap.put(vl, bl);
			}
		}
		LocalsHandler.remap(body, remap);
	}
	
	void drop_subscripts() {
		for(Statement stmt : body) {
			for(Statement s : Statement.enumerate(stmt)) {
				VarExpression v = null;
				if(s instanceof VarExpression) {
					v = (VarExpression) s;
				} else if(s instanceof CopyVarStatement) {
					v = ((CopyVarStatement) s).getVariable();
				}
				
				if(v != null) {
					un_subscript(v, v.getLocal());
				}
			}
		}
	}
	
	void un_subscript(VarExpression v, Local l) {
		if(l instanceof VersionedLocal) {
			Local unsubscript = locals.get(l.getIndex(), l.isStack());
			v.setLocal(unsubscript);
		}
	}
	
	public void run() {
		unroll_phis();
		resolve_interference();
		type_vars();
		drop_subscripts();
	}
}