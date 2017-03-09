package org.mapleir.deobimpl2.cxt;

import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.klass.InvocationResolver;

public interface IContext {

	ApplicationClassSource getApplication();
	
	InvocationResolver getInvocationResolver();
	
	CFGStore getCFGS();
}