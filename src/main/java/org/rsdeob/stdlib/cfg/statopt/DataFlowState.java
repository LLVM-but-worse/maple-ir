package org.rsdeob.stdlib.cfg.statopt;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.expr.Expression;
import org.rsdeob.stdlib.cfg.expr.VarExpression;
import org.rsdeob.stdlib.cfg.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class DataFlowState {
	public static final DataFlowExpression TOP_EXPR = new DataFlowExpression();
	public static final DataFlowExpression BOTTOM_EXPR = new DataFlowExpression();

	public HashMap<VarExpression, CopyVarStatement> in;
	public HashMap<VarExpression, CopyVarStatement> out;
	public final HashSet<CopyVarStatement> gen;
	public final HashSet<CopyVarStatement> kill;

	public DataFlowState(HashSet<CopyVarStatement> gen, HashSet<CopyVarStatement> kill) {
		in = new HashMap<>();
		out = new HashMap<>();
		this.gen = gen;
		this.kill = kill;
	}

	public void copyToOut(Set<CopyVarStatement> copies) {
		for (CopyVarStatement copy : copies) {
			out.put(copy.getVariable(), copy);
		}
	}

	public HashMap<VarExpression, CopyVarStatement> getGen() {
		HashMap<VarExpression, CopyVarStatement> result = new HashMap<>();
		for (CopyVarStatement copy : gen) {
			result.put(copy.getVariable(), copy);
		}
		return result;
	}

	private static class DataFlowExpression extends Expression {
		@Override
		public String toString() {
			return this == TOP_EXPR ? "T" : "_|_";
		}

		@Override
		public void onChildUpdated(int ptr) {

		}

		@Override
		public void toString(TabbedStringWriter printer) {

		}

		@Override
		public void toCode(MethodVisitor visitor) {
			throw new UnsupportedOperationException("TopExpression is for data flow use only");
		}

		@Override
		public boolean canChangeFlow() {
			return false;
		}

		@Override
		public boolean canChangeLogic() {
			return false;
		}

		@Override
		public boolean isAffectedBy(Statement stmt) {
			return false;
		}

		@Override
		public Expression copy() {
			throw new UnsupportedOperationException("Do not copy TopExpression; instantiate a new one instead");
		}

		@Override
		public Type getType() {
			throw new UnsupportedOperationException("TopExpression has no type");
		}
	}
}
