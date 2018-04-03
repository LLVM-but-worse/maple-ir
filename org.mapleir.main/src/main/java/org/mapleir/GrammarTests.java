package org.mapleir;

import org.mapleir.app.service.TypeUtils;
import org.mapleir.app.service.TypeUtils.ArrayType;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.NewArrayExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.ArrayStoreStmt;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.impl.StaticMethodLocalsPool;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class GrammarTests {

	public static void main(String[] args) {
		LocalsPool pool = new StaticMethodLocalsPool(0);
		
		Type atype = TypeUtils.getPrimitiveArrayType(Opcodes.T_INT);
		NewArrayExpr arr = new NewArrayExpr(new Expr[] {new ConstantExpr(5)}, atype);
		VarExpr v = new VarExpr(pool.get(1, false), atype);
		ArrayStoreStmt stmt = new ArrayStoreStmt(v.copy(), new ConstantExpr(0), new ConstantExpr(55), ArrayType.INT);
		System.out.println(stmt);
	}
}