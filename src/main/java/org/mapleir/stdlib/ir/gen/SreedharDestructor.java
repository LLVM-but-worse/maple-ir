package org.mapleir.stdlib.ir.gen;

import static org.mapleir.stdlib.collections.graph.dot.impl.ControlFlowGraphDecorator.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.ListCreator;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.dot.BasicDotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.collections.graph.dot.impl.ControlFlowGraphDecorator;
import org.mapleir.stdlib.collections.graph.dot.impl.LivenessDecorator;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.ir.code.CodeBody;
import org.mapleir.stdlib.ir.header.BlockHeaderStatement;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.objectweb.asm.Type;

public class SreedharDestructor {

	final CodeBody code;
	final ControlFlowGraph cfg;
	final Map<BasicBlock, BlockHeaderStatement> headers;
	
	SSABlockLivenessAnalyser liveness;
	NullPermeableHashMap<Local, Set<Local>> interfere;
	
	// critical maps.
	final Map<Local, Set<Local>> phiCongruenceClasses;
	// utility maps.
	final Map<Local, CopyVarStatement> phiDefs;
	final NullPermeableHashMap<Local, Set<Statement>> vusages;
	
	public SreedharDestructor(CodeBody code, ControlFlowGraph cfg) {
		this.code = code;
		this.cfg = cfg;
		
		interfere = new NullPermeableHashMap<>(new SetCreator<>());
		headers = new HashMap<>();
		phiCongruenceClasses = new HashMap<>();
		phiDefs = new HashMap<>();
		vusages = new NullPermeableHashMap<>(new SetCreator<>());
		
		init();
		updateInterference();
		csaa_iii();
		
		{ // scope here to help me keep track of where all the debug crap is
			System.out.println("after:");
			System.out.println(code);
			GraphUtils.rewriteCfg(cfg, code);
			BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
			DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> w = new DotWriter<>(config, cfg);
			SSABlockLivenessAnalyser livenessDbg = new SSABlockLivenessAnalyser(cfg);
			GraphUtils.rewriteCfg(cfg, code);
			livenessDbg.compute();
			w.removeAll()
					.setName("sreedhar-cssa")
					.add("liveness", new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(livenessDbg))
					.addBefore("liveness", "cfg", new ControlFlowGraphDecorator().setFlags(OPT_DEEP | OPT_STMTS))
					.export();
//			InterferenceGraph ig = InterferenceGraphBuilder.build(cfg, livenessDbg);
//			BasicDotConfiguration<InterferenceGraph, ColourableNode, InterferenceEdge> config2 = new BasicDotConfiguration<>(DotConfiguration.GraphType.UNDIRECTED);
//			DotWriter<InterferenceGraph, ColourableNode, InterferenceEdge> w2 = new DotWriter<>(config2, ig);
//			w2.add(new InterferenceGraphDecorator()).setName("sreedhar-cssa-ig").export();
		}
		
		nullify();
		updateInterference();
		coalesce();
		
		{ // scope here to help me keep track of where all the debug crap is
			System.out.println("after:");
			System.out.println(code);
			BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
			DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> w = new DotWriter<>(config, cfg);
			w.removeAll()
					.setName("sreedhar-coalesce")
					.add("cfg", new ControlFlowGraphDecorator().setFlags(OPT_DEEP | OPT_STMTS))
					.export();
		}
			
		unssa();
	}
	
