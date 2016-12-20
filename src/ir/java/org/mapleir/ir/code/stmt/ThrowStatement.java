package org.mapleir.ir.code.stmt;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ThrowStatement extends Stmt {

	private Expr expression;

	public ThrowStatement(Expr expression) {
		super(THROW);
		setExpression(expression);
	}

	public Expr getExpression() {
		return expression;
	}

	public void setExpression(Expr expression) {
		this.expression = expression;
		overwrite(expression, 0);
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression((Expr) read(ptr));
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("throw ");
		expression.toString(printer);
		printer.print(';');		
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		expression.toCode(visitor, cfg);
		visitor.visitInsn(Opcodes.ATHROW);		
	}

	@Override
	public boolean canChangeFlow() {
		return true;
	}

	@Override
	public boolean canChangeLogic() {
		return expression.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(CodeUnit stmt) {
		return expression.isAffectedBy(stmt);
	}

	@Override
	public ThrowStatement copy() {
		return new ThrowStatement(expression.copy());
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if(s instanceof ThrowStatement) {
			ThrowStatement thr = (ThrowStatement) s;
			return expression.equivalent(thr.expression);
		}
		return false;
	}
}