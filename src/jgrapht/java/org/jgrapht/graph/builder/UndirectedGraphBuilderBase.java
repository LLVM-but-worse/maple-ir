/* ==========================================
 * JGraphT : a free Java graph-theory library
 * ==========================================
 *
 * Project Info:  http://jgrapht.sourceforge.net/
 * Project Creator:  Barak Naveh (http://sourceforge.net/users/barak_naveh)
 *
 * (C) Copyright 2003-2008, by Barak Naveh and Contributors.
 *
 * This program and the accompanying materials are dual-licensed under
 * either
 *
 * (a) the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation, or (at your option) any
 * later version.
 *
 * or (per the licensee's choosing)
 *
 * (b) the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 */
/* -------------------------------
 * UndirectedGraphBuilderBase.java
 * -------------------------------
 * (C) Copyright 2015, by Andrew Chen and Contributors.
 *
 * Original Author:  Andrew Chen <llkiwi2006@gmail.com>
 * Contributor(s):   -
 *
 * $Id$
 *
 * Changes
 * -------
 * 12-Jan-2015 : Initial revision (AC);
 *
 */
package org.jgrapht.graph.builder;

import org.jgrapht.*;
import org.jgrapht.graph.*;


/**
 * Base class for {@link UndirectedGraphBuilder} for extending.
 */
public abstract class UndirectedGraphBuilderBase<V,
    E,
    G extends UndirectedGraph<V, E>,
    B extends UndirectedGraphBuilderBase<V, E, G, B>>
    extends AbstractGraphBuilder<V, E, G, B>
{
    

    /**
     * Creates a builder based on {@code baseGraph}. {@code baseGraph} must be
     * mutable.
     *
     * @param baseGraph the graph object to base building on
     */
    public UndirectedGraphBuilderBase(G baseGraph)
    {
        super(baseGraph);
    }

    

    @Override public UnmodifiableUndirectedGraph<V, E> buildUnmodifiable()
    {
        return new UnmodifiableUndirectedGraph<V, E>(this.graph);
    }
}

// End UndirectedGraphBuilderBase.java
