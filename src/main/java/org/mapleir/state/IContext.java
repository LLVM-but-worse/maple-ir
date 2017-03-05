package org.mapleir.state;

import org.mapleir.stdlib.klass.InvocationResolver;
import org.mapleir.stdlib.klass.library.ApplicationClassSource;

public interface IContext {

	ApplicationClassSource getApplication();
	
	InvocationResolver getInvocationResolver();
	
	CFGStore getCFGS();
}