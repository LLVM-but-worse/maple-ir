package me.polishcivi.cfg.graph;

/**
 * Created by polish on 21.11.15.
 */
public interface ICFGEdge extends Cloneable {

	String label();

	ICFGEdge clone();

	boolean checkEquality(ICFGEdge other);
}