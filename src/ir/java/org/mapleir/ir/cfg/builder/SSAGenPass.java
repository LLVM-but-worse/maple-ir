package org.mapleir.ir.cfg.builder;

import java.util.*;
import java.util.Map.Entry;

import org.mapleir.ir.analysis.SimpleDfs;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExceptionExpression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.ConditionalJumpStatement;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.SwitchStatement;
import org.mapleir.ir.code.stmt.ThrowStatement;
import org.mapleir.ir.code.stmt.UnconditionalJumpStatement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsHandler;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.cfg.edge.ConditionalJumpEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdges;
import org.mapleir.stdlib.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.cfg.edge.SwitchEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.cfg.edge.UnconditionalJumpEdge;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.collections.graph.flow.TarjanDominanceComputor;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.stdlib.ir.transform.Liveness;
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LabelNode;

public class SSAGenPass extends ControlFlowGraphBuilder.BuilderPass {
	
	public static boolean DO_SPLIT = true;
	public static boolean ULTRANAIVE = false;
	public static boolean SKIP_SIMPLE_COPY_SPLIT = true;
	public static boolean PRUNE_EDGES = true;
	public static int SPLIT_BLOCK_COUNT = 0;

	private final Map<Local, Integer> counters;
	private final Map<Local, Stack<Integer>> stacks;
	// TODO: use arrays.
	private final Map<BasicBlock, Integer> insertion;
	private final Map<BasicBlock, Integer> process;
	private final NullPermeableHashMap<BasicBlock, Set<Local>> splits;
	private final Map<BasicBlock, Integer> preorder;
	private final Set<BasicBlock> handlers;
	
	private TarjanDominanceComputor<BasicBlock> doms;
	private Liveness<BasicBlock> liveness;
	private int splitCount;
	
	public SSAGenPass(ControlFlowGraphBuilder builder) {
		super(builder);

		counters = new HashMap<>();
		stacks = new HashMap<>();
		
		insertion = new HashMap<>();
		process = new HashMap<>();
		
		splits = new NullPermeableHashMap<>(new SetCreator<>());
		preorder = new HashMap<>();
		handlers = new HashSet<>();
	}
	
	private void splitRanges() {
		// produce cleaner cfg
		List<BasicBlock> order = new ArrayList<>(builder.graph.vertices());
		
		for(ExceptionRange<BasicBlock> er : builder.graph.getRanges()) {
			BasicBlock h = er.getHandler();
			handlers.add(h);
			
			Set<Local> ls = new HashSet<>(liveness.in(h));
			for(BasicBlock b : er.get()) {
				splits.getNonNull(b).addAll(ls);
				
				//				boolean outside = false;
				//
				//				for(FlowEdge<BasicBlock> e : builder.graph.getReverseEdges(b)) {
				//					BasicBlock p = e.src;
				//					if(!er.containsVertex(p)) {
				//						outside = true;
				//					}
				//				}
				
				//				if(outside) {
				//					BasicBlock n = splitBlock(b, 0);
				//					order.add(order.indexOf(b), n);
				//				}
			}
		}
		
		for(Entry<BasicBlock, Set<Local>> e : splits.entrySet()) {
			BasicBlock b = e.getKey();
			Set<Local> ls = e.getValue();
			
			ArrayList<Statement> stmtsCopy = new ArrayList<>(b);
			int i = 0;
			boolean checkSplit = false;
			for (int i1 = 0; i1 < stmtsCopy.size(); i1++) {
				Statement stmt = stmtsCopy.get(i1);
				//				System.out.println("@" + i1 + "@" + i + " " + stmt);
				if (b.size() == i)
					throw new IllegalStateException("s");
				
				if ((!SKIP_SIMPLE_COPY_SPLIT || checkSplit) && stmt.getOpcode() == Opcode.LOCAL_STORE) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					VarExpression v = copy.getVariable();
					if (ls.contains(v.getLocal()) || (ULTRANAIVE && !v.getLocal().isStack())) {
						BasicBlock n = splitBlock(b, i);
						//						System.out.println("Split " + b.getId() + " into " + b.getId() + " and " + n.getId());
						order.add(order.indexOf(b), n);
						i = 0;
						checkSplit = false;
					}
				} else {
					// do not split if we have only seen simple or synthetic copies (catch copy is synthetic)
					if (stmt instanceof CopyVarStatement) {
						CopyVarStatement copy = (CopyVarStatement) stmt;
						int opc = copy.getExpression().getOpcode();
						if (!copy.isSynthetic() && opc != Opcode.LOCAL_LOAD && opc != Opcode.CATCH) {
							checkSplit = true;
						}
					} else {
						checkSplit = true;
					}
				}
				i++;
			}
		}
		
