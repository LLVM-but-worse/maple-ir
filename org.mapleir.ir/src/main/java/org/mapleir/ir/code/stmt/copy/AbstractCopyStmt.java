package org.mapleir.ir.code.stmt.copy;

import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.codegen.BytecodeFrontend;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public abstract class AbstractCopyStmt extends Stmt {

	private final boolean synthetic;
	private Expr expression;
	private VarExpr variable;
	
	public AbstractCopyStmt(int opcode, VarExpr variable, Expr expression) {
		this(opcode, variable, expression, false);
	}
	
	public AbstractCopyStmt(int opcode, VarExpr variable, Expr expression, boolean synthetic) {
		super(opcode);
		
		if (variable == null | expression == null)
			throw new IllegalArgumentException("Neither variable nor statement can be null!");
		
		this.synthetic = synthetic;
		this.expression = expression;
		this.variable = variable;
		
		if(!synthetic) {
			overwrite(expression, 0);
		}
	}
	
	public boolean isSynthetic() {
		return synthetic;
	}

	public VarExpr getVariable() {
		return variable;
	}

	public void setVariable(VarExpr var) {
		variable = var;
	}
	
	public Expr getExpression() {
		return expression;
	}
	
	public void setExpression(Expr expression) {
		this.expression = expression;
		if(!synthetic) {
			overwrite(expression, 0);
		}
	}
	
	public int getIndex() {
		return variable.getLocal().getIndex();
	}

	public Type getType() {
		return variable.getType();
	}

	@Override
	public void onChildUpdated(int ptr) {
		if(!synthetic) {
			setExpression(read(ptr));
		}
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print(toString());
	}
	
	@Override
	public String toString() {
		if(synthetic) {
			return "synth(" + variable + " = " + expression + ");";
		} else {
			return variable + " = " + expression + ";";
		}
	}

	@Override
	// todo: this probably needs a refactoring
	public void toCode(MethodVisitor visitor, BytecodeFrontend assembler) {
		if(expression instanceof VarExpr) {
			if(((VarExpr) expression).getLocal() == variable.getLocal()) {
				return;
			}
		}
		
		variable.getLocal().setTempLocal(false);
		
		expression.toCode(visitor, assembler);
		Type type = variable.getType();
		if (TypeUtils.isPrimitive(type)) {
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), type);
			for (int i = 0; i < cast.length; i++)
				visitor.visitInsn(cast[i]);
		}

		Local local = variable.getLocal();
		if(local.isStack()) {
			visitor.visitVarInsn(TypeUtils.getVariableStoreOpcode(getType()), variable.getLocal().getCodeIndex());
			variable.getLocal().setTempLocal(true);
		} else {
			visitor.visitVarInsn(TypeUtils.getVariableStoreOpcode(getType()), variable.getLocal().getCodeIndex());
		}
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}
	
	public boolean isRedundant() {
		if(!synthetic && expression instanceof VarExpr) {
			return ((VarExpr) expression).getLocal() == variable.getLocal();
		} else {
			return false;
		}
	}

	@Override
	public abstract AbstractCopyStmt copy();

	@Override
	public abstract boolean equivalent(CodeUnit s);
}