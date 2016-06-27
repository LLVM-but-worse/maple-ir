package org.rsdeob.stdlib.ir;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.cfg.util.TypeUtils.ArrayType;
import org.rsdeob.stdlib.ir.expr.*;
import org.rsdeob.stdlib.ir.expr.ArithmeticExpression.Operator;
import org.rsdeob.stdlib.ir.expr.ComparisonExpression.ValueComparisonType;
import org.rsdeob.stdlib.ir.header.BlockHeaderStatement;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.stat.*;
import org.rsdeob.stdlib.ir.stat.ConditionalJumpStatement.ComparisonType;
import org.rsdeob.stdlib.ir.stat.MonitorStatement.MonitorMode;

public class StatementGenerator implements Opcodes {

	private static final int[] EMPTY_STACK_HEIGHTS = new int[]{};
	private static final int[] SINGLE_RETURN_HEIGHTS = new int[]{1};
	private static final int[] DOUBLE_RETURN_HEIGHTS = new int[]{2};
	
	private static final int[] DUP_HEIGHTS = new int[]{1};
	private static final int[] SWAP_HEIGHTS = new int[]{1, 1};
	private static final int[] DUP_X1_HEIGHTS = new int[]{1, 1};
	private static final int[] DUP2_32_HEIGHTS = new int[]{1, 1};
	private static final int[] DUP2_X1_32_HEIGHTS = new int[]{1, 1, 1};
	private static final int[] DUP2_X1_64_HEIGHTS = new int[]{2, 1};
	private static final int[] DUP2_X2_64x64_HEIGHTS = new int[]{2, 2};
	private static final int[] DUP2_X2_64x32_HEIGHTS = new int[]{2, 1, 1};
	private static final int[] DUP2_X2_32x64_HEIGHTS = new int[]{1, 1, 2};
	private static final int[] DUP2_X2_32x32_HEIGHTS = new int[]{1, 1, 1, 1};
	private static final int[] DUP_X2_64_HEIGHTS = new int[]{1, 2};
	private static final int[] DUP_X2_32_HEIGHTS = new int[]{1, 1, 1};

	MethodNode m;
	ControlFlowGraph graph;
	Set<BasicBlock> updatedStacks;
	Set<BasicBlock> analysedBlocks;
	LinkedList<BasicBlock> queue;
	Map<BasicBlock, BlockHeaderStatement> headers;
	RootStatement root;
	int stackBase;

	transient volatile BasicBlock currentBlock;
	transient volatile ExpressionStack currentStack;

	public StatementGenerator(ControlFlowGraph cfg) {
		graph = cfg;
		m = cfg.getMethod();
		updatedStacks = new HashSet<>();
		analysedBlocks = new HashSet<>();
		queue = new LinkedList<>();
		headers = new HashMap<>();
		// System.out.println(graph);
	}
	
	public void init(int base) {
		Statement.ID_COUNTER = 0;
		stackBase = base;
		root = new RootStatement(m.maxLocals);
		
		for (BasicBlock b : graph.vertices()) {
			headers.put(b, new BlockHeaderStatement(b));
		}
		
		queueEntryBlocks();
	}
	
	private void defineInputs(MethodNode m, BasicBlock b) {
		// build the entry in sets
		Type[] args = Type.getArgumentTypes(m.desc);
		int index = 0;
		if((m.access & Opcodes.ACC_STATIC) == 0) {
			addEntry(index, Type.getType(m.owner.name), b);
			index++;
		}
	
		for(int i=0; i < args.length; i++) {
			Type arg = args[i];
			addEntry(index, arg, b);
			index += arg.getSize();
		}
	}
	
	private void addEntry(int index, Type type, BasicBlock b) {
		CopyVarStatement stmt = selfDefine(_var_expr(index, type, false));
		b.getStatements().add(new SyntheticStatement(stmt));
	}
	
	private CopyVarStatement selfDefine(VarExpression var) {
		return new CopyVarStatement(var, var);
	}

	public RootStatement buildRoot() {
		for (BasicBlock b : graph.vertices()) {
			root.write(headers.get(b));
			for (Statement n : b.getStatements()) {
				root.write(n);
			}
		}
		return root;
	}

	void addStmt(Statement stmt) {
		currentBlock.getStatements().add(stmt);
	}

	Expression pop() {
		return currentStack.pop();
	}
	
	Expression peek() {
		return currentStack.peek();
	}

	void push(Expression e) {
		currentStack.push(e);
	}

	public void createExpressions() {
		while (queue.size() > 0) {
			BasicBlock b = queue.removeFirst();
			if (!analysedBlocks.contains(b)) {
				analysedBlocks.add(b);

				ExpressionStack stack = process(b);

				// check merge exit stack with next input stack
				BasicBlock im = b.getImmediate();
				if (im != null && !queue.contains(im)) {
					update_target_stack(b, im, stack);
//					enqueue.addFirst(im);
				}

				/* updateTargetStack is now handled in 
				 * the instruction handler methods.
				 * 
				 * for (FlowEdge _succ : b.getSuccessors()) {
				 *	 if (!(_succ instanceof TryCatchEdge)) {
				 *		BasicBlock succ = _succ.dst;
				 *	    updateTargetStack(b, succ, stack);
				 *   }
				 * } */
			}
		}
	}

