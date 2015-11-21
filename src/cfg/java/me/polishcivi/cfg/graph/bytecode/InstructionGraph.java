package me.polishcivi.cfg.graph.bytecode;

import me.polishcivi.cfg.graph.ICFGEdge;
import me.polishcivi.cfg.graph.edge.DecisionEdge;
import me.polishcivi.cfg.graph.edge.GOTOEdge;
import me.polishcivi.cfg.graph.edge.ImmediateEdge;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.AbstractBaseGraph;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.ListIterator;

/**
 * Created by polish on 21.11.15.
 */
public class InstructionGraph extends AbstractBaseGraph<InstructionVertex, ICFGEdge> implements DirectedGraph<InstructionVertex, ICFGEdge>, Opcodes {
    private final MethodNode methodNode;
    private final InstructionVertex firstInstruction;

    /**
     * @param methodNode
     */
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

    /**
     *
     */
    private InstructionVertex build() {
        final ListIterator<AbstractInsnNode> iterator = this.methodNode.instructions.iterator();
        final HashMap<AbstractInsnNode, InstructionVertex> instructionToVertex = new HashMap<>();

        AbstractInsnNode firstInstruction = null;
        AbstractInsnNode previous = null;
        while (iterator.hasNext()) {
            final AbstractInsnNode instruction = iterator.next();

            if (!(instruction instanceof LabelNode) && !(instruction instanceof FrameNode) && !(instruction instanceof LineNumberNode)) {
                if (firstInstruction == null) {
                    firstInstruction = instruction;
                }
                final InstructionVertex vertex;
                if (!instructionToVertex.containsKey(instruction)) {
                    vertex = new InstructionVertex(instruction);
                    this.addVertex(vertex);
                    instructionToVertex.put(instruction, vertex);

                    if (instruction instanceof JumpInsnNode) {
                        final AbstractInsnNode targetInsn = nextRealInstruction(((JumpInsnNode) instruction).label);
                        final InstructionVertex targetBlock;
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
                    final InstructionVertex previousBlock = instructionToVertex.get(previous);
                    if (previous.type() == AbstractInsnNode.JUMP_INSN && previous.opcode() != GOTO && previous.opcode() != JSR) {
                        //if previous is decision block, the sequent instruction to it its false successor of that instruction.
                        this.addEdge(previousBlock, vertex, new DecisionEdge(false));
                    } else if //not return and nothing that produces jumps eg TableSwitch
                            (previous.type() != AbstractInsnNode.TABLESWITCH_INSN &&
                                    previous.type() != AbstractInsnNode.JUMP_INSN &&//GOTO
                                    previous.type() != AbstractInsnNode.LOOKUPSWITCH_INSN &&
                                    previous.opcode() != RET &&
                                    previous.opcode() != ATHROW &&
                                    (previous.opcode() < IRETURN ||
                                            previous.opcode() > RETURN)) {
                        this.addEdge(previousBlock, vertex, new ImmediateEdge());
                    }
                }

                previous = instruction;
            }
        }
        return instructionToVertex.get(firstInstruction);
    }

    /**
     * @param from
     * @return
     */
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

    /**
     * @return
     */
    public MethodNode getMethodNode() {
        return this.methodNode;
    }

    /**
     * @return
     */
    public InstructionVertex getFirstInstruction() {
        return this.firstInstruction;
    }

    @Override
    public ICFGEdge addEdge(InstructionVertex sourceVertex, InstructionVertex targetVertex) {
        throw new RuntimeException("You must specify the edge type.");
    }
}
