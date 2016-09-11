package org.mapleir.ir;

import org.mapleir.ir.analysis.DominanceLivenessAnalyser;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.SreedharDestructor;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.cfg.builder.SSAGenPass;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.ConditionalJumpStatement;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.SwitchStatement;
import org.mapleir.ir.code.stmt.UnconditionalJumpStatement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.NodeTable;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Benchmark {
	public static void main(String[] args) throws IOException {
		HashMap<String, List<MethodNode>> tests = new LinkedHashMap<>();
		
		/*
		File testDir = new File("res/specjvm2008");
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
		}*/

		tests.put("procyon", getMethods(new JarInfo(new File("res/procyon.jar"))));
		
		tests.put("fernflower", getMethods(new JarInfo(new File("res/fernflower.jar"))));

		benchCFG(tests);
	}

	private static HashMap<String, Long> results = new LinkedHashMap<>();

	private static void benchCopies(HashMap<String, List<MethodNode>> tests) throws IOException {
		for (Entry<String, List<MethodNode>> test : tests.entrySet()) {
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
		results.put(key, results.getOrDefault(key, 0L) + countCopies(cfg));
	}
	
	private static void benchCFG(HashMap<String, List<MethodNode>> tests) throws IOException {
		for (Entry<String, List<MethodNode>> test : tests.entrySet()) {
			results.clear();
			int k = 0;
			for (MethodNode m : test.getValue()) {
				k++;
				System.out.println("  " + m.toString() + " (" + k + " / " + test.getValue().size() + ")");
				ControlFlowGraph cfg;
				
				SSAGenPass.DO_SPLIT = false;
				SSAGenPass.SKIP_SIMPLE_COPY_SPLIT = false;
				cfg = ControlFlowGraphBuilder.build(m);
				recordHandlers(cfg, "NO_SPLIT");
				
				SSAGenPass.DO_SPLIT = true;
				SSAGenPass.SKIP_SIMPLE_COPY_SPLIT = false;
				cfg = ControlFlowGraphBuilder.build(m);
				recordHandlers(cfg, "SPLIT");
				
				SSAGenPass.DO_SPLIT = true;
				SSAGenPass.SKIP_SIMPLE_COPY_SPLIT = true;
				cfg = ControlFlowGraphBuilder.build(m);
				recordHandlers(cfg, "SPLIT_SKIP");
			}
			printResults(test.getKey());
		}
		printResultsHeader();
	}
	
	private static void recordHandlers(ControlFlowGraph cfg, String key) {
		int count = 0;
		for (BasicBlock b : cfg.vertices())
			for (FlowEdge<BasicBlock> e : cfg.getEdges(b))
				if (e instanceof TryCatchEdge)
					count++;
		results.put(key, results.getOrDefault(key, 0L) + count);
	}

	private static void benchmark(HashMap<String, List<MethodNode>> tests) throws IOException {
		final int NUM_ITER = 1000;
//		System.in.read();

		for (Entry<String, List<MethodNode>> test : tests.entrySet()) {
			results.clear();
			int k = 0;
			for (MethodNode m : test.getValue()) {
				k++;
				System.out.println("  " + m.toString() + " (" + k + " / " + test.getValue().size() + ")");
				try {
					final ControlFlowGraph cfgOrig = ControlFlowGraphBuilder.build(m);
					for (int i = 0; i < NUM_ITER; i++) {
						time();
						ControlFlowGraph cfg = deepCopyCfg(cfgOrig);
						new SreedharDestructor(cfg);
						time("Sreedhar3");
					}
					
					for (int i = 0; i < NUM_ITER; i++) {
						ControlFlowGraph cfg = deepCopyCfg(cfgOrig);
						DominanceLivenessAnalyser resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b0000);
						time("Boissinot");
					}
					
					for (int i = 0; i < NUM_ITER; i++) {
						ControlFlowGraph cfg = deepCopyCfg(cfgOrig);
						DominanceLivenessAnalyser resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b0001);
						time("BValue");
					}
					
					for (int i = 0; i < NUM_ITER; i++) {
						ControlFlowGraph cfg = deepCopyCfg(cfgOrig);
						DominanceLivenessAnalyser resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b0011);
						time("BSharing");
					}
				} catch (UnsupportedOperationException e) {
					System.err.println(e.getMessage());
				} catch (RuntimeException e) {
					throw new RuntimeException(e);
				}
			}
			normalizeResults(test.getValue().size());
			printResults(test.getKey());
		}
		printResultsHeader();
	}

	private static ControlFlowGraph deepCopyCfg(ControlFlowGraph cfg) {
		// Just make a new cfg. too much scaffolding to take care of with copy()
		ControlFlowGraph copy = new ControlFlowGraph(cfg.getMethod(), cfg.getLocals().getBase());
		
		// Copy blocks
		Map<BasicBlock, BasicBlock> map = new HashMap<>();
		for (BasicBlock b : cfg.vertices()) {
			BasicBlock bCopy = new BasicBlock(copy, b.getNumericId() + 1000, b.getLabelNode());
			// Copy statements
			for (Statement stmt : b)
				bCopy.add(stmt.copy());
			copy.addVertex(bCopy);
			map.put(b, bCopy);
		}
		
		// Update locals handler
		for (BasicBlock bCopy : copy.vertices()) {
			for (Statement sCopy : bCopy) {
				if (sCopy instanceof AbstractCopyStatement) {
					AbstractCopyStatement copyStmt = (AbstractCopyStatement) sCopy;
					Local l = copyStmt.getVariable().getLocal();
					if (l instanceof VersionedLocal) {
						VersionedLocal vl = (VersionedLocal) l;
						copy.getLocals().get(vl.getIndex(), vl.getSubscript(), vl.isStack());
					} else {
						copy.getLocals().get(l.getIndex(), l.isStack());
					}
					
					if (copyStmt instanceof CopyPhiStatement) {
						PhiExpression phi = (PhiExpression) copyStmt.getExpression();
						for (Expression arg : phi.getArguments().values()) {
							if (arg instanceof VarExpression) {
								l = ((VarExpression) arg).getLocal();
								if (l instanceof VersionedLocal) {
									VersionedLocal vl = (VersionedLocal) l;
									copy.getLocals().get(vl.getIndex(), vl.getSubscript(), vl.isStack());
								} else {
									copy.getLocals().get(l.getIndex(), l.isStack());
								}
							} else for (Statement child : arg) {
								if (child instanceof VarExpression) {
									l = ((VarExpression) child).getLocal();
									if (l instanceof VersionedLocal) {
										VersionedLocal vl = (VersionedLocal) l;
										copy.getLocals().get(vl.getIndex(), vl.getSubscript(), vl.isStack());
									} else {
										copy.getLocals().get(l.getIndex(), l.isStack());
									}
								}
							}
						}
					}
				}
				
				for (Statement child : sCopy) {
					if (child instanceof VarExpression) {
						Local l = ((VarExpression) child).getLocal();
						if (l instanceof VersionedLocal) {
							VersionedLocal vl = (VersionedLocal) l;
							copy.getLocals().get(vl.getIndex(), vl.getSubscript(), vl.isStack());
						} else {
							copy.getLocals().get(l.getIndex(), l.isStack());
						}
					}
				}
			}
		}
		
		// Fix jump targets
		for (BasicBlock bCopy : copy.vertices()) {
			if (bCopy.isEmpty())
				continue;
			Statement last = bCopy.get(bCopy.size() - 1);
			if (!last.canChangeFlow())
				continue;
			if (last instanceof UnconditionalJumpStatement) {
				UnconditionalJumpStatement jump = (UnconditionalJumpStatement) last;
				jump.setTarget(map.get(jump.getTarget()));
			} else if (last instanceof ConditionalJumpStatement) {
				ConditionalJumpStatement cjmp = (ConditionalJumpStatement) last;
				cjmp.setTrueSuccessor(map.get(cjmp.getTrueSuccessor()));
			} else if (last instanceof SwitchStatement) {
				SwitchStatement swtch = (SwitchStatement) last;
				for (Entry<Integer, BasicBlock> en : swtch.getTargets().entrySet())
					en.setValue(map.get(en.getValue()));
			}
		}
		
		// Fix phis
		for (BasicBlock bCopy : copy.vertices()) {
			for (int i = 0; i < bCopy.size() && bCopy.get(i) instanceof CopyPhiStatement; i++) {
				CopyPhiStatement copyPhi = (CopyPhiStatement) bCopy.get(i);
				PhiExpression phi = copyPhi.getExpression();
				Map<BasicBlock, Expression> phiArgsCopy = new HashMap<>(phi.getArguments());
				phi.getArguments().clear();
				for (Entry<BasicBlock, Expression> arg : phiArgsCopy.entrySet())
					phi.getArguments().put(map.get(arg.getKey()), arg.getValue());
			}
		}
		
		// Copy ranges
		Map<ExceptionRange<BasicBlock>, ExceptionRange<BasicBlock>> rangeMap = new HashMap<>();
		for (ExceptionRange<BasicBlock> range : cfg.getRanges()) {
			ExceptionRange<BasicBlock> rCopy = new ExceptionRange<>(range.getNode());
			for (BasicBlock b : range.getNodes())
				rCopy.addVertex(map.get(b));
			for (String type : range.getTypes())
				rCopy.addType(type);
			rCopy.setHandler(map.get(range.getHandler()));
			copy.addRange(rCopy);
			rangeMap.put(range, rCopy);
		}
		
		// Copy edges
		for (BasicBlock b : cfg.vertices()) {
			for (FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
				if (e instanceof TryCatchEdge)
					copy.addEdge(map.get(b), new TryCatchEdge<>(map.get(b), rangeMap.get(((TryCatchEdge) e).erange)));
				else
					copy.addEdge(map.get(b), e.clone(map.get(e.src), map.get(e.dst)));
			}
		}
		
		// Copy entries
		for (BasicBlock entry : cfg.getEntries())
			copy.getEntries().add(map.get(entry));
		
		return copy;
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

	private static void normalizeResults(int size) {
		for (Entry<String, Long> result : results.entrySet()) {
			result.setValue(result.getValue() / size);
		}
	}
	
	private static void printResults(String testName) {
		System.out.print(testName + ",");
		for (Iterator<Long> iterator = results.values().iterator(); iterator.hasNext();) {
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
		results.put(key, results.getOrDefault(key, 0L) + elapsed);
		now = -1L;
	}

	private static List<MethodNode> getMethods(File[] files) throws IOException {
		List<MethodNode> methods = new ArrayList<>();
		for (File f : files)
			for (MethodNode m : getMethods(f))
				methods.add(m);
		return methods;
	}

	private static List<MethodNode> getMethods(File f) throws IOException {
		InputStream is = new FileInputStream(f);
		ClassReader cr = new ClassReader(is);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		return cn.methods;
	}

	private static List<MethodNode> getMethods(JarInfo jar) throws IOException {
		List<MethodNode> methods = new ArrayList<>();
		NodeTable<ClassNode> nt = new NodeTable<>();
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(jar);
		dl.download();
		nt.putAll(dl.getJarContents().getClassContents().namedMap());
		for (ClassNode cn : nt)
			methods.addAll(cn.methods);
		return methods;
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