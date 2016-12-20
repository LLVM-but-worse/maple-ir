package org.mapleir.ir.code.stmt;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class PopStatement extends Stmt {

	private Expr expression;
	
	public PopStatement(Expr expression) {
		super(POP);
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
		setExpression((Expr)read(ptr));
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("_consume(");
		if(expression != null) {
			expression.toString(printer);
		} else {
			printer.print("_NULL_STMT_");
		}
		printer.print(");");		
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		expression.toCode(visitor, cfg);
		if (expression.getType() != Type.VOID_TYPE)
			visitor.visitInsn(TypeUtils.getPopOpcode(expression.getType()));	
	}

	@Override
	public boolean canChangeFlow() {
		return false;
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
	public PopStatement copy() {
		return new PopStatement(expression.copy());
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		return s instanceof PopStatement && expression.equivalent(((PopStatement) s).expression);
	}
}