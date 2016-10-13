package org.mapleir.ir.cfg.builder;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.tree.AbstractInsnNode.*;

import java.util.*;
import java.util.Map.Entry;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.ExpressionStack;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.*;
import org.mapleir.ir.code.expr.ArithmeticExpression.Operator;
import org.mapleir.ir.code.expr.ComparisonExpression.ValueComparisonType;
import org.mapleir.ir.code.stmt.*;
import org.mapleir.ir.code.stmt.ConditionalJumpStatement.ComparisonType;
import org.mapleir.ir.code.stmt.MonitorStatement.MonitorMode;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.cfg.edge.ConditionalJumpEdge;
import org.mapleir.stdlib.cfg.edge.DefaultSwitchEdge;
import org.mapleir.stdlib.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.cfg.edge.SwitchEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.cfg.edge.UnconditionalJumpEdge;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.cfg.util.TypeUtils.ArrayType;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;

public class GenerationPass extends ControlFlowGraphBuilder.BuilderPass {

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
	

	private final InsnList insns;
	private final BitSet finished;
	private final LinkedList<LabelNode> queue;
	private final Set<LabelNode> marks;
	
	private BitSet stacks;
	private BasicBlock currentBlock;
	private ExpressionStack currentStack;
	private boolean saved;

	public GenerationPass(ControlFlowGraphBuilder builder) {
		super(builder);
		
		/* a block can exist in the map in the graph 
		 * but not be populated yet.
		 * we do this so that when a flow function is reached, 
		 * we can create the block reference and then handle
		 * the creation mechanism later. */
		finished = new BitSet();
		queue = new LinkedList<>();
		stacks = new BitSet();
		marks = new HashSet<>();
		
		insns = builder.method.instructions;
	}
	
	BasicBlock makeBlock(LabelNode label) {
		BasicBlock b = new BasicBlock(builder.graph, ++builder.count, label);
		queue(label);
		builder.graph.addVertex(b);
		return b;
	}
	
	BasicBlock resolveTarget(LabelNode label) {
		BasicBlock block = builder.graph.getBlock(label);
		if(block == null) {
			block = makeBlock(label);
		}
		return block;
	}
	
	void init() {
		entry(checkLabel());
		
		for(TryCatchBlockNode tc : builder.method.tryCatchBlocks) {
//			System.out.println(tc.start);
//			System.out.println(tc.end);
//			System.out.println(tc.handler);
			handler(tc);
		}
	}

	LabelNode checkLabel() {
		AbstractInsnNode first = insns.getFirst();
		if (first == null) {
			LabelNode nFirst = new LabelNode();
			insns.add(nFirst);
			first = nFirst;
		} else if (!(first instanceof LabelNode)) {
			LabelNode nFirst = new LabelNode();
			insns.insertBefore(first, nFirst);
			first = nFirst;
		}
		return (LabelNode) first;
	}
	
	void entry(LabelNode firstLabel) {
		LabelNode l = new LabelNode();
		BasicBlock entry = new BasicBlock(builder.graph, ++builder.count, l);
		builder.graph.addVertex(entry);
		builder.graph.getEntries().add(entry);
		entry.setInputStack(new ExpressionStack(16));
		defineInputs(builder.method, entry);
		insns.insertBefore(firstLabel, l);
		
		BasicBlock b = makeBlock(firstLabel);
		b.setInputStack(new ExpressionStack(16));
		queue(firstLabel);
		
		builder.graph.addEdge(entry, new ImmediateEdge<>(entry, b));
	}
	
	void handler(TryCatchBlockNode tc) {
		LabelNode label = tc.handler;
		BasicBlock handler = resolveTarget(label);
		marks.add(tc.start);
		marks.add(tc.end);
		if(handler.getInputStack() != null) {
//			System.err.println(handler.getInputStack());
//			System.err.println("Double handler: " + handler.getId() + " " + tc);
			return;
		}
		
		ExpressionStack stack = new ExpressionStack(16);
		handler.setInputStack(stack);
		
		Expression expr = new CaughtExceptionExpression(tc.type);
		Type type = expr.getType();
		VarExpression var = _var_expr(0, type, true);
		CopyVarStatement stmt = copy(var, expr, handler);
		handler.add(stmt);
		
		stack.push(load_stack(0, type));
		
		queue(label);
		
		stacks.set(handler.getNumericId());
	}
	
