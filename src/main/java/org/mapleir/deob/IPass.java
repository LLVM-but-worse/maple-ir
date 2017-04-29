package org.mapleir.deob;

import org.mapleir.context.AnalysisContext;

import java.util.List;

public interface IPass {
	
	default String getId() {
		return getClass().getSimpleName();
	}
	
	default boolean isQuantisedPass() {
		return true;
	}

	int accept(AnalysisContext cxt, IPass prev, List<IPass> completed);
}