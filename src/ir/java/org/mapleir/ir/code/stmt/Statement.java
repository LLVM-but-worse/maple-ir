package org.mapleir.ir.code.stmt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.objectweb.asm.MethodVisitor;

public abstract class Statement implements FastGraphVertex, Opcode {
	
	private static int ID_COUNTER = 1;
	private final int id = ID_COUNTER++;
	
	private final int opcode;
	private Statement parent;
	private BasicBlock block;
	public Statement[] children;
	private int ptr;

//	private boolean isDirty = false;
//	private final List<Statement> flatChildrenCache;

	public Statement(int opcode) {
		this.opcode = opcode;
		children = new Statement[8];
		ptr = 0;

//		flatChildrenCache = new ArrayList<>();
//		markDirty();
	}
	
	public BasicBlock getBlock() {
		return block;
	}
	
	public void setBlock(BasicBlock block) {
		this.block = block;
		
		// i.e. removed, so invalidate this statement.
		if(block == null) {
//			if(Test.temp) {
//				System.out.println("Statement.setBlock() " + this);
//			}
//			markDirty();
//			setParent(null);
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
	
	public final String getOpname() {
		return Opcode.opname(opcode);
	}
	
	public int deepSize() {
		int size = 1;
		for (int i = 0; i < children.length; i++) {
			if (children[i] != null) {
				size += children[i].deepSize();
			}
		}
		return size;
	}
	
	public int capacity() {
		return children.length;
	}
	
	public int size() {
		int size = 0;
		for (int i = 0; i < children.length; i++) {
			if (children[i] != null) {
				size++;
			}
		}
		
		/* it's a debug thing 
		int size2 = 0;
		for(int i=0; i < children.length; i++) {
			if(children[i] == null) {
				size2 = i;
				break;
			}
		}
		if(size != size2) {
			throw new IllegalStateException(String.format("%d vs %d", size, size2));
		} */
		
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
//		markDirty();
		Statement prev = children[index];

		if(prev != s && prev != null) {
			prev.setParent(null);
		}
		children[index] = s;
		
		if(s != null) {
			if(s.parent != null) {
				throw new IllegalStateException(s + " already belongs to " + s.parent + " (new:" + (parent != null ? getRootParent() : this) + ")");
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
//		setBlock(null);
//		setParent(null);
//		if(parent == null) {
//			throw new UnsupportedOperationException("{{" + this + "}} unlink from nothing");
//		} else {
//			
//		}
		if(parent != null) {
			parent.deleteAt(parent.indexOf(this));
		}
	}

	protected void delete0() {
		unlink();
		for(Statement c : children) {
			if(c != null) {
				c.delete0();
			}
		}
	}
	
	public void delete() {
		if(parent != null) {
			parent.deleteAt(parent.indexOf(this));
		} else {
			if(block == null) {
				throw new IllegalStateException();
			}
			block.remove(this);
			delete0();
		}
	}

	public void deleteAt(int _ptr) {
//		System.out.println("del " + children[_ptr]);
//		System.out.println("in");
//		System.out.println(children[_ptr].getRootParent());
		
		if (_ptr < 0 || _ptr >= children.length || (_ptr > 0 && children[_ptr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("ptr=%d, len=%d, addr=%d", ptr, children.length, _ptr));
		if (children[_ptr] == null)
			throw new UnsupportedOperationException("No statement at " + _ptr);
		
		if ((_ptr + 1) < children.length && children[_ptr + 1] == null) {
			// ptr: s5 (4)
			// len = 8
			// before: [s1, s2, s3, s4, s5  , null, null, null]
			// after : [s1, s2, s3, s4, null, null, null, null]
			writeAt(_ptr, null);
			onChildUpdated(_ptr);
		} else {
			// ptr: s2 (1)
			// len = 8
			// before: [s1, s2, s3, s4, s5 ,  null, null, null]
			// del s2 (1)
			// before: [s1, null, s3, s4, s5 ,  null, null, null]
			// ptr+1 = s3 (2)
			// (ptr+1 to len) = {2, 3, 4, 5, 6, 7}
			// shift elements down 1
			// after : [s1, s3, s4, s5, null, null, null, null]
			writeAt(_ptr, null);
			onChildUpdated(_ptr);
//			System.out.println("lop");
			for (int i = _ptr + 1; i < children.length; i++) {
				Statement s = children[i];
				// set the parent to null, since
				// the intermediary step in this
				// shifting looks like:
				//   [s1, s3, s3, s4, s5, null, null, null]
				// then we remove the second one
				//   [s1, s3, null, s4, s5, null, null, null]
				if(s != null) {
					s.setParent(null);
				}
				writeAt(i-1, s);
				onChildUpdated(i - 1);
				writeAt(i, null);
				onChildUpdated(i);
				// we need to set the parent again,
				// because we have 2 of the same
				// node in the children array, which
				// means the last writeAt call, sets
				// the parent as null.
				if(s != null) {
					s.setParent(this);
				}
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
//			if(prev != null) {
//				System.out.println("In: " + this);
//				System.out.println(" myPar: {{" + parent + "}}");
//				System.out.println("  Overwrite {{" + prev + "}} with {{" + node + "}}");
//				System.out.println("   prePrevPar: " + prev.parent + ", sPar: " + node.parent);
//			}
			writeAt(newPtr, node);
			onChildUpdated(newPtr);

//			if(prev != null) {
//				System.out.println("   posPrevPar: " + prev.parent + ", sPar: " + node.parent);
//				System.out.println(" myPar: {{" + parent + "}}");
//			}
			
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
//		Statement oldParent = this.parent;
		this.parent = parent;
		if(parent != null) {
			block = parent.block;
		} else {
			block = null;
		}
		
//		if(Test.temp) {
//			System.out.println("  setParent of {{" + this + "}} === {{" + parent + "}}");
//		}
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

//	private void markDirty() {
//		isDirty = true;
//		markDirtyUp();
//		
//		for(Statement c : children) {
//			if(c != null) {
//				c.markDirty();
//			}
//		}
//	}
	
//	private void markDirtyUp() {
//		isDirty = true;
//		if (parent != null)
//			parent.markDirtyUp();
//	}

/*	@Override
	public Iterator<Statement> iterator() {
//		if (isDirty) {
//		flatChildrenCache.clear();
//		new StatementVisitor(this) {
//			@Override
//			public Statement visit(Statement stmt) {
//				flatChildrenCache.add(stmt);
//				return stmt;
//			}
//		}.visit();
//		isDirty = false;
//	}
//	return new ArrayList<>(flatChildrenCache).iterator();
//		new StatementVisitor(this) {
//			@Override
//			public Statement visit(Statement stmt) {
//				list.add(stmt);
//				return stmt;
//			}
//		}.visit();
		List<Statement> list = new ArrayList<>();
		for(Statement c : children) {
			if(c != null) {
				list.add(c);
			}
		}
		return list.iterator();
	} */
	
	/**
	 * Checks the consistency of a top level statement. Note that if this
	 * Statement is not the highest one in the chain, it will produce false
	 * exceptions.
	 */
	public void checkConsistency() {
		checkConsistency(null);
	}
	
	public void checkConsistency(Statement parent) {
		if(this.parent != parent) {
			System.err.println("pc: " + (parent != null ? Arrays.toString(parent.children) : "NOPAR"));
			System.err.println("ac: " + (this.parent != null ? Arrays.toString(this.parent.children) : "NO PARENT"));
			throw new IllegalStateException("Differening parents: " + this + "\n   Suggested: " + (this.parent == null ? "NULLL" : this.parent) + "\n   Actual: " + parent);
		}
		
		boolean prev = true;
		for(int i=0; i < children.length; i++) {
			Statement s = children[i];
			if(!prev) { 
				if(s != null) {
					StringBuilder sb = new StringBuilder();
					for(Statement s1 : children) {
						sb.append("  ").append(s1).append("\n");
					}
					throw new IllegalStateException("Disjoint children: " + this);
				}
			}
			
			prev = (s != null);
		}
		
		for(Statement c : children) {
			if(c != null) {
				c.checkConsistency(this);
			}
		}
	}

	public void spew(String ind) {
//		System.out.println(ind + this);
//		System.out.println(ind + "c: " + Arrays.toString(children));
//		for(Statement c : getChildren()) {
//			c.spew(ind + "  ");
//		}
	}
	
	protected Set<Statement> _enumerate() {
		Set<Statement> set = new HashSet<>();
		set.add(this);
		
		if(opcode == Opcode.PHI_STORE) {
			CopyPhiStatement phi = (CopyPhiStatement) this;
			for(Expression e : phi.getExpression().getArguments().values()) {
				set.addAll(e._enumerate());
			}
		} else {
			for(Statement c : children) {
				if(c != null) {
					set.addAll(c._enumerate());
				}
			}
		}
		
		return set;
	}
	
	public Iterable<Statement> enumerate() {
		return _enumerate();
	}
	
	protected void dfsStmt(List<Statement> list) {
		for(Statement c : children) {
			if(c != null) {
				c.dfsStmt(list);
			}
		}
		list.add(this);
	}
	
	public List<Statement> execEnumerate() {
		List<Statement> list = new ArrayList<>();
		dfsStmt(list);
		return list;
	}
}