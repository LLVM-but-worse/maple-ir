package me.polishcivi.cfg.graph.basicblock;

import me.polishcivi.cfg.graph.ICFGEdge;
import me.polishcivi.cfg.graph.bytecode.InstructionGraph;
import me.polishcivi.cfg.graph.bytecode.InstructionVertex;
import org.jgrapht.DirectedGraph;
import org.jgrapht.event.TraversalListenerAdapter;
import org.jgrapht.event.VertexTraversalEvent;
import org.jgrapht.graph.AbstractBaseGraph;
import org.jgrapht.traverse.DepthFirstIterator;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

/**
 * Created by polish on 21.11.15.
 */
public class BasicBlockGraph extends AbstractBaseGraph<BasicBlockVertex, ICFGEdge> implements DirectedGraph<BasicBlockVertex, ICFGEdge>, Opcodes {
    /**
     * @param instructionGraph
     */
    public BasicBlockGraph(InstructionGraph instructionGraph) {
        super((sourceVertex, targetVertex) -> {
            throw new RuntimeException("No edge factory for this!");
        }, true, true);
        this.build(instructionGraph);
    }

    private void build(InstructionGraph bytecodeGraph) {
        final DepthFirstIterator<InstructionVertex, ICFGEdge> dfs = new DepthFirstIterator<>(bytecodeGraph, bytecodeGraph.getFirstInstruction());

        dfs.addTraversalListener(new TraversalListenerAdapter<InstructionVertex, ICFGEdge>() {
            private final LinkedList<InstructionVertex> currentBlock = new LinkedList<>();
            private final HashMap<InstructionVertex, BasicBlockVertex> blockPoints = new HashMap<>();
            private InstructionVertex previousVertex = null;


            @Override
            public void vertexFinished(VertexTraversalEvent<InstructionVertex> e) {
                final InstructionVertex vertex = e.getVertex();
                final Set<ICFGEdge> incoming = bytecodeGraph.incomingEdgesOf(vertex);
                boolean connectedWithPrevious = this.isConnectedWithPrevious(vertex);

                if (!connectedWithPrevious && !this.currentBlock.isEmpty()) {
                    this.closeBlock();
                }

                this.currentBlock.addFirst(vertex);

                if (incoming.size() == 0) {
                    this.closeBlock();
                } else if (incoming.size() == 1) {
                    final InstructionVertex predecessor = bytecodeGraph.getEdgeSource(incoming.iterator().next());
                    if (isGoto(predecessor) || bytecodeGraph.outDegreeOf(predecessor) > 1) {
                        this.closeBlock();
                    }
                } else if (incoming.size() > 1) {
                    this.closeBlock();
                }
                this.previousVertex = vertex;
            }

            /**
             *
             */
            private void closeBlock() {
                final InstructionVertex start = this.currentBlock.getFirst();
                final InstructionVertex end = this.currentBlock.getLast();

                /*
                Grabbing vertex from prediction cache / creating new one
                 */
                final BasicBlockVertex createdVertex = this.blockPoints.getOrDefault(start,
                        this.blockPoints.getOrDefault(end,
                                new BasicBlockVertex()));


                addVertex(createdVertex);
                this.blockPoints.putIfAbsent(start, createdVertex);

                if (createdVertex.getInstructions().isEmpty()) {
                    for (InstructionVertex instructionNode : this.currentBlock) {
                        createdVertex.getInstructions().add(instructionNode.getInstruction());
                    }
                }

                /*
                Now we are predicting edges based on bytecode'cfg end's outgoing node
                 */
                for (ICFGEdge edge : bytecodeGraph.outgoingEdgesOf(end)) {
                    final InstructionVertex target = bytecodeGraph.getEdgeTarget(edge);
                    final BasicBlockVertex block = this.blockPoints.getOrDefault(target, new BasicBlockVertex());
                    this.blockPoints.putIfAbsent(target, block);


                    addVertex(block);
                    addEdge(createdVertex, block, edge.clone());
                }
                this.currentBlock.clear();
            }

            /**
             *
             * @param vertex
             * @return
             */
            private boolean isConnectedWithPrevious(InstructionVertex vertex) {
                if (this.previousVertex == null) {
                    return true;
                }
                final Set<ICFGEdge> outgoing = bytecodeGraph.outgoingEdgesOf(vertex);
                for (ICFGEdge edge : outgoing) {
                    if (bytecodeGraph.getEdgeTarget(edge).equals(this.previousVertex)) {
                        return true;
                    }
                }
                return false;
            }

            /**
             *
             * @param vertex
             * @return
             */
            private boolean isGoto(InstructionVertex vertex) {
                return vertex.getInstruction().opcode() == GOTO;
            }
        });

        while (dfs.hasNext()) dfs.next();
    }
}
