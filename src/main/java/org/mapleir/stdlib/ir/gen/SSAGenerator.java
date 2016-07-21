package org.mapleir.stdlib.ir.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.DummyEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.flow.TarjanDominanceComputor;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.StatementVisitor;
import org.mapleir.stdlib.ir.expr.Expression;
import org.mapleir.stdlib.ir.expr.PhiExpression;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.header.BlockHeaderStatement;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.locals.LocalsHandler;
import org.mapleir.stdlib.ir.locals.VersionedLocal;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.impl.LivenessAnalyser;
import org.objectweb.asm.Type;

public class SSAGenerator {

	// if only CodeBody extended Statement ;(
	final StatementVisitor collectionVisitor = new StatementVisitor(null) {
		@Override
		public Statement visit(Statement stmt) {
			visitCollect0(stmt, stmt);
			return stmt;
		}
	};

	void visitCollect0(Statement root, Statement s) {
		if(s instanceof CopyVarStatement) {
			Local l = ((CopyVarStatement) s).getVariable().getLocal();
			locals.add(l);
			
			BasicBlock b = translation.get(root);
			if(b == null) {
				throw new IllegalStateException(root.toString());
			}
			assigns.getNonNull(l).add(b);
		} else if(s instanceof VarExpression) {
			locals.add(((VarExpression) s).getLocal());
		}
	}
	
	void collectLocals(Statement s) {
		visitCollect0(s, s);
		collectionVisitor.reset(s);
		collectionVisitor.visit();
	}
	
	final LocalsHandler handler;
	final CodeBody body;
	final Map<BasicBlock, BlockHeaderStatement> headers;
	
	final ControlFlowGraph cfg;
	final TarjanDominanceComputor<BasicBlock> doms;
	final BasicBlock exit;
	
	final Map<Statement, BasicBlock> translation;
	final Set<Local> locals;
	final NullPermeableHashMap<Local, Set<BasicBlock>> assigns;
	
	final LinkedList<BasicBlock> queue;
	
	final Map<BasicBlock, Integer> insertion;
	final Map<BasicBlock, Integer> process;
	
	final Map<Local, Integer> counters;
	final Map<Local, Stack<Integer>> stacks;
	
	final Map<VersionedLocal, CopyVarStatement> defs;
	
	final LivenessAnalyser liveness;
	
	public SSAGenerator(CodeBody body, ControlFlowGraph cfg, Map<BasicBlock, BlockHeaderStatement> headers) {
		this.body = body;
		this.cfg = cfg;
		this.headers = headers;
		
		handler = body.getLocals();
		
		translation = new HashMap<>();
		
		locals = new HashSet<>();
		queue = new LinkedList<>();
		assigns = new NullPermeableHashMap<>(new SetCreator<>());
		insertion = new HashMap<>();
		process = new HashMap<>();
		exit = new BasicBlock(cfg, "fakeexit", null);
		
		counters = new HashMap<>();
		stacks = new HashMap<>();
		
		liveness = new LivenessAnalyser(StatementGraphBuilder.create(cfg));
		defs = new HashMap<>();
		
		init();
		doms = new TarjanDominanceComputor<>(cfg);
	}
	
	public void run() {
		compute_phis();
		rename();
		de_init();
	}
	
	void de_init() {
		cfg.removeVertex(exit);
	}
	
	void init() {
		cfg.addVertex(exit);
		for(BasicBlock b : cfg.vertices()) {
			// connect dummy exit
			if(cfg.getEdges(b).size() == 0) {
				cfg.addEdge(b, new DummyEdge<>(b, exit));
			}
			// map translation
			for(Statement s : b.getStatements()) { 
				translation.put(s, b);
			}

			// init worklist
			insertion.put(b, 0);
			process.put(b, 0);
		}
		
		for(Statement stmt : body)  {
			// collect locals
			collectLocals(stmt);
		}
	}
	
	void rename() {
		for(Local l : locals) {
			counters.put(l, 0);
			stacks.put(l, new Stack<>());
		}
		
		Set<BasicBlock> vis = new HashSet<>();
		for(BasicBlock e : cfg.getEntries()) {
			search(e, vis);
		}
	}
	
	void search(BasicBlock b, Set<BasicBlock> vis) {
		if(vis.contains(b)) {
			return;
		}
		vis.add(b);
		
		for(Statement s : b.getStatements())  {
			if(s instanceof CopyVarStatement) {
				CopyVarStatement cvs = ((CopyVarStatement) s);
				if(cvs.getExpression() instanceof PhiExpression) {
					VarExpression var = cvs.getVariable();
					Local lhs = var.getLocal();
					VersionedLocal vl = _gen_name(lhs.getIndex(), lhs.isStack());
					var.setLocal(vl);
					defs.put(vl, cvs);
				}
			}
		}
		
		for(Statement s : b.getStatements())  {
			// doesn't even phi arguments.
			new StatementVisitor(s) {
				@Override
				public Statement visit(Statement stmt) {
					if(stmt instanceof VarExpression) {
						VarExpression var = (VarExpression) stmt;
						Local l = var.getLocal();
						var.setLocal(_top(s, l.getIndex(), l.isStack()));
					}
					return stmt;
				}
			}.visit();
			
			if(s instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) s;
				if(!(copy.getExpression() instanceof PhiExpression)) {
					VarExpression var = copy.getVariable();
					Local lhs = var.getLocal();
					VersionedLocal vl = _gen_name(lhs.getIndex(), lhs.isStack());
					var.setLocal(vl);
					defs.put(vl, copy);
				}
			}
		}
		
		List<FlowEdge<BasicBlock>> succs = new ArrayList<>();
		for(FlowEdge<BasicBlock> succE : cfg.getEdges(b)) {
			succs.add(succE);
		}
		
