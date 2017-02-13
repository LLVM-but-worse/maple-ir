/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.objectweb.asm.commons.blocksplit;

import java.util.Set;
import java.util.TreeSet;

/**
 * Strongly-connected component.
 */
public class StrongComponent implements Comparable<StrongComponent> {
    public final BasicBlock root;
    public final TreeSet<BasicBlock> members;
    public final TreeSet<StrongComponent> transitiveClosure;
    /**
     * Entry basic block if this is a split point, null if it isn't.
     */
    public BasicBlock splitPoint;
    /**
     * If this component is split out, this field references the
     * corresponding {@link SplitMethod} object.
     */
    SplitMethod splitMethod;

    /**
     * Size of the basic blocks in the transitive closure of this
     * component <i>that stay in the main method<i>.
     */
    int transitiveClosureSize;
    
    /**
     * Combined size of all the basic blocks in this component.
     */
    int size = -1;
    
    public StrongComponent(BasicBlock root) {
        this.root = root;
        this.members = new TreeSet<BasicBlock>();
        this.transitiveClosure = new TreeSet<StrongComponent>();
        this.transitiveClosure.add(this);
    }

    /**
     * Compute size of all basic blocks in this component and set
     * the {@link #size} field to it.
     */

    private void computeSize() {
        this.size = 0;
        for (BasicBlock b : members) {
            this.size += b.size;
        }
    }

    public static void computeSizes(Set<StrongComponent> scs) {
        for (StrongComponent sc : scs) {
            sc.computeSize();
        }
    }

    /**
     * Compute code size of all basic blocks in the transitive closure
     * of this component that remain in the main method.  Assumes the
     * {@link #splitMethod} field is set.
     */
    private void recomputeTransitiveClosureSize() {
        this.transitiveClosureSize = 0;
        for (StrongComponent sc : transitiveClosure) {
            if (sc.splitMethod == null)
                this.transitiveClosureSize += sc.size;
        }
    }

    public static void recomputeTransitiveClosureSizes(Set<StrongComponent> scs) {
        for (StrongComponent sc : scs) {
            sc.recomputeTransitiveClosureSize();
        }
    }

    public int compareTo(StrongComponent other) {
        return this.root.compareTo(other.root);
    }
    @Override
    public String toString() {
        return "*" + this.root.toString() + this.members.toString();
    }
}