	Statement getLastStatement(BasicBlock b) {
		return b.getStatements().get(b.getStatements().size() - 1);
	}
	
	 void save_stack() {
		int height = currentStack.height();
		while(height > 0) {
			int index = height;
			Expression expr = currentStack.pop();
			Type type = assign_stack(index, expr);
			push(load_stack(index, type));
			
			height -= type.getSize();
		}
	} 

	void update_target_stack(BasicBlock b, BasicBlock target, ExpressionStack stack) {
		if(updatedStacks.contains(b)) {
			save_stack();
		}
		// called just before a jump to a successor block may
		// happen. any operations, such as comparisons, that
		// happen before the jump are expected to have already
		// popped the left and right arguments from the stack before
		// checking the merge state.
		if (!updatedStacks.contains(target)) {
			// unfinalised block found.
			// System.out.println("Setting target stack of " + target.getId() + " to " + stack);
			target.setInputStack(stack.copy());
			updatedStacks.add(target);

			queue.addLast(target);
		} else if (!can_succeed(target.getInputStack(), stack)) {
			// if the targets input stack is finalised and
			// the new stack cannot merge into it, then there
			// is an error in the bytecode (verifier error).
			System.out.println("Current: " + stack + " in " + b.getId());
			System.out.println("Target : " + target.getInputStack() + " in " + target.getId());
			throw new IllegalStateException("Stack coherency mismatch into #" + target.getId());
		}
	}

	boolean can_succeed(ExpressionStack s, ExpressionStack succ) {
		// quick check stack heights
		if (s.height() != succ.height()) {
			return false;
		}
		ExpressionStack c0 = s.copy();
		ExpressionStack c1 = succ.copy();
		while (c0.height() > 0) {
			Expression e1 = c0.pop();
			Expression e2 = c1.pop();
			if (!(e1 instanceof VarExpression) || !(e2 instanceof VarExpression)) {
				return false;
			}
			if (((VarExpression) e1).getIndex() != ((VarExpression) e2).getIndex()) {
				return false;
			}
			if (!e1.getType().getDescriptor().equals(e2.getType().getDescriptor())) {
				return false;
			}
		}
		return true;
	}

	void queueEntryBlocks() {
		// entry blocks by definition have a certain
		// input stack (empty or a jvm created exception
		// object), so they are assumed to be 'updated'
		// which means that they can't be changed, only
		// used as references for predecessor edges to
		// check whether they are coherent when merging
		// into them.

		for(BasicBlock b : graph.getEntries()) {
			_entry(b);
		}

		for (TryCatchBlockNode tc : m.tryCatchBlocks) {
			_catches(tc);
		}
	}

