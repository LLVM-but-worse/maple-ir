package org.rsdeob.stdlib.cfg.ir.stat.header;

import org.objectweb.asm.Label;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class StatementHeaderStatement extends HeaderStatement {

	private Statement statement;
	
	public StatementHeaderStatement(Statement statement) {
		this.statement = statement;
	}
	
	public void setStatement(Statement statement) {
		this.statement = statement;
	}
	
	@Override
	public String getHeaderId() {
		return statement.getId();
	}

	@Override
	public Label getLabel() {
		throw new UnsupportedOperationException("StatementHeader is not writable (synthetic).");
	}
}