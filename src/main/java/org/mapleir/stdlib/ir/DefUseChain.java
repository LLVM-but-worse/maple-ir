package org.mapleir.stdlib.ir;

import java.util.Set;

import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.ir.expr.VarExpression;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.stat.Statement;

public class DefUseChain {

	private final NullPermeableHashMap<Local, Set<Statement>> uses;
	
	public DefUseChain() {
		uses = new NullPermeableHashMap<>(new SetCreator<>());
	}
	
	public Set<Statement> uses(Local l) {
		return uses.getNonNull(l);
	}
	
	public void create(CodeBody code) {
		uses.clear();
		
		for(Statement stmt : code) {
			create(stmt);
		}
	}
	
	protected void create(Statement stmt) {
		for(Statement s : Statement.enumerate_deep(stmt)) {
			if(s instanceof VarExpression) {
				VarExpression v = (VarExpression) s;
				Local l = v.getLocal();
				uses.getNonNull(l).add(stmt);
			}
		}
	}
	
	public void added(Statement stmt) {
		create(stmt);
	}
	
	public void remove(Statement stmt)  {
		for(Statement s : Statement.enumerate_deep(stmt)) {
			if(s instanceof VarExpression) {
				VarExpression v = (VarExpression) s;
				Local l = v.getLocal();
				uses.getNonNull(l).remove(stmt);
			}
		}
	}
	
	public void replace(Statement stmt, Local from, Local to) {
		for(Statement s : Statement.enumerate_deep(stmt)) {
			if(s instanceof VarExpression) {
				VarExpression v = (VarExpression) s;
				Local l = v.getLocal();
				if(l == from) {
					uses.getNonNull(from).remove(stmt);
					uses.getNonNull(to).add(stmt);
				}
			}
		}
	}
}