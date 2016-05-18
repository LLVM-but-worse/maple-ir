package org.rsdeob.stdlib.cfg;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.cfg.FlowEdge.TryCatchEdge;
import org.rsdeob.stdlib.cfg.expr.ArithmeticExpression;
import org.rsdeob.stdlib.cfg.expr.ArithmeticExpression.Operator;
import org.rsdeob.stdlib.cfg.expr.ArrayLengthExpression;
import org.rsdeob.stdlib.cfg.expr.ArrayLoadExpression;
import org.rsdeob.stdlib.cfg.expr.CastExpression;
import org.rsdeob.stdlib.cfg.expr.CaughtExceptionExpression;
import org.rsdeob.stdlib.cfg.expr.ComparisonExpression;
import org.rsdeob.stdlib.cfg.expr.ComparisonExpression.ValueComparisonType;
import org.rsdeob.stdlib.cfg.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.expr.Expression;
import org.rsdeob.stdlib.cfg.expr.FieldLoadExpression;
import org.rsdeob.stdlib.cfg.expr.InstanceofExpression;
import org.rsdeob.stdlib.cfg.expr.InvocationExpression;
import org.rsdeob.stdlib.cfg.expr.NegationExpression;
import org.rsdeob.stdlib.cfg.expr.NewArrayExpression;
import org.rsdeob.stdlib.cfg.expr.StackLoadExpression;
import org.rsdeob.stdlib.cfg.expr.UninitialisedObjectExpression;
import org.rsdeob.stdlib.cfg.expr.var.FieldStoreExpression;
import org.rsdeob.stdlib.cfg.stat.ArrayStoreStatement;
import org.rsdeob.stdlib.cfg.stat.BlockHeaderStatement;
import org.rsdeob.stdlib.cfg.stat.ConditionalJumpStatement;
import org.rsdeob.stdlib.cfg.stat.ConditionalJumpStatement.ComparisonType;
import org.rsdeob.stdlib.cfg.stat.MonitorStatement;
import org.rsdeob.stdlib.cfg.stat.MonitorStatement.MonitorMode;
import org.rsdeob.stdlib.cfg.stat.PopStatement;
import org.rsdeob.stdlib.cfg.stat.ReturnStatement;
import org.rsdeob.stdlib.cfg.stat.StackDumpStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.stat.SwitchStatement;
import org.rsdeob.stdlib.cfg.stat.ThrowStatement;
import org.rsdeob.stdlib.cfg.stat.UnconditionalJumpStatement;
import org.rsdeob.stdlib.cfg.util.ExpressionStack;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.cfg.util.TypeUtils.ArrayType;

public class StatementGenerator implements Opcodes {

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

	
	public static void assertTypeLen(Expression expr, int size) {
		if(expr.getType().getSize() != size) {
			throw new IllegalArgumentException(expr + " of length " + expr.getType().getSize() + " with expected size of " + size + ".");
		}
	}
	
	public void assertStackSize(int size) {
		if(currentStack.size() != size) {
			throw new IllegalArgumentException(currentStack.size() + " mismatches with expected " + size + ".");
		}
	}
	
