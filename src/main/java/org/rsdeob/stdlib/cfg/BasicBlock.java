package org.rsdeob.stdlib.cfg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.ImmediateEdge;
import org.rsdeob.stdlib.cfg.edge.TryCatchEdge;
import org.rsdeob.stdlib.collections.ExpressionStack;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;
import org.rsdeob.stdlib.ir.stat.Statement;

public class BasicBlock implements FastGraphVertex {

	private final ControlFlowGraph cfg;
	private final LabelNode label;
	private final List<AbstractInsnNode> insns;
	private String id;
	private int hashcode;
	private List<ExceptionRange<BasicBlock>> ranges;
	private ExpressionStack inputStack;
	private List<Statement> statements;
	
	public BasicBlock(ControlFlowGraph cfg, String id, LabelNode label, ExpressionStack stack) {
		this.cfg = cfg;
		this.id = id;
		this.label = label;
		inputStack = stack;
		
		hashcode = 31 + id.hashCode();
		insns = new ArrayList<AbstractInsnNode>();
		statements = new ArrayList<>();
	}
	
	public BasicBlock(ControlFlowGraph cfg, String id, LabelNode label) {
		this(cfg, id, label, null);
	}

	public List<Statement> getStatements() {
		return statements;
	}
	
	public ExpressionStack getInputStack() {
		return inputStack;
	}
	
	public void setInputStack(ExpressionStack stack) {
		inputStack = stack;
	}
	
	public boolean isDummy() {
		return label == null;
	}
	
	public void setId(String id) {
		this.id = id;
		hashcode = 31 + id.hashCode();
	}

	@Override
	public String getId() {
		return id;
	}

	public LabelNode getLabel() {
		return label;
	}

	public void prependInsns(List<AbstractInsnNode> newInsns) {
		insns.addAll(0, newInsns);
	}

	public void addInsn(AbstractInsnNode ain) {
		insns.add(ain);
	}

	public void removeInsn(AbstractInsnNode insn) {
		insns.remove(insn);
	}
	
