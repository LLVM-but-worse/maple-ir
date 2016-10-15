package org.mapleir.ir.code.expr;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class FieldLoadExpression extends Expression {

	private Expression instanceExpression;
	private String owner;
	private String name;
	private String desc;

	public FieldLoadExpression(Expression instanceExpression, String owner, String name, String desc) {
		super(FIELD_LOAD);
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
		return new FieldLoadExpression(instanceExpression == null ? null : instanceExpression.copy(), owner, name, desc);
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
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		if (instanceExpression != null) {
			instanceExpression.toCode(visitor, cfg);
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
			if(instanceExpression != null && load.instanceExpression == null) {
				return false;
			} else if(instanceExpression == null && load.instanceExpression != null) {
				return false;
			} else if(instanceExpression != null && load.instanceExpression != null) {
				if(!instanceExpression.equivalent(load.instanceExpression)) {
					return false;
				}
			}
			return name.equals(load.name) && desc.equals(load.desc) && owner.equals(load.owner);
		}
		return false;
	}
}