package org.mapleir.deobimpl2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.mapleir.deobimpl2.ConstantParameterPass.SemiConstantParameter;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.cfg.edge.UnconditionalJumpEdge;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ArithmeticExpr.Operator;
import org.mapleir.ir.code.expr.CastExpr;
import org.mapleir.ir.code.expr.ComparisonExpr;
import org.mapleir.ir.code.expr.ComparisonExpr.ValueComparisonType;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.NegationExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt.ComparisonType;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
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
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
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
		int j = 0;
		
		AtomicReference<ConstantParameterPass> pass = new AtomicReference<>();
		
		for(ICompilerPass c : completed) {
			if(c instanceof ConstantParameterPass) {
				pass.set((ConstantParameterPass) c);
			}
		}
		
		for(ClassNode cn : cxt.getClassTree().getClasses().values()) {
			for(MethodNode m : cn.methods) {
				
				ControlFlowGraph cfg = cxt.getIR(m);
				LocalsPool pool = cfg.getLocals();
				
				for(BasicBlock b : new HashSet<>(cfg.vertices())) {
					for(int i=0; i < b.size(); i++) {
						Stmt stmt = b.get(i);
						
						if(stmt.getOpcode() == COND_JUMP) {
							ConditionalJumpStmt cond = (ConditionalJumpStmt) stmt;
							
							Expr l = cond.getLeft();
							Expr r = cond.getRight();
							
							if(!isPrimitive(l.getType()) || !isPrimitive(r.getType())) {
								continue;
							}
							
							LocalValueResolver resolver = new LocalValueResolver() {
								@Override
								public Set<Expr> getValues(Local l) {
									AbstractCopyStmt copy = pool.defs.get(l);

									ConstantParameterPass cp = pass.get();
									if(cp != null) {
										Set<Expr> set = new HashSet<>();
										
										if(copy.isSynthetic()) {
											VarExpr vE = (VarExpr) copy.getExpression();
											if(vE.getLocal() != l) {
												throw new IllegalStateException(copy + " : " + l);
											}
											Set<SemiConstantParameter> set2 = cp.getSemiConstantValues(m);
											
											if(set2 != null) {
												for(SemiConstantParameter param : set2) {
													if(param.lvtIndex == l.getIndex()) {
														set.addAll(param.inputs);
													}
												}
											}
										} else {
											set.add(copy.getExpression());
										}
										
										return set;
									} else {
										Set<Expr> set = new HashSet<>();
										set.add(copy.getExpression());
										return set;
									}
								}
							};
							Set<ConstantExpr> lSet = evalPossibleValues(resolver, l);
							Set<ConstantExpr> rSet = evalPossibleValues(resolver, r);
							
							if(isValidSet(lSet) && isValidSet(rSet)) {
								predictBranch(cfg, b, cond, i, lSet, rSet);
							}
						}
						
						for(Expr e : stmt.enumerateOnlyChildren()) {
							CodeUnit par = e.getParent();
							if(par != null) {
								/* no point evaluating constants */
								if(e.getOpcode() != CONST_LOAD) {
									Expr val = eval(pool, e);
									if(val != null) {
										
										if(!val.equivalent(e)) {
											par.overwrite(val, par.indexOf(e));
											
											j++;
										}
									}
								} else if(e.getOpcode() == ARITHMETIC) {
									ArithmeticExpr ae = (ArithmeticExpr) e;
									
									Expr e2 = simplify(pool, ae);
									
									if(e2 != null) {
										par.overwrite(e2, par.indexOf(e));
										
										j++;
									}
									
								}
							}
						}
					}
				}
			}
		}
		
		System.out.printf("  evaluated %d constant expressions.%n", j);
	}
	
	private Expr simplify(LocalsPool pool, ArithmeticExpr e) {
		Expr r = e.getRight();
		
		Expr re = eval(pool, r);
		
		if(re instanceof ConstantExpr) {
			ConstantExpr ce =(ConstantExpr) re;
			
			Object o = ce.getConstant();
			
			if(o instanceof Integer || o instanceof Long) {
				if(FieldRSADecryptionPass.__eq((Number) o, 1, o instanceof Long)) {
					return e.getLeft().copy();
				} else if(FieldRSADecryptionPass.__eq((Number) o, 0, o instanceof Long)) {
					return new ConstantExpr(0);
				}
			}
		}
		
		return null;
		
		
	}
	
	private void predictBranch(ControlFlowGraph cfg, BasicBlock b, ConditionalJumpStmt cond, int insnIndex, Set<ConstantExpr> leftSet, Set<ConstantExpr> rightSet) {
		Boolean val = null;
		
		for(ConstantExpr lc : leftSet) {
			for(ConstantExpr rc : rightSet) {
				if(isPrimitive(lc.getType()) && isPrimitive(rc.getType())) {
					Bridge bridge = getConditionalEvalBridge(lc.getType(), rc.getType(), cond.getComparisonType());
					boolean branchVal = (boolean) bridge.eval(lc.getConstant(), rc.getConstant());
					
					if(val != null) {
						if(val.booleanValue() != branchVal) {
							return;
						}
					} else {
						val = branchVal;
					}
				} else {
					System.err.println("something::");
					System.err.println("  " + cond);
					System.err.println("  leftset: " + leftSet);
					System.err.println("  rightSet: " + rightSet);
					return;
				}
			}
		}
		
		if(val != null) {
			if(leftSet.size() > 1 || rightSet.size() > 1) {
				System.out.println("Strong elim:: predict=" + val.toString());
				System.out.println("  " + cond);
				System.out.println("  leftset: " + leftSet);
				System.out.println("  rightSet: " + rightSet);
			}
			eliminateBranch(cfg, b, cond, insnIndex, val);
		}
	}
	
	private void eliminateBranch(ControlFlowGraph cfg, BasicBlock b, ConditionalJumpStmt cond, int insnIndex, boolean val) {
		if(val) {
			// always true, jump to true successor
			for(FlowEdge<BasicBlock> fe : new HashSet<>(cfg.getEdges(b))) {
				if(fe.getType() == FlowEdges.COND) {
					if(fe.dst != cond.getTrueSuccessor()) {
						throw new IllegalStateException(fe + ", " + cond);
					}
					
					cfg.removeEdge(b, fe);
					DeadCodeEliminationPass.safeKill(fe);
				} else if(fe.getType() == FlowEdges.IMMEDIATE) {
					DeadCodeEliminationPass.safeKill(fe);
					cfg.removeEdge(b, fe);
				} else if(fe.getType() != FlowEdges.TRYCATCH) {
					throw new IllegalStateException(fe.toString());
				}
			}

			UnconditionalJumpStmt newJump = new UnconditionalJumpStmt(cond.getTrueSuccessor());
			b.set(insnIndex, newJump);
			UnconditionalJumpEdge<BasicBlock> uje = new UnconditionalJumpEdge<>(b, cond.getTrueSuccessor());
			cfg.addEdge(b, uje);
		} else {
			// always false, keep immediate (fallthrough) and
			// remove the conditional branch.

//			for (FlowEdge<BasicBlock> fe : new HashSet<>(cfg.getEdges(b))) {
//				if (fe.getType() == FlowEdges.COND) {
//					if (fe.dst != cond.getTrueSuccessor()) {
//						throw new IllegalStateException(fe + ", " + cond);
//					}
//
//					cfg.removeEdge(b, fe);
//					DeadCodeEliminationPass.safeKill(fe);
//				} else if (fe.getType() == FlowEdges.IMMEDIATE) {
//				} else if (fe.getType() != FlowEdges.TRYCATCH) {
//					throw new IllegalStateException(fe.toString());
//				}
//
//				b.remove(cond);
//			}
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
		} else if(e.getOpcode() == COMPARE) {
			ComparisonExpr comp = (ComparisonExpr) e;
			
			Expr l = comp.getLeft();
			Expr r = comp.getRight();
			
			Expr le = eval(pool, l);
			Expr re = eval(pool, r);
			
			if(le != null && re != null) {
				ConstantExpr lc = (ConstantExpr) le;
				ConstantExpr rc = (ConstantExpr) re;
				
				Bridge b = getComparisonBridge(lc.getType(), rc.getType(), comp.getComparisonType());
				
				System.out.println(b.method);
				System.out.println(comp + " -> " + b.eval(lc.getConstant(), rc.getConstant()));
				ConstantExpr cr = new ConstantExpr((int)b.eval(lc.getConstant(), rc.getConstant()));
				return cr;
			}
		}
		
		return null;
	}
	
	private boolean isValidSet(Set<?> set) {
		return set != null && set.size() > 0;
	}
	
	private <T> Set<T> returnCleanSet(Set<T> set) {
		if(set != null && set.size() > 0) {
			return set;
		} else {
			return null;
		}
	}
	
	private Set<ConstantExpr> evalPossibleValues(LocalValueResolver resolver, Expr e) {
		if(e.getOpcode() == CONST_LOAD) {
			Set<ConstantExpr> set = new HashSet<>();
			set.add((ConstantExpr) e);
			return set;
		} else if(e.getOpcode() == ARITHMETIC) {
			ArithmeticExpr ae = (ArithmeticExpr) e;
			Expr l = ae.getLeft();
			Expr r = ae.getRight();
			
			Set<ConstantExpr> le = evalPossibleValues(resolver, l);
			Set<ConstantExpr> re = evalPossibleValues(resolver, r);
			
			if(isValidSet(le) && isValidSet(re)) {
				Set<ConstantExpr> results = new HashSet<>();
				
				for(ConstantExpr lc : le) {
					for(ConstantExpr rc : re) {
						Bridge b = getArithmeticBridge(lc.getType(), rc.getType(), ae.getType(), ae.getOperator());
						results.add(new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant())));
					}
				}
				
				return returnCleanSet(results);
			}
		} else if(e.getOpcode() == NEGATE) {
			NegationExpr neg = (NegationExpr) e;
			Set<ConstantExpr> vals = evalPossibleValues(resolver, neg.getExpression());
			
			if(isValidSet(vals)) {
				Set<ConstantExpr> results = new HashSet<>();
				
				for(ConstantExpr c : vals) {
					Bridge b = getNegationBridge(c.getType());
					results.add(new ConstantExpr(b.eval(c.getConstant())));
				}
				
				return returnCleanSet(results);
			}
		} else if(e.getOpcode() == LOCAL_LOAD) {
			VarExpr v = (VarExpr) e;
			Local l = v.getLocal();
			
			Set<Expr> defExprs = resolver.getValues(l);

			if(isValidSet(defExprs)) {
				Set<ConstantExpr> vals = new HashSet<>();
				
				for(Expr defE : defExprs) {
					if(defE.getOpcode() == LOCAL_LOAD) {
						VarExpr v2 = (VarExpr) defE;
						
						// synthetic copies lhs = rhs;
						if(v2.getLocal() == l) {
							continue;
						}
					}
					
					Set<ConstantExpr> set2 = evalPossibleValues(resolver, defE);
					if(isValidSet(set2)) {
						vals.addAll(set2);
					}
				}
				
				return returnCleanSet(vals);
			}
		} else if(e.getOpcode() == CAST) {
			CastExpr cast = (CastExpr) e;
			Set<ConstantExpr> set = evalPossibleValues(resolver, cast.getExpression());
			
			if(isValidSet(set)) {
				Set<ConstantExpr> results = new HashSet<>();
				
				for(ConstantExpr ce : set) {
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
					
					results.add(new ConstantExpr(b.eval(ce.getConstant())));
				}
				
				return returnCleanSet(results);
			}
		} else if(e.getOpcode() == COMPARE) {
//			throw new UnsupportedOperationException("todo lmao");
//			ComparisonExpr comp = (ComparisonExpr) e;
			
//			Expr l = comp.getLeft();
//			Expr r = comp.getRight();
//			
//			Expr le = eval(pool, l);
//			Expr re = eval(pool, r);
//			
//			if(le != null && re != null) {
//				ConstantExpr lc = (ConstantExpr) le;
//				ConstantExpr rc = (ConstantExpr) re;
//				
//				Bridge b = getComparisonBridge(lc.getType(), rc.getType(), comp.getComparisonType());
//				
//				System.out.println(b.method);
//				System.out.println(comp + " -> " + b.eval(lc.getConstant(), rc.getConstant()));
//				ConstantExpr cr = new ConstantExpr((int)b.eval(lc.getConstant(), rc.getConstant()));
//				return cr;
//			}
		}
		
		return null;
	}
	
	static interface LocalValueResolver {
		Set<Expr> getValues(Local l);
	}
	
	static class PooledLocalValueResolver implements LocalValueResolver {
		
		final LocalsPool pool;
		
		PooledLocalValueResolver(LocalsPool pool) {
			this.pool = pool;
		}
		
		@Override
		public Set<Expr> getValues(Local l) {
			AbstractCopyStmt copy = pool.defs.get(l);
			
			Set<Expr> set = new HashSet<>();
			set.add(copy.getExpression());
			return set;
		}
	}
	
	private void cast(InsnList insns, Type from, Type to) {
		int[] cast = TypeUtils.getPrimitiveCastOpcodes(from, to);
		for (int i = 0; i < cast.length; i++) {
			insns.add(new InsnNode(cast[i]));
		}
	}
	
	private void branchReturn(InsnList insns, LabelNode trueSuccessor) {
		// return false
		insns.add(new InsnNode(Opcodes.ICONST_0));
		insns.add(new InsnNode(Opcodes.IRETURN));
		insns.add(trueSuccessor);
		// return true
		insns.add(new InsnNode(Opcodes.ICONST_1));
		insns.add(new InsnNode(Opcodes.IRETURN));
	}
	
	private Bridge getConditionalEvalBridge(Type lt, Type rt, ComparisonType type) {
		Type opType = TypeUtils.resolveBinOpType(lt, rt);
		String name = lt.getClassName() + type.name() + rt.getClassName() + "OPTYPE" + opType.getClassName() + "RETbool";

		String desc = "(" + lt.getDescriptor() + rt.getDescriptor() + ")Z";

		if(bridges.containsKey(name)) {
			return bridges.get(name);
		}
		

		MethodNode m = makeBase(name, desc);
		{
			InsnList insns = new InsnList();
			
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(lt), 0));
			cast(insns, lt, opType);
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(rt), rt.getSize()));
			cast(insns, rt, opType);
			

			LabelNode trueSuccessor = new LabelNode();
			
			if (opType == Type.INT_TYPE) {
				insns.add(new JumpInsnNode(Opcodes.IF_ICMPEQ + type.ordinal(), trueSuccessor));
			} else if (opType == Type.LONG_TYPE) {
				insns.add(new InsnNode(Opcodes.LCMP));
				insns.add(new JumpInsnNode(Opcodes.IFEQ + type.ordinal(), trueSuccessor));
			} else if (opType == Type.FLOAT_TYPE) {
				insns.add(new InsnNode((type == ComparisonType.LT || type == ComparisonType.LE) ? Opcodes.FCMPL : Opcodes.FCMPG));
				insns.add(new JumpInsnNode(Opcodes.IFEQ + type.ordinal(), trueSuccessor));
			} else if (opType == Type.DOUBLE_TYPE) {
				insns.add(new InsnNode((type == ComparisonType.LT || type == ComparisonType.LE) ? Opcodes.DCMPL : Opcodes.DCMPG));
				insns.add(new JumpInsnNode(Opcodes.IFEQ + type.ordinal(), trueSuccessor));
			} else {
				throw new IllegalArgumentException(opType.toString());
			}
			
			branchReturn(insns, trueSuccessor);
			
			m.instructions = insns;
		}
		
		return buildBridge(m);
	}
	
	private Bridge getComparisonBridge(Type lt, Type rt, ValueComparisonType type) {
		String name = lt.getClassName() + type.name() + rt.getClassName() + "RET" + rt.getClassName();

		if(bridges.containsKey(name)) {
			return bridges.get(name);
		}
		
		int op;
		String desc = ("(" + lt.getDescriptor() + rt.getDescriptor() + ")" + rt.getDescriptor());
		
		MethodNode m = makeBase(name, desc);
		
		if (lt == Type.LONG_TYPE || rt == Type.LONG_TYPE) {
			op = Opcodes.LCMP;
		} else if (lt == Type.FLOAT_TYPE || rt == Type.FLOAT_TYPE) {
			op = type == ValueComparisonType.GT ? Opcodes.FCMPG : Opcodes.FCMPL;
		} else if (lt == Type.DOUBLE_TYPE || rt == Type.DOUBLE_TYPE) {
			op = type == ValueComparisonType.GT ? Opcodes.DCMPG : Opcodes.DCMPL;
		} else {
			throw new IllegalArgumentException();
		}
		
		{
			InsnList insns = new InsnList();
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(lt), 0));
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(rt), rt.getSize() /*D,J=2, else 1*/));
			insns.add(new InsnNode(op));
			insns.add(new InsnNode(Opcodes.IRETURN));
			
			m.instructions = insns;
		}
		
		return buildBridge(m);
	}
	
	private Bridge getCastBridge(Type from, Type to) {
		String name = "CASTFROM" + from.getClassName() + "TO" + to.getClassName();
		
		if(bridges.containsKey(name)) {
			return bridges.get(name);
		}

		String desc = ("(" + from.getDescriptor() + ")" + to.getDescriptor());
		MethodNode m = makeBase(name, desc);
		
		InsnList insns = new InsnList();
		{
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(from), 0));
			cast(insns, from, to);
			insns.add(new InsnNode(TypeUtils.getReturnOpcode(to)));
			m.instructions = insns;
		}
		
		return buildBridge(m);
	}
	
	private Bridge getNegationBridge(Type t) {
		String name = "NEG" + t.getClassName();
		
		if(bridges.containsKey(name)) {
			return bridges.get(name);
		}
		
		String desc = ("(" + t.getDescriptor() + ")" + t.getDescriptor());
		MethodNode m = makeBase(name, desc);
		
		InsnList insns = new InsnList();
		{
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(t), 0));
			insns.add(new InsnNode(TypeUtils.getNegateOpcode(t)));
			insns.add(new InsnNode(TypeUtils.getReturnOpcode(t)));
			m.instructions = insns;
		}
		
		return buildBridge(m);
	}
	
	private Bridge getArithmeticBridge(Type t1, Type t2, Type rt, Operator op) {
		String name = t1.getClassName() + op.name() + t2.getClassName() + "RET" + rt.getClassName();
		
		if(bridges.containsKey(name)) {
			return bridges.get(name);
		}
		
		String desc = ("(" + t1.getDescriptor() + t2.getDescriptor() + ")" + rt.getDescriptor());
		MethodNode m = makeBase(name, desc);
		
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
			cast(insns, t1, leftType);

			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(t2), t2.getSize() /*D,J=2, else 1*/));
			cast(insns, t2, rightType);
			
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
		
		return buildBridge(m);
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

	private MethodNode makeBase(String name, String desc) {
		ClassNode owner = new ClassNode();
		owner.version = Opcodes.V1_7;
		owner.name = name;
		owner.superName = "java/lang/Object";
		owner.access = Opcodes.ACC_PUBLIC;
		
		MethodNode m = new MethodNode(owner, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "eval", desc, null, null);
		owner.methods.add(m);
		return m;
	}
	
	private Bridge buildBridge(MethodNode m) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		ClassNode owner = m.owner;
		owner.accept(cw);
		
		byte[] bytes = cw.toByteArray();
		Class<?> clazz = classLoader.make(owner.name, bytes);
		
		for(Method method : clazz.getDeclaredMethods()) {
			if(method.getName().equals("eval")) {
				Bridge b = new Bridge(method);
				
				bridges.put(owner.name, b);
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