	ExpressionStack process(BasicBlock b) {
//		System.out.println("Processing " + b.getId());
		updatedStacks.add(b);
		ExpressionStack stack = b.getInputStack().copy();

		currentBlock = b;
		currentStack = stack;

		for (AbstractInsnNode ain : b.getInsns()) {
			int opcode = ain.opcode();
			if(opcode != -1 && Statement.ID_COUNTER < 60) {
				  System.out.println("Executing " + Printer.OPCODES[ain.opcode()]);
				  System.out.println(" Prestack : " + stack);
			}
			switch (opcode) {
				case -1: {
					if (ain instanceof LabelNode)
						throw new IllegalStateException("Block should not contain label.");
					break;
				}
				case BIPUSH:
				case SIPUSH:
					_const(((IntInsnNode) ain).operand);
					break;
				case ACONST_NULL:
					_const(null);
					break;
				case ICONST_M1:
				case ICONST_0:
				case ICONST_1:
				case ICONST_2:
				case ICONST_3:
				case ICONST_4:
				case ICONST_5:
					_const((int) (opcode - ICONST_M1) - 1);
					break;
				case LCONST_0:
				case LCONST_1:
					_const((long) (opcode - LCONST_0));
					break;
				case FCONST_0:
				case FCONST_1:
				case FCONST_2:
					_const((float) (opcode - FCONST_0));
					break;
				case DCONST_0:
				case DCONST_1:
					_const((long) (opcode - DCONST_0));
					break;
				case LDC:
					_const(((LdcInsnNode) ain).cst);
					break;
				case LCMP:
				case FCMPL:
				case FCMPG:
				case DCMPL:
				case DCMPG: {
					_compare(ValueComparisonType.resolve(opcode));
					break;
				}
				case NEWARRAY: {
					_new_array(
						new Expression[] { stack.pop() }, 
						TypeUtils.getPrimitiveArrayType(((IntInsnNode) ain).operand)
					);
					break;
				}
				case ANEWARRAY: {
					_new_array(
						new Expression[] { stack.pop() }, 
						Type.getType("[L" + ((TypeInsnNode) ain).desc + ";")
					);
					break;
				}
				case MULTIANEWARRAY: {
					MultiANewArrayInsnNode in = (MultiANewArrayInsnNode) ain;
					Expression[] bounds = new Expression[in.dims];
					for (int i = in.dims - 1; i >= 0; i--) {
						bounds[i] = stack.pop();
					}
					_new_array(bounds, Type.getType(in.desc));
					break;
				}

				case RETURN:
					_return(Type.VOID_TYPE);
					break;
				case ATHROW:
					_throw();
					break;
					
				case MONITORENTER:
					_monitor(MonitorMode.ENTER);
					break;
				case MONITOREXIT:
					_monitor(MonitorMode.EXIT);
					break;
					
				case IRETURN:
				case LRETURN:
				case FRETURN:
				case DRETURN:
				case ARETURN:
					_return(Type.getReturnType(m.desc));
					break;
				case IADD:
				case LADD:
				case FADD:
				case DADD:
				case ISUB:
				case LSUB:
				case FSUB:
				case DSUB:
				case IMUL:
				case LMUL:
				case FMUL:
				case DMUL:
				case IDIV:
				case LDIV:
				case FDIV:
				case DDIV:
				case IREM:
				case LREM:
				case FREM:
				case DREM:
				
				case ISHL:
				case LSHL:
				case ISHR:
				case LSHR:
				case IUSHR:
				case LUSHR:
				
				case IAND:
				case LAND:
					
				case IOR:
				case LOR:
					
				case IXOR:
				case LXOR:
					_arithmetic(Operator.resolve(opcode));
					break;
				
				case INEG:
				case DNEG:
					_neg();
					break;
					
				case ARRAYLENGTH:
					_arraylength();
					break;
					
				case IALOAD:
				case LALOAD:
				case FALOAD:
				case DALOAD:
				case AALOAD:
				case BALOAD:
				case CALOAD:
				case SALOAD:
					_load_array(ArrayType.resolve(opcode));
					break;
					
				case IASTORE:
				case LASTORE:
				case FASTORE:
				case DASTORE:
				case AASTORE:
				case BASTORE:
				case CASTORE:
				case SASTORE:
					_store_array(ArrayType.resolve(opcode));
					break;
					
				case POP:
					_pop(1);
					break;
				case POP2:
					_pop(2);
					break;
					
				case DUP:
					_dup();
					break;
				case DUP_X1:
					_dup_x1();
					break;
				case DUP_X2:
					_dup_x2();
					break;

				case DUP2:
					_dup2();
					break;
				case DUP2_X1:
					_dup2_x1();
					break;
				case DUP2_X2:
					_dup2_x2();
					break;
					
				case SWAP:
					_swap();
					break;
					
				case I2L:
				case I2F:
				case I2D:
				case L2I:
				case L2F:
				case L2D:
				case F2I:
				case F2L:
				case F2D:
				case D2I:
				case D2L:
				case D2F:
				case I2B:
				case I2C:
				case I2S:
					_cast(TypeUtils.getCastType(opcode));
					break;
				case CHECKCAST:
					_cast(Type.getType("L" + ((TypeInsnNode)ain).desc + ";"));
					break;
				case INSTANCEOF:
					_instanceof(Type.getType("L" + ((TypeInsnNode)ain).desc + ";"));
					break;
				case NEW:
					_new(Type.getType("L" + ((TypeInsnNode)ain).desc + ";"));
					break;
					
				case INVOKEDYNAMIC:
					throw new UnsupportedOperationException("INVOKEDYNAMIC");
				case INVOKEVIRTUAL:
				case INVOKESTATIC:
				case INVOKESPECIAL:
				case INVOKEINTERFACE:
					MethodInsnNode min = (MethodInsnNode) ain;
					_call(opcode, min.owner, min.name, min.desc);
					break;
					
				case ILOAD:
				case LLOAD:
				case FLOAD:
				case DLOAD:
				case ALOAD:
					_load(((VarInsnNode) ain).var, TypeUtils.getLoadType(opcode));
					break;
					
				case ISTORE:
				case LSTORE:
				case FSTORE:
				case DSTORE:
				case ASTORE:
					_store(((VarInsnNode) ain).var, TypeUtils.getStoreType(opcode));
					break;
					
				case IINC:
					IincInsnNode iinc = (IincInsnNode) ain;
					_inc(iinc.var, iinc.incr);
					break;
					
				case PUTFIELD:
				case PUTSTATIC: {
					FieldInsnNode fin = (FieldInsnNode) ain;
					_store_field(opcode, fin.owner, fin.name, fin.desc);
					break;
				}
				case GETFIELD:
				case GETSTATIC:
					FieldInsnNode fin = (FieldInsnNode) ain;
					_load_field(opcode, fin.owner, fin.name, fin.desc);
					break;
					
				case TABLESWITCH: {
					TableSwitchInsnNode tsin = (TableSwitchInsnNode) ain;
					LinkedHashMap<Integer, BasicBlock> targets = new LinkedHashMap<>();
					for(int i=tsin.min; i <= tsin.max; i++) {
						BasicBlock targ = graph.getBlock(tsin.labels.get(i - tsin.min));
						targets.put(i, targ);
					}
					_switch(targets, graph.getBlock(tsin.dflt));
					break;
				}
				
				case LOOKUPSWITCH: {
					LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) ain;
					LinkedHashMap<Integer, BasicBlock> targets = new LinkedHashMap<>();
					for(int i=0; i < lsin.keys.size(); i++) {
						int key = lsin.keys.get(i);
						BasicBlock targ = graph.getBlock(lsin.labels.get(i));
						targets.put(key, targ);
					}
					_switch(targets, graph.getBlock(lsin.dflt));
					break;
				}
				
				case GOTO:
					_jump_uncond(graph.getBlock(((JumpInsnNode) ain).label));
					break;
				case IFNULL:
				case IFNONNULL:
					_jump_null(graph.getBlock(((JumpInsnNode) ain).label), opcode == IFNONNULL);
					break;
					
				case IF_ICMPEQ:
				case IF_ICMPNE:
				case IF_ICMPLT:
				case IF_ICMPGE:
				case IF_ICMPGT:
				case IF_ICMPLE:
				case IF_ACMPEQ:
				case IF_ACMPNE:
					_jump_compare(graph.getBlock(((JumpInsnNode) ain).label), ComparisonType.getType(opcode));
					break;
					
				case IFEQ:
				case IFNE:
				case IFLT:
				case IFGE:
				case IFGT:
				case IFLE:
					_jump_cmp0(graph.getBlock(((JumpInsnNode) ain).label), ComparisonType.getType(opcode));
					break;
			}
			
			if(Statement.ID_COUNTER < 60) {
				 System.out.println(" Poststack: " + stack);
				 System.out.println("   Added stmt: " + getLastStatement(currentBlock));
				 System.out.println();
			}
			
			 /*System.out.println(" Block stmts: ");
			 for(Statement stmt : b.getStatements()) {
				 System.out.println("   " + stmt);
			 }
			 System.out.println();*/
		}

