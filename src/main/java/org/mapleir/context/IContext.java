package org.mapleir.context;

import org.mapleir.context.app.ApplicationClassSource;
import org.mapleir.deob.intraproc.ExceptionAnalysis;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.stdlib.util.InvocationResolver;

public interface IContext {

	ApplicationClassSource getApplication();
	
	InvocationResolver getInvocationResolver();
	
	ExceptionAnalysis getExceptionAnalysis(ControlFlowGraph cfg);
	
	IRCache getIRCache();
}