package org.mapleir.context;

import org.mapleir.context.app.ApplicationClassSource;
import org.mapleir.deob.util.InvocationResolver;

public interface IContext {

	ApplicationClassSource getApplication();
	
	InvocationResolver getInvocationResolver();
	
	IRCache getIRCache();
}