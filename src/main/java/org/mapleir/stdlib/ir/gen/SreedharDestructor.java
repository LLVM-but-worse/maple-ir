package org.mapleir.stdlib.ir.gen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.collections.ListCreator;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.header.BlockHeaderStatement;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.locals.VersionedLocal;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.ssa.SSALivenessAnalyser;
import org.objectweb.asm.Type;

public class SreedharDestructor {

	final CodeBody code;
	final ControlFlowGraph cfg;
	final Map<BasicBlock, BlockHeaderStatement> headers;
	
	SSALivenessAnalyser liveness;
	NullPermeableHashMap<Local, Set<Local>> interfere;
	
	// critical maps.
	final Map<Local, Set<Local>> phiCongruenceClasses;
	final Map<Local, Set<Local>> unresolvedNeighbours;
	// utility maps.
	final Map<Local, CopyVarStatement> phiDefs;

	public SreedharDestructor(CodeBody code, ControlFlowGraph cfg) {
		this.code = code;
		this.cfg = cfg;
		
		interfere = new NullPermeableHashMap<>(new SetCreator<>());
		
		headers = new HashMap<>();
		
		phiCongruenceClasses = new HashMap<>();
		unresolvedNeighbours = new HashMap<>();
		
		phiDefs = new HashMap<>();

		init();
		find_interference();
		csaa_iii();
		
	}
	
	void init() {
		for(Statement stmt : code) {
			if(stmt instanceof BlockHeaderStatement) {
				BlockHeaderStatement hs = (BlockHeaderStatement) stmt;
				BasicBlock b = hs.getBlock();
				headers.put(b, hs);
			}
		}
	}
	
