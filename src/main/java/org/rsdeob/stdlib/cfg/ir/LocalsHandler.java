package org.rsdeob.stdlib.cfg.ir;

import java.util.HashMap;
import java.util.Map;

public class LocalsHandler {

	private final Map<String, Local> cache;
	
	public LocalsHandler() {
		cache = new HashMap<String, Local>();
	}
	
	public Local get(int index) {
		return get(index, false);
	}
	
	public Local get(int index, boolean isStack) {
		String key = (isStack ? "s" : "l") + "var" + index;
		if(cache.containsKey(key)) {
			return cache.get(key);
		} else {
			Local v = new Local(index, isStack);
			cache.put(key, v);
			return v;
		}
	}
	
	public Local newLocal(boolean isStack) {
		int index = cache.size();
		while(true) {
			String key = (isStack ? "s" : "l") + "var" + index;
			if(!cache.containsKey(key)) {
				return get(index, isStack);
			}
		}
	}
}