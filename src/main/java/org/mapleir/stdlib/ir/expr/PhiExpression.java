package org.mapleir.stdlib.ir.expr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class PhiExpression extends Expression {

	private final Map<HeaderStatement, Expression> locals;
	private Type type;
	
	public PhiExpression(Map<HeaderStatement, Expression> locals) {
		this.locals = locals;
	}
	
	public int getParameterCount() {
		return locals.size();
	}
	
	public Set<HeaderStatement> headers() {
		return new HashSet<>(locals.keySet());
	}
	
	public Map<HeaderStatement, Expression> getLocals() {
		return locals;
	}
	
	public Expression getLocal(HeaderStatement header) {
		return locals.get(header);
	}
	
	public void setLocal(HeaderStatement header, Expression e) {
		if(locals.containsKey(header)) {
			locals.put(header, e);
		} else {
			throw new IllegalStateException("phi has a fixed size of " + locals.size() + ": " + header + ", " + e);
		}
	}
	
	@Override
	public void onChildUpdated(int ptr) {
		
	}

	@Override
	public Expression copy() {
		Map<HeaderStatement, Expression> map = new HashMap<>();
		for(Entry<HeaderStatement, Expression> e : locals.entrySet()) {
			map.put(e.getKey(), e.getValue());
		}
		return new PhiExpression(map);
	}

	@Override
	public Type getType() {
		return type;
	}
	
	public void setType(Type type) {
		this.type = type;
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
			PhiExpression phi = (PhiExpression) s;
			
			Set<HeaderStatement> sources = new HashSet<>();
			sources.addAll(locals.keySet());
			sources.addAll(phi.locals.keySet());
			
			if(sources.size() != locals.size()) {
				return false;
			}
			
			for(HeaderStatement header : sources) {
				Expression e1 = locals.get(header);
				Expression e2 = phi.locals.get(header);
				if(e1 == null || e2 == null) {
					return false;
				}
				if(!e1.equivalent(e2)) {
					return false;
				}
			}
			
			return true;
		}
		return false;
	}
	
	public static boolean phi(Statement stmt) {
		return stmt instanceof CopyVarStatement && ((CopyVarStatement) stmt).getExpression() instanceof PhiExpression;
	}
}