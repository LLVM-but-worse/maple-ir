package org.rsdeob.stdlib.cfg.ir.stat;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;

public interface IStackDumpNode {
	
	int getIndex();
		
	Type getType();
	
	Expression getExpression();
	
	void setExpression(Expression e);
	
	boolean isRedundant();
}