package org.rsdeob.stdlib.cfg;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.cfg.FlowEdge.TryCatchEdge;
import org.rsdeob.stdlib.cfg.expr.*;
import org.rsdeob.stdlib.cfg.expr.ArithmeticExpression.Operator;
import org.rsdeob.stdlib.cfg.expr.ComparisonExpression.ValueComparisonType;
import org.rsdeob.stdlib.cfg.expr.var.FieldStoreExpression;
import org.rsdeob.stdlib.cfg.stat.*;
import org.rsdeob.stdlib.cfg.stat.ConditionalJumpStatement.ComparisonType;
import org.rsdeob.stdlib.cfg.stat.MonitorStatement.MonitorMode;
import org.rsdeob.stdlib.cfg.util.ExpressionStack;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.cfg.util.TypeUtils.ArrayType;

public class StatementGenerator implements Opcodes {

	private static final int[] EMPTY_STACK_HEIGHTS = new int[]{};
	private static final int[] SINGLE_RETURN_HEIGHTS = new int[]{1};
	private static final int[] DOUBLE_RETURN_HEIGHTS = new int[]{2};
	
	private static final int[] DUP_HEIGHTS = new int[]{1};
	private static final int[] DUP_X1_HEIGHTS = new int[]{1, 1};
	private static final int[] DUP2_SINGLE_HEIGHTS = new int[]{1, 1};
	
	MethodNode m;
	ControlFlowGraph graph;
	Set<BasicBlock> updatedStacks;
	Set<BasicBlock> analysedBlocks;
	LinkedList<BasicBlock> queue;
	RootStatement root;
	VarVersionsMap variables;
	int stackBase;

	transient volatile BasicBlock currentBlock;
	transient volatile ExpressionStack currentStack;

	public StatementGenerator(ControlFlowGraph cfg) {
		graph = cfg;
		m = cfg.getMethod();
		updatedStacks = new HashSet<>();
		analysedBlocks = new HashSet<>();
		queue = new LinkedList<>();
	}
	
	public void init(int base) {
		stackBase = base;
		variables = new VarVersionsMap(graph);
		root = new RootStatement(m, variables);
		
		queueEntryBlocks();
	}

	public RootStatement buildRoot() {
		for (BasicBlock b : graph.blocks()) {
			BlockHeaderStatement bstmt = new BlockHeaderStatement(b);
			root.write(bstmt);
			root.getBlockStatements().put(b, bstmt);
			for (Statement n : b.getStatements()) {
				root.write(n);
			}
		}
		graph.setRoot(root);
		return root;
	}

	void addStmt(Statement stmt) {
		currentBlock.getStatements().add(stmt);
	}

