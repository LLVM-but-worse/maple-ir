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
/* -------------------------------------
 * DirectedWeightedGraphBuilderBase.java
 * -------------------------------------
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


/**
 * Base class for {@link DirectedWeightedGraphBuilder} for extending.
 */
public abstract class DirectedWeightedGraphBuilderBase<V,
    E,
    G extends DirectedGraph<V, E> & WeightedGraph<V, E>,
    B extends DirectedWeightedGraphBuilderBase<V, E, G, B>>
    extends DirectedGraphBuilderBase<V, E, G, B>
{
    

    /**
     * Creates a builder based on {@code baseGraph}. {@code baseGraph} must be
     * mutable.
     *
     * @param baseGraph the graph object to base building on
     */
    public DirectedWeightedGraphBuilderBase(G baseGraph)
    {
        super(baseGraph);
    }

    

    /**
     * Adds an weighted edge to the graph being built. The source and target
     * vertices are added to the graph, if not already included.
     *
     * @param source source vertex of the edge.
     * @param target target vertex of the edge.
     * @param weight weight of the edge.
     *
     * @return this builder object
     *
     * @see Graphs#addEdgeWithVertices(Graph, Object, Object, double)
     */
    public B addEdge(V source, V target, double weight)
    {
        Graphs.addEdgeWithVertices(this.graph, source, target, weight);
        return this.self();
    }
}

// End DirectedWeightedGraphBuilderBase.java
