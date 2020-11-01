package org.mapleir.ir.algorithms;

import org.mapleir.flowgraph.edges.DefaultSwitchEdge;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.flowgraph.edges.SwitchEdge;
import org.mapleir.flowgraph.edges.UnconditionalJumpEdge;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.SwitchStmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.codegen.BytecodeFrontend;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.impl.VersionedLocal;
import org.mapleir.ir.utils.CFGUtils;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.collections.graph.algorithms.SimpleDfs;
import org.mapleir.stdlib.collections.map.ListCreator;
import org.mapleir.stdlib.collections.map.NullPermeableHashMap;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.*;
import java.util.Map.Entry;

/**
 * A dank SSA destructor that translates out of SSA by literally evaluating phi statements, lol
 * First we go to CSSA (based on Boissinot), then we EVALUATE PHIs, then we relabel all variables
 *
 * If you use this, it's gonna fuck up the stack map frames, so you have to use -noverify (lmao)
 */

@SuppressWarnings("DuplicatedCode")
public class TrollDestructor {
	public static void leaveSSA(ControlFlowGraph cfg) {
		new TrollDestructor(cfg);
	}

	private final ControlFlowGraph cfg;
	private final LocalsPool locals;
	private final BasicBlock entry;

	private final DominanceLivenessAnalyser resolver;
	private final SimpleDfs<BasicBlock> dom_dfs;
	private final SSADefUseMap defuse;

	private final Map<Local, CongruenceClass> congruenceClasses;
	private final Map<Local, Local> remap;

	private TrollDestructor(ControlFlowGraph cfg) {
		this.cfg = cfg;
		locals = cfg.getLocals();

		congruenceClasses = new HashMap<>();
		remap = new HashMap<>();

		// 1. Insert copies to enter CSSA.
		liftPhiOperands();

		entry = CFGUtils.deleteUnreachableBlocks(cfg);

		copyPhiOperands();

		resolver = new DominanceLivenessAnalyser(cfg, entry, null);

		dom_dfs = traverseDominatorTree();
		defuse = createDuChains();
		resolver.setDefuse(defuse);

		coalescePhisStupidly();

		remapLocals();
		applyRemapping();

		sequentialize();
	}

	private void liftPhiOperands() {
		for (BasicBlock b : cfg.vertices()) {
			for (Stmt stmt : new ArrayList<>(b)) {
				if (stmt.getOpcode() == Opcode.PHI_STORE) {
					CopyPhiStmt copy = (CopyPhiStmt) stmt;
					for (Entry<BasicBlock, Expr> e : copy.getExpression().getArguments().entrySet()) {
						Expr expr = e.getValue();
						int opcode = expr.getOpcode();
						if (opcode == Opcode.CONST_LOAD || opcode == Opcode.CATCH) {
							VersionedLocal vl = locals.makeLatestVersion(locals.get(0, false));
							CopyVarStmt cvs = new CopyVarStmt(new VarExpr(vl, expr.getType()), expr);
							e.setValue(new VarExpr(vl, expr.getType()));

							insertEnd(e.getKey(), cvs);
						} else if (opcode != Opcode.LOCAL_LOAD) {
							throw new IllegalArgumentException("Non-variable expression in phi: " + copy);
						}
					}
				}
			}
		}
	}

	private void copyPhiOperands() {
		for (BasicBlock b : cfg.vertices()) {
			if (b != entry) {
				copyPhiOperands(b);
			}
		}
	}

