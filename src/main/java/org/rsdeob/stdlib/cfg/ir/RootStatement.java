package org.rsdeob.stdlib.cfg.ir;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.stat.header.HeaderStatement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class RootStatement extends Statement {

	private final MethodNode method;
	private final Map<BasicBlock, HeaderStatement> headers;
	
	public RootStatement(MethodNode method) {
		this.method = method;
		headers = new HashMap<>();
	}
	
	public Map<BasicBlock, HeaderStatement> getHeaders() {
		return headers;
	}

	public MethodNode getMethod() {
		return method;
	}
	
	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		for (int addr = 0; read(addr) != null; addr++) {
			Statement stmt = read(addr);
			if(!(stmt instanceof HeaderStatement)) {
				printer.print(stmt.getId() + ". ");
			}
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