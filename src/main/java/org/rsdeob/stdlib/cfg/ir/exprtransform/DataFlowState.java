package org.rsdeob.stdlib.cfg.ir.exprtransform;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;

import static org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowExpression.NOT_A_CONST;
import static org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowExpression.UNDEFINED;
import static org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowState.CopySet.AllVarsExpression.VAR_ALL;


public class DataFlowState {
	public HashSet<CopyVarStatement> gen;
	public HashSet<CopyVarStatement> kill;
	public CopySet in;
	public CopySet out;

	public DataFlowState(HashSet<CopyVarStatement> gen, HashSet<CopyVarStatement> kill) {
		this.gen = gen;
		this.kill = kill;
		in = new CopySet();
		out = new CopySet();
	}

	public static class CopySet extends HashMap<String, CopyVarStatement> {
		public CopySet() {
			super();
		}

		public CopySet(CopySet other) {
			super(other);
		}

		public CopySet(Map<String, Set<CopyVarStatement>> bibl) {
			for (Map.Entry<String, Set<CopyVarStatement>> entry : bibl.entrySet()) {
				if (entry.getValue().size() == 1)
					super.put(entry.getKey(), entry.getValue().iterator().next());
			}
		}

		public void setVar(CopyVarStatement copy) {
			put(copy.getVariable().toString(), copy);
		}

		@Override
		public boolean containsKey(Object key) {
			if (super.containsKey(VAR_ALL))
				return key instanceof VarExpression;
			return super.containsKey(key);
		}

		@Override
		public CopyVarStatement get(Object key) {
			if (super.containsKey(key))
				return super.get(key);
			if (super.containsKey(VAR_ALL))
				return super.get(VAR_ALL);
			return null;
		}

		// Apply logic table
		private CopySet binaryOper(CopySet other, BinaryOperator<Expression> fn) {
			CopySet result = new CopySet();
			
			Set<CopyVarStatement> copies = new HashSet<>(this.values());
			copies.addAll(other.values());
			
			for (CopyVarStatement copy : copies) {
				VarExpression var = copy.getVariable();
				Expression rhs;
				if (this.containsKey(var.toString()) && other.containsKey(var.toString())) {
					rhs = fn.apply(this.get(var.toString()).getExpression(), other.get(var.toString()).getExpression());
				} else {
					// i.e. one of the statements doesn't define
					// the variable
					rhs = UNDEFINED;
				}
				result.put(var.toString(), new CopyVarStatement(var, rhs));
			}
			return result;
		}

		public CopySet meet(CopySet b) {
			return binaryOper(b, CopySet::meet);
		}

		private static Expression meet(Expression a, Expression b) {
			if (a == UNDEFINED)
				return b;
			else if (b == UNDEFINED)
				return a;
			else if (a == NOT_A_CONST || b == NOT_A_CONST)
				return NOT_A_CONST;
			else if (a.equals(b))
				return a;
			else
				return NOT_A_CONST;
		}

		public static class AllVarsExpression extends VarExpression {
			public static AllVarsExpression VAR_ALL = new AllVarsExpression();

			private AllVarsExpression() {
				super(-1, Type.VOID_TYPE, true);
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof VarExpression;
			}

			@Override
			public void toString(TabbedStringWriter printer) {
				printer.print("var*");
			}
		}
	}
}
