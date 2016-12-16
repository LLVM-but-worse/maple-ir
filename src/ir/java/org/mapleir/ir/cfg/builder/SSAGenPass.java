package org.mapleir.ir.cfg.builder;

import java.util.*;
import java.util.Map.Entry;

import org.mapleir.ir.analysis.Liveness;
import org.mapleir.ir.analysis.SSABlockLivenessAnalyser;
import org.mapleir.ir.analysis.SimpleDfs;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ssaopt.ConstraintUtil;
import org.mapleir.ir.cfg.builder.ssaopt.LatestValue;
import org.mapleir.ir.cfg.edge.ConditionalJumpEdge;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.cfg.edge.ImmediateEdge;
import org.mapleir.ir.cfg.edge.SwitchEdge;
import org.mapleir.ir.cfg.edge.TryCatchEdge;
import org.mapleir.ir.cfg.edge.UnconditionalJumpEdge;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.ConstantExpression;
import org.mapleir.ir.code.expr.Expression;
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
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.GraphUtils;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.collections.graph.flow.TarjanDominanceComputor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LabelNode;

public class SSAGenPass extends ControlFlowGraphBuilder.BuilderPass {

	private static final boolean OPTIMISE = true;
	public static int SPLIT_BLOCK_COUNT = 0;

	private final Map<VersionedLocal, AbstractCopyStatement> defs;
	private final Map<VersionedLocal, Type> types;
	private final Map<Local, Integer> counters;
	private final Map<Local, Stack<Integer>> stacks;
	private final List<BasicBlock> order;
	// TODO: use arrays.
	private final Map<BasicBlock, Integer> insertion;
	private final Map<BasicBlock, Integer> process;
	private final Map<BasicBlock, Integer> preorder;
	private final Set<BasicBlock> handlers;
	
	private final Map<VersionedLocal, LatestValue> latest;
	private final Map<VersionedLocal, Set<VarExpression>> uses;
//	private final List<DeferredTranslation> deferred;
	private final Set<VersionedLocal> deferred;
	
	private TarjanDominanceComputor<BasicBlock> doms;
	private Liveness<BasicBlock> liveness;
	private int graphSize;
	
	public SSAGenPass(ControlFlowGraphBuilder builder) {
		super(builder);

		defs = new HashMap<>();
		types = new HashMap<>();
		
		counters = new HashMap<>();
		stacks = new HashMap<>();
		order = new ArrayList<>();
		
		insertion = new HashMap<>();
		process = new HashMap<>();
		
		preorder = new HashMap<>();
		handlers = new HashSet<>();
		
		latest = new HashMap<>();
		uses = new HashMap<>();
//		deferred = new ArrayList<>();
		deferred = new HashSet<>();
	}
	
