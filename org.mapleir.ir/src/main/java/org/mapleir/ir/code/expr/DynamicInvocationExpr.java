package org.mapleir.ir.code.expr;

import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class DynamicInvocationExpr extends InvocationExpr {

	/**
	 * The function factory, e.g. the binder.
	 * ex: metafactory
	 */
	private Handle bootstrapMethod;
	
	/**
	 * Mandatory args to the bootstrapper specifying the lambda to bind and the "decomposed" function type
	 * Ex: {Test, lambda$func$0, (ILjava/lang/Integer;)V}
	 */
	private Object[] bootstrapArgs;
	
	/**
	 * Name of the bound method.
	 * Ex: accept (for Consumer), run (for Runnable)
	 */
	private String boundName;
	
	public DynamicInvocationExpr(Handle bootstrapMethod, Object[] bootstrapArgs, String bootstrapDesc, Expr[] args, String boundName) {
		super(CallType.DYNAMIC, args, bootstrapMethod.getOwner(), bootstrapMethod.getName(), bootstrapDesc);
		
		this.bootstrapMethod = bootstrapMethod;
		this.bootstrapArgs = bootstrapArgs;
		this.boundName = boundName;
		assert(Type.getArgumentTypes(bootstrapDesc).length == args.length); // I hope this tells me when this fucks up, because this is not a matter of if, but when.
		
		for(int i = 0; i < args.length; i++) {
			overwrite(args[i], i);
		}
	}

	public Handle getBootstrapMethod() {
		return bootstrapMethod;
	}
	
	public Object[] getBootstrapArgs() {
		return bootstrapArgs;
	}
	
	public String getBoundName() {
		return boundName;
	}

	/**
	 * The descriptor of the bootstrapper method (given the 3 bootstrapArgs parameters implicitly passed).
	 * This is key, since it tells you how it affects the stack!
	 * Ex: (LTest;I)Ljava/util/function/Consumer;
	 */
	public String getBootstrapDesc() {
		return getDesc();
	}
	
	/**
	 * The value of the bound args.
	 * Ex: {lvar0, 0}
	 */
	public Expr[] getArgs() {
		return getArgumentExprs();
	}
	
	public Type getProvidedFuncType() {
		return Type.getMethodType(getBootstrapDesc()).getReturnType();
	}

	@Override
	public Expr copy() {
		Expr[] arguments = new Expr[getArgs().length];
		for (int i = 0; i < arguments.length; i++) {
			arguments[i] = getArgs()[i].copy();
		}
		
		Handle bsmHandleCopy = new Handle(bootstrapMethod.getTag(), bootstrapMethod.getOwner(), bootstrapMethod.getName(), bootstrapMethod.getDesc());
		Object[] bsmArgsCopy = new Object[bootstrapArgs.length];
		System.arraycopy(bootstrapArgs, 0, bsmArgsCopy, 0, bsmArgsCopy.length);
		return new DynamicInvocationExpr(bsmHandleCopy, bsmArgsCopy, getBootstrapDesc(), arguments, boundName);
	}
	
	@Override
	public Type getType() {
		return getProvidedFuncType();
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print(bootstrapMethod.getOwner());
		printer.print(".");
		printer.print(bootstrapMethod.getName());
		printer.print("<");
		printer.print(getProvidedFuncType().getClassName());
		printer.print(">");

		for(int i = 0; i < bootstrapArgs.length; i++) {
			Object o = bootstrapArgs[i];
			printer.print(o.toString());
			if(i != (bootstrapArgs.length -1)) {
				printer.print(", ");
			}
		}
		if(getArgs().length > 0) {
			printer.print(", ");
			for(int i = 0; i < getArgs().length; i++) {
				Expr expr = getArgs()[i];
				printer.print(expr.toString());
				if(i != (getArgs().length -1)) {
					printer.print(", ");
				}
			}
		}
		printer.print(")");
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		super.toCode(visitor, cfg);
		visitor.visitInvokeDynamicInsn(boundName, getBootstrapDesc(), bootstrapMethod, bootstrapArgs);
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if (!super.equivalent(s))
			return false;

		DynamicInvocationExpr o = (DynamicInvocationExpr) s;
		if (!boundName.equals(o.boundName))
			return false;
		if (!bootstrapMethod.equals(o.bootstrapMethod))
			return false;
		if (bootstrapArgs.length != o.bootstrapArgs.length)
			return false;
		for (int i = 0; i < bootstrapArgs.length; i++)
			if (!bootstrapArgs[i].equals(o.bootstrapArgs[i]))
				return false;
		return true;
	}
}
