package org.mapleir.deob;

import java.util.List;

import org.mapleir.context.AnalysisContext;

public interface IPass {
	
	default boolean is(Class<? extends IPass> clz) {
		return getClass() == clz;
	}
	
	default boolean is(String id) {
		return getId().equals(id);
	}
	
	default String getId() {
		return getClass().getSimpleName();
	}
	
	default boolean isQuantisedPass() {
		return true;
	}

	int accept(AnalysisContext cxt, IPass prev, List<IPass> completed);
}