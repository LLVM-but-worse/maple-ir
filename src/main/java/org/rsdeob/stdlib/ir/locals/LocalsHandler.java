package org.rsdeob.stdlib.ir.locals;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.collections.SetMultimap;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
	
	public BasicLocal unversion(VersionedLocal versionedLocal) {
		return get(versionedLocal.getIndex(), versionedLocal.isStack());
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
		SetMultimap<Local, Type> types = new SetMultimap<>();
		for(Statement stmt : code) {
			for (VarExpression var : stmt.getUsedVars()) {
				Local local = var.getLocal();
				types.put(local, var.getType());
				System.out.println("(1)type of " + stmt + " = " + var.getType() + " (local=" + local + ")");
			}
			for(Statement s : stmt.enumerate(child -> child instanceof CopyVarStatement && ((CopyVarStatement) child).isSynthetic())) {
				VarExpression var = ((CopyVarStatement) s).getVariable();
				Local local = var.getLocal();
				types.put(local, var.getType());
				System.out.println("(2)type of " + s + " = " + var.getType() + " (local=" + local + ")");
			}
		}

		Map<Local, Type> saveTypes = new HashMap<>();
		
		for(Entry<Local, Set<Type>> e : types.asMap().entrySet()) {
			Set<Type> set = e.getValue();
			Set<Type> refined = new HashSet<>();
			if(set.size() > 1) {
				for(Type t : set) {
					refined.add(TypeUtils.asSimpleType(t));
				}
				if(refined.size() != 1) {
					System.err.println(e);
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