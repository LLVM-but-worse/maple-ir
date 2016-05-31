package org.rsdeob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementBuilder;
import org.rsdeob.stdlib.cfg.ir.StatementGenerator;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.expr.ArithmeticExpression.Operator;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.ConditionalJumpStatement.ComparisonType;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.transform.impl.LivenessAnalyser;
import org.rsdeob.stdlib.cfg.ir.transform.impl.DefinitionAnalyser;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class LivenessTest {

	public static void main(String[] args) throws Exception {
		ClassNode cn = new ClassNode();
		ClassReader cr = new ClassReader(LivenessTest.class.getCanonicalName());
		cr.accept(cn, 0);
		
		for(MethodNode m : cn.methods) {
			if(m.name.startsWith("test1")) {
				ControlFlowGraphBuilder cfgbuilder = new ControlFlowGraphBuilder(m);
				ControlFlowGraph cfg = cfgbuilder.build();
				
				ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
				List<BasicBlock> blocks = deobber.deobfuscate(cfg);
				GraphUtils.naturaliseGraph(cfg, blocks);
				
				StatementGenerator generator = new StatementGenerator(cfg);
				generator.init(m.maxLocals);
				generator.createExpressions();
				RootStatement root = generator.buildRoot();
				
				StatementGraph sgraph = StatementGraphBuilder.create(cfg);
				System.out.println("Processing " + m);
				System.out.println(cfg);
				System.out.println(root);
				System.out.println();
				
//				TrackerImpl ffa = new TrackerImpl(sgraph, m);
//				ffa.run();
				
//				for(Statement stmt : sgraph.vertices()) {
//					System.out.println(stmt);
//					System.out.println("  IN:");
//					NullPermeableHashMap<String, Set<CopyVarStatement>> in = ffa.in(stmt);
//					List<String> inVars = new ArrayList<>(in.keySet());
//					Collections.sort(inVars);
//					for(String var : inVars) {
//						System.out.println("     ");
//					}
//				}
				simplify(root, sgraph, m);
				
//				for(Statement stmt : sgraph.vertices()) {
//					System.out.println(stmt);
//					System.out.println("  IN:");
//					Map<String, Boolean> in = la.in(stmt);
//					List<String> inVars = new ArrayList<>(in.keySet());
//					Collections.sort(inVars);
//					for(String var : inVars) {
//						if(in.get(var)) {
//							System.out.println("     " + var + " is " + (in.get(var) ? "live" : "dead."));
//						}
//					}
//					System.out.println("  OUT:");
//					Map<String, Boolean> out = la.out(stmt);
//					List<String> outVars = new ArrayList<>(out.keySet());
//					Collections.sort(outVars);
//					for(String var : outVars) {
//						if(out.get(var)) {
//							System.out.println("     " + var + " is " + (out.get(var) ? "live" : "dead."));
//						}
//					}
//				}
			}
		}
	}
	
	private static void simplify(RootStatement root, StatementGraph graph, MethodNode m) {
//		System.out.println(graph);
		DefinitionAnalyser ffa = new DefinitionAnalyser(graph, m);
		ffa.run();
		ffa.propagate();
		System.out.println();
		System.out.println();
		System.out.println("After propagation");
		System.out.println();
		System.out.println();
		System.out.println(root);
//		System.out.println(graph);
		
		LivenessAnalyser la = new LivenessAnalyser(graph);
		la.run();
		
		for(Statement stmt : graph.vertices()) {
			if(stmt instanceof CopyVarStatement) {
//				Map<String, Boolean> in = la.in(stmt);
				Map<String, Boolean> out = la.out(stmt);
				
				CopyVarStatement copy = (CopyVarStatement) stmt;
				VarExpression var = copy.getVariable();
				
				if(!out.get(var.toString())) {
					System.out.println("redundant " + copy);
					// root.delete(copy.getChildPointer());
					root.delete(root.indexOf(copy));
				}
			}
		}
		
		System.out.println();
		System.out.println();
		System.out.println("After dead expression elimation");
		System.out.println();
		System.out.println();
		
		System.out.println(root);
	}
	
	void test1() {
		int x = 0;
		int z = 10;
		while(x <= 10) {
			x++;
			z = x;
		}
		System.out.println(x);
		System.out.println(z);
	}
	
	public static void main1(String[] args) throws Exception {		
		VarExpression x = new VarExpression(0, Type.INT_TYPE) {
			@Override
			public void toString(TabbedStringWriter printer) {
				printer.print('x');
			}
		};
		
		// x := 0
		// while(x != 10) {
		//    x = x + 1;
		// }

		StatementBuilder b = new StatementBuilder();
		b.add(b.assign(x, b.constant(0)));
		
		List<Statement> body = new ArrayList<>();
		body.add(b.assign(x, b.arithmetic(x, b.constant(1), Operator.ADD)));
		
		List<Statement> loop = b.whileloop(x, b.constant(10), ComparisonType.NE, body);
		for(Statement stmt : loop) {
			b.add(stmt);
		}
		
		b.add(b.call(Opcodes.INVOKESTATIC, "test", "use", "(I)V", new Expression[]{x}));
	}
}