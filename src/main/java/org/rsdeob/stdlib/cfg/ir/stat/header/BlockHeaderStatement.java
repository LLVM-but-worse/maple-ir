package org.rsdeob.stdlib.cfg.ir.stat.header;

import org.objectweb.asm.Label;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class BlockHeaderStatement extends HeaderStatement {

	private final BasicBlock block;
	
	public BlockHeaderStatement(BasicBlock block) {
		this.block = block;
	}
	
	public BasicBlock getBlock() {
		return block;
	}
	
	@Override
	public String getHeaderId() {
		return block.getId();
	}

	@Override
	public Label getLabel() {
		return block.getLabel().getLabel();
	}

	@Override
	public Statement copy() {
		return new BlockHeaderStatement(block);
	}
}