package org.mapleir.stdlib.ir.header;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.ir.stat.Statement;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.LabelNode;

public class BlockHeaderStatement extends HeaderStatement {

	private final BasicBlock block;
	private LabelNode label;
	
	public BlockHeaderStatement(BasicBlock block) {
		this.block = block;
		label = block.getLabel();
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
		return label.getLabel();
	}

	@Override
	public Statement copy() {
		return new BlockHeaderStatement(block);
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof BlockHeaderStatement) {
			return block == ((BlockHeaderStatement) s).block;
		}
		return false;
	}

	@Override
	public void resetLabel() {
		label = new LabelNode();
		label.getLabel();
	}
}