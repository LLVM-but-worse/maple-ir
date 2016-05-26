package org.rsdeob.stdlib.cfg.ir.stat;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression.Precedence;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

public class FieldStoreStatement extends Statement {

	private Expression instanceExpression;
	private Expression valueExpression;
	private String owner;
	private String name;
	private String desc;

	public FieldStoreStatement(Expression instanceExpression, Expression valueExpression, String owner, String name, String desc) {
		this.instanceExpression = instanceExpression;
		this.valueExpression = valueExpression;
		this.owner = owner;
		this.name = name;
		this.desc = desc;
		
		overwrite(instanceExpression, 0);
		overwrite(valueExpression, instanceExpression == null ? 0 : 1);
	}

	public Expression getInstanceExpression() {
		return instanceExpression;
	}

	public void setInstanceExpression(Expression instanceExpression) {
		if (this.instanceExpression == null && instanceExpression != null) {
			this.instanceExpression = instanceExpression;
			overwrite(valueExpression, 1);
			overwrite(this.instanceExpression, 0);
		} else if (this.instanceExpression != null && instanceExpression == null) {
			this.instanceExpression = instanceExpression;
			overwrite(valueExpression, 0);
			overwrite(null, 1);
		} else {
			this.instanceExpression = instanceExpression;
			overwrite(this.instanceExpression, 0);
		}
	}

	public Expression getValueExpression() {
		return valueExpression;
	}

	public void setValueExpression(Expression valueExpression) {
		this.valueExpression = valueExpression;
		overwrite(valueExpression, instanceExpression == null ? 0 : 1);
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	@Override
	public void onChildUpdated(int ptr) {
		if (instanceExpression != null && ptr == 0) {
			setInstanceExpression((Expression) read(0));
		} else if (instanceExpression == null && ptr == 0) {
			setValueExpression((Expression) read(0));
		} else if (ptr == 1) {
			setValueExpression((Expression) read(1));
		}
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		if (instanceExpression != null) {
			int selfPriority = Precedence.MEMBER_ACCESS.ordinal();
			int basePriority = instanceExpression.getPrecedence();
			if (basePriority > selfPriority)
				printer.print('(');
			instanceExpression.toString(printer);
			if (basePriority > selfPriority)
				printer.print(')');
		} else
			printer.print(owner.replace('/', '.'));
		printer.print('.');
		printer.print(name);
		printer.print(" = ");
		valueExpression.toString(printer);
		printer.print(';');
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		if (instanceExpression != null)
			instanceExpression.toCode(visitor);
		valueExpression.toCode(visitor);
		if (TypeUtils.isPrimitive(Type.getType(desc))) {
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(valueExpression.getType(), Type.getType(desc));
			for (int i = 0; i < cast.length; i++)
				visitor.visitInsn(cast[i]);
		}
		visitor.visitFieldInsn(instanceExpression != null ? Opcodes.PUTFIELD : Opcodes.PUTSTATIC, owner, name, desc);		
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
		return stmt.canChangeLogic()
				|| (instanceExpression != null && instanceExpression.isAffectedBy(stmt)) 
				|| valueExpression.isAffectedBy(stmt);
	}
}