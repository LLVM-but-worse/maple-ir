package org.rsdeob.stdlib.ir.locals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.stat.Statement;

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
	
	public Local get(int index, int subscript) {
		return get(index, subscript, false);
	}

	public List<Local> getOrderedList() {
		List<Local> list = new ArrayList<>();
		list.addAll(cache.values());
		Collections.sort(list);
		return list;
	}
	
	public VersionedLocal get(int index, int subscript, boolean isStack) {
		String key = key(index, subscript, isStack);
		if(cache.containsKey(key)) {
			return (VersionedLocal) cache.get(key);
		} else {
			VersionedLocal v = new VersionedLocal(base, index, subscript, isStack);
			cache.put(key, v);
			return v;
		}
	}
	
	public Local get(int index, boolean isStack) {
		String key = key(index, isStack);
		if(cache.containsKey(key)) {
			return cache.get(key);
		} else {
			BasicLocal v = new BasicLocal(base, index, isStack);
			cache.put(key, v);
			return v;
		}
	}
	
	/* public Local newLocal(boolean isStack) {
		int index = cache.size();
		while(true) {
			String key = key(index, isStack);
			if(!cache.containsKey(key)) {
				return get(index, isStack);
			}
		}
	} */
	
	public void realloc(CodeBody code) {
		NullPermeableHashMap<Local, Set<Type>> types = new NullPermeableHashMap<>(new SetCreator<>());
		for(Statement stmt : code) {
			for(Statement s : Statement.enumerate(stmt)) {
				if(s instanceof VarExpression) {
					VarExpression var = (VarExpression) s;
					Local local = var.getLocal();
					types.getNonNull(local).add(var.getType());
				}
			}
		}

		Map<Local, Type> saveTypes = new HashMap<>();
		
		for(Entry<Local, Set<Type>> e : types.entrySet()) {
			Set<Type> set = e.getValue();
			Set<Type> refined = new HashSet<>();
			if(set.size() > 1) {
				for(Type t : set) {
					refined.add(TypeUtils.asSimpleType(t));
				}
				if(refined.size() != 1) {
					throw new RuntimeException("illegal typesets: " + types);
				}
				saveTypes.put(e.getKey(), refined.iterator().next());
			} else {
				saveTypes.put(e.getKey(), set.iterator().next());
			}
		}
		
		System.out.println(saveTypes);
		
		// lvars then svars, ordered of course,
		List<Local> worklist = new ArrayList<>(saveTypes.keySet());
		Collections.sort(worklist);

		int index = 0;
		for(Local l : worklist) {
			Type type = saveTypes.get(l);
			System.out.println(l + "  set to " + index);
			l.setIndex(index);
			index += type.getSize();
		}
	}
	
	private List<Local> createWorkList(Set<Local> keySet) {
		List<Local> real = new ArrayList<>();
		List<Local> stack = new ArrayList<>();
		for(Local l : keySet) {
			if(l.isStack()) {
				stack.add(l);
			} else {
				real.add(l);
			}
		}
		Collections.sort(real);
		Collections.sort(stack);
		
		List<Local> res = new ArrayList<>();
		res.addAll(real);
		res.addAll(stack);
		return res;
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
	
	public AtomicInteger getBase0() {
		return base;
	}
	
	public void setBase(int b) {
		base.set(b);
	}
	
	public static String key(int index, boolean stack) {
		return (stack ? "s" : "l") + "var" + index;
	}
	
	public static String key(int index, int subscript, boolean stack) {
		return (stack ? "s" : "l") + "var" + index + "_" + subscript;
	}
}