package me.polishcivi.cfg.graph.edge;


import me.polishcivi.cfg.graph.ICFGEdge;

/**
 * Joins sequent blocks/instructions, if the target's predecessor edges are true / goto edges
 * or target has only one predecessor (our source)
 * The target of this edge should be just right after in bytecode.
 * <p>
 * It can be seen in ternary operations such as int i = v ? 1 : 0
 * When they get nested there is always one immediate successor at the end and other edges are goto.
 */
public class ImmediateEdge implements ICFGEdge {

    @Override
    public String label() {
        return "immediate";
    }

    @Override
    public ICFGEdge clone() {
        return new ImmediateEdge();
    }

    @Override
    public boolean checkEquality(ICFGEdge other) {
        return other.getClass().equals(this.getClass());
    }
}
