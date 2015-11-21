package me.polishcivi.cfg.graph.edge;

import me.polishcivi.cfg.graph.ICFGEdge;

/**
 * Joins the block/instruction when source's instruction is: GOTO, JSR
 */
public class GOTOEdge implements ICFGEdge {

	@Override
	public String label() {
		return "goto";
	}

	@Override
	public ICFGEdge clone() {
		return new GOTOEdge();
	}

	@Override
	public boolean checkEquality(ICFGEdge other) {
		return other.getClass().equals(this.getClass());
	}
}
