package org.mapleir.stdlib.ir.gen;

import java.util.*;
import java.util.Map.Entry;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.FastBlockGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.collections.ListCreator;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.collections.graph.dot.BasicDotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration.GraphType;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.collections.graph.flow.TarjanDominanceComputor;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.header.BlockHeaderStatement;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class BoissinotDestructor {

	private final ControlFlowGraph cfg;
	private final CodeBody code;
	private final Map<BasicBlock, HeaderStatement> headers;
	
	public BoissinotDestructor(ControlFlowGraph cfg, CodeBody code) {
		this.cfg = cfg;
		this.code = code;
		headers = new HashMap<>();
		
		init();
		insert_copies();
		compute_value_interference();
	}
	
	void init() {
		for(Statement s : code) {
			if(s instanceof BlockHeaderStatement) {
				BlockHeaderStatement h = (BlockHeaderStatement) s;
				BasicBlock b = h.getBlock();
				headers.put(b, h);
			}
		}
	}

	void insert_copies() {
		for(BasicBlock b : cfg.vertices()) {
			NullPermeableHashMap<BasicBlock, List<PhiRes>> wl = new NullPermeableHashMap<>(new ListCreator<>());
			ParallelCopyVarStatement dstCopy = new ParallelCopyVarStatement();
			
			for(Statement stmt : b.getStatements()) {
				if(PhiExpression.phi(stmt)) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					PhiExpression phi = (PhiExpression) copy.getExpression();
					
					for(Entry<HeaderStatement, Expression> e : phi.getLocals().entrySet()) {
						BlockHeaderStatement h = (BlockHeaderStatement) e.getKey();
						VarExpression v = (VarExpression) e.getValue();
						PhiRes r = new PhiRes(copy.getVariable().getLocal(), phi, h, v.getLocal(), v.getType());
						wl.getNonNull(h.getBlock()).add(r);
					}
					
					Local dst = copy.getVariable().getLocal();
					Local src = code.getLocals().getLatestVersion(dst);
					dstCopy.pairs.add(new CopyPair(dst, src));
					copy.getVariable().setLocal(src);
				} else {
					// phis are only at the start.
					break;
				}
			}
			
			if(dstCopy.pairs.size() > 0) {
				insert_end(b, dstCopy);
			}
			
			for(Entry<BasicBlock, List<PhiRes>> e : wl.entrySet()) {
				BasicBlock p = e.getKey();
				
				ParallelCopyVarStatement copy = new ParallelCopyVarStatement();
				
				for(PhiRes r : e.getValue()) {
					Local src = r.l;
					Local dst = code.getLocals().makeLatestVersion(r.target);
					copy.pairs.add(new CopyPair(dst, src));
					
					r.phi.setLocal(r.src, new VarExpression(dst, r.type));
				}
				
				insert_end(p, copy);
			}
		}
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
	
	void compute_value_interference() {
		TarjanDominanceComputor<BasicBlock> domc = new TarjanDominanceComputor<>(cfg);
		
		FastBlockGraph domTree = new FastBlockGraph();
		for(Entry<BasicBlock, Set<BasicBlock>> e : domc.getTree().entrySet()) {
			BasicBlock b = e.getKey();
			domTree.addVertex(b);
			for(BasicBlock l : e.getValue()) {
				domTree.addEdge(b, new ImmediateEdge<>(b, l));
			}
		}
		
		for(BasicBlock b : domTree.vertices()) {
			if(domTree.getReverseEdges(b).size() == 0) {
				domTree.getEntries().add(b);
			}
		}
		
		BasicDotConfiguration<FastBlockGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(GraphType.DIRECTED);
		DotWriter<FastBlockGraph, BasicBlock, FlowEdge<BasicBlock>> w = new DotWriter<>(config, domTree);
		w.setName("domtree").export();
		
		LinkedList<BasicBlock> wl = new LinkedList<>();
		wl.addAll(domTree.getEntries());
		List<BasicBlock> reversePostOrder = new ArrayList<>();
				
		while(!wl.isEmpty()) {
			BasicBlock b = wl.pop();
			if(!reversePostOrder.contains(b)) {
				reversePostOrder.add(b);
				for(FlowEdge<BasicBlock> sE : domTree.getEdges(b)) {
					wl.add(sE.dst);
				}
			}
		}
		
		System.out.println(reversePostOrder);
		
		compute_reduced_reachability();
		
	}
	
	ControlFlowGraph reducedCfg;
	final List<BasicBlock> postorder = new ArrayList<>(); // postorder is toposort backwards
	
	final NullPermeableHashMap<BasicBlock, Set<BasicBlock>> rv = new NullPermeableHashMap<>(new SetCreator<>());
	final Map<BasicBlock, Set<BasicBlock>> tq = new HashMap<>();
	
	private List<BasicBlock> computePostorder(ControlFlowGraph cfg, BasicBlock entry) {
		Set<BasicBlock> visited = new HashSet<>();
		dfsPostorder(cfg, entry, visited, postorder);
		return postorder;
	}
	
	private void dfsPostorder(ControlFlowGraph cfg, BasicBlock b, Set<BasicBlock> visited, List<BasicBlock> postorder) {
		visited.add(b);
		for (FlowEdge<BasicBlock> e : cfg.getEdges(b))
			if (!visited.contains(e.dst))
				dfsPostorder(cfg, e.dst, visited, postorder);
		postorder.add(b);
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
		
		BasicBlock entry = cfg.getEntries().iterator().next();
		ExtendedDfs dfs = new ExtendedDfs(entry);

		Set<FlowEdge<BasicBlock>> back = dfs.edges.get(ExtendedDfs.BACK);
		reducedCfg = cfg.copy();
		for (FlowEdge<BasicBlock> backEdge : back)
			reducedCfg.removeEdge(backEdge.src, backEdge);
		
		computePostorder(reducedCfg, entry);
		for (BasicBlock b : postorder) {
			rv.getNonNull(b).add(b);
			for (FlowEdge<BasicBlock> e : cfg.getReverseEdges(b))
				rv.getNonNull(e.src).addAll(rv.get(b));
		}
		for(BasicBlock b : postorder) {
			System.out.println(b.getId());
		}
		for(BasicBlock b : cfg.vertices()) {
			System.out.println(b.getId() + " = " + rv.get(b));
		}
		
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
		FastBlockGraph dfs_tree = make_dfs_tree(dfs);
	
		// tq0 = {q}
		for(BasicBlock b : cfg.vertices()) {
			Set<BasicBlock> set = new HashSet<>();
			set.add(b);
			tq.put(b, set);
		}
	}
	
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
				// s' = src
				if(back.contains(pred) && rt.contains(src)) {
					res.add(src);
				}
			}
		}
		
		return res;
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
	
	class ExtendedDfs {
		static final int WHITE = 0, GREY = 1, BLACK = 2;
		static final int TREE = WHITE, BACK = GREY, FOR_CROSS = BLACK;
		
		final NullPermeableHashMap<BasicBlock, Integer> colours;
		final Map<Integer, Set<FlowEdge<BasicBlock>>> edges;
		final Map<BasicBlock, BasicBlock> parents;
		final List<BasicBlock> pre;
		final List<BasicBlock> post;
		
		ExtendedDfs(BasicBlock entry) {
			colours = new NullPermeableHashMap<>(new ValueCreator<Integer>() {
				@Override
				public Integer create() {
					return Integer.valueOf(0);
				}
			});
			parents = new HashMap<>();
			edges = new HashMap<>();
			pre = new ArrayList<>();
			post = new ArrayList<>();
			
			edges.put(TREE, new HashSet<>());
			edges.put(BACK, new HashSet<>());
			edges.put(FOR_CROSS, new HashSet<>());
			
			dfs(entry);
		}

		void dfs(BasicBlock b) {
			colours.put(b, GREY);
			pre.add(b);
			
			for(FlowEdge<BasicBlock> sE : cfg.getEdges(b))  {
				BasicBlock s = sE.dst;
				edges.get(colours.getNonNull(s)).add(sE);
				if(colours.getNonNull(s).intValue() == WHITE) {
					parents.put(s, b);
					dfs(s);
				}
			}
			
			post.add(b);
			colours.put(b, BLACK);
		}
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
		return false;
	}
	
	
	
	class PhiRes {
		final Local target;
		final PhiExpression phi;
		final HeaderStatement src;
		final Local l;
		final Type type;
		
		PhiRes(Local target, PhiExpression phi, HeaderStatement src, Local l, Type type) {
			this.target = target;
			this.phi = phi;
			this.src = src;
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