	private void copyPhiOperands(BasicBlock b) {
		NullPermeableHashMap<BasicBlock, List<PhiRes>> wl = new NullPermeableHashMap<>(new ListCreator<>());
		ParallelCopyVarStmt dst_copy = new ParallelCopyVarStmt();

		// given a phi: L0: x0 = phi(L1:x1, L2:x2)
		// insert the copies:
		// L0: x0 = x3 (at the end of L0)
		// L1: x4 = x1
		// L2: x5 = x2
		// and change the phi to:
		// x3 = phi(L1:x4, L2:x5)

		for (Stmt stmt : b) {
			// phis only appear at the start of a block.
			if (stmt.getOpcode() != Opcode.PHI_STORE) {
				break;
			}

			CopyPhiStmt copy = (CopyPhiStmt) stmt;
			PhiExpr phi = copy.getExpression();

			// for every xi arg of the phi from pred Li, add it to the worklist
			// so that we can parallelise the copy when we insert it.
			for (Entry<BasicBlock, Expr> e : phi.getArguments().entrySet()) {
				BasicBlock h = e.getKey();
				// these are validated in init().
				VarExpr v = (VarExpr) e.getValue();
				PhiRes r = new PhiRes(copy.getVariable().getLocal(), phi, h, v.getLocal(), v.getType());
				wl.getNonNull(h).add(r);
			}

			// for each x0, where x0 is a phi copy target, create a new variable z0 for
			// a copy x0 = z0 and replace the phi copy target to z0.
			Local x0 = copy.getVariable().getLocal();
			Local z0 = locals.makeLatestVersion(x0);
			dst_copy.pairs.add(new CopyPair(x0, z0, copy.getVariable().getType())); // x0 = z0
			copy.getVariable().setLocal(z0); // z0 = phi(...)
		}

		// resolve
		if (dst_copy.pairs.size() > 0)
			insertStart(b, dst_copy);

		for (Entry<BasicBlock, List<PhiRes>> e : wl.entrySet()) {
			BasicBlock p = e.getKey();
			ParallelCopyVarStmt copy = new ParallelCopyVarStmt();

			for (PhiRes r : e.getValue()) {
				// for each xi source in a phi, create a new variable zi, and insert the copy
				// zi = xi in the pred Li. then replace the phi arg from Li with zi.

				Local xi = r.l;
				Local zi = locals.makeLatestVersion(xi);
				copy.pairs.add(new CopyPair(zi, xi, r.type));

				// we consider phi args to be used in the pred instead of the block
				// where the phi is, so we need to update the def/use maps here.
				r.phi.setArgument(r.pred, new VarExpr(zi, r.type));
			}

			insertEnd(p, copy);
		}
	}

	private void insertStart(BasicBlock b, Stmt copy) {
		if (b.isEmpty()) {
			b.add(copy);
		} else {
			// insert after phi.
			int i;
			for (i = 0; i < b.size() && b.get(i).getOpcode() == Opcode.PHI_STORE; i++)
				; // skipping ptr.
			b.add(i, copy);
		}
	}

	private void insertEnd(BasicBlock b, Stmt copy) {
		if (b.isEmpty()) {
			b.add(copy);
		} else {
			int pos = b.size() - 1;
			if (b.get(pos).canChangeFlow()) {
				b.add(pos, copy);
			} else {
				b.add(copy);
			}
		}
	}

	private SimpleDfs<BasicBlock> traverseDominatorTree() {
		return new SimpleDfs<>(resolver.domc.getDominatorTree(), entry, SimpleDfs.PRE | SimpleDfs.TOPO);
	}

	private SSADefUseMap createDuChains() {
		SSADefUseMap defuse = new SSADefUseMap(cfg) {
			@Override
			protected void build(BasicBlock b, Stmt stmt, Set<Local> usedLocals) {
				if (stmt instanceof ParallelCopyVarStmt) {
					ParallelCopyVarStmt copy = (ParallelCopyVarStmt) stmt;
					for (CopyPair pair : copy.pairs) {
						defs.put(pair.targ, b);
						uses.getNonNull(pair.source).add(b);
					}
				} else {
					super.build(b, stmt, usedLocals);
				}
			}

			@Override
			protected void buildIndex(BasicBlock b, Stmt stmt, int index, Set<Local> usedLocals) {
				if (stmt instanceof ParallelCopyVarStmt) {
					ParallelCopyVarStmt copy = (ParallelCopyVarStmt) stmt;
					for (CopyPair pair : copy.pairs) {
						defIndex.put(pair.targ, index);
						lastUseIndex.getNonNull(pair.source).put(b, index);
					}
				} else {
					super.buildIndex(b, stmt, index, usedLocals);
				}
				return;
			}
		};
		defuse.computeWithIndices(dom_dfs.getPreOrder());
		return defuse;
	}

