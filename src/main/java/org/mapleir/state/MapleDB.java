package org.mapleir.state;

import org.mapleir.stdlib.klass.InvocationResolver;
import org.mapleir.stdlib.klass.library.ApplicationClassSource;

public class MapleDB implements IContext {
	private ApplicationClassSource app;
	private InvocationResolver resolver;
	private CFGStore cfgs;
	
	public MapleDB(ApplicationClassSource app) {
		this.app = app;
		resolver = new InvocationResolver(app);
		cfgs = new CFGStore();
	}
	
	@Override
	public CFGStore getCFGS() {
		return cfgs;
	}
	@Override
	public InvocationResolver getInvocationResolver() {
		return resolver;
	}

	@Override
	public ApplicationClassSource getApplication() {
		return app;
	}
}
