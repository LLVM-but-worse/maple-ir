package org.mapleir.stdlib.collections.nullpermeable;

import java.util.ArrayList;
import java.util.List;

public class ListCreator<T> implements ValueCreator<List<T>> {

	@Override 
	public List<T> create() {
		return new ArrayList<>();
	}
}