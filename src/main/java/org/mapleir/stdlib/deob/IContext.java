package org.mapleir.stdlib.deob;

import org.mapleir.deobimpl2.cxt.CFGStore;
import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.klass.InvocationResolver;

public interface IContext {

	ApplicationClassSource getApplication();
	
	InvocationResolver getInvocationResolver();
	
	CFGStore getCFGS();
}