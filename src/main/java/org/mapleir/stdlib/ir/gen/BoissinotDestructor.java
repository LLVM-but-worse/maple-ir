package org.mapleir.stdlib.ir.gen;

import static org.mapleir.stdlib.collections.graph.dot.impl.ControlFlowGraphDecorator.*;

import java.util.*;
import java.util.Map.Entry;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.FastBlockGraph;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.collections.ListCreator;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.dot.BasicDotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.collections.graph.dot.impl.ControlFlowGraphDecorator;
import org.mapleir.stdlib.collections.graph.dot.impl.LivenessDecorator;
import org.mapleir.stdlib.collections.graph.flow.TarjanDominanceComputor;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.ir.code.CodeBody;
import org.mapleir.stdlib.ir.gen.BoissinotDestructor.ExtendedDfs;
import org.mapleir.stdlib.ir.header.BlockHeaderStatement;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.ir.transform.Liveness;
import org.mapleir.ir.analysis.dataflow.impl.CodeAnalytics;
import org.mapleir.ir.transform.ssa.SSALivenessAnalyser;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class BoissinotDestructor implements Liveness<BasicBlock> {

	private final ControlFlowGraph cfg;
	private final CodeBody code;
	private final Map<BasicBlock, HeaderStatement> headers;
	private final Map<Local, BasicBlock> defs;
	private final NullPermeableHashMap<Local, Set<BasicBlock>> uses;
	private final Set<Local> phis;
	
	private InterferenceResolver resolver;
	
	// delete me
	private final Set<Local> localsTest = new HashSet<>();
	
	public BoissinotDestructor(ControlFlowGraph cfg, CodeBody code) {
		this.cfg = cfg;
		this.code = code;
		headers = new HashMap<>();
		defs = new HashMap<>();
		uses = new NullPermeableHashMap<>(new SetCreator<>());
		phis = new HashSet<>();
		
		init();
		insert_copies();
		verify();
		
		BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
		DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, cfg);
		writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP | OPT_STMTS))
		.setName("after-insert")
		.export();
		
		resolver = new InterferenceResolver();
		
		SSALivenessAnalyser live = new SSALivenessAnalyser(cfg);
		for(BasicBlock b : cfg.vertices())
			for (Statement stmt : b.getStatements())
				for (Local l : stmt.getUsedLocals())
					localsTest.add(l);
		

		
		writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP | OPT_STMTS))
				.add("liveness", new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(this))
				.setName("liveness-baguette")
				.export();
		writer.removeAll()
				.add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP | OPT_STMTS))
				.add(new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(live))
				.setName("liveness-bibl")
				.export();
		
		coalesce();
		
		
