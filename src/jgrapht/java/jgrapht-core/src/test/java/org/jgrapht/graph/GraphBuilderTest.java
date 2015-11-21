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
 * GraphBuilderTest.java
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
package org.jgrapht.graph;

import java.util.*;

import org.jgrapht.*;
import org.jgrapht.graph.builder.*;

public class GraphBuilderTest
        extends EnhancedTestCase
{
    //~ Instance fields --------------------------------------------------------

    private String v1 = "v1";
    private String v2 = "v2";
    private String v3 = "v3";
    private String v4 = "v4";
    private String v5 = "v5";
    private String v6 = "v6";
    private String v7 = "v7";
    private String v8 = "v8";

    //~ Methods ----------------------------------------------------------------

    public void testAddVertex() {
        Graph<String, DefaultEdge> g =
                new DirectedGraphBuilder<String, DefaultEdge, DefaultDirectedGraph<String, DefaultEdge>>
                (new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class))
                .addVertex(v1)
                .addVertices(v2, v3)
                .build();

        assertEquals(3, g.vertexSet().size());
        assertEquals(0, g.edgeSet().size());
        assertTrue(g.vertexSet().containsAll(Arrays.asList(v1, v2, v3)));
    }

    public void testAddEdge() {
        UnmodifiableGraph<String, DefaultWeightedEdge> g =
                new DirectedWeightedGraphBuilder<String, DefaultWeightedEdge, DefaultDirectedWeightedGraph<String, DefaultWeightedEdge>>
                (new DefaultDirectedWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class))
                .addEdge(v1, v2)
                .addEdgeChain(v3, v4, v5, v6)
                .addEdge(v7, v8, 10.0)
                .buildUnmodifiable();

        assertEquals(8, g.vertexSet().size());
        assertEquals(5, g.edgeSet().size());
        assertTrue(g.vertexSet().containsAll(Arrays.asList(v1, v2, v3, v4, v5, v6, v7, v8)));
        assertTrue(g.containsEdge(v1, v2));
        assertTrue(g.containsEdge(v3, v4));
        assertTrue(g.containsEdge(v4, v5));
        assertTrue(g.containsEdge(v5, v6));
        assertTrue(g.containsEdge(v7, v8));
        assertEquals(10.0, g.getEdgeWeight(g.getEdge(v7, v8)));
    }

    public void testAddGraph() {
        Graph<String, DefaultEdge> g1 =
                DefaultDirectedGraph.<String, DefaultEdge>builder(DefaultEdge.class)
//                new DirectedGraphBuilder<String, DefaultEdge, DefaultDirectedGraph<String, DefaultEdge>>
//                (new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class))
                .addVertex(v1)
                .addEdge(v2, v3)
                .buildUnmodifiable();

        Graph<String, DefaultEdge> g2 =
                new DirectedGraphBuilder<String, DefaultEdge, DefaultDirectedGraph<String, DefaultEdge>>
                (new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class))
                .addGraph(g1)
                .addEdge(v1, v4)
                .build();

        assertEquals(4, g2.vertexSet().size());
        assertEquals(2, g2.edgeSet().size());
        assertTrue(g2.vertexSet().containsAll(Arrays.asList(v1, v2, v3, v3)));
        assertTrue(g2.containsEdge(v2, v3));
        assertTrue(g2.containsEdge(v1, v4));
    }

    public void testRemoveVertex() {
        Graph<String, DefaultEdge> g1 =
                new DirectedGraphBuilder<String, DefaultEdge, DefaultDirectedGraph<String, DefaultEdge>>
                (new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class))
                .addEdge(v1, v3)
                .addEdgeChain(v2, v3, v4, v5)
                .buildUnmodifiable();

        Graph<String, DefaultEdge> g2 =
                new DirectedGraphBuilder<String, DefaultEdge, DefaultDirectedGraph<String, DefaultEdge>>
                (new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class))
                .addGraph(g1)
                .removeVertex(v2)
                .removeVertices(v4, v5)
                .build();

        assertEquals(2, g2.vertexSet().size());
        assertEquals(1, g2.edgeSet().size());
        assertTrue(g2.vertexSet().containsAll(Arrays.asList(v1, v3)));
        assertTrue(g2.containsEdge(v1, v3));
    }

    public void testRemoveEdge() {
        Graph<String, DefaultEdge> g1 =
                new DirectedGraphBuilder<String, DefaultEdge, DefaultDirectedGraph<String, DefaultEdge>>
                (new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class))
                .addEdgeChain(v1, v2, v3, v4)
                .buildUnmodifiable();

        Graph<String, DefaultEdge> g2 =
                new DirectedGraphBuilder<String, DefaultEdge, DefaultDirectedGraph<String, DefaultEdge>>
                (new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class))
                .addGraph(g1)
                .removeEdge(v2, v3)
                .build();

        assertEquals(4, g2.vertexSet().size());
        assertEquals(2, g2.edgeSet().size());
        assertTrue(g2.vertexSet().containsAll(Arrays.asList(v1, v2, v3, v4)));
        assertTrue(g2.containsEdge(v1, v2));
        assertTrue(g2.containsEdge(v3, v4));
    }
}

// End GraphBuilderTest.java
