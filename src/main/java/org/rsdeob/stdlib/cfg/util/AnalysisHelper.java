package org.rsdeob.stdlib.cfg.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AnalysisHelper {
	
	public static void print(Map<String, ?> map) {
		List<String> keys = new ArrayList<>();
		keys.addAll(map.keySet());
		Collections.sort(keys);
		
		for(String key : keys) {
			System.out.println("      " + key + ": " + map.get(key));
		}
	}
}