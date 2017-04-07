package org.mapleir.deob.intraproc.eval.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.mapleir.deob.intraproc.eval.EvaluationFunctor;

public class ReflectiveFunctor<T> implements EvaluationFunctor<T> {
	private final Method method;
	
	public ReflectiveFunctor(Method method) {
		this.method = method;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public T eval(Object... args) throws IllegalArgumentException {
		try {
			return (T) method.invoke(null, args);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new IllegalArgumentException(String.format("Params: %s, args: %s", method.getParameterTypes(), args));
		}
	}
}