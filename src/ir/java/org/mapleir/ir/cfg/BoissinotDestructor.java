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

		BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
		DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, cfg);
		writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.setName("pre-destruct")
				.export();

		// 1. Insert copies to enter CSSA.
		init();
		insert_copies();
		createDuChains();
		verify();

		localsTest.addAll(defuse.phis.keySet());
		localsTest.addAll(defuse.uses.keySet());
		localsTest.addAll(defuse.defs.keySet());
		writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.add("liveness", new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(this))
				.setName("after-insert")
				.export();

		// 2. Build value interference
		compute_value_interference();
		defuse.buildIndices();

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
		applyRemapping(remap);
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
		for (Statement stmt : pcvs) {
			if (stmt.getOpcode() == Opcode.LOCAL_LOAD)
				System.out.println(((VarExpression) stmt).getLocal());
		}
		List<CopyVarStatement> seqd = sequentialize(pcvs, spill);
		System.out.println("seq test: " + pcvs);
		for (CopyVarStatement cvs : seqd)
			System.out.println("  " + cvs);
	}

	private void init() {
		// save non-var phi args to vars
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b)  {
				if(stmt instanceof CopyPhiStatement) {
					CopyPhiStatement copy = (CopyPhiStatement) stmt;
					for (Entry<BasicBlock, Expression> en : copy.getExpression().getArguments().entrySet())
						if (en.getValue().getOpcode() != Opcode.LOCAL_LOAD)
							separatePhiDef(copy, en.getKey());
				}
			}
		}

		// create dominance
		resolver = new DominanceLivenessAnalyser(cfg, null);
	}

	private Local separatePhiDef(CopyPhiStatement copy, BasicBlock pred) {
		PhiExpression phi = copy.getExpression();
		Expression expr = phi.getArgument(pred);

		Local ul = cfg.getLocals().makeLatestVersion(copy.getVariable().getLocal());

		CopyVarStatement newCopy = new CopyVarStatement(new VarExpression(ul, expr.getType()), expr);
		insert_end(pred, newCopy);
		phi.setArgument(pred, new VarExpression(ul, expr.getType()));

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
		}

		// resolve
		if(dst_copy.pairs.size() > 0)
			insert_start(b, dst_copy);

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

				r.phi.setArgument(r.pred, new VarExpression(zi, r.type));
			}

			if (FACILITATE_COALESCE) {
				// replace uses of xi with zi in dominated successors to faciltate coalescing
				for (BasicBlock succ : resolver.sdoms.getNonNull(p)) {
					for (Statement stmt : succ) {
						for (Statement child : stmt) {
							if (child.getOpcode() == Opcode.LOCAL_LOAD) {
								VarExpression var = ((VarExpression) child);
								Local local = var.getLocal();
								for (CopyPair pair : copy.pairs) {
									if (local == pair.source)
										var.setLocal(pair.targ);
								}
							}
						}
					}
				}
			}

			insert_end(p, copy);
			record_pcopy(p, copy);
		}
	}

	private static final boolean FACILITATE_COALESCE = false;

	void record_pcopy(BasicBlock b, ParallelCopyVarStatement copy) {
		System.out.println("INSERT: " + copy + " from " + Thread.currentThread().getStackTrace()[2].toString());

		for(CopyPair p : copy.pairs) {
			localsTest.add(p.targ);
			localsTest.add(p.source);
		}
	}

	void insert_empty(BasicBlock b, List<Statement> stmts, Statement s) {
		stmts.add(s);
	}

	void insert_start(BasicBlock b, ParallelCopyVarStatement copy) {

		if(b.isEmpty()) {
			insert_empty(b, b, copy);
		} else {
			// insert after phi.
			int i;
			for (i = 0; b.get(i).getOpcode() == Opcode.PHI_STORE && i < b.size(); i++);
			b.add(i, copy);
		}
		record_pcopy(b, copy);
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

	void createDuChains() {
		defuse = new SSADefUseMap(cfg, true) {
			@Override
			protected void build(BasicBlock b, Statement stmt) {
				if (stmt instanceof ParallelCopyVarStatement) {
					ParallelCopyVarStatement copy = (ParallelCopyVarStatement) stmt;
					for (CopyPair pair : copy.pairs) {
						defs.put(pair.targ, b);
						uses.getNonNull(pair.source).add(b);
					}
				} else {
					super.build(b, stmt);
				}
			}

			@Override
			protected void buildIndex(BasicBlock b, Statement stmt, int index) {
				if (stmt instanceof ParallelCopyVarStatement) {
					ParallelCopyVarStatement copy = (ParallelCopyVarStatement) stmt;
					for (CopyPair pair : copy.pairs) {
						defIndex.put(pair.targ, index);
						lastUseIndex.getNonNull(pair.source).put(b, index);
					}
				} else {
					super.buildIndex(b, stmt, index);
				}
			}
		};

		resolver.setDefuse(defuse);
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

	HashMap<Local, Integer> preDfsDomOrder;
	ExtendedDfs dom_dfs;
	HashMap<Local, Local> equalAncIn;
	HashMap<Local, Local> equalAncOut;

	void compute_value_interference() {
		values = new HashMap<>();
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

		// topo
		dom_dfs = new ExtendedDfs(dom_tree, cfg.getEntries().iterator().next(), ExtendedDfs.POST | ExtendedDfs.PRE);
		List<BasicBlock> postorder = dom_dfs.getPostOrder();
		for (int i = postorder.size() - 1; i >= 0; i--) {
			BasicBlock bl = postorder.get(i);
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
					PhiExpression phi = copy.getExpression();

					// value of phi def is first decided arg or undecided
					values.put(b, b);
//						for (Expression arg : phi.getArguments().values()) {
//							Local phiLocal = ((VarExpression) arg).getLocal();
//							Local phiValue = values.get(phiLocal);
//							if (!phiValue.toString().equals(phiLocal.toString())) {
//								values.put(b, phiValue);
//								break;
//							}
//						}

					System.out.println("  value " + b + " " + values.get(b) + " phi");
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

		// it might be possible to put this code into the reverse postorder but the paper specified preorder
		preDfsDomOrder = new HashMap<>();
		int domIndex = 0;
		for (BasicBlock bl : dom_dfs.getPreOrder()) {
			for (Statement stmt : bl) {
				if (stmt instanceof AbstractCopyStatement) {
					preDfsDomOrder.put(((AbstractCopyStatement) stmt).getVariable().getLocal(), domIndex++);
				} else if (stmt.getOpcode() == -1) {
					for (CopyPair pair : ((ParallelCopyVarStatement) stmt).pairs)
						preDfsDomOrder.put(pair.targ, domIndex++);
				}
			}
		}

		System.out.println("values:");
		for (Entry<Local, Local> e : values.entrySet())
			System.out.println("  " + e.getKey() + " = " + e.getValue());
		System.out.println();

		System.out.println();
		for (Entry<Local, Integer> e : preDfsDomOrder.entrySet())
			System.out.println(e.getKey() + " " + e.getValue());
		System.out.println();
		System.out.println();
	}

	boolean doms(Local x, Local y) {
		BasicBlock bx = defuse.defs.get(x);
		BasicBlock by = defuse.defs.get(y);
		
		if(resolver.sdoms(bx, by)) {
			return true;
		} else if(bx == by) {
			int i1 = defuse.defIndex.get(x);
			int i2 = defuse.defIndex.get(y);
			return i1 > i2;
		} else {
			return false;
		}
	}

	boolean checkPreDomOrder(Local x, Local y) {
		return preDfsDomOrder.get(x) < preDfsDomOrder.get(y);
	}

	boolean intersect(Local a, Local b) {
		if (a == b) {
			for (Entry<Local, CongruenceClass> e : congruenceClasses.entrySet())
				System.err.println(e.getKey() + " in " + e.getValue());
			throw new IllegalArgumentException("me too thanks: " + a);
		}
		if (checkPreDomOrder(a, b)) {
			throw new IllegalArgumentException("b should dom a");
//			Local temp = a;
//			a = b;
//			b = temp;
		}
		BasicBlock defA = defuse.defs.get(a);
		if (checkPreDomOrder(b, a)) { // dom = b ; def = a
			if (resolver.isLiveOut(defA, b)) // if it's liveOut it definitely intersects
				return true;
			if (!resolver.isLiveIn(defA, b) && defA != defuse.defs.get(b)) // defA == defB or liveIn to intersect
				return false;
			// ambiguous case. we need to check if use(dom) occurs after def(def), in that case it interferes. otherwise no
			int domUseIndex = defuse.lastUseIndex.get(b).get(defA);
			int defDefIndex = defuse.defIndex.get(a);
			return domUseIndex > defDefIndex;
		} else {
			throw new IllegalArgumentException("this shouldn't happen???");
//			return false;
		}
	}

	class CongruenceClass extends TreeSet<Local> {
		CongruenceClass() {
			super(new Comparator<Local>() {
				@Override
				public int compare(Local o1, Local o2) {
					if (o1 == o2)
						return 0;
					return checkPreDomOrder(o1, o2)? -1 : 1;
				}
			});
		}
	}

	class LocalInfo {
		final Local l;
		final CongruenceClass conClass;

		LocalInfo(Local local, CongruenceClass congruenceClass) {
			l = local;
			conClass = congruenceClass;
		}

		@Override
		public String toString() {
			return l.toString() + " \u2208 " + conClass;
		}
	}

	private final static boolean VALUE_INTERFERENCE = true;
	boolean interference(Local a, Local b, boolean sameConClass) {
		if (VALUE_INTERFERENCE) {
			equalAncOut.put(a, null);
			if (sameConClass)
				b = equalAncOut.get(b);

			if (values.get(a) != values.get(b)) {
				Local tmp = b;
				while (tmp != null && !intersect(a, tmp)) {
					System.out.println("      traverse " + tmp);
					tmp = equalAncIn.get(tmp);
				}
				System.out.println("      different values " + tmp);
				return tmp != null;
			} else {
				System.out.println("      fucker in action");
				Local tmp = b;
				while (tmp != null && !intersect(a, tmp))
					tmp = equalAncIn.get(tmp);
				equalAncOut.put(a, tmp);
				return false;
			}
		} else {
			return intersect(a, b);
		}
	}

	boolean checkInterfereSingle(CongruenceClass red, CongruenceClass blue) {
		Local a = red.first();
		Local b = blue.first();
		if (checkPreDomOrder(a, b)) { // we want a > b in dom order (b is parent)
			Local c = a;
			a = b;
			b = c;
		}
		if (intersect(a, b) && values.get(a) != values.get(b)) {
			return true;
		} else {
			equalAncIn.put(a, b);
			red.add(b);
			congruenceClasses.put(b, red);
			return false;
		}
	}

	boolean checkInterfere(CongruenceClass red, CongruenceClass blue) {
		Stack<LocalInfo> dom = new Stack<>();
		Local ir = red.first(), ib = blue.first();
		Local lr = red.last(), lb = blue.last();
		boolean redHasNext = true, blueHasNext = true;
		while (redHasNext || blueHasNext) {
			LocalInfo current;
			if (!blueHasNext || (redHasNext && blueHasNext && checkPreDomOrder(ir, ib))) {
				current = new LocalInfo(ir, red); // current = red[ir++)
				if (redHasNext = ir != lr)
					ir = red.higher(ir);
//				System.out.println("    Red next, current=" + current + ", hasNext=" + redHasNext);
			} else {
				current = new LocalInfo(ib, blue); // current = blue[ib++]
				if (blueHasNext = ib != lb)
					ib = blue.higher(ib);
//				System.out.println("    Blue next, current=" + current + ", hasNext=" + blueHasNext);
			}

			if (!dom.isEmpty()) {
				LocalInfo other;
				do
					other = dom.pop();
				while (!dom.isEmpty() && !doms(other.l, current.l));

				LocalInfo parent = other;
				if (parent != null) {
					System.out.println("    check " + current + " vs " + parent + ":");
					if (interference(current.l, parent.l, current.conClass == parent.conClass)) {
						System.out.println("      => true");
						return true;
					}
				}
			}
			dom.push(current);
		}

		return false;
	}

	// all the locals in a set will be mapped to that set. there is only 1 instance of the set.
	// whenever a local is added ot the set the mapping is added and the opposite is true for when a local is removed
	// locals within the classes should be kept unique
	Map<Local, CongruenceClass> congruenceClasses;

	CongruenceClass getCongruenceClass(Local l) {
		if (congruenceClasses.containsKey(l))
			return congruenceClasses.get(l);
		CongruenceClass conClass = new CongruenceClass();
		conClass.add(l);
		congruenceClasses.put(l, conClass);
		return conClass;
	}

	// process the copy a = b
	// returns whether the a and b can be coalesced
	boolean tryCoalesceCopy(Local a, Local b) {
		CongruenceClass conClassA = getCongruenceClass(a);
		CongruenceClass conClassB = getCongruenceClass(b);

		System.out.println("  Check intersection: " + a + " \u2208 " + conClassA + " vs " + b + " \u2208 " + conClassB + ": ");
		if (conClassA.size() == 1 && conClassB.size() == 1)
			return checkInterfereSingle(conClassA, conClassB);

		if (checkInterfere(conClassA, conClassB)) {
			System.out.println("  => true");
			return false;
		}
		System.out.println("  => false");

		// merge congruence classes
		merge(conClassA, conClassB);
		return true;
	}

	void merge(CongruenceClass conClassA, CongruenceClass conClassB) {
		System.out.println("    pre-merge: " + conClassA);
		conClassA.addAll(conClassB);
		for (Local l : conClassB)
			congruenceClasses.put(l, conClassA);
		System.out.println("    post-merge: " + conClassA);

		for (Local l : conClassA) {
			Local in = equalAncIn.get(l);
			Local out = equalAncOut.get(l);
			if (in != null && out != null)
				equalAncIn.put(l, checkPreDomOrder(in, out) ? in : out);
			else
				equalAncIn.put(l, in != null ? in : out != null ? out : null);
		}
	}

	void applyRemapping(Map<Local, Local> remap) {
		// defuse can be used here to speed things up. TODO
		for(BasicBlock b : cfg.vertices()) {
			for (Iterator<Statement> it = b.iterator(); it.hasNext(); ) {
				Statement stmt = it.next();
				int opcode = stmt.getOpcode();

				if (opcode == -1) {
					ParallelCopyVarStatement copy = (ParallelCopyVarStatement) stmt;
					for (Iterator<CopyPair> it2 = copy.pairs.iterator(); it2.hasNext(); ) {
						CopyPair p = it2.next();
						p.source = remap.getOrDefault(p.source, p.source);
						p.targ = remap.getOrDefault(p.targ, p.targ);
						if (p.source == p.targ)
							it2.remove();
					}
					if (copy.pairs.isEmpty())
						it.remove();
				} else if (opcode == Opcode.LOCAL_STORE || opcode == Opcode.PHI_STORE) {
					AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
					VarExpression v = copy.getVariable();
					v.setLocal(remap.getOrDefault(v.getLocal(), v.getLocal()));
					if (copy.getExpression() instanceof VarExpression && ((VarExpression) copy.getExpression()).getLocal() == v.getLocal())
						it.remove();
				}

				for (Statement s : stmt) {
					if (s.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpression v = (VarExpression) s;
						v.setLocal(remap.getOrDefault(v.getLocal(), v.getLocal()));
					}
				}
			}
		}

		for (Entry<Local, Local> e : remap.entrySet()) {
			System.out.println(e.getKey() + " -> " + e.getValue());
			defuse.defs.remove(e.getKey());
			defuse.uses.remove(e.getKey());
		}
	}

	Map<Local, Local> remap;

	private void coalescePhis() {
		congruenceClasses = new HashMap<>();
		remap = new HashMap<>();

		for (Entry<Local, PhiExpression> e : defuse.phis.entrySet()) {
			Local l1 = e.getKey();
			BasicBlock b = defuse.defs.get(l1);
			// since we are now in csaa, phi locals never interfere and are in the same congruence class.
			// therefore we can coalesce them all together and drop phis. with this, we leave cssa.
			PhiExpression phi = e.getValue();

			CongruenceClass phiConClass = new CongruenceClass();
			phiConClass.add(l1);
			congruenceClasses.put(l1, phiConClass);
//			remap.put(l1, l1);

			for(Expression ex : phi.getArguments().values()) {
				VarExpression v = (VarExpression) ex;
				Local l = v.getLocal();
				phiConClass.add(l);
				congruenceClasses.put(l, phiConClass);
//				remap.put(l, l1);
			}
			System.out.println("phi conclass: " + phiConClass);

			// we can simply drop all the phis without further consideration
			for (Iterator<Statement> it = b.iterator(); it.hasNext();) {
				if (it.next().getOpcode() == Opcode.PHI_STORE)
					it.remove();
				else
					break;
			}
		}

		defuse.phis.clear();
		System.out.println();
	}

	private void coalesceCopies() {
		equalAncIn = new HashMap<>();
		equalAncOut = new HashMap<>();
		// now for each copy check if lhs and rhs congruence classes do not interfere.
		// if they do not interfere merge the conClasses and those two vars can be coalesced. delete the copy.
		for (BasicBlock b : dom_dfs.getPreOrder()) {
			Iterator<Statement> it = b.iterator();
			while (it.hasNext()) {
				Statement stmt = it.next();
				if (stmt.getOpcode() == Opcode.LOCAL_STORE) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					if (copy.getExpression().getOpcode() == Opcode.LOCAL_STORE) {
						Local lhs = copy.getVariable().getLocal();
						Local rhs = ((VarExpression) copy.getExpression()).getLocal();
						if (tryCoalesceCopy(lhs, rhs)) {
//							Local remapLocal = congruenceClasses.get(lhs).first();
//							remap.put(lhs, remapLocal);
//							remap.put(rhs, remapLocal);
							it.remove();
							System.out.println("  => coalesce " + lhs + " = " + rhs);
//							System.out.println("  remap to " + remapLocal);
						}
						System.out.println();
					}
				} else if (stmt.getOpcode() == -1) {
					// we need to do it for each one. if all of the copies are removed then remove the pcvs
					ParallelCopyVarStatement copy = (ParallelCopyVarStatement) stmt;
					System.out.println("p coalesce " + copy);
					for (Iterator<CopyPair> pairIter = copy.pairs.iterator(); pairIter.hasNext(); ) {
						System.out.println();
						CopyPair pair = pairIter.next();
						if (tryCoalesceCopy(pair.targ, pair.source)) {
//							Local remapLocal = congruenceClasses.get(pair.targ).first();
//							remap.put(pair.targ, remapLocal);
//							remap.put(pair.source, remapLocal);
							pairIter.remove();
							System.out.println("  => psub coalesce " + pair.targ + " = " + pair.source);
//							System.out.println("    remap to " + remapLocal);
						}
					}
					if (copy.pairs.isEmpty()) {
						it.remove();
						System.out.println("=> total coalesce");
					}
					System.out.println();
					System.out.println();
				}
			}
		}

		System.out.println("Final congruence classes:");
		for (Entry<Local, CongruenceClass> e : congruenceClasses.entrySet())
			System.out.println(e.getKey() + " => " + e.getValue());
		System.out.println();
		// ok NOW we remap to avoid that double remap issue
		for (Entry<Local, CongruenceClass> e : congruenceClasses.entrySet())
			remap.put(e.getKey(), e.getValue().first());
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
				if (a == c && pred.get(a) != null) {
					if (!pred.containsKey(a))
						throw new IllegalStateException("this shouldn't happen");
					ready.push(a); // just copied, can be overwritten
				}
			}

			Local b = to_do.pop();
			if (values.get(b) != values.get(loc.get(pred.get(b)))) {
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
			return false;
		}

		@Override
		public boolean canChangeLogic() {
			return false;
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