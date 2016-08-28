package org.mapleir.ir.code.stmt;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MonitorStatement extends Statement {

	public enum MonitorMode {
		ENTER, EXIT;
	}

	private Expression expression;
	private MonitorMode mode;

	public MonitorStatement(Expression expression, MonitorMode mode) {
		super(MONITOR);
		setExpression(expression);
		this.mode = mode;
	}

	public void setExpression(Expression expression) {
		this.expression = expression;
		overwrite(expression, 0);
	}

	public Expression getExpression() {
		return expression;
	}

	public MonitorMode getMode() {
		return mode;
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression((Expression) read(ptr));
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print(mode == MonitorMode.ENTER ? "MONITORENTER" : "MONITOREXIT");
		printer.print('(');
		expression.toString(printer);
		printer.print(')');
		printer.print(';');		
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		expression.toCode(visitor, cfg);
		visitor.visitInsn(mode == MonitorMode.ENTER ? Opcodes.MONITORENTER : Opcodes.MONITOREXIT);		
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return true;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return stmt.canChangeLogic() || expression.isAffectedBy(stmt);
	}

	@Override
	public Statement copy() {
		return new MonitorStatement(expression, mode);
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof MonitorStatement) {
			MonitorStatement mon = (MonitorStatement) s;
			return mode == mon.mode && expression.equivalent(mon.expression);
		}
		return false;
	}
}