	public void updateLabelRef(Map<LabelNode, LabelNode> labelTracking) {
//		LabelNode oldLabel = oldRef.getLabel();
//		LabelNode newLabel = newRef.getLabel();
		
		for(AbstractInsnNode ain : insns) {
			if(ain.type() == AbstractInsnNode.JUMP_INSN) {
				JumpInsnNode jin = (JumpInsnNode) ain;
				jin.label = labelTracking.get(jin.label);
			} else if(ain.type() == AbstractInsnNode.TABLESWITCH_INSN) {
				TableSwitchInsnNode tsin = (TableSwitchInsnNode) ain;
				tsin.labels.replaceAll(new UnaryOperator<LabelNode>() {
					@Override
					public LabelNode apply(LabelNode t) {
//						if(t == oldLabel) {
//							return newLabel;
//						} else {
//							return t;
//						}
						return labelTracking.get(t);
					}
				});
//				if(tsin.dflt == oldLabel) {
//					tsin.dflt = newLabel;
//				}
				tsin.dflt = labelTracking.get(tsin.dflt);
			} else if(ain.type() == AbstractInsnNode.LOOKUPSWITCH_INSN) {
				LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) ain;
				lsin.labels.replaceAll(new UnaryOperator<LabelNode>() {
					@Override
					public LabelNode apply(LabelNode t) {
//						if(t == oldLabel) {
//							return newLabel;
//						} else {
//							return t;
//						}
						return labelTracking.get(t);
					}
				});
//				if(lsin.dflt == oldLabel) {
//					lsin.dflt = newLabel;
//				}
				lsin.dflt = labelTracking.get(lsin.dflt);
			}
		}
	}
	
	public AbstractInsnNode realFirst() {
		for(int i=0; i < size(); i++) {
			AbstractInsnNode ain = insns.get(i);
			if(ain.opcode() != -1) {
				return ain;
			}
		}
		return null;
	}

	public AbstractInsnNode realLast() {
		for (int i = size() - 1; i >= 0; i--) {
			AbstractInsnNode ain = insns.get(i);
			if(ain.opcode() != -1) {
				return ain;
			}
		}
		return null;
	}
	
	public AbstractInsnNode last() {
		if (size() == 0) {
			return null;
		} else {
			return insns.get(size() - 1);
		}
	}

	public void clear() {
		insns.clear();
	}

	public List<AbstractInsnNode> getInsns() {
		return insns;
	}

	public int size() {
		return insns.size();
	}

	public int cleanSize() {
		int size = 0;
		for (AbstractInsnNode ain : insns) {
			if (ain.opcode() != -1) {
				size++;
			}
		}
		return size;
	}

	public List<ExceptionRange<BasicBlock>> getProtectingRanges() {
		if(ranges != null) {
			return ranges;
		}
		
		List<ExceptionRange<BasicBlock>> ranges = new ArrayList<>();
		for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			if(er.containsVertex(this)) {
				ranges.add(er);
			}
		}
		return (this.ranges = ranges);
	}
	
	public boolean isHandler() {
		for(FlowEdge<BasicBlock> e : cfg.getReverseEdges(this)) {
			if(e instanceof TryCatchEdge) {
				if(e.dst == this) {
					return true;
				} else {
					throw new IllegalStateException("incoming throw edge for " + getId() + " with dst " + e.dst.getId());
				}
			}
		}
		return false;
	}
	
	public Set<FlowEdge<BasicBlock>> getPredecessors() {
		return new HashSet<>(cfg.getReverseEdges(this));
	}

	public Set<FlowEdge<BasicBlock>> getPredecessors(Predicate<? super FlowEdge<BasicBlock>> e) {
		Set<FlowEdge<BasicBlock>> set = getPredecessors();
		set.removeIf(e.negate());
		return set;
	}

	public Set<FlowEdge<BasicBlock>> getSuccessors() {
		return new HashSet<>(cfg.getEdges(this));
	}

	public Set<FlowEdge<BasicBlock>> getSuccessors(Predicate<? super FlowEdge<BasicBlock>> e) {
		Set<FlowEdge<BasicBlock>> set = getSuccessors();
		set.removeIf(e.negate());
		return set;
	}

	public List<BasicBlock> getJumpEdges() {
		List<BasicBlock> jes = new ArrayList<>();
		for (FlowEdge<BasicBlock> e : cfg.getEdges(this)) {
			if (!(e instanceof ImmediateEdge)) {
				jes.add(e.dst);
			}
		}
		return jes;
	}
	
	private Set<FlowEdge<BasicBlock>> findImmediatesImpl(Set<FlowEdge<BasicBlock>> set) {
		Set<FlowEdge<BasicBlock>> iset = new HashSet<>();
		for(FlowEdge<BasicBlock> e : set) {
			if(e instanceof ImmediateEdge) {
				iset.add(e);
			}
		}
		return iset;
	}
	
	private FlowEdge<BasicBlock> findSingleImmediateImpl(Set<FlowEdge<BasicBlock>> _set) {
		Set<FlowEdge<BasicBlock>> set = findImmediatesImpl(_set);
		int size = set.size();
		if(size == 0) {
			return null;
		} else if(size > 1) {
			throw new IllegalStateException(set.toString());
		} else {
			return set.iterator().next();
		}
	}

	public ImmediateEdge<BasicBlock> getImmediateEdge() {
		return (ImmediateEdge<BasicBlock>) findSingleImmediateImpl(cfg.getEdges(this));
	}
	
	public BasicBlock getImmediate() {
		FlowEdge<BasicBlock> e =  findSingleImmediateImpl(cfg.getEdges(this));
		if(e != null) {
			return e.dst;
		} else {
			return null;
		}
	}
	
	public ImmediateEdge<BasicBlock> getIncomingImmediateEdge() {
		return (ImmediateEdge<BasicBlock>) findSingleImmediateImpl(cfg.getReverseEdges(this));
	}

	public BasicBlock getIncomingImmediate() {
		FlowEdge<BasicBlock> e =  findSingleImmediateImpl(cfg.getReverseEdges(this));
		if(e != null) {
			return e.src;
		} else {
			return null;
		}
	}

	@Override
	public int hashCode() {
		return hashcode;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		} else if (obj instanceof BasicBlock) {
			BasicBlock other = (BasicBlock) obj;
			return hashcode == other.hashcode;
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		return String.format("Block #%s (%s)", id, label != null ? label.hashCode() : "dummy");
	}
}