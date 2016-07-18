package org.rsdeob;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.collections.graph.util.DotExporter;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.SSAGenerator;
import org.rsdeob.stdlib.ir.StatementGenerator;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.ir.export.SGDotExporter;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.SSATransformer;
import org.rsdeob.stdlib.ir.transform.Transformer;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;
import org.rsdeob.stdlib.ir.transform.impl.DefinitionAnalyser;
import org.rsdeob.stdlib.ir.transform.impl.LivenessAnalyser;
import org.rsdeob.stdlib.ir.transform.ssa.SSAInitialiserAggregator;
import org.rsdeob.stdlib.ir.transform.ssa.SSALocalAccess;
import org.rsdeob.stdlib.ir.transform.ssa.SSAPropagator;

public class AnalyticsTest {

	public static boolean debug = true;
	
	public static void main(String[] args) throws Throwable {
		InputStream i = new FileInputStream(new File("res/a.class"));
		ClassReader cr = new ClassReader(i);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		//
		Iterator<MethodNode> it = cn.methods.listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();

			if(!m.toString().equals("a/a/f/a.H(J)La/a/f/o;")) {
				continue;
			}
//			LocalsTest.main([Ljava/lang/String;)V
//			org/rsdeob/AnalyticsTest.tryidiots(I)V
//			a/a/f/a.<init>()V
//			a/a/f/a.H(J)La/a/f/o;
//			a/a/f/a.H(La/a/f/o;J)V
			System.out.println("Processing " + m + "\n");
			ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(m);
			ControlFlowGraph cfg = builder.build();
			ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
			List<BasicBlock> blocks = deobber.deobfuscate(cfg);
			deobber.removeEmptyBlocks(cfg, blocks);
			GraphUtils.naturaliseGraph(cfg, blocks);
			
			// GraphUtils.output(cfg, blocks, new File("C:/Users/Bibl/Desktop/cfg testing"), "test11");
			
			StatementGenerator gen = new StatementGenerator(cfg);
			gen.init(m.maxLocals);
			gen.createExpressions();
			CodeBody code = gen.buildRoot();

//			System.out.println(code);
//			System.out.println();

			SSAGenerator ssagen = new SSAGenerator(code, cfg, gen.getHeaders());
			ssagen.run();
			
			System.out.println("SSA:");
			System.out.println(code);
			System.out.println();
			System.out.println();

			StatementGraph sgraph = StatementGraphBuilder.create(cfg);
			SSALocalAccess localAccess = new SSALocalAccess(code);
			
			SSATransformer[] transforms = new SSATransformer[]{
				new SSAPropagator(code, localAccess, sgraph, gen.getHeaders().values()),
				new SSAInitialiserAggregator(code, localAccess, sgraph)
			};
			
			try {
				while(true) {
					int change = 0;
					for(SSATransformer t : transforms) {
						change += t.run();
					}
					if(change <= 0) {
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				// TODO: handle exception
			}
			
			System.out.println();
			System.out.println();
			System.out.println("Optimised SSA:");
			System.out.println(code);
			
//			for(Statement stmt : code) {
//				if(stmt._getId() == 121) {
//					new StatementVisitor(stmt) {
//						@Override
//						public Statement visit(Statement stmt) {
//							System.out.println(stmt);
//							return stmt;
//						}
//					}.visit();
//				}
//			}
			
//			System.out.println(localAccess.defs);
			SSALocalAccess real = new SSALocalAccess(code);
			Set<VersionedLocal> set = new HashSet<>(real.useCount.keySet());
			set.addAll(localAccess.useCount.keySet());
			List<VersionedLocal> keys = new ArrayList<>(set);
			Collections.sort(keys);
			for(VersionedLocal e : keys) {
				AtomicInteger i1 = real.useCount.get(e);
				AtomicInteger i2 = localAccess.useCount.get(e);
				if(i1 == null) {
					System.err.println("Real no contain: " + e + ", other: " + i2.get());
				} else if(i2 == null) {
					System.err.println("Current no contain: " + e + ", other: " + i1.get());
				} else if(i1.get() != i2.get()) {
					System.err.println("Mismatch: " + e + " " + i1.get() + ":" + i2.get());
				}
			}
		}
	}

	public static void optimise(CodeBody code, CodeAnalytics analytics) {
		Transformer[] transforms = LivenessTest.transforms(code, analytics);
		
		while(true) {
			int change = 0;

			for(Transformer t : transforms) {
				change += t.run();
			}
			
			if(change <= 0) {
				break;
			}
		}
	}
	
	public static void verify_callback(CodeBody code, CodeAnalytics analytics, Statement stmt) {
		code.commit();
		
		DefinitionAnalyser d1 = new DefinitionAnalyser(analytics.sgraph);
		LivenessAnalyser l1 = new LivenessAnalyser(analytics.sgraph);
		
		for(Statement s : code) {
			try {
				Map<Local, Set<CopyVarStatement>> din1 = analytics.definitions.in(s);
				Map<Local, Set<CopyVarStatement>> din2 = d1.in(s);
				Map<Local, Set<CopyVarStatement>> dout1 = analytics.definitions.out(s);
				Map<Local, Set<CopyVarStatement>> dout2 = d1.out(s);

				dcheck("DIN", din1, din2, analytics, code, stmt, s);
				dcheck("DOUT", dout1, dout2, analytics, code, stmt, s);
				
				Map<Local, Boolean> lin1 = analytics.liveness.in(s);
				Map<Local, Boolean> lin2 = l1.in(s);
				Map<Local, Boolean> lout1 = analytics.liveness.out(s);
				Map<Local, Boolean> lout2 = l1.out(s);

				lcheck("LIN", lin1, lin2, analytics, code, stmt, s);
				lcheck("LOUT", lout1, lout2, analytics, code, stmt, s);
			} catch(Exception e) {
				if(e instanceof RuntimeException) {
					throw (RuntimeException)e;
				} else {
					throw new RuntimeException(e);
				}
			}
		}
	}
	
	public static void lcheck(String type, Map<Local, Boolean> s1, Map<Local, Boolean> s2, CodeAnalytics analytics, CodeBody code, Statement stmt, Statement cur) throws Exception {
		if(!(cur instanceof HeaderStatement) && !lcheck0(s1, s2)) {
			System.err.println();
			System.err.println(code);
			System.err.println("Fail: " + stmt.getId() + ". " + stmt);
			System.err.println(" Cur: " + cur.getId() + ". " + cur);
			System.err.println("  Actual " + type + "1:");
			if(s1 != null) {
				for(Entry<Local, Boolean> e : s1.entrySet()) {
					System.err.println("    " + e.getKey() + " = " + e.getValue());
				}
			} else {
				System.err.println("   NULL");
			}
			System.err.println("  Should " + type + "2:");
			if(s2 != null) {
				for(Entry<Local, Boolean> e : s2.entrySet()) {
					System.err.println("    " + e.getKey() + " = " + e.getValue());
				}
			} else {
				System.err.println("   NULL");
			}
			SGDotExporter exporter = new SGDotExporter(analytics.sgraph, code, "failedat_lcheck", "-sg");
			exporter.addHighlight(stmt, "turquoise2");
			exporter.addHighlight(cur, "yellow2");
			exporter.output(DotExporter.OPT_DEEP);
			throw new RuntimeException();
		}
	}
	
	public static boolean lcheck0(Map<Local, Boolean> s1, Map<Local, Boolean> s2) {
		if(s1 == null || s2 == null) {
			return false;
		}
		
		Set<Local> keys = new HashSet<>();
		keys.addAll(s1.keySet());
		keys.addAll(s2.keySet());
		
		for(Local key : keys) {
			if(!s1.containsKey(key)) {
				// return false;
			} else if(!s2.containsKey(key)) {
				// return false;
			} else {
				if(s1.get(key).booleanValue() != s2.get(key).booleanValue()) {
					return false;
				}
			}
		}
		
		return true;
	}
	
	public static void dcheck(String type, Map<Local, Set<CopyVarStatement>> s1, Map<Local, Set<CopyVarStatement>> s2, CodeAnalytics analytics, CodeBody code, Statement stmt, Statement cur) throws Exception {
		if(!(s1 == null && s2 == null) && !dcheck0(s1, s2)) {
			System.err.println();
			System.err.println(code);
			System.err.println("Fail: " + stmt.getId() + ". " + stmt);
			System.err.println(" Cur: " + cur.getId() + ". " + cur);
			System.err.println("  Actual " + type + "1:");
			if(s1 != null) {
				for(Entry<Local, Set<CopyVarStatement>> e : s1.entrySet()) {
					System.err.println("    " + e.getKey() + " = " + e.getValue());
				}
			} else {
				System.err.println("   NULL");
			}
			System.err.println("  Should " + type + "2:");
			if(s2 != null) {
				for(Entry<Local, Set<CopyVarStatement>> e : s2.entrySet()) {
					System.err.println("    " + e.getKey() + " = " + e.getValue());
				}
			} else {
				System.err.println("   NULL");
			}
			SGDotExporter exporter = new SGDotExporter(analytics.sgraph, code, "failedat_dcheck", "-sg");
			exporter.addLabel(stmt, "Fail");
			exporter.addHighlight(stmt, "turquoise2");
			exporter.addLabel(cur, "Cur");
			exporter.addHighlight(cur, "yellow2");
			exporter.output(DotExporter.OPT_DEEP);
			throw new RuntimeException();
		}
	}
	
	public static boolean dcheck0(Map<Local, Set<CopyVarStatement>> s1, Map<Local, Set<CopyVarStatement>> s2) {
		if(s1 == null || s2 == null) {
			return false;
		}
		
		Set<Local> keys = new HashSet<>();
		keys.addAll(s1.keySet());
		keys.addAll(s2.keySet());
		
		for(Local key : keys) {
			Set<CopyVarStatement> set1 = s1.get(key);
			Set<CopyVarStatement> set2 = s2.get(key);
			
			if(set1 == null && set2.isEmpty()) {
				continue;
			} else if(set2 == null && set1.isEmpty()) {
				continue;
			}
			
			if(!set1.equals(set2)) {
				for(Local l : keys) {
					if(s1.get(l) == null || s1.get(l).isEmpty()) {
						s1.remove(l);
					}
					if(s2.get(l) == null || s2.get(l).isEmpty()) {
						s2.remove(l);
					}
				}
				System.err.println("[D] mistmatch for " + key);
				return false;
			}
		}
		
		return true;
	}

	public void tryidiots(int x) {
		int y = 0;
		try {
			if(x == 5) {
				y = 2;
			} else {
				y = 3;
			}
		} catch(Exception e) {
			System.out.println(e.getMessage() + " " + y);
		}
	}
}