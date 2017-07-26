package org.mapleir.deob.passes;

import java.util.List;

import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.IPass;

public class StaticPathActualiserPass implements IPass {

	@Override
	public int accept(AnalysisContext cxt, IPass prev, List<IPass> completed) {
		return 0;
	}
}