	public void createExpressions() {
		while (queue.size() > 0) {
			BasicBlock b = queue.removeFirst();
			if (!analysedBlocks.contains(b)) {
				analysedBlocks.add(b);

				ExpressionStack stack = process(b);

				// check merge exit stack with next input stack
				if (b.getImmediate() != null) {
					// FIXME: alloc or create vars?
					createStackVariables(b, stack);
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

	void createStackVariables(BasicBlock block, ExpressionStack stack) {
		createStackVariables(block, stack, stack.size());
	}
	
	int getNextStackIndex(Set<Integer> contained, int stackIndex) {
		while(contained.contains(stackIndex)) {
			stackIndex--;
		}
		return stackIndex;
	}
	
	void createStackVariables(BasicBlock block, ExpressionStack stack, int depth) {
		// stack(t->b) = [x, y, z]
		// exprs (2)   = [x, y]
		// stack(t->b) = [z], so we then have to push expressions starting from y, then x.
		int stackIndex = stackBase;
		Expression[] exprs = new Expression[depth];
		// collect top (n=depth) items.
//		for(int i=0; i < exprs.length; i++) {
		
		// first collect vars that are on the stack after the
		// depth target.
		Set<Integer> contained = new HashSet<>();
		for(int i=0; i < (stack.size() - depth/*remaining vars*/); i++) {
			Expression expr = stack.peek(i + depth);
			if(expr instanceof StackLoadExpression) {
				contained.add(((StackLoadExpression) expr).getIndex());
			}
		}
		
		// System.out.println("Contained: " + contained);

		stackIndex += stack.size();
		for (int i = exprs.length - 1; i >= 0; i--) {
			exprs[i] = stack.pop1();
			// stackIndex += exprs[i].getType().getSize();
		}

		for(int i=0; i < exprs.length; i++) {
//		for (int i = exprs.length - 1; i >= 0; i--) {
			Expression e = exprs[i];
			Type type = TypeUtils.asSimpleType(e.getType());

			stackIndex = getNextStackIndex(contained, stackIndex);
			
			if(e instanceof StackLoadExpression) {
				if(((StackLoadExpression) e).getIndex() == stackIndex) {
					stack.push(e);
					// stackIndex -= e.getType().getSize();
					stackIndex--;
					continue;
				}
			}
			
			block.getStatements().add(new StackDumpStatement(e, stackIndex, type));
			stack.push(new StackLoadExpression(stackIndex, type, true));
			// System.out.println("  Creating save couple: " + block.getStatements().get(block.getStatements().size() - 1));
//			stackIndex -= e.getType().getSize();

			stackIndex--;
		}
		
		// System.out.println("   Couplestack: " + stack + " for " + depth + " items.");
	}

	void allocStack(BasicBlock block, ExpressionStack stack, int depth, int startdepth) {
		// layout of exprs:
		// +- 0 (top of stack)
		// |  1
		// |  2
		// | ...
		// +- startdepth
		// |  startdepth + 1
		// |  startdepth + 2
		// |  ...
		// +- startdepth + depth - 1 (bottom of stack)

		int stackIndex = stackBase;

		// Populate exprs and empty relevant parts of the stack
		Expression[] exprs = new Expression[depth + startdepth];
		for (int i = 0; i < exprs.length; i++) {
			exprs[i] = stack.pop1();
			stackIndex += exprs[i].getType().getSize();
		}

		// Repopulate stack
		for (int i = exprs.length - 1; i >= 0; i--) {
			Expression e = exprs[i];
			Type type = TypeUtils.asSimpleType(e.getType());

			boolean skip = i < startdepth; // Ignore stack above startdepth; just push it back on
			skip = skip || e instanceof StackLoadExpression && ((StackLoadExpression) e).getIndex() == stackIndex; // Ignore identical stack variables
			if(skip) {
				stack.push(e);
				stackIndex -= e.getType().getSize();
				continue;
			}

			// Allocate new variable
			block.getStatements().add(new StackDumpStatement(e, stackIndex, type));
			stack.push(new StackLoadExpression(stackIndex, type, true));
			// System.out.println("  Creating save couple: " + block.getStatements().get(block.getStatements().size() - 1));
			stackIndex -= e.getType().getSize();
		}

		// System.out.println("   Couplestack: " + stack);
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
			throw new IllegalStateException("Stack coherency mistmatch into #" + target.getId());
		}
	}

	boolean canSucceed(ExpressionStack s, ExpressionStack succ) {
		// quick check stack heights
		if (s.size() != succ.size()) {
			return false;
		}
		ExpressionStack c0 = s.copy();
		ExpressionStack c1 = succ.copy();
		while (c0.size() > 0) {
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
		// System.out.println("Processing " + b.getId());
		updatedStacks.add(b);
		ExpressionStack stack = b.getInputStack().copy();

		currentBlock = b;
		currentStack = stack;

		for (AbstractInsnNode ain : b.getInsns()) {
			int opcode = ain.opcode();
			if(opcode != -1) {
				// System.out.println("Executing " + Printer.OPCODES[ain.opcode()]);
				// System.out.println(" Prestack : " + stack);
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
					// System.out.println(currentStack.toTypeString());
					_dup();
					break;
				case DUP_X1:
					// System.out.println(currentStack.toTypeString());
					_dup_x1();
					break;
				case DUP_X2:
					// System.out.println(currentStack.toTypeString());
					_dup_x2();
					break;

				case DUP2:
					// System.out.println(currentStack.toTypeString());
					_dup2();
					break;
				case DUP2_X1:
					// System.out.println(currentStack.toTypeString());
					_dup2_x1();
					break;
				case DUP2_X2:
					// System.out.println(currentStack.toTypeString());
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
			
			// System.out.println(" Poststack: " + stack);
			// System.out.println();
		}

		return stack;
	}

	void _jump_compare(BasicBlock target, ComparisonType type, Expression left, Expression right) {
		updateTargetStack(currentBlock, target, currentStack);
		addStmt(new ConditionalJumpStatement(left, right, target, type));
	}
	
	void _jump_compare(BasicBlock target, ComparisonType type) {
		if(currentStack.size() > 2) {
			createStackVariables(currentBlock, currentStack);
		}
		Expression right = pop();
		Expression left = pop();
		_jump_compare(target, type, left, right);
	}
	
	void _jump_cmp0(BasicBlock target, ComparisonType type) {
		if(currentStack.size() > 1) {
			createStackVariables(currentBlock, currentStack);
		}
		Expression left = pop();
		ConstantExpression right = new ConstantExpression(0);
		_jump_compare(target, type, left, right);
	}

	void _jump_null(BasicBlock target, boolean invert) {
		if(currentStack.size() > 1) {
			createStackVariables(currentBlock, currentStack);
		}
		Expression left = pop();
		ConstantExpression right = new ConstantExpression(null);
		ComparisonType type = invert ? ComparisonType.NE : ComparisonType.EQ;
		
		_jump_compare(target, type, left, right);
	}

	void _jump_uncond(BasicBlock target) {
		createStackVariables(currentBlock, currentStack);
		updateTargetStack(currentBlock, target, currentStack);
		addStmt(new UnconditionalJumpStatement(target));
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
		push(new ConstantExpression(o));
	}

	void _compare(ValueComparisonType ctype) {
		Expression right = pop();
		Expression left = pop();
		push(new ComparisonExpression(left, right, ctype));
	}

	void _return(Type type) {
		if (type == Type.VOID_TYPE) {
			assertStackSize(0);
			addStmt(new ReturnStatement());
		} else {
			addStmt(new ReturnStatement(type, pop()));
		}
	}

	void _throw() {
		assertStackSize(1);
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
		// if(currentStack.size() > amt) {
		// 	createStackVariables(currentBlock, currentStack);
		//}
		
		while(amt > 0) {
			Expression expr = pop();
			addStmt(new PopStatement(expr));
			amt -= expr.getType().getSize();
		}
		if(amt < 0) {
			throw new UnsupportedOperationException("invalid pop lengths.");
		}
	}
	
	void _dup() {
		assertTypeLen(peek().copy(), 1);
		push(peek().copy());
		allocStack(currentBlock, currentStack, 1, 1);
	}

	void _dup2() {
		Expression first = pop();
		
		if(first.getType().getSize() == 2) {
			push(first);
			push(first.copy());
		} else {
			Expression second = pop();
			push(second);
			push(first);
			push(second.copy());
			push(first.copy());
		}

		// FIXME: stack alloc
		allocStack(currentBlock, currentStack, 2, 2);
	}

	void _dup_x1() {
		Expression expr2 = pop();
		assertTypeLen(expr2, 1);
		Expression expr1 = pop();
		assertTypeLen(expr1, 1);
		push(expr2.copy());
		push(expr1);
		push(expr2);
		allocStack(currentBlock, currentStack, 1, 2);
	}

	void _dup_x2() {
		// [a, b, c] -> [a, b, c, a]
		Expression first = pop();
		assertTypeLen(first, 1);
		Expression second = pop();
		
		if(second.getType().getSize() == 2) {
			// second = {b, c}
			push(first.copy());
			push(second);
			push(first);
			// FIXME: stack alloc
		} else {
			// second = {b}
			Expression third = pop(); // {c}
			push(first.copy());
			push(third);
			push(second);
			push(first);
			// FIXME: stack alloc
			allocStack(currentBlock, currentStack, 1, 3);
		}
	}

	void _dup2_x1() {
		Expression first = pop();
		
		if(first.getType().getSize() == 2) {
			Expression second = pop();
			push(second.copy());
			push(first.copy());
			push(second);
			push(first);
			// FIXME: stack alloc
		} else {
			Expression second = pop();
			Expression third = pop();
			push(second.copy());
			push(first.copy());
			push(third);
			push(second);
			push(first);
			allocStack(currentBlock, currentStack, 2, 3);
		}
	}

	void _dup2_x2() {
		// [a, b, c, d] -> [a, b, c, d, a, b]
		Expression first = pop();
		
		if(first.getType().getSize() == 2) {
			// first = {a, b}
			Expression second = pop();
			if(second.getType().getSize() == 2) {
				// second = {c, d}
				push(first.copy());
				push(second);
				push(first);
				// FIXME: stack alloc
			} else {
				// second = {c}
				Expression third = pop(); // {d}
				push(first.copy());
				push(third);
				push(second);
				push(first);
				// FIXME: stack alloc
			}
		} else {
			// first = {a}
			Expression second = pop(); // {b}
			// if the first len is 1, then the second
			// must also be 1.
			assertTypeLen(second, 1);
			Expression third = pop();
			if(third.getType().getSize() == 2) {
				// {a} {b} {c,d}
				// i.e. {c, d} are one type
				push(second.copy()); // [b]
				push(first.copy());  // [a, b]
				push(third);         // [c, d, a, b]
				push(second);        // [b, c, d, a, b]
				push(first);         // [a, b, {c, d}, a, b]
				// FIXME: stack alloc
			} else {
				// {a} {b} {c} {d}
				Expression fourth = pop();
				
				push(second.copy()); // [b]
				push(first.copy());  // [a, b]
				push(fourth);        // [d, a, b]
				push(third);         // [c, d, a, b]
				push(second);        // [b, c, d, a, b]
				push(first);         // [a, b, c, d, a, b]
				allocStack(currentBlock, currentStack, 4, 4);
			}
		}
	}
	
	void _swap() {
		// FIXME:
		createStackVariables(currentBlock, currentStack);
		Expression expr2 = pop();
		Expression expr1 = pop();
		push(expr2);
		push(expr1);
	}
	
	void _cast(Type type) {
		push(new CastExpression(pop(), type));
	}
	
	void _instanceof(Type type) {
		push(new InstanceofExpression(pop(), type));
	}
	
	void _new(Type type) {
		push(new UninitialisedObjectExpression(type));
	}
	
	void _new_array(Expression[] bounds, Type type) {
		push(new NewArrayExpression(bounds, type));
	}
	
	void _call(int op, String owner, String name, String desc) {
		int argLen = Type.getArgumentTypes(desc).length + (op == INVOKESTATIC ? 0 : 1);
		Expression[] args = new Expression[argLen];
		for (int i = args.length - 1; i >= 0; i--) {
			args[i] = pop();
		}
		InvocationExpression callExpr = new InvocationExpression(op, args, owner, name, desc);
		if(callExpr.getType() == Type.VOID_TYPE) {
//			createStackVariables(currentBlock, currentStack);
			addStmt(new PopStatement(callExpr));
		} else {
			push(callExpr);
		}
	}
	
	void _switch(LinkedHashMap<Integer, BasicBlock> targets, BasicBlock dflt) {
		if(currentStack.size() > 1) {
			createStackVariables(currentBlock, currentStack);
		}
		Expression expr = pop();
		for(Entry<Integer, BasicBlock> e : targets.entrySet()) {
			updateTargetStack(currentBlock, e.getValue(), currentStack);
		}
		updateTargetStack(currentBlock, dflt, currentStack);
		addStmt(new SwitchStatement(expr, targets, dflt));
	}

	void _store_field(int opcode, String owner, String name, String desc) {
		if(opcode == PUTFIELD) {
			if(currentStack.size() > 2) {
				//createStackVariables(currentBlock, currentStack);
			}
			
			Expression val = pop();
			Expression inst = pop();
			addStmt(new FieldStoreExpression(inst, val, owner, name, desc));
		} else if(opcode == PUTSTATIC) {
			if(currentStack.size() > 1) {
				//createStackVariables(currentBlock, currentStack);
			}
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
			push(fExpr);
		} else {
			throw new UnsupportedOperationException(Printer.OPCODES[opcode] + " " + owner + "." + name + "   " + desc);
		}
	}
	
	void _store(int index, Type type) {
		if(currentStack.size() > 1) {
			createStackVariables(currentBlock, currentStack);
		}
		Expression expr = pop();
		addStmt(new StackDumpStatement(expr, index, type));
	}
	
	void _load(int index, Type type) {
		push(new StackLoadExpression(index, type));
	}
	
	void _inc(int index, int amt) {
		createStackVariables(currentBlock, currentStack);
		StackLoadExpression load = new StackLoadExpression(index, Type.INT_TYPE);
		ArithmeticExpression inc = new ArithmeticExpression(new ConstantExpression(amt), load, Operator.ADD);
		addStmt(inc);
	}
}