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
	private String name;
	private String desc;
	
	public DynamicInvocationExpression(Handle provider, String name, String desc) {
		super(DYNAMIC_INVOKE);
		
		this.provider = provider;
		this.name = name;
		this.desc = desc;
	}

	public Handle getProvider() {
		return provider;
	}

	public String getName() {
		return name;
	}

	public String getDesc() {
		return desc;
	}
	
	@Override
	public void onChildUpdated(int ptr) {
		
	}

	@Override
	public Expression copy() {
		return null;
	}

	@Override
	public Type getType() {
		// TODO:
		return Type.getMethodType(desc).getReturnType();
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print(provider.getOwner() + "." + provider.getName() + " " + provider.getDesc());
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		
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
		return false;
	}

	@Override
	public boolean equivalent(Statement s) {
		return false;
	}
}