	Expression pop() {
		return currentStack.pop1();
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
				if (b.getImmediate() != null) {
					queue.addFirst(b.getImmediate());
				}

				for (FlowEdge _succ : b.getSuccessors()) {
					if (!(_succ instanceof TryCatchEdge)) {
						BasicBlock succ = _succ.dst;
						updateTargetStack(b, succ, stack);
					}
				}
			}
		}
	}

	Statement getLastStatement(BasicBlock b) {
		return b.getStatements().get(b.getStatements().size() - 1);
	}

	void updateTargetStack(BasicBlock b, BasicBlock target, ExpressionStack stack) {
		// called just before a jump to a successor block may
		// happen. any operations, such as comparisons, that
		// happen before the jump are expected to have already
		// popped the left and right arguments from the stack before
		// checking the merge state.
		if (!updatedStacks.contains(target)) {
			// unfinalised block found.
			target.setInputStack(stack.copy());
			updatedStacks.add(target);

			queue.addLast(target);
		} else if (!canSucceed(target.getInputStack(), stack)) {
			// if the targets input stack is finalised and
			// the new stack cannot merge into it, then there
			// is an error in the bytecode (verifier error).
			System.out.println("Current: " + stack + " in " + b.getId());
			System.out.println("Target : " + target.getInputStack() + " in " + target.getId());
			throw new IllegalStateException("Stack coherency mismatch into #" + target.getId());
		}
	}

	boolean canSucceed(ExpressionStack s, ExpressionStack succ) {
		// quick check stack heights
		if (s.height() != succ.height()) {
			return false;
		}
		ExpressionStack c0 = s.copy();
		ExpressionStack c1 = succ.copy();
		while (c0.height() > 0) {
			Expression e1 = c0.pop1();
			Expression e2 = c1.pop1();
			if (!(e1 instanceof StackLoadExpression) || !(e2 instanceof StackLoadExpression)) {
				return false;
			}
			if (((StackLoadExpression) e1).getIndex() != ((StackLoadExpression) e2).getIndex()) {
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

		_entry(graph.getEntry());

		for (TryCatchBlockNode tc : m.tryCatchBlocks) {
			_catches(tc);
		}
	}

	ExpressionStack process(BasicBlock b) {
		 System.out.println("Processing " + b.getId());
		updatedStacks.add(b);
		ExpressionStack stack = b.getInputStack().copy();

		currentBlock = b;
		currentStack = stack;

		for (AbstractInsnNode ain : b.getInsns()) {
			int opcode = ain.opcode();
			if(opcode != -1) {
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
						new Expression[] { stack.pop1() }, 
						TypeUtils.getPrimitiveArrayType(((IntInsnNode) ain).operand)
					);
					break;
				}
				case ANEWARRAY: {
					_new_array(
						new Expression[] { stack.pop1() }, 
						Type.getType("[L" + ((TypeInsnNode) ain).desc + ";")
					);
					break;
				}
				case MULTIANEWARRAY: {
					MultiANewArrayInsnNode in = (MultiANewArrayInsnNode) ain;
					Expression[] bounds = new Expression[in.dims];
					for (int i = in.dims - 1; i >= 0; i--) {
						bounds[i] = stack.pop1();
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
			
			 System.out.println(" Poststack: " + stack);
			 /*System.out.println(" Block stmts: ");
			 for(Statement stmt : b.getStatements()) {
				 System.out.println("   " + stmt);
			 }
			 System.out.println();*/
		}

		return stack;
	}
	
	Type assign_stack(Expression expr, int index) {
		Type type = TypeUtils.asSimpleType(expr.getType());
		// var_x := expr;
		StackDumpStatement stmt = new StackDumpStatement(expr, index, type, true);
		addStmt(stmt);
		return type;
	}
	
	Expression load_stack(int index, Type type) {
		return new StackLoadExpression(index, type, true);
	}

	void _post_jump() {
		// TODO: 
	}
	
	void _jump_compare(BasicBlock target, ComparisonType type, Expression left, Expression right) {
		updateTargetStack(currentBlock, target, currentStack);
		addStmt(new ConditionalJumpStatement(left, right, target, type));
		_post_jump();
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
		updateTargetStack(currentBlock, target, currentStack);
		addStmt(new UnconditionalJumpStatement(target));
		_post_jump();
	}

	void _entry(BasicBlock entry) {
		entry.setInputStack(new ExpressionStack(1024 * 8));
		queue.add(entry);
		updatedStacks.add(entry);
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
		Type type = assign_stack(e, index);
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
		Expression index = pop();
		Expression array = pop();
		push(new ArrayLoadExpression(array, index, type));
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
	
	// TODO: verify dup and dup2
	void _dup() {
		currentStack.assertHeights(DUP_HEIGHTS);
		int index = currentStack.height() + 1;
		Type type = assign_stack(pop(), index);
		push(load_stack(index, type));
		push(load_stack(index, type));
	}

	void _dup2() {
		Type topType = peek().getType();
		
		if(topType.getSize() == 2) {
			// TODO: Currently untested
			
			// [x:2, y, z] -> [x:2, x:2, y, z]
			// [var2, var1, var0] -> [var3, var3, var1, var0]
			//  statements:
			//       var3 = var2
			int index = currentStack.height() + 1;
			assign_stack(pop(), index);
			push(load_stack(index, topType)); // copy
			push(load_stack(index, topType)); // original
		} else {
			currentStack.assertHeights(DUP2_SINGLE_HEIGHTS);
			// [x:1, y:1, z] -> [x:1, y:1, x:1, y:1]
			// [var2, var1, var0] -> [var4, var3, var4, var3, var0]
			//  statements:
			//       var3 = var1
			//       var4 = var2
			int xIndex = currentStack.height() +2; // var2
			int yIndex = currentStack.height() +1; // var1
			Expression x = pop();
			Expression y = pop();
			// swap creation order
			Type yType = assign_stack(y, yIndex);
			Type xType = assign_stack(x, xIndex);
			push(load_stack(yIndex, yType)); // y copy
			push(load_stack(xIndex, xType)); // x copy
			push(load_stack(yIndex, yType)); // y original
			push(load_stack(xIndex, xType)); // x original
		}
	}

	void _dup_x1() {
		currentStack.assertHeights(DUP_X1_HEIGHTS);
		// [x, y] -> [x, y, x]
		// [var1, var0] -> [var2, var1, var0]
		//  statements:
		//       var2 = var1
		//       var1 = var0
		//       var0 = var2
		int xIndex  = currentStack.height() - 0;
		int yIndex  = currentStack.height() - 1;
		int x2Index = currentStack.height() - 2;
		Expression x = pop();
		Type xType = assign_stack(x, xIndex);
		Type yType = assign_stack(pop(), yIndex);
		x = load_stack(xIndex, xType);
		assign_stack(x, x2Index);
		
		push(load_stack(x2Index, xType));
		push(load_stack(yIndex, yType));
		push(x);
	}

	void _dup_x2() {
		boolean _64 = currentStack.peek(1).getType().getSize() == 2;
		int xIndex  = currentStack.height() - 0;
		Expression x = pop();
		Type xType = assign_stack(x, xIndex);
		if(_64) {
			// [x, {y,z}] -> [x, {y,z}, x]
			int yzIndex = currentStack.height();
			int x2Index = currentStack.height() - 1;
			Type yzType = assign_stack(pop(), yzIndex);
			x = load_stack(xIndex, xType);
			assign_stack(x, x2Index);
			
			push(load_stack(x2Index, xType));
			push(load_stack(yzIndex, yzType));
			push(x);
		} else {
			// [x, y, z] -> [x, y, z, x]
			int yIndex  = currentStack.height();
			int zIndex  = currentStack.height() - 1;
			int x2Index = currentStack.height() - 2;
			Type yType = assign_stack(pop(), yIndex);
			Type zType = assign_stack(pop(), zIndex);
			x = load_stack(xIndex, xType);
			assign_stack(x, x2Index);
			
			push(load_stack(x2Index, xType));
			push(load_stack(zIndex, zType));
			push(load_stack(yIndex, yType));
			push(x);
		}
	}

	void _dup2_x1() {
		throw new UnsupportedOperationException();
	}

	void _dup2_x2() {
		throw new UnsupportedOperationException();
	}
	
	void _swap() {
		throw new UnsupportedOperationException();
	}
	
	void _cast(Type type) {
		Expression e = new CastExpression(pop(), type);
		int index = currentStack.height();
		assign_stack(e, index);
		push(load_stack(index, type));
	}
	
	void _instanceof(Type type) {
		InstanceofExpression e = new InstanceofExpression(pop(), type);
		int index = currentStack.height();
		assign_stack(e, index);
		push(load_stack(index, type));
	}
	
	void _new(Type type) {
		int index = currentStack.height() + 1;
		UninitialisedObjectExpression e = new UninitialisedObjectExpression(type);
		assign_stack(e, index);
		push(load_stack(index, type));
	}
	
	void _new_array(Expression[] bounds, Type type) {
		int index = currentStack.height() + 1;
		NewArrayExpression e = new NewArrayExpression(bounds, type);
		assign_stack(e, index);
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
			Type type = assign_stack(callExpr, index);
			push(load_stack(index, type));
		}
	}
	
	void _switch(LinkedHashMap<Integer, BasicBlock> targets, BasicBlock dflt) {
		Expression expr = pop();
		for(Entry<Integer, BasicBlock> e : targets.entrySet()) {
			updateTargetStack(currentBlock, e.getValue(), currentStack);
		}
		updateTargetStack(currentBlock, dflt, currentStack);
		addStmt(new SwitchStatement(expr, targets, dflt));
	}

	void _store_field(int opcode, String owner, String name, String desc) {
		if(opcode == PUTFIELD) {
			Expression val = pop();
			Expression inst = pop();
			addStmt(new FieldStoreExpression(inst, val, owner, name, desc));
		} else if(opcode == PUTSTATIC) {
			Expression val = pop();
			addStmt(new FieldStoreExpression(null, val, owner, name, desc));
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
			Type type = assign_stack(fExpr, index);
			push(load_stack(index, type));
		} else {
			throw new UnsupportedOperationException(Printer.OPCODES[opcode] + " " + owner + "." + name + "   " + desc);
		}
	}
	
	void _store(int index, Type type) {
		Expression expr = pop();
		addStmt(new StackDumpStatement(expr, index, type));
	}
	
	void _load(int index, Type type) {
		StackLoadExpression e = new StackLoadExpression(index, type);
		push(e);
		assign_stack(e, currentStack.height());
	}
	
	void _inc(int index, int amt) {
		StackLoadExpression load = new StackLoadExpression(index, Type.INT_TYPE);
		ArithmeticExpression inc = new ArithmeticExpression(new ConstantExpression(amt), load, Operator.ADD);
		addStmt(new StackDumpStatement(inc, index, Type.INT_TYPE));
	}
}