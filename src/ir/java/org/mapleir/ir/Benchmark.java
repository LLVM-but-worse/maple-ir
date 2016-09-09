package org.mapleir.ir;

import org.mapleir.ir.analysis.DominanceLivenessAnalyser;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.SreedharDestructor;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

public class Benchmark {
	public static void main(String[] args) throws IOException {
		File testDir = new File("res/specjvm2008");
		HashMap<String, Iterable<MethodNode>> tests = new LinkedHashMap<>();
		for (File testFile : testDir.listFiles()) {
			if (testFile.isDirectory())
				tests.put(testFile.getName(), getMethods(testFile.listFiles()));
			else
				tests.put(testFile.getName(), getMethods(testFile));
		}

		ClassReader cr = new ClassReader(Test.class.getCanonicalName());
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		for (MethodNode m : cn.methods) {
			if (m.name.startsWith("test")) {
				List<MethodNode> methods = new ArrayList<>();
				methods.add(m);
				tests.put(m.name, methods);
			}
		}

		benchCopies(tests);
	}

	private static HashMap<String, Integer> results = new LinkedHashMap<>();
	private static void benchCopies(HashMap<String, Iterable<MethodNode>> tests) throws IOException {
		for (Entry<String, Iterable<MethodNode>> test : tests.entrySet()) {
			results.clear();
			for (MethodNode m : test.getValue()) {
				try {
					ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
					new SreedharDestructor(cfg);
					recordCopies(cfg, "Sreedhar3");

					cfg = ControlFlowGraphBuilder.build(m);
					DominanceLivenessAnalyser resolver = new DominanceLivenessAnalyser(cfg, null);
					new BoissinotDestructor(cfg, resolver, 0b0000);
					recordCopies(cfg, "Boissinot");

					cfg = ControlFlowGraphBuilder.build(m);
					resolver = new DominanceLivenessAnalyser(cfg, null);
					new BoissinotDestructor(cfg, resolver, 0b0001);
					recordCopies(cfg, "BValue");

					cfg = ControlFlowGraphBuilder.build(m);
					resolver = new DominanceLivenessAnalyser(cfg, null);
					new BoissinotDestructor(cfg, resolver, 0b0011);
					recordCopies(cfg, "BSharing");

					cfg = ControlFlowGraphBuilder.build(m);
					resolver = new DominanceLivenessAnalyser(cfg, null);
					new BoissinotDestructor(cfg, resolver, 0b0111);
					recordCopies(cfg, "ComplexValues");

					cfg = ControlFlowGraphBuilder.build(m);
					resolver = new DominanceLivenessAnalyser(cfg, null);
					new BoissinotDestructor(cfg, resolver, 0b1111);
					recordCopies(cfg, "BAll");
				} catch (RuntimeException e) {
					System.err.println(test.getKey());
					System.err.println(m.toString());
					throw new RuntimeException(e);
				}
			}
			printResults(test.getKey());
		}
		printResultsHeader();
	}

	private static void recordCopies(ControlFlowGraph cfg, String key) {
		results.put(key, results.getOrDefault(key, 0) + countCopies(cfg));
	}

	private static void benchmark(HashMap<String, Iterable<MethodNode>> tests) throws IOException {
		final int NUM_ITER = 10000;
		System.in.read();

		printResultsHeader();
		for (Entry<String, Iterable<MethodNode>> test : tests.entrySet()) {
			results.clear();
			for (MethodNode m : test.getValue()) {
				try {
					for (int i = 0; i < NUM_ITER; i++) {
						ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
						DominanceLivenessAnalyser resolver = new DominanceLivenessAnalyser(cfg, null);

						time();
						new SreedharDestructor(cfg);
						time("Sreedhar3");

						cfg = ControlFlowGraphBuilder.build(m);
						resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b0000);
						time("Boissinot");

						cfg = ControlFlowGraphBuilder.build(m);
						resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b0001);
						time("BValue");

						cfg = ControlFlowGraphBuilder.build(m);
						resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b0011);
						time("BSharing");

						cfg = ControlFlowGraphBuilder.build(m);
						resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b0111);
						time("BFacilitate");

						cfg = ControlFlowGraphBuilder.build(m);
						resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b1011);
						time("BValueSeq");

						cfg = ControlFlowGraphBuilder.build(m);
						resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b1111);
						time("BAll");
					}
				} catch (RuntimeException e) {
					throw new RuntimeException(e);
				}
			}
			printResults(test.getKey());
		}
	}

	private static void printResultsHeader() {
		System.out.print("testcase,");
		for (Iterator<String> iterator = results.keySet().iterator(); iterator.hasNext();) {
			System.out.print(iterator.next());
			if (iterator.hasNext())
				System.out.print(",");
		}
		System.out.println();
	}

	private static void printResults(String testName) {
		System.out.print(testName + ",");
		for (Iterator<Integer> iterator = results.values().iterator(); iterator.hasNext();) {
			System.out.print(iterator.next());
			if (iterator.hasNext())
				System.out.print(",");
		}
		System.out.println();
	}

	private static long now = -1L;

	private static void time() {
		if (now != -1L)
			throw new IllegalStateException();
		now = System.nanoTime();
	}

	private static void time(String key) {
		long elapsed = System.nanoTime() - now;
		if (now == -1L)
			throw new IllegalStateException();
		results.put(key, (int) elapsed / 1_000_000);
		now = -1L;
	}

	private static Iterable<MethodNode> getMethods(File[] files) throws IOException {
		List<MethodNode> methods = new ArrayList<>();
		for (File f : files)
			for (MethodNode m : getMethods(f))
				methods.add(m);
		return methods;
	}

	private static Iterable<MethodNode> getMethods(File f) throws IOException {
		InputStream is = new FileInputStream(f);
		ClassReader cr = new ClassReader(is);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		return cn.methods;
	}

	private static int countCopies(ControlFlowGraph cfg) {
//		System.out.println(cfg);
		int count = 0;
		for (BasicBlock b : cfg.vertices()) {
			for (Statement stmt : b) {
				if (stmt instanceof AbstractCopyStatement) {
					AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
					if (!copy.isSynthetic())
						count++;
				}
			}
		}
		return count;
	}
}