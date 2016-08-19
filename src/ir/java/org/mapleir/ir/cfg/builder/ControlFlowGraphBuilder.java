package org.mapleir.ir.cfg.builder;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.*;
import org.mapleir.ir.code.stmt.ArrayStoreStatement;
import org.mapleir.ir.code.stmt.FieldStoreStatement;
import org.mapleir.ir.code.stmt.MonitorStatement;
import org.mapleir.ir.code.stmt.PopStatement;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsHandler;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.cfg.edge.DummyEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;
import org.mapleir.stdlib.collections.graph.flow.TarjanDominanceComputor;
import org.mapleir.stdlib.ir.StatementVisitor;
import org.mapleir.stdlib.ir.transform.Liveness;
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.mapleir.stdlib.ir.transform.ssa.SSALocalAccess;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public class ControlFlowGraphBuilder {
	
	private static final Set<Class<? extends Statement>> UNCOPYABLE = new HashSet<>();
	
	static {
		UNCOPYABLE.add(InvocationExpression.class);
		UNCOPYABLE.add(UninitialisedObjectExpression.class);
		UNCOPYABLE.add(InitialisedObjectExpression.class);
	}
	
	final MethodNode method;
	final ControlFlowGraph graph;

	BasicBlock exit;
	
	// ssa
	final Map<BasicBlock, Integer> insertion;
	final Map<BasicBlock, Integer> process;
	final Set<Local> locals;
	final NullPermeableHashMap<Local, Set<BasicBlock>> assigns;
	private final Map<Local, Integer> counters;
	private final Map<Local, Stack<Integer>> stacks;
	private final Map<VersionedLocal, AbstractCopyStatement> defs;
	private TarjanDominanceComputor<BasicBlock> doms;
	private Liveness<BasicBlock> liveness;
	
	private SSALocalAccess localAccess;
	
	private ControlFlowGraphBuilder(MethodNode method) {
		this.method = method;
		graph = new ControlFlowGraph(method, method.maxLocals);
		
		insertion = new HashMap<>();
		process = new HashMap<>();
		locals = new HashSet<>();
		assigns = new NullPermeableHashMap<>(new SetCreator<>());
		counters = new HashMap<>();
		stacks = new HashMap<>();
		defs = new HashMap<>();
	}
	
	/* static final Map<Class<?>, Integer> WEIGHTS = new HashMap<>();
	
	{
		WEIGHTS.put(ImmediateEdge.class, 10);
		WEIGHTS.put(ConditionalJumpEdge.class, 9);
		WEIGHTS.put(UnconditionalJumpEdge.class, 8);
		WEIGHTS.put(DefaultSwitchEdge.class, 7);
		WEIGHTS.put(SwitchEdge.class, 6);
		WEIGHTS.put(TryCatchEdge.class, 5);
	}  */
	
	class TarjanSCC <N extends FastGraphVertex> {
		final FlowGraph<N, FlowEdge<N>> graph;
		final Map<N, Integer> index;
		final Map<N, Integer> low;
		final LinkedList<N> stack;
		final List<List<N>> comps;
		int cur;
		
		TarjanSCC(FlowGraph<N, FlowEdge<N>> graph) {
			this.graph = graph;
			
			index = new HashMap<>();
			low = new HashMap<>();
			stack = new LinkedList<>();
			comps = new ArrayList<>();
		}

		/* List<FlowEdge<N>> weigh(Set<FlowEdge<N>> edges) {
			List<FlowEdge<N>> list = new ArrayList<>(edges);
			Collections.sort(list, new Comparator<FlowEdge<N>>() {
				@Override
				public int compare(FlowEdge<N> o1, FlowEdge<N> o2) {
					Class<?> c1 = o1.getClass();
					Class<?> c2 = o2.getClass();
					
					if(!WEIGHTS.containsKey(c1)) {
						throw new IllegalStateException(c1.toString());
					} else if(!WEIGHTS.containsKey(c2)) {
						throw new IllegalStateException(c2.toString());
					}
					
					int p1 = WEIGHTS.get(c1);
					int p2 = WEIGHTS.get(c2);
					
					// p2, p1 because higher weights are
					// more favoured.
					return Integer.compare(p2, p1);
				}
			});
			System.out.println("list: " + list);
			return list;
		} */
		
		void search(N n) {
			index.put(n, cur);
			low.put(n, cur);
			cur++;
			
			stack.push(n);
			
			for(FlowEdge<N> e : graph.getEdges(n)) {
				N s = e.dst;
				if(low.containsKey(s)) {
					low.put(n, Math.min(low.get(n), index.get(s)));
				} else {
					search(s);
					low.put(n, Math.min(low.get(n), low.get(s)));
				}
			}
			
			if(low.get(n) == index.get(n)) {
				List<N> c = new ArrayList<>();
				
				N w = null;
				do {
					w = stack.pop();
					c.add(0, w);
				} while (w != n);
				
				comps.add(0, bfs(n, c));
			}
		}
		
		List<N> bfs(N n, List<N> cand) {
			LinkedList<N> queue = new LinkedList<>();
			queue.add(n);
			
			List<N> bfs = new ArrayList<>();
			while(!queue.isEmpty()) {
				n = queue.pop();
				
				if(bfs.contains(n)) {
					continue;
				} else if(!cand.contains(n)) {
//					System.out.println(n.getId() + " jumps out of component: " + cand);
					continue;
				}
				
				bfs.add(n);
				
				for(FlowEdge<N> e : graph.getEdges(n)) {
					N s = e.dst;
					queue.addLast(s);
				}
			}
			
			
			return bfs;
		}
	}
	
	void findComponents() {
		TarjanSCC<BasicBlock> scc = new TarjanSCC<>(graph);
		for(BasicBlock b : graph.vertices()) {
			if(!scc.low.containsKey(b)) {
				scc.search(b);
			}
		}
		
		List<BasicBlock> order = new ArrayList<>();
		for(List<BasicBlock> c : scc.comps) {
			order.addAll(c);
		}
		
		naturaliseGraph(order);
	}
	
	void naturaliseGraph(List<BasicBlock> order) {
		// copy edge sets
		Map<BasicBlock, Set<FlowEdge<BasicBlock>>> edges = new HashMap<>();
		for(BasicBlock b : order) {
			edges.put(b, graph.getEdges(b));
		}
		// clean graph
		graph.clear();
		
		// rename and add blocks
		int label = 1;
		for(BasicBlock b : order) {
			b.setId(label++);
			graph.addVertex(b);
		}
		
		for(Entry<BasicBlock, Set<FlowEdge<BasicBlock>>> e : edges.entrySet()) {
			BasicBlock b = e.getKey();
			for(FlowEdge<BasicBlock> fe : e.getValue()) {
				graph.addEdge(b, fe);
			}
		}
	}
	
	void insertPhis(BasicBlock b, Local l, int i, LinkedList<BasicBlock> queue) {
		if(b == exit) {
			return; // exit
		}
				
		for(BasicBlock x : doms.iteratedFrontier(b)) {
			if(insertion.get(x) < i) {
				if(x.size() > 0 && graph.getReverseEdges(x).size() > 1) {
					// pruned SSA
					if(liveness.in(x).contains(l)) {
						Map<BasicBlock, Expression> vls = new HashMap<>();
						int subscript = 0;
						for(FlowEdge<BasicBlock> fe : graph.getReverseEdges(x)) {
							vls.put(fe.src, new VarExpression(graph.getLocals().get(l.getIndex(), subscript++, l.isStack()), null));
						}
						PhiExpression phi = new PhiExpression(vls);
						CopyPhiStatement assign = new CopyPhiStatement(new VarExpression(l, null), phi);
						
						x.add(0, assign);
					}
				}
				
				insertion.put(x, i);
				if(process.get(x) < i) {
					process.put(x, i);
					queue.add(x);
				}
			}
		}
	}
	
	void insertPhis() {
		int i = 0;
		for(Local l : locals) {
			i++;
			
			LinkedList<BasicBlock> queue = new LinkedList<>();
			for(BasicBlock b : assigns.get(l)) {
				process.put(b, i);
				queue.add(b);
			}
			while(!queue.isEmpty()) {
				insertPhis(queue.poll(), l, i, queue);
			}
		}
	}
	
	VersionedLocal _gen_name(int index, boolean isStack) {
		LocalsHandler handler = graph.getLocals();
		Local l = handler.get(index, isStack);
		int subscript = counters.get(l);
		stacks.get(l).push(subscript);
		counters.put(l, subscript+1);
		return handler.get(index, subscript, isStack);
	}
	
	VersionedLocal _top(Statement root, int index, boolean isStack) {
		LocalsHandler handler = graph.getLocals();
		Local l = handler.get(index, isStack);
		Stack<Integer> stack = stacks.get(l);
		if(stack == null) {
			System.err.println(graph);
			System.err.println(stacks);
			throw new NullPointerException(root.toString() + ", " +  l.toString());
		} else if(stack.isEmpty()) {
			System.err.println(graph);
			System.err.println(stacks);
			throw new IllegalStateException(root.toString() + ", " +  l.toString());
		}
		int subscript = stack.peek();
		return handler.get(index, subscript, isStack);
	}
	
	void renamePhis(BasicBlock b) {
		for(Statement stmt : b) {
			if(stmt.getOpcode() == Opcode.PHI_STORE) {
				CopyPhiStatement copy = (CopyPhiStatement) stmt;
				VarExpression var = copy.getVariable();
				Local lhs = var.getLocal();
				VersionedLocal vl = _gen_name(lhs.getIndex(), lhs.isStack());
				var.setLocal(vl);;
				defs.put(vl, copy);
			}
		}
	}
	
	void renameNonPhis(BasicBlock b) {
		for(Statement stmt : b) {
			int opcode = stmt.getOpcode();
			
			if(opcode != Opcode.PHI_STORE) {
				for(Statement s : stmt) {
					if(s.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpression var = (VarExpression) s;
						Local l = var.getLocal();
						var.setLocal(_top(s, l.getIndex(), l.isStack()));
					}
				}
			}
			
			if(opcode == Opcode.LOCAL_STORE) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				VarExpression var = copy.getVariable();
				Local lhs = var.getLocal();
				VersionedLocal vl = _gen_name(lhs.getIndex(), lhs.isStack());
				var.setLocal(vl);
				defs.put(vl, copy);
			}
		}
	}
	
	void fixPhiArgs(BasicBlock b, BasicBlock succ) {
		for(Statement stmt : succ) {
			if(stmt.getOpcode() == Opcode.PHI_STORE) {
				CopyPhiStatement copy = (CopyPhiStatement) stmt;
				PhiExpression phi = copy.getExpression();
				Expression e = phi.getArgument(b);
				if(e.getOpcode() == Opcode.LOCAL_LOAD) {
					Local l = (VersionedLocal) ((VarExpression) e).getLocal();
					l = _top(stmt, l.getIndex(), l.isStack());
					try {
						AbstractCopyStatement varDef = defs.get(l);
						if(copy.getType() == null) {
							Type t = TypeUtils.asSimpleType(varDef.getType());
							copy.getVariable().setType(t);
							phi.setType(t);
						} else {
							Type t = varDef.getType();
							Type oldT = copy.getType();
							// TODO: common supertypes
							if(!oldT.equals(TypeUtils.asSimpleType(t))) {
								throw new IllegalStateException(l + " " + copy + " " + t + " " + copy.getType());
							}
						}
						VarExpression var = new VarExpression(l, varDef.getType());
						phi.setArgument(b, var);
					} catch (IllegalStateException eg) {
						System.err.println(graph);
						System.err.println(succ.getId() + ": " + phi.getId() + ". " + phi);
						throw eg;
					}
				} else {
					throw new UnsupportedOperationException(String.valueOf(e));
				}
			}
		}
	}
	
	void search(BasicBlock b, Set<BasicBlock> vis) {
		if(vis.contains(b)) {
			return;
		}
		vis.add(b);
		
		renamePhis(b);
		renameNonPhis(b);
		
		List<FlowEdge<BasicBlock>> succs = new ArrayList<>();
		for(FlowEdge<BasicBlock> succE : graph.getEdges(b)) {
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
			fixPhiArgs(b, succ);
		}
		
		for(FlowEdge<BasicBlock> succE : succs) {
			BasicBlock succ = succE.dst;
			search(succ, vis);
		}
		
		for (Statement s : b) {
			if (s.getOpcode() == Opcode.PHI_STORE || s.getOpcode() == Opcode.LOCAL_STORE) {
				AbstractCopyStatement cvs = (AbstractCopyStatement) s;
				Local l = cvs.getVariable().getLocal();
				l = graph.getLocals().get(l.getIndex(), l.isStack());
				stacks.get(l).pop();
			}
		}
	}
	
	void rename() {
		for(Local l : locals) {
			counters.put(l, 0);
			stacks.put(l, new Stack<>());
		}
		
		Set<BasicBlock> vis = new HashSet<>();
		for(BasicBlock e : graph.getEntries()) {
			search(e, vis);
		}
	}

	void ssa() {
		// technically this shouldn't be an ssa analyser.
		SSABlockLivenessAnalyser liveness = new SSABlockLivenessAnalyser(graph);
		liveness.compute();
		this.liveness = liveness;
		
		doms = new TarjanDominanceComputor<>(graph);
		insertPhis();
		rename();
	}
	
	class FeedbackStatementVisitor extends StatementVisitor {
		
		private boolean change = false;
		
		public FeedbackStatementVisitor(Statement root) {
			super(root);
		}
		
		private Set<Statement> _enumerate(Statement stmt) {
			Set<Statement> set = new HashSet<>();
			set.add(stmt);
			
			if(stmt.getOpcode() == Opcode.PHI_STORE) {
				CopyPhiStatement phi = (CopyPhiStatement) stmt;
				for(Expression e : phi.getExpression().getArguments().values()) {
					set.addAll(_enumerate(e));
				}
			} else {
				for(Statement s : stmt) {
					set.addAll(_enumerate(s));
				}
			}
			
			return set;
		}
		
		private Iterable<Statement> enumerate(Statement stmt) {
			return _enumerate(stmt);
		}

		private Set<Statement> findReachable(Statement from, Statement to) {
			Set<Statement> res = new HashSet<>();
			BasicBlock f = from.getBlock();
			BasicBlock t = to.getBlock();
			
			int end = f == t ? f.indexOf(to) : f.size();
			for(int i=f.indexOf(from); i < end; i++) {
				res.add(f.get(i));
			}
			
			if(f != t) {
				for(BasicBlock r : graph.wanderAllTrails(f, t)) {
					res.addAll(r);
				}
			}
			
			return res;
		}
		
		private Set<Statement> findReachable(Statement stmt) {
			Set<Statement> res = new HashSet<>();
			BasicBlock b = stmt.getBlock();
			for(int i=b.indexOf(stmt); i < b.size(); i++) {
				res.add(b.get(i));
			}
			
			for(BasicBlock r : graph.wanderAllTrails(b, exit)) {
				res.addAll(r);
			}
			
			return res;
		}
		
		private boolean cleanEquivalentPhis() {
			boolean change = false;
			
			for(BasicBlock b : graph.vertices()) {
				List<CopyPhiStatement> phis = new ArrayList<>();
				for(Statement stmt : b) {
					if(stmt.getOpcode() == Opcode.PHI_STORE) {
						phis.add((CopyPhiStatement) stmt);
					} else {
						break;
					}
				}
								
				if(phis.size() > 1) {
					NullPermeableHashMap<CopyPhiStatement, Set<CopyPhiStatement>> equiv = new NullPermeableHashMap<>(new SetCreator<>());
					for(CopyPhiStatement cps : phis) {
						if(equiv.values().contains(cps)) {
							continue;
						}
						PhiExpression phi = cps.getExpression();
						for(CopyPhiStatement cps2 : phis) {
							if(cps != cps2) {
								if(equiv.keySet().contains(cps2)) {
									continue;
								}
								PhiExpression phi2 = cps2.getExpression();
								if(phi.equivalent(phi2)) {
									equiv.getNonNull(cps).add(cps2);
								}
							}
						}
					}

					for(Entry<CopyPhiStatement, Set<CopyPhiStatement>> e : equiv.entrySet()) {						
						// key should be earliest
						// remove vals from code and replace use of val vars with key var
						
						// choose which phi to keep.
						// favour lvars.
						
						Set<CopyPhiStatement> all = new HashSet<>();
						all.add(e.getKey());
						all.addAll(e.getValue());
						
						CopyPhiStatement keepPhi = null;
						
						for(CopyPhiStatement cps : all) {
							if(!cps.getVariable().getLocal().isStack()) {
								keepPhi = cps;
								break;
							}
						}
						
						if(keepPhi == null) {
							keepPhi = e.getKey();
						}
						
						Set<CopyPhiStatement> useless = new HashSet<>(all);
						useless.remove(keepPhi);
						
						VersionedLocal phiLocal = (VersionedLocal) keepPhi.getVariable().getLocal();
						
						Set<VersionedLocal> toReplace = new HashSet<>();
						for(CopyPhiStatement def : useless) {
							VersionedLocal local = (VersionedLocal) def.getVariable().getLocal();
							toReplace.add(local);
							killed(def);
							b.remove(def);
						}
						
						// replace uses
						for(Statement reachable : findReachable(keepPhi)) {
							for(Statement s : reachable) {
								if(s instanceof VarExpression) {
									VarExpression var = (VarExpression) s;
									VersionedLocal l = (VersionedLocal) var.getLocal();
									if(toReplace.contains(l)) {
										reuseLocal(phiLocal);
										unuseLocal(l);
										var.setLocal(phiLocal);
									}
								}
							}
						}
						
						for(CopyPhiStatement def : useless) {
							Local local = def.getVariable().getLocal();
							localAccess.useCount.remove(local);
							localAccess.defs.remove(local);
						}
						change = true;
					}
				}
			}
			return change;
		}
		
		private boolean cleanDead() {
			boolean changed = false;
			Iterator<Entry<VersionedLocal, AtomicInteger>> it = localAccess.useCount.entrySet().iterator();
			while(it.hasNext()) {
				Entry<VersionedLocal, AtomicInteger> e = it.next();
				if(e.getValue().get() == 0)  {
					AbstractCopyStatement def = localAccess.defs.get(e.getKey());
					if(!def.isSynthetic()) {
						if(!fineBladeDefinition(def, it)) {
							killed(def);
							changed = true;
						}
					}
				}
			}
			return changed;
		}
		
		private void killed(Statement stmt) {
			for(Statement s : enumerate(stmt)) {
				if(s.getOpcode() == Opcode.LOCAL_LOAD) {
					unuseLocal(((VarExpression) s).getLocal());
				}
			}
		}
		
		private void copied(Statement stmt) {
			for(Statement s : enumerate(stmt)) {
				if(s.getOpcode() == Opcode.LOCAL_LOAD) {
					reuseLocal(((VarExpression) s).getLocal());
				}
			}
		}
		
		private boolean fineBladeDefinition(AbstractCopyStatement def, Iterator<?> it) {
			it.remove();
			Expression rhs = def.getExpression();
			BasicBlock b = def.getBlock();
			if(isUncopyable(rhs)) {
				PopStatement pop = new PopStatement(rhs);
				b.set(b.indexOf(def), pop);
				return true;
			} else {
				// easy remove
				b.remove(def);
				Local local = def.getVariable().getLocal();
				localAccess.useCount.remove(local);
				return false;
			}
		}
		
		private void scalpelDefinition(AbstractCopyStatement def) {
			def.getBlock().remove(def);
			Local local = def.getVariable().getLocal();
			localAccess.useCount.remove(local);
			localAccess.defs.remove(local);
		}
		
		private int uses(Local l) {
			if(localAccess.useCount.containsKey(l)) {
				return localAccess.useCount.get(l).get();
			} else {
				throw new IllegalStateException("Local " + l + " not in useCount map. Def: " + localAccess.defs.get(l));
			}
		}

		private void _xuselocal(Local l, boolean re) {
			if(localAccess.useCount.containsKey(l)) {
				if(re) {
					localAccess.useCount.get(l).incrementAndGet();
				} else {
					localAccess.useCount.get(l).decrementAndGet();
				}
			} else {
				throw new IllegalStateException("Local " + l + " not in useCount map. Def: " + localAccess.defs.get(l));
			}
		}
		
		private void unuseLocal(Local l) {
			_xuselocal(l, false);
		}
		
		private void reuseLocal(Local l) {
			_xuselocal(l, true);
		}
		
		private Statement handleConstant(AbstractCopyStatement def, VarExpression use, ConstantExpression rhs) {
			// x = 7;
			// use(x)
			//         goes to
			// x = 7
			// use(7)
			
			// localCount -= 1;
			unuseLocal(use.getLocal());
			return rhs.copy();
		}

		private Statement handleVar(AbstractCopyStatement def, VarExpression use, VarExpression rhs) {
			Local x = use.getLocal();
			Local y = rhs.getLocal();
			if(x == y) {
				return null;
			}
						
			// x = y
			// use(x)
			//         goes to
			// x = y
			// use(y)
			
			// rhsCount += 1;
			// useCount -= 1;
			reuseLocal(y);
			unuseLocal(x);
			return rhs.copy();
		}

		private Statement handleComplex(AbstractCopyStatement def, VarExpression use) {
			if(!canTransferToUse(root, use, def)) {
				return null;
			}

			
			// this can be propagated
			Expression propagatee = def.getExpression();
			if(isUncopyable(propagatee)) {
				// say we have
				// 
				// void test() {
				//    x = func();
				//    use(x);
				//    use(x);
				// }
				//
				// int func() {
				//    print("blowing up reactor core " + (++core));
				//    return core;
				// }
				// 
				// if we lazily propagated the rhs (func()) into both uses
				// it would blow up two reactor cores instead of the one
				// that it currently is set to destroy. this is why uncop-
				// yable statements (in reality these are expressions) ne-
				// ed to have only  one definition for them to be propaga-
				// table. at the moment the only possible expressions that
				// have these side effects are invoke type ones.
				if(uses(use.getLocal()) == 1) {
					// since there is only 1 use of this expression, we
					// will copy the propagatee/rhs to the use and then
					// remove the definition. this means that the only
					// change to uses is the variable that was being
					// propagated. i.e.
					
					// svar0_1 = lvar0_0.invoke(lvar1_0, lvar3_0.m)
					// use(svar0_1)
					//  will become
					// use(lvar0_0.invoke(lvar1_0, lvar3_0.m))
					
					// here the only thing we need to change is
					// the useCount of svar0_1 to 0. (1 - 1)
					unuseLocal(use.getLocal());
					scalpelDefinition(def);
					return propagatee;
				}
			} else {
				// these statements here can be copied as many times
				// as required without causing multiple catastrophic
				// reactor meltdowns.
				if(propagatee instanceof ArrayLoadExpression) {
					// TODO: CSE instead of this cheap assumption.
					if(uses(use.getLocal()) == 1) {
						unuseLocal(use.getLocal());
						scalpelDefinition(def);
						return propagatee;
					}
				} else {
					// x = ((y * 2) + (9 / lvar0_0.g))
					// use(x)
					//       goes to
					// x = ((y * 2) + (9 / lvar0_0.g))
					// use(((y * 2) + (9 / lvar0_0.g)))
					Local local = use.getLocal();
					unuseLocal(local);
					copied(propagatee);
					if(uses(local) == 0) {
						// if we just killed the local
						killed(def);
						scalpelDefinition(def);
					}
					return propagatee;
				}
			}
			return null;
		}
		
		private Statement findSubstitution(Statement root, AbstractCopyStatement def, VarExpression use) {
			// n.b. if this is called improperly (i.e. unpropagatable def),
			//      then the code may be dirtied/ruined.
			Local local = use.getLocal();
			if(!local.isStack()) {
				if(root.getOpcode() == Opcode.LOCAL_STORE || root.getOpcode() == Opcode.PHI_STORE) {
					AbstractCopyStatement cp = (AbstractCopyStatement) root;
					if(cp.getVariable().getLocal().isStack()) {
						return use;
					}
				}
			}
			Expression rhs = def.getExpression();
			if(rhs instanceof ConstantExpression) {
				return handleConstant(def, use, (ConstantExpression) rhs);
			} else if(rhs instanceof VarExpression) {
				return handleVar(def, use, (VarExpression) rhs);
			} else if (!(rhs instanceof CaughtExceptionExpression || rhs instanceof PhiExpression)) {
				return handleComplex(def, use);
			}
			return use;
		}

		private Statement visitVar(VarExpression var) {
			AbstractCopyStatement def = localAccess.defs.get(var.getLocal());
			return findSubstitution(root, def, var);
		}
		
		private Statement visitPhi(PhiExpression phi) {
			for(BasicBlock s : phi.getSources()) {
				Expression e = phi.getArgument(s);
				
				if(e.getOpcode() == Opcode.LOCAL_LOAD) {
					VarExpression use = (VarExpression) e;
					
					AbstractCopyStatement def = localAccess.defs.get((use).getLocal());
					Expression rhs = def.getExpression();
					int opcode = rhs.getOpcode();

					VarExpression cand = null;
					
					if(opcode == Opcode.LOCAL_LOAD) {
						VarExpression v = (VarExpression) rhs;
						Local l = v.getLocal();
						Local deflhs = def.getVariable().getLocal();
						// we only want to propagate if;
						//  l.isStack() == deflhs.isStack();
						// or:
						//  use.isStack() && !defrhs.isStack();
						if((l.isStack() == deflhs.isStack()) || (!l.isStack() && deflhs.isStack())) {
							cand = (VarExpression) e;
						}
					} else if(opcode == Opcode.CONST_LOAD) {
						cand = (VarExpression) e;
					}
					
					if(cand != null) {
						Statement sub = findSubstitution(phi, def, (VarExpression) e);
						if(sub != null && sub != e) {
							phi.setArgument(s, (Expression) sub);
							change = true;
						}
					}
				}
			}
			return phi;
		}

		@Override
		public Statement visit(Statement stmt) {
			if(stmt.getOpcode() == Opcode.LOCAL_LOAD) {
				return choose(visitVar((VarExpression) stmt), stmt);
			} else if(stmt.getOpcode() == Opcode.PHI) {
				return choose(visitPhi((PhiExpression) stmt), stmt);
			}
			return stmt;
		}
		
		private Statement choose(Statement e, Statement def) {
			if(e != null) {
				return e;
			} else {
				return def;
			}
		}
		
		private boolean isUncopyable(Statement stmt) {
			for(Statement s : enumerate(stmt)) {
				if(UNCOPYABLE.contains(s.getClass())) {
					return true;
				}
			}
			return false;
		}
		
		private boolean canTransferToUse(Statement use, Statement tail, AbstractCopyStatement def) {
			Local local = def.getVariable().getLocal();
			Expression rhs = def.getExpression();

			Set<String> fieldsUsed = new HashSet<>();
			AtomicBoolean invoke = new AtomicBoolean();
			AtomicBoolean array = new AtomicBoolean();
			
			{
				if(rhs instanceof FieldLoadExpression) {
					fieldsUsed.add(((FieldLoadExpression) rhs).getName() + "." + ((FieldLoadExpression) rhs).getDesc());
				} else if(rhs instanceof InvocationExpression || rhs instanceof InitialisedObjectExpression) {
					invoke.set(true);
				} else if(rhs instanceof ArrayLoadExpression) {
					array.set(true);
				} else if(rhs instanceof ConstantExpression) {
					return true;
				}
			}
			
			new StatementVisitor(rhs) {
				@Override
				public Statement visit(Statement stmt) {
					if(stmt instanceof FieldLoadExpression) {
						fieldsUsed.add(((FieldLoadExpression) stmt).getName() + "." + ((FieldLoadExpression) stmt).getDesc());
					} else if(stmt instanceof InvocationExpression || stmt instanceof InitialisedObjectExpression) {
						invoke.set(true);
					} else if(stmt instanceof ArrayLoadExpression) {
						array.set(true);
					}
					return stmt;
				}
			}.visit();
			
			Set<Statement> path = findReachable(def, use);
			path.remove(def);
			path.add(use);
			
			if(def.toString().equals("lvar2_2 = lvar2_2 + 1;")) {
				System.out.println("REACHES: " + def + " to " + use);
				for(Statement s : path)  {
					System.out.println("  " + s);
				}
			}
			
			boolean canPropagate = true;
			
			for(Statement stmt : path) {
				if(stmt != use) {
					if(stmt instanceof FieldStoreStatement) {
						if(invoke.get()) {
							canPropagate = false;
							break;
						} else if(fieldsUsed.size() > 0) {
							FieldStoreStatement store = (FieldStoreStatement) stmt;
							String key = store.getName() + "." + store.getDesc();
							if(fieldsUsed.contains(key)) {
								canPropagate = false;
								break;
							}
						}
					} else if(stmt instanceof ArrayStoreStatement) {
						if(invoke.get() || array.get()) {
							canPropagate = false;
							break;
						}
					} else if(stmt instanceof MonitorStatement) {
						if(invoke.get()) {
							canPropagate = false;
							break;
						}
					} else if(stmt instanceof InitialisedObjectExpression || stmt instanceof InvocationExpression) {
						if(invoke.get() || fieldsUsed.size() > 0 || array.get()) {
							canPropagate = false;
							break;
						}
					}
				}
				
				if(!canPropagate) {
					return false;
				}
				
				AtomicBoolean canPropagate2 = new AtomicBoolean(canPropagate);
				if(invoke.get() || array.get() || !fieldsUsed.isEmpty()) {
					new StatementVisitor(stmt) {
						@Override
						public Statement visit(Statement s) {
							if(root == use && (s instanceof VarExpression && ((VarExpression) s).getLocal() == local)) {
								_break();
							} else {
								if((s instanceof InvocationExpression || s instanceof InitialisedObjectExpression) || (invoke.get() && (s instanceof FieldStoreStatement || s instanceof ArrayStoreStatement))) {
									canPropagate2.set(false);
									_break();
								}
							}
							return s;
						}
					}.visit();
					canPropagate = canPropagate2.get();
					
					if(!canPropagate) {
						return false;
					}
				}
			}
			
			if(canPropagate) {
				return true;
			} else {
				return false;
			}
		}
		
		public boolean changed() {
			return change;
		}
		
		// Selects a statement to be processed.
		@Override
		public void reset(Statement stmt) {
			super.reset(stmt);
			change = false;
		}
		
		@Override
		protected void visited(Statement stmt, Statement node, int addr, Statement vis) {
			if(vis != node) {
				stmt.overwrite(vis, addr);
				change = true;
			}
			verify();
		}
		
		private void verify() {
			SSALocalAccess fresh = new SSALocalAccess(graph);
			
			Set<VersionedLocal> keySet = new HashSet<>(fresh.useCount.keySet());
			keySet.addAll(localAccess.useCount.keySet());
			List<VersionedLocal> sortedKeys = new ArrayList<>(keySet);
			Collections.sort(sortedKeys);
			
			String message = null;
			for(VersionedLocal e : sortedKeys) {
				AtomicInteger i1 = fresh.useCount.get(e);
				AtomicInteger i2 = localAccess.useCount.get(e);
				if(i1 == null) {
					message = "Real no contain: " + e + ", other: " + i2.get();
				} else if(i2 == null) {
					message = "Current no contain: " + e + ", other: " + i1.get();
				} else if(i1.get() != i2.get()) {
					message = "Mismatch: " + e + " " + i1.get() + ":" + i2.get();
				}
			}
			
			if(message != null) {
				throw new RuntimeException(message + "\n" + graph.toString());
			}
		}
	}
	
	boolean attemptPop(PopStatement pop) {
		Expression expr = pop.getExpression();
		if(expr instanceof VarExpression) {
			VarExpression var = (VarExpression) expr;
			localAccess.useCount.get(var.getLocal()).decrementAndGet();
			pop.getBlock().remove(pop);
			return true;
		} else if(expr instanceof ConstantExpression) {
			pop.getBlock().remove(pop);
			return true;
		}
		return false;
	}
	
	boolean attempt(Statement stmt, FeedbackStatementVisitor visitor) {
		if(stmt instanceof PopStatement) {
			boolean at = attemptPop((PopStatement)stmt);
			if(at) {
				return true;
			}
		}

		visitor.reset(stmt);
		visitor.visit();
		return visitor.changed();
	}
	
	int aggregate_initialisers() {
		int changes = 0;
		
		for(BasicBlock b : graph.vertices()) {
			for(Statement stmt : new ArrayList<>(b)) {
				if (stmt instanceof PopStatement) {
					PopStatement pop = (PopStatement) stmt;
					Expression expr = pop.getExpression();
					if (expr instanceof InvocationExpression) {
						InvocationExpression invoke = (InvocationExpression) expr;
						if (invoke.getOpcode() == Opcodes.INVOKESPECIAL && invoke.getName().equals("<init>")) {
							Expression inst = invoke.getInstanceExpression();
							if (inst instanceof VarExpression) {
								VarExpression var = (VarExpression) inst;
								VersionedLocal local = (VersionedLocal) var.getLocal();

								AbstractCopyStatement def = localAccess.defs.get(local);

								Expression rhs = def.getExpression();
								if (rhs instanceof UninitialisedObjectExpression) {
									// replace pop(x.<init>()) with x := new Klass();
									// remove x := new Klass;
									
									// here we are assuming that the new object
									// can't be used until it is initialised.
									UninitialisedObjectExpression obj = (UninitialisedObjectExpression) rhs;
									Expression[] args = invoke.getParameterArguments();
									Expression[] newArgs = Arrays.copyOf(args, args.length);
									InitialisedObjectExpression newExpr = new InitialisedObjectExpression(obj.getType(), invoke.getOwner(), invoke.getDesc(), newArgs);
									// remove the old def
									// add a copy statement before the pop (x = newExpr)
									// remove the pop statement
									
									b.remove(def);
									
									CopyVarStatement newCvs = new CopyVarStatement(var, newExpr);
									localAccess.defs.put(local, newCvs);
									localAccess.useCount.get(local).decrementAndGet();
									
									int index = b.indexOf(pop);
									b.add(index, newCvs);
									b.remove(pop);
									
									changes++;
								}
							} else if(inst instanceof UninitialisedObjectExpression) {
								// replace pop(new Klass.<init>(args)) with pop(new Klass(args))
								UninitialisedObjectExpression obj = (UninitialisedObjectExpression) inst;
								Expression[] args = invoke.getParameterArguments();
								Expression[] newArgs = Arrays.copyOf(args, args.length);
								InitialisedObjectExpression newExpr = new InitialisedObjectExpression(obj.getType(), invoke.getOwner(), invoke.getDesc(), newArgs);
								// replace pop contents
								// no changes to defs or uses
								
								pop.setExpression(newExpr);
								
								changes++;
							} else {
								System.err.println(b);
								System.err.println("Stmt: " + stmt.getId() + ". " + stmt);
								System.err.println("Inst: " + inst);
								throw new RuntimeException("interesting1 " + inst.getClass());
							}
						}
					}
				}
			}
		}
		
		return changes;
	}
	
	int opt() {
		// TODO: optimise
		
		FeedbackStatementVisitor visitor = new FeedbackStatementVisitor(null);
		int changes = 0;
		for(BasicBlock b : graph.vertices()) {
			for(Statement stmt : new ArrayList<>(b)) {
				if(!b.contains(stmt)) {
					continue;
				}
				if(attempt(stmt, visitor)) {
					changes++;
				}
				if(visitor.cleanDead()) {
					changes++;
				}
				if(visitor.cleanEquivalentPhis()) {
					changes++;
				}
			}
		}
		
		changes += aggregate_initialisers();
		
		return changes;
	}
	
	ControlFlowGraphBuilder reduce() {
		while(mergeImmediates() > 0);
//		findComponents();
		naturaliseGraph(new ArrayList<>(graph.vertices()));

		System.out.println(graph);
		
		exit = new BasicBlock(graph, graph.size() * 2, null);
		for(BasicBlock b : graph.vertices()) {
			if(graph.getEdges(b).size() == 0) {
				graph.addEdge(b, new DummyEdge<>(b, exit));
			}
			
			insertion.put(b, 0);
			process.put(b, 0);
		}
		
		
		ssa();
		
		localAccess = new SSALocalAccess(graph);
		while(opt() > 0);
		
		graph.removeVertex(exit);
		
		return this;
	}
	
	public static abstract class BuilderPass {
		protected final ControlFlowGraphBuilder builder;
		
		public BuilderPass(ControlFlowGraphBuilder builder) {
			this.builder = builder;
		}
		
		public abstract void run();
	}
	
	private BuilderPass[] resolvePasses() {
		return new BuilderPass[] {
				new GenerationPass(this)
		};
	}
	
	public static ControlFlowGraph build(MethodNode method) {
		ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(method);
		try {
			for(BuilderPass p : builder.resolvePasses()) {
				p.run();
			}
			return builder.reduce().graph;
		} catch(RuntimeException e) {
//			List<BasicBlock> blocks = new ArrayList<>(builder.graph.vertices());
//			Collections.sort(blocks, new Comparator<BasicBlock>() {
//				@Override
//				public int compare(BasicBlock o1, BasicBlock o2) {
//					int i1 = builder.insns.indexOf(o1.getLabelNode());
//					int i2 = builder.insns.indexOf(o2.getLabelNode());
//					return Integer.compare(i1, i2);
//				}
//			});
//			builder.naturaliseGraph(blocks);
			System.err.println(builder.graph);
			throw e;
		}
	}
}