	private void coalescePhisStupidly() {
		Random random = new Random();

		// so, instead of opting for the sane algorithm of flattening pccs and dropping phis, we are going to
		// literally evaluate the phi statements by checking which block we came from.
		Local predLocal = locals.makeLatestVersion(locals.getNextFreeLocal(false));
		VarExpr predVar = new VarExpr(predLocal, Type.INT_TYPE);
		Map<BasicBlock, Integer> dispatchSrcKeys = new HashMap<>();

		for (BasicBlock b : cfg.vertices()) {
			int dispatchSrcKey = b.hashCode() + random.nextInt();
			dispatchSrcKeys.put(b, dispatchSrcKey);
			Stmt newCopy = new CopyVarStmt(predVar.copy(), new ConstantExpr(dispatchSrcKey));
			if(b.get(b.size() - 1).canChangeFlow())
				b.add(b.size() - 1, newCopy);
			else
				b.add(newCopy);
		}

		BasicBlock phiDispatchBlock = new BasicBlock(cfg);
		cfg.addVertex(phiDispatchBlock);

		LinkedHashMap<Integer, BasicBlock> dispatcherDsts = new LinkedHashMap<>();
		phiDispatchBlock.add(new SwitchStmt(predVar.copy(), dispatcherDsts, phiDispatchBlock));
		cfg.addEdge(new DefaultSwitchEdge<>(phiDispatchBlock, phiDispatchBlock));

		// expand phis into switches based on predecessor (LOL)
		for (BasicBlock b : new ArrayList<>(cfg.vertices())) {
			NullPermeableHashMap<BasicBlock, Integer> dispatchDstKeys = new NullPermeableHashMap<>(dispatchSrcKeys::get);

			for (ListIterator<Stmt> it = b.listIterator(); it.hasNext(); ) {
				Stmt stmt = it.next();
				if (stmt instanceof CopyPhiStmt) {
					it.remove(); // delete
					CopyPhiStmt phi = (CopyPhiStmt) stmt;

					// split off the top to insert our call to the dispatcher
					BasicBlock splitBlock = CFGUtils.splitBlock(cfg, b, it.previousIndex());
					Set<FlowEdge<BasicBlock>> splitEdges = cfg.getEdges(splitBlock);
					assert (splitEdges.size() == 1);
					cfg.removeEdge(splitEdges.iterator().next());
					int phiStmtKey = random.nextInt();
					// This calculation is implemented in code below. It's a shitty lfsr
					Expr dynamicDispatchKeyExpr =
						new ArithmeticExpr(
								new ArithmeticExpr(
										new ConstantExpr(5),
										predVar.copy(),
										ArithmeticExpr.Operator.SHL
								),
								new ConstantExpr(phiStmtKey),
								ArithmeticExpr.Operator.XOR
						);
					splitBlock.add(new CopyVarStmt(predVar.copy(), dynamicDispatchKeyExpr));
					splitBlock.add(new UnconditionalJumpStmt(phiDispatchBlock));
					cfg.addEdge(new UnconditionalJumpEdge<>(splitBlock, phiDispatchBlock));

					for (Entry<BasicBlock, Expr> phiArg : phi.getExpression().getArguments().entrySet()) {
						// make a stub block to do the copy implementing that phi expr
						BasicBlock stubBlock = new BasicBlock(cfg);
						// i don't think it's necessary to copy phi arg expression since we
						// are just reassigning it to a different block. tricky!
						stubBlock.add(new CopyVarStmt(phi.getVariable().copy(), phiArg.getValue()));
						stubBlock.add(new UnconditionalJumpStmt(b));
						cfg.addEdge(new UnconditionalJumpEdge<>(stubBlock, b));

						// Calculation for the dynamic IR above
						int dispatchDstKey = (dispatchDstKeys.getNonNull(phiArg.getKey()) << 5) ^ phiStmtKey;
						dispatchDstKeys.put(phiArg.getKey(), dispatchDstKey);
						System.out.printf("block = %s, phi_src = %s, stub_block = %s, split_block = %s, id = 0x%08x\n", b, phiArg.getKey(), stubBlock, splitBlock, dispatchDstKey);
						assert !dispatcherDsts.containsKey(dispatchDstKey);

						dispatcherDsts.put(dispatchDstKey, stubBlock);
						cfg.addEdge(new SwitchEdge<>(phiDispatchBlock, stubBlock, dispatchDstKey));
					}
				} else break;
			}
		}

		defuse.phiDefs.clear();
	}

