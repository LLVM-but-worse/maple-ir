package org.mapleir.ir.code;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.codegen.BytecodeFrontend;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This is the shared base between the {@link Stmt} and {@link Expr} classes,
 * which are the parents for all of the instructions that can be represented in
 * the IR. Units, like nodes in a tree, have children and depending on the type
 * of the child, i.e. whether the unit is a statement or an expression, can
 * have a parent also. These children are of the {@link Expr} type and operations
 * manipulating the children of a given node are implemented in this class.<br>
 * Operations include reading, writing and overwriting children of a given unit
 * as well polling for capacity, size and index information.
 * 
 * <p> Each CodeUnit has a unique id therefore two CodeUnit objects can only be
 * equal iff their id's match.<br>
 * Opcodes for each unit are described in {@link Opcode}.<br>
 */
public abstract class CodeUnit implements FastGraphVertex, Opcode {

	/**
	 * Flag position mask used for indicating whether the current CodeUnit is a
	 * {@link Stmt} or an {@link Expr}.
	 */
	public static final int FLAG_STMT = 0x01;

	/**
	 * Global unit identifier counter.
	 */
	private static int G_ID_COUNTER = 1;
	/**
	 * Unique global unit identifier.
	 */
	protected final int id = G_ID_COUNTER++;
	/**
	 * Opcode to encode the sort of instruction this unit is.
	 */
	protected final int opcode;
	/**
	 * Bitfield for boolean state information of this unit.
	 */
	protected int flags;
	/**
	 * The {@link BasicBlock} that this unit belongs to. If the current unit is
	 * a {@link Stmt}, the block is the direct container above this unit,
	 * otherwise for {@link Expr}'s, the block is inherited from the root
	 * parent of this expression.
	 */
	private BasicBlock block;

	/**
	 * The children of this unit.
	 */
	public Expr[] children;
	/**
	 * Index of the last item in the children array.
	 */
	private int ptr;

	public CodeUnit(int opcode) {
		this.opcode = opcode;
		children = new Expr[8];
	}

	protected void setFlag(int flag, boolean val) {
		if(val) {
			flags |= flag;
		} else {
			flags ^= flag;
		}
	}

	public boolean isFlagSet(int f) {
		return ((flags & f) != 0);
	}

	@Override
	public int getNumericId() {
		return id;
	}

	@Override
	public String getDisplayName() {
		return Integer.toString(id);
	}

	@Override
	public int hashCode() {
		return Long.hashCode(id);
	}

	public final int getOpcode() {
		return opcode;
	}

	public final String getOpname() {
		return Opcode.opname(opcode);
	}

	public BasicBlock getBlock() {
		return block;
	}

	public void setBlock(BasicBlock block) {
		this.block = block;
		
		// TODO: may invalidate the statement if block is null

		for(Expr s : children) {
			if(s != null) {
				s.setBlock(block);
			}
		}
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

	public int size() {
		int size = 0;
		for (int i = 0; i < children.length; i++) {
			if (children[i] != null) {
				size++;
			}
		}
		return size;
	}

	public int capacity() {
		return children.length;
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
		Expr[] newArray = new Expr[(int) len];
		System.arraycopy(children, 0, newArray, 0, children.length);
		children = newArray;
	}

	public int indexOf(Expr s) {
		for (int i = 0; i < children.length; i++) {
			if (children[i] == s) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Gets the child {@link Expr} at the given index.
	 * @param newPtr The index of the child.
	 * @return The child {@link Expr}.
	 */
	public Expr read(int newPtr) {
		if (newPtr < 0 || newPtr >= children.length || (newPtr > 0 && children[newPtr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("%s, ptr=%d, len=%d, addr=%d", this.getClass().getSimpleName(), ptr, children.length, newPtr));
		return children[newPtr];
	}

	protected Expr writeAt(int index, Expr s) {
		Expr prev = children[index];

		if(prev != s && prev != null) {
			prev.setParent(null);
		}
		children[index] = s;

		if(s != null) {
			if(s.parent != null) {
				throw new IllegalStateException(s + " already belongs to " + s.parent + " (new:" + (getRootParent0()) + ")");
			} else {
				s.setParent(this);
			}
		}

		return prev;
	}

	public void delete() {
		if(block == null) {
			throw new IllegalStateException();
		}
		block.remove(this);
		delete0();
	}

	protected void delete0() {
		for(Expr c : children) {
			if(c != null) {
				c.delete0();
			}
		}
	}

	public void deleteAt(int _ptr) {
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
			for (int i = _ptr + 1; i < children.length; i++) {
				Expr s = children[i];
				// set the parent to null, since
				// the intermediary step in this
				// shifting looks like:
				//   [s1, s3, s3, s4, s5, null, null, null]
				// then we remove the second one
				//   [s1, s3, null, s4, s5, null, null, null]
				
				// end of active stmts in child array.
				if(s == null) {
					break;
				}
				
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

	public Expr overwrite(Expr node, int newPtr) {
		if(shouldExpand()) {
			expand();
		}

		if (newPtr < 0 || newPtr >= children.length || (newPtr > 0 && children[newPtr - 1] == null))
			throw new ArrayIndexOutOfBoundsException(String.format("ptr=%d, len=%d, addr=%d", ptr, children.length, newPtr));

		if (children[newPtr] != node) {
			Expr prev = children[newPtr];
			writeAt(newPtr, node);
			onChildUpdated(newPtr);
			return prev;
		}

		return null;
	}

	public List<Expr> getChildren() {
		List<Expr> list = new ArrayList<>();
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

	public abstract void onChildUpdated(int ptr);

	public abstract void toString(TabbedStringWriter printer);

	public abstract void toCode(MethodVisitor visitor, BytecodeFrontend assembler);

	public abstract boolean canChangeFlow();

	public abstract boolean equivalent(CodeUnit s);

	public abstract CodeUnit copy();

	private Stmt getRootParent0() {
		if((flags & FLAG_STMT) != 0) {
			return (Stmt) this;
		} else {
			return ((Expr) this).getRootParent();
		}
	}

	protected Set<Expr> _enumerate() {
		Set<Expr> set = new HashSet<>();

		if(opcode == Opcode.PHI) {
			/*CopyPhiStmt phi = (CopyPhiStmt) this;
			for(Expr e : phi.getExpression().getArguments().values()) {
				set.add(e);
				set.addAll(e._enumerate());
			}*/
			PhiExpr phi = (PhiExpr) this;
			for(Expr e : phi.getArguments().values()) {
				set.add(e);
				set.addAll(e._enumerate());
			}
		} else {
			for(Expr c : children) {
				if(c != null) {
					set.add(c);
					set.addAll(c._enumerate());
				}
			}
		}

		return set;
	}

	public Iterable<Expr> enumerateOnlyChildren() {
		return _enumerate();
	}

	protected void dfsStmt(List<CodeUnit> list) {
		for(Expr c : children) {
			if(c != null) {
				c.dfsStmt(list);
			}
		}
		list.add(this);
	}

	public List<CodeUnit> enumerateExecutionOrder() {
		List<CodeUnit> list = new ArrayList<>();
		dfsStmt(list);
		return list;
	}

	@Override
	public String toString() {
		return print(this);
	}

	public static String print(CodeUnit node) {
		TabbedStringWriter printer = new TabbedStringWriter();
		node.toString(printer);
		return printer.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		CodeUnit codeUnit = (CodeUnit) o;

		return id == codeUnit.id;
	}
}