	void defineInputs(MethodNode m, BasicBlock b) {
		Type[] args = Type.getArgumentTypes(m.desc);
		int index = 0;
		if((m.access & Opcodes.ACC_STATIC) == 0) {
			addEntry(index, Type.getType("L" + m.owner.name + ";"), b);
			index++;
		}
	
		for(int i=0; i < args.length; i++) {
			Type arg = args[i];
			addEntry(index, arg, b);
			index += arg.getSize();
		}
	}
	
	void addEntry(int index, Type type, BasicBlock b) {
		VarExpression var = _var_expr(index, type, false);
		CopyVarStatement stmt = selfDefine(var);
		builder.assigns.getNonNull(var.getLocal()).add(b);
		b.add(stmt);
	}
	
	CopyVarStatement selfDefine(VarExpression var) {
		return new CopyVarStatement(var, var, true);
	}
	
	void queue(LabelNode label) {
		if(!queue.contains(label)) {
			queue.addLast(label);
		}
	}
	
	void preprocess(BasicBlock b) {
		ExpressionStack stack = b.getInputStack().copy();
		stacks.set(b.getNumericId());
		
		currentBlock = b;
		currentStack = stack;
		saved = false;
	}
	
	void process(LabelNode label) {
		/* it may not be properly initialised yet, however. */
		BasicBlock block = builder.graph.getBlock(label);
		
		/* if it is, we don't need to process it. */
		if(block != null && finished.get(block.getNumericId())) {
			return;
		} else if(block == null) {
			block = makeBlock(label);
		} else {
			// i.e. not finished.
		}
		
		preprocess(block);
		
		/* populate instructions. */
		int codeIndex = insns.indexOf(label);
		finished.set(block.getNumericId());
		while(codeIndex < insns.size() - 1) {
			AbstractInsnNode ain = insns.get(++codeIndex);
			int type = ain.type();
			
			if(ain.opcode() != -1) {
				process(block, ain);
			}
			
			if(type == LABEL) {
				// split into new block
				BasicBlock immediate = resolveTarget((LabelNode) ain);
				builder.graph.addEdge(block, new ImmediateEdge<>(block, immediate));
				break;
			} else if(type == JUMP_INSN) {
				JumpInsnNode jin = (JumpInsnNode) ain;
				BasicBlock target = resolveTarget(jin.label);
				
				if(jin.opcode() == JSR) {
					throw new UnsupportedOperationException("jsr " + builder.method);
				} else if(jin.opcode() == GOTO) {
					builder.graph.addEdge(block, new UnconditionalJumpEdge<>(block, target, jin.opcode()));
				} else {
					builder.graph.addEdge(block, new ConditionalJumpEdge<>(block, target, jin.opcode()));
					int nextIndex = codeIndex + 1;
					AbstractInsnNode nextInsn = insns.get(nextIndex);
					if(!(nextInsn instanceof LabelNode)) {
						LabelNode newLabel = new LabelNode();
						insns.insert(ain, newLabel);
						nextInsn = newLabel;
					}
					
					// create immediate successor reference if it's not already done
					BasicBlock immediate = resolveTarget((LabelNode) nextInsn);
					builder.graph.addEdge(block, new ImmediateEdge<>(block, immediate));
				}
				break;
			} else if(type == LOOKUPSWITCH_INSN) {
				LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) ain;
				
				for(int i=0; i < lsin.keys.size(); i++) {
					BasicBlock target = resolveTarget(lsin.labels.get(i));
					builder.graph.addEdge(block, new SwitchEdge<>(block, target, lsin, lsin.keys.get(i)));
				}
				
				BasicBlock dflt = resolveTarget(lsin.dflt);
				builder.graph.addEdge(block, new DefaultSwitchEdge<>(block, dflt, lsin));
				break;
			} else if(type == TABLESWITCH_INSN) {
				TableSwitchInsnNode tsin = (TableSwitchInsnNode) ain;
				for(int i=tsin.min; i <= tsin.max; i++) {
					BasicBlock target = resolveTarget(tsin.labels.get(i - tsin.min));
					builder.graph.addEdge(block, new SwitchEdge<>(block, target, tsin, i));
				}
				BasicBlock dflt = resolveTarget(tsin.dflt);
				builder.graph.addEdge(block, new DefaultSwitchEdge<>(block, dflt, tsin));
				break;
			} else if(isExitOpcode(ain.opcode())) {
				break;
			}
		}
		
