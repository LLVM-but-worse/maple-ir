package org.mapleir.ir.code.expr;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
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
public class DynamicInvocationExpression extends Expression {

	private Handle provider;
	private Object[] providerArgs;
	private String name;
	private String desc;
	private Expression[] args;
	
	public DynamicInvocationExpression(Handle provider, Object[] providerArgs, String name, String desc, Expression[] args) {
		super(DYNAMIC_INVOKE);
		
		this.provider = provider;
		this.providerArgs = providerArgs;
		this.name = name;
		this.desc = desc;
		this.args = args;
		
		for(int i=0; i < args.length; i++) {
			overwrite(args[i], i);
		}
	}

	public Handle getProvider() {
		return provider;
	}
	
	public Object[] getProviderArgs() {
		return providerArgs;
	}

	public String getName() {
		return name;
	}

	public String getDesc() {
		return desc;
	}
	
	public Expression[] getArgumentExpressions() {
		return args;
	}

	public void updateArgument(int index, Expression arg) {
		if (index < 0 || (index) >= args.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		
		args[index] = arg;
		overwrite(arg, index);
	}

	public void setArgumentExpressions(Expression[] _args) {
		// TODO: genpass._dynamicinvoke
//		if (callType != Opcodes.INVOKESTATIC && argumentExpressions.length <= 0) {
//			throw new ArrayIndexOutOfBoundsException();
//		}

		if (_args.length < args.length) {
			setChildPointer(0);
			while (read(0) != null) {
				delete();
			}
		}
		args = _args;
		for (int i = 0; i < _args.length; i++) {
			overwrite(_args[i], i);
		}
	}
	
	@Override
	public void onChildUpdated(int ptr) {
		updateArgument(ptr, (Expression) read(ptr));
	}

	@Override
	public Expression copy() {
		Expression[] arguments = new Expression[args.length];
		for (int i = 0; i < arguments.length; i++) {
			arguments[i] = args[i].copy();
		}
		Object[] pargs = new Object[providerArgs.length];
		System.arraycopy(providerArgs, 0, pargs, 0, pargs.length);
		return new DynamicInvocationExpression(new Handle(provider.getTag(), provider.getOwner(), provider.getName(), provider.getDesc()), pargs, name, desc, arguments);
	}

	@Override
	public Type getType() {
		return Type.getMethodType(desc).getReturnType();
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print(provider.getOwner() + "." + provider.getName() + " (");
		for(int i=0; i < providerArgs.length; i++) {
			Object o = providerArgs[i];
			printer.print(o.toString());
			if(i != (providerArgs.length -1)) {
				printer.print(", ");
			}
		}
		printer.print(").<dynamic_call>(");
		if(args.length > 0) {
			printer.tab();
			printer.print("\n");
			for(int i=0; i < args.length; i++) {
				Object o = args[i];
				printer.print(o.toString());
				if(i != (args.length -1)) {
					printer.print(", ");
				}
			}
			printer.untab();
			printer.print("\n");
		}
		printer.print(")");
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		// TODO: casting
		visitor.visitInvokeDynamicInsn(name, desc, provider, providerArgs);
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
	public boolean canChangeLogic() {
		return true;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		if(stmt.canChangeLogic()) {
			return true;
		}
		
		for(Expression e : args) {
			if(e.isAffectedBy(stmt)) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s.getOpcode() == DYNAMIC_INVOKE) {
			DynamicInvocationExpression o = (DynamicInvocationExpression) s;
			if(!name.equals(o.name) || !provider.equals(o.provider) || !desc.equals(o.desc)) {
				return false;
			}
			if(args.length != o.args.length) {
				return false;
			}
			for(int i=0; i < args.length; i++) {
				if(!args[i].equivalent(o.args[i])) {
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