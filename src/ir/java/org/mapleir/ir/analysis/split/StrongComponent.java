package org.mapleir.ir.analysis.split;

import java.util.TreeSet;

import org.mapleir.ir.analysis.FlowGraphUtils.TCNode;

public class StrongComponent implements Comparable<StrongComponent> {
    public final TCNode root;
    public final TreeSet<TCNode> members;
    public final TreeSet<StrongComponent> transitiveClosure;
    /** Entry basic block if this is a split point, null if it isn't. */
    public TCNode splitPoint;
//    /**
//     * If this component is split out, this field references the
//     * corresponding {@link SplitMethod} object.
//     */
//    SplitMethod splitMethod;

    /**
     * Size of the basic blocks in the transitive closure of this
     * component <i>that stay in the main method<i>.
     */
    int transitiveClosureSize;
    
    /**
     * Combined size of all the basic blocks in this component.
     */
    int size = -1;
    
    public StrongComponent(TCNode root) {
        this.root = root;
        members = new TreeSet<>();
        transitiveClosure = new TreeSet<>();
        transitiveClosure.add(this);
    }

//    /**
//     * Compute size of all basic blocks in this component and set
//     * the {@link #size} field to it.
//     */
//
//    private void computeSize() {
//        size = 0;
//        for (BasicBlock b : members) {
//            size += b.size;
//        }
//    }

//    public static void computeSizes(Set<StrongComponent> scs) {
//        for (StrongComponent sc : scs) {
//            sc.computeSize();
//        }
//    }

//    /**
//     * Compute code size of all basic blocks in the transitive closure
//     * of this component that remain in the main method.  Assumes the
//     * {@link #splitMethod} field is set.
//     */
//    private void recomputeTransitiveClosureSize() {
//        transitiveClosureSize = 0;
//        for (StrongComponent sc : transitiveClosure) {
//            if (sc.splitMethod == null)
//                transitiveClosureSize += sc.size;
//        }
//    }
//
//    public static void recomputeTransitiveClosureSizes(Set<StrongComponent> scs) {
//        for (StrongComponent sc : scs) {
//            sc.recomputeTransitiveClosureSize();
//        }
//    }

    @Override
	public int compareTo(StrongComponent other) {
        return root.block.compareTo(other.root.block);
    }
    @Override
    public String toString() {
        return "*" + root.toString() + members.toString();
    }
}