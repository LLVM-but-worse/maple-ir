package org.rsdeob.stdlib.cfg.stat.base;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.expr.Expression;

public interface IStackDumpNode {
	
	int getIndex();
	
	void setIndex(int index);
	
	Type getType();
	
	void setType(Type type);
	
	Expression getExpression();
	
	void setExpression(Expression expr);
	
	boolean isRedundant();
}