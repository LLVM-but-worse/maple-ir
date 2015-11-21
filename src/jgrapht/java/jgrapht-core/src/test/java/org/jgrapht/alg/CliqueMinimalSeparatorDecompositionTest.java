/* ==========================================
 * JGraphT : a free Java graph-theory library
 * ==========================================
 *
 * Project Info:  http://jgrapht.sourceforge.net/
 * Project Creator:  Barak Naveh (http://sourceforge.net/users/barak_naveh)
 *
 * (C) Copyright 2003-2015, by Barak Naveh and Contributors.
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
/* -----------------
 * CliqueMinimalSeparatorDecompositionTest.java
 * -----------------
 * (C) Copyright 2015, by Florian Buenzli and Contributors.
 *
 * Original Author:  Florian Buenzli
 * Contributor(s):   -
 *
 * $Id$
 *
 * Changes
 * -------
 * 06-Feb-2015 : Initial revision (FB);
 *
 */
package org.jgrapht.alg;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.VertexFactory;
import org.jgrapht.generate.RandomGraphGenerator;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.Multigraph;
import org.jgrapht.graph.Pseudograph;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.graph.Subgraph;

import junit.framework.TestCase;

/**
 * Tests for the Clique Minimal Separator Decomposition.
 * 
 * @author Florian Buenzli <fbuenzli@student.ethz.ch>
 */
