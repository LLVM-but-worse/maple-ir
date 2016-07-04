package org.rsdeob.stdlib.ir;

import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.ir.api.ICodeListener;
import org.rsdeob.stdlib.ir.header.StatementHeaderStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CodeBody implements Collection<Statement> {

	private final LocalsHandler locals;
	private final List<Statement> stmts;
	private final List<ICodeListener<Statement>> listeners;
	
	public CodeBody(int maxL) {
		stmts = new ArrayList<>();
		locals = new LocalsHandler(maxL + 1);
		listeners = new CopyOnWriteArrayList<>();
	}

	public void registerListener(ICodeListener<Statement> listener) {
		listeners.add(listener);
	}
	
	public void unregisterListener(ICodeListener<Statement> listener) {
		listeners.remove(listener);
	}

	public void clearListeners() {
		listeners.clear();
	}

	public List<ICodeListener<Statement>> getListeners() {
		return new ArrayList<>(listeners);
	}
	
	public LocalsHandler getLocals() {
		return locals;
	}
	
	public void replace(Statement old, Statement n) {
		stmts.set(stmts.indexOf(old), n);
		for(ICodeListener<Statement> l : listeners)
			l.replaced(old, n);
	}

	public Statement remove(int index) {
		Statement prev = stmts.remove(index);
		for(ICodeListener<Statement> l : listeners)
			l.removed(prev);
		return prev;
	}

	public boolean remove(Statement s) {
		boolean ret = stmts.remove(s);
		for(ICodeListener<Statement> l : listeners)
			l.removed(s);
		return ret;
	}

	//FIXME
	public boolean add(Statement s) {
		boolean ret = stmts.add(s);
		for(ICodeListener<Statement> l : listeners)
			l.update(s);
		return ret;
	}

	public void forceUpdate(Statement stmt) {
		for(ICodeListener<Statement> l : listeners)
			l.update(stmt);
	}
	
	public void insert(int index, Statement stmt) {
		Statement p = stmts.get(index);
		Statement s = stmts.get(index + 1);
		stmts.add(index, stmt);
		for(ICodeListener<Statement> l : listeners) {
			l.insert(p, s, stmt);
		}
	}
	
	public void insert(Statement p, Statement s, Statement n) {
		stmts.add(stmts.indexOf(p) + 1, n);
		for(ICodeListener<Statement> l : listeners) {
			l.insert(p, s, n);
		}
	}

	public void commit() {
		for(ICodeListener<Statement> l : listeners)
			l.commit();
	}

	public int size() {
		return stmts.size();
	}

	public int indexOf(Statement stmt) {
		return stmts.indexOf(stmt);
	}

	public Statement getAt(int index) {
		return stmts.get(index);
	}

	public List<Statement> stmts() {
		return new ArrayList<>(stmts);
	}

	public void toString(TabbedStringWriter printer) {
		for (int addr = 0; addr < stmts.size(); addr++) {
			Statement stmt = stmts.get(addr);
			if(!(stmt instanceof StatementHeaderStatement)) {
				printer.print(stmt.getId() + ". ");
				stmt.toString(printer);
			}

			if (addr < stmts.size() - 1) {
				Statement next = stmts.get(addr + 1);
				if (next != null) {
					if (!(stmt instanceof StatementHeaderStatement)) {
						printer.print('\n', !next.changesIndentation());
					}
				}
			}
		}
	}

	@Override
	public String toString() {
		TabbedStringWriter printer = new TabbedStringWriter();
		toString(printer);
		return printer.toString();
	}

	// todo: implement
	@Override
	public Iterator<Statement> iterator() {
		return stmts.iterator();
	}

	@Override
	public Object[] toArray() {
		throw new NotImplementedException();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		throw new NotImplementedException();
	}

	@Override
	public boolean isEmpty() {
		throw new NotImplementedException();
	}

	@Override
	public boolean contains(Object o) {
		throw new NotImplementedException();
	}

	@Override
	public boolean remove(Object o) {
		throw new NotImplementedException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		throw new NotImplementedException();
	}

	@Override
	public boolean addAll(Collection<? extends Statement> c) {
		throw new NotImplementedException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new NotImplementedException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new NotImplementedException();
	}

	@Override
	public void clear() {
		throw new NotImplementedException();
	}
}