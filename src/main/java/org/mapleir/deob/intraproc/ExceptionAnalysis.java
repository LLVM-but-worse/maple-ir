package org.mapleir.deob.intraproc;

import java.lang.invoke.WrongMethodTypeException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.mapleir.ir.code.CodeUnit;
import org.objectweb.asm.Type;

public interface ExceptionAnalysis {

	static Type type(Class<?> c) {
		return Type.getType(c);
	}
	
	static Set<Type> __getIntrinsicErrors() {
		/* all extend VirtualMachineError */
		Set<Type> set = new HashSet<>();
		set.add(type(InternalError.class));
		set.add(type(OutOfMemoryError.class));
		set.add(type(StackOverflowError.class));
		set.add(type(UnknownError.class));
		return set;
	}
	
	Set<Type> VM_ERRORS = Collections.unmodifiableSet(__getIntrinsicErrors());
	
	Type THROWABLE = type(Throwable.class);
	
	Type NO_FIELD_ERROR = type(NoSuchFieldError.class);
	Type NO_METHOD_ERROR = type(NoSuchMethodError.class);
	Type ABSTRACT_METHOD_ERROR = type(AbstractMethodError.class);
	Type UNSATISFIED_LINK_ERROR = type(UnsatisfiedLinkError.class);
	Type ILLEGAL_ACCESS_ERROR = type(IllegalAccessError.class);
	Type WRONG_METHOD_TYPE_EXCEPTION = type(WrongMethodTypeException.class);
	Type INSTANTIATION_ERROR = type(InstantiationError.class);
	
	Type INCOMPATIBLE_CLASS_CHANGE_ERROR = type(IncompatibleClassChangeError.class);
	
	Type CLASS_CAST_EXCEPTION = type(ClassCastException.class);
	Type ARITHMETIC_EXCEPTION = type(ArithmeticException.class);
	
	Type NULL_POINTER_EXCEPTION = type(NullPointerException.class);
	Type INDEX_OUT_OF_BOUNDS_EXCEPTION = type(IndexOutOfBoundsException.class);
	Type ILLEGAL_MONITOR_STATE_EXCEPTION = type(IllegalMonitorStateException.class);
	Type NEGATIVE_ARRAY_SIZE_EXCEPTION = type(NegativeArraySizeException.class);
	
	Type RUNTIME_EXCEPTION = type(RuntimeException.class);
	Type ERROR = type(Error.class);
	
	Set<Type> getPossibleUserThrowables(CodeUnit u);
	
	Set<Type> getForcedThrowables(CodeUnit u);
}