package org.mapleir.ir;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

public class Benchmark {
	private static int NUM_ITER = 5000;
	
	public static void main(String[] args) throws IOException {
		InputStream is = new FileInputStream(new File("res/a.class"));
		ClassReader cr = new ClassReader(is);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		
		Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();
			if(!m.toString().equals("a/a/f/a.<init>()V"))
				continue;
			
			System.in.read();
			System.out.println("Processing " + m + "\n");
		
			long now = System.nanoTime();
			
			for (int i = 0; i < NUM_ITER; i++) {
				ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
			}
			
			long elapsed = System.nanoTime() - now;
			elapsed /= 1_000_000;
			System.out.println("Elapsed: " + elapsed + " ms");
			System.out.println("Each: " + (elapsed / (float) NUM_ITER) + " ms");
		}
	}
}