	void verify() {
		NullPermeableHashMap<Local, Set<Statement>> vusages = new NullPermeableHashMap<>(new SetCreator<>());
		for(Statement stmt : code) {
			for(Statement s : Statement.enumerate_deep(stmt)) {
				if(s instanceof VarExpression) {
					vusages.getNonNull(((VarExpression) s).getLocal()).add(stmt);
				}
			}
			if(stmt instanceof CopyVarStatement) {
				vusages.getNonNull(((CopyVarStatement) stmt).getVariable().getLocal()).add(stmt);
			}
		}
		
		for(Entry<Local, Set<Statement>> e : vusages.entrySet()) {
			Local l = e.getKey();
			if(!this.vusages.containsKey(l)) {
				System.err.println(code);
				throw new RuntimeException(l.toString());
			} else if(!e.getValue().equals(this.vusages.get(l))) {
				System.err.println(code);
				System.err.println(l);
				System.err.println(vusages.get(l));
				System.err.println(this.vusages.get(l));
				throw new RuntimeException();
			}
		}
		
		updateInterference();
		
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b.getStatements()) {
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					if(copy.getExpression() instanceof PhiExpression) {
						Local l = copy.getVariable().getLocal();
						List<Local> pcc = new ArrayList<>(phiCongruenceClasses.get(l));

						for(int i=0; i < pcc.size(); i++) {
							Local j = pcc.get(i);
							
							for(int k=i+1; k < pcc.size(); k++) {
								Local m = pcc.get(k);
								
								if(interfere(j, m)) {
									System.err.println(stmt);
									System.err.println("j: " + j +", m: " + m);
									throw new RuntimeException();
								}
							}
						}
					}
				}
			}
		}
	}
	
	void replace_uses(Statement stmt, Local o, Local l) {
		for(Statement s : Statement.enumerate_deep(stmt)) {
			if(s instanceof VarExpression) {
				VarExpression v = (VarExpression) s;
				if(v.getLocal() == o) {
					v.setLocal(l);
				}
			}
		}
		if(stmt instanceof CopyVarStatement) {
			CopyVarStatement c = (CopyVarStatement) stmt;
			VarExpression v = c.getVariable();
			if(v.getLocal() == l) {
				v.setLocal(l);
			}
		}
	}
	
	void _added(Statement stmt) {
		for(Statement s : Statement.enumerate_deep(stmt)) {
			if(s instanceof VarExpression) {
				vusages.getNonNull(((VarExpression) s).getLocal()).add(stmt);
			}
		}
		if(stmt instanceof CopyVarStatement) {
			vusages.getNonNull(((CopyVarStatement) stmt).getVariable().getLocal()).add(stmt);
		}
	}
	
	void _removed(Statement stmt) {
		for(Statement s : Statement.enumerate_deep(stmt)) {
			if(s instanceof VarExpression) {
				vusages.getNonNull(((VarExpression) s).getLocal()).remove(stmt);
			}
		}
		if(stmt instanceof CopyVarStatement) {
			vusages.getNonNull(((CopyVarStatement) stmt).getVariable().getLocal()).remove(stmt);
		}
	}
	
	void init() {
		for(Statement stmt : code) {
			if(stmt instanceof BlockHeaderStatement) {
				BlockHeaderStatement hs = (BlockHeaderStatement) stmt;
				BasicBlock b = hs.getBlock();
				headers.put(b, hs);
			} else {
				_added(stmt);
			}
		}
	}
	
	void updateInterference() {
		liveness = new SSABlockLivenessAnalyser(cfg);
		GraphUtils.rewriteCfg(cfg, code);
		liveness.compute();
		// FIXME: interference
//		interfere = InterferenceGraphBuilder.buildMap(cfg, liveness);
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
					PhiResource newR = new PhiResource(r.block, newL, r.target, r.type);
					ress.remove(r);
					ress.add(newR);
				}

