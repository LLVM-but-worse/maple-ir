package org.rsdeob.stdlib.ir;

import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.stat.Statement;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalsHandler {

	private final AtomicInteger base;
	private final Map<String, Local> cache;
	
	public LocalsHandler(int base) {
		this.base = new AtomicInteger(base);
		cache = new HashMap<>();
	}
	
	public Local get(int index) {
		return get(index, false);
	}
	
	public List<Local> getOrderedList() {
		List<Local> list = new ArrayList<>();
		list.addAll(cache.values());
		Collections.sort(list);
		return list;
	}
	
	public Local get(int index, boolean isStack) {
		String key = key(index, isStack);
		if(cache.containsKey(key)) {
			return cache.get(key);
		} else {
			Local v = new Local(base, index, isStack);
			cache.put(key, v);
			return v;
		}
	}
	
	public Local newLocal(boolean isStack) {
		int index = cache.size();
		while(true) {
			String key = key(index, isStack);
			if(!cache.containsKey(key)) {
				return get(index, isStack);
			}
		}
	}
	
	private void pack(List<Local> list) {
		Collections.sort(list);
		int index = 0;
		for(Local local : list) {
			local.setIndex(index++);
		}
	}
	
	public void pack(CodeBody stmtList) {
		Set<Local> locals = new HashSet<>();
		for (Statement s : stmtList) {
			new StatementVisitor(s) {
				@Override
				public Statement visit(Statement stmt) {
					if (stmt instanceof VarExpression) {
						locals.add(((VarExpression) stmt).getLocal());
					}
					return stmt;
				}
			}.visit();
		}
		pack(locals);
	}
	
	public void pack(Set<Local> used) {
		// FIXME: longs and doubles
		List<Local> stacks = new ArrayList<>();
		List<Local> locals = new ArrayList<>();
		for(Local l : cache.values()) {
			if(!used.contains(l))
				continue;
			if(l.isStack()) {
				stacks.add(l);
			} else {
				locals.add(l);
			}
		}
		pack(stacks);
		pack(locals);
	}
	
	public int getBase() {
		return base.get();
	}
	
	public void setBase(int b) {
		base.set(b);
	}
	
	public static String key(int index, boolean stack) {
		return (stack ? "s" : "l") + "var" + index;
	}
}