package org.mapleir.stdlib.deob;

import java.util.List;

import org.mapleir.stdlib.IContext;

public interface IPass {
	
	default String getId() {
		return getClass().getSimpleName();
	}
	
	default boolean isIncremental() {
		return true;
	}

	int accept(IContext cxt, IPass prev, List<IPass> completed);
}