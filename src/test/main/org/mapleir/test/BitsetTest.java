package org.mapleir.test;

import org.junit.Test;
import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.FastBlockGraph;
import org.mapleir.stdlib.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;

import java.util.Set;

public class BitsetTest {
	public static void main(String[] args) {
		new BitsetTest().test();
	}
	
	@Test
	public void test() {
		FastBlockGraph g = new FastBlockGraph();
		BasicBlock b0 = block();
		BasicBlock b1 = block();
		BasicBlock b2 = block();
		BasicBlock b3 = block();
		BasicBlock b4 = block();
		BasicBlock b5 = block();
		BasicBlock b6 = block();
		BasicBlock b7 = block();
		BasicBlock end = block();
		g.addVertex(end);
		
		edge(g, b0, b1);
		edge(g, b1, b2);
		edge(g, b1, b3);
		edge(g, b2, b7);
		edge(g, b3, b4);
		edge(g, b3, b5);
		edge(g, b4, b6);
		edge(g, b5, b6);
		edge(g, b6, b7);
		edge(g, b7, b1);
		edge(g, b7, end);

		g.getEntries().add(b0);
		
		GenericBitSet<BasicBlock> set1 = g.createBitSet();
		GenericBitSet<BasicBlock> set2 = g.createBitSet();
		set2.remove(b0);
		set2.remove(b1);
		set2.remove(b2);
		set2.remove(b3);
		System.out.println("Test1:");
		System.out.println(set1);
		System.out.println(set2);
		
		GenericBitSet<BasicBlock> set3 = set1.copy();
		set1.removeAll(set2);
		System.out.println("\nTest2:");
		System.out.println(set1);
		System.out.println(set3);
		
		set1 = set3.relativeComplement(set2);
		System.out.println("\nTest3:");
		System.out.println(set1);
		System.out.println(set2);
		System.out.println(set3);
		
		set1.addAll(set2);
		System.out.println("\nTest4:");
		System.out.println(set1);
		System.out.println(set2);
		System.out.println(set3);
		
		System.out.println("\nTest4:");
		System.out.println(set1.containsAll(set2));
		System.out.println(set1.containsAll(set3));
		System.out.println(set2.containsAll(set3));
		System.out.println(set3.containsAll(set3));
		System.out.println(set1.equals(set3));
		System.out.println(set2.equals(set3));
	}
	
	static void edge(FastBlockGraph graph, BasicBlock n, BasicBlock s) {
		graph.addEdge(n, new ImmediateEdge<>(n, s));
	}
	
	private static int index = 0;
	static BasicBlock block() {
		return new BasicBlock(null, index++, null);
	}
}
