package org.rsdeob.stdlib.ir.expr;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public class FieldLoadExpression extends Expression {

	private Expression instanceExpression;
	private String owner;
	private String name;
	private String desc;

	public FieldLoadExpression(Expression instanceExpression, String owner, String name, String desc) {
		setInstanceExpression(instanceExpression);
		this.owner = owner;
		this.name = name;
		this.desc = desc;
	}

	public Expression getInstanceExpression() {
		return instanceExpression;
	}

	public void setInstanceExpression(Expression instanceExpression) {
		this.instanceExpression = instanceExpression;
		overwrite(instanceExpression, 0);
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
	public Expression copy() {
		return new FieldLoadExpression(instanceExpression, owner, name, desc);
	}

	@Override
	public Type getType() {
		return Type.getType(desc);
	}

	@Override
	public void onChildUpdated(int ptr) {
		setInstanceExpression((Expression) read(ptr));
	}
	
	@Override
	public Precedence getPrecedence0() {
		return instanceExpression != null ? Precedence.MEMBER_ACCESS : Precedence.NORMAL;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		if (instanceExpression != null) {
			int selfPriority = getPrecedence();
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
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		if (instanceExpression != null) {
			instanceExpression.toCode(visitor, analytics);
		}
		visitor.visitFieldInsn(instanceExpression != null ? Opcodes.GETFIELD : Opcodes.GETSTATIC, owner, name, desc);		
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return instanceExpression != null && instanceExpression.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return stmt.canChangeLogic() || (instanceExpression != null && instanceExpression.isAffectedBy(stmt));
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof FieldLoadExpression) {
			FieldLoadExpression load = (FieldLoadExpression) s;
			return instanceExpression.equivalent(load.instanceExpression) &&
					name.equals(load.name) && desc.equals(load.desc) && owner.equals(load.owner);
		}
		return false;
	}
}