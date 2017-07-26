package org.mapleir.context;

import org.mapleir.app.client.ApplicationContext;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.InvocationResolver;
import org.mapleir.deob.intraproc.ExceptionAnalysis;
import org.mapleir.ir.cfg.ControlFlowGraph;

/**
 * Top-level interface responsible for holding global exception information
 */
public interface AnalysisContext {

	ApplicationClassSource getApplication();
	
	InvocationResolver getInvocationResolver();
	
	ExceptionAnalysis getExceptionAnalysis(ControlFlowGraph cfg);
	
	IRCache getIRCache();
	
	ApplicationContext getApplicationContext();
}
