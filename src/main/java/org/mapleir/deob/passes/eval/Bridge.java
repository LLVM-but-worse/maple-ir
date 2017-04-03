package org.mapleir.deob.passes.eval;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class Bridge {
	private final Method method;
	
	public Bridge(Method method) {
		this.method = method;
	}
	
	public Object eval(Object... objs) {
		try {
			return method.invoke(null, objs);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			System.err.println(Arrays.toString(method.getParameterTypes()));
			System.err.println(Arrays.toString(objs));
			throw new RuntimeException(e);
		}
	}
	
}
