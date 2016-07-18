package org.rsdeob.deobimpl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.cfg.tree.NodeVisitor;
import org.objectweb.asm.commons.cfg.tree.node.AbstractNode;
import org.objectweb.asm.commons.cfg.tree.node.ArithmeticNode;
import org.objectweb.asm.commons.cfg.tree.node.NumberNode;
import org.objectweb.asm.commons.cfg.tree.util.TreeBuilder;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.deob.IPhase;
import org.topdank.banalysis.asm.insn.InstructionPrinter;

import java.util.*;
import java.util.Map.Entry;

public class ConstantOperationReordererPhase implements IPhase {
	
	public static final String KEY_ID = ConstantOperationReordererPhase.class.getCanonicalName();
	private static final TreeBuilder TREE_BUILDER = new TreeBuilder();

	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
		NodeVisitorImpl nv = new NodeVisitorImpl();

		Map<MethodNode, Set<ReorderActor>> actorMap = new HashMap<MethodNode, Set<ReorderActor>>();
		for(ClassNode cn : cxt.getNodes().values()) {
			for(MethodNode m : cn.methods) {
				TREE_BUILDER.build(m).accept(nv);
				actorMap.put(m, new HashSet<ReorderActor>(nv.actors));
				nv.actors.clear();
			}
		}
		
		for(Entry<MethodNode, Set<ReorderActor>> e : actorMap.entrySet()) {
			MethodNode m = e.getKey();
			
			if(m.toString().equals("f.u(Lee;Ljava/awt/Component;II)Lbg;")) {
				InstructionPrinter.consolePrint(m);
				System.err.println("done");
				System.out.println(TREE_BUILDER.build(m));
				System.err.println("done");
			}
			
			for(ReorderActor actor : e.getValue()) {
				actor.reorder(m);
			}
			
			if(m.toString().equals("f.u(Lee;Ljava/awt/Component;II)Lbg;")) {
				InstructionPrinter.consolePrint(m);
			}
		}
		