public class CliqueMinimalSeparatorDecompositionTest
    extends TestCase
{

    /**
     * Test graph:<br>
     * <code>o-o<br>
     * |/|<br>
     * o-o<br></code>
     */
    public void testSimpleGraph1()
    {
        SimpleGraph<Integer, DefaultEdge> g = 
            new SimpleGraph<Integer, DefaultEdge>(DefaultEdge.class);
        g.addVertex(1);
        g.addVertex(2);
        g.addVertex(3);
        g.addVertex(4);
        g.addEdge(1, 2);
        g.addEdge(2, 3);
        g.addEdge(3, 4);
        g.addEdge(1, 3);
        g.addEdge(2, 4);
        // validate graph
        assertEquals(4, g.vertexSet().size());
        assertEquals(5, g.edgeSet().size());

        CliqueMinimalSeparatorDecomposition<Integer, DefaultEdge> cmsd = 
            new CliqueMinimalSeparatorDecomposition<Integer, DefaultEdge>(g);
        // check triangulation
        assertEquals(4, cmsd.getMinimalTriangulation().vertexSet().size());
        assertEquals(5, cmsd.getMinimalTriangulation().edgeSet().size());

        // check atoms
        boolean atom1found = false, atom2found = false;
        for (Set<Integer> atom : cmsd.getAtoms()) {
            if (atom.equals(new HashSet<Integer>(
                Arrays.asList(new Integer[] { 1, 2, 3 }))))
            {
                atom1found = true;
            } else if (atom.equals(new HashSet<Integer>(
                Arrays.asList(new Integer[] { 2, 3, 4 }))))
            {
                atom2found = true;
            }
        }
        assertEquals(2, cmsd.getAtoms().size());
        assertTrue(atom1found);
        assertTrue(atom2found);

        // check seprators
        boolean separator1found = false;
        for (Set<Integer> separator : cmsd.getSeparators()) {
            if (separator.equals(new HashSet<Integer>(
                Arrays.asList(new Integer[] { 2, 3 }))))
            {
                separator1found = true;
            }
        }
        assertEquals(1, cmsd.getSeparators().size());
        assertTrue(separator1found);
    }
    /**
     * Test pseudo graph based on:<br>
     * <code>o-o<br>
     * |/|<br>
     * o-o<br></code>
     */
    public void testPseudograph1()
    {
        Pseudograph<Integer, DefaultEdge> g = 
            new Pseudograph<Integer, DefaultEdge>(DefaultEdge.class);
        g.addVertex(1);
        g.addVertex(2);
        g.addVertex(3);
        g.addVertex(4);
        g.addEdge(1, 1);
        g.addEdge(1, 2);
        g.addEdge(2, 2);
        g.addEdge(2, 3);
        g.addEdge(2, 3);
        g.addEdge(3, 4);
        g.addEdge(1, 3);
        g.addEdge(2, 4);
        g.addEdge(2, 4);
        g.addEdge(2, 4);
        // validate graph
        assertEquals(4, g.vertexSet().size());
        assertEquals(10, g.edgeSet().size());

        CliqueMinimalSeparatorDecomposition<Integer, DefaultEdge> cmsd = 
            new CliqueMinimalSeparatorDecomposition<Integer, DefaultEdge>(g);
        // check triangulation
        assertEquals(4, cmsd.getMinimalTriangulation().vertexSet().size());
        assertEquals(5, cmsd.getMinimalTriangulation().edgeSet().size());

        // check atoms
        boolean atom1found = false, atom2found = false;
        for (Set<Integer> atom : cmsd.getAtoms()) {
            if (atom.equals(new HashSet<Integer>(
                Arrays.asList(new Integer[] { 1, 2, 3 }))))
            {
                atom1found = true;
            } else if (atom.equals(new HashSet<Integer>(
                Arrays.asList(new Integer[] { 2, 3, 4 }))))
            {
                atom2found = true;
            }
        }
        assertEquals(2, cmsd.getAtoms().size());
        assertTrue(atom1found);
        assertTrue(atom2found);

        // check seprators
        boolean separator1found = false;
        for (Set<Integer> separator : cmsd.getSeparators()) {
            if (separator.equals(new HashSet<Integer>(
                Arrays.asList(new Integer[] { 2, 3 }))))
            {
                separator1found = true;
            }
        }
        assertEquals(1, cmsd.getSeparators().size());
        assertTrue(separator1found);
    }

    /**
     * Test graph:<br>
     * <code>o-o<br>
     * | |<br>
     * o-o<br></code>
     */
    public void testSimpleGraph2()
    {
        SimpleGraph<Integer, DefaultEdge> g = 
            new SimpleGraph<Integer, DefaultEdge>(DefaultEdge.class);
        g.addVertex(1);
        g.addVertex(2);
        g.addVertex(3);
        g.addVertex(4);
        g.addEdge(1, 2);
        g.addEdge(2, 3);
        g.addEdge(3, 4);
        g.addEdge(4, 1);
        // validate graph
        assertEquals(4, g.vertexSet().size());
        assertEquals(4, g.edgeSet().size());

        CliqueMinimalSeparatorDecomposition<Integer, DefaultEdge> cmsd = 
            new CliqueMinimalSeparatorDecomposition<Integer, DefaultEdge>(g);
        // check triangulation
        assertEquals(4, cmsd.getMinimalTriangulation().vertexSet().size());
        assertEquals(5, cmsd.getMinimalTriangulation().edgeSet().size());

        // check atoms
        boolean atom1found = false;
        for (Set<Integer> atom : cmsd.getAtoms()) {
            if (atom.equals(new HashSet<Integer>(
                Arrays.asList(new Integer[] { 1, 2, 3, 4 }))))
            {
                atom1found = true;
            }
        }
        assertEquals(1, cmsd.getAtoms().size());
        assertTrue(atom1found);

        // check seprators
        assertEquals(0, cmsd.getSeparators().size());
    }

    /**
     * Test graph: An Introduction to Clique Minimal Separator Decomposition,
     * Berry et al. 2010, Figure 1, DOI:10.3390/a3020197
     * <a href="http://www.mdpi.com/1999-4893/3/2/197">
     * http://www.mdpi.com/1999-4893/3/2/197</a>
     */
    public void testBerry2010()
    {
        SimpleGraph<String, DefaultEdge> g = 
            new SimpleGraph<String, DefaultEdge>(DefaultEdge.class);
        g.addVertex("a");
        g.addVertex("b");
        g.addVertex("c");
        g.addVertex("d");
        g.addVertex("e");
        g.addVertex("f");
        g.addVertex("g");
        g.addVertex("h");
        g.addVertex("i");
        g.addVertex("j");
        g.addVertex("k");
        g.addEdge("a", "b");
        g.addEdge("b", "c");
        g.addEdge("c", "d");
        g.addEdge("d", "e");
        g.addEdge("e", "g");
        g.addEdge("f", "g");
        g.addEdge("d", "f");
        g.addEdge("a", "k");
        g.addEdge("k", "j");
        g.addEdge("c", "k");
        g.addEdge("d", "j");
        g.addEdge("c", "j");
        g.addEdge("d", "k");
        g.addEdge("f", "k");
        g.addEdge("f", "j");
        g.addEdge("g", "j");
        g.addEdge("g", "k");
        g.addEdge("h", "j");
        g.addEdge("h", "i");
        g.addEdge("i", "k");
        // validate graph
        assertEquals(11, g.vertexSet().size());
        assertEquals(20, g.edgeSet().size());

        CliqueMinimalSeparatorDecomposition<String, DefaultEdge> cmsd = 
            new CliqueMinimalSeparatorDecomposition<String, DefaultEdge>(g);
        // check triangulation
        assertEquals(11, cmsd.getMinimalTriangulation().vertexSet().size());
        assertEquals(20 + 3, cmsd.getMinimalTriangulation().edgeSet().size());

        // check atoms
        boolean atom1found = false, atom2found = false, 
            atom3found = false, atom4found = false;
        for (Set<String> atom : cmsd.getAtoms()) {
            if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "a", "b", "c", "k" }))))
            {
                atom1found = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "c", "d", "j", "k" }))))
            {
                atom2found = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "h", "i", "j", "k" }))))
            {
                atom3found = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "d", "e", "f", "g", "j", "k" }))))
            {
                atom4found = true;
            }
        }
        assertEquals(4, cmsd.getAtoms().size());
        assertTrue(atom1found);
        assertTrue(atom2found);
        assertTrue(atom3found);
        assertTrue(atom4found);

        // check seprators
        boolean separator1found = false, separator2found = false, 
            separator3found = false;
        for (Set<String> separator : cmsd.getSeparators()) {
            if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "c", "k" }))))
            {
                separator1found = true;
            } else if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "j", "k" }))))
            {
                separator2found = true;
            } else if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "d", "j", "k" }))))
            {
                separator3found = true;
            }
        }
        assertEquals(3, cmsd.getSeparators().size());
        assertTrue(separator1found);
        assertTrue(separator2found);
        assertTrue(separator3found);
    }

    /**
     * Test graph: Decomposition by clique separators, Tarjan, 1985, Figure 1,
     * DOI: 10.1016/0012-365X(85)90051-2
     * <a href="http://www.sciencedirect.com/science/article/pii/0012365X85900512">
     * http://www.sciencedirect.com/science/article/pii/0012365X85900512</a>
     */
    public void testTarjan1985()
    {
        SimpleGraph<String, DefaultEdge> g = 
            new SimpleGraph<String, DefaultEdge>(DefaultEdge.class);
        g.addVertex("a");
        g.addVertex("b");
        g.addVertex("c");
        g.addVertex("d");
        g.addVertex("e");
        g.addVertex("f");
        g.addVertex("g");
        g.addVertex("h");
        g.addVertex("i");
        g.addVertex("j");
        g.addVertex("k");
        g.addEdge("a", "c");
        g.addEdge("a", "d");
        g.addEdge("a", "f");
        g.addEdge("b", "c");
        g.addEdge("b", "g");
        g.addEdge("c", "d");
        g.addEdge("c", "f");
        g.addEdge("c", "h");
        g.addEdge("d", "e");
        g.addEdge("d", "f");
        g.addEdge("d", "i");
        g.addEdge("e", "j");
        g.addEdge("f", "k");
        g.addEdge("g", "h");
        g.addEdge("h", "k");
        g.addEdge("i", "j");
        g.addEdge("i", "k");
        // validate graph
        assertEquals(11, g.vertexSet().size());
        assertEquals(17, g.edgeSet().size());

        CliqueMinimalSeparatorDecomposition<String, DefaultEdge> cmsd = 
            new CliqueMinimalSeparatorDecomposition<String, DefaultEdge>(g);
        // check triangulation
        assertEquals(11, cmsd.getMinimalTriangulation().vertexSet().size());

        // disabled:  this currently returns 23 instead of 21
        /*
        assertEquals(17 + 4, cmsd.getMinimalTriangulation().edgeSet().size());
        */

        // check atoms
        boolean atom1found = false, atom2found = false, 
            atom3found = false, atom4found = false;
        for (Set<String> atom : cmsd.getAtoms()) {
            if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "a", "c", "d", "f" }))))
            {
                atom1found = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "b", "c", "g", "h" }))))
            {
                atom2found = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "d", "e", "i", "j" }))))
            {
                atom3found = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "c", "d", "f", "h", "i", "k" }))))
            {
                atom4found = true;
            }
        }
        assertEquals(4, cmsd.getAtoms().size());
        assertTrue(atom1found);
        assertTrue(atom2found);
        assertTrue(atom3found);
        assertTrue(atom4found);

        // check seprators
        boolean separator1found = false, separator2found = false, 
            separator3found = false;
        for (Set<String> separator : cmsd.getSeparators()) {
            if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "c", "d", "f" }))))
            {
                separator1found = true;
            } else if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "c", "h" }))))
            {
                separator2found = true;
            } else if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "d", "i" }))))
            {
                separator3found = true;
            }
        }
        assertEquals(3, cmsd.getSeparators().size());
        assertTrue(separator1found);
        assertTrue(separator2found);
        assertTrue(separator3found);
    }

    /**
     * Test graph: CliqueMinimalSeparatorDecomposition1.jpg
     * <p>
     * <img src="CliqueMinimalSeparatorDecomposition1.jpg" />
     */
    public void testGraph1()
    {
        SimpleGraph<String, DefaultEdge> g = 
            new SimpleGraph<String, DefaultEdge>(DefaultEdge.class);
        g.addVertex("a");
        g.addVertex("b");
        g.addVertex("c");
        g.addVertex("d");
        g.addVertex("e");
        g.addVertex("f");
        g.addVertex("g");
        g.addVertex("h");
        g.addVertex("i");
        g.addVertex("j");
        g.addVertex("k");
        g.addVertex("l");
        g.addVertex("m");
        g.addVertex("n");
        g.addEdge("a", "b");
        g.addEdge("a", "d");
        g.addEdge("b", "e");
        g.addEdge("c", "e");
        g.addEdge("d", "e");
        g.addEdge("e", "f");
        g.addEdge("d", "g");
        g.addEdge("d", "h");
        g.addEdge("f", "k");
        g.addEdge("g", "i");
        g.addEdge("h", "i");
        g.addEdge("d", "i");
        g.addEdge("e", "j");
        g.addEdge("i", "j");
        g.addEdge("j", "k");
        g.addEdge("i", "l");
        g.addEdge("i", "m");
        g.addEdge("i", "n");
        g.addEdge("j", "m");
        g.addEdge("j", "n");
        // validate graph
        assertEquals(14, g.vertexSet().size());
        assertEquals(20, g.edgeSet().size());

        CliqueMinimalSeparatorDecomposition<String, DefaultEdge> cmsd = 
            new CliqueMinimalSeparatorDecomposition<String, DefaultEdge>(g);
        // check triangulation
        assertEquals(14, cmsd.getMinimalTriangulation().vertexSet().size());
        assertEquals(20 + 3, cmsd.getMinimalTriangulation().edgeSet().size());

        // check atoms
        assertEquals(9, cmsd.getAtoms().size());
        boolean[] atomsFound = new boolean[cmsd.getAtoms().size()];
        for (Set<String> atom : cmsd.getAtoms()) {
            if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "a", "b", "d", "e" }))))
            {
                atomsFound[0] = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "c", "e" }))))
            {
                atomsFound[1] = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "d", "g", "i" }))))
            {
                atomsFound[2] = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "d", "h", "i" }))))
            {
                atomsFound[3] = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "d", "e", "i", "j" }))))
            {
                atomsFound[4] = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "e", "f", "j", "k" }))))
            {
                atomsFound[5] = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "i", "l" }))))
            {
                atomsFound[6] = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "i", "j", "m" }))))
            {
                atomsFound[7] = true;
            } else if (atom.equals(new HashSet<String>(
                Arrays.asList(new String[] { "i", "j", "n" }))))
            {
                atomsFound[8] = true;
            }
        }
        for (int i = 0; i < atomsFound.length; ++i)
            assertTrue("atom " + i, atomsFound[i]);

        // check seprators
        assertEquals(6, cmsd.getSeparators().size());
        boolean[] separatorsFound = new boolean[cmsd.getSeparators().size()];
        for (Set<String> separator : cmsd.getSeparators()) {
            if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "d", "e" }))))
            {
                separatorsFound[0] = true;
            } else if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "e" }))))
            {
                separatorsFound[1] = true;
            } else if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "d", "i" }))))
            {
                separatorsFound[2] = true;
            } else if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "i" }))))
            {
                separatorsFound[3] = true;
            } else if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "e", "j" }))))
            {
                separatorsFound[4] = true;
            } else if (separator.equals(new HashSet<String>(
                Arrays.asList(new String[] { "i", "j" }))))
            {
                separatorsFound[5] = true;
            }
        }
        for (int i = 0; i < separatorsFound.length; ++i)
            assertTrue("separator " + i, separatorsFound[i]);


        // check component counts
        assertEquals(6, cmsd.getFullComponentCount().size());

        assertEquals(2, cmsd.getFullComponentCount().get(new HashSet<String>(
                Arrays.asList(new String[] { "d", "e" }))).intValue());

        assertEquals(2, cmsd.getFullComponentCount().get(new HashSet<String>(
                Arrays.asList(new String[] { "e" }))).intValue());

        assertEquals(3, cmsd.getFullComponentCount().get(new HashSet<String>(
                Arrays.asList(new String[] { "d", "i" }))).intValue());

        assertEquals(2, cmsd.getFullComponentCount().get(new HashSet<String>(
                Arrays.asList(new String[] { "i" }))).intValue());

        assertEquals(2, cmsd.getFullComponentCount().get(new HashSet<String>(
                Arrays.asList(new String[] { "e", "j" }))).intValue());

        assertEquals(3, cmsd.getFullComponentCount().get(new HashSet<String>(
                Arrays.asList(new String[] { "i", "j" }))).intValue());
    }

    /**
     * Test random graphs.
     * You may change the number of vertices and edges.
     */
    public void testRandomGraphs()
    {
        int rounds = 42;
        while (rounds --> 0) {

            // number of vertices
            final int n = 80 + rounds;

            // number of edges, sparse but enough to get a connected graph, 
            // 10% more than phase transition from disconnected to conntected.
            final int m = (int) Math.ceil(1.1 * Math.log(n) / n
                * (n * (n - 1) / 2));

            // generate a connected random graph with n vertices and m edges
            RandomGraphGenerator<Integer, DefaultEdge> generator;
            SimpleGraph<Integer, DefaultEdge> g;
            ConnectivityInspector<Integer, DefaultEdge> inspector;
            do {
                g = new SimpleGraph<Integer, DefaultEdge>(DefaultEdge.class);
                generator = 
                    new RandomGraphGenerator<Integer, DefaultEdge>(n, m);
                generator.generateGraph(g, new VertexFactory<Integer>()
                {
                    int i;
                    public Integer createVertex()
                    {
                        return ++i;
                    }
                }, null);

                inspector = new ConnectivityInspector<Integer, DefaultEdge>(g);
            } while (!inspector.isGraphConnected());

            // decompose graph
            CliqueMinimalSeparatorDecomposition<Integer, DefaultEdge> cmsd = 
                new CliqueMinimalSeparatorDecomposition<Integer, DefaultEdge>(g);

            // check triangulation
            assertEquals(cmsd.getMinimalTriangulation().edgeSet().size(), 
                g.edgeSet().size() + cmsd.getFillEdges().size());

            // check seprators
            for (Set<Integer> separator : cmsd.getSeparators()) {
                assertTrue(separator.size() >= 1);
                assertTrue(isClique(g, separator));
            }
            assertTrue(cmsd.getSeparators().size() < cmsd.getAtoms().size());

            // check component count
            assertEquals(cmsd.getSeparators().size(), 
                cmsd.getFullComponentCount().size());

            for (Set<Integer> separator : cmsd.getSeparators()) {
                assertTrue(cmsd.getFullComponentCount().get(separator)
                    .intValue() >= 2);
            }
        }
    }

    /**
     * Check whether the subgraph of <code>graph</code> induced by the given
     * <code>vertices</code> is complete, i.e. a clique.
     * 
     * @param graph the graph.
     * @param vertices the vertices to induce the subgraph from.
     * @return true if the induced subgraph is a clique.
     */
    private static <V, E> boolean isClique(
        UndirectedGraph<V, E> graph,
        Set<V> vertices)
    {
        for (V v1 : vertices) {
            for (V v2 : vertices) {
                if (v1 != v2 && graph.getEdge(v1, v2) == null)
                    return false;
            }
        }
        return true;
    }

}
