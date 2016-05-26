package org.rsdeob.stdlib.cfg.ir.expr;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class SyntheticExpression extends Expression {

	private Expression expr;
	
	public SyntheticExpression(Expression expr) {
		this.expr = expr;
		overwrite(expr, 0);
	}
	
	@Override
	public void onChildUpdated(int ptr) {
		expr = (Expression) read(ptr);
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("synth(");
		expr.toString(printer);
		printer.print(")");
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		expr.toCode(visitor);
	}

	@Override
	public boolean canChangeFlow() {
		return expr.canChangeFlow();
	}

	@Override
	public boolean canChangeLogic() {
		return expr.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return expr.isAffectedBy(stmt);
	}

	@Override
	public Expression copy() {
		return new SyntheticExpression(expr);
	}

	@Override
	public Type getType() {
		return expr.getType();
	}
	
	public Expression getExpression() {
		return expr;
	}
}