	private void splitRanges() {
		// produce cleaner cfg
		List<BasicBlock> order = new ArrayList<>(builder.graph.vertices());
		NullPermeableHashMap<BasicBlock, Set<Local>> splits = new NullPermeableHashMap<>(new SetCreator<>());
		
		for(ExceptionRange<BasicBlock> er : builder.graph.getRanges()) {
			BasicBlock h = er.getHandler();
			handlers.add(h);
			
			Set<Local> ls = new HashSet<>(liveness.in(h));
			for(BasicBlock b : er.get()) {
				splits.getNonNull(b).addAll(ls);
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
				if (b.size() == i)
					throw new IllegalStateException("s");
				
				if (checkSplit && stmt.getOpcode() == Opcode.LOCAL_STORE) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					VarExpression v = copy.getVariable();
					if (ls.contains(v.getLocal())) {
						BasicBlock n = splitBlock(b, i);
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
		for(BasicBlock b : dfs.getPreOrder()) {
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
		BasicBlock newBlock = new BasicBlock(cfg, graphSize++, new LabelNode());
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
							
							/* Map<BasicBlock, Expression> vls = new HashMap<>();
							for(FlowEdge<BasicBlock> fe : builder.graph.getReverseEdges(x)) {
								vls.put(fe.src, new VarExpression(newl, null));
							}
							vls.put(x, catcher.getExpression().copy());
							catcher.delete();
							
							PhiExpression phi = new PhiExceptionExpression(vls);
							CopyPhiStatement assign = new CopyPhiStatement(new VarExpression(l, null), phi);
							
							x.add(0, assign); */
							
							throw new UnsupportedOperationException(builder.method.toString());
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
		
		for(BasicBlock b : order) {
			for(Statement s : b) {
				if(s.getOpcode() != Opcode.PHI_STORE) {
					break;
				}
				
				CopyPhiStatement cps = (CopyPhiStatement) s;
				
				for(Entry<BasicBlock, Expression> e : cps.getExpression().getArguments().entrySet()) {
					BasicBlock src = e.getKey();
					if(vis.contains(src))
						continue;
					
					VarExpression v = (VarExpression) e.getValue();
					Local l = v.getLocal();
					// what if the def is never reached?
					AbstractCopyStatement def = defs.get(l);
					v.setType(def.getType());
				}
			}
		}
	}
	
	private void search(BasicBlock b, Set<BasicBlock> vis) {
		if(vis.contains(b)) {
			return;
		}
		vis.add(b);
		
		searchImpl(b);
		
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
		
		unstackDefs(b);
	}
	
	private void fixPhiArgs(BasicBlock b, BasicBlock succ) {
		for(Statement stmt : succ) {
			if(stmt.getOpcode() == Opcode.PHI_STORE) {
				CopyPhiStatement copy = (CopyPhiStatement) stmt;
				PhiExpression phi = copy.getExpression();
				Expression e = phi.getArgument(b);
				
				if(e.getOpcode() == Opcode.LOCAL_LOAD) {
					VarExpression v = (VarExpression) e;
					
					translate(v, true, true);
					
					VersionedLocal ssaL = (VersionedLocal) v.getLocal();
					
					Type t = types.get(ssaL);
					copy.getVariable().setType(t);
					phi.setType(t);
				} else {
					throw new IllegalArgumentException(phi + ", " + e);
				}
			} else {
				/* No need to search the rest of the block
				 * after we have visited the phis as they
				 * precede all other statements.
				 */
				break;
			}
		}
	}
	
	private void unstackDefs(BasicBlock b) {
		for (Statement s : b) {
			if (s.getOpcode() == Opcode.PHI_STORE || s.getOpcode() == Opcode.LOCAL_STORE) {
				AbstractCopyStatement cvs = (AbstractCopyStatement) s;
				Local l = cvs.getVariable().getLocal();
				l = builder.graph.getLocals().get(l.getIndex(), l.isStack());
				stacks.get(l).pop();
			}
		}
	}
	
	private void searchImpl(BasicBlock b) {
		for(Statement stmt : b) {
			int opcode = stmt.getOpcode();
			
			if(opcode == Opcode.PHI_STORE) {
				/* We can rename these any time as these
				 * are visited before all other statements
				 * in a block (since they are always
				 * the starting statements of a block, if
				 * that block contains phi statements).
				 */
				CopyPhiStatement copy = (CopyPhiStatement) stmt;
				generate(copy);
			} else {
				/* Translates locals into their latest SSA
				 * versioned locals.
				 * 
				 * Do this before a LOCAL_STORE (x = ...)
				 * so that the target local isn't defined
				 * before the use so that copies in the
				 * form x = x; do not get mangled into
				 * x0 = x0 after SSA renaming.
				 * 
				 * We rename phi args later as the source
				 * local can originate from exotic blocks.
				 */
				translate(stmt, true, false);
			}
			
			if(opcode == Opcode.LOCAL_STORE) {
				/* Generate the target local after
				 * renaming the source uses. 
				 */
				CopyVarStatement copy = (CopyVarStatement) stmt;
				generate(copy);
			}
		}
	}
	
	private VersionedLocal generate(AbstractCopyStatement copy) {
		VarExpression v = copy.getVariable();
		Local oldLocal = v.getLocal();
		int index = oldLocal.getIndex();
		boolean isStack = oldLocal.isStack();
		
		LocalsPool handler = builder.graph.getLocals();
		Local l = handler.get(index, isStack);
		int subscript = counters.get(l);
		stacks.get(l).push(subscript);
		counters.put(l, subscript+1);
		
		VersionedLocal ssaL = handler.get(index, subscript, isStack);
		
		if(OPTIMISE) {
			makeValue(copy, ssaL);
		}
		
		v.setLocal(ssaL);
		defs.put(ssaL, copy);
		types.put(ssaL, copy.getExpression().getType());
		uses.put(ssaL, new HashSet<>());
		
		return ssaL;
	}
	
	private void makeValue(AbstractCopyStatement copy, VersionedLocal ssaL) {
		Expression e = copy.getExpression();
		if(e.getOpcode() == Opcode.LOCAL_LOAD) {
			if(copy.isSynthetic()) {
				LatestValue value = new LatestValue(builder.graph, LatestValue.TYPE_LOCAL, ssaL, ssaL);
				latest.put(ssaL, value);
			} else {
				/* i.e. x = y, where x and y are both variables.
				 * 
				 * It is expected that the local uses of the copy 
				 * (rhs) are visited before the target is.
				 */
				VarExpression rhs = (VarExpression) e;
				VersionedLocal rhsL = (VersionedLocal) rhs.getLocal();
				if(!latest.containsKey(ssaL)) {
					if(latest.containsKey(rhsL)) {
						LatestValue anc = latest.get(rhsL);
						
						/* To improve generated code, the following
						 * adjustments are made to simple mapping:
						 * 
						 * 1. If an ancestor is an svar, but the
						 *    current ssaL is an lvar, overwrite
						 *    the local with the lvar.*/
						
						Object sval = anc.getSuggestedValue();
//						if(anc.getType() == LatestValue.TYPE_LOCAL) {
//							VersionedLocal ancL = (VersionedLocal) anc.getSuggestedValue();
//							//if(!(ancL.isStack() && !ssaL.isStack())) {
//								/* We can use the ancestor.*/
//							//	sval = ancL;
//							//} else {
//								/* Reject the ancestor.*/
//							//	sval = ssaL;
//							//}
//							sval = ancL;
//						} else {
//							/* Use whatever the ancestor uses (non local).*/
//							sval = anc.getSuggestedValue();
//						}

						LatestValue value = new LatestValue(builder.graph, anc.getType(), rhsL, sval);
						value.importConstraints(anc);
						latest.put(ssaL, value);
					} else {
						throw new IllegalStateException("Non anc parent: " + ssaL + " = " + rhsL + " (def: " + defs.get(rhsL) + ")");
					}
				} else {
					throw new IllegalStateException("Revisit def " + ssaL + " ( = " + rhsL + ")");
				}	
			}
		} else {
			int opcode = e.getOpcode();
			
			LatestValue value;
			if(opcode == Opcode.CONST_LOAD) {
				ConstantExpression ce = (ConstantExpression) e;
				value = new LatestValue(builder.graph, LatestValue.TYPE_CONST, ce, ce);
			} else if((opcode & Opcode.CLASS_PHI) == Opcode.CLASS_PHI){
				value = new LatestValue(builder.graph, LatestValue.TYPE_PHI, ssaL, ssaL);
			} else {
				if(e.getOpcode() == Opcode.LOCAL_LOAD) {
					throw new RuntimeException(copy + "    " + e);
				}
				value = new LatestValue(builder.graph, LatestValue.TYPE_OTHER, e, e);
				value.makeConstraints(e);
			}
			
			latest.put(ssaL, value);
		}
	}
	
	private void translate(Statement stmt, boolean resolve, boolean isPhi) {
		/* At the point in the lifetime of
		 * the IR, we can only have var loads
		 * as child expressions of a phi.*/
		if(stmt.getOpcode() == Opcode.LOCAL_LOAD) {
			translateStmt((VarExpression) stmt, resolve, isPhi);
		} else if(!isPhi) {
			for(Statement c : stmt.getChildren()) {
				translate(c, resolve, false);
			}
		}
	}
	
	private void translateStmt(VarExpression var, boolean resolve, boolean isPhi) {
		/* Here we only remap local variable loads
		 * on the right hand side of a statement or
		 * expression. This means that if we are able
		 * to simply replace a local load which has
		 * a constant or deferred local value.
		 * 
		 * However, if the value of the local is
		 * a complex expression we need to check that
		 * we can propagate it before we do.
		 * 
		 * Since we will only replace a single
		 * local load in the original expression,
		 * only 1 variable is killed. However, there
		 * may be local load expressions in the
		 * propagated expression. To account for this,
		 * these local loads must be counted as new
		 * uses (except for when an expression is
		 * moved instead of copied to a use site).*/
		
		Local l = var.getLocal();
		VersionedLocal ssaL;
		
		if(resolve) {
			ssaL = latest(var, l.getIndex(), l.isStack());
		} else {
			ssaL = (VersionedLocal) l;
		}
		
		uses.get(ssaL).add(var);
		
		VersionedLocal newL = ssaL;

		boolean exists = true;

		if(OPTIMISE) {
			if(latest.containsKey(ssaL)) {
				/* Try to propagate a simple copy local
				 * to its use site. It is possible that
				 * a non simple copy (including phis)
				 * will not have a mapping. In this case
				 * they will not have an updated target.*/
				LatestValue value = latest.get(ssaL);
				if((value.getType() == LatestValue.TYPE_LOCAL || value.getType() == LatestValue.TYPE_PHI) && ssaL != value.getSuggestedValue()) {
					VersionedLocal vl = (VersionedLocal) value.getSuggestedValue();
					if((ssaL.isStack() && !vl.isStack()) || (ssaL.isStack() == vl.isStack())) {
						newL = vl;
					}
				} else if(!isPhi && (value.getType() != LatestValue.TYPE_LOCAL && value.getType() != LatestValue.TYPE_PHI)) {
					Expression e = null;
					
					AbstractCopyStatement def = defs.get(ssaL);
					Expression rval = (Expression) value.getSuggestedValue();
					if(ConstraintUtil.isUncopyable(rval)) {
						/* If the expression is uncopyable, we may
						 * be able to propagate it but it is also
						 * possible that we may not be able to. The
						 * current copy may be a simple copy, however,
						 * so we can try to propagate the simply copy
						 * target first. */
						if(value.getRealValue() instanceof VersionedLocal) {
							VersionedLocal realVal = (VersionedLocal) value.getRealValue();
							AbstractCopyStatement realValDef = defs.get(realVal);
							Expression realValDefE = realValDef.getExpression();
							if(realValDefE.getOpcode() == Opcode.LOCAL_LOAD) {
								VersionedLocal varDef = (VersionedLocal) ((VarExpression) realValDefE).getLocal();
								newL = varDef;
							}
						}
						
						if(newL == ssaL) {
							deferred.add(newL);
//							System.out.println("Start Check constraints");
//							if(value.canPropagate(def, var.getRootParent(), var, true)) {
//								DeferredTranslation dt = new DeferredTranslation(ssaL, def, var.getRootParent(), var, value);
//								deferred.add(dt);
//								System.out.println(value);
//								System.out.println("defer: " + def);
//							} 
//							else {
//								System.out.println("SSAGenPass.translateStmt(noprop)");
//							}
//							System.out.println("End Check constraints");
						}
					} else {
						if(!value.hasConstraints() || value.canPropagate(def, var.getRootParent(), var, false)) {
							e = rval;
						}
					}
					
					if(e != null) {
//						System.out.println("=====");
//						System.out.println("   ssaL: " + ssaL);
//						System.out.println("   bpar: " + var.getParent());
						Statement parent = var.getParent();
						int idx = parent.indexOf(var);
						parent.overwrite(e = e.copy(), idx);
						
//						System.out.println("    def: " + def);
//						System.out.println("    idx: " + idx);
//						System.out.println("    val: " + value);
//						System.out.println("   apar: " + parent);
//						System.out.println("      e: " + e);

						/* Remove the use of the var before
						 * we translate the children of the 
						 * newly propagated expression.*/
						uses.get(ssaL).remove(var);
//						System.out.println("   uses: " + uses.get(ssaL));
						
						/* Account for the new uses.*/
						for(Statement c : e.enumerate()) {
							if(c.getOpcode() == Opcode.LOCAL_LOAD) {
//								System.out.println("           v: " + c);
								VarExpression ve = (VarExpression) c;
								VersionedLocal veL = (VersionedLocal) ve.getLocal();
								uses.get(veL).add(ve);
							}
						}
						
						/* Finally see if we can reduce
						 * this statement further.*/
						translate(e, false, isPhi);
						
						exists = false;
					}
				} else {
					newL = ssaL;
				}
			} else {
				throw new IllegalStateException("No (self) ancestors: " + l + " -> " + ssaL);
			}
		}

		if(exists) {
			if(OPTIMISE) {
				/* If we removed the local load expression,
				 * check to see if we need to update the
				 * use-map.*/
				// System.out.println("replace: " + ssaL + " with " + newL);
				if(ssaL != newL) {
					System.out.println(ssaL + "  -->  " + newL);
					uses.get(ssaL).remove(var);
					uses.get(newL).add(var);
				}
			}

			/* If the expression still exists, update
			 * or set both variable and type information.*/
			var.setLocal(newL);
			Type type = types.get(ssaL);
			if(type == null) {
				throw new IllegalStateException(var + ", " + ssaL + ", t=null");
			} else {
				var.setType(type);
			}
		}
	}
	
	private VersionedLocal latest(Statement root, int index, boolean isStack) {
		LocalsPool handler = builder.graph.getLocals();
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
		
		return handler.get(index, stack.peek()/*subscript*/, isStack);
	}
	
	private void pruneStatements() {
		for(Entry<VersionedLocal, Set<VarExpression>> e : uses.entrySet()) {
			VersionedLocal vl = e.getKey();
			if(e.getValue().size() == 0) {
				AbstractCopyStatement def = defs.get(vl);
				prune(def);
			}
		}
	}
	
	private void prune(AbstractCopyStatement def) {
		if(def.isSynthetic()) {
			return;
		}
		
		Expression e = def.getExpression();
		
		if(canPrune(e)) {
			def.getRootParent().delete();
		}
	}
	
	private boolean canPrune(Statement stmt) {
		int op = stmt.getOpcode();
		if(op == Opcode.INVOKE || op == Opcode.DYNAMIC_INVOKE || op == Opcode.INIT_OBJ) {
			return false;
		}
		
		for(Statement s : stmt.getChildren()) {
			if(!canPrune(s)) {
				return false;
			}
		}
		
		return true;
	}
	
	private void processDeferredTranslations() {
		for (VersionedLocal l : deferred) {
			Set<VarExpression> useSet = uses.get(l);

			if (useSet.size() == 1) {
				/* In this case, the only place that the value
				 * of this assignment will be used is at the use site.
				 * Since that value can not be spread until this one
				 * is, we can propagate it.*/
				AbstractCopyStatement def = defs.get(l);
				if (def.getOpcode() != Opcode.PHI_STORE) {
					System.out.println(" propp " + l);
					Expression rhs = def.getExpression();
					rhs.unlink();
					def.delete();

					VarExpression use = useSet.iterator().next();
					Statement parent = use.getParent();
					parent.overwrite(rhs, parent.indexOf(use));
				}
			} else {
			}
		}
		
//		for(DeferredTranslation d : deferred) {
//			VersionedLocal l = d.getLocal();
//			Set<VarExpression> useSet = uses.get(l);
//			System.out.println("dt:");
//			System.out.println("   l   : " + d.getLocal());
//			System.out.println("   def : " + d.getDef());
//			System.out.println("   use : " + d.getUse());
//			System.out.println("   tail: " + d.getTail());
//			System.out.println("   val : " + d.getValue());
//			
//			if(useSet.size() == 1) {
//				/* In this case, the only place that the value of
//				 * this assignment will be used is at the use site.
//				 * Since that value can not be spread until this one
//				 * is, we can propagate it. */
//				AbstractCopyStatement def = d.getDef();
//				if(def.getOpcode() != Opcode.PHI_STORE) {
//					System.out.println("   willprop");
//					Expression rhs = def.getExpression();
//					rhs.unlink();
//					def.delete();
//					
//					VarExpression use = (VarExpression) d.getTail();
//					Statement parent = use.getParent();
//					parent.overwrite(rhs, parent.indexOf(use));
//				}
//			} else {
//				System.out.println("   wontprop");
//			}
//		}
	}
	
	private void makeLiveness() {
		SSABlockLivenessAnalyser liveness = new SSABlockLivenessAnalyser(builder.graph);
		liveness.compute();
		this.liveness = liveness;
	}
	
	@Override
	public void run() {
		System.out.println(builder.graph);
		System.out.println();
		System.out.println();
		System.out.println();
		
		graphSize = builder.graph.size() + 1;
		builder.head = GraphUtils.connectHead(builder.graph);

		order.addAll(builder.graph.vertices());
		order.remove(builder.head);
		order.add(0, builder.head);
		builder.naturaliseGraph(order);
		
		makeLiveness();
		splitRanges();
		makeLiveness();
		
		doms = new TarjanDominanceComputor<>(builder.graph, new SimpleDfs<>(builder.graph, builder.head, true, false).getPreOrder());
		insertPhis();
		rename();
		
		if(OPTIMISE) {
			processDeferredTranslations();
			pruneStatements();
		}
		
		GraphUtils.disconnectHead(builder.graph, builder.head);
	}
}