		return stack;
	}

	// var[index] = expr
	Type assign_stack(int index, Expression expr) {
		Type type = expr.getType();
		// VarExpression var = new VarExpression(index, type, true);
		VarExpression var = _var_expr(index, type, true);
		CopyVarStatement stmt = new CopyVarStatement(var, expr);
		addStmt(stmt);
		return type;
	}
	
	Expression load_stack(int index, Type type) {
//		return root.getLocals().get(index, true);
		// return new VarExpression(index, type, true);
		return _var_expr(index, type, true);
	}
	
	void _jump_compare(BasicBlock target, ComparisonType type, Expression left, Expression right) {
		update_target_stack(currentBlock, target, currentStack);
		addStmt(new ConditionalJumpStatement(left, right, headers.get(target), type));
	}
	
	void _jump_compare(BasicBlock target, ComparisonType type) {
		Expression right = pop();
		Expression left = pop();
		_jump_compare(target, type, left, right);
	}
	
	void _jump_cmp0(BasicBlock target, ComparisonType type) {
		Expression left = pop();
		ConstantExpression right = new ConstantExpression(0);
		_jump_compare(target, type, left, right);
	}

	void _jump_null(BasicBlock target, boolean invert) {
		Expression left = pop();
		ConstantExpression right = new ConstantExpression(null);
		ComparisonType type = invert ? ComparisonType.NE : ComparisonType.EQ;
		
		_jump_compare(target, type, left, right);
	}

	void _jump_uncond(BasicBlock target) {
		update_target_stack(currentBlock, target, currentStack);
		addStmt(new UnconditionalJumpStatement(headers.get(target)));
	}

	void _entry(BasicBlock entry) {
		entry.setInputStack(new ExpressionStack(1024 * 8));
		queue.add(entry);
		updatedStacks.add(entry);
		defineInputs(m, entry);
	}

	void _catches(TryCatchBlockNode tc) {
		LabelNode label = tc.handler;
		BasicBlock handler = graph.getBlock(label);
		ExpressionStack stack = new ExpressionStack(1024 * 8);
		stack.push(new CaughtExceptionExpression(tc.type));
		handler.setInputStack(stack);

		queue.addLast(handler);
		updatedStacks.add(handler);
	}

	void _nop() {

	}

	void _const(Object o) {
		Expression e = new ConstantExpression(o);
		int index = currentStack.height();
		Type type = assign_stack(index, e);
		push(load_stack(index, type));
	}

	void _compare(ValueComparisonType ctype) {
		Expression right = pop();
		Expression left = pop();
		push(new ComparisonExpression(left, right, ctype));
	}

	void _return(Type type) {
		if (type == Type.VOID_TYPE) {
			currentStack.assertHeights(EMPTY_STACK_HEIGHTS);
			addStmt(new ReturnStatement());
		} else {
			if(type.getSize() == 2) {
				currentStack.assertHeights(DOUBLE_RETURN_HEIGHTS);
			} else {
				currentStack.assertHeights(SINGLE_RETURN_HEIGHTS);
			}
			addStmt(new ReturnStatement(type, pop()));
		}
	}

	void _throw() {
		currentStack.assertHeights(SINGLE_RETURN_HEIGHTS);
		addStmt(new ThrowStatement(pop()));
	}

	void _monitor(MonitorMode mode) {
		addStmt(new MonitorStatement(pop(), mode));
	}

	void _arithmetic(Operator op) {
		push(new ArithmeticExpression(pop(), pop(), op));
	}
	
	void _neg() {
		push(new NegationExpression(pop()));
	}
	
	void _arraylength() {
		push(new ArrayLengthExpression(pop()));
	}
	
	void _load_array(ArrayType type) {
		// prestack: var1, var0 (height = 2)
		// poststack: var0
		// assignments: var0 = var0[var1]
		int height = currentStack.height();
		Expression index = pop();
		Expression array = pop();
		assign_stack(height - 2, new ArrayLoadExpression(array, index, type));
		push(load_stack(height - 2, type.getType()));
	}
	
	void _store_array(ArrayType type) {
		Expression value = pop();
		Expression index = pop();
		Expression array = pop();
		addStmt(new ArrayStoreStatement(array, index, value, type));
	}
	
	void _pop(int amt) {
		for(int i=0; i < amt; i++) {
			addStmt(new PopStatement(pop()));
		}
	}
	
	void _dup() {
		// prestack: var0 (height = 1)
		// poststack: var1, var0
		// assignments: var1 = var0(initial)
		currentStack.assertHeights(DUP_HEIGHTS);
		int baseHeight = currentStack.height();

		Expression var0 = pop();

		Type var1Type = assign_stack(baseHeight, var0); // var1 = var0
		push(load_stack(baseHeight - 1, var0.getType())); //  push var0
		push(load_stack(baseHeight, var1Type)); // push var1
	}

	void _dup_x1() {
		// prestack: var1, var0 (height = 2)
		// poststack: var2, var1, var0
		// assignments: var0 = var1(initial)
		// assignments: var1 = var0(initial)
		// assignments: var2 = var1(initial)
		currentStack.assertHeights(DUP_X1_HEIGHTS);
		int baseHeight = currentStack.height();

		Expression var1 = pop();
		Expression var0 = pop();

		Type var3Type = assign_stack(baseHeight + 1, var0); // var3 = var0

		Type var0Type = assign_stack(baseHeight - 2, var1); // var0 = var1(initial)
		Type var2Type = assign_stack(baseHeight + 0, var1); // var2 = var1(initial)
		Type var1Type = assign_stack(baseHeight - 1, load_stack(baseHeight + 1, var3Type)); // var1 = var3 = var0(initial)

		push(load_stack(baseHeight - 2, var0Type)); // push var0
		push(load_stack(baseHeight - 1, var1Type)); // push var1
		push(load_stack(baseHeight + 0, var2Type)); // push var2
	}

	void _dup_x2() {
		int baseHeight = currentStack.height();

		if(currentStack.peek(1).getType().getSize() == 2) {
			// prestack: var2, var0 (height = 3)
			// poststack: var3, var1, var0
			// assignments: var0 = var2(initial)
			// assignments: var1 = var0(initial)
			// assignments: var3 = var2(initial)
			currentStack.assertHeights(DUP_X2_64_HEIGHTS);

			Expression var2 = pop();
			Expression var0 = pop();

			Type var4Type = assign_stack(baseHeight + 1, var0); // var4 = var0(initial)

			Type var0Type = assign_stack(baseHeight - 3, var2); // var0 = var2(initial)
			Type var3Type = assign_stack(baseHeight + 0, var2); // var3 = var2(initial)
			Type var1Type = assign_stack(baseHeight - 2, load_stack(baseHeight + 1, var4Type)); // var1 = var4 = var0(initial)

			push(load_stack(baseHeight - 3, var0Type)); // push var0
			push(load_stack(baseHeight - 2, var1Type)); // push var1
			push(load_stack(baseHeight + 0, var3Type)); // push var3
		} else {
			// prestack: var2, var1, var0 (height = 3)
			// poststack: var3, var2, var1, var0
			// assignments: var0 = var2(initial)
			// assignments: var1 = var0(initial)
			// assignments: var2 = var1(initial)
			// assignments: var3 = var2(initial)
			currentStack.assertHeights(DUP_X2_32_HEIGHTS);

			Expression var2 = pop();
			Expression var1 = pop();
			Expression var0 = pop();

			Type var4Type = assign_stack(baseHeight + 1, var0); // var4 = var0(initial)
			Type var5Type = assign_stack(baseHeight + 2, var1); // var5 = var1(initial)

			Type var0Type = assign_stack(baseHeight - 3, var2); // var0 = var2(initial)
			Type var3Type = assign_stack(baseHeight + 0, var2); // var3 = var2(initial)
			Type var1Type = assign_stack(baseHeight - 2, load_stack(baseHeight + 1, var4Type)); // var1 = var4 = var0(initial)
			Type var2Type = assign_stack(baseHeight - 1, load_stack(baseHeight + 2, var5Type)); // var2 = var5 = var1(initial)

			push(load_stack(baseHeight - 3, var0Type)); // push var0
			push(load_stack(baseHeight - 2, var1Type)); // push var1
			push(load_stack(baseHeight - 1, var2Type)); // push var2
			push(load_stack(baseHeight + 0, var3Type)); // push var3
		}
	}

	void _dup2() {
		int baseHeight = currentStack.height();

		if(peek().getType().getSize() == 2) {
			// prestack: var0 (height = 2)
			// poststack: var2, var0
			// assignments: var2 = var0

			Expression var0 = pop();

			Type var2Type = assign_stack(baseHeight, var0); // var2 = var0
			push(load_stack(baseHeight - 2, var0.getType())); //  push var0
			push(load_stack(baseHeight, var2Type)); // push var2
		} else {
			// prestack: var1, var0 (height = 2)
			// poststack: var3, var2, var1, var0
			// assignments: var2 = var0(initial)
			// assignments: var3 = var1(initial)
			currentStack.assertHeights(DUP2_32_HEIGHTS);

			Expression var1 = pop();
			Expression var0 = pop();

			Type var2Type = assign_stack(baseHeight + 0, var0); // var2 = var0
			Type var3Type = assign_stack(baseHeight + 1, var1); // var3 = var1

			push(load_stack(baseHeight - 2, var0.getType())); // push var0
			push(load_stack(baseHeight - 1, var1.getType())); // push var1
			push(load_stack(baseHeight + 0, var2Type)); // push var2
			push(load_stack(baseHeight + 1, var3Type)); // push var3
		}
	}

	void _dup2_x1() {
		Type topType = peek().getType();
		int baseHeight = currentStack.height();

		if(topType.getSize() == 2) {
			// prestack: var2, var0 (height = 3)
			// poststack: var3, var2, var0
			// assignments: var0 = var2(initial)
			// assignemnts: var2 = var0(initial)
			// assignments: var3 = var2(initial)
			currentStack.assertHeights(DUP2_X1_64_HEIGHTS);

			Expression var2 = pop();
			Expression var0 = pop();

			Type var4Type = assign_stack(baseHeight + 1, var0); // var4 = var0(initial)

			Type var3Type = assign_stack(baseHeight - 0, var2); // var3 = var2(initial)
			Type var0Type = assign_stack(baseHeight - 3, var2); // var0 = var2(initial)
			Type var2Type = assign_stack(baseHeight - 1, load_stack(baseHeight + 1, var4Type)); // var2 = var4 = var0(initial)

			push(load_stack(baseHeight - 3, var0Type)); // push var0
			push(load_stack(baseHeight - 1, var2Type)); // push var2
			push(load_stack(baseHeight - 0, var3Type)); // push var3
		} else {
			// prestack: var2, var1, var0 (height = 3)
			// poststack: var4, var3, var2, var1, var0
			// assignments: var0 = var1(initial)
			// assignments: var1 = var2(initial)
			// assignments: var2 = var0(initial)
			// assignments: var3 = var1(initial)
			// assignments: var4 = var2(initial)
			currentStack.assertHeights(DUP2_X1_32_HEIGHTS);

			Expression var2 = pop();
			Expression var1 = pop();
			Expression var0 = pop();

			Type var5Type = assign_stack(baseHeight + 2, var0); // var5 = var0(initial)

			Type var0Type = assign_stack(baseHeight - 3, var1); // var0 = var1(initial)
			Type var1Type = assign_stack(baseHeight - 2, var2); // var1 = var2(initial)
			Type var3Type = assign_stack(baseHeight + 0, var1); // var3 = var1(initial)
			Type var4Type = assign_stack(baseHeight + 1, var2); // var4 = var2(initial)
			Type var2Type = assign_stack(baseHeight - 1, load_stack(baseHeight + 2, var5Type)); // var2 = var5 = var0(initial)

			push(load_stack(baseHeight - 3, var0Type)); // push var0
			push(load_stack(baseHeight - 2, var1Type)); // push var1
			push(load_stack(baseHeight - 1, var2Type)); // push var2
			push(load_stack(baseHeight + 0, var3Type)); // push var3
			push(load_stack(baseHeight + 1, var4Type)); // push var4
		}
	}

	void _dup2_x2() {
		Type topType = peek().getType();
		int baseHeight = currentStack.height();
		if(topType.getSize() == 2) {
			Type bottomType = currentStack.peek(1).getType();
			if (bottomType.getSize() == 2) {
				// 64x64
				// prestack: var2, var0 (height = 4)
				// poststack: var4, var2, var0
				// assignments: var0 = var2(initial)
				// assignments: var2 = var0(initial)
				// assignments: var4 = var2(initial)
				currentStack.assertHeights(DUP2_X2_64x64_HEIGHTS);

				Expression var2 = pop();
				Expression var0 = pop();

				Type var6Type = assign_stack(baseHeight + 2, var0); // var6 = var0(initial)

				Type var0Type = assign_stack(baseHeight - 4, var2); // var0 = var2(initial)
				Type var4Type = assign_stack(baseHeight - 0, var2); // var4 = var2(initial)
				Type var2Type = assign_stack(baseHeight - 2, load_stack(baseHeight + 2, var6Type)); // var2 = var6 = var0(initial)

				push(load_stack(baseHeight - 4, var0Type)); // push var0;
				push(load_stack(baseHeight - 2, var2Type)); // push var2;
				push(load_stack(baseHeight - 0, var4Type)); // push var4;
			} else {
				//64x32
				// prestack: var2, var1, var0 (height = 4)
				// poststack: var4, var3, var2, var0
				// assignments: var0 = var2(initial)
				// assignments: var2 = var0(initial)
				// assignments: var3 = var1(initial)
				// assignments: var4 = var2(initial)
				currentStack.assertHeights(DUP2_X2_64x32_HEIGHTS);

				Expression var2 = pop();
				Expression var1 = pop();
				Expression var0 = pop();

				Type var6Type = assign_stack(baseHeight + 2, var0); // var6 = var0(initial)

				Type var0Type = assign_stack(baseHeight - 4, var2); // var0 = var2
				Type var3Type = assign_stack(baseHeight - 1, var1); // var3 = var1
				Type var4Type = assign_stack(baseHeight + 0, var2); // var4 = var2
				Type var2Type = assign_stack(baseHeight - 2, load_stack(baseHeight + 2, var6Type)); // var2 = var0

				push(load_stack(baseHeight - 4, var0Type)); // push var0
				push(load_stack(baseHeight - 2, var2Type)); // push var2
				push(load_stack(baseHeight - 1, var3Type)); // push var3
				push(load_stack(baseHeight + 0, var4Type)); // push var4
			}
		} else {
			Type bottomType = currentStack.peek(2).getType();
			if (bottomType.getSize() == 2) {
				// 32x64
				// prestack: var3, var2, var0 (height = 4)
				// poststack: var5, var4, var2, var1, var0
				// assignments: var0 = var2(initial)
				// assignments: var1 = var3(initial)
				// assignments: var2 = var0(initial)
				// assignments: var4 = var2(initial)
				// assignments: var5 = var3(initial)
				currentStack.assertHeights(DUP2_X2_32x64_HEIGHTS);

				Expression var3 = pop();
				Expression var2 = pop();
				Expression var0 = pop();

				Type var6Type = assign_stack(baseHeight + 2, var0); // var6 = var0(initial)

				Type var0Type = assign_stack(baseHeight - 4, var2); // var0 = var2(initial)
				Type var1Type = assign_stack(baseHeight - 3, var3); // var1 = var3(initial)
				Type var4Type = assign_stack(baseHeight + 0, var2); // var4 = var2(initial)
				Type var5Type = assign_stack(baseHeight + 1, var3); // var5 = var3(initial)
				Type var2Type = assign_stack(baseHeight - 2, load_stack(baseHeight + 2, var6Type)); // var2 = var6 = var0(initial)

				push(load_stack(baseHeight - 4, var0Type)); // push var0
				push(load_stack(baseHeight - 3, var1Type)); // push var1
				push(load_stack(baseHeight - 2, var2Type)); // push var2
				push(load_stack(baseHeight + 0, var4Type)); // push var4
				push(load_stack(baseHeight + 1, var5Type)); // push var5
			} else {
				// 32x32
				// prestack: var3, var2, var1, var0 (height = 4)
				// poststack: var5, var4, var3, var2, var1, var0
				// var0 = var2
				// var1 = var3
				// var2 = var0
				// var3 = var1
				// var4 = var2
				// var5 = var3
				currentStack.assertHeights(DUP2_X2_32x32_HEIGHTS);

				Expression var3 = pop();
				Expression var2 = pop();
				Expression var1 = pop();
				Expression var0 = pop();

				Type var6Type = assign_stack(baseHeight + 2, var0); // var6 = var0(initial)
				Type var7Type = assign_stack(baseHeight + 3, var1); // var7 = var1(initial)

				Type var0Type = assign_stack(baseHeight - 4, var2); // var0 = var2(initial)
				Type var1Type = assign_stack(baseHeight - 3, var3); // var1 = var3(initial)
				Type var4Type = assign_stack(baseHeight + 0, var2); // var4 = var2(initial)
				Type var5Type = assign_stack(baseHeight + 1, var3); // var5 = var3(initial)
				Type var2Type = assign_stack(baseHeight - 2, load_stack(baseHeight + 2, var6Type)); // var2 = var6 = var0(initial)
				Type var3Type = assign_stack(baseHeight - 1, load_stack(baseHeight + 3, var7Type)); // var3 = var7 = var1(initial)

				push(load_stack(baseHeight - 4, var0Type)); // push var0
				push(load_stack(baseHeight - 3, var1Type)); // push var1
				push(load_stack(baseHeight - 2, var2Type)); // push var2
				push(load_stack(baseHeight - 1, var3Type)); // push var3
				push(load_stack(baseHeight + 0, var4Type)); // push var4
				push(load_stack(baseHeight + 1, var5Type)); // push var5
			}
		}
	}
	
	void _swap() {
		// prestack: var1, var0 (height = 2)
		// poststack: var1, var0
		// assignments: var0 = var1 (initial)
		// assignments: var1 = var0 (initial)

		currentStack.assertHeights(SWAP_HEIGHTS);
		int baseHeight = currentStack.height();

		Expression var1 = pop();
		Expression var0 = pop();

		Type var2Type = assign_stack(baseHeight + 0, var0); // var2 = var0
		Type var3Type = assign_stack(baseHeight + 1, var1); // var3 = var1

		Type var0Type = assign_stack(baseHeight - 2, load_stack(baseHeight + 1, var3Type)); // var0 = var3 = var1(initial)
		Type var1Type = assign_stack(baseHeight - 1, load_stack(baseHeight + 0, var2Type)); // var1 = var2 = var0(initial)

		push(load_stack(baseHeight - 2, var0Type)); // push var0
		push(load_stack(baseHeight - 1, var1Type)); // push var1
	}
	
	void _cast(Type type) {
		Expression e = new CastExpression(pop(), type);
		int index = currentStack.height();
		assign_stack(index, e);
		push(load_stack(index, type));
	}
	
	void _instanceof(Type type) {
		InstanceofExpression e = new InstanceofExpression(pop(), type);
		int index = currentStack.height();
		assign_stack(index, e);
		push(load_stack(index, type));
	}
	
	void _new(Type type) {
		int index = currentStack.height();
		UninitialisedObjectExpression e = new UninitialisedObjectExpression(type);
		assign_stack(index, e);
		push(load_stack(index, type));
	}
	
	void _new_array(Expression[] bounds, Type type) {
		int index = currentStack.height();
		NewArrayExpression e = new NewArrayExpression(bounds, type);
		assign_stack(index, e);
		push(load_stack(index, type));
	}
	
	void _call(int op, String owner, String name, String desc) {
		int argLen = Type.getArgumentTypes(desc).length + (op == INVOKESTATIC ? 0 : 1);
		Expression[] args = new Expression[argLen];
		for (int i = args.length - 1; i >= 0; i--) {
			args[i] = pop();
		}
		InvocationExpression callExpr = new InvocationExpression(op, args, owner, name, desc);
		if(callExpr.getType() == Type.VOID_TYPE) {
			addStmt(new PopStatement(callExpr));
		} else {
			int index = currentStack.height();
			Type type = assign_stack(index, callExpr);
			push(load_stack(index, type));
		}
	}
	
	void _switch(LinkedHashMap<Integer, BasicBlock> targets, BasicBlock dflt) {
		LinkedHashMap<Integer, HeaderStatement> targets2 = new LinkedHashMap<>();
		
		Expression expr = pop();
		for (Entry<Integer, BasicBlock> e : targets.entrySet()) {
			update_target_stack(currentBlock, e.getValue(), currentStack);
			targets2.put(e.getKey(), headers.get(e.getValue()));
		}
		
		update_target_stack(currentBlock, dflt, currentStack);
		
		addStmt(new SwitchStatement(expr, targets2, headers.get(dflt)));
	}

	void _store_field(int opcode, String owner, String name, String desc) {
		if(opcode == PUTFIELD) {
			Expression val = pop();
			Expression inst = pop();
			addStmt(new FieldStoreStatement(inst, val, owner, name, desc));
		} else if(opcode == PUTSTATIC) {
			Expression val = pop();
			addStmt(new FieldStoreStatement(null, val, owner, name, desc));
		} else {
			throw new UnsupportedOperationException(Printer.OPCODES[opcode] + " " + owner + "." + name + "   " + desc);
		}
	}
	
	void _load_field(int opcode, String owner, String name, String desc) {
		if(opcode == GETFIELD || opcode == GETSTATIC) {
			Expression inst = null;
			if(opcode == GETFIELD) {
				inst = pop();
			}
			FieldLoadExpression fExpr = new FieldLoadExpression(inst, owner, name, desc);
			int index = currentStack.height();
			Type type = assign_stack(index, fExpr);
			push(load_stack(index, type));
		} else {
			throw new UnsupportedOperationException(Printer.OPCODES[opcode] + " " + owner + "." + name + "   " + desc);
		}
	}
	
	void _store(int index, Type type) {
		Expression expr = pop();
//		VarExpression var = new VarExpression(index, type, false);
//		VarExpression var = root.getLocals().get(index);
		VarExpression var = _var_expr(index, type, false);
		addStmt(new CopyVarStatement(var, expr));
	}

	void _load(int index, Type type) {
		VarExpression e = _var_expr(index, type, false);
		assign_stack(currentStack.height(), e);
		push(e);
//		System.out.printf("   Visiting load var%d, height=%d.%n", index, currentStack.height());
//		VarExpression e1 = createVarExpression(index, type, false);
//		assign_stack(currentStack.height(), e1);
//		push(load_stack(index, type));
//		System.out.printf("   After pushing loadexpr, height=%d.%n", currentStack.height());
//		
//		System.out.printf("   After assign_stack, height=%d.%n", currentStack.height());
//		System.out.println("    Added stmt: " + getLastStatement(currentBlock));
	}

	void _inc(int index, int amt) {
		VarExpression load = _var_expr(index, Type.INT_TYPE, false);
		ArithmeticExpression inc = new ArithmeticExpression(new ConstantExpression(amt), load, Operator.ADD);
		VarExpression var = _var_expr(index, Type.INT_TYPE, false);
		addStmt(new CopyVarStatement(var, inc));
	}
	
	VarExpression _var_expr(int index, Type type, boolean isStack) {
		return new VarExpression(root.getLocals().get(index, isStack), type);
	}
}