//		for(BasicBlock b : cfg.vertices()) {
//			Set<Local> l1 = live.in(b);
////			Set<Local> l2 = in(b);
//			
//			for(Local l : l1) {
//				boolean b1 = resolver.live_in(b, l);
//				if(!b1) {
//					System.err.println(b.getId() + " -> " + l);
//					throw new RuntimeException();
//				}
//			}
//			
//			Set<Local> l2 = new HashSet<>(localsTest);
//			l2.removeAll(l1);
//			
//			for(Local l : l2) {
//				boolean b1 = resolver.live_in(b, l);
//				if(b1) {
//					System.err.println(b.getId() + " -> !" + l);
//					throw new RuntimeException();
//				}
//			}
//		}
	}
	
	void verify() {
		System.out.println(code);
		StringBuilder sb = new StringBuilder();
		for(BasicBlock b : cfg.vertices()) {
			GraphUtils.printBlock(cfg, sb, b, 0, true, true);
			sb.append("\n");
		}
		System.out.println(sb.toString());
		Map<Local, BasicBlock> defs = new HashMap<>();
		NullPermeableHashMap<Local, Set<BasicBlock>> uses = new NullPermeableHashMap<>(new SetCreator<>());
		
		for (BasicBlock b : cfg.vertices()) {
			for (Statement stmt : b.getStatements()) {
				if(stmt instanceof ParallelCopyVarStatement) {
					ParallelCopyVarStatement pcopy = (ParallelCopyVarStatement) stmt;
					for(CopyPair p : pcopy.pairs) {
						defs.put(p.targ, b);
						uses.getNonNull(p.source).add(b);
					}
				} else {
					boolean _phi = false;
					if (stmt instanceof CopyVarStatement) {
						CopyVarStatement copy = (CopyVarStatement) stmt;
						Local l = copy.getVariable().getLocal();
						defs.put(l, b);

						Expression e = copy.getExpression();
						if (e instanceof PhiExpression) {
							_phi = true;
							PhiExpression phi = (PhiExpression) e;
							for (Entry<HeaderStatement, Expression> en : phi.getArguments().entrySet()) {
								BasicBlock p = ((BlockHeaderStatement) en.getKey()).getBlock();
								Local ul = ((VarExpression) en.getValue()).getLocal();
								uses.getNonNull(ul).add(p);
							}

							phis.add(l);
						}
					}

					if (!_phi) {
						for (Statement s : Statement.enumerate(stmt)) {
							if (s instanceof VarExpression) {
								Local l = ((VarExpression) s).getLocal();
								uses.getNonNull(l).add(b);
							}
						}
					}
				}
			}
		}
		
		Set<Local> set = new HashSet<>();
		set.addAll(defs.keySet());
		set.addAll(this.defs.keySet());
		
		for(Local l : set) {
			BasicBlock b1 = defs.get(l);
			BasicBlock b2 = this.defs.get(l);
			
			if(b1 != b2) {
				System.err.println("Defs:");
				System.err.println(b1 + ", " + b2 + ", " + l);
				throw new RuntimeException();
			}
		}
		
		set.clear();
		set.addAll(uses.keySet());
		set.addAll(this.uses.keySet());
		
		for(Local l : set) {
			Set<BasicBlock> s1 = uses.getNonNull(l);
			Set<BasicBlock> s2 = this.uses.getNonNull(l);
			if(!s1.equals(s2)) {
				System.err.println("Uses:");
				System.err.println(GraphUtils.toBlockArray(s1, false));
				System.err.println(GraphUtils.toBlockArray(s2, false));
				System.err.println(l);
				throw new RuntimeException();
			}
		}
	}
	
	@Override
	public Set<Local> in(BasicBlock b) {
		Set<Local> live = new HashSet<>();
		for (Local l : localsTest)
			if (resolver.live_in(b, l))
				live.add(l);
		return live;
	}
	
	@Override
	public Set<Local> out(BasicBlock b) {
		return new HashSet<>();
	}
	
	void init() {
		BasicBlock b = null;
		for(Statement stmt : code) {
			if(stmt instanceof BlockHeaderStatement) {
				BlockHeaderStatement h = (BlockHeaderStatement) stmt;
				b = h.getBlock();
				headers.put(b, h);
			} else {
				boolean _phi = false;
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					Local l = copy.getVariable().getLocal();
					defs.put(l, b);
					
					Expression e = copy.getExpression();
					if(e instanceof PhiExpression) {
						_phi = true;
						PhiExpression phi = (PhiExpression) e;
						for(Entry<HeaderStatement, Expression> en : phi.getArguments().entrySet()) {
							BasicBlock p = ((BlockHeaderStatement) en.getKey()).getBlock();
							Local ul = ((VarExpression) en.getValue()).getLocal();
							uses.getNonNull(ul).add(p);
						}
						
						phis.add(l);
					}
				}
				
				if(!_phi) {
					for(Statement s : Statement.enumerate(stmt)) {
						if(s instanceof VarExpression) {
							Local l = ((VarExpression) s).getLocal();
							uses.getNonNull(l).add(b);
						}
					}
				}
			}
		}
	}

	void insert_copies() {
		for(BasicBlock b : cfg.vertices()) {
			insert_copies(b);
		}
	}
	
	void insert_copies(BasicBlock b) {
		NullPermeableHashMap<BasicBlock, List<PhiRes>> wl = new NullPermeableHashMap<>(new ListCreator<>());
		ParallelCopyVarStatement dst_copy = new ParallelCopyVarStatement();
		
		// given a phi: L0: x0 = phi(L1:x1, L2:x2)
		//  insert the copies:
		//   L0: x0 = x3 (at the end of L0)
		//   L1: x4 = x1
		//   L2: x5 = x2
		//  and change the phi to:
		//   x3 = phi(L1:x4, L2:x5)
		
		for(Statement stmt : b.getStatements()) {
			if(!PhiExpression.phi(stmt)) {
				break;
			}
			
			CopyVarStatement copy = (CopyVarStatement) stmt;
			PhiExpression phi = (PhiExpression) copy.getExpression();
			
			// for every xi arg of the phi from pred Li, add it to the worklist
			// so that we can parallelise the copy when we insert it.
			for(Entry<HeaderStatement, Expression> e : phi.getArguments().entrySet()) {
				BlockHeaderStatement h = (BlockHeaderStatement) e.getKey();
				VarExpression v = (VarExpression) e.getValue();
				PhiRes r = new PhiRes(copy.getVariable().getLocal(), phi, h, v.getLocal(), v.getType());
				wl.getNonNull(h.getBlock()).add(r);
			}
			
			// for each x0, where x0 is a phi copy target, create a new
			// variable z0 for a copy z0 = x0 and replace the phi
			// copy target to z0.
			Local x0 = copy.getVariable().getLocal();
			Local z0 = code.getLocals().makeLatestVersion(x0);
			dst_copy.pairs.add(new CopyPair(x0, z0)); // x0 = z0
			copy.getVariable().setLocal(z0); // z0 = phi(...)
			
			// both defined and used in this block.
			defs.put(x0, b);
			defs.put(z0, b);
			uses.getNonNull(x0).add(b);
			uses.getNonNull(z0).add(b);
		}
		
		// resolve
		if(dst_copy.pairs.size() > 0) {
			insert_start(b, dst_copy);
		}
		
		for(Entry<BasicBlock, List<PhiRes>> e : wl.entrySet()) {
			BasicBlock p = e.getKey();
			
			ParallelCopyVarStatement copy = new ParallelCopyVarStatement();
			
			for(PhiRes r : e.getValue()) {
				// for each xi source in a phi, create a new variable zi,
				// and insert the copy zi = xi in the pred Li. then replace
				// the phi arg from Li with zi.
				
				Local xi = r.l;
				Local zi = code.getLocals().makeLatestVersion(xi);
				copy.pairs.add(new CopyPair(zi, xi));
				
				// we consider phi args to be used in the pred
				//  instead of the block where the phi is, so
				//  we need to update the def/use maps here.
				
				// zi is defined in the pred.
				defs.put(zi, p);
				// xi is used in the zi def.
				uses.getNonNull(zi).add(p);
				// xi is replaced with zi in the phi block,
				//  but for this implementation, we consider
				//  the phi source uses to be in the pre.
				//  n.b. that zi, which should be used in the
				//       phi pred is already added above.
				uses.getNonNull(xi).remove(p);
				
				r.phi.setArgument(r.pred, new VarExpression(zi, r.type));
			}

			insert_end(p, copy);
		}
	}
	
	void record_pcopy(BasicBlock b, ParallelCopyVarStatement copy) {
		System.out.println("INSERT: " + copy);
		
		for(CopyPair p : copy.pairs) {
			defs.put(p.targ, b);
			uses.getNonNull(p.source).add(b);
			
			localsTest.add(p.targ);
			localsTest.add(p.source);
		}
	}
	
	void insert_empty(BasicBlock b, List<Statement> stmts, ParallelCopyVarStatement copy) {
		int i = code.indexOf(headers.get(b));
		if(i == -1) {
			throw new IllegalStateException(b.getId());
		}
		code.add(i + 1, copy);
		stmts.add(copy);
	}
	
	void insert_start(BasicBlock b, ParallelCopyVarStatement copy) {
		record_pcopy(b, copy);

		List<Statement> stmts = b.getStatements();		
		if(stmts.isEmpty()) {
			insert_empty(b, stmts, copy);
		} else {
			// insert after phi.
			int i = 0;
			Statement stmt = stmts.get(0);
			while(PhiExpression.phi(stmt)) {
				stmt = stmts.get(++i);
			}
			
			stmts.add(stmts.indexOf(stmt), copy);
			code.add(code.indexOf(stmt), copy);
		}
	}
	
	void insert_end(BasicBlock b, ParallelCopyVarStatement copy) {
		record_pcopy(b, copy);
		
		List<Statement> stmts = b.getStatements();
		if(stmts.isEmpty()) {
			insert_empty(b, stmts, copy);
		} else {
			Statement last = stmts.get(stmts.size() - 1);
			int index = code.indexOf(last);
			if(!last.canChangeFlow()) {
				index += 1;
				stmts.add(copy);
			} else {
				// index += 1;
				//  ^ do this above so that s goes to the end
				//    but here it needs to go before the end/jump.
				// add before the jump
				stmts.add(stmts.indexOf(last), copy);
			}
			code.add(index, copy);
		}
	}
	
	void coalesce() {
		// since we are now in csaa, all phi locals
		//  can we coalesced into the same var.
		
		Map<Local, Local> remap = new HashMap<>();
//		Set<Set<Local>> pccs = new HashSet<>();
		for(BasicBlock b : cfg.vertices()) {
			Iterator<Statement> it = b.getStatements().iterator();
			while(it.hasNext()) {
				Statement stmt = it.next();
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					Expression e = copy.getExpression();
					if(e instanceof PhiExpression) {
//						Set<Local> pcc = new HashSet<>();
//						pcc.add(copy.getVariable().getLocal());
						Local l1 = copy.getVariable().getLocal();
						Local newL = code.getLocals().makeLatestVersion(l1);
						remap.put(l1, newL);
						
						PhiExpression phi = (PhiExpression) e;
						
						for(Expression ex : phi.getArguments().values()) {
							VarExpression v = (VarExpression) ex;
							Local l = v.getLocal();
							remap.put(l, newL);
//							pcc.add(l);
						}
						
//						pccs.add(pcc);
						
						code.remove(copy);
						it.remove();
					}
				}
			}
		}
		
		for(Statement stmt : code) {
			if(stmt instanceof ParallelCopyVarStatement) {
				ParallelCopyVarStatement copy = (ParallelCopyVarStatement) stmt;
				for(CopyPair p : copy.pairs) {
					p.source = remap.getOrDefault(p.source, p.source);
					p.targ = remap.getOrDefault(p.targ, p.targ);
				}
			} else {
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					VarExpression v = copy.getVariable();
					v.setLocal(remap.getOrDefault(v.getLocal(), v.getLocal()));
				}
				
				for(Statement s : Statement.enumerate(stmt)) {
					if(s instanceof VarExpression) {
						VarExpression v = (VarExpression) s;
						v.setLocal(remap.getOrDefault(v.getLocal(), v.getLocal()));
					}
				}
			}
		}
	}
	
	class InterferenceResolver {

		final NullPermeableHashMap<BasicBlock, Set<BasicBlock>> rv;
		final NullPermeableHashMap<BasicBlock, Set<BasicBlock>> tq;
		final NullPermeableHashMap<BasicBlock, Set<BasicBlock>> sdoms;
		final Map<Local, Local> values;
		
		BasicBlock entry;
		ControlFlowGraph red_cfg;
		ExtendedDfs cfg_dfs;
		ExtendedDfs reduced_dfs;
		ExtendedDfs dom_dfs;
		TarjanDominanceComputor<BasicBlock> domc;
		
		public InterferenceResolver() {
			rv = new NullPermeableHashMap<>(new SetCreator<>());
			tq = new NullPermeableHashMap<>(new SetCreator<>());
			sdoms = new NullPermeableHashMap<>(new SetCreator<>());
			values = new HashMap<>();
			
			compute_reduced_reachability();
			compute_strict_doms();
			compute_value_interference();
		}
		
		void compute_value_interference() {
			FastBlockGraph dom_tree = new FastBlockGraph();
			for(Entry<BasicBlock, Set<BasicBlock>> e : domc.getTree().entrySet()) {
				BasicBlock b = e.getKey();
				for(BasicBlock c : e.getValue()) {
					dom_tree.addEdge(b, new ImmediateEdge<>(b, c));
				}
			}
			
			BasicDotConfiguration<FastBlockGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
			DotWriter<FastBlockGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, dom_tree);
			writer.removeAll()
			.setName("domtree")
			.export();
			
			dom_dfs = new ExtendedDfs(dom_tree, entry, ExtendedDfs.POST);
			
			// topo
			Collections.reverse(dom_dfs.post);
			for(BasicBlock bl : dom_dfs.post) {
				List<Statement> stmts = bl.getStatements();
				for(Statement stmt : stmts) {
					if(stmt instanceof CopyVarStatement) {
						CopyVarStatement copy = (CopyVarStatement) stmt;
						Expression e = copy.getExpression();
						Local b = copy.getVariable().getLocal();
						
						if(e instanceof VarExpression) {
							Local a = ((VarExpression) e).getLocal();
							values.put(b, values.get(a));
						} else {
							values.put(b, b);
						}
					} else if(stmt instanceof ParallelCopyVarStatement) {
						ParallelCopyVarStatement copy = (ParallelCopyVarStatement) stmt;
						for(CopyPair p : copy.pairs) {
							values.put(p.targ, values.get(p.source));
						}
					}
				}
			}
		}
		
		void compute_strict_doms() {
			domc = new TarjanDominanceComputor<>(cfg);

			NullPermeableHashMap<BasicBlock, Set<BasicBlock>> sdoms = new NullPermeableHashMap<>(new SetCreator<>());
			// i think this is how you do it..
			for(BasicBlock b : cfg_dfs.pre) {
				BasicBlock idom = domc.idom(b);
				if(idom != null) {
					sdoms.getNonNull(b).add(idom);
					sdoms.getNonNull(b).addAll(sdoms.getNonNull(idom));
				}
			}
			
			for(Entry<BasicBlock, Set<BasicBlock>> e : sdoms.entrySet()) {
				for(BasicBlock b : e.getValue()) {
					this.sdoms.getNonNull(b).add(e.getKey());
				}
			}
			
			for(BasicBlock b : cfg.vertices()) {
				System.out.println(b.getId() + " sdoms " + GraphUtils.toBlockArray(this.sdoms.getNonNull(b), false));
			}
		}
		
		void compute_reduced_reachability() {
			// This gives rise to the reduced graph 'eG' of G 
			//  which contains everything from G but the back 
			//  edges. If there is a path from q to u in the 
			//  reduced graph we say that u is reduced reachable 
			//  from q. To be able to efficiently check for reduced 
			//  reachability we precompute the transitive closure
			//  of this relation. For each node v we store in Rv 
			//  all nodes reduced reachable from v.
			
			// r(v) = {w where there is a path from v to w in eG}
			
			if(cfg.getEntries().size() != 1) {
				throw new IllegalStateException(cfg.getEntries().toString());
			}
			
			entry = cfg.getEntries().iterator().next();
			
			cfg_dfs = new ExtendedDfs(cfg, entry, ExtendedDfs.EDGES | ExtendedDfs.PRE /* for sdoms*/ );
			Set<FlowEdge<BasicBlock>> back = cfg_dfs.edges.get(ExtendedDfs.BACK);
			
			red_cfg = reduce(cfg, back);
			reduced_dfs = new ExtendedDfs(red_cfg, entry, ExtendedDfs.POST | ExtendedDfs.PRE);
			
			BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
			DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, red_cfg);
			writer.add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP | OPT_STMTS))
					.setName("reducedCfg")
					.export();
			
			// rv calcs.
			for (BasicBlock b : reduced_dfs.post) {
				rv.getNonNull(b).add(b);
				for (FlowEdge<BasicBlock> e : red_cfg.getReverseEdges(b))
					rv.getNonNull(e.src).addAll(rv.get(b));
			}
			
			System.out.println("rv:");
			for(BasicBlock b : cfg.vertices())
				System.out.println(b.getId() + " = " + rv.get(b));
			
			// Paths Containing Back Edges: Of course, for the 
			//  completeness of our algorithm we must also handle 
			//  back edges.
			
			// Our goal is to answer a liveness query by testing 
			//  for the reduced reachability of uses from back edge 
			//  targets. Hence, a second part of our precomputation 
			//  constructs for each node q a set Tq that contains all 
			//  back edge targets relevant for this query. For this 
			//  precomputation to make sense, these Tq must be independent 
			//  of variables. Thus, they must contain all relevant 
			//  back edge targets for any variable.
			
			// The first question is, given a specific query (q, a), 
			//  how do we decide which back edge targets of Tq to 
			//  consider? Apparently, this choice depends on the 
			//  variable or more precisely on its dominance subtree.
			
			// FastBlockGraph dfs_tree = make_dfs_tree(dfs);
			
			// tq calcs.
			Map<BasicBlock, Set<BasicBlock>> tups = new HashMap<>();
			
			for (BasicBlock b : cfg.vertices())
				tups.put(b, tup(b, back));
			for (BasicBlock v : reduced_dfs.pre) {
				tq.getNonNull(v).add(v);
				for (BasicBlock w : tups.get(v))
					tq.get(v).addAll(tq.get(w));
			}
			System.out.println("preorder: " + reduced_dfs.pre);
			System.out.println("tq: ");
			for (BasicBlock v : reduced_dfs.pre)
				System.out.println(v.getId() + " = " + tq.get(v));
		}
		
		// Tup(t) = set of unreachable backedge targets from reachable sources
		Set<BasicBlock> tup(BasicBlock t, Set<FlowEdge<BasicBlock>> back) {
			Set<BasicBlock> rt = rv.get(t);
			
			// t' in {V - r(t)}
			Set<BasicBlock> set = new HashSet<>(cfg.vertices());
			set.removeAll(rt);
			
			// all s' where (s', t') is a backedge and s'
			//  is in rt.
			//  because we have O(1) reverse edge lookup,
			//  can find the preds of each t' that is a
			//  backedge.
			
			// set of s'
			Set<BasicBlock> res = new HashSet<>();
			
			for(BasicBlock tdash : set) {
				for(FlowEdge<BasicBlock> pred : cfg.getReverseEdges(tdash)) {
					BasicBlock src = pred.src;
					// s' = src, t' = dst
					if(back.contains(pred) && rt.contains(src)) {
						res.add(pred.dst); // backedge TARGETS
					}
				}
			}
			
			return res;
		}
		
		ControlFlowGraph reduce(ControlFlowGraph cfg, Set<FlowEdge<BasicBlock>> back) {
			ControlFlowGraph reducedCfg = cfg.copy();
			for (FlowEdge<BasicBlock> e : back) {
				reducedCfg.removeEdge(e.src, e);
			}
			return reducedCfg;
		}
		
		FastBlockGraph make_dfs_tree(ExtendedDfs dfs) {
			FastBlockGraph g = new FastBlockGraph();
			// map of node -> parent
			for(Entry<BasicBlock, BasicBlock> e : dfs.parents.entrySet()) {
				BasicBlock src = e.getValue();
				BasicBlock dst = e.getKey();
				FlowEdge<BasicBlock> edge = new ImmediateEdge<>(src, dst);
				g.addEdge(src, edge);
			}
			return g;
		}
		
		boolean live_check()  {
			// 1: A variable a is live-in at a node q if there
			//      exists a path from q to a node u where a is 
			//      used and that path does not contain def (a).
			
			// 2: A variable a is live-out at a node q if it is
			//      live-in at a successor of q.
			
			// The CFG node def (a) where a is defined will be
			//  abbreviated by d. 
			// Furthermore, the variable a is used at a node u.
			
			// The basic idea of the algorithm is simple. It is the 
			// straightforward implementation of Definition 2:
			//    For each use u we test if u is reachable from the query
			//    block q without passing the definition d.
			
			// Our algorithm is thus related to problems such as computing 
			//  the transitive closure or finding a (shortest) path between 
			//  two nodes in a graph. However, the paths relevant for liveness 
			//  are further constrained:
			//    they must not contain the definition of the variable.
			
			// Simple Paths: The first observation considers paths that do not
			//  contain back edges. If such a path starts at some node q strictly
			//  dominated by d and ends at u, all nodes on the path are strictly
			//  dominated by d. Especially, the path cannot contain d. Hence, the
			//  existence of a back-edge-free path from q to u directly proves a
			//  being live-in at q.
			throw new UnsupportedOperationException();
		}
		
		boolean live_in(BasicBlock b, Local l) {
			if(phis.contains(l)) {
				System.out.println("phi: " + l + ", " + b.getId() + ", " + defs.get(l).getId());
			}
			if(phis.contains(l) && defs.get(l) == b) {
				return true;
			}
			
//			System.out.println("LiveInCheck a=" + l + ", q=" +  b.getId());
//			System.out.println("  def=" + defs.get(l).getId() + "; sdoms = " + GraphUtils.toBlockArray(sdoms.get(defs.get(l)), false));
//			System.out.println("  use=" + GraphUtils.toBlockArray(uses.get(l), false));
			Set<BasicBlock> tqa = new HashSet<>(tq.get(b));
//			System.out.println("   tqa1: " + GraphUtils.toBlockArray(tqa, false));
			tqa.retainAll(sdoms.get(defs.get(l)));
//			System.out.println("   tqa2: " + GraphUtils.toBlockArray(tqa, false));
			
			for(BasicBlock t : tqa) {
				Set<BasicBlock> rt = new HashSet<>(rv.get(t));
//				System.out.println("    t=" + t + " ; reachable=" + GraphUtils.toBlockArray(rt, false));
				rt.retainAll(uses.get(l));
				if(!rt.isEmpty()) {
//					System.out.println("=> result: true\n");
					return true;
				}
			}
			
//			System.out.println("=> result: false\n");
			return false;
		}
	}
	
	class PhiRes {
		final Local target;
		final PhiExpression phi;
		final HeaderStatement pred;
		final Local l;
		final Type type;
		
		PhiRes(Local target, PhiExpression phi, HeaderStatement src, Local l, Type type) {
			this.target = target;
			this.phi = phi;
			pred = src;
			this.l = l;
			this.type = type;
		}
	}
	
	static class CopyPair {
		Local targ;
		Local source;
		
		CopyPair(Local dst, Local src) {
			targ = dst;
			source = src;
		}
		
		@Override
		public String toString() {
			return targ + " =P= " + source;
		}
	}
	
	static class ParallelCopyVarStatement extends Statement {
		
		final List<CopyPair> pairs;
		
		ParallelCopyVarStatement() {
			pairs = new ArrayList<>();
		}
		
		ParallelCopyVarStatement(List<CopyPair> pairs) {
			this.pairs = pairs;
		}
		
		@Override
		public void onChildUpdated(int ptr) {
		}

		@Override
		public void toString(TabbedStringWriter printer) {
			printer.print("PARALLEL(");
			
			Iterator<CopyPair> it = pairs.iterator();
			while(it.hasNext()) {
				CopyPair p = it.next();
				printer.print(p.targ.toString());
				
				if(it.hasNext()) {
					printer.print(", ");
				}
			}
			
			printer.print(") = (");
			
			it = pairs.iterator();
			while(it.hasNext()) {
				CopyPair p = it.next();
				printer.print(p.source.toString());
				
				if(it.hasNext()) {
					printer.print(", ");
				}
			}
			
			printer.print(")");
		}

		@Override
		public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
			throw new UnsupportedOperationException("Synthetic");
		}

		@Override
		public boolean canChangeFlow() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean canChangeLogic() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isAffectedBy(Statement stmt) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Statement copy() {
			return new ParallelCopyVarStatement(new ArrayList<>(pairs));
		}

		@Override
		public boolean equivalent(Statement s) {
			return s instanceof ParallelCopyVarStatement && ((ParallelCopyVarStatement) s).pairs.equals(pairs);
		}
	}
}