package org.rsdeob.stdlib.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.MethodVisitor;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.ir.api.ICodeListener;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.header.StatementHeaderStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public class RootStatement extends Statement {

	private final LocalsHandler locals;
	private final List<ICodeListener<Statement>> listeners;
	
	public RootStatement(int maxL) {
		locals = new LocalsHandler(maxL + 1);
		listeners = new CopyOnWriteArrayList<>();
	}
	
	public List<ICodeListener<Statement>> getListeners() {
		return new ArrayList<>(listeners);
	}

	public void updateBase() {
		final AtomicInteger max = new AtomicInteger(0);
		new StatementVisitor(this) {
			@Override
			public Statement visit(Statement s) {
				if (s instanceof VarExpression) {
					Local local = ((VarExpression) s).getLocal();
					if (!local.isStack() && local.getIndex() > max.get())
						max.set(local.getIndex());
				}
				return s;
			}
		}.visit();
		locals.setBase(max.get() + 1);
		System.out.println("New maxlocals: " + locals.getBase());
	}
	
	public LocalsHandler getLocals() {
		return locals;
	}
	
	// TODO: exceptions
	
	@Override
	protected Statement writeAt(int index, Statement s) {
		Statement prev = super.writeAt(index, s);
		if(s == null) {
			if(prev != null) {
				for(ICodeListener<Statement> l : listeners) {
					l.removed(prev);
				}
			}
		} else {
			if(prev != null) {
				for(ICodeListener<Statement> l : listeners) {
					l.replaced(prev, s);
				}
			} else {
				for(ICodeListener<Statement> l : listeners) {
					l.added(s);
				}
			}
		}
		return prev;
	}
	
	@Override
	public void delete(int _ptr) {
		super.delete(_ptr);
	}
	
	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		for (int addr = 0; read(addr) != null; addr++) {
			Statement stmt = read(addr);
			if(!(stmt instanceof StatementHeaderStatement)) {
				printer.print(stmt.getId() + ". ");
				stmt.toString(printer);
			}
			
			Statement next = read(addr + 1);
			if(next != null) {
				if(!(stmt instanceof StatementHeaderStatement)) {
					printer.print('\n', !next.changesIndentation());
				}
			}
		}
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		for (int addr = 0; read(addr) != null; addr++) {
			read(addr).toCode(visitor, analytics);
		}
	}

	@Override
	public boolean canChangeFlow() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean canChangeLogic() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Statement copy() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean equivalent(Statement s) {
		throw new UnsupportedOperationException();
	}
}