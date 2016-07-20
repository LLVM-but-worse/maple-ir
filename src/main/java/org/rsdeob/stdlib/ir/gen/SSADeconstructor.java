package org.rsdeob.stdlib.ir.gen;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.collections.SetMultimap;
import org.rsdeob.stdlib.collections.graph.util.GraphUtils;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.PhiExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.header.BlockHeaderStatement;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.locals.BasicLocal;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.locals.LocalsHandler;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.ssa.SSALocalAccess;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.rsdeob.stdlib.ir.transform.ssa.SSAUtil.replaceLocals;
import static org.rsdeob.stdlib.ir.transform.ssa.SSAUtil.visitAll;
import static org.rsdeob.stdlib.ir.transform.ssa.SSAUtil.vl;

public class SSADeconstructor {
	
	private final LocalsHandler locals;
	private final CodeBody body;
	private SSALocalAccess localsAccess;
	private final ControlFlowGraph cfg;
	
	// variable interference resolution
	private Set<VersionedLocal> interferingLocals;
	
	// SetMultimap<>() type enforcement
	private Map<VersionedLocal, Type> localTypes;
	private int maxLocals;
	
	private final SetMultimap<VersionedLocal, VarExpression> uses; // umm...this is updated but never queried. what
	
	public SSADeconstructor(CodeBody body, ControlFlowGraph cfg) {
		this.body = body;
		this.cfg = cfg;
		localsAccess = new SSALocalAccess(body);
		
		locals = body.getLocals();
		uses = new SetMultimap<>();
		
		GraphUtils.rewriteCfg(cfg, body);
	}
	
	// Processing code
	public void run() {
		unrollPhis();
		resolveInterferingVars();
		enforceVarTyping();
		dropSubscripts();
	}
	
	// Phi removal
	private void unrollPhis() {
		for (Statement stmt : new HashSet<>(body)) {
			if (stmt instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				Expression expr = copy.getExpression();
				if (expr instanceof PhiExpression) {
					Local l = copy.getVariable().getLocal();
					PhiExpression phi = (PhiExpression) expr;
					unroll(phi, l);
					body.remove(copy);
					localsAccess.defs.remove(vl(l));
					localsAccess.useCount.remove(vl(l));
				}
			}
		}
	}
	
	private void unroll(PhiExpression phi, Local l) {
		for (Entry<HeaderStatement, Expression> e : phi.getLocals().entrySet()) {
			Expression expr = e.getValue();
			if (expr instanceof VarExpression) {
				Local l2 = ((VarExpression) expr).getLocal();
				if (l2.getIndex() == l.getIndex() && l2.isStack() == l.isStack()) {
					continue;
				}
			}
			HeaderStatement header = e.getKey();
			if (header instanceof BlockHeaderStatement) {
				BlockHeaderStatement bh = (BlockHeaderStatement) header;
				BasicBlock block = bh.getBlock();
				List<Statement> stmts = block.getStatements();
				int index = -1;
				if (stmts.isEmpty()) {
					index = body.indexOf(bh) + 1;
				} else {
					Statement last = stmts.get(stmts.size() - 1);
					index = body.indexOf(last);
					if (!last.canChangeFlow()) {
						index += 1;
					}
				}
				
				CopyVarStatement copy = new CopyVarStatement(new VarExpression(l, phi.getType()), expr);
				body.add(index, copy);
				stmts.add(copy);
				localsAccess.defs.put((VersionedLocal) l, copy);
				// FIXME: not a real fixme, just note that useCount in localaccess isn't synced.
			} else {
				throw new UnsupportedOperationException(header.toString() + ", " + header.getClass().getCanonicalName());
			}
		}
	}
	
	private void computeMaxLocals() {
		for (VersionedLocal local : localsAccess.defs.keySet())
			maxLocals = Math.max(maxLocals, local.getIndex());
		System.err.println("(1.0.0) maxLocals = " + maxLocals);
	}
	
	private void resolveInterferingVars() {
		localsAccess = new SSALocalAccess(body);
		computeMaxLocals();
		findInterferingVars();
		fixInterferingVars();
		localsAccess = null;
	}
	