		// TODO: check if it should have an immediate.
		BasicBlock im = block.getImmediate();
		if (im != null && !queue.contains(im)) {
			// System.out.println("Updating " + block.getId() + " -> " + im.getId());
			// System.out.println("  Pre: " + currentStack);
			update_target_stack(block, im, currentStack);
			// System.out.println("  Pos: " + currentStack);
		}
	}
	
	static boolean isExitOpcode(int opcode) {
		switch(opcode) {
			case Opcodes.RET:
			case Opcodes.ATHROW:
			case Opcodes.RETURN:
			case Opcodes.IRETURN:
			case Opcodes.LRETURN:
			case Opcodes.FRETURN:
			case Opcodes.DRETURN:
			case Opcodes.ARETURN: {
				return true;
			}
			default: {
				return false;
			}
		}
	}
	
	void process(BasicBlock b, AbstractInsnNode ain) {
		int opcode = ain.opcode();
		
		// System.out.println("Executing " + Printer.OPCODES[opcode]);
		// System.out.println(" PreStack: " + currentStack);
		
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
				_const(opcode - ICONST_M1 - 1);
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
				save_stack(false);
				_new_array(
					new Expression[] { pop() }, 
					TypeUtils.getPrimitiveArrayType(((IntInsnNode) ain).operand)
				);
				break;
			}
			case ANEWARRAY: {
				save_stack(false);
				_new_array(
					new Expression[] { pop() }, 
					Type.getType("[L" + ((TypeInsnNode) ain).desc + ";")
				);
				break;
			}
			case MULTIANEWARRAY: {
				MultiANewArrayInsnNode in = (MultiANewArrayInsnNode) ain;
				Expression[] bounds = new Expression[in.dims];
				for (int i = in.dims - 1; i >= 0; i--) {
					bounds[i] = pop();
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
				_return(Type.getReturnType(builder.method.desc));
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
				InvokeDynamicInsnNode dy = (InvokeDynamicInsnNode) ain;
				_dynamic_call(dy.bsm, dy.bsmArgs, dy.name, dy.desc);
				break;
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
					BasicBlock targ = resolveTarget(tsin.labels.get(i - tsin.min));
					targets.put(i, targ);
				}
				_switch(targets, resolveTarget(tsin.dflt));
				break;
			}
			
			case LOOKUPSWITCH: {
				LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) ain;
				LinkedHashMap<Integer, BasicBlock> targets = new LinkedHashMap<>();
				for(int i=0; i < lsin.keys.size(); i++) {
					int key = lsin.keys.get(i);
					BasicBlock targ = resolveTarget(lsin.labels.get(i));
					targets.put(key, targ);
				}
				_switch(targets, resolveTarget(lsin.dflt));
				break;
			}
			
			case GOTO:
				_jump_uncond(resolveTarget(((JumpInsnNode) ain).label));
				break;
			case IFNULL:
			case IFNONNULL:
				_jump_null(resolveTarget(((JumpInsnNode) ain).label), opcode == IFNONNULL);
				break;
				
			case IF_ICMPEQ:
			case IF_ICMPNE:
			case IF_ICMPLT:
			case IF_ICMPGE:
			case IF_ICMPGT:
			case IF_ICMPLE:
			case IF_ACMPEQ:
			case IF_ACMPNE:
				_jump_cmp(resolveTarget(((JumpInsnNode) ain).label), ComparisonType.getType(opcode));
				break;
				
			case IFEQ:
			case IFNE:
			case IFLT:
			case IFGE:
			case IFGT:
			case IFLE:
				_jump_cmp0(resolveTarget(((JumpInsnNode) ain).label), ComparisonType.getType(opcode));
				break;
		}
		

		// System.out.println(" PosStack: " + currentStack);
	}
	
	void _nop() {

	}

	void _const(Object o) {
		Expression e = new ConstantExpression(o);
		// int index = currentStack.height();
		// Type type = assign_stack(index, e);
		// push(load_stack(index, type));
		push(e);
	}

	void _compare(ValueComparisonType ctype) {
		save_stack(false);
		Expression right = pop();
		Expression left = pop();
		push(new ComparisonExpression(left, right, ctype));
	}

	void _return(Type type) {
		if (type == Type.VOID_TYPE) {
			currentStack.assertHeights(EMPTY_STACK_HEIGHTS);
			addStmt(new ReturnStatement());
		} else {
			save_stack(false);
			if(type.getSize() == 2) {
				currentStack.assertHeights(DOUBLE_RETURN_HEIGHTS);
			} else {
				currentStack.assertHeights(SINGLE_RETURN_HEIGHTS);
			}
			addStmt(new ReturnStatement(type, pop()));
		}
	}

	void _throw() {
		save_stack(false);
		currentStack.assertHeights(SINGLE_RETURN_HEIGHTS);
		addStmt(new ThrowStatement(pop()));
	}

	void _monitor(MonitorMode mode) {
		save_stack(false);
		addStmt(new MonitorStatement(pop(), mode));
	}

	void _arithmetic(Operator op) {
		save_stack(false);
		Expression e = new ArithmeticExpression(pop(), pop(), op);
		int index = currentStack.height();
		Type type = assign_stack(index, e);
		push(load_stack(index, type));
	}
	
	void _neg() {
		save_stack(false);
		push(new NegationExpression(pop()));
	}
	
	void _arraylength() {
		save_stack(false);
		push(new ArrayLengthExpression(pop()));
	}
	
	void _load_array(ArrayType type) {
		save_stack(false);
		// prestack: var1, var0 (height = 2)
		// poststack: var0
		// assignments: var0 = var0[var1]
//		int height = currentStack.height();
		Expression index = pop();
		Expression array = pop();
		push(new ArrayLoadExpression(array, index, type));
//		assign_stack(height - 2, new ArrayLoadExpression(array, index, type));
//		push(load_stack(height - 2, type.getType()));
	}
	
	void _store_array(ArrayType type) {
		save_stack(false);
		Expression value = pop();
		Expression index = pop();
		Expression array = pop();
		addStmt(new ArrayStoreStatement(array, index, value, type));
	}
	
	void _pop(int amt) {
		for(int i=0; i < amt; ) {
			Expression top = pop();
			addStmt(new PopStatement(top));
			i += top.getType().getSize();
		}
	}
	
	void _dup() {
		// prestack: var0 (height = 1)
		// poststack: var1, var0
		// assignments: var1 = var0(initial)
		currentStack.assertHeights(DUP_HEIGHTS);
		int baseHeight = currentStack.height();
		save_stack(false);
		
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
		save_stack(false);

		Expression var1 = pop();
		Expression var0 = pop();

		Type var3Type = assign_stack(baseHeight + 1, var0); // var3 = var0

		Type var0Type = assign_stack(baseHeight - 2, var1); // var0 = var1(initial)
		Type var2Type = assign_stack(baseHeight + 0, var1.copy()); // var2 = var1(initial)
		Type var1Type = assign_stack(baseHeight - 1, load_stack(baseHeight + 1, var3Type)); // var1 = var3 = var0(initial)

		push(load_stack(baseHeight - 2, var0Type)); // push var0
		push(load_stack(baseHeight - 1, var1Type)); // push var1
		push(load_stack(baseHeight + 0, var2Type)); // push var2
	}

	void _dup_x2() {
		int baseHeight = currentStack.height();
		save_stack(false);

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
			Type var3Type = assign_stack(baseHeight + 0, var2.copy()); // var3 = var2(initial)
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
		save_stack(false);

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
		save_stack(false);

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
		save_stack(false);
		
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
		save_stack(false);

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
		save_stack(false);
		Expression e = new CastExpression(pop(), type);
		int index = currentStack.height();
		assign_stack(index, e);
		push(load_stack(index, type));
	}
	
	void _instanceof(Type type) {
		save_stack(false);
		InstanceofExpression e = new InstanceofExpression(pop(), type);
		int index = currentStack.height();
		assign_stack(index, e);
		push(load_stack(index, Type.BOOLEAN_TYPE));
	}
	
	void _new(Type type) {
		save_stack(false);
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
	
	void _dynamic_call(Handle _bsm, Object[] _args, String name, String desc) {
		save_stack(false);
		Handle provider = new Handle(_bsm.getTag(), _bsm.getOwner(), _bsm.getName(), _bsm.getDesc());
		Object[] pArgs = new Object[_args.length];
		System.arraycopy(_args, 0, pArgs, 0, pArgs.length);
		
		// FIXME: can this end up as a virtual call
		Expression[] args = new Expression[Type.getArgumentTypes(desc).length];
		for(int i = args.length - 1; i >= 0; i--) {
			args[i] = pop();
		}
		
		DynamicInvocationExpression expr = new DynamicInvocationExpression(provider, pArgs, name, desc, args);
		if(expr.getType() == Type.VOID_TYPE) {
			addStmt(new PopStatement(expr));
		} else {
			int index = currentStack.height();
			Type type = assign_stack(index, expr);
			push(load_stack(index, type));
		}
		
		// TODO: redo vm lambdas as static resolution calls/concrete calls.
	}
	
	void _call(int op, String owner, String name, String desc) {
		save_stack(false);
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
		save_stack(false);
		Expression expr = pop();
		
		for (Entry<Integer, BasicBlock> e : targets.entrySet()) {
			update_target_stack(currentBlock, e.getValue(), currentStack);
		}
		
		update_target_stack(currentBlock, dflt, currentStack);
		
		addStmt(new SwitchStatement(expr, targets, dflt));
	}

	void _store_field(int opcode, String owner, String name, String desc) {
		save_stack(false);
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
		save_stack(false);
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
		save_stack(false);
		Expression expr = pop();
		VarExpression var = _var_expr(index, expr.getType(), false);
		addStmt(copy(var, expr));
	}

	void _load(int index, Type type) {
		VarExpression e = _var_expr(index, type, false);
		// assign_stack(currentStack.height(), e);
		push(e);
	}

	void _inc(int index, int amt) {
		save_stack(false);
		VarExpression load = _var_expr(index, Type.INT_TYPE, false);
		ArithmeticExpression inc = new ArithmeticExpression(new ConstantExpression(amt), load, Operator.ADD);
		VarExpression var = _var_expr(index, Type.INT_TYPE, false);
		addStmt(copy(var, inc));
	}
	
	CopyVarStatement copy(VarExpression v, Expression e) {
		return copy(v, e, currentBlock);
	}
	
	CopyVarStatement copy(VarExpression v, Expression e, BasicBlock b) {
		builder.assigns.getNonNull(v.getLocal()).add(b);
		return new CopyVarStatement(v.getParent() != null? v.copy() : v, e.getParent() != null? e.copy() : e);
	}
	
	VarExpression _var_expr(int index, Type type, boolean isStack) {
		Local l = builder.graph.getLocals().get(index, isStack);
		builder.locals.add(l);
		return new VarExpression(l, type);
	}
	
	// var[index] = expr
	Type assign_stack(int index, Expression expr) {
		if(expr.getOpcode() == Opcode.LOCAL_LOAD) {
			VarExpression v = (VarExpression) expr;
			if(v.getIndex() == index && v.getLocal().isStack()) {
				return expr.getType();
			}
		}
		Type type = expr.getType();
		VarExpression var = _var_expr(index, type, true);
		CopyVarStatement stmt = copy(var, expr);
		addStmt(stmt);
		return type;
	}
	
	Expression load_stack(int index, Type type) {
		return _var_expr(index, type, true);
	}
	
	void _jump_cmp(BasicBlock target, ComparisonType type, Expression left, Expression right) {
		update_target_stack(currentBlock, target, currentStack);
		addStmt(new ConditionalJumpStatement(left, right, target, type));
	}
	
	void _jump_cmp(BasicBlock target, ComparisonType type) {
		save_stack(false);
		Expression right = pop();
		Expression left = pop();
		_jump_cmp(target, type, left, right);
	}
	
	void _jump_cmp0(BasicBlock target, ComparisonType type) {
		save_stack(false);
		Expression left = pop();
		ConstantExpression right = new ConstantExpression(0);
		_jump_cmp(target, type, left, right);
	}

	void _jump_null(BasicBlock target, boolean invert) {
		save_stack(false);
		Expression left = pop();
		ConstantExpression right = new ConstantExpression(null);
		ComparisonType type = invert ? ComparisonType.NE : ComparisonType.EQ;
		
		_jump_cmp(target, type, left, right);
	}

	void _jump_uncond(BasicBlock target) {
		update_target_stack(currentBlock, target, currentStack);
		addStmt(new UnconditionalJumpStatement(target));
	}
	
	Expression _pop(Expression e) {
		if(e.getParent() != null) {
			return e.copy();
		} else {
			return e;
		}
	}
	
	Expression pop() {
		return _pop(currentStack.pop());
	}
	
	Expression peek() {
		return currentStack.peek();
	}

	void push(Expression e) {
		currentStack.push(e);
	}
	
	void addStmt(Statement stmt) {
		currentBlock.add(stmt);
	}
	
	void save_stack() {
		save_stack(true);
	}
	
	void save_stack(boolean check) {
		// System.out.println("Saving " + currentBlock.getId());
		if (!currentBlock.isEmpty() && currentBlock.get(currentBlock.size() - 1).canChangeFlow()) {
			throw new IllegalStateException("Flow instruction already added to block; cannot save stack: "  + currentBlock.getId());
		}
		
		// System.out.println("\n   Befor: " + currentStack);
		// System.out.println("     With size: " + currentStack.size());
		// System.out.println("     With height: " + currentStack.height());
		
		ExpressionStack copy = currentStack.copy();
		int len = currentStack.size();
		currentStack.clear();
		
		int height = 0;
		for(int i=len-1; i >= 0; i--) {
			// peek(0) = top
			// peek(len-1) = btm

			int index = height;
			Expression expr = copy.peek(i);
			
			if(expr.getParent() != null) {
				expr = expr.copy();
			}
			
			// System.out.println("    Pop: " + expr + ":" + expr.getType());
			// System.out.println("    Idx: " + index);
			Type type = assign_stack(index, expr);
			Expression e = load_stack(index, type);
			// System.out.println("    Push " + e + ":" + e.getType());
			// System.out.println("    tlen: " + type.getSize());

			currentStack.push(e);
			
			height += type.getSize();
		}
		
		if(check) {
			saved = true;
		}
		
		//System.out.println("   After: " + currentStack + "\n");
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
			if (!(e1.getOpcode() == Opcode.LOCAL_LOAD) || !(e2.getOpcode() == Opcode.LOCAL_LOAD)) {
				return false;
			}
			if (((VarExpression) e1).getIndex() != ((VarExpression) e2).getIndex()) {
				return false;
			}
			if (e1.getType().getSize() != e2.getType().getSize()) {
				return false;
			}
		}
		return true;
	}
	
	void update_target_stack(BasicBlock b, BasicBlock target, ExpressionStack stack) {
		if(stacks.get(b.getNumericId()) && !saved) {
			save_stack();
		}
		// called just before a jump to a successor block may
		// happen. any operations, such as comparisons, that
		// happen before the jump are expected to have already
		// popped the left and right arguments from the stack before
		// checking the merge state.
		if (!stacks.get(target.getNumericId())) {
			// unfinalised block found.
			// System.out.println("Setting target stack of " + target.getId() + " to " + stack);
			target.setInputStack(stack.copy());
			stacks.set(target.getNumericId());

			queue(target.getLabelNode());
		} else if (!can_succeed(target.getInputStack(), stack)) {
			// if the targets input stack is finalised and
			// the new stack cannot merge into it, then there
			// is an error in the bytecode (verifier error).
			
			// BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
			// DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, builder.graph);
			// writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(ControlFlowGraphDecorator.OPT_DEEP)).setName("6996").export();
			
			System.err.println("Current: " + stack + " in " + b.getId());
			System.err.println("Target : " + target.getInputStack() + " in " + target.getId());
			System.err.println(builder.graph);
			throw new IllegalStateException("Stack coherency mismatch into #" + target.getId());
		}
	}
	
	void makeRanges(List<BasicBlock> order) {
//		System.out.println(builder.graph);
//		BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
//		DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, builder.graph);
//		writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(ControlFlowGraphDecorator.OPT_DEEP)).setName("test9999").export();
		
		Map<String, ExceptionRange<BasicBlock>> ranges = new HashMap<>();
		for(TryCatchBlockNode tc : builder.method.tryCatchBlocks) {
			
//			System.out.printf("from %d to %d, handler:%d, type:%s.%n", insns.indexOf(tc.start), insns.indexOf(tc.end), insns.indexOf(tc.handler), tc.type);
//			System.out.println(String.format("%s:%s:%s", BasicBlock.createBlockName(insns.indexOf(tc.start)), BasicBlock.createBlockName(insns.indexOf(tc.end)), builder.graph.getBlock(tc.handler).getId()));
			
			int start = builder.graph.getBlock(tc.start).getNumericId();
			int end = builder.graph.getBlock(tc.end).getNumericId() - 1;
			
			List<BasicBlock> range = GraphUtils.range(order, start, end);
			BasicBlock handler = builder.graph.getBlock(tc.handler);
			String key = String.format("%s:%s:%s", BasicBlock.createBlockName(start), BasicBlock.createBlockName(end), handler.getId());
			
			ExceptionRange<BasicBlock> erange;
			if(ranges.containsKey(key)) {
				erange = ranges.get(key);
			} else {
				erange = new ExceptionRange<>(tc);
				erange.setHandler(handler);
				erange.addVertices(range);
				ranges.put(key, erange);
				
				if(!erange.isContiguous()) {
					System.out.println(erange + " not contiguous");
				}
				builder.graph.addRange(erange);
			}
			
			erange.addType(tc.type);
			
			ListIterator<BasicBlock> lit = range.listIterator();
			while(lit.hasNext()) {
				BasicBlock block = lit.next();
				builder.graph.addEdge(block, new TryCatchEdge<>(block, erange));
			}
		}
	}
	
	void ensureMarks() {
		// it is possible for the start/end blocks of ranges
		// to not be generated/blocked during generation,
		// so we generate them here.
		
		for(LabelNode m : marks) {
			// creates the block if it's not
			// already in the graph.
			resolveTarget(m);
		}
		// queue is irrelevant at this point.
		queue.clear();
		
		// since the blocks created were not reached
		// it means that their inputstacks were empty.
		// this also means no edges are needed to connect
		// them except for the range edges which are done
		// later.
		// we can also rely on the natural label ordering
		// code to fix up the graph to make it look like
		// this block is next to the previous block in code.
	}
	
	void processQueue() {
		while(!queue.isEmpty()) {
			LabelNode label = queue.removeFirst();
			process(label);
		}
		
		ensureMarks();
		
		List<BasicBlock> blocks = new ArrayList<>(builder.graph.vertices());
		Collections.sort(blocks, new Comparator<BasicBlock>() {
			@Override
			public int compare(BasicBlock o1, BasicBlock o2) {
				int i1 = insns.indexOf(o1.getLabelNode());
				int i2 = insns.indexOf(o2.getLabelNode());
				return Integer.compare(i1, i2);
			}
		});
		
		builder.naturaliseGraph(blocks);
		makeRanges(blocks);
	}

	@Override
	public void run() {
		if(builder.count == 0) { // no blocks created
			init();
			processQueue();
		}
	}
}