package org.mapleir.serviceframework.util;

import org.mapleir.serviceframework.api.IServiceContext;
import org.mapleir.serviceframework.api.IServiceReference;
import org.mapleir.serviceframework.api.IServiceRegistry;
import org.mapleir.serviceframework.impl.ServiceRegistryImpl;

public class ServiceHelper {
	
	private static IServiceRegistry GLOBAL_REGISTRY;
	
	public static IServiceRegistry getGlobalServiceRegistry() {
		return GLOBAL_REGISTRY;
	}
	
	private static void __bootstrap() {
		if(GLOBAL_REGISTRY != null) {
			throw new RuntimeException(new IllegalStateException());
		} else {
			GLOBAL_REGISTRY = new ServiceRegistryImpl();
		}
	}
	
	static {
		__bootstrap();
	}
	
	public static <T> T attemptGet(IServiceRegistry registry, IServiceContext context, Class<T> serviceClazz) {
		IServiceReference<T> ref = registry.getServiceReference(context, serviceClazz);
		try {
			return registry.getService(ref);
		} finally {
			if(ref != null) {
				registry.ungetService(ref);
			}
		}
	}
}