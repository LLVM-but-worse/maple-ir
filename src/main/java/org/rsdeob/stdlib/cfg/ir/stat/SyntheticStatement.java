package org.rsdeob.stdlib.cfg.ir.stat;

import org.objectweb.asm.MethodVisitor;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class SyntheticStatement extends Statement {

	private Statement statement;
	
	public SyntheticStatement(Statement statement) {
		this.statement = statement;
	}

	public Statement getStatement() {
		return statement;
	}

	public void setStatement(Statement statement) {
		this.statement = statement;
	}

	@Override
	public void onChildUpdated(int ptr) {
		statement = read(ptr);
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("synth(");
		statement.toString(printer);
		printer.print(")");		
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		statement.toCode(visitor);
	}

	@Override
	public boolean canChangeFlow() {
		return statement.canChangeFlow();
	}

	@Override
	public boolean canChangeLogic() {
		return statement.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return statement.isAffectedBy(stmt);
	}

	@Override
	public Statement copy() {
		return new SyntheticStatement(statement);
	}

	@Override
	public boolean equivalent(Statement s) {
		return (s instanceof SyntheticStatement) && statement.equivalent(((SyntheticStatement) s).statement);
	}
}