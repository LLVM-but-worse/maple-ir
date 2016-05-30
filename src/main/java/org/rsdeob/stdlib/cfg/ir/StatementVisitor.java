package org.rsdeob.stdlib.cfg.ir;

import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public abstract class StatementVisitor {

	private Statement root;
	private Statement[] current;
	private int[] currentPtrs;
	private int depth;
	private boolean broken;

	public StatementVisitor(Statement root) {
		this.root = root;
		current = new Statement[2];
		currentPtrs = new int[2];
	}

	public void _break() {
		broken = true;
	}

	public void visit() {
		visit(0);
	}

	public void visit(int startPtr) {
		try {
			_start(root, startPtr);
		} catch (RuntimeException t) {
			if (t.getMessage() == null || !t.getMessage().equals("break")) {
				throw t;
			}
			depth = 0;
			broken = false;
		}
	}

	private void _start(Statement stmt, int startAddr) {
		if ((depth + 1) >= current.length) {
			expand();
		}
		depth++;
		current[depth - 1] = stmt;
		for (int addr = startAddr; stmt.read(addr) != null; addr++) {
			currentPtrs[depth - 1] = addr;
			Statement node = stmt.read(addr);
			_start(node, 0);
			stmt.overwrite(visit(node), addr);
			if (broken) {
				throw new RuntimeException("break");
			}
		}
		current[depth - 1] = null;
		currentPtrs[depth - 1] = 0;
		depth--;
	}

	private void expand() {
		Statement[] current = new Statement[this.current.length * 2];
		int[] ptr = new int[currentPtrs.length * 2];
		System.arraycopy(this.current, 0, current, 0, this.current.length);
		System.arraycopy(currentPtrs, 0, ptr, 0, currentPtrs.length);
		this.current = current;
		currentPtrs = ptr;
	}

	public Statement getRoot() {
		return root;
	}

	public Statement getCurrent(int depth) {
		return current[depth - 1];
	}

	public int getCurrentPtr(int depth) {
		return currentPtrs[depth - 1];
	}

	public int getDepth() {
		return depth;
	}

	public abstract Statement visit(Statement stmt);
}