		Collections.sort(succs, new Comparator<FlowEdge<BasicBlock>>() {
			@Override
			public int compare(FlowEdge<BasicBlock> o1, FlowEdge<BasicBlock> o2) {
				return o1.dst.compareTo(o2.dst);
			}
		});		
		
		for(FlowEdge<BasicBlock> succE : succs) {
			BasicBlock succ = succE.dst;
			HeaderStatement header = headers.get(b);
			// int j = pred(b, succ);
			// System.out.printf("block=%s, succ=%s, j=%d.%n", b.getId(), succ.getId(), j);
			
			for(Statement s : succ.getStatements())  {
				if(s instanceof CopyVarStatement) {
					CopyVarStatement cvs = ((CopyVarStatement) s);
					if(cvs.getExpression() instanceof PhiExpression) {
						PhiExpression phi = (PhiExpression) cvs.getExpression();
						Expression e = phi.getLocal(header);
						if(e instanceof VarExpression) {
							Local l = (VersionedLocal) ((VarExpression) e).getLocal();
							l = _top(s, l.getIndex(), l.isStack());
							try {
								CopyVarStatement varDef = defs.get(l);
								if(cvs.getType() == null) {
									Type t = TypeUtils.asSimpleType(varDef.getType());
									cvs.getVariable().setType(t);
									phi.setType(t);
								} else {
									Type t = varDef.getType();
									Type oldT = cvs.getType();
									// TODO: common supertypes
									if(!oldT.equals(TypeUtils.asSimpleType(t))) {
										throw new IllegalStateException(l + " " + cvs + " " + t + " " + cvs.getType());
									}
								}
								VarExpression var = new VarExpression(l, varDef.getType());
								phi.setLocal(header, var);
							} catch (IllegalStateException eg) {
								System.err.println(body);
								System.err.println(succ.getId() + ": " + phi.getId() + ". " + phi);
								throw eg;
							}
						} else {
							throw new UnsupportedOperationException(String.valueOf(e));
						}
					}
				}
			}
		}
		
		for(FlowEdge<BasicBlock> succE : succs) {
			BasicBlock succ = succE.dst;
			search(succ, vis);
		}
		
		for (Statement s : b.getStatements()) {
			if (s instanceof CopyVarStatement) {
				CopyVarStatement cvs = (CopyVarStatement) s;
				Local l = cvs.getVariable().getLocal();
				l = handler.get(l.getIndex(), l.isStack());
				stacks.get(l).pop();
			}
		}
	}
	
	VersionedLocal _gen_name(int index, boolean isStack) {
		Local l = handler.get(index, isStack);
		int subscript = counters.get(l);
		stacks.get(l).push(subscript);
		counters.put(l, subscript+1);
		return handler.get(index, subscript, isStack);
	}
	
	VersionedLocal _top(Statement root, int index, boolean isStack) {
		Local l = handler.get(index, isStack);
		Stack<Integer> stack = stacks.get(l);
		if(stack == null) {
			System.err.println(body);
			System.err.println(stacks);
			throw new NullPointerException(root.toString() + ", " +  l.toString());
		} else if(stack.isEmpty()) {
			System.err.println(body);
			System.err.println(stacks);
			throw new IllegalStateException(root.toString() + ", " +  l.toString());
		}
		int subscript = stack.peek();
		return handler.get(index, subscript, isStack);
	}
	
	int pred(BasicBlock b, BasicBlock s) {
		int j = 0;
		for(FlowEdge<BasicBlock> pE : cfg.getReverseEdges(s)) {
			BasicBlock p = pE.src;
			if(p == b) {
				return j;
			} else {
				j++;
			}
		}
		throw new IllegalStateException(b.getId() + " /-> " + s);
	}
	
	void compute_phis() {
		int localCount = 0;
		for(Local local : new HashSet<>(locals)) {
			localCount++;
			for(BasicBlock b : assigns.get(local)) {
				process.put(b, localCount);
				queue.add(b);
			}
			while(!queue.isEmpty()) {
				process(localCount, local, queue.poll());
			}
		}
	}
	
	void process(int localCount, Local l, BasicBlock s) {
		if(s == exit) {
			return;
		}
		
		for(BasicBlock x : doms.iteratedFrontier(s)) {
			if(insertion.get(x) < localCount) {

				List<Statement> stmts = x.getStatements();
				int count = cfg.getReverseEdges(x).size();
				if(stmts.size() > 0 && count > 1) {
					Statement first = null;
					for(Statement stmt : stmts) {
						if(stmt instanceof CopyVarStatement) {
							if(((CopyVarStatement) stmt).getExpression() instanceof PhiExpression) {
								continue;
							}
						}
						
						first = stmt;
						break;
					}
					// pruned SSA
					if(liveness.in(first).get(l)) {
						Map<HeaderStatement, Expression> vls = new HashMap<>();
						int subscript = 0;
						for(FlowEdge<BasicBlock> fe : cfg.getReverseEdges(x)) {
							BasicBlock pred = fe.src;
							HeaderStatement header = headers.get(pred);
							vls.put(header, new VarExpression(handler.get(l.getIndex(), subscript++, l.isStack()), null));
						}
						PhiExpression phi = new PhiExpression(vls);
						CopyVarStatement assign = new CopyVarStatement(new VarExpression(l, null), phi);
						
						body.add(body.indexOf(stmts.get(0)), assign);
						stmts.add(1, assign);
					}
				}
				
				insertion.put(x, localCount);
				if(process.get(x) < localCount) {
					process.put(x, localCount);
					queue.add(x);
				}
			}
		}
	}
}