package org.mapleir.ir.cfg;

import static org.mapleir.ir.dot.ControlFlowGraphDecorator.OPT_DEEP;

import java.util.*;
import java.util.Map.Entry;

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
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class BoissinotDestructor implements Liveness<BasicBlock>, Opcode {

	private final ControlFlowGraph cfg;
	
	private SSADefUseMap defuse;
	private DominanceLivenessAnalyser resolver;
	private Map<Local, Local> values;
	
	// delete me
	private final Set<Local> localsTest = new HashSet<>();
	
	public BoissinotDestructor(ControlFlowGraph cfg) {
		this.cfg = cfg;
		
		defuse = new SSADefUseMap(cfg, false);
		values = new HashMap<>();
		
		init();
		insert_copies();
		verify();
		
		BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
		DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, cfg);
		writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
			.setName("after-insert")
			.export();
		
		SSABlockLivenessAnalyser live = new SSABlockLivenessAnalyser(cfg);
		for (BasicBlock b : cfg.vertices()) {
			for (Statement stmt : b) {
				if(stmt.getOpcode() == Opcode.LOCAL_LOAD) {
					localsTest.add(((VarExpression) stmt).getLocal());
				}
			}
		}
		
		System.out.println("defs:");
		for(Entry<Local, BasicBlock> e : defuse.defs.entrySet()) {
			System.out.println(e.getKey() + " in " + e.getValue().getId());
		}
		
		System.out.println("PHIS: " + defuse.phis);
		
		localsTest.addAll(defuse.phis);
		localsTest.addAll(defuse.uses.keySet());
		localsTest.addAll(defuse.defs.keySet());
		
		resolver = new DominanceLivenessAnalyser(cfg, defuse);

		writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.add("liveness", new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(this))
				.setName("liveness-baguette")
				.export();
		writer.removeAll()
				.add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.add(new LivenessDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>>().setLiveness(live))
				.setName("liveness-bibl")
				.export();
		
		coalesce();
		
		compute_value_interference();
		
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

	HashMap<BasicBlock, Integer> preDfsDomOrder;

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

		ExtendedDfs dom_dfs = new ExtendedDfs(dom_tree, cfg.getEntries().iterator().next(), ExtendedDfs.POST | ExtendedDfs.PRE);

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
						values.put(b, values.get(a));
					} else {
						values.put(b, b);
					}
				} else if (stmt instanceof ParallelCopyVarStatement) {
					ParallelCopyVarStatement copy = (ParallelCopyVarStatement) stmt;
					for (CopyPair p : copy.pairs) {
						Local l = p.targ;
						values.put(l, values.get(p.source));
					}
				}
			}
		}

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

	boolean checkIntersect(List<Local> red, List<Local> blue) {
		Stack<Local> dom = new Stack<>();
		int ir = 0;
		int ib = 0;
		while (ir < red.size() || ib < blue.size()) {
			Local current;
			if (ir == red.size() || (ir < red.size() && ib < blue.size() && preDfsDomOrder.get(defuse.defs.get(blue.get(ib))) < preDfsDomOrder.get(defuse.defs.get(red.get(ir)))))
				current = blue.get(ib++);
			else
				current = red.get(ir++);

			Local other = dom.isEmpty() ? null : dom.peek();
			while (other != null && !doms(other, current)) {
				dom.pop();
				other = dom.isEmpty() ? null : dom.peek();
			}
			Local parent = other;
			if (parent != null && interference(current, parent))
				return true;
			dom.push(current);
		}
		return false;
	}

	boolean interference(Local a, Local b) {
		// how the f--k do you compute equal intersecting ancestor?????
		return false;
	}
	
	void verify() {
		Map<Local, BasicBlock> defs = new HashMap<>();
		NullPermeableHashMap<Local, Set<BasicBlock>> uses = new NullPermeableHashMap<>(new SetCreator<>());
		
		for (BasicBlock b : cfg.vertices()) {
			for (Statement stmt : b) {
				if(stmt instanceof ParallelCopyVarStatement) {
					ParallelCopyVarStatement pcopy = (ParallelCopyVarStatement) stmt;
					for(CopyPair p : pcopy.pairs) {
						System.out.println("def " + p.targ + " = " + p.source + "  in " + b.getId());
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
					PhiExpression phi = (PhiExpression) copy.getExpression();
					
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
					
					defuse.phis.add(copy.getVariable().getLocal());
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
			PhiExpression phi = (PhiExpression) copy.getExpression();
			
			// for every xi arg of the phi from pred Li, add it to the worklist
			// so that we can parallelise the copy when we insert it.
			for(Entry<BasicBlock, Expression> e : phi.getArguments().entrySet()) {
				BasicBlock h = e.getKey();
				VarExpression v = (VarExpression) e.getValue();
				PhiRes r = new PhiRes(copy.getVariable().getLocal(), phi, h, v.getLocal(), v.getType());
				wl.getNonNull(h).add(r);
			}
			
			// for each x0, where x0 is a phi copy target, create a new
			// variable z0 for a copy z0 = x0 and replace the phi
			// copy target to z0.
			Local x0 = copy.getVariable().getLocal();
			Local z0 = cfg.getLocals().makeLatestVersion(x0);
			dst_copy.pairs.add(new CopyPair(x0, z0)); // x0 = z0
			copy.getVariable().setLocal(z0); // z0 = phi(...)
			
			// both defined and used in this block.
			defuse.defs.put(x0, b);
			defuse.defs.put(z0, b);
			defuse.uses.getNonNull(x0).add(b);
			defuse.uses.getNonNull(z0).add(b);
			
			defuse.phis.remove(x0);
			defuse.phis.add(z0);
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
				copy.pairs.add(new CopyPair(zi, xi));
				
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
			Statement stmt = b.get(0);
			while(stmt.getOpcode() == Opcode.PHI_STORE) {
				stmt = b.get(++i);
			}
			
			b.add(b.indexOf(stmt), copy);
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
	
	void coalesce() {
		// since we are now in csaa, all phi locals
		//  can we coalesced into the same var.
		
		Map<Local, Local> remap = new HashMap<>();
		for(BasicBlock b : cfg.vertices()) {
			Iterator<Statement> it = b.iterator();
			while(it.hasNext()) {
				Statement stmt = it.next();
				if(stmt.getOpcode() == Opcode.PHI_STORE) {
					CopyPhiStatement copy = (CopyPhiStatement) stmt;
					PhiExpression phi = copy.getExpression();
					
					Local l1 = copy.getVariable().getLocal();
					Local newL = cfg.getLocals().makeLatestVersion(l1);
					remap.put(l1, newL);
					
					for(Expression ex : phi.getArguments().values()) {
						VarExpression v = (VarExpression) ex;
						Local l = v.getLocal();
						remap.put(l, newL);
					}
					
					it.remove();
				}
			}
		}
		
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