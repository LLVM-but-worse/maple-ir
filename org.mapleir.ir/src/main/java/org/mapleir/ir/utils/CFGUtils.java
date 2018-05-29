package org.mapleir.ir.utils;

import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.flowgraph.edges.*;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.SwitchStmt;
import org.mapleir.ir.code.stmt.ThrowStmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.objectweb.asm.tree.LabelNode;

import java.util.HashSet;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class CFGUtils {
	/**
	 * Split the block upto statement index `to`, exclusively, into a new block.
	 * @return the newly created block containing the instructions before `to`
	 */
	public static BasicBlock splitBlock(ControlFlowGraph cfg, int newId, BasicBlock b, int to) {
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

		// split block
		BasicBlock newBlock = splitBlockSimple(cfg, newId, b, to);

		// redo ranges
		for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			if (er.containsVertex(b))
				er.addVertexBefore(b, newBlock);
		}

		// redirect b preds into newBlock and remove them.
		Set<FlowEdge<BasicBlock>> oldEdges = new HashSet<>(cfg.getReverseEdges(b));
		for (FlowEdge<BasicBlock> e : oldEdges) {
			BasicBlock p = e.src();
			FlowEdge<BasicBlock> c;
			if (e instanceof TryCatchEdge) { // b is ehandler
				TryCatchEdge<BasicBlock> tce = (TryCatchEdge<BasicBlock>) e;
				if (tce.dst() != tce.erange.getHandler()) {
					// System.err.println(builder.method.owner + "#" + builder.method.name);
					System.err.println(cfg);
					System.err.println("Very odd split case. please investigate");
					System.err.println("Offending postsplit block: " + b);
					System.err.println("Offending newblock: " + newBlock);
					System.err.println("Offending edge: " + tce);
					System.err.println("Offending erange: " + tce.erange);
				}
				if (tce.erange.getHandler() != newBlock) {
					tce.erange.setHandler(newBlock);
					cfg.addEdge(tce.src(), tce.clone(tce.src(), null));
					cfg.removeEdge(tce.src(), tce);
				}
			} else {
				c = e.clone(p, newBlock);
				cfg.addEdge(p, c);
				cfg.removeEdge(p, e);
			}

			// Fix flow instruction targets
			if (!p.isEmpty()) {
				Stmt last = p.get(p.size() - 1);
				int op = last.getOpcode();
				if (e instanceof ConditionalJumpEdge) {
					if (op != Opcode.COND_JUMP)
						throw new IllegalArgumentException("wrong flow instruction");
					ConditionalJumpStmt j = (ConditionalJumpStmt) last;
//					assertTarget(last, j.getTrueSuccessor(), b);
					if (j.getTrueSuccessor() == b)
						j.setTrueSuccessor(newBlock);
				} else if (e instanceof UnconditionalJumpEdge) {
					if (op != Opcode.UNCOND_JUMP)
						throw new IllegalArgumentException("wrong flow instruction, got " + last.getOpname() + " instead");
					UnconditionalJumpStmt j = (UnconditionalJumpStmt) last;
					BasicBlock t = j.getTarget();
					if(t != b) {
						System.err.println(cfg);
						System.err.println(j.getBlock());
						throw new IllegalStateException(j + ", "+ t.getDisplayName() + " != " + b.getDisplayName());
					}
					j.setTarget(newBlock);
				} else if (e instanceof SwitchEdge) {
					if (op != Opcode.SWITCH_JUMP)
						throw new IllegalArgumentException("wrong flow instruction.");
					SwitchStmt s = (SwitchStmt) last;
					for (Map.Entry<Integer, BasicBlock> en : s.getTargets().entrySet()) {
						BasicBlock t = en.getValue();
						if (t == b) {
							en.setValue(newBlock);
						}
					}
				}
			}
		}


		// if (!checkCloneHandler(newBlock)) {
		// 	System.err.println(cfg);
		// 	System.err.println(newBlock.getDisplayName());
		// 	System.err.println(b.getDisplayName());
		// 	throw new IllegalStateException("the new block should always need a handler..?");
		// }

		// clone exception edges
		for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
			if (e.getType() == FlowEdges.TRYCATCH) {
				TryCatchEdge<BasicBlock> c = ((TryCatchEdge<BasicBlock>) e).clone(newBlock, null); // second param is discarded (?)
				cfg.addEdge(newBlock, c);
			}
		}

		// create immediate to newBlock
		cfg.addEdge(newBlock, new ImmediateEdge<>(newBlock, b));

		return newBlock;
	}

	private static boolean checkCloneHandler(BasicBlock b) {
		if (b.isEmpty())
			throw new IllegalArgumentException("empty block after split?");
		// backwards iteration is faster
		for (ListIterator<Stmt> it = b.listIterator(b.size()); it.hasPrevious(); ) {
			if (checkCloneHandler(it.previous()))
				return true;
		}
		return false;
	}

	private static boolean checkCloneHandler(Stmt stmt) {
		if (stmt instanceof CopyVarStmt) {
			CopyVarStmt copy = (CopyVarStmt) stmt;
			int opc = copy.getExpression().getOpcode();
			if (!copy.isSynthetic() && opc != Opcode.LOCAL_LOAD && opc != Opcode.CATCH)
				return true;
		} else if (stmt.canChangeFlow()) {
			if (stmt instanceof ThrowStmt)
				return true;
			// for ssagenpass, no need to check child exprs as no complex subexprs can occur before propagation.
			// otherwise, we do need to check.
		} else {
			return true;
		}
		return false;
	}

	// this is such a f--king kludge
	public static int getMaxId(ControlFlowGraph cfg) {
		return cfg.vertices().stream().map(BasicBlock::getNumericId).reduce(Integer::max).orElse(0);
	}

	/**
	 * Splits block up to index `to`, exclusively. Doesn't update edges, etc.
	 * @return The new block, containing the split-off instructions
	 */
	public static BasicBlock splitBlockSimple(ControlFlowGraph cfg, int newId, BasicBlock b, int to) {
		BasicBlock newBlock = new BasicBlock(cfg, newId, new LabelNode());
		cfg.addVertex(newBlock);
		b.transferUp(newBlock, to);
		return newBlock;
	}
}
