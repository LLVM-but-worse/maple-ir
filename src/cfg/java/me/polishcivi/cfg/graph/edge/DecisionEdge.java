package me.polishcivi.cfg.graph.edge;

import me.polishcivi.cfg.graph.ICFGEdge;

/**
 * Joins the block/instruction when source's instruction is: IFEQ, IFNE, IFLT,
 * IFGE, IFGT, IFLE, IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT,
 * IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE, IFNULL or IFNONNULL.
 *
 * Simply not GOTO and JSR
 */
public class DecisionEdge implements ICFGEdge {
	private final boolean logic;

	public DecisionEdge(boolean logic) {
		this.logic = logic;
	}

	@Override
	public String label() {
		return Boolean.toString(this.logic);
	}

	@Override
	public ICFGEdge clone() {
		return new DecisionEdge(this.logic);
	}

	@Override
	public boolean checkEquality(ICFGEdge other) {
		return other instanceof DecisionEdge && ((DecisionEdge) other).logic == this.logic;
	}

	@Override
	public String toString() {
		return "Decision edge logic = " + this.logic;
	}

	public boolean getLogic() {
		return this.logic;
	}
}