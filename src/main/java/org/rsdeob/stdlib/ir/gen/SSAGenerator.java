package org.rsdeob.stdlib.ir.gen;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.DummyEdge;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.SetMultimap;
import org.rsdeob.stdlib.collections.graph.flow.TarjanDominanceComputor;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementVisitor;
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
import org.rsdeob.stdlib.ir.transform.impl.LivenessAnalyser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class SSAGenerator {

	// if only CodeBody extended Statement ;(
	private final StatementVisitor collectionVisitor = new StatementVisitor(null) {
		@Override
		public Statement visit(Statement stmt) {
			visitCollect0(stmt, stmt);
			return stmt;
		}
	};

	private void visitCollect0(Statement root, Statement s) {
		if(s instanceof CopyVarStatement) {
			Local l = ((CopyVarStatement) s).getVariable().getLocal();
			locals.add(l);
			
			BasicBlock b = translation.get(root);
			if(b == null) {
				throw new IllegalStateException(root.toString());
			}
			assigns.put(l, b);
		} else if(s instanceof VarExpression) {
			locals.add(((VarExpression) s).getLocal());
		}
	}
	
	private void collectLocals(Statement s) {
		visitCollect0(s, s);
		collectionVisitor.reset(s);
		collectionVisitor.visit();
	}
	
	private final LocalsHandler handler;
	private final CodeBody body;
	private final Map<BasicBlock, BlockHeaderStatement> headers;
	
	private final ControlFlowGraph cfg;
	private final TarjanDominanceComputor<BasicBlock> doms;
	private final BasicBlock exit;
	
	private final Map<Statement, BasicBlock> translation;
	private final Set<Local> locals;
	private final SetMultimap<Local, BasicBlock> assigns;
	
	private final LinkedList<BasicBlock> queue;
	
	private final Map<BasicBlock, Integer> insertion;
	private final Map<BasicBlock, Integer> process;
	
	private final Map<Local, Integer> counters;
	private final Map<Local, Stack<Integer>> stacks;
	
	private	final LivenessAnalyser liveness;
	
	public SSAGenerator(CodeBody body, ControlFlowGraph cfg, Map<BasicBlock, BlockHeaderStatement> headers) {
		this.body = body;
		this.cfg = cfg;
		this.headers = headers;
		
		handler = body.getLocals();
		
		translation = new HashMap<>();
		
		locals = new HashSet<>();
		queue = new LinkedList<>();
		assigns = new SetMultimap<>();
		insertion = new HashMap<>();
		process = new HashMap<>();
		exit = new BasicBlock(cfg, "fakeexit", null);
		
		counters = new HashMap<>();
		stacks = new HashMap<>();
		
		liveness = new LivenessAnalyser(StatementGraphBuilder.create(cfg));
		
		init();
		doms = new TarjanDominanceComputor<>(cfg);
	}
	
	public void run() {
		computePhis();
		rename();
		de_init();
	}
	
	private void de_init() {
		cfg.removeVertex(exit);
	}
	
	private void init() {
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
	
	private void rename() {
		for(Local l : locals) {
			counters.put(l, 0);
			stacks.put(l, new Stack<>());
		}
		
		Set<BasicBlock> vis = new HashSet<>();
		for(BasicBlock e : cfg.getEntries()) {
			search(e, vis);
		}
	}
	
	private void search(BasicBlock b, Set<BasicBlock> vis) {
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
					var.setLocal(_gen_name(lhs.getIndex(), lhs.isStack()));
				}
			}
		}
		
		for(Statement s : b.getStatements())  {
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
					var.setLocal(_gen_name(lhs.getIndex(), lhs.isStack()));
				}
			}
		}
		
		List<FlowEdge<BasicBlock>> succs = new ArrayList<>(cfg.getEdges(b));
		Collections.sort(succs, (o1, o2) -> o1.dst.compareTo(o2.dst));
				
		// TODO: maybe sort succs
		
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
							VersionedLocal l = (VersionedLocal) ((VarExpression) e).getLocal();
							try {
								phi.setLocal(header, _top(s, l.getIndex(), l.isStack()));
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
		
		for(Statement s : b.getStatements())  {
			if(s instanceof CopyVarStatement) {
				CopyVarStatement cvs = (CopyVarStatement) s;
//				if(cvs.isSynthetic()) {
					Local l = cvs.getVariable().getLocal();
					l = handler.get(l.getIndex(), l.isStack());
					stacks.get(l).pop();
//				}
			}
		}
	}
	
	private VersionedLocal _gen_name(int index, boolean isStack) {
		Local l = handler.get(index, isStack);
		int subscript = counters.get(l);
		stacks.get(l).push(subscript);
		counters.put(l, subscript+1);
		return handler.get(index, subscript, isStack);
	}
	
	private VersionedLocal _top(Statement root, int index, boolean isStack) {
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
	
	private void computePhis() {
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
	
	private void process(int localCount, Local l, BasicBlock s) {
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
						stmts.add(0, assign);
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