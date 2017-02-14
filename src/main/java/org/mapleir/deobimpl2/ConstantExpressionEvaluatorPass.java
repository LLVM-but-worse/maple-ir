package org.mapleir.deobimpl2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ArithmeticExpr.Operator;
import org.mapleir.ir.code.expr.CastExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.NegationExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class ConstantExpressionEvaluatorPass implements ICompilerPass, Opcode {

	private final BridgeClassLoader classLoader;
	private final Map<String, Bridge> bridges;
	
	public ConstantExpressionEvaluatorPass() {
		classLoader = new BridgeClassLoader();
		bridges = new HashMap<>();
	}
	
	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		for(ClassNode cn : cxt.getClassTree().getClasses().values()) {
			for(MethodNode m : cn.methods) {
				
				ControlFlowGraph cfg = cxt.getIR(m);
				LocalsPool pool = cfg.getLocals();
				
				for(BasicBlock b : cfg.vertices()) {
					for(Stmt stmt : b) {
						for(Expr e : stmt.enumerateOnlyChildren()) {
							CodeUnit par = e.getParent();
							if(par != null) {
								/* no point evaluating constants */
								if(e.getOpcode() != CONST_LOAD) {
									Expr val = eval(pool, e);
									if(val != null) {
										par.overwrite(val, par.indexOf(e));
									}
								}
							}
						}
					}
				}
			}
		}
	}
	
	private Expr eval(LocalsPool pool, Expr e) {
		if(e.getOpcode() == CONST_LOAD) {
			return e;
		} else if(e.getOpcode() == ARITHMETIC) {
			ArithmeticExpr ae = (ArithmeticExpr) e;
			Expr l = ae.getLeft();
			Expr r = ae.getRight();
			
			Expr le = eval(pool, l);
			Expr re = eval(pool, r);
			
			if(le != null && re != null) {
				ConstantExpr lc = (ConstantExpr) le;
				ConstantExpr rc = (ConstantExpr) re;
				
				Bridge b = getArithmeticBridge(lc.getType(), rc.getType(), ae.getType(), ae.getOperator());
				
				ConstantExpr cr = new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()));
				return cr;
			}
		} else if(e.getOpcode() == NEGATE) {
			NegationExpr neg = (NegationExpr) e;
			Expr e2 = eval(pool, neg.getExpression());
			
			if(e2 != null) {
				ConstantExpr ce = (ConstantExpr) e2;
				Bridge b = getNegationBridge(e2.getType());
				
				ConstantExpr cr = new ConstantExpr(b.eval(ce.getConstant()));
				return cr;
			}
		} else if(e.getOpcode() == LOCAL_LOAD) {
			VarExpr v = (VarExpr) e;
			Local l = v.getLocal();
			
			AbstractCopyStmt def = pool.defs.get(l);
			Expr rhs = def.getExpression();
			
			if(rhs.getOpcode() == LOCAL_LOAD) {
				VarExpr v2 = (VarExpr) rhs;
				
				// synthetic copies lhs = rhs;
				if(v2.getLocal() == l) {
					return null;
				}
			}
			
			return eval(pool, rhs);
		} else if(e.getOpcode() == CAST) {
			CastExpr cast = (CastExpr) e;
			Expr e2 = eval(pool, cast.getExpression());
			
			if(e2 != null) {
				ConstantExpr ce = (ConstantExpr) e2;
				
				if(!ce.getType().equals(cast.getExpression().getType())) {
					throw new IllegalStateException(ce.getType() + " : " + cast.getExpression().getType());
				}
				Type from = ce.getType();
				Type to = cast.getType();
				
				boolean p1 = isPrimitive(from);
				boolean p2 = isPrimitive(to);
				
				if(p1 != p2) {
					throw new IllegalStateException(from + " to " + to);
				}
				
				if(!p1 && !p2) {
					return null;
				}
				
				Bridge b = getCastBridge(from, to);
				
				ConstantExpr cr = new ConstantExpr(b.eval(ce.getConstant()));
				return cr;
			}
		}
		
		return null;
	}
	
	private boolean isPrimitive(Type t) {
		switch(t.getSort()) {
			case Type.VOID:
			case Type.ARRAY:
			case Type.OBJECT:
			case Type.METHOD:
				return false;
			default:
				return true;
		}
	}
	
	private Bridge getCastBridge(Type from, Type to) {
		String name = "CASTFROM" + from.getClassName() + "TO" + to.getClassName();
		
		if(bridges.containsKey(name)) {
			return bridges.get(name);
		}
		
		ClassNode owner = new ClassNode();
		owner.version = Opcodes.V1_7;
		owner.name = name;
		owner.superName = "java/lang/Object";
		owner.access = Opcodes.ACC_PUBLIC;
		
		String desc = ("(" + from.getDescriptor() + ")" + to.getDescriptor());
		MethodNode m = new MethodNode(owner, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "eval", desc, null, null);
		
		InsnList insns = new InsnList();
		{
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(from), 0));
			
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(from, to);
			for (int i = 0; i < cast.length; i++) {
				insns.add(new InsnNode(cast[i]));
			}
			
			insns.add(new InsnNode(TypeUtils.getReturnOpcode(to)));
			m.instructions = insns;
		}
		
		owner.methods.add(m);
		
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		owner.accept(cw);
		
		byte[] bytes = cw.toByteArray();
		Class<?> clazz = classLoader.make(name, bytes);
		
		for(Method method : clazz.getDeclaredMethods()) {
			if(method.getName().equals("eval")) {
				Bridge b = new Bridge(method);
				
				bridges.put(name, b);
				return b;
			}
		}
		
		throw new UnsupportedOperationException();
	}
	
	private Bridge getNegationBridge(Type t) {
		String name = "NEG" + t.getClassName();
		
		if(bridges.containsKey(name)) {
			return bridges.get(name);
		}
		
		ClassNode owner = new ClassNode();
		owner.version = Opcodes.V1_7;
		owner.name = name;
		owner.superName = "java/lang/Object";
		owner.access = Opcodes.ACC_PUBLIC;
		
		String desc = ("(" + t.getDescriptor() + ")" + t.getDescriptor());
		MethodNode m = new MethodNode(owner, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "eval", desc, null, null);
		
		InsnList insns = new InsnList();
		{
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(t), 0));
			insns.add(new InsnNode(TypeUtils.getNegateOpcode(t)));
			insns.add(new InsnNode(TypeUtils.getReturnOpcode(t)));
			m.instructions = insns;
		}
		
		owner.methods.add(m);
		
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		owner.accept(cw);
		
		byte[] bytes = cw.toByteArray();
		Class<?> clazz = classLoader.make(name, bytes);
		
		for(Method method : clazz.getDeclaredMethods()) {
			if(method.getName().equals("eval")) {
				Bridge b = new Bridge(method);
				
				bridges.put(name, b);
				return b;
			}
		}
		
		throw new UnsupportedOperationException();
	}
	
	private Bridge getArithmeticBridge(Type t1, Type t2, Type rt, Operator op) {
		String name = t1.getClassName() + op.name() + t2.getClassName() + "RET" + rt.getClassName();
		
		if(bridges.containsKey(name)) {
			return bridges.get(name);
		}
		
		ClassNode owner = new ClassNode();
		owner.version = Opcodes.V1_7;
		owner.name = name;
		owner.superName = "java/lang/Object";
		owner.access = Opcodes.ACC_PUBLIC;
		
		String desc = ("(" + t1.getDescriptor() + t2.getDescriptor() + ")" + rt.getDescriptor());
		MethodNode m = new MethodNode(owner, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "eval", desc, null, null);
		
		InsnList insns = new InsnList();
		{
			Type leftType = null;
			Type rightType = null;
			if (op == Operator.SHL || op == Operator.SHR || op == Operator.USHR) {
				leftType = rt;
				rightType = Type.INT_TYPE;
			} else {
				leftType = rightType = rt;
			}
			
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(t1), 0));
			int[] lCast = TypeUtils.getPrimitiveCastOpcodes(t1, leftType);
			for (int i = 0; i < lCast.length; i++) {
				insns.add(new InsnNode(lCast[i]));
			}

			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(t2), 1));
			int[] rCast = TypeUtils.getPrimitiveCastOpcodes(t2, rightType);
			for (int i = 0; i < rCast.length; i++) {
				insns.add(new InsnNode(rCast[i]));
			}
			
			int opcode;
			switch (op) {
				case ADD:
					opcode = TypeUtils.getAddOpcode(rt);
					break;
				case SUB:
					opcode = TypeUtils.getSubtractOpcode(rt);
					break;
				case MUL:
					opcode = TypeUtils.getMultiplyOpcode(rt);
					break;
				case DIV:
					opcode = TypeUtils.getDivideOpcode(rt);
					break;
				case REM:
					opcode = TypeUtils.getRemainderOpcode(rt);
					break;
				case SHL:
					opcode = TypeUtils.getBitShiftLeftOpcode(rt);
					break;
				case SHR:
					opcode = TypeUtils.bitShiftRightOpcode(rt);
					break;
				case USHR:
					opcode = TypeUtils.getBitShiftRightUnsignedOpcode(rt);
					break;
				case OR:
					opcode = TypeUtils.getBitOrOpcode(rt);
					break;
				case AND:
					opcode = TypeUtils.getBitAndOpcode(rt);
					break;
				case XOR:
					opcode = TypeUtils.getBitXorOpcode(rt);
					break;
				default:
					throw new RuntimeException();
			}
			
			insns.add(new InsnNode(opcode));
			insns.add(new InsnNode(TypeUtils.getReturnOpcode(rt)));
			
			m.instructions = insns;
		}
		
		owner.methods.add(m);
		
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		owner.accept(cw);
		
		byte[] bytes = cw.toByteArray();
		Class<?> clazz = classLoader.make(name, bytes);
		
		for(Method method : clazz.getDeclaredMethods()) {
			if(method.getName().equals("eval")) {
				Bridge b = new Bridge(method);
				
				bridges.put(name, b);
				return b;
			}
		}
		
		throw new UnsupportedOperationException();
	}
	
	private static class Bridge {
		private final Method method;
		
		Bridge(Method method) {
			this.method = method;
		}
		
		public Object eval(Object... objs) {
			try {
				Object ret = method.invoke(null, objs);
				return ret;
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	private static class BridgeClassLoader extends ClassLoader {
		public Class<?> make(String name, byte[] bytes) {
			return defineClass(name.replace("/", "."), bytes, 0, bytes.length);
		}
	}
}