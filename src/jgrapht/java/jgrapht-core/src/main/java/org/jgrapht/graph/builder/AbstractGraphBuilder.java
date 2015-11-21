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
/* ---------------------
 * GraphBuilderBase.java
 * ---------------------
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
 * Base class for builders of {@link Graph}
 *
 * @see DirectedGraphBuilderBase
 * @see UndirectedGraphBuilderBase
 */
public abstract class AbstractGraphBuilder<V,
    E, G extends Graph<V, E>, B extends AbstractGraphBuilder<V, E, G, B>>
{
    

    protected final G graph;

    

    /**
     * Creates a builder based on {@code baseGraph}. {@code baseGraph} must be
     * mutable.
     *
     * @param baseGraph the graph object to base building on
     */
    public AbstractGraphBuilder(G baseGraph)
    {
        this.graph = baseGraph;
    }

    

    /**
     * @return the {@code this} object.
     */
    protected abstract B self();

    /**
     * Adds {@code vertex} to the graph being built.
     *
     * @param vertex the vertex to add
     *
     * @return this builder object
     *
     * @see Graph#addVertex(Object)
     */
    public B addVertex(V vertex)
    {
        this.graph.addVertex(vertex);
        return this.self();
    }

    /**
     * Adds each vertex of {@code vertices} to the graph being built.
     *
     * @param vertices the vertices to add
     *
     * @return this builder object
     *
     * @see #addVertex(Object)
     */
    public B addVertices(V ... vertices)
    {
        for (V vertex : vertices) {
            this.addVertex(vertex);
        }
        return this.self();
    }

    /**
     * Adds an edge to the graph being built. The source and target vertices are
     * added to the graph, if not already included.
     *
     * @param source source vertex of the edge.
     * @param target target vertex of the edge.
     *
     * @return this builder object
     *
     * @see Graphs#addEdgeWithVertices(Graph, Object, Object)
     */
    public B addEdge(V source, V target)
    {
        Graphs.addEdgeWithVertices(this.graph, source, target);
        return this.self();
    }

    /**
     * Adds a chain of edges to the graph being built. The vertices are added to
     * the graph, if not already included.
     *
     * @return this builder object
     *
     * @see #addEdge(Object, Object)
     */
    public B addEdgeChain(V first, V second, V ... rest)
    {
        this.addEdge(first, second);
        V last = second;
        for (V vertex : rest) {
            this.addEdge(last, vertex);
            last = vertex;
        }
        return this.self();
    }

    /**
     * Adds all the vertices and all the edges of the {@code sourceGraph} to the
     * graph being built.
     *
     * @return this builder object
     *
     * @see Graphs#addGraph(Graph, Graph)
     */
    public B addGraph(Graph<? extends V, ? extends E> sourceGraph)
    {
        Graphs.addGraph(this.graph, sourceGraph);
        return this.self();
    }

    /**
     * Removes {@code vertex} from the graph being built, if such vertex exist
     * in graph.
     *
     * @param vertex the vertex to remove
     *
     * @return this builder object
     *
     * @see Graph#removeVertex(Object)
     */
    public B removeVertex(V vertex)
    {
        this.graph.removeVertex(vertex);
        return this.self();
    }

    /**
     * Removes each vertex of {@code vertices} from the graph being built, if
     * such vertices exist in graph.
     *
     * @param vertices the vertices to remove
     *
     * @return this builder object
     *
     * @see #removeVertex(Object)
     */
    public B removeVertices(V ... vertices)
    {
        for (V vertex : vertices) {
            this.removeVertex(vertex);
        }
        return this.self();
    }

    /**
     * Removes an edge going from source vertex to target vertex from the graph
     * being built, if such vertices and such edge exist in the graph.
     *
     * @param source source vertex of the edge.
     * @param target target vertex of the edge.
     *
     * @return this builder object
     *
     * @see Graph#removeVertex(Object)
     */
    public B removeEdge(V source, V target)
    {
        this.graph.removeEdge(source, target);
        return this.self();
    }

    /**
     * Build the graph. Calling any method (including this method) on this
     * builder object after calling this method is undefined behaviour.
     *
     * @return the built graph.
     */
    public G build()
    {
        return this.graph;
    }

    /**
     * Build an unmodifiable version graph. Calling any method (including this
     * method) on this builder object after calling this method is undefined
     * behaviour.
     *
     * @return the built unmodifiable graph.
     *
     * @see #build()
     */
    public UnmodifiableGraph<V, E> buildUnmodifiable()
    {
        return new UnmodifiableGraph<V, E>(this.graph);
    }
}

// End GraphBuilderBase.java