	private void remapLocals() {
		// ok NOW we remap to avoid that double remap issue
		for (Entry<Local, CongruenceClass> e : congruenceClasses.entrySet()) {
			remap.put(e.getKey(), e.getValue().first());
		}

		// Flatten pccs into one variable through remapping
		// System.out.println("remap:");
		Map<Local, Local> remap = new HashMap<>();
		for (Entry<Local, CongruenceClass> entry : congruenceClasses.entrySet()) {
			CongruenceClass pcc = entry.getValue();
			if (pcc.isEmpty())
				continue;

			Local local = entry.getKey();
			if (remap.containsKey(local))
				continue;

			Local newLocal = locals.get(locals.getMaxLocals() + 1, 0, false);
			// System.out.println("  " + local + " -> " + newLocal);
			remap.put(local, newLocal);
			for (Local pccLocal : pcc) {
				if (remap.containsKey(pccLocal))
					continue;
				newLocal = locals.get(locals.getMaxLocals() + 1, 0, false);
				remap.put(pccLocal, newLocal);
				// System.out.println("  " + pccLocal + " -> " + newLocal);
			}
		}
		System.out.println();
	}

	// Flatten ccs so that each local in each cc is replaced with a new representative local.
	private void applyRemapping() {
		GenericBitSet<BasicBlock> processed = cfg.createBitSet();
		GenericBitSet<BasicBlock> processed2 = cfg.createBitSet();
		for (Local e : remap.keySet()) {
			for (BasicBlock used : defuse.uses.getNonNull(e)) {
				if (processed.contains(used))
					continue;
				processed.add(used);
				for (Stmt stmt : used) {
					for (Expr s : stmt.enumerateOnlyChildren()) {
						if (s.getOpcode() == Opcode.LOCAL_LOAD) {
							VarExpr v = (VarExpr) s;
							v.setLocal(remap.getOrDefault(v.getLocal(), v.getLocal()));
						}
					}
				}
			}
			BasicBlock b = defuse.defs.get(e);
			if (processed2.contains(b))
				continue;
			processed2.add(b);

			for (Iterator<Stmt> it = b.iterator(); it.hasNext();) {
				Stmt stmt = it.next();
				if (stmt instanceof ParallelCopyVarStmt) {
					ParallelCopyVarStmt copy = (ParallelCopyVarStmt) stmt;
					for (Iterator<CopyPair> it2 = copy.pairs.iterator(); it2.hasNext();) {
						CopyPair p = it2.next();
						p.source = remap.getOrDefault(p.source, p.source);
						p.targ = remap.getOrDefault(p.targ, p.targ);
						if (p.source == p.targ)
							it2.remove();
					}
					if (copy.pairs.isEmpty())
						it.remove();
				} else if (stmt instanceof CopyVarStmt) {
					AbstractCopyStmt copy = (AbstractCopyStmt) stmt;
					VarExpr v = copy.getVariable();
					v.setLocal(remap.getOrDefault(v.getLocal(), v.getLocal()));
					if (!copy.isSynthetic() && copy.getExpression().getOpcode() == Opcode.LOCAL_LOAD)
						if (((VarExpr) copy.getExpression()).getLocal() == v.getLocal())
							it.remove();
				} else if (stmt instanceof CopyPhiStmt) {
					throw new IllegalArgumentException("Phi copy still in block?");
				}
			}
		}
		for (Local e : remap.keySet()) {
			defuse.defs.remove(e);
			defuse.uses.remove(e);
		}
	}

