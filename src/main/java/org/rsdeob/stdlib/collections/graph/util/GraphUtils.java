package org.rsdeob.stdlib.collections.graph.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.DefaultSwitchEdge;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.JumpEdge;
import org.rsdeob.stdlib.cfg.edge.SwitchEdge;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.cfg.util.LabelHelper;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator.SuperNode;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;

public class GraphUtils {

	public static final Comparator<BasicBlock> BLOCK_COMPARATOR = new Comparator<BasicBlock>() {
		@Override
		public int compare(BasicBlock o1, BasicBlock o2) {
			return o1.getId().compareTo(o2.getId());
		}
	};
	public static final Predicate<FlowEdge<?>> ACCEPT_ALL_EDGES = new Predicate<FlowEdge<?>>() {
		@Override
		public boolean test(FlowEdge<?> t) {
			return false;
		}
	};
	
	public static boolean isFlowBlock(BasicBlock b) {
		AbstractInsnNode last = b.last();
		if(last == null) {
			return false;
		}
		switch(last.type()) {
			case AbstractInsnNode.JUMP_INSN:
			case AbstractInsnNode.LOOKUPSWITCH_INSN:
			case AbstractInsnNode.TABLESWITCH_INSN:
				return true;
			default:
				return false;
		}
	}
	
	public static String toBlockArray(Collection<BasicBlock> col) {
		return toBlockArray(col, true);
	}
	
