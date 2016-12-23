package org.mapleir.stdlib.deob;

import java.util.List;

import org.mapleir.stdlib.IContext;

public interface ICompilerPass {
	
	default String getId() {
		return getClass().getSimpleName();
	}
	
	void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed);
}