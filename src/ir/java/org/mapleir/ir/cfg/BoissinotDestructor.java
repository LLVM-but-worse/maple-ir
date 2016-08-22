package org.mapleir.ir.cfg;

import static org.mapleir.ir.dot.ControlFlowGraphDecorator.OPT_DEEP;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.ir.analysis.DominanceLivenessAnalyser;
import org.mapleir.ir.analysis.ExtendedDfs;
import org.mapleir.ir.analysis.SSADefUseMap;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.dot.ControlFlowGraphDecorator;
import org.mapleir.ir.dot.LivenessDecorator;
import org.mapleir.ir.locals.BasicLocal;
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
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.mapleir.stdlib.ir.transform.Liveness;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class BoissinotDestructor implements Liveness<BasicBlock>, Opcode {

	private final ControlFlowGraph cfg;

	private SSADefUseMap defuse;
	private DominanceLivenessAnalyser resolver;
	private Map<Local, Local> values;

	// delete me
	private final Set<Local> localsTest = new HashSet<>();
	@Override
	public Set<Local> in(BasicBlock b) {
		Set<Local> live = new HashSet<>();
		for (Local l : localsTest)
			if (resolver.isLiveIn(b, l))
				live.add(l);
		return live;
	}

	@Override
	public Set<Local> out(BasicBlock b) {
		Set<Local> live = new HashSet<>();
		for (Local l : localsTest)
			if (resolver.isLiveOut(b, l))
				live.add(l);
		return live;
	}

	public BoissinotDestructor(ControlFlowGraph cfg) {
		this.cfg = cfg;
		defuse = new SSADefUseMap(cfg, false);
		values = new HashMap<>();

		BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
		DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, cfg);
		writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.setName("pre-destruct")
				.export();

		// 1. Insert copies to enter CSSA.
		init();
		insert_copies();
		verify();

		resolver = new DominanceLivenessAnalyser(cfg, defuse); // this part belongs in 2

		localsTest.addAll(defuse.phis.keySet());
		localsTest.addAll(defuse.uses.keySet());
		localsTest.addAll(defuse.defs.keySet());
		writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.add("liveness", new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(this))
				.setName("after-insert")
				.export();

		// 2. Build value interference
		compute_value_interference();

		// 3. Aggressively coalesce while in CSSA to leave SSA
		// 3a. Coalesce phi locals to leave CSSA (!!!)
		coalescePhis();
		localsTest.clear();
		localsTest.addAll(defuse.uses.keySet());
		localsTest.addAll(defuse.defs.keySet());
		writer.removeAll()
				.add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.add("liveness", new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(this))
				.setName("after-coalesce-phis")
				.export();

		// 3b. Coalesce the rest of the copies
		coalesceCopies();
		writer.removeAll()
				.add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.setName("after-coalesce")
				.export();

		// 4. Sequentialize parallel copies
		sequentialize();
		writer.removeAll()
				.add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.setName("after-sequentialize")
				.export();
	}

	public static void testSequentialize() {
		AtomicInteger base = new AtomicInteger(0);
		Local a = new BasicLocal(base, 1, false);
		Local b = new BasicLocal(base, 2, false);
		Local c = new BasicLocal(base, 3, false);
		Local d = new BasicLocal(base, 4, false);
		Local e = new BasicLocal(base, 5, false);
		Local spill = new BasicLocal(base, 6, false);
		System.out.println("SEQ TEST START");
		ParallelCopyVarStatement pcvs = new ParallelCopyVarStatement();
		pcvs.pairs.add(new CopyPair(a, b, null));
		pcvs.pairs.add(new CopyPair(b, a, null));
		pcvs.pairs.add(new CopyPair(c, b, null));
		pcvs.pairs.add(new CopyPair(d, c, null));
		pcvs.pairs.add(new CopyPair(e, a, null));
		List<CopyVarStatement> seqd = sequentialize(pcvs, spill);
		System.out.println("seq test: " + pcvs);
		for (CopyVarStatement cvs : seqd)
			System.out.println("  " + cvs);
	}

	private void init() {
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b)  {
				int opcode = stmt.getOpcode();
				boolean isPhi = opcode == PHI_STORE;

				if(isPhi || opcode == Opcode.LOCAL_STORE) {
					AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
					Local l = copy.getVariable().getLocal();
					defuse.defs.put(l, b);
				}

				if(isPhi) {
					CopyPhiStatement copy = (CopyPhiStatement) stmt;
					PhiExpression phi = copy.getExpression();

					for(Entry<BasicBlock, Expression> en : phi.getArguments().entrySet()) {
						BasicBlock p = en.getKey();
						Expression expr = en.getValue();
						Local ul = null;

						if(expr.getOpcode() != Opcode.LOCAL_LOAD) {
							ul = separatePhiDef(copy, p);
						} else {
							VarExpression v = (VarExpression) expr;
							ul = v.getLocal();
						}

						defuse.uses.getNonNull(ul).add(p);
					}

					defuse.phis.put(copy.getVariable().getLocal(), copy.getExpression());
				} else {
					for(Statement s : stmt) {
						if(s.getOpcode() == Opcode.LOCAL_LOAD) {
							Local l = ((VarExpression) s).getLocal();
							defuse.uses.getNonNull(l).add(b);
						}
					}
				}
			}
		}
	}

	private Local separatePhiDef(CopyPhiStatement copy, BasicBlock pred) {
		PhiExpression phi = copy.getExpression();
		Expression expr = phi.getArgument(pred);

		Local ul = cfg.getLocals().makeLatestVersion(copy.getVariable().getLocal());

		CopyVarStatement newCopy = new CopyVarStatement(new VarExpression(ul, expr.getType()), expr);
		insert_end(pred, newCopy);
		phi.setArgument(pred, new VarExpression(ul, expr.getType()));

		// we consider phi args to be used in the pred.
		defuse.defs.put(ul, pred);
		defuse.uses.getNonNull(ul).add(pred);

		return ul;
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

		for(Statement stmt : b) {
			if(stmt.getOpcode() != Opcode.PHI_STORE) {
				break;
			}

			CopyPhiStatement copy = (CopyPhiStatement) stmt;
			PhiExpression phi = copy.getExpression();

			// for every xi arg of the phi from pred Li, add it to the worklist
			// so that we can parallelise the copy when we insert it.
			for(Entry<BasicBlock, Expression> e : phi.getArguments().entrySet()) {
				BasicBlock h = e.getKey();
				VarExpression v = (VarExpression) e.getValue();
				PhiRes r = new PhiRes(copy.getVariable().getLocal(), phi, h, v.getLocal(), v.getType());
				wl.getNonNull(h).add(r);
			}

			// for each x0, where x0 is a phi copy target, create a new
			// variable z0 for a copy x0 = z0 and replace the phi
			// copy target to z0.
			Local x0 = copy.getVariable().getLocal();
			Local z0 = cfg.getLocals().makeLatestVersion(x0);
			dst_copy.pairs.add(new CopyPair(x0, z0, copy.getVariable().getType())); // x0 = z0
			copy.getVariable().setLocal(z0); // z0 = phi(...)

			// both defined and used in this block.
			defuse.defs.put(x0, b);
			defuse.defs.put(z0, b);
			defuse.uses.getNonNull(z0).add(b);

			defuse.phis.remove(x0);
			defuse.phis.put(z0, phi);
		}

		// resolve
		if(dst_copy.pairs.size() > 0) {
			insert_start(b, dst_copy);
			record_pcopy(b, dst_copy);
		}

		for(Entry<BasicBlock, List<PhiRes>> e : wl.entrySet()) {
			BasicBlock p = e.getKey();

			ParallelCopyVarStatement copy = new ParallelCopyVarStatement();

			for(PhiRes r : e.getValue()) {
				// for each xi source in a phi, create a new variable zi,
				// and insert the copy zi = xi in the pred Li. then replace
				// the phi arg from Li with zi.

				Local xi = r.l;
				Local zi = cfg.getLocals().makeLatestVersion(xi);
				copy.pairs.add(new CopyPair(zi, xi, r.type));

				// we consider phi args to be used in the pred
				//  instead of the block where the phi is, so
				//  we need to update the def/use maps here.

				// zi is defined in the pred.
				defuse.defs.put(zi, p);
				// xi is used in the zi def.
				defuse.uses.getNonNull(zi).add(p);
				// xi is replaced with zi in the phi block,
				//  but for this implementation, we consider
				//  the phi source uses to be in the pre.
				//  n.b. that zi, which should be used in the
				//       phi pred is already added above.
				defuse.uses.getNonNull(xi).remove(p);

				r.phi.setArgument(r.pred, new VarExpression(zi, r.type));
			}

			insert_end(p, copy);
			record_pcopy(p, copy);
		}
	}

	void record_pcopy(BasicBlock b, ParallelCopyVarStatement copy) {
		System.out.println("INSERT: " + copy);

		for(CopyPair p : copy.pairs) {
			defuse.defs.put(p.targ, b);
			defuse.uses.getNonNull(p.source).add(b);

			localsTest.add(p.targ);
			localsTest.add(p.source);
		}
	}

	void insert_empty(BasicBlock b, List<Statement> stmts, Statement s) {
		stmts.add(s);
	}

	void insert_start(BasicBlock b, ParallelCopyVarStatement copy) {
		record_pcopy(b, copy);

		if(b.isEmpty()) {
			insert_empty(b, b, copy);
		} else {
			// insert after phi.
			int i = 0;
			Statement stmt;
			do
				stmt = b.get(i++);
			while(stmt.getOpcode() == Opcode.PHI_STORE && i < b.size());
			b.add(i, copy);
		}
	}

	void insert_end(BasicBlock b, Statement copy) {
		if(b.isEmpty()) {
			insert_empty(b, b, copy);
		} else {
			Statement last = b.get(b.size() - 1);
			if(!last.canChangeFlow()) {
				b.add(copy);
			} else {
				// index += 1;
				//  ^ do this above so that s goes to the end
				//    but here it needs to go before the end/jump.
				// add before the jump
				b.add(b.indexOf(last), copy);
			}
		}
	}

	void verify() {
		Map<Local, BasicBlock> defs = new HashMap<>();
		NullPermeableHashMap<Local, Set<BasicBlock>> uses = new NullPermeableHashMap<>(new SetCreator<>());

		for (BasicBlock b : cfg.vertices()) {
			for (Statement stmt : b) {
				if(stmt instanceof ParallelCopyVarStatement) {
					ParallelCopyVarStatement pcopy = (ParallelCopyVarStatement) stmt;
					for(CopyPair p : pcopy.pairs) {
//						System.out.println("def " + p.targ + " = " + p.source + "  in " + b.getId());
						defs.put(p.targ, b);
						uses.getNonNull(p.source).add(b);
					}
				} else {
					boolean _phi = false;
					if (stmt instanceof AbstractCopyStatement) {
						AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
						Local l = copy.getVariable().getLocal();
						defs.put(l, b);

						Expression e = copy.getExpression();
						if (e instanceof PhiExpression) {
							_phi = true;
							PhiExpression phi = (PhiExpression) e;
							for (Entry<BasicBlock, Expression> en : phi.getArguments().entrySet()) {
								Local ul = ((VarExpression) en.getValue()).getLocal();
								uses.getNonNull(ul).add(en.getKey());
							}
						}
					}

					if (!_phi) {
						for (Statement s : stmt) {
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
		set.addAll(defuse.defs.keySet());

		for(Local l : set) {
			BasicBlock b1 = defs.get(l);
			BasicBlock b2 = defuse.defs.get(l);

			if(b1 != b2) {
				System.err.println(cfg);
				System.err.println("Defs:");
				System.err.println(b1 + ", " + b2 + ", " + l);
				throw new RuntimeException();
			}
		}

		set.clear();
		set.addAll(uses.keySet());
		set.addAll(defuse.uses.keySet());

		for(Local l : set) {
			Set<BasicBlock> s1 = uses.getNonNull(l);
			Set<BasicBlock> s2 = defuse.uses.getNonNull(l);
			if(!s1.equals(s2)) {
				System.err.println(cfg);
				System.err.println("Uses:");
				System.err.println(GraphUtils.toBlockArray(s1));
				System.err.println(GraphUtils.toBlockArray(s2));
				System.err.println(l);
				throw new RuntimeException();
			}
		}
	}

	ExtendedDfs dom_dfs;
	HashMap<BasicBlock, Integer> preDfsDomOrder;
	HashMap<Local, Local> equalAncIn;

	void compute_value_interference() {
		FastBlockGraph dom_tree = new FastBlockGraph();
		for (Entry<BasicBlock, Set<BasicBlock>> e : resolver.domc.getTree().entrySet()) {
			BasicBlock b = e.getKey();
			dom_tree.addVertex(b);
			for (BasicBlock c : e.getValue()) {
				dom_tree.addEdge(b, new ImmediateEdge<>(b, c));
			}
		}

		BasicDotConfiguration<FastBlockGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
		DotWriter<FastBlockGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, dom_tree);
		writer.removeAll().setName("domtree").export();

		dom_dfs = new ExtendedDfs(dom_tree, cfg.getEntries().iterator().next(), ExtendedDfs.POST | ExtendedDfs.PRE);

		// topo
		for (int i = dom_dfs.getPostOrder().size() - 1; i >= 0; i--) {
			BasicBlock bl = dom_dfs.getPostOrder().get(i);
			for (Statement stmt : bl) {
				if (stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					Expression e = copy.getExpression();
					Local b = copy.getVariable().getLocal();

					if (e instanceof VarExpression) {
						Local a = ((VarExpression) e).getLocal();
						System.out.println("  value " + b + " " + values.get(a));
						values.put(b, values.get(a));
					} else {
						System.out.println("  value " + b + " auto");
						values.put(b, b);
					}
				} else if (stmt instanceof CopyPhiStatement) {
					CopyPhiStatement copy = (CopyPhiStatement) stmt;
					Local b = copy.getVariable().getLocal();
					System.out.println("  value " + b + " phi auto");
					values.put(b, b);
				} else if (stmt instanceof ParallelCopyVarStatement) {
					ParallelCopyVarStatement copy = (ParallelCopyVarStatement) stmt;
					for (CopyPair p : copy.pairs) {
						Local l = p.targ;
						System.out.println("  value " + l + " " + values.get(p.source) + " parallel");
						values.put(l, values.get(p.source));
					}
				}
			}
		}
		System.out.println("values:");
		for (Entry<Local, Local> e : values.entrySet())
			System.out.println("  " + e.getKey() + " = " + e.getValue());
		System.out.println();

		// it might be possible to put this code into the reverse postorder but the paper specified preorder
		preDfsDomOrder = new HashMap<>();
		int index = 0;
		for (BasicBlock bl : dom_dfs.getPreOrder())
			preDfsDomOrder.put(bl, index++);
	}

	boolean doms(Local x, Local y) {
		BasicBlock bx = defuse.defs.get(x);
		BasicBlock by = defuse.defs.get(y);
		return resolver.doms(bx, by);
	}

	boolean checkPreDomOrder(Local x, Local y) {
		BasicBlock bx = defuse.defs.get(x);
		BasicBlock by = defuse.defs.get(y);
		if (by == null) {
			System.out.println("trap");
		}
		return preDfsDomOrder.get(bx) < preDfsDomOrder.get(by);
	}

	boolean intersect(Local a, Local b) {
		BasicBlock defA = defuse.defs.get(a);
		BasicBlock defB = defuse.defs.get(b);
		if (defA == defB) {
			boolean aLive = resolver.isLiveOut(defA, a);
			boolean bLive = resolver.isLiveOut(defA, b);
			if (aLive && bLive) // they definitely interfere
				return true;

			// ambiguous case. we need to check whether a and b are live at the same time in any point until we see both defs
			int defsSeen = 0;
			for (int i = defA.size() - 1; i >= 0; i--) {
				Statement stmt = defA.get(i);
				if (stmt instanceof CopyVarStatement) {
					Local copyLocal = ((CopyVarStatement) stmt).getVariable().getLocal();
					if (copyLocal.toString().equals(a.toString())) {
						if (++defsSeen == 2)
							return false;
					} else if (copyLocal.toString().equals(b.toString())) {
						if (++defsSeen == 2)
							return false;
					}
				} else if (stmt instanceof ParallelCopyVarStatement) {
					for (CopyPair pair : ((ParallelCopyVarStatement) stmt).pairs) {
						if (pair.targ.toString().equals(a.toString())) {
							if (++defsSeen == 2)
								return false;
						} else if (pair.targ.toString().equals(b.toString())) {
							if (++defsSeen == 2)
								return false;
						}
					}
					for (CopyPair pair : ((ParallelCopyVarStatement) stmt).pairs) {
						if (pair.source.toString().equals(a.toString()))
							aLive = true;
						else if (pair.source.toString().equals(b.toString()))
							bLive = true;
						if (aLive && bLive)
							return true;
					}
				} else if (stmt instanceof CopyPhiStatement) {
					throw new IllegalArgumentException("phi in block still?");
				} else {
					for (Statement child : stmt) {
						if (child.getOpcode() == Opcode.LOCAL_LOAD) {
							Local l = ((VarExpression) child).getLocal();
							if (l.toString().equals(a.toString()))
								aLive = true;
							else if (l.toString().equals(b.toString()))
								bLive = true;
							if (aLive && bLive)
								return true;
						}
					}
				}
			}
			return false;
		} else {
			if (resolver.sdoms(defA, defB)) {
				BasicBlock temp = defA;
				defA = defB;
				defB = temp;
			}
			if (resolver.sdoms(defB, defA)) {
				if (resolver.isLiveOut(defA, b)) // if it's liveOut it definitely intersects
					return true;
				if (!resolver.isLiveIn(defA, b)) // defA != defB, so it must be liveIn in order to intersect.
					return false;
			} else {
				return false;
			}
		}

		// ambiguous case. we need to check if use(dom) occurs after def(def), in that case it interferes. otherwise no
		for (int i = defA.size() - 1; i >= 0; i--) {
			Statement stmt = defA.get(i);
			if (stmt instanceof CopyVarStatement) {
				if (((CopyVarStatement) stmt).getVariable().getLocal().toString().equals(a.toString()))
					return false;
			} else if (stmt instanceof ParallelCopyVarStatement) {
				for (CopyPair pair : ((ParallelCopyVarStatement) stmt).pairs)
					if (pair.targ.toString().equals(a.toString()))
						return false;
				for (CopyPair pair : ((ParallelCopyVarStatement) stmt).pairs)
					if (pair.source.toString().equals(b.toString()))
						return true;
			} else if (stmt instanceof CopyPhiStatement) {
				throw new IllegalArgumentException("phi in block still?");
			} else {
				for (Statement child : stmt)
					if (child.getOpcode() == Opcode.LOCAL_LOAD)
						if (((VarExpression) child).getLocal().toString().equals(b.toString()))
							return true;
			}
		}
		throw new IllegalStateException("this shouldn't happen");
	}

	boolean checkIntersect(List<Local> red, List<Local> blue) {
		Stack<Local> dom = new Stack<>();
		int ir = 0;
		int ib = 0;
		while (ir < red.size() || ib < blue.size()) {
			Local current;
			if (ir == red.size() || (ir < red.size() && ib < blue.size() && checkPreDomOrder(blue.get(ib), red.get(ir))))
				current = blue.get(ib++);
			else
				current = red.get(ir++);

			if (!dom.isEmpty()) {
				Local other;
				do
					other = dom.pop();
				while (!dom.isEmpty() && !doms(other, current));
				Local parent = other;
				if (parent != null && interference(current, parent))
					return true;
			}
			dom.push(current);
		}
		return false;
	}

	boolean interference(Local a, Local b) {
		intersect(a, b); // fuck it; ignore values for now.
		// how the f--k do you compute equal intersecting ancestor?????
		return false;
	}

	// all the locals in a set will be mapped to that set. there is only 1 instance of the set.
	// whenever a local is added ot the set the mapping is added and the opposite is true for when a local is removed
	// locals within the classes should be kept unique
	Map<Local, List<Local>> congruenceClasses;

	List<Local> getCongruenceClass(Local l) {
		if (congruenceClasses.containsKey(l))
			return congruenceClasses.get(l);
		List<Local> conClass = new ArrayList<>();
		conClass.add(l);
		congruenceClasses.put(l, conClass);
		return conClass;
	}

	// process the copy a = b
	// returns whether the a and b can be coalesced
	boolean tryCoalesceCopy(Local a, Local b) {
		List<Local> conClassA = getCongruenceClass(a);
		List<Local> conClassB = getCongruenceClass(b);

		System.out.print("  check intersect: " + conClassA + " vs " + conClassB + ": ");
		if (checkIntersect(conClassA, conClassB)) {
			System.out.println("true");
			return false;
		}
		System.out.println("false");
		// merge congruence classes
		conClassA.addAll(conClassB);
		for (Local l : conClassB)
			congruenceClasses.put(l, conClassA);
		return true;
	}

	void applyRemapping(Map<Local, Local> remap) {
		// defuse can be used here to speed things up. TODO
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b) {
				int opcode = stmt.getOpcode();

				if(opcode == -1) {
					ParallelCopyVarStatement copy = (ParallelCopyVarStatement) stmt;
					for(CopyPair p : copy.pairs) {
						p.source = remap.getOrDefault(p.source, p.source);
						p.targ = remap.getOrDefault(p.targ, p.targ);
					}
				} else if(opcode == Opcode.LOCAL_STORE || opcode == Opcode.PHI_STORE) {
					AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
					VarExpression v = copy.getVariable();
					v.setLocal(remap.getOrDefault(v.getLocal(), v.getLocal()));
				}

				for(Statement s : stmt) {
					if(s.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpression v = (VarExpression) s;
						v.setLocal(remap.getOrDefault(v.getLocal(), v.getLocal()));
					}
				}
			}
		}

		for (Entry<Local, Local> e : remap.entrySet()) {
			defuse.defs.remove(e.getKey());
			defuse.uses.remove(e.getKey());
		}
	}

	private void coalescePhis() {
		// use defuse.phis TODO
		Map<Local, Local> remap = new HashMap<>();
		for (Entry<Local, PhiExpression> e : defuse.phis.entrySet()) {
			Local l1 = e.getKey();
			BasicBlock b = defuse.defs.get(l1);
			// since we are now in csaa, phi locals never interfere and are in the same congruence class.
			// therefore we can coalesce them all together and drop phis. with this, we leave cssa.
			PhiExpression phi = e.getValue();

			Local newL = cfg.getLocals().makeLatestVersion(l1);
			remap.put(l1, newL);
			values.put(newL, newL); // collapsed phi vars have 'blank' value
			// we have to update values map too for all the defs
			defuse.defs.put(newL, b); // TODO: move this processing for defuse
			Set<BasicBlock> newUses = new HashSet<>();
			newUses.add(b);
			newUses.addAll(defuse.uses.get(l1));
			defuse.uses.put(newL, newUses);

			System.out.println("  " + newL + " phi auto");
			System.out.println("phi def remap: " + l1 + " " + newL);

			for(Expression ex : phi.getArguments().values()) {
				VarExpression v = (VarExpression) ex;
				Local l = v.getLocal();
				remap.put(l, newL);
				newUses.addAll(defuse.uses.get(l));
				// we have to update values map too for all the defs
				// PROBLEM IS NOW WE HAVE MULTIPLE DEFS OF THE SAME VAR BUT WE CAN'T MATCH THAT IN DEFUSE.DEFS
				// NOW THE DOMINANCE LIVENESS DOESNT WORK (IT DEPENDS ON SSA)
				// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
				System.out.println("phi use remap: " + l + " " + newL);
			}

			// we can simply drop all the phis without further consideration
			for (Iterator<Statement> it = b.iterator(); it.hasNext();) {
				if (it.next().getOpcode() == Opcode.PHI_STORE)
					it.remove();
				else
					break;
			}
		}
		defuse.phis.clear();

		applyRemapping(remap);
		remap.clear();
	}

	private void coalesceCopies() {
		Map<Local, Local> remap = new HashMap<>();
		congruenceClasses = new HashMap<>();
		// now for each copy check if lhs and rhs congruence classes do not interfere.
		// if they do not interfere merge the conClasses and those two vars can be coalesced. delete the copy.
		for(BasicBlock b : dom_dfs.getPreOrder()) {
			Iterator<Statement> it = b.iterator();
			while (it.hasNext()) {
				Statement stmt = it.next();
				if (stmt.getOpcode() == Opcode.LOCAL_STORE) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					if (copy.getExpression().getOpcode() == Opcode.LOCAL_STORE) {
						Local lhs = copy.getVariable().getLocal();
						Local rhs = ((VarExpression) copy.getExpression()).getLocal();
						if (tryCoalesceCopy(lhs, rhs)) {
							Local remapLocal = congruenceClasses.get(lhs).get(0);
							remap.put(lhs, remapLocal);
							remap.put(rhs, remapLocal);
							it.remove();
							System.out.println("coalesce " + lhs + " = " + rhs);
							System.out.println("  remap to " + remapLocal);
						}
					}
				} else if (stmt.getOpcode() == -1) {
					// we need to do it for each one. if all of the copies are removed then remove the pcvs
					ParallelCopyVarStatement copy = (ParallelCopyVarStatement) stmt;
					System.out.println("p coalesce " + copy);
					for (Iterator<CopyPair> pairIter = copy.pairs.iterator(); pairIter.hasNext();) {
						CopyPair pair = pairIter.next();
						if (tryCoalesceCopy(pair.targ, pair.source)) {
							Local remapLocal = congruenceClasses.get(pair.targ).get(0);
							remap.put(pair.targ, remapLocal);
							remap.put(pair.source, remapLocal);
							pairIter.remove();
							System.out.println("  psub coalesce " + pair.targ + " = " + pair.source);
							System.out.println("    remap to " + remapLocal);
						}
					}
					if (copy.pairs.isEmpty()) {
						it.remove();
						System.out.println("  >total coalesce");
					}
				}
			}
		}
		applyRemapping(remap);
	}

	void sequentialize() {
		for (BasicBlock b : cfg.vertices())
			sequentialize(b);
	}

	void sequentialize(BasicBlock b) {
		LinkedHashMap<ParallelCopyVarStatement, Integer> p = new LinkedHashMap<>();
		for (int i = 0; i < b.size(); i++) {
			Statement stmt = b.get(i);
			if (stmt instanceof ParallelCopyVarStatement)
				p.put((ParallelCopyVarStatement) stmt, i);
		}
		if (p.isEmpty())
			return;
		int indexOffset = 0;
		Local spill = cfg.getLocals().makeLatestVersion(p.entrySet().iterator().next().getKey().pairs.get(0).targ);
		for (Entry<ParallelCopyVarStatement, Integer> e : p.entrySet()) {
			ParallelCopyVarStatement pcvs = e.getKey();
			int index = e.getValue() + indexOffset;
			if (pcvs.pairs.size() == 0)
				throw new IllegalArgumentException("pcvs is empty");
			else if (pcvs.pairs.size() == 1) { // constant sequentialize for trivial parallel copies
				CopyPair pair = pcvs.pairs.get(0);
				CopyVarStatement newCopy = new CopyVarStatement(new VarExpression(pair.targ, pair.type), new VarExpression(pair.source, pair.type));
				b.set(index, newCopy);
			} else {
				List<CopyVarStatement> sequentialized = sequentialize(pcvs, spill);
				b.remove(pcvs);
				// warning: O(N^2) operation
				for (CopyVarStatement cvs : sequentialized) // warning: O(N^2) operation
					b.add(index + indexOffset++, cvs);
				indexOffset--;
			}
		}
	}

	static List<CopyVarStatement> sequentialize(ParallelCopyVarStatement pcvs, Local spill) {
		Stack<Local> ready = new Stack<>();
		Stack<Local> to_do = new Stack<>();
		Map<Local, Local> loc = new HashMap<>();
		Map<Local, Local> values = new HashMap<>();
		Map<Local, Local> pred = new HashMap<>();
		Map<Local, Type> types = new HashMap<>();
		pred.put(spill, null);

		for (CopyPair pair : pcvs.pairs) { // initialization
			loc.put(pair.targ, null);
			loc.put(pair.source, null);
			values.put(pair.targ, pair.targ);
			values.put(pair.source, pair.source);
			types.put(pair.targ, pair.type);
			types.put(pair.source, pair.type);
		}

		for (CopyPair pair : pcvs.pairs) {
			loc.put(pair.source, pair.source); // needed and not copied yet
			pred.put(pair.targ, pair.source); // unique predecessor
			to_do.push(pair.targ); // copy into b to be done
		}

		for (CopyPair pair : pcvs.pairs) {
			if (!loc.containsKey(pair.targ))
				throw new IllegalStateException("this shouldn't happen");
			if (loc.get(pair.targ) == null) // b is not used and can be overwritten
				ready.push(pair.targ);
		}

		List<CopyVarStatement> result = new ArrayList<>();
		while (!to_do.isEmpty()) {
			while (!ready.isEmpty()) {
				Local b = ready.pop(); // pick a free location
				Local a = pred.get(b); // available in c
				Local c = loc.get(a);
				if ((!types.containsKey(b) && b != spill) || (!types.containsKey(c) && c != spill))
					throw new IllegalStateException("this shouldn't happen " + b + " " + c);

				VarExpression varB = new VarExpression(b, types.get(b)); // generate the copy b = c
				VarExpression varC = new VarExpression(c, types.get(c));
				result.add(new CopyVarStatement(varB, varC));
				values.put(b, values.get(c));

				loc.put(a, b);
				if (a.toString().equals(c.toString()) && pred.get(a) != null) {
					if (!pred.containsKey(a))
						throw new IllegalStateException("this shouldn't happen");
					ready.push(a); // just copied, can be overwritten
				}
			}

			Local b = to_do.pop();
			if (!values.get(b).toString().equals(values.get(loc.get(pred.get(b))).toString())) {
				System.out.println("  spill " + b + " (" + b + " vs " + loc.get(pred.get(b)).toString() + ")" + " (" + values.get(b) + " vs " + values.get(loc.get(pred.get(b))) + ")");
				if (!types.containsKey(b))
					throw new IllegalStateException("this shouldn't happen");
				VarExpression varN = new VarExpression(spill, types.get(b)); // generate copy n = b
				VarExpression varB = new VarExpression(b, types.get(b));
				result.add(new CopyVarStatement(varN, varB));
				values.put(spill, values.get(b));
				loc.put(b, spill);
				ready.push(b);
			}
		}

		return result;
	}

	class PhiRes {
		final Local target;
		final PhiExpression phi;
		final BasicBlock pred;
		final Local l;
		final Type type;

		PhiRes(Local target, PhiExpression phi, BasicBlock src, Local l, Type type) {
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
		Type type;

		CopyPair(Local dst, Local src, Type type) {
			targ = dst;
			source = src;
			this.type = type;
		}

		@Override
		public String toString() {
			return targ + " =P= " + source;
		}
	}

	static class ParallelCopyVarStatement extends Statement {

		final List<CopyPair> pairs;

		ParallelCopyVarStatement() {
			super(-1);
			pairs = new ArrayList<>();
		}

		ParallelCopyVarStatement(List<CopyPair> pairs) {
			super(-1);
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