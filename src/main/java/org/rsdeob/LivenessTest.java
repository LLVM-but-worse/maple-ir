package org.rsdeob;

import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGenerator;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.transform.impl.DeadAssignmentEliminator;
import org.rsdeob.stdlib.cfg.ir.transform.impl.DefinitionAnalyser;
import org.rsdeob.stdlib.cfg.ir.transform.impl.LivenessAnalyser;
import org.rsdeob.stdlib.cfg.ir.transform.impl.NewValuePropagator;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.cfg.util.GraphUtils;

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

				simplify(cfg, root, sgraph, m);
//				System.out.println(root);
			}
		}
	}
	
	public static void simplify(ControlFlowGraph cfg, RootStatement root, StatementGraph graph, MethodNode m) {
		while(true) {
			int change = 0;
//			System.out.println("graph1: ");
//			System.out.println(graph);
			System.out.println("LivenessTest.simplify(0)");

			DefinitionAnalyser defAnalyser = new DefinitionAnalyser(graph, m);
			defAnalyser.run();
			
//			for(Statement stmt : graph.vertices()) {
//				System.out.println(stmt);
//				List<String> keys = new ArrayList<>(defAnalyser.in(stmt).keySet());
//				Collections.sort(keys);
//				System.out.println("  IN:");
//				for(String key : keys) {
//					System.out.println("     " + key + " = " + defAnalyser.in(stmt).get(key));
//				}
//				keys = new ArrayList<>(defAnalyser.out(stmt).keySet());
//				Collections.sort(keys);
//				System.out.println("  OUT:");
//				for(String key : keys) {
//					System.out.println("     " + key + " = " + defAnalyser.out(stmt).get(key));
//				}				
//			}
			System.out.println("LivenessTest.simplify(2)");
			// change += ValuePropagator.propagateDefinitions1(root, graph, defAnalyser);
//			change += CopyPropagator.propagateDefinitions(cfg, root, defAnalyser);
			

			LivenessAnalyser la = new LivenessAnalyser(graph);
			la.run();
			
			System.out.println("LivenessTest.simplify(1)");
			NewValuePropagator prop = new NewValuePropagator(root, graph);
			prop.process(defAnalyser, la);
			
//			System.out.println();
//			System.out.println();
//			System.out.println("After propagation");
//			System.out.println(root);
//			System.out.println();
//			System.out.println();
			
//			System.out.println("graph2: ");
//			System.out.println(graph);
			
			change += DeadAssignmentEliminator.run(root, graph, la);
			
			
			System.out.println();
			System.out.println();
//			System.out.println("After elimination");
			System.out.println(root);
//			System.out.println();
//			System.out.println();
//			
			
			if(change <= 0) {
				break;
			}
			
			
		}
	}
	
	void test1() {
		int x = 0;
		int z = 10;
		while(x <= 10) {
			x++;
			z = x;
		}
		System.out.println(x);
		System.out.println(x);
	}
	
//	public static void main1(String[] args) throws Exception {		
//		VarExpression x = new VarExpression(0, Type.INT_TYPE) {
//			@Override
//			public void toString(TabbedStringWriter printer) {
//				printer.print('x');
//			}
//		};
//		
//		// x := 0
//		// while(x != 10) {
//		//    x = x + 1;
//		// }
//
//		StatementBuilder b = new StatementBuilder();
//		b.add(b.assign(x, b.constant(0)));
//		
//		List<Statement> body = new ArrayList<>();
//		body.add(b.assign(x, b.arithmetic(x, b.constant(1), Operator.ADD)));
//		
//		List<Statement> loop = b.whileloop(x, b.constant(10), ComparisonType.NE, body);
//		for(Statement stmt : loop) {
//			b.add(stmt);
//		}
//		
//		b.add(b.call(Opcodes.INVOKESTATIC, "test", "use", "(I)V", new Expression[]{x}));
//	}
}