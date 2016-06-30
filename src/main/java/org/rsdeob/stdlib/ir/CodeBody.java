package org.rsdeob.stdlib.ir;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.ir.api.ICodeListener;
import org.rsdeob.stdlib.ir.header.StatementHeaderStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

public class CodeBody implements Iterable<Statement> {

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

	// TODO: exceptions

	public Statement set(int index, Statement s) {
		Statement prev = stmts.set(index, s);
		if(s == null)
			throw new IllegalArgumentException("Statement cannot be null");
		if (s != prev && prev != null) {
			for(ICodeListener<Statement> l : listeners)
				l.replaced(prev, s);
		}
		
//		if(prev != null) {
//			for(ICodeListener<Statement> l : listeners)
//				l.removed(prev);
//		}
		
		return prev;
	}

	public Statement remove(int index) {
		Statement prev = stmts.remove(index);
		for(ICodeListener<Statement> l : listeners)
			l.remove(prev);
		return prev;
	}

	public boolean remove(Statement s) {
		boolean ret = stmts.remove(s);
		for(ICodeListener<Statement> l : listeners)
			l.remove(s);
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
		System.out.println("insert " + p +"  " + s + "  "+ n);
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
	
	@Override
	public Iterator<Statement> iterator() {
		return stmts.iterator();
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
}