	private void sequentialize() {
		for (BasicBlock b : cfg.vertices())
			sequentialize(b);
	}

	private void sequentialize(BasicBlock b) {
		// TODO: just rebuild the instruction list
		LinkedHashMap<ParallelCopyVarStmt, Integer> p = new LinkedHashMap<>();
		for (int i = 0; i < b.size(); i++) {
			Stmt stmt = b.get(i);
			if (stmt instanceof ParallelCopyVarStmt)
				p.put((ParallelCopyVarStmt) stmt, i);
		}

		if (p.isEmpty())
			return;
		int indexOffset = 0;
		Local spill = locals.makeLatestVersion(p.entrySet().iterator().next().getKey().pairs.get(0).targ);
		for (Entry<ParallelCopyVarStmt, Integer> e : p.entrySet()) {
			ParallelCopyVarStmt pcvs = e.getKey();
			int index = e.getValue();
			if (pcvs.pairs.size() == 0)
				throw new IllegalArgumentException("pcvs is empty");
			else if (pcvs.pairs.size() == 1) { // constant sequentialize for trivial parallel copies
				CopyPair pair = pcvs.pairs.get(0);
				CopyVarStmt newCopy = new CopyVarStmt(new VarExpr(pair.targ, pair.type),
						new VarExpr(pair.source, pair.type));
				b.set(index + indexOffset, newCopy);
			} else {
				List<CopyVarStmt> sequentialized = pcvs.sequentialize(spill);
				b.remove(index + indexOffset--);
				for (CopyVarStmt cvs : sequentialized) { // warning: O(N^2) operation
					b.add(index + ++indexOffset, cvs);
				}
			}
		}
	}

	private class PhiRes {
		final Local target;
		final PhiExpr phi;
		final BasicBlock pred;
		final Local l;
		final Type type;

		PhiRes(Local target, PhiExpr phi, BasicBlock src, Local l, Type type) {
			this.target = target;
			this.phi = phi;
			pred = src;
			this.l = l;
			this.type = type;
		}

		@Override
		public String toString() {
			return String.format("targ=%s, p=%s, l=%s(type=%s)", target, pred, l, type);
		}
	}

