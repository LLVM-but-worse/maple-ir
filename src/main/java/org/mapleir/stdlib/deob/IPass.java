package org.mapleir.stdlib.deob;

import java.util.List;

import org.mapleir.deobimpl2.cxt.IContext;

public interface IPass {
	
	default String getId() {
		return getClass().getSimpleName();
	}
	
	default boolean isQuantisedPass() {
		return true;
	}

	int accept(IContext cxt, IPass prev, List<IPass> completed);
}