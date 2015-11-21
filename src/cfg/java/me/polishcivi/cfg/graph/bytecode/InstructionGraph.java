package me.polishcivi.cfg.graph.bytecode;

import java.util.HashMap;
import java.util.ListIterator;

import me.polishcivi.cfg.graph.ICFGEdge;
import me.polishcivi.cfg.graph.edge.DecisionEdge;
import me.polishcivi.cfg.graph.edge.GOTOEdge;
import me.polishcivi.cfg.graph.edge.ImmediateEdge;

import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.AbstractBaseGraph;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

/**
 * Created by polish on 21.11.15.
 */
public class InstructionGraph extends AbstractBaseGraph<InstructionVertex, ICFGEdge> implements DirectedGraph<InstructionVertex, ICFGEdge>, Opcodes {
	private static final long serialVersionUID = 3530136324552521668L;

	private final MethodNode methodNode;
	private final InstructionVertex firstInstruction;

	public InstructionGraph(MethodNode methodNode) {
		super((sourceVertex, targetVertex) -> {
			throw new RuntimeException("No edge factory for this!");
		}, true, true);
		if (!methodNode.tryCatchBlocks.isEmpty()) {
			throw new IllegalArgumentException("Methods with try-catch blocks not supported!");
		}
		this.methodNode = methodNode;
		this.firstInstruction = this.build();
	}

	private InstructionVertex build() {
		ListIterator<AbstractInsnNode> iterator = this.methodNode.instructions.iterator();
		HashMap<AbstractInsnNode, InstructionVertex> instructionToVertex = new HashMap<>();

		AbstractInsnNode firstInstruction = null;
		AbstractInsnNode previous = null;
		while (iterator.hasNext()) {
			AbstractInsnNode instruction = iterator.next();

			if (!(instruction instanceof LabelNode) && !(instruction instanceof FrameNode) && !(instruction instanceof LineNumberNode)) {
				if (firstInstruction == null) {
					firstInstruction = instruction;
				}
				InstructionVertex vertex;
				if (!instructionToVertex.containsKey(instruction)) {
					vertex = new InstructionVertex(instruction);
					this.addVertex(vertex);
					instructionToVertex.put(instruction, vertex);

					if (instruction instanceof JumpInsnNode) {
						AbstractInsnNode targetInsn = nextRealInstruction(((JumpInsnNode) instruction).label);
						InstructionVertex targetBlock;
						if (!instructionToVertex.containsKey(targetInsn)) {
							instructionToVertex.put(targetInsn, targetBlock = new InstructionVertex(targetInsn));
							this.addVertex(targetBlock);
						} else {
							targetBlock = instructionToVertex.get(targetInsn);
						}
						this.addEdge(vertex, targetBlock, vertex.getInstruction().opcode() == GOTO ? new GOTOEdge() : new DecisionEdge(true));
					} else if (instruction instanceof TableSwitchInsnNode) {
						throw new RuntimeException("//TODO IMPLEMENT ME");
					} else if (instruction instanceof LookupSwitchInsnNode) {
						throw new RuntimeException("//TODO IMPLEMENT ME");
					}
				} else {
					vertex = instructionToVertex.get(instruction);
				}

				if (previous != null) {
					InstructionVertex previousBlock = instructionToVertex.get(previous);
					if (previous.type() == AbstractInsnNode.JUMP_INSN && previous.opcode() != GOTO && previous.opcode() != JSR) {
						// if previous is decision block, the sequent
						// instruction to it its false successor of that
						// instruction.
						this.addEdge(previousBlock, vertex, new DecisionEdge(false));
					} else if // not return and nothing that produces jumps eg
								// TableSwitch
					(previous.type() != AbstractInsnNode.TABLESWITCH_INSN && previous.type() != AbstractInsnNode.JUMP_INSN
							&& // GOTO
							previous.type() != AbstractInsnNode.LOOKUPSWITCH_INSN && previous.opcode() != RET && previous.opcode() != ATHROW
							&& (previous.opcode() < IRETURN || previous.opcode() > RETURN)) {
						this.addEdge(previousBlock, vertex, new ImmediateEdge());
					}
				}

				previous = instruction;
			}
		}
		return instructionToVertex.get(firstInstruction);
	}

	private AbstractInsnNode nextRealInstruction(AbstractInsnNode from) {
		AbstractInsnNode node = from;
		while (node != null) {
			if (!(node instanceof LabelNode) && !(node instanceof FrameNode) && !(node instanceof LineNumberNode)) {
				return node;
			}
			node = node.getNext();
		}
		return null;
	}

	public MethodNode getMethodNode() {
		return this.methodNode;
	}

	public InstructionVertex getFirstInstruction() {
		return this.firstInstruction;
	}

	@Override
	public ICFGEdge addEdge(InstructionVertex sourceVertex, InstructionVertex targetVertex) {
		throw new RuntimeException("You must specify the edge type.");
	}
}
