package org.rsdeob;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.StatementBuilder;
import org.rsdeob.stdlib.cfg.ir.expr.ArithmeticExpression.Operator;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.ConditionalJumpStatement.ComparisonType;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class LivenessTest {

	public static void main(String[] args) throws Exception {		
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
		
		System.out.println(b.getRoot());
	}
}