		builder.naturaliseGraph(order);
		
		SimpleDfs<BasicBlock> dfs = new SimpleDfs<>(builder.graph, builder.graph.getEntries().iterator().next(), true, false);
		int po = 0;
		for(BasicBlock b : dfs.preorder) {
			insertion.put(b, 0);
			process.put(b, 0);
			preorder.put(b, po++);
		}
	}
	
	private BasicBlock splitBlock(BasicBlock b, int to) {
		/* eg. split the block as follows:
		 * 
		 *  NAME:
		 *    stmt1
		 *    stmt2
		 *    stmt3
		 *    stmt4
		 *    stmt5
		 *    jump L1, L2
		 *   [jump edge to L1]
		 *   [jump edge to L2]
		 *   [exception edges]
		 * 
		 * split at 3, create a new block (incoming 
		 * immediate), transfer instruction from 0
		 * to index into new block, create immediate
		 * edge to old block, clone exception edges,
		 * redirect pred edges.
		 * 
		 * 1/9/16: we also need to modify the last
		 *         statement of the pred blocks to
		 *         point to NAME'.
		 * 
		 *  NAME':
		 *    stmt1
		 *    stmt2
		 *    stmt3
		 *   [immediate to NAME]
		 *  NAME:
		 *    stmt4
		 *    stmt5
		 *    jump L1, L2
		 *   [jump edge to L1]
		 *   [jump edge to L2]
		 *   [exception edges]
		 */
		SPLIT_BLOCK_COUNT++;
		
		// split block
		ControlFlowGraph cfg = builder.graph;
		BasicBlock newBlock = new BasicBlock(cfg, splitCount++, new LabelNode());
		b.transferUp(newBlock, to);
		cfg.addVertex(newBlock);
		
		// redo ranges
		for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			if (er.containsVertex(b))
				er.addVertexBefore(b, newBlock);
		}

		// redirect b preds into newBlock and remove them.
		Set<FlowEdge<BasicBlock>> oldEdges = new HashSet<>(cfg.getReverseEdges(b));
		for (FlowEdge<BasicBlock> e : oldEdges) {
			BasicBlock p = e.src;
			FlowEdge<BasicBlock> c;
			if (e instanceof TryCatchEdge) { // b is ehandler
				TryCatchEdge<BasicBlock> tce = (TryCatchEdge<BasicBlock>) e;
				if (tce.erange.getHandler() != newBlock || tce.dst != tce.erange.getHandler()) {
					tce.erange.setHandler(newBlock);
					cfg.addEdge(tce.src, tce.clone(tce.src, null));
					cfg.removeEdge(tce.src, tce);
				}
			} else {
				c = e.clone(p, newBlock);
				cfg.addEdge(p, c);
				cfg.removeEdge(p, e);
			}
			
			// Fix flow instruction targets
			if (!p.isEmpty()) {
				Statement last = p.get(p.size() - 1);
				int op = last.getOpcode();
				if (e instanceof ConditionalJumpEdge) {
					if (op != Opcode.COND_JUMP)
						throw new IllegalArgumentException("wrong flow instruction");
					ConditionalJumpStatement j = (ConditionalJumpStatement) last;
//					assertTarget(last, j.getTrueSuccessor(), b);
					if (j.getTrueSuccessor() == b)
						j.setTrueSuccessor(newBlock);
				} else if (e instanceof UnconditionalJumpEdge) {
					if (op != Opcode.UNCOND_JUMP)
						throw new IllegalArgumentException("wrong flow instruction");
					UnconditionalJumpStatement j = (UnconditionalJumpStatement) last;
					assertTarget(j, j.getTarget(), b);
					j.setTarget(newBlock);
				} else if (e instanceof SwitchEdge) {
					if (op != Opcode.SWITCH_JUMP)
						throw new IllegalArgumentException("wrong flow instruction.");
					SwitchStatement s = (SwitchStatement) last;
					for (Entry<Integer, BasicBlock> en : s.getTargets().entrySet()) {
						BasicBlock t = en.getValue();
						if (t == b) {
							en.setValue(newBlock);
						}
					}
				}
			}
		}

		
		if (!checkCloneHandler(newBlock)) {
			System.err.println(cfg);
			System.err.println(newBlock.getId());
			System.err.println(b.getId());
			throw new IllegalStateException("the new block should always need a handler..?");
		}
			
		// clone exception edges
		for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
			if (e.getType() == FlowEdges.TRYCATCH) {
				TryCatchEdge<BasicBlock> c = ((TryCatchEdge<BasicBlock>) e).clone(newBlock, null); // second param is discarded (?)
				cfg.addEdge(newBlock, c);
			}
		}
		if (!checkCloneHandler(b)) {
			// remove unnecessary handler edges if this block is now all simple copies, synth copies, or simple jumps.
			for (FlowEdge<BasicBlock> e : new HashSet<>(cfg.getEdges(b))) {
				if (e instanceof TryCatchEdge)
					cfg.removeEdge(b, e);
			}
		}
		
		// create immediate to newBlock
		cfg.addEdge(newBlock, new ImmediateEdge<>(newBlock, b));
		
		// update assigns
		Set<Local> assignedLocals = new HashSet<>();
		for (Statement stmt : b)
			if (stmt.getOpcode() == Opcode.LOCAL_STORE)
				assignedLocals.add(((CopyVarStatement) stmt).getVariable().getLocal());
		for (Statement stmt : newBlock) {
			if (stmt.getOpcode() == Opcode.LOCAL_STORE) {
				Local copyLocal = ((CopyVarStatement) stmt).getVariable().getLocal();
				Set<BasicBlock> set = builder.assigns.get(copyLocal);
				set.add(newBlock);
				if (!assignedLocals.contains(copyLocal))
					set.remove(b);
			}
		}
		
		return newBlock;
	}
	
	private boolean checkCloneHandler(BasicBlock b) {
		if (!SKIP_SIMPLE_COPY_SPLIT || !PRUNE_EDGES)
			return true;
		if (b.isEmpty())
			throw new IllegalArgumentException("empty block after split?");
		// backwards iteration is faster
		for (ListIterator<Statement> it = b.listIterator(b.size()); it.hasPrevious(); ) {
			Statement stmt = it.previous();
			if (stmt instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				int opc = copy.getExpression().getOpcode();
				if (!copy.isSynthetic() && opc != Opcode.LOCAL_LOAD && opc != Opcode.CATCH)
					return true;
			} else if (stmt.canChangeFlow()) {
				if (stmt instanceof ThrowStatement)
					return true;
				// no need to check child exprs as no complex subexprs can occur before propagation.
			} else {
				return true;
			}
		}
		return false;
	}
	
	private void assertTarget(Statement s, BasicBlock t, BasicBlock b) {
		if(t != b) {
			System.err.println(builder.graph);
			System.err.println(s.getBlock());
			throw new IllegalStateException(s + ", "+ t.getId() + " != " + b.getId());
		}
	}
	
	private void insertPhis() {
		int i = 0;
		for(Local l : builder.locals) {
			i++;
			
			LinkedList<BasicBlock> queue = new LinkedList<>();
			for(BasicBlock b : builder.assigns.get(l)) {
				process.put(b, i);
				queue.add(b);
			}
			while(!queue.isEmpty()) {
				insertPhis(queue.poll(), l, i, queue);
			}
		}
	}
	
	private void insertPhis(BasicBlock b, Local l, int i, LinkedList<BasicBlock> queue) {
		if(b == null || b == builder.head) {
			return; // exit
		}

		Local newl = builder.graph.getLocals().get(l.getIndex(), 0, l.isStack());
		
		for(BasicBlock x : doms.iteratedFrontier(b)) {
			if(insertion.get(x) < i) {
				// pruned SSA
				if(liveness.in(x).contains(l)) {
					/* Scenarios: (assuming live in)
					 *   multiple lvar into any block      -> add phi
					 *   
					 *   svar0 into handler from exception 
					 *    and non exception edge and       -> add phi
					 */
					if(handlers.contains(x) && l.isStack()) {
						boolean naturalFlow = false;
						for(FlowEdge<BasicBlock> e : builder.graph.getReverseEdges(x)) {
							if(e.getType() != FlowEdges.TRYCATCH) {
								naturalFlow = true;
								break;
							}
						}
						if(naturalFlow) {
							CopyVarStatement catcher = null;
							
							for(Statement stmt : x) {
								if(stmt.getOpcode() == Opcode.LOCAL_STORE) {
									CopyVarStatement copy = (CopyVarStatement) stmt;
									Expression e = copy.getExpression();
									if(e.getOpcode() == Opcode.CATCH) {
										catcher = copy;
										break;
									}
								}
							}
							
							if(catcher == null) {
								throw new IllegalStateException(x.getId());
							}
							
							if(catcher.getVariable().getLocal() != l) {
								continue;
							}
							
							Map<BasicBlock, Expression> vls = new HashMap<>();
							for(FlowEdge<BasicBlock> fe : builder.graph.getReverseEdges(x)) {
								vls.put(fe.src, new VarExpression(newl, null));
							}
							vls.put(x, catcher.getExpression().copy());
							catcher.delete();
							
							PhiExpression phi = new PhiExceptionExpression(vls);
							CopyPhiStatement assign = new CopyPhiStatement(new VarExpression(l, null), phi);
							
							x.add(0, assign);
						}
					} else if(builder.graph.getReverseEdges(x).size() > 1) {
						Map<BasicBlock, Expression> vls = new HashMap<>();
						for(FlowEdge<BasicBlock> fe : builder.graph.getReverseEdges(x)) {
							vls.put(fe.src, new VarExpression(newl, null));
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
	
	private void rename() {
		for(Local l : builder.locals) {
			counters.put(l, 0);
			stacks.put(l, new Stack<>());
		}
		
		Set<BasicBlock> vis = new HashSet<>();
		for(BasicBlock e : builder.graph.getEntries()) {
			search(e, vis);
		}
	}
	
	private void search(BasicBlock b, Set<BasicBlock> vis) {
		if(vis.contains(b)) {
			return;
		}
		vis.add(b);
		
		renamePhis(b);
		renameNonPhis(b);
		
		List<FlowEdge<BasicBlock>> succs = new ArrayList<>();
		for(FlowEdge<BasicBlock> succE : builder.graph.getEdges(b)) {
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
				l = builder.graph.getLocals().get(l.getIndex(), l.isStack());
				stacks.get(l).pop();
			}
		}
	}
	
	private void renamePhis(BasicBlock b) {
		for(Statement stmt : b) {
			if(stmt.getOpcode() == Opcode.PHI_STORE) {
				CopyPhiStatement copy = (CopyPhiStatement) stmt;
				VarExpression var = copy.getVariable();
				Local lhs = var.getLocal();
				VersionedLocal vl = _gen_name(lhs.getIndex(), lhs.isStack());
				var.setLocal(vl);;
				builder.defs.put(vl, copy);
			}
		}
	}
	
	private void renameNonPhis(BasicBlock b) {
		for(Statement stmt : b) {
			int opcode = stmt.getOpcode();
			
			if(opcode != Opcode.PHI_STORE) {
				for(Statement s : stmt.enumerate()) {
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
				builder.defs.put(vl, copy);
			}
		}
	}
	
	private void fixPhiArgs(BasicBlock b, BasicBlock succ) {
		for(Statement stmt : succ) {
			if(stmt.getOpcode() == Opcode.PHI_STORE) {
				CopyPhiStatement copy = (CopyPhiStatement) stmt;
				PhiExpression phi = copy.getExpression();
				Expression e = phi.getArgument(b);
				
//				if(e == null) {
//					Local l = copy.getVariable().getLocal();
//					Local newl = builder.graph.getLocals().get(l.getIndex(), 0, l.isStack());
//					phi.setArgument(b, e = new VarExpression(newl, null));
//				}
				
				if(e.getOpcode() == Opcode.LOCAL_LOAD) {
					Local l = ((VarExpression) e).getLocal();
					l = _top(stmt, l.getIndex(), l.isStack());
					try {
						AbstractCopyStatement varDef = builder.defs.get(l);
						if(copy.getType() == null) {
							Type t = TypeUtils.asSimpleType(varDef.getType());
							copy.getVariable().setType(t);
							phi.setType(t);
						} else {
							Type t = varDef.getType();
							Type oldT = copy.getType();
							// TODO: common supertypes
							if(oldT.getSize() != TypeUtils.asSimpleType(t).getSize()) {
								throw new IllegalStateException(l + " " + copy + " " + t + " " + copy.getType());
							}
						}
						VarExpression var = new VarExpression(l, varDef.getType());
						phi.setArgument(b, var);
					} catch (IllegalStateException eg) {
						System.err.println(builder.graph);
						System.err.println(succ.getId() + ": " + phi.getId() + ". " + phi);
						throw eg;
					}
				}
			} else {
				break;
			}
		}
	}
	
	private VersionedLocal _gen_name(int index, boolean isStack) {
		LocalsHandler handler = builder.graph.getLocals();
		Local l = handler.get(index, isStack);
		int subscript = counters.get(l);
		stacks.get(l).push(subscript);
		counters.put(l, subscript+1);
		return handler.get(index, subscript, isStack);
	}
	
	private VersionedLocal _top(Statement root, int index, boolean isStack) {
		LocalsHandler handler = builder.graph.getLocals();
		Local l = handler.get(index, isStack);
		Stack<Integer> stack = stacks.get(l);
		if(stack == null) {
			System.err.println(builder.graph);
			System.err.println(stacks);
			throw new NullPointerException(root.toString() + ", " +  l.toString());
		} else if(stack.isEmpty()) {
			System.err.println(builder.graph);
			System.err.println(stacks);
			throw new IllegalStateException(root.toString() + ", " +  l.toString());
		}
		int subscript = stack.peek();
		return handler.get(index, subscript, isStack);
	}
	
	/* private void fixFinally() {
		// fix finally blocks that have handler ranges to themselves
		for(ExceptionRange<BasicBlock> er : builder.graph.getRanges()) {
			BasicBlock h = er.getHandler();
			
			// debug czech
//			for(FlowEdge<BasicBlock> e : builder.graph.getReverseEdges(h)) {
//				if(e.getType() != FlowEdges.TRYCATCH) {
//					System.out.println(builder.graph);
//					throw new RuntimeException(h.getId() + " : " + e.toString());
//				}
//			}
			
			for (FlowEdge<BasicBlock> e : new HashSet<>(builder.graph.getEdges(h))) {
				if (e instanceof TryCatchEdge && e.dst == h && e.src == h) {
					// this needs to be fixed. we will insert an empty block with a goto back to the handler
					BasicBlock newBlock = new BasicBlock(builder.graph, splitCount++, new LabelNode());
					newBlock.add(new ThrowStatement(new CaughtExceptionExpression("null")));

					ExceptionRange<BasicBlock> newEr2 = new ExceptionRange<>(er.getNode());
					newEr2.addVertex(newBlock);
					newEr2.setHandler(h);
					builder.graph.addVertex(newBlock);
					builder.graph.addEdge(newBlock, new TryCatchEdge<>(newBlock, newEr2));
					builder.graph.addRange(newEr2);

					builder.graph.removeEdge(h, e);
					for(ExceptionRange<BasicBlock> er2 : builder.graph.getRanges())
						if (er2.getHandler() == h)
							er2.removeVertex(h);

					ExceptionRange<BasicBlock> newEr = new ExceptionRange<>(er.getNode());
					newEr.addVertex(h);
					newEr.setHandler(newBlock);
					builder.graph.addEdge(h, new TryCatchEdge<>(h, newEr));
					builder.graph.addRange(newEr);

				}
			}
		}
	}
	private void cleanHandlerPhis() {
		for(ExceptionRange<BasicBlock> er : builder.graph.getRanges()) {
			BasicBlock h = er.getHandler();
			
			for(Statement stmt : h) {
				if(stmt.getOpcode() != Opcode.PHI_STORE) {
					break;
				}
				
				CopyPhiStatement cps = (CopyPhiStatement) stmt;
				PhiExpression phi = cps.getExpression();
				
				NullPermeableHashMap<Local, Set<BasicBlock>> conf = new NullPermeableHashMap<>(new SetCreator<>());
				Set<Local> wl = new HashSet<>();
				
				for(Entry<BasicBlock, Expression> e : phi.getArguments().entrySet()) {
					BasicBlock b = e.getKey();
					Expression expr = e.getValue();
					if(expr.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpression v = (VarExpression) expr;
						Local l = v.getLocal();
						Set<BasicBlock> set = conf.getNonNull(l);
						if(set.size() >= 1) {
							wl.add(l);
						}
						set.add(b);
					} else {
						throw new UnsupportedOperationException(String.valueOf(expr));
					}
				}
				
				for(Local l : wl) {
					// should be fairly small, size >= 2
					Set<BasicBlock> cand = conf.get(l);
					// FIXME: is this right? or do we need a dominance test.
					List<BasicBlock> pre = new ArrayList<>(cand);
					Collections.sort(pre, new Comparator<BasicBlock>() {
						@Override
						public int compare(BasicBlock o1, BasicBlock o2) {
							return Integer.compare(preorder.get(o1), preorder.get(o2));
						}
					});
					
					for(int i=1; i < cand.size(); i++) {
						BasicBlock b = pre.get(i);
						phi.removeArgument(b);
					}
				}
			}
		}
	} */

	@Override
	public void run() {
		splitCount = builder.graph.size() + 1;
		builder.head = GraphUtils.connectHead(builder.graph);

		List<BasicBlock> order = new ArrayList<>(builder.graph.vertices());
		order.remove(builder.head);
		order.add(0, builder.head);
		builder.naturaliseGraph(order);
		
//		BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
//		DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, builder.graph);
//		writer.removeAll().add(new ControlFlowGraphDecorator()).setName("headed").export();

		
		SSABlockLivenessAnalyser liveness = new SSABlockLivenessAnalyser(builder.graph);
		liveness.compute();
		this.liveness = liveness;
		
		splitRanges();
		// TODO: update instead of recomp?
		liveness = new SSABlockLivenessAnalyser(builder.graph);
		liveness.compute();
		this.liveness = liveness;
		
		doms = new TarjanDominanceComputor<>(builder.graph, new SimpleDfs<>(builder.graph, builder.head, true, false).preorder);
		insertPhis();
		rename();
		
		GraphUtils.disconnectHead(builder.graph, builder.head);
	}
}