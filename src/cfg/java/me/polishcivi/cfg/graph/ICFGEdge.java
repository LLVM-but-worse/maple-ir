package me.polishcivi.cfg.graph;

/**
 * Created by polish on 21.11.15.
 */
public interface ICFGEdge extends Cloneable {

    /**
     * @return
     */
    String label();

    /**
     * @return
     */
    ICFGEdge clone();

    /**
     * @param other
     * @return
     */
    boolean checkEquality(ICFGEdge other);
}