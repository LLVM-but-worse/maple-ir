package org.mapleir.deob.passes.eval;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Bridge {
	private final Method method;
	
	public Bridge(Method method) {
		this.method = method;
	}
	
	public Object eval(Object... objs) {
		try {
			Object ret = method.invoke(null, objs);
			return ret;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
	
}
