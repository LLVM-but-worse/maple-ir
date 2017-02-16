package org.mapleir.ir.locals;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.collections.bitset.BitSetIndexer;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.collections.bitset.IncrementalBitSetIndexer;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.Type;

public class LocalsPool implements ValueCreator<GenericBitSet<Local>> {

	private final AtomicInteger base;
	private final Map<String, Local> cache;
	private final Map<BasicLocal, VersionedLocal> latest;
	private final BitSetIndexer<Local> indexer;
	
	public final Map<VersionedLocal, AbstractCopyStmt> defs;
	public final Map<VersionedLocal, Set<VarExpr>> uses;
	
	public LocalsPool(int base) {
		this.base = new AtomicInteger(base);
		cache = new HashMap<>();
		latest = new HashMap<>();
		indexer = new IncrementalBitSetIndexer<>();
		
		defs = new HashMap<>();
		uses = new HashMap<>();
	}
	
	public Set<Local> getAll(Predicate<Local> p)  {
		Set<Local> set = new HashSet<>();
		for(Local l : cache.values()) {
			if(p.test(l)) {
				set.add(l);
			}
		}
		return set;
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
	
	public int realloc(ControlFlowGraph cfg) {
		NullPermeableHashMap<Local, Set<Type>> types = new NullPermeableHashMap<>(new SetCreator<>());
		int min = 0;
		Set<Local> safe = new HashSet<>();
		for(BasicBlock b : cfg.vertices()) {
			for(Stmt stmt : b) {
				if(stmt.getOpcode() == Opcode.LOCAL_STORE) {
					CopyVarStmt cp = (CopyVarStmt) stmt;
					VarExpr var = cp.getVariable();
					Local local = var.getLocal();
					if(!cp.isSynthetic()) {
						types.getNonNull(local).add(var.getType());
					} else {
						safe.add(local);
					}
					types.getNonNull(local).add(var.getType());
				}
				
				for(Expr s : stmt.enumerateOnlyChildren()) {
					if(s.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpr var = (VarExpr) s;
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
					boolean valid = false;
					
					if(refined.size() == 2) {
						// TODO: proper check
						Iterator<Type> it = refined.iterator();
						if(it.next().getSize() == it.next().getSize()) {
							Type t = refined.iterator().next();
							refined.clear();
							refined.add(t);
							valid = true;
						}
					}
					
					if(!valid) {
						for(Entry<Local, Set<Type>> e1 : types.entrySet()) {
							System.err.println(e1.getKey() + "  ==  " + e1.getValue());
						}
						// String.format("illegal typesets for %s, set=%s, refined=%s", args)
						throw new RuntimeException("illegal typesets for " + e.getKey());
					}
				}
				Local l = e.getKey();
				stypes.put(l, refined.iterator().next());
				
//				if(!safe.contains(l)) {
//					stypes.put(l, refined.iterator().next());
//				}
			} else {
				Local l = e.getKey();
				stypes.put(l, set.iterator().next());
//				if(!safe.contains(l)) {
//				}
			}
		}
		
//		for(Entry<Local, Type> e : stypes.entrySet()) {
//			System.out.println(e.getKey() + "  ==  " + e.getValue());
//		}
		
		// lvars then svars, ordered of course,
		List<Local> wl = new ArrayList<>(stypes.keySet());
//		System.out.println("safe: " + safe);
		Collections.sort(wl, new Comparator<Local>() {
			@Override
			public int compare(Local o1, Local o2) {
				boolean s1 = safe.contains(o1);
				boolean s2 = safe.contains(o2);
				
				if(s1 && !s2) {
					return -1;
				} else if(!s1 && s2) {
					return 1;
				} else {
					return o1.compareTo(o2);
				}
			}
		});
//		System.out.println("wl: " + wl);
		
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
		
		return idx;
	}
	
	public static void remap(ControlFlowGraph cfg, Map<? extends Local, ? extends Local> remap) {
		for(BasicBlock b : cfg.vertices()) {
			for(Stmt stmt : b) {
				if(stmt.getOpcode() == Opcode.LOCAL_STORE) {
					VarExpr v = ((CopyVarStmt) stmt).getVariable();
					Local l = v.getLocal();
					if(remap.containsKey(l)) {
						Local l2 = remap.get(l);
						v.setLocal(l2);
					}
				}
				
				for(Expr s : stmt.enumerateOnlyChildren()) {
					if(s.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpr v = (VarExpr) s;
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