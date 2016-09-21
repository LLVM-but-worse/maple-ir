package org.mapleir.ir.code.stmt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.ir.StatementVisitor;
import org.objectweb.asm.MethodVisitor;

public abstract class Statement implements FastGraphVertex, Opcode, Iterable<Statement> {
	
	private static int ID_COUNTER = 1;
	private final int id = ID_COUNTER++;
	
	private final int opcode;
	private Statement parent;
	private BasicBlock block;
	private Statement[] children;
	private int ptr;

	private boolean isDirty = false;
	private final List<Statement> flatChildrenCache;

	public Statement(int opcode) {
		this.opcode = opcode;
		children = new Statement[8];
		ptr = 0;

		flatChildrenCache = new ArrayList<>();
		markDirty();
	}
	
	public BasicBlock getBlock() {
		return block;
	}
	
	static String str(StackTraceElement e) {
		return e.getClassName() + "." + e.getMethodName() + ":" + e.getLineNumber();
	}
	
	public void setBlock(BasicBlock block) {
		this.block = block;
		
		// i.e. removed, so invalidate this statement.
		if(block == null) {
			markDirty();
			parent = null;
		}
		
		for(Statement s : children) {
			if(s != null) {
				s.setBlock(block);
			}
		}
	}
	
	public final int getOpcode() {
		return opcode;
	}
	
	public int deepSize() {
		int size = 1;
		for (int i = 0; i < children.length; i++)
			if (children[i] != null)
				size += children[i].deepSize();
		return size;
	}
	
	public int size() {
		int size = 0;
		for (int i = 0; i < children.length; i++)
			if (children[i] != null)
				size++;
		return size;
	}

	protected boolean shouldExpand() {
		double max = children.length * 0.50;
		return (double) size() > max;
	}
	
	protected void expand() {
		if (children.length >= Integer.MAX_VALUE)
			throw new UnsupportedOperationException();
		long len = children.length * 2;
		if (len > Integer.MAX_VALUE)
			len = Integer.MAX_VALUE;
		Statement[] newArray = new Statement[(int) len];
		System.arraycopy(children, 0, newArray, 0, children.length);
		children = newArray;
	}
	
	private Statement writeAt(int index, Statement s) {
		markDirty();
		Statement prev = children[index];
		children[index] = s;
		
		if(prev != null) {
			prev.setParent(null);
		}
		if(s != null) {
			if(s.parent != null) {
				throw new IllegalStateException(s + " already belongs to " + s.parent + " (new:" + getRootParent() + ")");
			} else {
				s.setParent(this);
			}
		}
		
		return prev;
	}
	
	public Statement read() {
		if (children[ptr] == null)
			return null;
		return children[ptr++];
	}

