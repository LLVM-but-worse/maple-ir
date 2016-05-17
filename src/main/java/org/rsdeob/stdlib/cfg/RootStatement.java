package org.rsdeob.stdlib.cfg;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.stat.BlockHeaderStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class RootStatement extends Statement {

	private final MethodNode method;
	private final VarVersionsMap variables;
	private final Map<BasicBlock, BlockHeaderStatement> blockStatements;
	
	public RootStatement(MethodNode method, VarVersionsMap variables) {
		this.method = method;
		this.variables = variables;
		blockStatements = new HashMap<>();
	}
	
	public Map<BasicBlock, BlockHeaderStatement> getBlockStatements() {
		return blockStatements;
	}

	public MethodNode getMethod() {
		return method;
	}
	
	public VarVersionsMap getVariables() {
		return variables;
	}
	
	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		for (int addr = 0; read(addr) != null; addr++) {
			Statement stmt = read(addr);
//			printer.print(stmt.hashCode() + "  ");
			stmt.toString(printer);
			
			Statement next = read(addr + 1);
			if(next != null) {				
				printer.print('\n', !next.changesIndentation());
			}
		}
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		for (int addr = 0; read(addr) != null; addr++) {
			read(addr).toCode(visitor);
		}
	}
	
	public void dump(MethodNode m) {
		m.visitCode();
		m.instructions.clear();
		toCode(m);
		m.visitEnd();
	}

	@Override
	public boolean canChangeFlow() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean canChangeLogic() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		throw new UnsupportedOperationException();
	}
}