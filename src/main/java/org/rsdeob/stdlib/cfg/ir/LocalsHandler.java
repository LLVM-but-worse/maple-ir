package org.rsdeob.stdlib.cfg.ir;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalsHandler {

	private final AtomicInteger base;
	private final Map<String, Local> cache;
	
	public LocalsHandler(AtomicInteger base) {
		this.base = base;
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
	
	public static String key(int index, boolean stack) {
		return (stack ? "s" : "l") + "var" + index;
	}
}