package org.mapleir.ir.code.expr;

import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Consider the following bsm/provider:
 *  java/lang/invoke/LambdaMetafactory.metafactory
 *      (java/lang/invoke/MethodHandles$Lookup; // VM
 *       Ljava/lang/String;                     // VM
 *       Ljava/lang/invoke/MethodType;          // VM
 *       Ljava/lang/invoke/MethodType;
 *       Ljava/lang/invoke/MethodHandle;
 *       Ljava/lang/invoke/MethodType;)
 *                                      Ljava/lang/invoke/CallSite;
 *  the first 3 arguments are provided by the vm,
 *  the string arg is included as the method name with the insn.
 *  the last 3 are provided as bsmArgs.
 */
public class DynamicInvocationExpr extends Expr {

	private Handle provider;
	private Object[] providerArgs;
	private String lambdaName;
	private String lambdaDesc;
	private Expr[] lambdaArgs;
	
	public DynamicInvocationExpr(Handle provider, Object[] providerArgs, String lambdaName, String lambdaDesc, Expr[] lambdaArgs) {
		super(DYNAMIC_INVOKE);
		
		this.provider = provider;
		this.providerArgs = providerArgs;
		this.lambdaName = lambdaName;
		this.lambdaDesc = lambdaDesc;
		this.lambdaArgs = lambdaArgs;
		assert(Type.getArgumentTypes(lambdaDesc).length == lambdaArgs.length); // I hope this tells me when this fucks up, because this is not a matter of if, but when.
		
		for(int i=0; i < lambdaArgs.length; i++) {
			overwrite(lambdaArgs[i], i);
		}
	}

	public Handle getProvider() {
		return provider;
	}
	
	public Object[] getProviderArgs() {
		return providerArgs;
	}

	public String getLambdaName() {
		return lambdaName;
	}

	public String getLambdaDesc() {
		return lambdaDesc;
	}
	
	public Expr[] getArgumentExpressions() {
		return lambdaArgs;
	}

	public void updateArgument(int index, Expr arg) {
		if (index < 0 || (index) >= lambdaArgs.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		
		lambdaArgs[index] = arg;
		overwrite(arg, index);
	}

	public void setArgumentExpressions(Expr[] _args) {
		// TODO: genpass._dynamicinvoke
//		if (callType != Opcodes.INVOKESTATIC && argumentExpressions.length <= 0) {
//			throw new ArrayIndexOutOfBoundsException();
//		}

		if (_args.length < lambdaArgs.length) {
			setChildPointer(0);
			while (read(0) != null) {
				deleteAt(getChildPointer());
			}
		}
		lambdaArgs = _args;
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
		Expr[] arguments = new Expr[lambdaArgs.length];
		for (int i = 0; i < arguments.length; i++) {
			arguments[i] = lambdaArgs[i].copy();
		}
		Object[] pargs = new Object[providerArgs.length];
		System.arraycopy(providerArgs, 0, pargs, 0, pargs.length);
		return new DynamicInvocationExpr(new Handle(provider.getTag(), provider.getOwner(), provider.getName(), provider.getDesc()), pargs, lambdaName, lambdaDesc, arguments);
	}

	@Override
	public Type getType() {
		return Type.getMethodType(lambdaDesc).getReturnType();
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print(provider.getOwner() + "." + provider.getName() + "<");
		for(int i=0; i < providerArgs.length; i++) {
			Object o = providerArgs[i];
			printer.print(o.toString());
			if(i != (providerArgs.length -1)) {
				printer.print(", ");
			}
		}
		if(lambdaArgs.length > 0) {
			printer.print(", ");
			for(int i = 0; i < lambdaArgs.length; i++) {
				Object o = lambdaArgs[i];
				printer.print(o.toString());
				if(i != (lambdaArgs.length -1)) {
					printer.print(", ");
				}
			}
		}
		printer.print(")");
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		// I'm not sure if this is correct. Perhaps only for metafactories.
		assert(providerArgs.length == 3);
		assert(providerArgs[providerArgs.length - 2] instanceof Handle);
		Type[] argTypes = Type.getArgumentTypes(lambdaDesc);
		
		for (int i = 0; i < lambdaArgs.length; i++) {
			lambdaArgs[i].toCode(visitor, cfg);
			if (TypeUtils.isPrimitive(lambdaArgs[i].getType())) {
				int[] cast = TypeUtils.getPrimitiveCastOpcodes(lambdaArgs[i].getType(), argTypes[i]);
				for (int a = 0; a < cast.length; a++) {
					visitor.visitInsn(cast[a]);
				}
			}
		}
		visitor.visitInvokeDynamicInsn(lambdaName, lambdaDesc, provider, providerArgs);
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
			if(!lambdaName.equals(o.lambdaName) || !provider.equals(o.provider) || !lambdaDesc.equals(o.lambdaDesc)) {
				return false;
			}
			if(lambdaArgs.length != o.lambdaArgs.length) {
				return false;
			}
			for(int i = 0; i < lambdaArgs.length; i++) {
				if(!lambdaArgs[i].equivalent(o.lambdaArgs[i])) {
					return false;
				}
			}
			
			if(providerArgs.length != o.providerArgs.length) {
				return false;
			}
			for(int i=0; i < providerArgs.length; i++) {
				// FIXME: ?
				if(!providerArgs[i].equals((o.providerArgs[i]))) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
}
