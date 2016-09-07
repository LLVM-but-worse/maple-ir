package org.mapleir.ir;

import org.mapleir.ir.cfg.BoissinotDestructor;
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
	private static int NUM_ITER = 10000;
	
	public static void main(String[] args) throws IOException {
		InputStream is = new FileInputStream(new File("res/specjvm2008/FFT.class"));
		ClassReader cr = new ClassReader(is);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);

		long totalTime = 0;
		System.in.read();

		Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
		while (it.hasNext()) {
			MethodNode m = it.next();
//			if(!m.toString().startsWith("spec/benchmarks/scimark/fft/FFT.num_flops"))
//				continue;

			System.out.println("Processing " + m + "\n");

			try {
				ControlFlowGraph cfgOrig = ControlFlowGraphBuilder.build(m);
				for (int i = 0; i < NUM_ITER; i++) {
					ControlFlowGraph cfg = cfgOrig.copy();
					long now = System.nanoTime();
//					new SreedharDestructor(cfg);
					new BoissinotDestructor(cfg);
					totalTime += System.nanoTime() - now;
				}
			} catch (RuntimeException e) {
				throw new RuntimeException(e);
			}
		}
//		totalTime = BoissinotDestructor.elapse1 + BoissinotDestructor.elapse2 + BoissinotDestructor.elapse3 + BoissinotDestructor.elapse4;
		totalTime /= 1_000_000;
		System.out.println("Elapsed: " + totalTime + " ms");
		System.out.println("Each: " + (totalTime / (float) NUM_ITER) + " ms");
		System.out.println("Elapsed1: " + BoissinotDestructor.elapse1 / 1_000_000 + " ms");
		System.out.println("Elapsed2: " + BoissinotDestructor.elapse2 / 1_000_000 + " ms");
		System.out.println("Elapsed3: " + BoissinotDestructor.elapse3 / 1_000_000 + " ms");
		System.out.println("Elapsed4: " + BoissinotDestructor.elapse4 / 1_000_000 + " ms");
	}
}