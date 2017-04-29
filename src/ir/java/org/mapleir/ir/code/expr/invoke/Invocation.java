package org.mapleir.ir.code.expr.invoke;

import org.mapleir.ir.code.Expr;

/* Definitions:
 *   parameterExprs:= the Exprs that are actually passed to 
 *                    the receiver object, i.e excluding the
 *                    receiver.
 *   argumentExprs:= the Exprs that are both virtually and
 *                   physically passed during the invocation,
 *                   i.e. including the receiver.
 *   physicalReceiver:= a receiver object on which a method is
 *                      called that may be acquired between
 *                      before the call. */
public abstract class Invocation extends Expr {
	
	public Invocation(int opcode) {
		super(opcode);
	}

	public abstract boolean isStatic();
	
	public abstract Expr getPhysicalReceiver();
	
	public abstract Expr[] getArgumentExprs();
	
	public abstract Expr[] getParameterExprs();
	
	public abstract String getOwner();
	
	public abstract String getName();
	
	public abstract String getDesc();
}