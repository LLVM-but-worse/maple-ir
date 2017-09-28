package org.mapleir.serviceframework.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.mapleir.serviceframework.api.IServiceContext;
import org.mapleir.serviceframework.api.IServiceFactory;
import org.mapleir.serviceframework.api.IServiceQuery;
import org.mapleir.serviceframework.api.IServiceReference;
import org.mapleir.serviceframework.api.IServiceRegistry;

public class ServiceRegistryImpl implements IServiceRegistry {
	
//	private static final Log
	
	private final ServiceMap map = new ServiceMap();
	
	@Override
	public <T> IServiceReference<T> getServiceReference(Class<T> clazz) {
		Collection<IServiceReference<T>> col = getServiceReferences(clazz, new ClassServiceQuery<T>(clazz));
		if(col != null && !col.isEmpty()) {
			return col.iterator().next();
		} else {
			return null;
		}
	}

	@Override
	public <T> Collection<IServiceReference<T>> getServiceReferences(Class<T> clazz, IServiceQuery<T> query) {
		List<IServiceReference<T>> refs = map.get(clazz);
		if(refs.isEmpty()) {
			return null;
		}
		
		List<IServiceReference<T>> newList = new ArrayList<IServiceReference<T>>();
		for(IServiceReference<T> ref : refs) {
			if(query.accept(this, clazz, ref)) {
				newList.add(ref);
			}
		}
		
		return newList;
	}

	@Override
	public <T> void registerService(Class<T> klass, T obj) {
		map.get(klass).add(new InternalServiceReferenceImpl<T>(obj));
	}

	@Override
	public <T> void registerServiceFactory(Class<T> klass,
			IServiceFactory<T> factory) {
		map.get(klass).add(new InternalFactoryServiceReferenceImpl<>(factory));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getService(IServiceContext context, IServiceReference<T> _ref) {
		if(_ref instanceof IInternalServiceReference) {
			IInternalServiceReference<?> ref = ((IInternalServiceReference<?>) _ref);
			Object o = ref.get();
			ref.lock();
			
			try {
				return (T) o;
			} catch(ClassCastException e) {
				
			}
		} else {
			System.err.println("ServiceRef: " + (_ref == null ? "null" : _ref.getClass().getCanonicalName()));
		}
		return null;
	}

	@Override
	public void ungetService(IServiceContext context, IServiceReference<?> _ref) {
		if(_ref instanceof IInternalServiceReference) {
			((IInternalServiceReference) _ref).unlock();
		} else {
			System.err.println("ServiceRef: " + (_ref == null ? "null" : _ref.getClass().getCanonicalName()));
		}
	}
	
	@SuppressWarnings("serial")
	private static class ServiceMap extends HashMap<Class<?>, List<IServiceReference<?>>> {
		
		public <T> List<IServiceReference<T>> get(Class<T> key) {
			if(!(key instanceof Class))
				throw new IllegalArgumentException();
			
			Class<T> clazz = (Class<T>) key;
			List<IServiceReference<T>> list = (List) super.get(clazz);
			if(list != null) {
				return list;
			} else {
				list = new ArrayList<IServiceReference<T>>();
				super.put(clazz, (List) list);
				return list;
			}
		}
	}
}