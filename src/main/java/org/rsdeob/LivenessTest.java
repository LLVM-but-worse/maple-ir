package org.rsdeob;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.ConditionalJumpStatement;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class LivenessTest {

	public static void main(String[] args) throws Exception {
		RootStatement root = new RootStatement(null);
		
		VarExpression x = new VarExpression(0, Type.INT_TYPE) {
			@Override
			public void toString(TabbedStringWriter printer) {
				printer.print('x');
			}
		};
		
		root.write(new CopyVarStatement(x, new ConstantExpression(0)));
		root.write(new ConditionalJumpStatement(x, new ConstantExpression(10), null, null));
		System.out.println(root);
	}
}