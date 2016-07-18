package org.rsdeob.stdlib.ir.transform.ssa;

import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.expr.PhiExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.locals.BasicLocal;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.rsdeob.stdlib.ir.stat.Statement.enumerate;

public class SSAUtil {
	/**
	 * Safely casts a local into a versioned local;
	 * @param l local to cast
	 * @return the given local, casted to a VersionedLocal
	 */
	public static VersionedLocal vl(Local l) {
		if (l instanceof VersionedLocal)
			return (VersionedLocal) l;
		else
			throw new IllegalArgumentException("Local " + l + " is not versioned!");
	}
	
	/**
	 * Returns a set containing all of the statements and child statements in the given CodeBody matching a filter
	 * @param code CodeBody to visit all statements in
	 * @param filter Filter which returns true for statements to be visited
	 * @return An set containing all statements and child statements
	 */
	public static Set<Statement> visitAll(CodeBody code, Predicate<Statement> filter) {
		Set<Statement> allStmts = new HashSet<>();
		code.forEach(stmt -> enumerate(stmt).stream().filter(filter).forEach(allStmts::add));
		return allStmts;
	}
	
	public static Set<Statement> visitAll(CodeBody code) {
		return visitAll(code, stmt -> true);
	}
	
	public static void replaceLocals(CodeBody code, Predicate<VersionedLocal> filter, Function<VersionedLocal, BasicLocal> replaceFn) {
		for (Statement child : visitAll(code)) {
			VarExpression var = null;
			if (child instanceof VarExpression) {
				var = (VarExpression) child;
			} else if (child instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) child;
				var = copy.getVariable();
			} else if (child instanceof PhiExpression) {
				throw new IllegalStateException(child.toString());
			}
			if (var != null && var.getLocal() instanceof VersionedLocal) {
				VersionedLocal versionedLocal = (VersionedLocal) var.getLocal();
				if (filter.test(versionedLocal))
					var.setLocal(replaceFn.apply(versionedLocal));
			}
		}
	}
}
