package org.mapleir.deobimpl2;

import org.mapleir.deobimpl2.cxt.IContext;
import org.mapleir.deobimpl2.util.IPConstAnalysis;
import org.mapleir.deobimpl2.util.IPConstAnalysis.ChildVisitor;
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
import org.mapleir.ir.code.expr.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.ir.code.expr.NegationExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt.ComparisonType;
import org.mapleir.ir.code.stmt.NopStmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.deob.IPass;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConstantExpressionEvaluatorPass implements IPass, Opcode {

	private final BridgeClassLoader classLoader;
	private final Map<String, Bridge> bridges;
	
	public ConstantExpressionEvaluatorPass() {
		classLoader = new BridgeClassLoader();
		bridges = new HashMap<>();
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		int k = 0;
		int j = 0;
		
		IPConstAnalysisVisitor vis = new IPConstAnalysisVisitor(cxt);
		IPConstAnalysis.create(cxt, vis);
		
		for(;;) {
			int js = j;
			int ks = k;
			
			for(ClassNode cn : cxt.getApplication().iterate()) {
				for(MethodNode m : cn.methods) {
					
					ControlFlowGraph cfg = cxt.getCFGS().getIR(m);
					LocalsPool pool = cfg.getLocals();
					
					for(BasicBlock b : new HashSet<>(cfg.vertices())) {
						for(int i=0; i < b.size(); i++) {
							Stmt stmt = b.get(i);
							
							if(stmt.getOpcode() == COND_JUMP && simplifyConditionalBranch(vis, cfg, (ConditionalJumpStmt)stmt, i)) {
								 k++;
							}

							for(CodeUnit e : stmt.enumerateExecutionOrder()) {
								if(e instanceof Expr) {
									CodeUnit par = ((Expr) e).getParent();
									if(par != null) {
										j += simplifyArithmetic(pool, par, (Expr) e);
									}
								}
							}
						}
					}
				}
			}
			
			if(js == j && ks == k) {
				break;
			}
		}
		
//		System.out.printf("  evaluated %d constant expressions.%n", j);
//		System.out.printf("  eliminated %d constant branches.%n", k);
		
		return j;
	}
	
	private int simplifyArithmetic(LocalsPool pool, CodeUnit par, Expr e) {
		int j = 0;
		
		/* no point evaluating constants */
		if(e.getOpcode() != CONST_LOAD) {
			Expr val = eval(pool, e);
			if(val != null) {
				if(!val.equivalent(e)) {
					overwrite(par, e, val, pool);
					e = val;
					j++;
				}
			}
			
			// TODO: fix this hack for nested exprs
			if(e.getOpcode() == ARITHMETIC) {
				ArithmeticExpr ae = (ArithmeticExpr) e;
				Operator op = ae.getOperator();
				
				Expr e2 = simplify(pool, ae);
				
				if(e2 != null) {
					overwrite(par, e, e2, pool);
					j++;
				} else if (op == Operator.MUL) {
					/* (x * c) * k
					 * to
					 * (x * ck)
					 */
					Expr l = ae.getLeft();
					Expr r = ae.getRight();
					
					if(l.getOpcode() == ARITHMETIC) {
						ArithmeticExpr xcExpr = (ArithmeticExpr) l;

						if(xcExpr.getOperator() == Operator.MUL) {
							Expr r2 = xcExpr.getRight();
							Expr c = eval(pool, r2);
							Expr k = eval(pool, r);
							if(c != null && k != null) {
								ConstantExpr cc = (ConstantExpr) c;
								ConstantExpr ck = (ConstantExpr) k;
								
								Bridge bridge = getArithmeticBridge(c.getType(), k.getType(), c.getType(), Operator.MUL);

								/*System.out.println("eval: " + bridge.method + " " + cc.getConstant().getClass() + " " + ck.getConstant().getClass());
								System.out.println("   actual: " + cc.getType() + ", " +  ck.getType());
								System.out.println("      " + cc.getConstant() +"  " + ck.getConstant());*/
								
								Object v = bridge.eval(cc.getConstant(), ck.getConstant());
								
								ArithmeticExpr newAe = new ArithmeticExpr(new ConstantExpr(v, c.getType()), xcExpr.getLeft().copy(), Operator.MUL);
								overwrite(ae.getParent(), ae, newAe, pool);
								j++;
							}
						}
					}
				}
			}
		}
		
		return j;
	}
	
	private void overwrite(CodeUnit parent, Expr from, Expr to, LocalsPool pool) {
		updateDefuse(from, to, pool);
		parent.overwrite(to, parent.indexOf(from));
	}
	
	private void updateDefuse(Expr from, Expr to, LocalsPool pool) {
		// remove uses in from
		for(Expr e : from.enumerateWithSelf()) {
			if (e.getOpcode() == Opcode.LOCAL_LOAD) {
				VersionedLocal l = (VersionedLocal) ((VarExpr) e).getLocal();
				pool.uses.get(l).remove(e);
			}
		}
		
		// add uses in to
		for(Expr e : to.enumerateWithSelf()) {
			if (e.getOpcode() == Opcode.LOCAL_LOAD) {
				VarExpr var = (VarExpr) e;
				pool.uses.get((VersionedLocal) var.getLocal()).add(var);
			}
		}
	}
	
	private boolean simplifyConditionalBranch(IPConstAnalysisVisitor vis, ControlFlowGraph cfg, ConditionalJumpStmt cond, int i) {
		Expr l = cond.getLeft();
		Expr r = cond.getRight();
		
		if (!isPrimitive(l.getType()) || !isPrimitive(r.getType())) {
			if(l instanceof ConstantExpr && r instanceof ConstantExpr && !isPrimitive(l.getType()) && !isPrimitive(r.getType())) {
				return attemptNullarityBranchElimination(cfg, cond.getBlock(), cond, i, (ConstantExpr) l, (ConstantExpr) r);
			}
			return false;
		}
		
		LocalValueResolver resolver;
		
		LocalsPool pool = cfg.getLocals();
		if(vis != null) {
			// FIXME: use
			resolver = new SemiConstantLocalValueResolver(cfg.getMethod(), pool, vis);
		} else {
			resolver = new PooledLocalValueResolver(pool);
		}
		
		Set<ConstantExpr> lSet = evalPossibleValues(resolver, l);
		Set<ConstantExpr> rSet = evalPossibleValues(resolver, r);
		
		if(isValidSet(lSet) && isValidSet(rSet)) {
			return attemptPrimitiveBranchElimination(cfg, cond.getBlock(), cond, i, lSet, rSet);
		} else {
			return false;
		}
	}
	
	private boolean attemptNullarityBranchElimination(ControlFlowGraph cfg, BasicBlock b, ConditionalJumpStmt cond, int insnIndex, ConstantExpr left, ConstantExpr right) {
		if (left.getConstant() == null && right.getConstant() == null) {
			eliminateBranch(cfg, b, cond, insnIndex, cond.getComparisonType() == ComparisonType.EQ);
			return true;
		}
		if (cond.getComparisonType() == ComparisonType.EQ) {
			if ((left.getConstant() == null) != (right.getConstant() == null)) {
				eliminateBranch(cfg, b, cond, insnIndex, false);
				return true;
			}
		}
		return false;
	}
	
	private boolean attemptPrimitiveBranchElimination(ControlFlowGraph cfg, BasicBlock b, ConditionalJumpStmt cond, int insnIndex, Set<ConstantExpr> leftSet, Set<ConstantExpr> rightSet) {
		Boolean val = null;
		
		for(ConstantExpr lc : leftSet) {
			for(ConstantExpr rc : rightSet) {
				if(isPrimitive(lc.getType()) && isPrimitive(rc.getType())) {
					Bridge bridge = getConditionalEvalBridge(lc.getType(), rc.getType(), cond.getComparisonType());
					/*System.out.println("eval: " + bridge.method + " " + lc.getConstant().getClass() + " " + rc.getConstant().getClass());
					System.out.println("   actual: " + lc.getType() + ", " +  rc.getType());
					System.out.println("      " + lc.getConstant() +"  " + rc.getConstant());*/
					
					boolean branchVal = (boolean) bridge.eval(lc.getConstant(), rc.getConstant());
					
					if(val != null) {
						if(val != branchVal) {
							return false;
						}
					} else {
						val = branchVal;
					}
				} else {
					System.err.println("something::");
					System.err.println("  " + cond);
					System.err.println("  leftset: " + leftSet);
					System.err.println("  rightSet: " + rightSet);
					System.err.println(cfg);
					System.err.println(cfg.getMethod());
					throw new UnsupportedOperationException();
				}
			}
		}

		// FIXME: remove check when false branch removal is supported.
		if(val != null && val.booleanValue()) {
			/*if(leftSet.size() > 1 || rightSet.size() > 1) {
				System.out.println("Strong elim:: predict=" + val.toString());
				System.out.println("  " + cond);
				System.out.println("  leftset: " + leftSet);
				System.out.println("  rightSet: " + rightSet);
			}*/
			eliminateBranch(cfg, b, cond, insnIndex, val);
			return true;
		}
		return false;
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
					return new ConstantExpr(0, ce.getType());
				}
			}
		}
		
		return null;
	}
	
	private void killStmt(LocalsPool pool, ConditionalJumpStmt c) {
		for(Expr e : c.enumerateOnlyChildren()) {
			if(e.getOpcode() == Opcode.LOCAL_LOAD) {
				VarExpr v = (VarExpr) e;
				
				Local l = v.getLocal();
				pool.uses.get(l).remove(v);
			}
		}
	}
	
	private void eliminateBranch(ControlFlowGraph cfg, BasicBlock b, ConditionalJumpStmt cond, int insnIndex, boolean val) {
		LocalsPool pool = cfg.getLocals();

		for(FlowEdge<BasicBlock> fe : new HashSet<>(cfg.getEdges(b))) {
			if(fe.getType() == FlowEdges.COND) {
				if(fe.dst != cond.getTrueSuccessor()) {
					throw new IllegalStateException(fe + ", " + cond);
				}
				
				cfg.removeEdge(b, fe);
				DeadCodeEliminationPass.safeKill(pool, fe);
			}
		}
		killStmt(pool, cond);
		if(val) {
			// always true, jump to true successor
			for(FlowEdge<BasicBlock> fe : new HashSet<>(cfg.getEdges(b))) {
				if(fe.getType() == FlowEdges.IMMEDIATE) {
					DeadCodeEliminationPass.safeKill(pool, fe);
					cfg.removeEdge(b, fe);
				}
			}
			UnconditionalJumpStmt newJump = new UnconditionalJumpStmt(cond.getTrueSuccessor());
			b.set(insnIndex, newJump);
			UnconditionalJumpEdge<BasicBlock> uje = new UnconditionalJumpEdge<>(b, cond.getTrueSuccessor());
			cfg.addEdge(b, uje);
		} else {
			// always false, keep immediate (fallthrough) and
			// remove the conditional branch.
			b.set(insnIndex, new NopStmt());
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
				
				ConstantExpr cr = new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()), ae.getType());
				return cr;
			}
		} else if(e.getOpcode() == NEGATE) {
			NegationExpr neg = (NegationExpr) e;
			Expr e2 = eval(pool, neg.getExpression());
			
			if(e2 != null) {
				ConstantExpr ce = (ConstantExpr) e2;
				Bridge b = getNegationBridge(e2.getType());
				
				ConstantExpr cr = new ConstantExpr(b.eval(ce.getConstant()), ce.getType());
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
				
				ConstantExpr cr = new ConstantExpr(b.eval(ce.getConstant()), to);
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
				
				ConstantExpr cr = new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()), Type.INT_TYPE);
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
						results.add(new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()), ae.getType()));
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
					results.add(new ConstantExpr(b.eval(c.getConstant()), c.getType()));
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
					
					results.add(new ConstantExpr(b.eval(ce.getConstant()), to));
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
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(rt), lt.getSize()));
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
		String name = lt.getClassName() + type.name() + rt.getClassName() + "RETint";
		if(bridges.containsKey(name)) {
			return bridges.get(name);
		}
		
		String desc = "(" + lt.getDescriptor() + rt.getDescriptor() + ")I";
		MethodNode m = makeBase(name, desc);
		{
			Type opType = TypeUtils.resolveBinOpType(lt, rt);
			
			InsnList insns = new InsnList();
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(lt), 0));
			cast(insns, lt, opType);
			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(rt), lt.getSize()));
			cast(insns, rt, opType);
		
			int op;
			if (opType == Type.DOUBLE_TYPE) {
				op = type == ValueComparisonType.GT ? Opcodes.DCMPG : Opcodes.DCMPL;
			} else if (opType == Type.FLOAT_TYPE) {
				op = type == ValueComparisonType.GT ? Opcodes.FCMPG : Opcodes.FCMPL;
			} else if (opType == Type.LONG_TYPE) {
				op = Opcodes.LCMP;
			} else {
				throw new IllegalArgumentException();
			}
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

			insns.add(new VarInsnNode(TypeUtils.getVariableLoadOpcode(t2), t1.getSize() /*D,J=2, else 1*/));
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
	
	static class SemiConstantLocalValueResolver implements LocalValueResolver {
		private final MethodNode method;
		private final LocalsPool pool;
		private final IPConstAnalysisVisitor vis;
		
		public SemiConstantLocalValueResolver(MethodNode method, LocalsPool pool, IPConstAnalysisVisitor vis) {
			this.method = method;
			this.pool = pool;
			this.vis = vis;
		}

		@Override
		public Set<Expr> getValues(Local l) {
			Set<Expr> set = new HashSet<>();
			
			AbstractCopyStmt copy = pool.defs.get(l);
			if(copy.isSynthetic()) {
				VarExpr vE = (VarExpr) copy.getExpression();
				if(vE.getLocal() != l) {
					throw new IllegalStateException(copy + " : " + l);
				}
				
				int paramNum = copy.getBlock().indexOf(copy);
				if(!Modifier.isStatic(method.access)) {
					/* for a virtual call, the implicit
					 * this object isn't considered a
					 * parameter, so the current computed
					 * paramNum is off by +1 (as it is
					 * including the lvar0_0 synth def). */
					paramNum -= 1;
				}
				
				if(!vis.unconst.get(method)[paramNum]) {
					set.addAll(vis.constParams.get(method).get(paramNum));
				}
			} else {
				set.add(copy.getExpression());
			}
			
			return set;
		}
	}

	static class IPConstAnalysisVisitor implements ChildVisitor {

		final IContext cxt;
		final Map<MethodNode, List<Set<ConstantExpr>>> constParams = new HashMap<>();
		final Map<MethodNode, boolean[]> unconst = new HashMap<>();
		
		IPConstAnalysisVisitor(IContext cxt) {
			this.cxt = cxt;
		}
		
		@Override
		public void postVisitMethod(IPConstAnalysis analysis, MethodNode m) {
			int pCount = Type.getArgumentTypes(m.desc).length;
			boolean[] arr = new boolean[pCount];
			
			if(Modifier.isStatic(m.access)) {
				if(!constParams.containsKey(m)) {
					List<Set<ConstantExpr>> l = new ArrayList<>();
					constParams.put(m, l);
					
					for(int i=0; i < pCount; i++) {
						l.add(new HashSet<>());
					}
					
					unconst.put(m, arr);
				}
			} else {
				for(MethodNode site : cxt.getInvocationResolver().resolveVirtualCalls(m.owner.name, m.name, m.desc, true)) {
					if(!constParams.containsKey(site)) {
						List<Set<ConstantExpr>> l = new ArrayList<>();
						constParams.put(site, l);
						
						for(int i=0; i < pCount; i++) {
							l.add(new HashSet<>());
						}
						
						unconst.put(site, arr);
					}
				}
			}
		}
		
		@Override
		public void postProcessedInvocation(IPConstAnalysis analysis, MethodNode caller, MethodNode callee, Expr call) {
			Expr[] params;
			
			if(call.getOpcode() == INVOKE) {
				params = ((InvocationExpr) call).getParameterArguments();
			} else if(call.getOpcode() == INIT_OBJ) {
				params = ((InitialisedObjectExpr) call).getArgumentExpressions();
			} else {
				throw new UnsupportedOperationException(String.format("%s -> %s (%s)", caller, callee, call));
			}
			
			for(int i=0; i < params.length; i++) {
				Expr e = params[i];
				
				if(e.getOpcode() == Opcode.CONST_LOAD) {
					if(Modifier.isStatic(callee.access)) {
						constParams.get(callee).get(i).add((ConstantExpr) e);
					} else {
						/* only chain callsites *can* have this input */
						for(MethodNode site : cxt.getInvocationResolver().resolveVirtualCalls(callee.owner.name, callee.name, callee.desc, true)) {
							constParams.get(site).get(i).add((ConstantExpr) e);
						}
					}
				} else {
					/* callsites tainted */
					if(Modifier.isStatic(callee.access)) {
						unconst.get(callee)[i] = true;
					} else {
						/* only chain callsites *can* have this input */
						for(MethodNode site : cxt.getInvocationResolver().resolveVirtualCalls(callee.owner.name, callee.name, callee.desc, true)) {
							unconst.get(site)[i] = true;
						}
					}
				}
			}
		}
	}
}