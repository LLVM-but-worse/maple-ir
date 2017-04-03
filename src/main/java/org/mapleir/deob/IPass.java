package org.mapleir.deob;

import org.mapleir.context.IContext;

import java.util.List;

public interface IPass {
	
	default String getId() {
		return getClass().getSimpleName();
	}
	
	default boolean isQuantisedPass() {
		return true;
	}

	int accept(IContext cxt, IPass prev, List<IPass> completed);
}