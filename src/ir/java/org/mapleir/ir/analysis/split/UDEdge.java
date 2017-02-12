package org.mapleir.ir.analysis.split;

import org.mapleir.stdlib.collections.graph.FastGraphEdge;

public class UDEdge extends FastGraphEdge<UDNode> {

	public DoublyLinkedList<UDEdge>.ListNode node;
	public UDNode represented;
	public EquivClass equivClass;
	public EquivClass recentEquivClass;
	public int recentSize;
	public boolean seen;

	public UDEdge(UDNode src, UDNode dst) {
		super(src, dst);
	}

	public UDNode other(UDNode o) {
		if (o == dst) {
			return src;
		} else if (o == src) {
			return dst;
		} else {
			throw new UnsupportedOperationException(this + ", " + o);
		}
	}
}