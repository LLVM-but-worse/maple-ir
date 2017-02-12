package org.mapleir.ir.analysis.split;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mapleir.ir.analysis.FlowGraphUtils.IdSource;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class UDNode implements FastGraphVertex {

	public final BasicBlock block;
	public final String id;
	public final DoublyLinkedList<UDEdge> bracketList;
	public final List<UDEdge> cappingEdges;
	public final Set<UDEdge> backEdgesFrom;
	public final Set<UDEdge> backEdgesTo;
	public final Set<UDEdge> treeEdges;
	public final List<UDEdge> allEdges;
	public int dfsNum = -1;
	public UDEdge parent;
	public UDNode hi;
	
	public UDNode(BasicBlock block, String id) {
		this.block = block;
		this.id = id;
		
		bracketList = new DoublyLinkedList<>();
		cappingEdges = new ArrayList<>();
		allEdges = new ArrayList<>();
		backEdgesFrom = new HashSet<>();
		backEdgesTo = new HashSet<>();
		treeEdges = new HashSet<>();
	}

    public UDEdge addEdge(UDNode other) {
    	UDEdge edge = new UDEdge(this, other);
        allEdges.add(edge);
        other.allEdges.add(edge);
        return edge;
    }

    public void addEdge(UDEdge edge) {
        allEdges.add(edge);
    }
    
	public void computeCycleEquivalence(IdSource idSource) {
		// hi0 := min { t.dfsnum | (n, t) is a backedge } ;
		UDNode hi0 = null;
		for (UDEdge edge : backEdgesFrom) {
			UDNode other = edge.other(this);
			if ((hi0 == null) || (hi0.dfsNum > other.dfsNum)) {
				hi0 = other;
			}
		}
		// hi1 := min { c.hi | c is a child of n } ;
		// hichild := any child c of n having c.hi = hi1 ;
		UDNode hi1 = null;
		UDNode hiChild = null;
		for (UDEdge edge : treeEdges) {
			UDNode other = edge.other(this);
			if ((hi1 == null) || (hi1.dfsNum > other.hi.dfsNum)) {
				hiChild = other;
				hi1 = other.hi;
			}
		}
		// n.hi := min { hi0, hi1 } ;
		if (hi0 == null) {
			hi = hi1;
		} else if (hi1 == null) {
			hi = hi0;
		} else {
			hi = (hi0.dfsNum < hi1.dfsNum) ? hi0 : hi1;
		}

		// hi2 := min { c.hi | c is a child of n other than hichild }
		UDNode hi2 = null;
		for (UDEdge edge : treeEdges) {
			UDNode other = edge.other(this);
			if (other != hiChild) {
				if ((hi2 == null) || (hi2.dfsNum > other.hi.dfsNum)) {
					hi2 = other.hi;
				}
			}
		}

		/* compute bracketlist */
		DoublyLinkedList<UDEdge> blist = bracketList;
		assert blist.size() == 0;
		// n.blist := create ()
		// for each child c of n do
		for (UDEdge edge : treeEdges) {
			// n.blist := concat (c.blist, n.blist)
			blist.appendDestroying(edge.other(this).bracketList);
		}
		// for each capping backedge d from a descendant of n to n do
		for (UDEdge edge : cappingEdges) {
			// delete (n.blist, d) ;
			blist.delete(edge.node);
		}
		// for each backedge b from a descendant of n to n do
		for (UDEdge edge : backEdgesTo) {
			// delete (n.blist, b) ;
			blist.delete(edge.node);
			// if b.class undefined then
			if (edge.equivClass == null) {
				// b.class := new-class () ;
				edge.equivClass = new EquivClass(idSource);
			}
		}
		// for each backedge e from n to an ancestor of n do
		for (UDEdge edge : backEdgesFrom) {
			// push (n.blist, e) ;
			DoublyLinkedList<UDEdge>.ListNode dnode = blist.prepend(edge);
			edge.node = dnode;
		}
		// if hi2 < hi0 then
		if ((hi2 != null) && (hi2.dfsNum < dfsNum) && ((hi0 == null) || hi0.dfsNum > hi2.dfsNum)) {
			/* create capping backedge */
			// d := (n, node[hi2]) ;
			UDEdge edge = new UDEdge(this, hi2);
			hi2.cappingEdges.add(edge);
			// push (n.blist, d) ;
			DoublyLinkedList<UDEdge>.ListNode dnode = blist.prepend(edge);
			edge.node = dnode;
		}
		/* determine class for edge from parent(n) to n */
		UDEdge parent = this.parent;
		// if n is not the root of dfstree then
		if (parent != null) {
			// let e be the tree edge from parent(n) to n :
			// b := top(n.blist) ;
			UDEdge edge = blist.getFirst();
			// if b.recentSize != size(n.blist) then
			int bsize = blist.size();
			if (edge.recentSize != bsize) {
				// b.recentSize := size(n.bracketList) ;
				edge.recentSize = bsize;
				// b.recentEquivClass := new-class() ;
				edge.recentEquivClass = new EquivClass(idSource);
			}
			// e.class := b.recentEquivClass ;
			parent.equivClass = edge.recentEquivClass;

			/* check for e, b equivalence */
			// if b.recentSize = 1 then
			if (edge.recentSize == 1) {
				// b.class := e.class ;
				edge.equivClass = parent.equivClass;
			}
		}
	}
	
	public void computeSpanningTree(List<UDNode> nodes) {
		if(dfsNum != -1) {
			throw new UnsupportedOperationException(Integer.toString(dfsNum));
		}
		
		dfsNum = nodes.size();
		nodes.add(this);
		
		for(UDEdge e : allEdges) {
			if(e != parent) {
				UDNode o = e.other(this);
				
				if(o.dfsNum == -1) {
					treeEdges.add(e);
					o.parent = e;
					o.computeSpanningTree(nodes);
				} else if(o.dfsNum < dfsNum) {
					backEdgesFrom.add(e);
					o.backEdgesTo.add(e);
				}
			}
		}
	}

    public void computeSESE() {
        for (UDEdge edge : treeEdges) {
            if (!edge.seen) {
                edge.seen = true;
                edge.equivClass.addEdge(edge);
                edge.other(this).computeSESE();
            }
        }
    }
	
	@Override
	public String getId() {
		return id;
	}

	@Override
	public int getNumericId() {
		return BasicBlock.numeric(id);
	}
}