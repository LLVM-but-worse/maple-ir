package org.rsdeob.stdlib.cfg.ir.transform;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class DataFlowExpression extends Expression {
	
	public static final DataFlowExpression TOP_EXPR = new DataFlowExpression();
	public static final DataFlowExpression BOTTOM_EXPR = new DataFlowExpression();
	
	@Override
	public String toString() {
		return this == TOP_EXPR ? "T" : "_|_";
	}

	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		throw new UnsupportedOperationException("TopExpression is for data flow use only");
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return false;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return false;
	}

	@Override
	public Expression copy() {
		throw new UnsupportedOperationException("Do not copy TopExpression; instantiate a new one instead");
	}

	@Override
	public Type getType() {
		throw new UnsupportedOperationException("TopExpression has no type");
	}
}