		System.out.printf("   Reordered:%n");
		System.out.printf("      %d const + var expressions.%n", nv.adds);
		System.out.printf("      %d -const + var expressions.%n", nv.subswitch);
		System.out.printf("      %d const * var expressions.%n", nv.mults);
		System.out.printf("      %d var - -const expressions.%n", nv.negsub);
		System.out.printf("      %d var + -const expressions.%n", nv.possub);
		System.out.printf("   Found %d min value -const + var expressions.%n", nv.subadds);
		System.out.printf("   Found %d min value var - -const expressions.%n", nv.inegsub);
	}
	
	class NodeVisitorImpl extends NodeVisitor {
		final Set<ReorderActor> actors = new HashSet<ReorderActor>();
		int adds, subswitch, subadds, mults, negsub, possub, inegsub;
		
		@Override
		public void visitOperation(ArithmeticNode an) {
			if(an.size() < 2) {
				return;
			}
			
			NumberNode nn = an.firstNumber();
			if(nn == null || !nn.isInt()) {
				return;
			}
			AbstractNode other = an.get(0);
			if(nn == other) {
				other = an.get(1);
			} else {
				// it means the operation is
				// (something)  [operation]  CONSTANT
				// which is fine except for the
				// var - - const fringe case.
				if((an.subtracting() || an.adding()) && nn.longNumber() < 0) {
					Number num = nn.nNumber();
					Class<? extends Number> type = getOperationType(an);
					Number inverse = invertNumber(type, num);
					if(num != inverse) {
						actors.add(new NegateNegativeReorderer(an, new AbstractInsnNode[]{nn.insn(), an.insn()}, nn, type, inverse));
						if(an.subtracting()) {
							negsub++;
						} else {
							possub++;
						}
					} else {
						inegsub++;
						System.out.println("inegsub: " + an);
					}
				}
				return;
			}
			
			if(an.adding()) {
				/* Possible scenarios:
				 *  const   +   var
				 *         <->
				 *   var    +  const
				 * All we need to do here is remove the
				 * instruction which pushes the constant
				 * onto the stack and then insert  it before
				 * the operation instruction. (Cheap way/easy
				 * way of swapping around the operands).
				 * 
				 * > bipush 100   <->    iload
				 * > iload        <->    bipush 100
				 * > iadd         <->    iadd
				 * 
				 */
				
				
				 /* 
				 *  -const   +   var
				 *          <->
				 *   var     +  -const
				 *          <->
				 *   var     -    const
				 * This is a bit more complicated. Here we
				 * need to invert the number so that it is
				 * a positive constant and then we switch
				 * the addition sign to a subtraction sign
				 * so that we effectively end up with a
				 * normal subtraction operation. This becomes
				 * a little bit more complicated when the
				 * constant is the lowest possible negative
				 * value of the constant type, ie. all the
				 * bits of the number are set to '1'. If it
				 * is the lowest negative value ten we have
				 * to convert the operation to a
				 *   var     +  (-const)
				 */
				
				if(nn.longNumber() >= 0) {
					actors.add(new SimpleOperationReorderer(an, new AbstractInsnNode[]{nn.insn(), an.insn()}, nn));
					adds++;
				} else {
					Number num = nn.nNumber();
					Class<? extends Number> type = getOperationType(an);
					Number inverse = invertNumber(type, num);
					if(num != inverse) {
						actors.add(new AdditionToSubtractionReorderer(an, new AbstractInsnNode[]{nn.insn(), an.insn()}, nn, type, inverse));
						subswitch++;
					} else {
						// doesn't seem to happen in the client atm.
						actors.add(new SimpleOperationReorderer(an, new AbstractInsnNode[]{nn.insn(), an.insn()}, nn));
						subadds++;
					}
				}
			} else if(an.subtracting()) {
				/* 
				 *  const   -   var
				 *    > This is currently unfixable (legimate code)
				 *    
				 *  
				 *   var    -   -const
				 *         <->
				 *   var    +    const
				 *   
				 * ^#see 'adding' part II.
				 * ^#see 'var[]const' above.
				 */
				
			} else if(an.multiplying()) {
				/* 
				 *  const   *   var
				 *         <->
				 *   var    *  const
				 */
				if(an.method().toString().equals("f.u(Lee;Ljava/awt/Component;II)Lbg;")) {
					System.out.println(an);
				}
				actors.add(new SimpleOperationReorderer(an, new AbstractInsnNode[]{nn.insn(), an.insn()}, nn));
				mults++;
			}
		}
	}

	abstract class ReorderActor {
		
		final ArithmeticNode an;
		final AbstractInsnNode[] insns;
		final NumberNode cst;
		
		ReorderActor(ArithmeticNode an, AbstractInsnNode[] insns, NumberNode cst) {
			this.an = an;
			this.insns = insns;
			this.cst = cst;
		}
		
		abstract void reorder(MethodNode m);
	}
	
	class SimpleOperationReorderer extends ReorderActor {

		SimpleOperationReorderer(ArithmeticNode an, AbstractInsnNode[] insns, NumberNode cst) {
			super(an, insns, cst);
		}

		@Override
		void reorder(MethodNode m) {
//			InsnList list = m.instructions;
//			AbstractInsnNode cstInsn = insns[0];
//			AbstractInsnNode opInsn = insns[1];
//			if(m.toString().equals("f.u(Lee;Ljava/awt/Component;IIB)Lbg;")) {
//				System.out.println(cstInsn + "  " + opInsn);
//				BasicInterpreter ba = new BasicInterpreter();
//				Analyzer<BasicValue> analyser = new Analyzer<BasicValue>(ba);
//				try {
//					analyser.analyze(m.owner.name, m);
//				} catch (AnalyzerException e1) {
//					e1.printStackTrace();
//					System.out.println("at: " + e1.node);
//				}
//				Frame<BasicValue> frame = analyser.getFrames()[list.indexOf(opInsn)];
//				System.out.println(frame.getStackSize());
//				
//			}
//			list.remove(cstInsn);
//			list.insertBefore(opInsn, cstInsn);
		}
	}
	
	class AdditionToSubtractionReorderer extends ReorderActor {

		final Class<? extends Number> type;
		final Number newNumber;
		
		public AdditionToSubtractionReorderer(ArithmeticNode an, AbstractInsnNode[] insns, NumberNode cst, Class<? extends Number> type, Number newNumber) {
			super(an, insns, cst);
			this.type = type;
			this.newNumber = newNumber;
		}

		@Override
		void reorder(MethodNode m) {
			InsnList list = m.instructions;
			AbstractInsnNode cstInsn = insns[0];
			AbstractInsnNode opInsn = insns[1];
			// remove the previous constant insn
			list.remove(cstInsn);
			// generate the new one
			cstInsn = numberToNode(type, newNumber);
			// add the new one after
			list.insertBefore(opInsn, cstInsn);
			// swap the operation sign
			opInsn.setOpcode(invertOpcode(type, opInsn.opcode()));
		}
	}
	
	class NegateNegativeReorderer extends ReorderActor {

		final Class<? extends Number> type;
		final Number newNumber;
		
		public NegateNegativeReorderer(ArithmeticNode an, AbstractInsnNode[] insns, NumberNode cst, Class<? extends Number> type, Number newNumber) {
			super(an, insns, cst);
			this.type = type;
			this.newNumber = newNumber;
		}

		@Override
		void reorder(MethodNode m) {
			InsnList list = m.instructions;
			AbstractInsnNode cstInsn = insns[0];
			AbstractInsnNode opInsn = insns[1];
			// generate the new one
			AbstractInsnNode newCstInsn = numberToNode(type, newNumber);
			// add the new one after the old one
			list.insertBefore(cstInsn, newCstInsn);
			// remove the previous constant insn
			list.remove(cstInsn);
			// swap the operation sign
			opInsn.setOpcode(invertOpcode(type, opInsn.opcode()));
		}
	}
	
	private static Number invertNumber(Class<? extends Number> type, Number number) {
		if(type == Byte.class) {
			byte val = number.byteValue();
			if(!(val >= Byte.MIN_VALUE && val <= Byte.MAX_VALUE)) {
				throw new IllegalArgumentException("byte out of bounds: " + val);
			} else {
				if(val == Byte.MIN_VALUE) {
					// 128 as short
					// abs(int) even though it should be abs(short)
					System.out.println("[[[111]]]");
					return Short.valueOf((short)Math.abs((int)val));
				} else {
					// guaranteed to be in the byte range
					return Byte.valueOf((byte)Math.abs((int)val));
				}
			}
		} else if(type == Short.class) {
			short val = number.shortValue();
			if(!(val >= Short.MIN_VALUE && val <= Short.MAX_VALUE)) {
				throw new IllegalArgumentException("short out of bounds: " + val);
			} else {
				if(val == Short.MIN_VALUE) {
					// 32768 as int
					System.out.println("[[[222]]]");
					return Integer.valueOf(Math.abs((int)val));
				} else {
					// guaranteed to be in the short range
					return Short.valueOf((short)Math.abs((int)val));
				}
			}
		} else if(type == Integer.class) {
			int val = number.intValue();
			if(!(val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE)) {
				throw new IllegalArgumentException("int out of bounds: " + val);
			} else {
				if(val == Integer.MIN_VALUE) {
					// 2147483648 as long
					System.out.println("[[[333]]]");
					return Long.valueOf(Math.abs((long)val));
				} else {
					// guaranteed to be in the int range
					return Integer.valueOf((int)Math.abs((int)val));
				}
			}
		} else if(type == Long.class) {
			long val = number.longValue();
			if(!(val >= Long.MIN_VALUE && val <= Long.MAX_VALUE)) {
				throw new IllegalArgumentException("long out of bounds: " + val);
			} else {
				if(val == Long.MIN_VALUE) {
					// 9223372036854775808 is out of bounds still so
					// return the negative number.
					System.out.println("[[[444]]]");
					return number;
				} else {
					// guaranteed to be in the long range
					return Long.valueOf((long)Math.abs((long)val));
				}
			}
		} 
		// FIXME: Floating point values
		
		/* else if(type == Float.class) {
			float val = number.floatValue();
			if(!(val >= Float.MIN_VALUE && val <= Float.MAX_VALUE)) {
				throw new IllegalArgumentException("float out of bounds: " + val);
			} else {
				if(val == Float.MIN_VALUE) {
					// 1.4E-45 as double
					return number;
				} else {
					// guaranteed to be in the long range
					return Long.valueOf((long)Math.abs((long)val));
				}
			}
		} else if(type == Double.class) {
			double val = number.doubleValue();
			if(!(val >= Double.MIN_VALUE && val <= Double.MAX_VALUE)) {
				throw new IllegalArgumentException("double out of bounds: " + val);
			} else {
				if(val == Double.MIN_VALUE) {
					// 4.9E-324 as positive
					return number;
				} else {
					// guaranteed to be in the long range
					return Long.valueOf((long)Math.abs((long)val));
				}
			}
		} */
		
		else {
			throw new IllegalArgumentException(type.getCanonicalName());
		}
	}
	
	private static int invertOpcode(Class<? extends Number> type, int opcode) {
		if(opcode >= Opcodes.IADD && opcode <= Opcodes.DADD) {
			if(type == Byte.class || type == Short.class || type == Integer.class) {
				return Opcodes.ISUB;
			} else if(type == Long.class) {
				return Opcodes.LSUB;
			} else if(type == Float.class) {
				return Opcodes.FSUB;
			} else if(type == Double.class) {
				return Opcodes.DSUB;
			}
		} else if(opcode >= Opcodes.ISUB && opcode <= Opcodes.DSUB) {
			if(type == Byte.class || type == Short.class || type == Integer.class) {
				return Opcodes.IADD;
			} else if(type == Long.class) {
				return Opcodes.LADD;
			} else if(type == Float.class) {
				return Opcodes.FADD;
			} else if(type == Double.class) {
				return Opcodes.DADD;
			}
		}
		
		throw new IllegalStateException(Printer.OPCODES[opcode]);
	}
	
	private static Class<? extends Number> getOperationType(ArithmeticNode an) {
		int opcode = an.opcode();
		String name = Printer.OPCODES[opcode];
		switch(name.charAt(0)) {
			case 'I':
				return Integer.class;
			case 'L':
				return Long.class;
			case 'D':
				return Double.class;
			case 'F':
				return Float.class;
			default:
				throw new RuntimeException(name);
		}
	}
	
	private static AbstractInsnNode numberToNode(Class<? extends Number> type, Number number) {
		if(type == Byte.class) {
			byte val = number.byteValue();
			long lval = number.longValue();
			// check that the value is in the byte range
			if(val != lval || !(lval >= Byte.MIN_VALUE && lval <= Byte.MAX_VALUE)) {
				throw new IllegalStateException();
			}
			return new IntInsnNode(Opcodes.BIPUSH, val);
		} else if(type == Short.class) {
			short val = number.shortValue();
			long lval = number.longValue();
			// check that the value is in the short range
			if(val != lval || !(lval >= Short.MIN_VALUE && lval <= Short.MAX_VALUE)) {
				throw new IllegalStateException();
			}
			return new IntInsnNode(Opcodes.SIPUSH, val);
		} else if(type == Integer.class) {
			int val = number.intValue();
			if(val >= -1 && val <= 5) {
				return new InsnNode(Opcodes.ICONST_0 + val);
			} else if(val >= Byte.MIN_VALUE && val <= Byte.MAX_VALUE) {
				return new IntInsnNode(Opcodes.BIPUSH, val);
			} else if(val >= Short.MIN_VALUE && val <= Short.MAX_VALUE) {
				return new IntInsnNode(Opcodes.SIPUSH, val);
			} else {
				return new LdcInsnNode(val);
			}
		} else if(type == Long.class) {
			int val = number.intValue();
			if(val == 0 || val == 1) {
				return new InsnNode(Opcodes.LCONST_0 + val);
			} else if(val >= Byte.MIN_VALUE && val <= Byte.MAX_VALUE) {
				return new IntInsnNode(Opcodes.BIPUSH, val);
			} else if(val >= Short.MIN_VALUE && val <= Short.MAX_VALUE) {
				return new IntInsnNode(Opcodes.SIPUSH, val);
			} else {
				return new LdcInsnNode(val);
			}
		} else if(type == Float.class) {
			float val = number.floatValue();
			int ival = number.intValue();
			if(val == ival && (ival == 0 || ival == 1 || ival == 2)) {
				return new InsnNode(Opcodes.FCONST_0 + ival);
			} else {
				return new LdcInsnNode(val);
			}
		} else if(type == Double.class) {
			double val = number.doubleValue();
			int ival = number.intValue();
			if(val == ival && (ival == 0 || ival == 1)) {
				return new InsnNode(Opcodes.DCONST_0 + ival);
			} else {
				return new LdcInsnNode(val);
			}
		} else {
			throw new IllegalArgumentException(type.getCanonicalName());
		}
	}
}