	public Statement read(int newPtr) {
		if (newPtr < 0 || newPtr >= children.length || (newPtr > 0 && children[newPtr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("%s, ptr=%d, len=%d, addr=%d", this.getClass().getSimpleName(), ptr, children.length, newPtr));
		return children[newPtr];
	}

	public void write(Statement node) {
		if(shouldExpand()) {
			expand();
		}
		
		if (children[ptr] == null) {
			writeAt(ptr, node);
			onChildUpdated(ptr++);
		} else {
			List<Statement> writeable = new ArrayList<>();
			for (int i = ptr; i < children.length; i++) {
				if (children[i] != null) {
					writeable.add(children[i]);
					writeAt(i, null);
				}
			}
			writeAt(ptr, node);
			onChildUpdated(ptr);
			int writePtr = ++ptr;
			for (Statement n : writeable) {
				writeAt(writePtr, n);
				onChildUpdated(writePtr++);
			}
		}
	}
	
	public void unlink() {
		markDirty();
		block = null;
		parent = null;
		
		for(Statement c : children) {
			if(c != null) {
				c.unlink();
			}
		}
	}

	public void delete() {
		delete(ptr);
	}

	public void delete(int _ptr) {
		if (_ptr < 0 || _ptr >= children.length || (_ptr > 0 && children[_ptr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("ptr=%d, len=%d, addr=%d", ptr, children.length, _ptr));
		if (children[_ptr] == null)
			throw new UnsupportedOperationException("No statement at " + _ptr);
		
		if ((_ptr + 1) < children.length && children[_ptr + 1] == null) {
			writeAt(_ptr, null);
			onChildUpdated(_ptr);
		} else {
			writeAt(_ptr, null);
			onChildUpdated(_ptr);
			for (int i = _ptr + 1; i < children.length; i++) {
				writeAt(i-1, children[i]);
				onChildUpdated(i - 1);
				writeAt(i, null);
				onChildUpdated(i);
			}
		}
	}

	public Statement overwrite(Statement node) {
		if(shouldExpand()) {
			expand();
		}
		
		if (children[ptr] != node) {
			Statement prev = writeAt(ptr, node);
			onChildUpdated(ptr);
			return prev;
		}
		
		return null;
	}

	public Statement overwrite(Statement node, int newPtr) {
		if(shouldExpand()) {
			expand();
		}
		
		if (newPtr < 0 || newPtr >= children.length || (newPtr > 0 && children[newPtr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("ptr=%d, len=%d, addr=%d", ptr, children.length, newPtr));
		
		if (children[newPtr] != node) {
			Statement prev = children[newPtr];
			writeAt(newPtr, node);
			onChildUpdated(newPtr);
			return prev;
		}
		
		return null;
	}

	public int indexOf(Statement s) {
		for (int i = 0; i < children.length; i++) {
			if (children[i] == s) {
				return i;
			}
		}
		return -1;
	}
	
	public List<Statement> getChildren() {
		List<Statement> list = new ArrayList<>();
		for (int i = 0; i < children.length; i++) {
			if (children[i] != null) {
				list.add(children[i]);
			}
		}
		return list;
	}

	public void setChildPointer(int _ptr) {
		if (_ptr < 0 || _ptr >= children.length || (_ptr > 0 && children[_ptr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("ptr=%d, len=%d, addr=%d", ptr, children.length, _ptr));
		ptr = _ptr;
	}

	public int getChildPointer() {
		return ptr;
	}
	
	public Statement getParent() {
		return parent;
	}
	
	protected void setParent(Statement parent) {
		this.parent = parent;
		for(Statement c : children) {
			if(c != null) {
				c.setParent(parent);
			}
		}
	}
	
	public Statement getRootParent() {
		Statement p = parent;
		if(p == null) {
			if(this instanceof Expression) {
				throw new UnsupportedOperationException("We've found a dangler, " + id + ". " + this);
			}
			return this;
		} else {
			return p.getRootParent();
		}
	}
	
	public boolean changesIndentation() {
		return false;
	}

	public abstract void onChildUpdated(int ptr);
	
	public abstract void toString(TabbedStringWriter printer);
	
	public abstract void toCode(MethodVisitor visitor, ControlFlowGraph cfg);
	
	public abstract boolean canChangeFlow();
	
	public abstract boolean canChangeLogic();
	
	public abstract boolean isAffectedBy(Statement stmt);
	
	@Override
	public String toString() {
		return print(this);
	}

	@Override
	public int getNumericId() {
		return id;
	}
	
	@Override
	public String getId() {
		return Long.toString(id);
	}
	
	@Override
	public int hashCode() {
		return Long.hashCode(id);
	}
	
	public abstract Statement copy();
	
	public abstract boolean equivalent(Statement s);
	
	public static String print(Statement node) {
		TabbedStringWriter printer = new TabbedStringWriter();
		node.toString(printer);
		return printer.toString();
	}

	private void markDirty() {
		isDirty = true;
//		if (parent != null)
//			parent.markDirty();
		
		for(Statement c : children) {
			if(c != null) {
				c.markDirty();
			}
		}
	}

	private void verify() {
		if (!isDirty) {
			List<Statement> verifyList = new ArrayList<>();
			new StatementVisitor(this) {
				@Override
				public Statement visit(Statement stmt) {
					verifyList.add(stmt);
					return stmt;
				}
			}.visit();
			if (!flatChildrenCache.toString().equals(verifyList.toString())) {
				System.out.println("Cache " + this + " " + flatChildrenCache);
				System.out.println("Proper " + this + " " + verifyList + "\n");
				throw new IllegalStateException("Child statement cache mismatch");
			}
		}
	}

	@Override
	public Iterator<Statement> iterator() {
		if (isDirty) {
			flatChildrenCache.clear();
			new StatementVisitor(this) {
				@Override
				public Statement visit(Statement stmt) {
					flatChildrenCache.add(stmt);
					return stmt;
				}
			}.visit();
			isDirty = false;
		}
//		else
//			verify();
		return new ArrayList<>(flatChildrenCache).iterator();
	}
}