package org.mapleir.ir.code.stmt;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MonitorStmt extends Stmt {

	public enum MonitorMode {
		ENTER, EXIT;
	}

	private Expr expression;
	private MonitorMode mode;

	public MonitorStmt(Expr expression, MonitorMode mode) {
		super(MONITOR);
		setExpression(expression);
		this.mode = mode;
	}

	public void setExpression(Expr expression) {
		this.expression = expression;
		overwrite(expression, 0);
	}

	public Expr getExpression() {
		return expression;
	}

	public MonitorMode getMode() {
		return mode;
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression(read(ptr));
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
	public boolean isAffectedBy(CodeUnit stmt) {
		return stmt.canChangeLogic() || expression.isAffectedBy(stmt);
	}

	@Override
	public MonitorStmt copy() {
		return new MonitorStmt(expression.copy(), mode);
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if(s instanceof MonitorStmt) {
			MonitorStmt mon = (MonitorStmt) s;
			return mode == mon.mode && expression.equivalent(mon.expression);
		}
		return false;
	}
}