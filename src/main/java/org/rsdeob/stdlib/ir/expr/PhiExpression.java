package org.rsdeob.stdlib.ir.expr;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public class PhiExpression extends Expression {

	private final List<Expression> locals;
	
	public PhiExpression(List<VersionedLocal> locals, Type type) {
		this.locals = new ArrayList<>();
		for(VersionedLocal l : locals)  {
			this.locals.add(new VarExpression(l, type));
		}
	}
	
	public PhiExpression(List<Expression> locals) {
		this.locals = locals;
	}
	
	public int getParameterCount() {
		return locals.size();
	}
	
	public List<Expression> getLocals() {
		return locals;
	}
	
	public Expression getLocal(int j) {
		return locals.get(j);
	}
	
	public void setLocal(int j, Expression e) {
		locals.set(j, e);
	}
	
	public void setLocal(int j, VersionedLocal l) {
		locals.set(j, new VarExpression(l, locals.get(j).getType()));
	}
	
	@Override
	public void onChildUpdated(int ptr) {
		
	}

	@Override
	public Expression copy() {
		List<Expression> locals = new ArrayList<>();
		for(Expression e : this.locals) {
			locals.add(e.copy());
		}
		return new PhiExpression(locals);
	}

	@Override
	public Type getType() {
		return Type.VOID_TYPE;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("\u0278" + locals);
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		throw new UnsupportedOperationException("Phi is not executable.");
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
		return false;
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof PhiExpression) {
			return ((PhiExpression) s).locals.equals(locals);
		}
		return false;
	}
}