package org.mapleir.state;

import org.mapleir.stdlib.klass.InvocationResolver;

public interface IContext {

	ApplicationClassSource getApplication();
	
	InvocationResolver getInvocationResolver();
	
	CFGStore getCFGS();
}