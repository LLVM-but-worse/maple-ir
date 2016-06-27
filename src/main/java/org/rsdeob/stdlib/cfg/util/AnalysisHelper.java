package org.rsdeob.stdlib.cfg.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rsdeob.stdlib.ir.stat.Statement;

public class AnalysisHelper {
	
	public static void print(Map<String, ?> map) {
		List<String> keys = new ArrayList<>();
		keys.addAll(map.keySet());
		Collections.sort(keys);
		
		for(String key : keys) {
			System.out.println("      " + key + ": " + map.get(key));
		}
	}
	
	private static void traverse(Set<Statement> set, Set<Statement> error, Statement s) {
		Statement c = null;
		for(int i=0; (c = s.read(i)) != null; i++) {
			if(!set.contains(c)) {
				set.add(c);
				traverse(set, error, c);
			} else {
				error.add(c);
			}
		}
	}
	
	public static Set<Statement> findErrorNodes(Statement s) {
		Set<Statement> set = new HashSet<>();
		set.add(s);
		Set<Statement> error = new HashSet<>();
		traverse(set, error, s);
		return error;
	}
}