	public static String toBlockArray(Collection<BasicBlock> col, boolean b1) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		Iterator<BasicBlock> it = col.iterator();
		while(it.hasNext()) {
			BasicBlock b = it.next();
			sb.append(b.getId());
			
			if(b1) {
				sb.append(" ").append(b.getLabel().getLabel().hashCode());
			}
			
			if(it.hasNext()) {
				sb.append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
	public static String toSuperNodeArray(Collection<SuperNode> col) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		Iterator<SuperNode> it = col.iterator();
		while(it.hasNext()) {
			SuperNode b = it.next();
			sb.append(b.entry.getId());
			
			if(it.hasNext()) {
				sb.append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
	public static boolean isExitOpcode(int opcode) {
		switch(opcode) {
			// ignore these as they stop control flow
			case Opcodes.RET:
			case Opcodes.ATHROW:
			case Opcodes.RETURN:
			case Opcodes.IRETURN:
			case Opcodes.LRETURN:
			case Opcodes.FRETURN:
			case Opcodes.DRETURN:
			case Opcodes.ARETURN: {
				return true;
			}
			default: {
				return false;
			}
		}
	}

	public static List<BasicBlock> collectSuccessors(ControlFlowGraph cfg, Collection<BasicBlock> blocks, BasicBlock block) {
		if(!cfg.containsVertex(block)) {
			System.out.println(cfg.vertices());
			throw new IllegalStateException("no block for " + cfg.getMethod() + " " + block);
		}
		List<BasicBlock> list = new ArrayList<BasicBlock>();
		for(FlowEdge<BasicBlock> e : cfg.getEdges(block)) {
			if(blocks.contains(e.dst) && !list.contains(e.dst)) {
				list.add(e.dst);
			}
		}
		return list;
	}

	public static List<BasicBlock> collectSuccessors(ControlFlowGraph cfg, BasicBlock block) {
		if(!cfg.containsVertex(block)) {
			System.out.println(cfg.vertices());
			throw new IllegalStateException("no block for " + cfg.getMethod() + " " + block);
		}
		List<BasicBlock> list = new ArrayList<BasicBlock>();
		for(FlowEdge<BasicBlock> e : cfg.getEdges(block)) {
			if(!list.contains(e.dst)) {
				list.add(e.dst);
			}
		}
		return list;
	}

	public static Set<BasicBlock> collectPredecessors(ControlFlowGraph cfg, BasicBlock block) {
		Set<BasicBlock> list = new HashSet<BasicBlock>();
		for(FlowEdge<BasicBlock> e : cfg.getReverseEdges(block)) {
			if(!list.contains(e.src)) {
				list.add(e.src);
			}
		}
		return list;
	}

	public static List<BasicBlock> range(List<BasicBlock> gblocks, int start, int end) {
		if(start > end) {
			throw new IllegalArgumentException("start > end: " + start + " > " + end);
		}
		BasicBlock startBlock = null, endBlock = null;
		int startIndex = 0, endIndex = 0;
		String startName = LabelHelper.createBlockName(start);
		String endName = LabelHelper.createBlockName(end);
		int blockIndex = 0;
		for(BasicBlock b : gblocks) {
			if(b.getId().equals(startName)) {
				startBlock = b;
				startIndex = blockIndex;
			}
			if(b.getId().equals(endName)) {
				endBlock = b;
				endIndex = blockIndex;
			}
			
			if(startBlock != null && endBlock != null) {
				break;
			}
			blockIndex++;
		}
		
		if(startBlock == null || endBlock == null) {
			throw new UnsupportedOperationException("start or end null, " + start + " " + end);
		} else if(startIndex > endIndex) {
			throw new IllegalArgumentException("startIndex > endIndex: " + startIndex + " > " + endIndex);
		}

		List<BasicBlock> blocks = new ArrayList<BasicBlock>();
		for(int i=startIndex; i <= endIndex; i++) {
			BasicBlock block = gblocks.get(i);
			if(block == null) {
				throw new IllegalArgumentException("block " + LabelHelper.createBlockName(i) + "not in range");
			}
			blocks.add(block);
		}
		
		return blocks;
	}

	public static List<FlowEdge<BasicBlock>> findCommonEdges(ControlFlowGraph cfg, BasicBlock src, BasicBlock dst) {
		List<FlowEdge<BasicBlock>> edges = new ArrayList<FlowEdge<BasicBlock>>();
		for(FlowEdge<BasicBlock> e : cfg.getEdges(src)) {
			if(e.dst == dst) {
				edges.add(e);
			}
		}
		return edges;
	}

	public static String toCfgHeader(Collection<BasicBlock> blocks) {
		int total = 0;
		for(BasicBlock b : blocks) {
			total += b.getInsns().size();
		}
		StringBuilder sb = new StringBuilder("=========CFG(block_count=").append(blocks.size()).append("(").append(LabelHelper.createBlockName(blocks.size())).append("), ").append("insn_count=").append(total).append(") ").append("=========");
		return sb.toString();
	}
	
//	public static String toString(StatementGraph sgraph) {
//		Collection<Statement> stmts = sgraph.vertices();
//		int total = 0;
//		for(Statement stmt : stmts) {
//			total += stmt.size();
//		}
//		StringBuilder sb = new StringBuilder("\n=========StmtGraph(stmt_count=").append(stmts.size()).append("(").append(LabelHelper.createBlockName(stmts.size())).append("), ").append("count=").append(total).append(") ").append("=========\n\n");
//		for(Statement stmt : stmts) {
//			sb.append("       ").append(stmt.getId()).append(". ").append(stmt).append("\n");
//			
//			for(FlowEdge<Statement> e : sgraph.getEdges(stmt)) {
//				sb.append("         -> ").append(e.toString()).append('\n');
//			}
//
//			for(FlowEdge<Statement> p : sgraph.getReverseEdges(stmt)) {
//				sb.append("         <- ").append(p.toInverseString()).append('\n');
//			}
//		}
//		return sb.toString();
//	}
//
	public static String toString(ControlFlowGraph cfg, Collection<BasicBlock> blocks) {
		int total = 0;
		for(BasicBlock b : blocks) {
			total += b.getInsns().size();
		}
		StringBuilder sb = new StringBuilder("\n=========CFG(block_count=").append(blocks.size()).append("(").append(LabelHelper.createBlockName(blocks.size())).append("), ").append("insn_count=").append(total).append(") ").append("=========\n\n");
		int i = 0;
		for(BasicBlock b : blocks) {
			printBlock(cfg, blocks, sb, b, i);
			i += b.size();
			i++; // label
		}
		return sb.toString();
	}

	public static String toString(StatementGraph sg, Collection<Statement> stmts) {
		StringBuilder sb = new StringBuilder("\n=========SG(stmt_count=").append(stmts.size()).append(") ").append("=========\n");
		sb.append("  ENTRIES: " + sg.getEntries() +"\n\n");
		for(Statement stmt : stmts) {
			if (stmt instanceof HeaderStatement)
				continue;
			sb.append(stmt).append(" #").append(stmt.getId()).append('\n');
			for(FlowEdge<Statement> e : sg.getEdges(stmt)) {
				sb.append("         -> ").append(e.toString()).append('\n');
			}
			for(FlowEdge<Statement> p : sg.getReverseEdges(stmt)) {
				sb.append("         <- ").append(p.toInverseString()).append('\n');
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	public static void printBlock(ControlFlowGraph cfg, Collection<BasicBlock> blocks, StringBuilder sb, BasicBlock b, int insns, boolean headers) {
		if(headers) {
			sb.append("===#").append(b.isDummy() ? "Dummy" : "").append("Block ").append(b.getId()).append("(size=").append(b.size()).append(", ident=").append(b.getLabel() != null ? b.getLabel().hashCode() : "null").append(")===\n");
		}

		for(AbstractInsnNode ain : b.getInsns()) {
			if(ain.type() != AbstractInsnNode.FRAME && headers) {
				sb.append("       ").append(insns).append(". ");
			}
			insns++;
			if(ain.type() != AbstractInsnNode.FRAME && ain.type() != AbstractInsnNode.LINE && ain.type() != AbstractInsnNode.LABEL) {
				sb.append(Printer.OPCODES[ain.opcode()].toLowerCase());
			}
			switch(ain.type()) {
				case AbstractInsnNode.LINE:
					sb.append(" Line ").append(((LineNumberNode)ain).line);
					break;
				case AbstractInsnNode.FRAME:
					break;
				case AbstractInsnNode.LABEL:
					sb.append(" Label").append(' ').append(cfg.getBlock((LabelNode) ain));
					break;

				case AbstractInsnNode.INSN: {
					break;
				}
				case AbstractInsnNode.INT_INSN: {
					sb.append(' ').append(((IntInsnNode) ain).operand);
					break;
				}
				case AbstractInsnNode.VAR_INSN: {
					sb.append(' ').append(((VarInsnNode) ain).var);
					break;
				}
				case AbstractInsnNode.TYPE_INSN: {
					sb.append(' ').append(((TypeInsnNode) ain).desc);
					break;
				}
				case AbstractInsnNode.FIELD_INSN: {
					FieldInsnNode fin = (FieldInsnNode) ain;
					if(headers) sb.append(' ').append(fin.owner).append('.').append(fin.name).append(' ').append(fin.desc);
					break;
				}
				case AbstractInsnNode.METHOD_INSN: {
					MethodInsnNode min = (MethodInsnNode) ain;
					if(headers) sb.append(' ').append(min.owner).append('.').append(min.name).append(' ').append(min.desc);
					break;
				}
				case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
					// TODO: if it turns up
					System.err.println("dynamic");
					break;
				}
				case AbstractInsnNode.JUMP_INSN: {
					JumpInsnNode jin = (JumpInsnNode) ain;
					BasicBlock jb = cfg.getBlock(jin.label);
					if(headers) {
						sb.append(" #").append(jb != null ? jb.getId() : "null").append("   (").append(System.identityHashCode(jin)).append(", ").append(System.identityHashCode(jin.label)).append(")");
					} else {
						sb.append(" #").append(jb != null ? jb.getId() : "null");
					}
					break;
				}
				case AbstractInsnNode.LDC_INSN: {
					LdcInsnNode ldc = (LdcInsnNode) ain;
					sb.append(' ').append(ldc.cst).append(" (").append(ldc.cst != null ? ldc.cst.getClass().getSimpleName() : "").append(')');
					break;
				}
				case AbstractInsnNode.IINC_INSN: {
					IincInsnNode iinc = (IincInsnNode) ain;
					sb.append(' ').append(iinc.var).append(' ').append(iinc.incr);
					break;
				}
				case AbstractInsnNode.TABLESWITCH_INSN: {
					TableSwitchInsnNode tsin = (TableSwitchInsnNode) ain;
					for(int i=tsin.min; i <= tsin.max; i++) {
						BasicBlock target = cfg.getBlock(tsin.labels.get(i - tsin.min));
						sb.append("\n        [").append(i).append("] -> ").append('#').append(target.getId());
					}
					BasicBlock dflt = cfg.getBlock(tsin.dflt);
					sb.append("\n        [").append("dflt").append("] -> ").append('#').append(dflt.getId());
					break;
				}
				case AbstractInsnNode.LOOKUPSWITCH_INSN: {
					LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) ain;
					for(int i=0; i < lsin.keys.size(); i++) {
						BasicBlock target = cfg.getBlock(lsin.labels.get(i));
						sb.append("\n        [").append(lsin.keys.get(i)).append("] -> ").append('#').append(target.getId());
					}
					BasicBlock dflt = cfg.getBlock(lsin.dflt);
					sb.append("\n        [").append("dflt").append("] -> ").append('#').append(dflt.getId());
					break;
				}
				case AbstractInsnNode.MULTIANEWARRAY_INSN: {
					MultiANewArrayInsnNode main = (MultiANewArrayInsnNode) ain;
					sb.append(" ").append(main.desc).append(' ').append(main.dims);
					break;
				}
				default: {
					throw new UnsupportedOperationException(ain.toString());
				}
			}
			if(ain.type() != AbstractInsnNode.FRAME) {
				sb.append('\n');
			}
		}

		if(headers) {
			for(FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				sb.append("         -> ").append(e.toString()).append('\n');
			}

			for(FlowEdge<BasicBlock> p : cfg.getReverseEdges(b)) {
				sb.append("         <- ").append(p.toInverseString()).append('\n');
			}
		}
	}

	public static void printBlock(ControlFlowGraph cfg, Collection<BasicBlock> blocks, StringBuilder sb, BasicBlock b, int insns) {
		printBlock(cfg, blocks, sb, b, insns, true);
	}

	// modes: 1 - recreate and destroy
	//        2 - recreate and update
	//       else just recreate
	public static InsnList recreate(ControlFlowGraph cfg, List<BasicBlock> blocks, boolean rebuildRanges) {
		cfg.getMethod().instructions.removeAll(true);
		
		InsnList list = new InsnList();
		for(BasicBlock b : blocks) {
			if(!b.isDummy()) {
				list.add(b.getLabel());
				for(AbstractInsnNode ain : b.getInsns()) {
					list.add(ain);
					
					if(ain instanceof JumpInsnNode) {
						JumpInsnNode jin = (JumpInsnNode) ain;
						if(!cfg.containsVertex(b)) {
							System.out.println("err " + b.getId());
						}
						Set<FlowEdge<BasicBlock>> jumps = b.getSuccessors(e -> e instanceof JumpEdge);
						if(jumps.size() != 1) {
							StringBuilder sb = new StringBuilder();
							GraphUtils.printBlock(cfg, cfg.vertices(), sb, b, 0);
							System.err.println(sb);
							throw new IllegalStateException(cfg.getMethod() + " " + b.getId() + " " + Printer.OPCODES[ain.opcode()] + " " + jumps.toString() + " " + b.getSuccessors());
						}
						FlowEdge<BasicBlock> e = jumps.iterator().next();
						LabelNode target = e.dst.getLabel();
						jin.label = target;
					} else if(ain instanceof TableSwitchInsnNode) {
						TableSwitchInsnNode tsin = (TableSwitchInsnNode) ain;
						Set<FlowEdge<BasicBlock>> succs = b.getSuccessors(e -> e instanceof SwitchEdge);
						
						int branchCount = 0;
						boolean setdflt = false;
						for(FlowEdge<BasicBlock> e : succs) {
							SwitchEdge<BasicBlock> se = (SwitchEdge<BasicBlock>) e;
							if(se instanceof DefaultSwitchEdge) {
								if(setdflt) {
									throw new IllegalStateException();
								}
								tsin.dflt = se.dst.getLabel();
								setdflt = true;
							} else{
								branchCount++;
								int key = se.value;
								// TODO: allow any keys?
								//  check if contiguous
								if(key >= tsin.min && key <= tsin.max) {
									int index = key - tsin.min;
									tsin.labels.set(index, se.dst.getLabel());
								} else {
									throw new IllegalStateException();
								}
							}
						}
						
						// +1 because it's inclusive
						int predictedBranchCount = (tsin.max - tsin.min) + 1;
						if(branchCount != predictedBranchCount) {
							throw new IllegalStateException(String.format("%d:%d", branchCount, predictedBranchCount));
						}
					} else if(ain instanceof LookupSwitchInsnNode) {
						LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) ain;
						Set<FlowEdge<BasicBlock>> succs = b.getSuccessors(e -> e instanceof SwitchEdge);

						int branchCount = 0;
						boolean setdflt = false;
						for(FlowEdge<BasicBlock> e : succs) {
							SwitchEdge<BasicBlock> se = (SwitchEdge<BasicBlock>) e;
							if(se instanceof DefaultSwitchEdge) {
								if(setdflt) {
									throw new IllegalStateException();
								}
								lsin.dflt = se.dst.getLabel();
								setdflt = true;
							} else{
								branchCount++;
								int key = se.value;
								// TODO: allow any keys?
								int index = lsin.keys.indexOf(key);
								if(index == -1) {
									throw new IllegalStateException("no key for " + key);
								}
								lsin.labels.set(index, se.dst.getLabel());
							}
						}
						
						if(branchCount != lsin.keys.size()) {
							throw new IllegalStateException(String.format("%d:%d", branchCount, lsin.keys.size()));
						}
					}
				}
			}
		}
		
		if(rebuildRanges) {
			cfg.getMethod().tryCatchBlocks.clear();
			for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
				if(!er.isContiguous()) {
					System.out.println(er + " not contiguous");
				} else {
					for(String type : er.getTypes()) {
						LabelNode start = er.get().get(0).getLabel();
						BasicBlock endBlock = er.get().get(er.get().size() - 1);
						int exclusiveEndIndex = blocks.indexOf(endBlock) + 1;
						LabelNode end = blocks.get(exclusiveEndIndex).getLabel();
						TryCatchBlockNode tcbn = new TryCatchBlockNode(start, end, er.getHandler().getLabel(), type);
						cfg.getMethod().tryCatchBlocks.add(tcbn);
					}
				}
			}
		}

		return list;
	}

	//	private static String getType(TryCatchEdge e) {
	//		if(e.erange.types.size() > 1) {
	//			return "java/lang/RuntimeException";
	//		} else {
	//			return e.erange.types.iterator().next();
	//		}
	//	}

	public static FlowEdge<BasicBlock> findEdge(ControlFlowGraph cfg, BasicBlock s, BasicBlock e) {
		List<FlowEdge<BasicBlock>> edges = findCommonEdges(cfg, s, e);
		if(edges.size() == 1) {
			return edges.get(0);
		} else {
			return null;
		}
	}

	public static Set<FlowEdge<BasicBlock>> getEdgesOf(ControlFlowGraph cfg, BasicBlock v) {
		return getEdgesOf(cfg, v, ACCEPT_ALL_EDGES);
	}

	public static Set<FlowEdge<BasicBlock>> getEdgesOf(ControlFlowGraph cfg, BasicBlock v, Predicate<FlowEdge<?>> exclusionPredicate) {
		Set<FlowEdge<BasicBlock>> e = cfg.getEdges(v);
		if(e != null) {
			e = new HashSet<FlowEdge<BasicBlock>>(e);
		} else {
			e = new HashSet<FlowEdge<BasicBlock>>();
		}
		e.removeIf(exclusionPredicate);
		return e;
	}

	//	public static BasicBlock getBlock(Collection<BasicBlock> blocks, LabelNode label) {
	//		if(label != null) {
	//			for(BasicBlock block : blocks) {
	//				if(label.equals(block.getLabel())) {
	//					return block;
	//				}
	//			}
	//		}
	//		return null;
	//	}
	//	
	//	public static BasicBlock getBlock(Collection<BasicBlock> blocks, String id) {
	//		if(id != null) {
	//			for(BasicBlock block : blocks) {
	//				if(id.equals(block.getId())) {
	//					return block;
	//				}
	//			}
	//		}
	//		return null;
	//	}
	
	public static void mergeNext(ControlFlowGraph cfg, List<BasicBlock> order, BasicBlock b, BasicBlock next) {
		next.prependInsns(b.getInsns());
		b.clear();
		order.remove(b);
		
		// redirect the incoming edges to the next block
		Set<FlowEdge<BasicBlock>> incoming = cfg.getReverseEdges(b);
		for(FlowEdge<BasicBlock> e : new ArrayList<>(incoming)) {
			FlowEdge<BasicBlock> ce = e.clone(e.src, next);
			cfg.removeEdge(e.src, e);
			cfg.addEdge(e.src, ce);
		}

		// redirect the outgoing edges to the next block
		Set<FlowEdge<BasicBlock>> outgoing = cfg.getEdges(b);		
		for(FlowEdge<BasicBlock> e : new ArrayList<>(outgoing)) {
			if(e.dst != next) {
				FlowEdge<BasicBlock> ce = e.clone(next, e.dst);
				cfg.addEdge(next, ce);
			}
			
			cfg.removeEdge(e.src, e);
		}
		
		cfg.removeVertex(b);
	}

	public static void merge(ControlFlowGraph cfg, List<BasicBlock> order, BasicBlock pred, BasicBlock b, FlowEdge<BasicBlock> e) {
		// first transfer the insns
		for(AbstractInsnNode ain : b.getInsns()) {
			pred.addInsn(ain);
		}
		b.clear();
		// TODO: destroy the blocks insns?

		// remove the block
		order.remove(b);
		// transfer the edges going from this block outwards (dst)
		// to the pred (outwards to dst)

		// uses the fact that TryCatchEdge has a hashCode and
		// equals method to avoid duplicate try catch jumps.
		Set<FlowEdge<BasicBlock>> succEdges = cfg.getEdges(b);
		for(FlowEdge<BasicBlock> se : succEdges) {
			// add the edges to pred
			FlowEdge<BasicBlock> ce = se.clone(pred, se.dst);
			cfg.removeEdge(pred, se);
			cfg.addEdge(pred, ce);
		}

		// remove b.edges from the graph
		cfg.removeVertex(b);

		// remove the edge from pred -> dst
		cfg.removeEdge(pred, e);
		// mergeTrys(cfg, order, pred, b);
	}
	
	public static void naturaliseGraph(ControlFlowGraph cfg, List<BasicBlock> order) {
		// copy edge sets
		Map<BasicBlock, Set<FlowEdge<BasicBlock>>> edges = new HashMap<>();
		for(BasicBlock b : order) {
			edges.put(b, cfg.getEdges(b));
		}
		// clean graph
		cfg.clear();
		// rename and add blocks
		int label = 1;
		for(BasicBlock b : order) {
			String id = LabelHelper.createBlockName(label);
			label++;
			
			b.setId(id);
			cfg.addVertex(b);
		}
		
		for(Entry<BasicBlock, Set<FlowEdge<BasicBlock>>> e : edges.entrySet()) {
			BasicBlock b = e.getKey();
			for(FlowEdge<BasicBlock> fe : e.getValue()) {
				cfg.addEdge(b, fe);
			}
		}
	}
}