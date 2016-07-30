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
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.ssa.SSALivenessAnalyser;

public class SreedharDestructor {

	final CodeBody code;
	final ControlFlowGraph cfg;
	final SSALivenessAnalyser liveness;
	final NullPermeableHashMap<Local, Set<Local>> interfere;
	// critical maps.
	final NullPermeableHashMap<BasicBlock, Set<CopyVarStatement>> blockPhis;
	final Map<Local, Set<Local>> phiCongruenceClasses;
	final Map<Local, Set<Local>> unresolvedNeighbours;
	// utility maps.
	final Map<Local, CopyVarStatement> phiDefs;
	// the first local in the list(val) is the def(key).
	final NullPermeableHashMap<Local, List<PhiResource>> phiResources;

	public SreedharDestructor(CodeBody code, ControlFlowGraph cfg) {
		this.code = code;
		this.cfg = cfg;
		liveness = new SSALivenessAnalyser(cfg);
		
		interfere = new NullPermeableHashMap<>(new SetCreator<>());
		
		blockPhis = new NullPermeableHashMap<>(new SetCreator<>());
		phiCongruenceClasses = new HashMap<>();
		unresolvedNeighbours = new HashMap<>();
		
		phiDefs = new HashMap<>();
		phiResources = new NullPermeableHashMap<>(new ListCreator<>());

		find_interference();
		init();
		init2();
		
		System.out.println();
		System.out.println("phiCongruenceClasses:");
		print(phiCongruenceClasses);
		System.out.println("unresolvedNeighbours:");
		print(unresolvedNeighbours);
		System.out.println("phiResources:");
		print(phiResources);
	}
	
	void find_interference() {
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

	void init() {
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b.getStatements()) {
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					if(copy.getExpression() instanceof PhiExpression) {
						init_phi(b, copy);
					}
				}
			}
		}
	}
	
	void init_phi(BasicBlock b, CopyVarStatement copy) {
		Local def = copy.getVariable().getLocal();
		
		// remember phi copies
		blockPhis.getNonNull(b).add(copy);
		phiDefs.put(def, copy);
		
		map_initial_pcc(def, b, def);
		
		PhiExpression phi = (PhiExpression) copy.getExpression();
		for(Entry<HeaderStatement, Expression> en : phi.getLocals().entrySet()) {
			Expression e = en.getValue();
			if(!(e instanceof VarExpression)) {
				throw new UnsupportedOperationException("Cannot perform Sreedhar 3 on propagated phi resources: " + e);
			}
			VarExpression v = (VarExpression) e;
			BasicBlock li = ((BlockHeaderStatement) en.getKey()).getBlock();
			map_initial_pcc(def, li, v.getLocal());
		}
	}
	
	void map_initial_pcc(Local phiDef, BasicBlock li, Local xi) {
		PhiResource pr = new PhiResource(li, xi);
		phiResources.getNonNull(phiDef).add(pr);
		
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
	}
	
	void init2() {
		for(Entry<BasicBlock, Set<CopyVarStatement>> e : blockPhis.entrySet()) {
			BasicBlock b = e.getKey();
			Set<CopyVarStatement> phis = e.getValue();
			for(CopyVarStatement phi : phis) {
				Set<Local> cand = find_copy_candidates(b, phi.getVariable().getLocal());
				// resolve_conflicts(phi, b, cand);
				System.out.println("candidates: " + cand);
			}
		}
	}
	
	Set<Local> find_copy_candidates(BasicBlock b, Local def) {
		List<PhiResource> resources = phiResources.get(def);
		int len = resources.size();
		
		Set<Local> conflicts = new HashSet<>();
		Set<Local> candidates = new HashSet<>();
		
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
						candidates.add(xi);
					}
					if(c == Case.SECOND || c == Case.THIRD) {
						candidates.add(xj);
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
						conflicts.add(xi);
						conflicts.add(xj);
					}
				}
			}
		}
		
		List<Local> sorted = reorder(conflicts);
		
		for(Local l : sorted) {
			if(candidates.contains(l)) {
				continue;
			}
			
			Set<Local> neighbours = unresolvedNeighbours.get(l);
			boolean should = false;
			for(Local n : neighbours) {
				if(!candidates.contains(n)) {
					should = true;
					break;
				}
			}
			if(should) {
				candidates.add(l);
			}
		}
		
		return candidates;
	}
	
	List<Local> reorder(Set<Local> conflicts) {
		List<Local> res = new ArrayList<>();
		
		for(Local l : conflicts) {
			Set<Local> neighbours = unresolvedNeighbours.get(l);
			
			boolean added = false;
			
			for(Local j : new ArrayList<>(res)) {
				Set<Local> neighbours2 = unresolvedNeighbours.get(j);
				if(neighbours2.size() < neighbours.size()) {
					// l before j
					res.remove(l);
					res.add(res.indexOf(j), l);
					added = true;
					break;
				}
			}
			
			if(!added) {
				res.add(l);
			}
		}
		
		return res;
	}
	
	void resolve_conflicts(CopyVarStatement phi, BasicBlock l0, Local xi) {
		if(phi.getVariable().getLocal() == xi) {
			
		} else {
			
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
		
		PhiResource(BasicBlock block, Local local) {
			this.block = block;
			this.local = local;
		}
		
		@Override
		public String toString() {
			return block.getId() + ":" + local;
		}
	}
}