	private class CopyPair {
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
			return targ + " =P= " + source + " (" + type + ")";
		}
	}

	private class ParallelCopyVarStmt extends Stmt {
		public static final int PARALLEL_STORE = CLASS_RESERVED | CLASS_STORE | 0x1;
		final List<CopyPair> pairs;

		ParallelCopyVarStmt() {
			super(PARALLEL_STORE);
			pairs = new ArrayList<>();
		}

		ParallelCopyVarStmt(List<CopyPair> pairs) {
			super(PARALLEL_STORE);
			this.pairs = pairs;
		}

		@Override
		public void onChildUpdated(int ptr) {
		}

		@Override
		public void toString(TabbedStringWriter printer) {
			printer.print("PARALLEL(");

			for (Iterator<CopyPair> it = pairs.iterator(); it.hasNext();) {
				CopyPair p = it.next();
				printer.print(p.targ.toString());
				// printer.print("(" + p.type + ")");

				if (it.hasNext()) {
					printer.print(", ");
				}
			}

			printer.print(") = (");

			for (Iterator<CopyPair> it = pairs.iterator(); it.hasNext();) {
				CopyPair p = it.next();
				printer.print(p.source.toString());
				// printer.print("(" + p.type + ")");

				if (it.hasNext()) {
					printer.print(", ");
				}
			}

			printer.print(")");
		}

		private List<CopyVarStmt> sequentialize(Local spill) {
			Stack<Local> ready = new Stack<>();
			Stack<Local> to_do = new Stack<>();
			Map<Local, Local> loc = new HashMap<>();
			Map<Local, Local> pred = new HashMap<>();
			Map<Local, Type> types = new HashMap<>();
			pred.put(spill, null);

			for (CopyPair pair : pairs) { // initialization
				loc.put(pair.targ, null);
				loc.put(pair.source, null);
				types.put(pair.targ, pair.type);
				types.put(pair.source, pair.type);
			}

			for (CopyPair pair : pairs) {
				loc.put(pair.source, pair.source); // needed and not copied yet
				pred.put(pair.targ, pair.source); // unique predecessor
				to_do.push(pair.targ); // copy into b to be done
			}

			for (CopyPair pair : pairs) {
				if (!loc.containsKey(pair.targ))
					throw new IllegalStateException("this shouldn't happen");
				if (loc.get(pair.targ) == null) // b is not used and can be overwritten
					ready.push(pair.targ);
			}

			List<CopyVarStmt> result = new ArrayList<>();
			while (!to_do.isEmpty()) {
				while (!ready.isEmpty()) {
					Local b = ready.pop(); // pick a free location
					Local a = pred.get(b); // available in c
					Local c = loc.get(a);
					if ((!types.containsKey(b) && b != spill) || (!types.containsKey(c) && c != spill))
						throw new IllegalStateException("this shouldn't happen " + b + " " + c);

					VarExpr varB = new VarExpr(b, types.get(b)); // generate the copy b = c
					VarExpr varC = new VarExpr(c, types.get(b));
					result.add(new CopyVarStmt(varB, varC));

					loc.put(a, b);
					if (a == c && pred.get(a) != null) {
						if (!pred.containsKey(a))
							throw new IllegalStateException("this shouldn't happen");
						ready.push(a); // just copied, can be overwritten
					}
				}

				Local b = to_do.pop();
				if (b != loc.get(pred.get(b))) {
					if (!types.containsKey(b))
						throw new IllegalStateException("this shouldn't happen");
					VarExpr varN = new VarExpr(spill, types.get(b)); // generate the copy n = b
					VarExpr varB = new VarExpr(b, types.get(b));
					result.add(new CopyVarStmt(varN, varB));
					loc.put(b, spill);
					ready.push(b);
				}
			}

			return result;
		}

		@Override
		public void toCode(MethodVisitor visitor, BytecodeFrontend assembler) {
			throw new UnsupportedOperationException("Synthetic");
		}

		@Override
		public boolean canChangeFlow() {
			return false;
		}

		@Override
		public ParallelCopyVarStmt copy() {
			return new ParallelCopyVarStmt(new ArrayList<>(pairs));
		}

		@Override
		public boolean equivalent(CodeUnit s) {
			return s instanceof ParallelCopyVarStmt && ((ParallelCopyVarStmt) s).pairs.equals(pairs);
		}
	}

	private class CongruenceClass extends TreeSet<Local> {
		private static final long serialVersionUID = -4472334406997712498L;

		CongruenceClass() {
			super((o1, o2) -> {
				if (o1 == o2)
					return 0;
				return ((defuse.defIndex.get(o1) - defuse.defIndex.get(o2))) >> 31 | 1;
			});
		}
	}

	// Used for dumping the dominator graph
	// private static <N extends FastGraphVertex> void dumpGraph(FastDirectedGraph<N, FastGraphEdge<N>> g, String name) {
    //     try {
	// 		Exporter.fromGraph(GraphUtils.makeDotSkeleton(g)).export(new File("cfg testing", name + ".png"));
	// 	} catch (IOException e) {
	// 		e.printStackTrace();
	// 	}
    // }
}
