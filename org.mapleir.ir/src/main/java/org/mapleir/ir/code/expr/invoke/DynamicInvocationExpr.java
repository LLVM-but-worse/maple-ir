package org.mapleir.ir.code.expr.invoke;

import org.apache.log4j.Logger;
import org.mapleir.app.service.InvocationResolver;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.mapleir.asm.MethodNode;

import java.util.HashSet;
import java.util.Set;

/**
 * IMPORTANT:
 * - getDesc() refers to the desc of the ACTUAL CALLEE.
 * - getArgumentExprs() refers to the args of the ACTUAL CALLEE. getBoundArgs() is an alias.
 * - getBoundName() refers to the name of the ACTUAL CALLEE (i think).
 *
 * - getBootstrapDesc() refers to the desc of the BOOTSTRAP METHOD.
 * - getBootstrapArgs() refers to the args of the BOOTSTRAP METHOD.
 * - getName() refers to the name of the BOOTSTRAP METHOD.
 * - getOwner() refers to the owner of the BOOTSTRAP METHOD.
 */
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

	public DynamicInvocationExpr(Handle bootstrapMethod, Object[] bootstrapArgs, String resolvedCallDesc, Expr[] args, String boundName) {
		super(CallType.DYNAMIC, args, bootstrapMethod.getOwner(), bootstrapMethod.getName(), resolvedCallDesc);

		this.boundName = boundName;

		this.bootstrapMethod = bootstrapMethod;
		this.bootstrapArgs = bootstrapArgs;

		// I hope this tells me when this fucks up, because this is not a matter of if, but when.
		assert(Type.getArgumentTypes(resolvedCallDesc).length == args.length) : "You fucked up";
		assert(Type.getArgumentTypes(bootstrapMethod.getDesc()).length - 3 == bootstrapArgs.length) : "You fucked up";

		for(int i = 0; i < args.length; i++) {
			writeAt(args[i], i);
		}
	}

	// Getter/setters pertaining to the ACTUAL RESOLVED CALLEE.

	public String getBoundName() {
		return boundName;
	}

	public void setBoundName(String boundName) {
		this.boundName = boundName;
	}

	/**
	 * The value of the bound args. These are passed to the RESOLVED CALLEE.
	 * Ex: {lvar0, 0}
	 *
	 * Equivalent to getArgumentExprs(), but included for clarity.
	 */
	public Expr[] getBoundArgs() {
		return getArgumentExprs();
	}

	// Getters/setters pertaining to the BOOTSTRAP METHOD.

	public Object[] getBootstrapArgs() {
		return bootstrapArgs;
	}

	public void setBootstrapArgs(Object[] bootstrapArgs) {
		this.bootstrapArgs = bootstrapArgs;
	}

	// Equivalent to getOwner(), but included for clarity
	public String getBootstrapOwner() {
		return bootstrapMethod.getOwner();
	}

	// Equivalent to getName(), but included for clarity
	public String getBootstrapName() {
		return bootstrapMethod.getName();
	}

	/**
	 * The descriptor of the bootstrapper method (given the 3 bootstrapArgs parameters implicitly passed).
	 * These aren't supplied on the stack! The arguments on the stack go to the resolved callee!
	 * Ex: (LTest;I)Ljava/util/function/Consumer;
	 */
	public String getBootstrapDesc() {
		return bootstrapMethod.getDesc();
	}

	@Override
	public void setOwner(String owner) {
		super.setOwner(owner);
		bootstrapMethod = new Handle(bootstrapMethod.getTag(), owner, bootstrapMethod.getName(), bootstrapMethod.getDesc());
	}

	@Override
	public void setName(String name) {
		super.setName(name);
		bootstrapMethod = new Handle(bootstrapMethod.getTag(), bootstrapMethod.getOwner(), name, bootstrapMethod.getDesc());
	}

	// Other functions

	public Type getProvidedFuncType() {
		return Type.getMethodType(getDesc()).getReturnType();
	}

	@Override
	public DynamicInvocationExpr copy() {
		Handle bsmHandleCopy = new Handle(bootstrapMethod.getTag(), bootstrapMethod.getOwner(), bootstrapMethod.getName(), bootstrapMethod.getDesc());
		Object[] bsmArgsCopy = new Object[bootstrapArgs.length];
		System.arraycopy(bootstrapArgs, 0, bsmArgsCopy, 0, bsmArgsCopy.length);
		return new DynamicInvocationExpr(bsmHandleCopy, bsmArgsCopy, getDesc(), copyArgs(), boundName);
	}
	
	@Override
	public Type getType() {
		return getProvidedFuncType();
	}

	@Override
	public boolean isStatic() {
		return true;
	}

	@Override
	public boolean isDynamic() {
		return true;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("dynamic_invoke<");
		printer.print(getProvidedFuncType().getClassName());
		printer.print(">(");
		super.toString(printer);
		printer.print(")");
	}

	@Override
	public Expr[] getPrintedArgs() {
		Expr[] result = new Expr[bootstrapArgs.length + getArgumentExprs().length];
		for (int i = 0; i < bootstrapArgs.length; i++)
			result[i] = new ConstantExpr(bootstrapArgs[i]); // this is such a hack!
		System.arraycopy(getArgumentExprs(), 0, result, bootstrapArgs.length, getArgumentExprs().length);
		return result;
	}

	@Override
	protected void generateCallCode(MethodVisitor visitor) {
		visitor.visitInvokeDynamicInsn(boundName, getDesc(), bootstrapMethod, bootstrapArgs);
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

	@Override
	public Set<MethodNode> resolveTargets(InvocationResolver res) {
		// this is probably like 99% of all the invokedynamics
		if (bootstrapMethod.getOwner().equals("java/lang/invoke/LambdaMetafactory")
				&& bootstrapMethod.getName().equals("metafactory")) {
			assert (bootstrapArgs.length == 3 && bootstrapArgs[1] instanceof Handle);
			Handle boundFunc = (Handle) bootstrapArgs[1];
			switch (boundFunc.getTag()) {
			case Opcodes.H_INVOKESTATIC:
				return StaticInvocationExpr.resolveStaticCall(res, boundFunc.getOwner(), boundFunc.getName(), boundFunc.getDesc());
			case Opcodes.H_INVOKESPECIAL:
				if (!boundFunc.getName().equals("<init>")) {
					Logger.getLogger(this.getClass()).warn("Lambda function invocation of type H_INVOKESPECIAL is linking against " + boundFunc.getName());
				}
				//assert(boundFunc.getName().equals("<init>"));
				return VirtualInvocationExpr.resolveSpecialInvocation(res, boundFunc.getOwner(), boundFunc.getDesc());
			case Opcodes.H_INVOKEINTERFACE:
			case Opcodes.H_INVOKEVIRTUAL:
				return VirtualInvocationExpr.resolveVirtualInvocation(res, boundFunc.getOwner(), boundFunc.getOwner(), boundFunc.getDesc());
			default:
				throw new IllegalArgumentException("Unexpected metafactory bootstrap tag?? " + boundFunc.getTag());
			}
		}
		return new HashSet<>();
	}
}
