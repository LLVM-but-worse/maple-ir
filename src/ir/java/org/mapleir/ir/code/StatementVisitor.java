package org.mapleir.ir.code;

public abstract class StatementVisitor {

	protected CodeUnit root;
	private CodeUnit[] current;
	private int[] currentPtrs;
	private int depth;
	private boolean broken;

	public StatementVisitor(CodeUnit root) {
		this.root = root;
		current = new CodeUnit[2];
		currentPtrs = new int[2];
	}
	
	public void reset(CodeUnit root) {
		this.root = root;
		current = new CodeUnit[2];
		currentPtrs = new int[2];
		broken = false;
		depth = 0;
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
	
	protected void visited(CodeUnit stmt, Expr node, int addr, Expr vis) {
		if(vis != node) {
			stmt.overwrite(vis, addr);
		}
	}

	private void _start(CodeUnit stmt, int startAddr) {
		if ((depth + 1) >= current.length) {
			expand();
		}
		depth++;
		current[depth - 1] = stmt;
		for (int addr = startAddr; stmt.read(addr) != null; addr++) {
			currentPtrs[depth - 1] = addr;
			Expr node = stmt.read(addr);
			_start(node, 0);
			// System.out.println("Visiting " + stmt + " at addr=" + addr);
			// System.out.println("   Node: " + node);
			// System.out.println("   Visit: " + visit(node));
			// stmt.overwrite(visit(node), addr);
			Expr nn = visit(node);
			visited(stmt, node, addr, nn);
			if (broken) {
				throw new RuntimeException("break");
			}
		}
		current[depth - 1] = null;
		currentPtrs[depth - 1] = 0;
		depth--;
	}

	private void expand() {
		CodeUnit[] current = new CodeUnit[this.current.length * 2];
		int[] ptr = new int[currentPtrs.length * 2];
		System.arraycopy(this.current, 0, current, 0, this.current.length);
		System.arraycopy(currentPtrs, 0, ptr, 0, currentPtrs.length);
		this.current = current;
		currentPtrs = ptr;
	}

	public CodeUnit getRoot() {
		return root;
	}

	public CodeUnit getCurrent(int depth) {
		return current[depth - 1];
	}

	public int getCurrentPtr(int depth) {
		return currentPtrs[depth - 1];
	}

	public int getDepth() {
		return depth;
	}

	public abstract Expr visit(Expr stmt);
}