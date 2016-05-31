package org.rsdeob;

import java.util.ArrayList;
import java.util.List;

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
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.transform.impl.DefinitionAnalyser;
import org.rsdeob.stdlib.cfg.ir.transform.impl.ValuePropagator;
import org.rsdeob.stdlib.cfg.util.AnalysisHelper;
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

				simplify(root, sgraph, m);
				System.out.println(root);
			}
		}
	}
	
	public static void simplify(RootStatement root, StatementGraph graph, MethodNode m) {
		
		DefinitionAnalyser defAnalyser = new DefinitionAnalyser(graph, m);
		defAnalyser.run();
		
		for(Statement stmt : graph.vertices()) {
			System.out.println(stmt);
			System.out.println("   IN:");
			AnalysisHelper.print(defAnalyser.in(stmt));
			System.out.println("   OUT:");
			AnalysisHelper.print(defAnalyser.out(stmt));
		}
		
		
		ValuePropagator.propagateDefinitions(graph, defAnalyser);
//		la = new LivenessAnalyser(graph);
//		la.run();
//		DeadAssignmentEliminator.run(root, graph, la);
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