package org.mapleir.stdlib.collections.nullpermeable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Bibl (don't ban me pls)
 * @created 25 May 2015 (actually before this)
 */
public class SetCreator<T> implements ValueCreator<Set<T>> {
	@Override 
	public Set<T> create() {
		return new HashSet<>();
	}
	
	public static <T> ValueCreator<Set<T>> getInstance() {
		return HashSet::new;
	}
}