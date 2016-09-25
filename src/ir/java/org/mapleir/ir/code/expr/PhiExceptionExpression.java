package org.mapleir.ir.code.expr;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.mapleir.ir.cfg.BasicBlock;

public class PhiExceptionExpression extends PhiExpression {

	public PhiExceptionExpression(Map<BasicBlock, Expression> arguments) {
		super(EPHI, arguments);
	}
	
	@Override
	public PhiExpression copy() {
		Map<BasicBlock, Expression> map = new HashMap<>();
		for(Entry<BasicBlock, Expression> e : getArguments().entrySet()) {
			map.put(e.getKey(), e.getValue().copy());
		}
		return new PhiExceptionExpression(map);
	}
	
	@Override
	protected char getPhiType() {
		return '\u03D5';
	}
}