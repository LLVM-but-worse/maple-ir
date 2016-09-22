package org.mapleir.ir.locals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.collections.bitset.BitSetIndexer;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.collections.bitset.IncrementalBitSetIndexer;
import org.objectweb.asm.Type;

public class LocalsHandler implements ValueCreator<GenericBitSet<Local>> {

	private final AtomicInteger base;
	private final Map<String, Local> cache;
	private final Map<BasicLocal, VersionedLocal> latest;
	private final BitSetIndexer<Local> indexer;
	
	public LocalsHandler(int base) {
		this.base = new AtomicInteger(base);
		cache = new HashMap<>();
		latest = new HashMap<>();
		indexer = new IncrementalBitSetIndexer<>();
	}
	
	// factory
	public GenericBitSet<Local> createBitSet() {
		return new GenericBitSet<>(indexer);
	}

	@Override
	public GenericBitSet<Local> create() {
		return createBitSet();
	}
	
	public BasicLocal asSimpleLocal(Local l) {
		return get(l.getIndex(), l.isStack());
	}
	
	public VersionedLocal makeLatestVersion(Local l) {
		VersionedLocal vl = getLatestVersion(l);
		return get(vl.getIndex(), vl.getSubscript() + 1, vl.isStack());
	}
	
	public VersionedLocal getLatestVersion(Local l) {
		l = asSimpleLocal(l);
		if(!latest.containsKey(l)) {
			return get(l.getIndex(), 0, l.isStack());
		} else {
			return latest.get(l);
		}
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
			
			BasicLocal bl = get(index, isStack);
			if(latest.containsKey(bl)) {
				VersionedLocal old = latest.get(bl);
				if(subscript > old.getSubscript()) {
					latest.put(bl, v);
				} else if(subscript == old.getSubscript()) {
					throw new IllegalStateException("Created " + v + " with " + old + ", " + bl);
				}
			} else {
				latest.put(bl, v);
			}
			
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

	public BasicLocal newLocal(int i, boolean isStack) {
		while(true) {
			String key = key(i, isStack);
			if(!cache.containsKey(key)) {
				return get(i, isStack);
			}
			i++;
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
	
	public void realloc(ControlFlowGraph cfg) {
		NullPermeableHashMap<Local, Set<Type>> types = new NullPermeableHashMap<>(new SetCreator<>());
		int min = 0;
		Set<Local> safe = new HashSet<>();
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b) {

				if(stmt.getOpcode() == Opcode.LOCAL_STORE) {
					CopyVarStatement cp = (CopyVarStatement) stmt;
					VarExpression var = cp.getVariable();
					Local local = var.getLocal();
					if(!cp.isSynthetic()) {
						types.getNonNull(local).add(var.getType());
					} else {
//						min = Math.max(min, local.getIndex());
//						safe.add(local);
					}
					types.getNonNull(local).add(var.getType());
				}
				
				for(Statement s : stmt.enumerate()) {
					if(s.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpression var = (VarExpression) s;
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
				Local l = e.getKey();
				if(!safe.contains(l)) {
					stypes.put(l, refined.iterator().next());
				}
			} else {
				Local l = e.getKey();
				if(!safe.contains(l)) {
					stypes.put(l, set.iterator().next());
				}
			}
		}
		
//		for(Entry<Local, Type> e : stypes.entrySet()) {
//			System.out.println(e.getKey() + "  ==  " + e.getValue());
//		}
		
		// lvars then svars, ordered of course,
		List<Local> wl = new ArrayList<>(stypes.keySet());
		Collections.sort(wl);

		Map<Local, Local> remap = new HashMap<>();
		int idx = min;
		for(Local l : wl) {
			Type type = stypes.get(l);
			Local newL = get(idx, false);
			if(l != newL) {
				remap.put(l, newL);
			}
			idx += type.getSize();
		}
		remap(cfg, remap);
	}
	
	public static void remap(ControlFlowGraph cfg, Map<? extends Local, ? extends Local> remap) {
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b) {
				if(stmt.getOpcode() == Opcode.LOCAL_STORE) {
					VarExpression v = ((CopyVarStatement) stmt).getVariable();
					Local l = v.getLocal();
					if(remap.containsKey(l)) {
						Local l2 = remap.get(l);
						v.setLocal(l2);
					}
				}
				
				for(Statement s : stmt.enumerate()) {
					if(s.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpression v = (VarExpression) s;
						Local l = v.getLocal();
						if(remap.containsKey(l)) {
							v.setLocal(remap.get(l));
						}
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