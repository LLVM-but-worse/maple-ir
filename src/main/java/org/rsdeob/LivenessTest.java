package org.rsdeob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.util.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.collections.graph.util.GraphUtils;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.gen.StatementGenerator;
import org.rsdeob.stdlib.ir.gen.StatementGraphBuilder;
import org.rsdeob.stdlib.ir.transform.Transformer;
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
				CodeBody stmtList = generator.buildRoot();
				
				StatementGraph sgraph = StatementGraphBuilder.create(cfg);
				System.out.println("Processing " + m);
				System.out.println(cfg);
				System.out.println("Unoptimised IR");
				System.out.println(stmtList);
				System.out.println();

				optimise(cfg, stmtList, sgraph);
				System.out.println("================");
				System.out.println("================");
				System.out.println("================");
				
				System.out.println("Optimised IR");
				System.out.println(stmtList);
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
	
	public static void optimise(CodeBody code, CodeAnalytics analytics) {
		Transformer[] transforms = transforms(code, analytics);
		
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
	
	public static void optimise(ControlFlowGraph cfg, CodeBody stmtList, StatementGraph graph) {
		DefinitionAnalyser defAnalyser = new DefinitionAnalyser(graph);
		LivenessAnalyser la = new LivenessAnalyser(graph);
		UsesAnalyserImpl useAnalyser = new UsesAnalyserImpl(stmtList, graph, defAnalyser);
		CodeAnalytics analytics = new CodeAnalytics(cfg, graph, defAnalyser, la, useAnalyser);
		stmtList.registerListener(analytics);
		
		Transformer[] transforms = transforms(stmtList, analytics);
		
		while(true) {
			int change = 0;

			for(Transformer t : transforms) {
				change += t.run();
			}
			
			if(change <= 0) {
				break;
			}
		}
		
		stmtList.unregisterListener(analytics);
	}
	
	static Transformer[] transforms(CodeBody body, CodeAnalytics analytics) {
		return new Transformer[] {
			new CopyPropagator(body, analytics),
			new DeadAssignmentEliminator(body, analytics),
			new NewObjectInitialiserAggregator(body, analytics)
		};
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