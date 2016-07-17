package org.rsdeob.stdlib.ir.expr;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class PhiExpression extends Expression {

	private final Map<HeaderStatement, Expression> locals;
	
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
	
	public void setLocal(HeaderStatement header, VersionedLocal l) {
		if(locals.containsKey(header)) {
			Expression oldE = locals.get(header);
			locals.put(header, new VarExpression(l, oldE.getType()));
		} else {
			System.err.println(locals);
			throw new IllegalStateException("phi has a fixed size of " + locals.size() + ": " + header + ", " + l);
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
}