	void find_interference() {
		liveness = new SSALivenessAnalyser(cfg);
		interfere.clear();
		
		for(BasicBlock b : liveness.getGraph().vertices()) {
			Map<Local, Boolean> out = liveness.out(b);
			
			for(Statement stmt : b.getStatements()) {
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					Local def = copy.getVariable().getLocal();
					Expression e = copy.getExpression();
					
					if(!(e instanceof VarExpression)) {
						if(out.containsKey(def)) {
							for(Entry<Local, Boolean> entry : out.entrySet()) {
								if(entry.getValue()) {
									Local l = entry.getKey();
									if(def != l) {
										interfere.getNonNull(def).add(l);
										interfere.getNonNull(l).add(def);
									}
								}
							}
						}
					}
				}
			}
		}
	}
	
	static void print(Map<Local, ?> map) {
		for(Entry<Local, ?> e : map.entrySet()) {
			System.out.println("   " + e.getKey() + " = " + e.getValue());
		}
	}

	void csaa_iii() {
		NullPermeableHashMap<Local, List<PhiResource>> resmap = new NullPermeableHashMap<>(new ListCreator<>());
		NullPermeableHashMap<BasicBlock, Set<CopyVarStatement>> blockPhis = new NullPermeableHashMap<>(new SetCreator<>());
		
		for(BasicBlock l0 : cfg.vertices()) {
			Set<CopyVarStatement> phis = blockPhis.getNonNull(l0);
			for(Statement stmt : l0.getStatements()) {
				if(!(stmt instanceof CopyVarStatement)) {
					continue;
				}
				CopyVarStatement copy = (CopyVarStatement) stmt;
				if(copy.getExpression() instanceof PhiExpression) {
					init_phi(resmap, phis, l0, copy);
				}
			}
		}
		
		for(Entry<BasicBlock, Set<CopyVarStatement>> e : blockPhis.entrySet()) {
			BasicBlock l0 = e.getKey();
			for(CopyVarStatement copy : e.getValue()) {
				Local l = copy.getVariable().getLocal();
				
				List<PhiResource> ress = resmap.get(l);
				Set<PhiResource> cand = find_copy_candidates(l0, ress);
				
				for(PhiResource r : cand) {
					Local newL = resolve_conflicts(copy, l0, r);
					PhiResource newR = new PhiResource(r.block, newL, r.type);
					ress.remove(r);
					ress.add(newR);
				}

				Set<Local> mset = new HashSet<>();
				for(PhiResource r : ress) {
					mset.add(r.local);
				}

				mset = merge_pcc(mset);
				for(Local s : mset) {
					phiCongruenceClasses.remove(s);
					phiCongruenceClasses.put(s, mset);
				}
			}
		}
	}
	
	void init_phi(NullPermeableHashMap<Local, List<PhiResource>> phiResources, Set<CopyVarStatement> phis, BasicBlock l0, CopyVarStatement copy) {
		VarExpression dv = copy.getVariable();
		Local def = dv.getLocal();
		List<PhiResource> res = phiResources.getNonNull(def);
		
		// remember phi copies
		phis.add(copy);
		phiDefs.put(def, copy);
		
		res.add(map_initial_pcc(l0, dv));
		
		PhiExpression phi = (PhiExpression) copy.getExpression();
		for(Entry<HeaderStatement, Expression> en : phi.getLocals().entrySet()) {
			Expression e = en.getValue();
			if(!(e instanceof VarExpression)) {
				throw new UnsupportedOperationException("Cannot perform Sreedhar 3 on propagated phi resources: " + e);
			}
			VarExpression v = (VarExpression) e;
			BasicBlock li = ((BlockHeaderStatement) en.getKey()).getBlock();
			res.add(map_initial_pcc(li, v));
		}
	}
	
	PhiResource map_initial_pcc(BasicBlock li, VarExpression v) {
		Local xi = v.getLocal();
		// phiCongruenceClass[x] = {x};
		if(!phiCongruenceClasses.containsKey(xi)) {
			Set<Local> pcc = new HashSet<>();
			pcc.add(xi);
			phiCongruenceClasses.put(xi, pcc);
		}
		
		// unresolvedNeighborMap[xi] = {};
		if(!unresolvedNeighbours.containsKey(xi)) {
			unresolvedNeighbours.put(xi, new HashSet<>());
		}
		
		PhiResource pr = new PhiResource(li, xi, v.getType());
		return pr;
	}
	
	Set<PhiResource> find_copy_candidates(BasicBlock b, List<PhiResource> resources) {
		int len = resources.size();
		
		Set<PhiResource> conflicts = new HashSet<>();
		Set<PhiResource> candidates = new HashSet<>();
		
		for(int i=0; i < len - 1; i++) {
			PhiResource ri = resources.get(i);
			for(int j = i + 1; j < len; j++) {
				PhiResource rj = resources.get(j);
				
				if(ri.local == rj.local) {
					throw new IllegalStateException(ri + " " + rj);
				}
				
				ResolverFrame frame = new ResolverFrame(b, ri, rj, i == 0);
				if(frame.check_interference()) {
					Local xi = ri.local;
					Local xj = rj.local;
					Case c = frame.find_case();
					System.out.printf("xi:%s, xj:%s, c:%s.%n", xi, xj, c);
					if(c == Case.FIRST || c == Case.THIRD) {
						candidates.add(ri);
					}
					if(c == Case.SECOND || c == Case.THIRD) {
						candidates.add(rj);
					}
					if(c == Case.FOURTH) {
						Set<Local> unresolved;
						
						unresolved = unresolvedNeighbours.get(xi);
						if(unresolved == null) {
							unresolved = new HashSet<>();
							unresolvedNeighbours.put(xi, unresolved);
						}
						unresolved.add(xj);
						
						unresolved = unresolvedNeighbours.get(xj);
						if(unresolved == null) {
							unresolved = new HashSet<>();
							unresolvedNeighbours.put(xj, unresolved);
						}
						unresolved.add(xi);
						
						// "the final decision of which copy to insert is 
						// deferred until all pairs of interfering resources 
						// in the phi instruction are processed."
						conflicts.add(ri);
						conflicts.add(rj);
					}
				}
			}
		}
		
		List<PhiResource> sorted = reorder(conflicts);
		
		for(PhiResource r : sorted) {
			Local l = r.local;
			if(candidates.contains(l)) {
				continue;
			}
			
			Set<Local> neighbours = unresolvedNeighbours.get(l);
			boolean should = false;
			for(Local n : neighbours) {
				// this should be a small set i think.
				for(PhiResource c : candidates) {
					if(c.local == n) {
						should = true;
						break;
					}
				}
			}
			if(should) {
				candidates.add(r);
			}
		}
		
		return candidates;
	}
	
	List<PhiResource> reorder(Set<PhiResource> conflicts) {
		List<PhiResource> res = new ArrayList<>();
		
		for(PhiResource r : conflicts) {
			Set<Local> neighbours = unresolvedNeighbours.get(r.local);
			
			boolean added = false;
			
			for(PhiResource rj : new ArrayList<>(res)) {
				Set<Local> neighbours2 = unresolvedNeighbours.get(rj.local);
				if(neighbours2.size() < neighbours.size()) {
					// l before j
					res.remove(r);
					res.add(res.indexOf(rj), r);
					added = true;
					break;
				}
			}
			
			if(!added) {
				res.add(r);
			}
		}
		
		return res;
	}
	
	Local resolve_conflicts(CopyVarStatement phiCopy, BasicBlock l0, PhiResource r) {
		PhiExpression phi = (PhiExpression) phiCopy.getExpression();
		
		Local xi = r.local;
		Type type = r.type;
		BasicBlock li = r.block;
		
		VersionedLocal latest = code.getLocals().getLatestVersion(xi);
		VersionedLocal newi = code.getLocals().get(latest.getIndex(), latest.getSubscript() + 1, latest.isStack());
		VarExpression nv = new VarExpression(newi, type);
		CopyVarStatement copy = new CopyVarStatement(nv, new VarExpression(xi, type));
		
		if(!phiCongruenceClasses.containsKey(newi)) {
			Set<Local> set = new HashSet<>();
			set.add(newi);
			phiCongruenceClasses.put(newi, set);
		}
		
		if(phiCopy.getVariable().getLocal() == xi) {
			insert_start(li, copy);
			
			phiCopy.setVariable(nv);
		} else {
			insert_end(li, copy);
			
			HeaderStatement header = headers.get(li);
			Map<HeaderStatement, Expression> cont = phi.getLocals();
			cont.put(header, nv);
		}
		
		find_interference();
		
		return newi;
	}
	
	void insert_start(BasicBlock b, Statement s) {
		List<Statement> stmts = b.getStatements();

		int i = code.indexOf(headers.get(b));
		if(i == -1) {
			throw new IllegalStateException(b.getId());
		}
		code.add(i + 1, s);
		stmts.add(0, s);
	}
	
	void insert_end(BasicBlock b, Statement s) {
		List<Statement> stmts = b.getStatements();

		if(stmts.isEmpty()) {
			int i = code.indexOf(headers.get(b));
			if(i == -1) {
				throw new IllegalStateException(b.getId());
			}
			code.add(i + 1, s);
			stmts.add(s);
		} else {
			Statement last = stmts.get(stmts.size() - 1);
			int index = code.indexOf(last);
			if(!last.canChangeFlow()) {
				index += 1;
				stmts.add(s);
			} else {
				// index += 1;
				//  ^ do this above so that s goes to the end
				//    but here it needs to go before the end/jump.
				// add before the jump
				stmts.add(stmts.indexOf(last) - 1, s);
			}
			code.add(index, s);
		}
	}
	
	boolean interfere(Local i, Local j) {
		return interfere.getNonNull(i).contains(j);
	}
	
	boolean intersects(Set<Local> pcc, Map<Local, Boolean> live) {
		Set<Local> merged = merge_pcc(extract_live(live));
		
		for(Local l : pcc) {
			if(merged.contains(l)) {
				return true;
			}
		}
		return false;
	}
	
	Set<Local> extract_live(Map<Local, Boolean> live) {
		Set<Local> set = new HashSet<>();
		for(Entry<Local, Boolean> e : live.entrySet()) {
			if(e.getValue()) {
				set.add(e.getKey());
			}
		}
		return set;
	}
	
	Set<Local> merge_pcc(Set<Local> pcc) {
		LinkedList<Local> worklist = new LinkedList<>();
		worklist.addAll(pcc);
		
		Set<Local> newPcc = new HashSet<>();
		while(!worklist.isEmpty()) {
			Local l = worklist.pop();
			if(!newPcc.contains(l)) {
				newPcc.add(l);
				
				pcc = phiCongruenceClasses.get(l);
				if(pcc != null) {
					worklist.addAll(pcc);
				}
			}
		}
		
		return newPcc;
	}
	
	class ResolverFrame {
		final PhiResource ri;
		final PhiResource rj;
		final Set<Local> ipcc;
		final Set<Local> jpcc;
		final Map<Local, Boolean> iLive;
		final Map<Local, Boolean> jLive;
		
		// only xi can be the target of a phi where l0 is the definition block
		ResolverFrame(BasicBlock l0, PhiResource ri, PhiResource rj, boolean target) {
			this.ri = ri;
			this.rj = rj;
			
			ipcc = phiCongruenceClasses.get(ri.local);
			jpcc = phiCongruenceClasses.get(rj.local);
			
			if(target) {
				iLive = liveness.in(l0);
			} else {
				iLive = liveness.in(ri.block);
			}
			
			jLive = liveness.out(rj.block);
		}

		boolean check_interference() {
			if(ipcc == null || jpcc == null) {
				return true;
			}
			// such that there exists yi in phiCongruenceClass[xi], yj in phiCongruenceClass[xj],
			// and yi and yj interfere with each other,

			for(Local i : ipcc){
				for(Local j : jpcc)  {
					if(i != j && interfere(i, j)) {
						return true;
					}
				}
			}
			
			return false;
		}
		
		public Case find_case() {
			boolean b1 = intersects(ipcc, jLive);
			boolean b2 = intersects(jpcc, iLive);
			if(b1 && !b2) {
				return Case.FIRST;
			} else if(!b1 && b2) {
				return Case.SECOND;
			} else if(b1 && b2) {
				return Case.THIRD;
			} else {
				return Case.FOURTH;
			}
		}
	}
	
	enum Case {
		FIRST,
		SECOND,
		THIRD,
		FOURTH;
	}
	
	static class PhiResource {
		final BasicBlock block;
		final Local local;
		final Type type;
		
		PhiResource(BasicBlock block, Local local, Type type) {
			this.block = block;
			this.local = local;
			this.type = type;
		}
		
		@Override
		public String toString() {
			return block.getId() + ":" + local + "(" + type + ")";
		}
	}
}