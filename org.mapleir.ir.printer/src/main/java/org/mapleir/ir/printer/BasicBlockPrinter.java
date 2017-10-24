package org.mapleir.ir.printer;

import java.util.Iterator;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.printer.indentation.IndentationProvider;

public class BasicBlockPrinter extends IrPrinter<BasicBlock> {
	private IrPrinter<Stmt> stmtPrinter;

	public BasicBlockPrinter(IndentationProvider indentationProvider) {
		super(indentationProvider);
		this.stmtPrinter = new StmtPrinter(indentationProvider);
	}

	@Override
	public String toIr(BasicBlock basicBlock) {
		StringBuilder sb = new StringBuilder();

		sb.append(basicBlock.getDisplayName());
		sb.append(':');
		sb.append('\n');

		Iterator<Stmt> it = basicBlock.iterator();
		while (it.hasNext()) {
			sb.append(stmtPrinter.toIr(it.next(), 1));
			sb.append('\n');
		}

		return sb.toString();
	}
}
