package org.mapleir.stdlib.ir.locals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.objectweb.asm.Type;

public class LocalsHandler {

	private final AtomicInteger base;
	private final Map<String, Local> cache;
	
	public LocalsHandler(int base) {
		this.base = new AtomicInteger(base);
		cache = new HashMap<>();
	}

	public List<Local> getOrderedList() {
		List<Local> list = new ArrayList<>();
		list.addAll(cache.values());
		Collections.sort(list);
		return list;
	}
	
	public VersionedLocal get(int index, int subscript) {
		return get(index, subscript, false);
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
	
	public BasicLocal get(int index) {
		return get(index, false);
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
		NullPermeableHashMap<Local, Set<Type>> types = new NullPermeableHashMap<>(new SetCreator<>());
		for(Statement stmt : code) {
			for(Statement s : Statement.enumerate(stmt)) {
				if(s instanceof VarExpression) {
					VarExpression var = (VarExpression) s;
					Local local = var.getLocal();
					types.getNonNull(local).add(var.getType());
				} else if(s instanceof CopyVarStatement) {
					CopyVarStatement cp = (CopyVarStatement) s;
					if(cp.isSynthetic()) {
						VarExpression var = cp.getVariable();
						Local local = var.getLocal();
						types.getNonNull(local).add(var.getType());
					}
				}
			}
		}

		Map<Local, Type> stypes = new HashMap<>();
		
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
				stypes.put(e.getKey(), refined.iterator().next());
			} else {
				stypes.put(e.getKey(), set.iterator().next());
			}
		}
		
		System.out.println(stypes);
		
		// lvars then svars, ordered of course,
		List<Local> wl = new ArrayList<>(stypes.keySet());
		Collections.sort(wl);

		Map<Local, Local> remap = new HashMap<>();
		int idx = 0;
		for(Local l : wl) {
			Type type = stypes.get(l);
			Local newL = get(idx, false);
			if(l != newL) {
				remap.put(l, newL);
			}
			idx += type.getSize();
		}
		remap(code, remap);
	}
	
	public static void remap(CodeBody body, Map<? extends Local, ? extends Local> remap) {
		for(Statement stmt : body) {
			for(Statement s : Statement.enumerate(stmt)) {
				if(s instanceof VarExpression) {
					VarExpression v = (VarExpression) s;
					Local l = v.getLocal();
					if(remap.containsKey(l)) {
						v.setLocal(remap.get(l));
					}
				} else if(s instanceof CopyVarStatement) {
					VarExpression v = ((CopyVarStatement) s).getVariable();
					Local l = v.getLocal();
					if(remap.containsKey(l)) {
						Local l2 = remap.get(l);
						v.setLocal(l2);
					}
				}
			}
		}
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