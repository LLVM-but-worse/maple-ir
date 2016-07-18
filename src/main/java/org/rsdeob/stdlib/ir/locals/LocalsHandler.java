package org.rsdeob.stdlib.ir.locals;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

import java.util.*;
import java.util.Map.Entry;
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
	
	public BasicLocal get(int index, boolean isStack) {
		String key = key(index, isStack);
		if(cache.containsKey(key)) {
			return (BasicLocal) cache.get(key);
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
		NullPermeableHashMap<Local, Set<Type>> types = new NullPermeableHashMap<>(HashSet::new);
		for(Statement stmt : code) {
			for(Statement s : Statement.enumerate(stmt)) {
				if(s instanceof VarExpression) {
					VarExpression var = (VarExpression) s;
					Local local = var.getLocal();
					types.getNonNull(local).add(var.getType());
					System.out.println("(1)type of " + stmt + " = " + var.getType() + " (local=" + local + ")");
				} else if(s instanceof CopyVarStatement) {
					CopyVarStatement cp = (CopyVarStatement) s;
					if(cp.isSynthetic()) {
						VarExpression var = cp.getVariable();
						Local local = var.getLocal();
						types.getNonNull(local).add(var.getType());
						System.out.println("(2)type of " + cp + " = " + var.getType() + " (local=" + local + ")");
					}
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
					for(Entry<Local, Set<Type>> e1 : types.entrySet()) {
						System.err.println(e1.getKey() + "  ==  " + e1.getValue());
					}
					throw new RuntimeException("illegal typesets for " + e.getKey());
				}
				saveTypes.put(e.getKey(), refined.iterator().next());
			} else {
				saveTypes.put(e.getKey(), set.iterator().next());
			}
		}
		
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
	
	public int getBase() {
		return base.get();
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