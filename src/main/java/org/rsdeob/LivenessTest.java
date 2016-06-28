package org.rsdeob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.ir.StatementGenerator;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.ir.StatementList;
import org.rsdeob.stdlib.ir.transform.impl.*;

import java.util.List;

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
				StatementList root = generator.buildRoot();
				
				StatementGraph sgraph = StatementGraphBuilder.create(cfg);
				System.out.println("Processing " + m);
				System.out.println(cfg);
				System.out.println("Unoptimised IR");
				System.out.println(root);
				System.out.println();

				optimise(cfg, root, sgraph, m);
				System.out.println("================");
				System.out.println("================");
				System.out.println("================");
				
				System.out.println("Optimised IR");
				System.out.println(root);
//				System.out.println("================");
//				System.out.println("================");
//				System.out.println("================");
				
//				new StatementVisitor(root) {
//					@Override
//					public Statement visit(Statement stmt) {
//						System.out.println(stmt);
//						return stmt;
//					}
//				}.visit();
			}
		}
	}
	
	public static void optimise(ControlFlowGraph cfg, StatementList root, StatementGraph graph, MethodNode m) {
		while(true) {
			int change = 0;
			DefinitionAnalyser defAnalyser = new DefinitionAnalyser(graph, m);
			LivenessAnalyser la = new LivenessAnalyser(graph);
			UsesAnalyser useAnalyser = new UsesAnalyser(graph, defAnalyser);
			CopyPropagator prop = new CopyPropagator(root, graph);
			
			change += prop.process(defAnalyser, useAnalyser, la);
			CodeAnalytics analytics = new CodeAnalytics(root, cfg, graph, defAnalyser, la, useAnalyser);
			change += DeadAssignmentEliminator.run(analytics);
			NewObjectInitialiserAggregator.run(analytics);
			
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