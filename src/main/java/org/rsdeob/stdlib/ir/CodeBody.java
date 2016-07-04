package org.rsdeob.stdlib.ir;

import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.ir.api.ICodeListener;
import org.rsdeob.stdlib.ir.header.StatementHeaderStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

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

	@Override
	public boolean remove(Object o) {
		if (!(o instanceof Statement))
			return false;
		Statement s = (Statement) o;

		boolean ret = stmts.remove(s);
		for(ICodeListener<Statement> l : listeners)
			l.removed(s);
		return ret;
	}

	//FIXME
	@Override
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
		return stmts.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return stmts.toArray(a);
	}

	@Override
	public boolean isEmpty() {
		return stmts.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return stmts.contains(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return stmts.contains(c);
	}

	@Override
	public boolean addAll(Collection<? extends Statement> c) {
		boolean result = false;
		for (Statement s : c)
			result = result || add(s);
		return result;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean result = false;
		for (Object o : c)
			result = result || remove(o);
		return result;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean result = false;
		for (Object o : c)
			if (!contains(o))
				result = result || remove(o);
		return result;
	}

	@Override
	public void clear() {
		stmts.forEach(this::remove);
	}
}