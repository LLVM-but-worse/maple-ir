package org.rsdeob.stdlib.cfg.ir.stat;

import org.objectweb.asm.MethodVisitor;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class BlockHeaderStatement extends Statement {

	private BasicBlock block;
	
	public BlockHeaderStatement(BasicBlock block) {
		this.block = block;
	}
	
	public BasicBlock getBlock() {
		return block;
	}

	@Override
	public void onChildUpdated(int ptr) {
		
	}

	@Override
	public boolean changesIndentation() {
		return true;
	}
	
	@Override
	public void toString(TabbedStringWriter printer) {
		if(printer.getTabCount() > 0) {
			printer.untab();
		}
		printer.print(block.getId() + ":");
		printer.tab();
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		visitor.visitLabel(block.getLabel().getLabel());
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