//				System.out.println("Phi: " + copy);
//				for(PhiResource r : ress) {
//					System.out.println("  res: " + r);
//				}
				Set<Local> mset = new HashSet<>();
				for(PhiResource r : ress) {
					mset.add(r.local);
				}
				
				Set<Local> congmset = merge_pcc(mset);
				for(Local s : mset) {
					phiCongruenceClasses.remove(s);
					phiCongruenceClasses.put(s, congmset);
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
		
		res.add(map_initial_pcc(l0, dv, true));
		
		PhiExpression phi = (PhiExpression) copy.getExpression();
		for(Entry<HeaderStatement, Expression> en : phi.getArguments().entrySet()) {
			Expression e = en.getValue();
			if(!(e instanceof VarExpression)) {
				throw new UnsupportedOperationException("Cannot perform Sreedhar 3 on propagated phi resources: " + e);
			}
			VarExpression v = (VarExpression) e;
			BasicBlock li = ((BlockHeaderStatement) en.getKey()).getBlock();
			res.add(map_initial_pcc(li, v, false));
		}
	}
	
	PhiResource map_initial_pcc(BasicBlock li, VarExpression v, boolean target) {
		Local xi = v.getLocal();
		// phiCongruenceClass[x] = {x};
		if(!phiCongruenceClasses.containsKey(xi)) {
			Set<Local> pcc = new HashSet<>();
			pcc.add(xi);
			phiCongruenceClasses.put(xi, pcc);
		}
		
		// don't need to do this here now, do it
		// in csaa translation method.
		// unresolvedNeighborMap[xi] = {};
		// if(!unresolvedNeighbours.containsKey(xi)) {
		// 	unresolvedNeighbours.put(xi, new HashSet<>());
		// }
		
		PhiResource pr = new PhiResource(li, xi, target, v.getType());
		return pr;
	}
	
	Set<PhiResource> find_copy_candidates(BasicBlock b, List<PhiResource> resources) {
		int len = resources.size();
		
		Map<Local, Set<Local>> unresolvedNeighbours = new HashMap<>();
		
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
		
		List<PhiResource> sorted = reorder(conflicts, unresolvedNeighbours);
		
		for(PhiResource r : sorted) {
			Local l = r.local;
			if(candidates.contains(r)) {
				continue;
			}
			
			Set<Local> neighbours = unresolvedNeighbours.get(l);
			boolean should = false;
			for(Local n : neighbours) {
				// this should be a small set i think.
				boolean contains = false;
				for(PhiResource c : candidates) {
					if(c.local == n) {
						contains = true;
						break;
					}
				}
				if(!contains) {
					should = true;
				}
			}
			if(should) {
				candidates.add(r);
			}
		}
		
		System.out.println("cand: " + candidates);
		return candidates;
	}
	
	List<PhiResource> reorder(Set<PhiResource> conflicts, Map<Local, Set<Local>> unresolvedNeighbours) {
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
		verify();
		
		PhiExpression phi = (PhiExpression) phiCopy.getExpression();
		Local xi = r.local;
		Type type = r.type;
		BasicBlock li = r.block;

		Local newL = null;
		Local dst = null;
		Local src = null;
		
		if(r.target) {
			VersionedLocal latest = code.getLocals().getLatestVersion(xi);
			VersionedLocal new0 = code.getLocals().get(latest.getIndex(), latest.getIndex() + 1, latest.isStack());
			
			dst = xi;
			src = new0;
			newL = new0;
		} else {
			VersionedLocal latest = code.getLocals().getLatestVersion(xi);
			VersionedLocal newi = code.getLocals().get(latest.getIndex(), latest.getSubscript() + 1, latest.isStack());
			
			dst = newi;
			src = xi;
			newL = newi;
		}
		
		CopyVarStatement copy = new CopyVarStatement(new VarExpression(dst, type), new VarExpression(src, type));
		System.out.println("InsertCopy: " + copy + "  [[" + r + "]]");
		System.out.println("PCC: " + phiCongruenceClasses.get(newL));
//		if (!phiCongruenceClasses.containsKey(newL)) {
			Set<Local> set = new HashSet<>();
			set.add(newL);
			phiCongruenceClasses.put(newL, set);
//		}
		
		if(r.target) {
			// xi = new0;
			insert_start(l0, copy);
			phiCopy.setVariable(new VarExpression(newL, type));
		} else {
			// newi = xi;
			insert_end(li, copy);
			
			HeaderStatement header = headers.get(li);
			Map<HeaderStatement, Expression> cont = phi.getArguments();
			cont.put(header, new VarExpression(newL, type));
		}
		
		vusages.getNonNull(xi).remove(phiCopy);
		vusages.getNonNull(newL).add(phiCopy);
		
		updateInterference();
		verify();
		
		return newL;
	}
	
	void insert_start(BasicBlock b, Statement s) {
		List<Statement> stmts = b.getStatements();

		int i = code.indexOf(headers.get(b));
		if(i == -1) {
			throw new IllegalStateException(b.getId());
		}
		
		while (PhiExpression.phi(code.get(++i)));
		
		code.add(i, s);
		stmts.add(0, s);
		
		_added(s);
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
		
		_added(s);
	}
	
	void nullify() {
		for(Entry<Local, Set<Local>> e : new HashSet<>(phiCongruenceClasses.entrySet())) {
			Local l = e.getKey();
			if(e.getValue().size() == 1) {
				phiCongruenceClasses.put(l, new HashSet<>());
			}
		}
	}
	
	void coalesce() {
		updateInterference();
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : new ArrayList<>(b.getStatements())) {
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					Expression e = copy.getExpression();
					if(!(e instanceof VarExpression)) {
						continue;
					}
					
					Local lhs = copy.getVariable().getLocal();
					Local rhs = ((VarExpression) e).getLocal();
					
					Set<Local> lpcc = phiCongruenceClasses.get(lhs);
					Set<Local> rpcc = phiCongruenceClasses.get(rhs);
					
					if(lpcc == null || rpcc == null) {
						continue;
					}
					
					if(check_coalesce(lhs, rhs, lpcc, rpcc)) {
						verify();
						coalesce(b, stmt, lhs, rhs, lpcc, rpcc);
						updateInterference();
						verify();
					}
				}
			}
		}
	}
	
	boolean check_coalesce(Local lhs, Local rhs, Set<Local> lpcc, Set<Local> rpcc) {
		int l = lpcc.size();
		int r = rpcc.size();
		boolean valid = true;
		
		System.out.println("co: ");
		System.out.println("  lhs: " + lhs);
		System.out.println("  rhs: " + rhs);
		System.out.println("  lpcc: " + lpcc);
		System.out.println("  rpcc: " + rpcc);
		
		if(l == 0 && r == 0) { 
			// if pcc[x] and pcc[y] are both empty then the copy can be removed regardless of interference.
			System.out.println("coalesce case 1");
		} else if(l == 0 && r > 0) {
			// check if lhs interferes with (pcc[rhs] - rhs)
			System.out.println("check cc2 (left-handed)");
			valid = !interfereSingle(rpcc, rhs, lhs);
			if(valid)
				System.out.println("coalesce case 2 (lpcc empty)");
		} else if(l > 0 && r == 0) {
			// check if rhs interferes with (pcc[lhs] - lhs)
			valid = !interfereSingle(lpcc, lhs, rhs);
			if(valid)
				System.out.println("coalesce case 2 (rpcc empty)");
		} else if(l > 0 && r > 0) {
			// i.e. if the pcc's are different, we need to
			//      check for interference. if they are the
			//      same then the copy can be easily eliminated.
			if(!(l == r && lpcc.equals(rpcc))) {
				// check if (lpcc - l) interferes with rpcc
				//    or
				//          (rpcc - r) interferes with lpcc
				valid = !interfere(lpcc, rpcc, lhs, rhs);
				if(valid)
					System.out.println("coalesce case 3");
			}
		}
		
		return valid;
	}

	// 1. check any in jpcc vs (ipcc - i)
	// 2. check any in ipcc vs (jpcc - j)
	boolean interfere(Set<Local> ipcc, Set<Local> jpcc, Local i, Local j) {
		return interfereAny(ipcc, i, jpcc) || interfereAny(jpcc, j, ipcc);
	}
	
	// check if any in jpcc interfere with (ipcc - i)
	boolean interfereAny(Set<Local> ipcc, Local i, Set<Local> jpcc) {
		for(Local m : ipcc) {
			// (pcc[l] - l)
			if(m != i) {
				for(Local n : jpcc) {
					if(interfere(m, n)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	// check if j interferes with any in (ipcc - i)
	boolean interfereSingle(Set<Local> ipcc, Local i, Local j) {
		for(Local l : ipcc) {
			// (pcc[l] - l) interfere with j
			if(l != i && interfere(j, l)) {
				System.out.println(j + " interferes with " + l);
				return true;
			}
		}
		return false;
	}
	
	void coalesce(BasicBlock b, Statement s, Local lhs, Local rhs, Set<Local> lpcc, Set<Local> rpcc) {
		System.out.println("coalesce " + s);
		
		// remove lhs = rhs
		//  so replace all uses of lhs with rhs.
		
		verify();
		
		Iterator<Statement> it = vusages.getNonNull(lhs).iterator();
		while(it.hasNext()) {
//			Statement t = it.next();
//			replace_uses(t, lhs, rhs);
//			vusages.getNonNull(rhs).add(t);
//			it.remove();
		}
		
		b.getStatements().remove(s);
		code.remove(s);
		_removed(s);
		
		Set<Local> newPcc = new HashSet<>();
		newPcc.add(lhs);
		newPcc.add(rhs);
		newPcc.addAll(lpcc);
		newPcc.addAll(rpcc);
		
		Set<Local> mset = merge_pcc(newPcc);
		mset.add(lhs);
		mset.add(rhs);
		
		for(Local l : mset) {
			phiCongruenceClasses.remove(l);
			phiCongruenceClasses.put(l, mset);
			
			// TODO: update other info.
		}
		
		verify();
	}

	boolean interfere(Local i, Local j) {
		System.out.println(i + " vs " + j + " " + interfere.getNonNull(i).contains(j));
		return interfere.getNonNull(i).contains(j);
	}
	
	void unssa() {
		for(Entry<Local, Set<Local>> e : phiCongruenceClasses.entrySet()) {
			System.out.println(e.getKey() + " " + e.getValue());
		}
		AtomicInteger cur = new AtomicInteger(0);
		Map<Set<Local>, Local> remap = new HashMap<>();
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : new ArrayList<>(b.getStatements())) {
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement c = (CopyVarStatement) stmt;
					if(c.getExpression() instanceof PhiExpression) {
						b.getStatements().remove(stmt);
						code.remove(stmt);
						continue;
					} else {
						 
					}
					
					rename(c.getVariable(), cur, remap);
				}
				
				for(Statement s : Statement.enumerate_deep(stmt)) {
					if(s instanceof VarExpression) {
						VarExpression v = (VarExpression) s;
						rename(v, cur, remap);
					}
				}
			}
		}
	}
	
	void rename(VarExpression v, AtomicInteger cur, Map<Set<Local>, Local> remap) {
		Local l = v.getLocal();
		Set<Local> pcc = phiCongruenceClasses.get(l);
		if(pcc != null) {
			if(!remap.containsKey(pcc)) {
				remap.put(pcc, l);
			}
			
			System.out.println("remap " + l + " to " + remap.get(pcc) + " " + pcc + " (" + pcc.hashCode() + ")");
			l = remap.get(pcc);
			v.setLocal(l);
		}
	}

	boolean intersects(Set<Local> pcc, Set<Local> live) {
		Set<Local> merged = merge_pcc(live);
		
		for(Local l : pcc) {
			if(merged.contains(l)) {
				return true;
			}
		}
		return false;
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
		final Set<Local> iLive;
		final Set<Local> jLive;
		
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
		final boolean target;
		final Type type;
		
		PhiResource(BasicBlock block, Local local, boolean target, Type type) {
			this.block = block;
			this.local = local;
			this.target = target;
			this.type = type;
		}
		
		@Override
		public String toString() {
			return block.getId() + ":" + local + "(" + type + ")" + " (targ=" + Boolean.toString(target).substring(0, 1) + ")";
		}
	}
}