	private void findInterferingVars() {
		interferingLocals = new HashSet<>();
		for (BasicBlock block : cfg.vertices()) {
			Map<BasicLocal, Integer> usedLocals = new HashMap<>();
			for (Statement stmt : block.getStatements()) {
				for (VarExpression var : stmt.getUsedVars()) {
					if (var.getLocal() instanceof VersionedLocal) {
						VersionedLocal versioned = (VersionedLocal) var.getLocal();
						BasicLocal unversioned = locals.unversion(versioned);
						if (!usedLocals.containsKey(unversioned))
							usedLocals.put(unversioned, versioned.getSubscript());
						if (usedLocals.get(unversioned) != versioned.getSubscript()) {
							interferingLocals.add(versioned);
							System.err.println("(1.1.0) #" + block.getId() + ", " + unversioned + " interferes (" + usedLocals.get(unversioned) + " vs " + versioned.getSubscript() + ")");
						}
					}
				}
			}
		}
	}
	
	private void fixInterferingVars() {
		for (VersionedLocal usedLocal : interferingLocals) {
			VersionedLocal reassignLocal = locals.get(++maxLocals, 0, false);
			replaceLocals(body, versionedLocal -> versionedLocal == usedLocal, versionedLocal -> reassignLocal);
			System.err.println("(1.1.1) Reassigned interfering local " + usedLocal + " to " + reassignLocal);
		}
	}
	
	// Variable typing
	private void enforceVarTyping() {
		localsAccess = new SSALocalAccess(body);
		computeMaxLocals();
		mapTypes();
		unweaveUndroppables();
		localsAccess = null;
	}
	
	private void mapTypes() {
		localTypes = new HashMap<>();
		for (Statement var : visitAll(body, child -> child instanceof VarExpression))
			mapType((VarExpression) var);
		for (Statement cvs : visitAll(body, child -> child instanceof CopyVarStatement && ((CopyVarStatement) child).isSynthetic()))
			mapType(((CopyVarStatement) cvs).getVariable());
	}
	
	private void mapType(VarExpression var) {
		Local local = var.getLocal();
		if(!(local instanceof VersionedLocal)) {
			throw new IllegalStateException(local + " " + var);
		}
		VersionedLocal versionedLocal = (VersionedLocal) local;
		uses.put(versionedLocal, var);
		localTypes.put(versionedLocal, var.getType());
	}
	
	private void unweaveUndroppables() {
		SetMultimap<BasicLocal, Type> simpleTypes = new SetMultimap<>();
		
		for (Entry<VersionedLocal, Type> e : localTypes.entrySet()) {
			VersionedLocal versioned = vl(e.getKey());
			BasicLocal unversioned = locals.unversion(versioned);
			simpleTypes.put(unversioned, TypeUtils.asSimpleType(e.getValue()));
		}
		
		for (Entry<BasicLocal, Set<Type>> e : simpleTypes.asMap().entrySet()) {
			BasicLocal local = e.getKey();
			Set<Type> types = e.getValue();
			System.err.println("(1.3.0) " + local + ": " + types);
			if (types.size() > 1) {
				unweaveLocal(local, types);
			}
		}
	}
	
	private void unweaveLocal(BasicLocal local, Set<Type> set) {
		System.err.println("(1.3.1) Need to unweave " + local);
		
		// wind up spindle
		Map<Type, BasicLocal> spindle = new HashMap<>();
		for (Type simpleType : set) {
			BasicLocal newLocal = locals.get(++maxLocals, false);
			spindle.put(simpleType, newLocal);
			System.err.println("(1.3.2) Reassigning clash type " + simpleType + " to " + newLocal);
		}
		
		// unwind spindle
		for (VersionedLocal usedLocal : localsAccess.defs.keySet()) {
			System.err.println(usedLocal + " " + usedLocal.isVersionOf(local));
			if (usedLocal.isVersionOf(local)) {
				// usedLocal is clashing
				Type complexType = localTypes.get(usedLocal);
				BasicLocal reassignLocal = spindle.get(TypeUtils.asSimpleType(complexType));
				replaceLocals(body, versionedLocal -> versionedLocal == usedLocal, versionedLocal -> reassignLocal);
				System.err.println("(1.3.3) Reassigned clash local " + usedLocal + "(type=" + complexType + ") to " + reassignLocal);
			}
		}
	}
	
	// Subscript removal
	private void dropSubscripts() { // yes my child, it's this easy
		replaceLocals(body, versionedLocal -> true, locals::unversion);
	}
}