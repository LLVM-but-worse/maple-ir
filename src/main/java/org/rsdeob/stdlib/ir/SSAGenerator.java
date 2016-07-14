package org.rsdeob.stdlib.ir;

import java.util.*;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;
import org.rsdeob.stdlib.collections.graph.flow.TarjanDominanceComputor;
import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.PhiExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.locals.LocalsHandler;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.stat.SyntheticStatement;

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
		} else if(s instanceof SyntheticStatement) {
			visitCollect0(s, ((SyntheticStatement) s).getStatement());
		}
	}
	
	void collectLocals(Statement s) {
		visitCollect0(s, s);
		collectionVisitor.reset(s);
		collectionVisitor.visit();
	}
	
	final LocalsHandler handler;
	final CodeBody body;
	
	final ControlFlowGraph cfg;
	final TarjanDominanceComputor<BasicBlock> doms;
	final BasicBlock exit;
	
	final Map<Statement, BasicBlock> translation;
	final Set<Local> locals;
	final NullPermeableHashMap<Local, Set<BasicBlock>> assigns;
	
	final LinkedList<BasicBlock> queue;
	
	final Map<BasicBlock, Integer> insertion;
	final Map<BasicBlock, Integer> process;
	
	final Map<Local, Integer> count;
	final Map<Local, Stack<Integer>> stacks;
	
	public SSAGenerator(CodeBody body, ControlFlowGraph cfg) {
		this.body = body;
		this.cfg = cfg;
		handler = body.getLocals();
		
		translation = new HashMap<>();
		
		locals = new HashSet<>();
		queue = new LinkedList<>();
		assigns = new NullPermeableHashMap<>(new SetCreator<>());
		insertion = new HashMap<>();
		process = new HashMap<>();
		exit = new BasicBlock(cfg, "fakeexit", null);
		
		count = new HashMap<>();
		stacks = new HashMap<>();
		
		init();
		doms = new TarjanDominanceComputor<>(cfg);
	}
	
	public void run() {
		computePhis();
		rename();
	}
	
	void init() {
		cfg.addVertex(exit);
		for(BasicBlock b : cfg.vertices()) {
			// connect dummy exit
			if(cfg.getEdges(b).size() == 0) {
//				cfg.addEdge(b, new DummyEdge<>(b, exit));
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
			count.put(l, 0);
			stacks.put(l, new Stack<>());
		}
		
		for(BasicBlock e : cfg.getEntries()) {
			search(e);
		}
	}
	
	void search(BasicBlock b) {
		for(Statement s : b.getStatements()) {
			boolean isCopy = s instanceof CopyVarStatement;
			boolean synth = s instanceof SyntheticStatement;
			
			if(!synth && !(isCopy && ((CopyVarStatement) s).getExpression() instanceof PhiExpression)) {
				new StatementVisitor(s) {
					@Override
					public Statement visit(Statement stmt) {
						if(stmt instanceof VarExpression) {
							VarExpression v = (VarExpression) stmt;
							Local l = v.getLocal();
							if(!(l instanceof VersionedLocal)) {
								VersionedLocal newVar = handler.get(l.getIndex(), top(root, handler.get(l.getIndex(), l.isStack())), l.isStack());
								v.setLocal(newVar);
							}
						}
						return stmt;
					}
				}.visit();
			}
			
			if(synth || isCopy) {
				CopyVarStatement copy = null;
				if(synth) {
					SyntheticStatement syn = (SyntheticStatement) s;
					Statement s2 = syn.getStatement();
					if(s2 instanceof CopyVarStatement) {
						copy = (CopyVarStatement) s2;
						isCopy = true;
					}
				} else {
					copy = (CopyVarStatement) s;
				}
				
				if(isCopy) {
					Local lhs = copy.getVariable().getLocal();
					Local l = handler.get(lhs.getIndex(), lhs.isStack());
					int c = count.get(l);
					VersionedLocal newVar = handler.get(lhs.getIndex(), c, lhs.isStack());
					copy.setVariable(new VarExpression(newVar, copy.getVariable().getType()));
					stacks.get(l).push(c);
					count.put(l, c + 1);
				}
			}
		}
		
		for(FlowEdge<BasicBlock> succE : cfg.getEdges(b)) {
			BasicBlock succ = succE.dst;
			int j = pred(succ, b);
			
			for(Statement s : succ.getStatements()) {
				if(s instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) s;
					Expression expr = copy.getExpression();
					if(expr instanceof PhiExpression) {
						PhiExpression phi = (PhiExpression) expr;
						VersionedLocal l = phi.getLocal(j);
						int t = top(s, handler.get(l.getIndex(), l.isStack()));
						l = handler.get(l.getIndex(), t, l.isStack());
						phi.setLocal(j, l);
					}
				}
			}
		}
		
		for(BasicBlock c : doms.children(b)) {
			search(c);
		}
		
		for(Statement s : b.getStatements()) {
			CopyVarStatement copy = null;
			if(s instanceof CopyVarStatement) {
				copy = (CopyVarStatement) s;
			} else if(s instanceof SyntheticStatement) {
				if(((SyntheticStatement) s).getStatement() instanceof CopyVarStatement) {
					copy = (CopyVarStatement) ((SyntheticStatement) s).getStatement();
				}
			}
			if(copy != null) {
				Local l1 = copy.getVariable().getLocal();
				if(l1 instanceof VersionedLocal) {
					Local l = handler.get(l1.getIndex(), l1.isStack());
					stacks.get(l).pop();
				}
			}
		}
	}
	
	int top(Statement root, Local l) {
		Stack<Integer> stack = stacks.get(l);
		if(stack == null) {
			System.err.println(body);
			throw new NullPointerException(root.toString() + ", " +  l.toString());
		} else if(stack.isEmpty()) {
			System.err.println(body);
			System.err.println(stacks);
			throw new IllegalStateException(root.toString() + ", " +  l.toString());
		}
		return stack.peek();
	}
	
	int pred(BasicBlock b, BasicBlock s) {
		int j = 0;
		for(FlowEdge<BasicBlock> pE : cfg.getReverseEdges(b)) {
			BasicBlock p = pE.src;
			if(p == s) {
				return j;
			} else {
				j++;
			}
		}
		throw new IllegalStateException(b.getId() + " /-> " + s);
	}
	
	void computePhis() {
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
		
		System.out.println("frontier of " + s + " , " + doms.iteratedFronter(s));
		for(BasicBlock x : doms.iteratedFronter(s)) {
			if(insertion.get(x) < localCount) {

				List<Statement> stmts = x.getStatements();
				int count = cfg.getReverseEdges(x).size();
				if(stmts.size() > 0 && count > 1) {
					List<VersionedLocal> vls = new ArrayList<>();
					for(int i=0; i < count; i++) {
						vls.add(handler.get(l.getIndex(), i, l.isStack()));
					}
					PhiExpression phi = new PhiExpression(vls);
					VersionedLocal l2 = handler.get(l.getIndex(), count, l.isStack());
					CopyVarStatement assign = new CopyVarStatement(new VarExpression(l2, null), phi);
					
					body.add(assign, body.indexOf(stmts.get(0)));
					stmts.add(0, assign);
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