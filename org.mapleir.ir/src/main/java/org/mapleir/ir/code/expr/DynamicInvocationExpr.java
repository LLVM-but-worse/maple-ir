package org.mapleir.ir.code.expr;

import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class DynamicInvocationExpr extends Expr {

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
	 * The descriptor of the bootstrapper method (given the 3 bootstrapArgs parameters implicitly passed).
	 * This is key, since it tells you how it affects the stack!
	 * Ex: (LTest;I)Ljava/util/function/Consumer;
	 */
	private String bootstrapDesc;
	
	/**
	 * Name of the bound method.
	 * Ex: accept (for Consumer), run (for Runnable)
	 */
	private String boundName;

	/**
	 * The value of the bound args.
	 * Ex: {lvar0, 0}
	 */
	private Expr[] boundArgs;
	
	public DynamicInvocationExpr(Handle bootstrapMethod, Object[] bootstrapArgs, String bootstrapDesc, String boundName, Expr[] boundArgs) {
		super(DYNAMIC_INVOKE);
		
		this.bootstrapMethod = bootstrapMethod;
		this.bootstrapArgs = bootstrapArgs;
		this.boundName = boundName;
		this.bootstrapDesc = bootstrapDesc;
		this.boundArgs = boundArgs;
		assert(Type.getArgumentTypes(bootstrapDesc).length == boundArgs.length); // I hope this tells me when this fucks up, because this is not a matter of if, but when.
		
		for(int i = 0; i < boundArgs.length; i++) {
			overwrite(boundArgs[i], i);
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

	public String getBootstrapDesc() {
		return bootstrapDesc;
	}
	
	public Expr[] getBoundArgs() {
		return boundArgs;
	}

	public void updateArgument(int index, Expr arg) {
		if (index < 0 || (index) >= boundArgs.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		
		boundArgs[index] = arg;
		overwrite(arg, index);
	}

	public void setArgumentExpressions(Expr[] _args) {
		// TODO: genpass._dynamicinvoke
//		if (callType != Opcodes.INVOKESTATIC && argumentExpressions.length <= 0) {
//			throw new ArrayIndexOutOfBoundsException();
//		}

		if (_args.length < boundArgs.length) {
			setChildPointer(0);
			while (read(0) != null) {
				deleteAt(getChildPointer());
			}
		}
		boundArgs = _args;
		for (int i = 0; i < _args.length; i++) {
			overwrite(_args[i], i);
		}
	}
	
	@Override
	public void onChildUpdated(int ptr) {
		updateArgument(ptr, read(ptr));
	}

	@Override
	public Expr copy() {
		Expr[] arguments = new Expr[boundArgs.length];
		for (int i = 0; i < arguments.length; i++) {
			arguments[i] = boundArgs[i].copy();
		}
		
		Handle bsmHandleCopy = new Handle(bootstrapMethod.getTag(), bootstrapMethod.getOwner(), bootstrapMethod.getName(), bootstrapMethod.getDesc());
		Object[] bsmArgsCopy = new Object[bootstrapArgs.length];
		System.arraycopy(bootstrapArgs, 0, bsmArgsCopy, 0, bsmArgsCopy.length);
		return new DynamicInvocationExpr(bsmHandleCopy, bsmArgsCopy, bootstrapDesc, boundName, arguments);
	}

	public Type getProvidedFuncType() {
		return Type.getMethodType(bootstrapDesc).getReturnType();
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
		if(boundArgs.length > 0) {
			printer.print(", ");
			for(int i = 0; i < boundArgs.length; i++) {
				Expr expr = boundArgs[i];
				printer.print(expr.toString());
				if(i != (boundArgs.length -1)) {
					printer.print(", ");
				}
			}
		}
		printer.print(")");
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		Type[] argTypes = Type.getArgumentTypes(bootstrapDesc);
		for (int i = 0; i < boundArgs.length; i++) {
			boundArgs[i].toCode(visitor, cfg);
			if (TypeUtils.isPrimitive(boundArgs[i].getType())) {
				int[] cast = TypeUtils.getPrimitiveCastOpcodes(boundArgs[i].getType(), argTypes[i]);
				for (int a = 0; a < cast.length; a++) {
					visitor.visitInsn(cast[a]);
				}
			}
		}
		visitor.visitInvokeDynamicInsn(boundName, bootstrapDesc, bootstrapMethod, bootstrapArgs);
	}
	
	@Override
	public Precedence getPrecedence0() {
		return Precedence.METHOD_INVOCATION;
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if(s.getOpcode() == DYNAMIC_INVOKE) {
			DynamicInvocationExpr o = (DynamicInvocationExpr) s;
			if(!boundName.equals(o.boundName) || !bootstrapMethod.equals(o.bootstrapMethod) || !bootstrapDesc.equals(o.bootstrapDesc)) {
				return false;
			}
			if(boundArgs.length != o.boundArgs.length) {
				return false;
			}
			for(int i = 0; i < boundArgs.length; i++) {
				if(!boundArgs[i].equivalent(o.boundArgs[i])) {
					return false;
				}
			}
			
			if(bootstrapArgs.length != o.bootstrapArgs.length) {
				return false;
			}
			for(int i = 0; i < bootstrapArgs.length; i++) {
				// FIXME: ?
				if(!bootstrapArgs[i].equals((o.bootstrapArgs[i]))) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
}
