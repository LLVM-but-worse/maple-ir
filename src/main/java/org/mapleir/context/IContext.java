package org.mapleir.context;

import org.mapleir.context.app.ApplicationClassSource;
import org.mapleir.stdlib.util.InvocationResolver;

public interface IContext {

	ApplicationClassSource getApplication();
	
	InvocationResolver getInvocationResolver();
	
	IRCache getIRCache();
}