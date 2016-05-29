package org.rsdeob;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.BootBibl.C2;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGenerator;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.transform1.BackwardsFlowAnalyser;
import org.rsdeob.stdlib.cfg.ir.transform1.VariableStateComputer;

public class DataFlowAnalyserBoot {

	public static void main(String[] args) throws Exception {
		ClassNode cn = new ClassNode();
		ClassReader cr = new ClassReader(DataFlowAnalyserBoot.class.getCanonicalName());
		cr.accept(cn, 0);
		
		for(MethodNode m : cn.methods) {
			if(m.name.startsWith("test3")) {
				ControlFlowGraph cfg = new ControlFlowGraphBuilder(m).build();
				StatementGenerator generator = new StatementGenerator(cfg);
				generator.init(m.maxLocals);
				generator.createExpressions();
				RootStatement root = generator.buildRoot();
				StatementGraph sgraph = StatementGraphBuilder.create(cfg);
				System.out.println("Processing " + m);
				// System.out.println(cfg);
				// System.out.println(sgraph);
				
				Map<Statement, Set<String>> kill = new HashMap<>();
				Map<Statement, Set<String>> gen = new HashMap<>();
				
				for(Statement stmt : sgraph.vertices()) {
					if(stmt instanceof CopyVarStatement) {
						Set<String> set = new HashSet<>();
						set.add(VariableStateComputer.createVariableName((CopyVarStatement) stmt));
						kill.put(stmt, set);
					} else {
						kill.put(stmt, new HashSet<>());
					}
					
					Set<String> set = new HashSet<>();
					StatementVisitor vis = new StatementVisitor(stmt) {
						@Override
						public void visit(Statement stmt) {
							if(stmt instanceof VarExpression) {
								set.add(VariableStateComputer.createVariableName((VarExpression) stmt));
							}
						}
					};
					gen.put(stmt, set);
					vis.visit();
				}
				
				
				BackwardsFlowAnalyser<Statement, FlowEdge<Statement>, Set<String>> bfa = new BackwardsFlowAnalyser<Statement, FlowEdge<Statement>, Set<String>>(sgraph) {

					@Override
					protected Set<String> newState() {
						return new HashSet<>();
					}

					@Override
					protected Set<String> newEntryState() {
						return new HashSet<>();
					}

					@Override
					protected void merge(Set<String> in1, Set<String> in2, Set<String> out) {
						
					}

					@Override
					protected void copy(Set<String> src, Set<String> dst) {
						dst.addAll(src);
					}

					@Override
					protected boolean equals(Set<String> s1, Set<String> s2) {
						return s1.equals(s2);
					}

					@Override
					protected void propagate(Statement n, Set<String> in, Set<String> out) {
						// System.out.println("Propagating across " + n);
						out.addAll(in);
						
						final VarExpression rhs;
						
						if(n instanceof CopyVarStatement) {
							CopyVarStatement stmt = (CopyVarStatement) n;
							String name = VariableStateComputer.createVariableName(stmt);
							out.remove(name);
							rhs = stmt.getVariable();
						} else {
							rhs = null;
						}
						
						StatementVisitor vis = new StatementVisitor(n) {
							@Override
							public void visit(Statement stmt) {
								if(n != rhs && stmt instanceof VarExpression) {
									VarExpression var = (VarExpression) stmt;
									String name = VariableStateComputer.createVariableName(var);
									out.add(name);
								}
							}
						};
						vis.visit();
					}
				};
				
				/* BackwardsFlowAnalyser<Statement, FlowEdge<Statement>, Set<String>> bfa = new BackwardsFlowAnalyser<Statement, FlowEdge<Statement>, Set<String>>(sgraph) {
					
					@Override
					protected void propagate(Statement n, Set<String> currentOut, Set<String> currentIn) {
						Set<String> toKill = kill.get(n);
						currentIn.clear();
						currentIn.addAll(toKill);
						for(String s : currentOut) {
							if(currentIn.contains(s)) {
								currentIn.remove(s);
							} else {
								currentIn.add(s);
							}
						}
						
						Set<String> toGen = gen.get(n);
						currentIn.addAll(toGen);
					}
					
					@Override
					protected Set<String> newState() {
						return new HashSet<>();
					}
					
					@Override
					protected Set<String> newEntryState() {
						return new HashSet<>();
					}
					
					@Override
					protected void merge(Set<String> in1, Set<String> in2, Set<String> out) {
						out.addAll(in1);
						out.addAll(in2);
					}
					
					@Override
					protected boolean equals(Set<String> s1, Set<String> s2) {
						return s1.equals(s2);
					}
					
					@Override
					protected void copy(Set<String> src, Set<String> dst) {
						dst.addAll(src);
					}
				}; */
				
				for(Statement stmt : sgraph.vertices()) {
					System.out.println(stmt);
					System.out.println("  IN:");
					for(String in : bfa.in(stmt)) {
						System.out.println("     " + in);
					}
					System.out.println("  OUT:");
					for(String in : bfa.out(stmt)) {
						System.out.println("     " + in);
					}
					System.out.println();
				}
				
				System.out.println();
			}
		}
	}
	
	public void test9(Object o) {
		synchronized (o == null ? this : o) {
			System.out.println(o);
			System.out.println(this);
			test8(5, 7, 0);
		}
	}
	
	public void test8(int i, int j, int k) {
		test7(5);
		if(i > 0) {
			test8(i - 1, j, k);
		}
		test7(16);
	}
	
	public void test7(int i) {
		System.out.println(i);
		System.out.println(i + " hi " + i);
	}

	public void test4() {
		BootBibl newBoot = new BootBibl();
		newBoot.DVAL += new C2().FVAL;
		new C2().FVAL = (float) newBoot.DVAL;
		
		System.out.println(newBoot);
	}
	
	public void test6() {
		for(int i=0; i < 3; i++) {
			test5();
		}
	}
	
	public void test5() {
		BootBibl boot = new BootBibl();
		boot.DVAL = 5;
	}
	
	double DVAL = 5D;
	float FVAL = 10F;
	String SVAL = "";
	
	public void test3() {
		double d = DVAL * 847545D;
		float f = FVAL * 8573845743F;
		double c = 0;
		if(d > f) {
			c = (d + f);
		} else {
			c = (d - f);
		}
		
		System.out.println(c);
	}
	
	public void test2(int i, int j, int k) {
		int s = 283472 * i;
		int d = j * 4334857;
		int f = 345345 * (34784 * k);
		
		System.out.println(s);
		System.out.println(d);
		System.out.println(f);
	}
	
	public void test(int i) {
		System.out.println("k " + i);
	}
	
	public float ternTest2(int i) {
		return testNormalReturn(i == 8 ? 5 : 10);
	}
	
	public float testNormalReturn(int i) {
		if(i == 1) {
			return 5F;
		} else {
			return 10F;
		}
	}
	
	public float ternTest(int i) {
		return i == 1 ? testNormalReturn(i) : testNormalReturn(i);
	}
}