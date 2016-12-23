package org.mapleir.deobimpl2;

import java.util.*;

import org.mapleir.IRCallTracer;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.ConstantExpression;
import org.mapleir.ir.code.expr.InitialisedObjectExpression;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public class ConstantParameterPass implements ICompilerPass, Opcode {
	
	private static final Comparator<Integer> INTEGER_ORDERER = new Comparator<Integer>() {
		@Override
		public int compare(Integer o1, Integer o2) {
			return Integer.compareUnsigned(o1, o2);
		}
	};
	
	@Override
	public String getId() {
		return "Argument-Prune";
	}
	
	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		Map<MethodNode, Set<Expr>> calls = new HashMap<>();
		Map<MethodNode, List<List<Expr>>> args = new HashMap<>();
		Map<MethodNode, int[]> paramIndices = new HashMap<>();
		Set<Expr> processed = new HashSet<>();
		
		IRCallTracer tracer = new IRCallTracer(cxt) {
			@Override
			protected void visitMethod(MethodNode m) {
				Type[] paramTypes = Type.getArgumentTypes(m.desc);
				List<List<Expr>> lists = new ArrayList<>(paramTypes.length);
				int[] idxs = new int[paramTypes.length];
				int idx = 0;
				if((m.access & Opcodes.ACC_STATIC) == 0) {
					idx++;
				}
				for(int i=0; i < paramTypes.length; i++) {
					lists.add(new ArrayList<>());

					idxs[i] = idx;
					idx += paramTypes[i].getSize();
				}
				paramIndices.put(m, idxs);
				args.put(m, lists);
				calls.put(m, new HashSet<>());
			}
			
			@Override
			protected void processedInvocation(MethodNode caller, MethodNode callee, Expr e) {
				calls.get(callee).add(e);
				
				Expr[] params;
				
				if(e.getOpcode() == INVOKE) {
					params = ((InvocationExpression) e).getParameterArguments();
				} else if(e.getOpcode() == INIT_OBJ) {
					params = ((InitialisedObjectExpression) e).getArgumentExpressions();
				} else {
					throw new UnsupportedOperationException(String.format("%s -> %s (%s)", caller, callee, e));
				}
				
				for(int i=0; i < params.length; i++) {
					args.get(callee).get(i).add(params[i]);
				}
			}
		};
		

		for(MethodNode mn : cxt.getActiveMethods()) {
			tracer.trace(mn);
		}
		
		int kp = 0;
		
		for(MethodNode mn : cxt.getActiveMethods()) {
			// TODO: try to find the original method
			// in the class chain and if we have control
			// over all of the classes, only remove the
			// parameter then.
			ControlFlowGraph cfg = cxt.getIR(mn);
			
			List<List<Expr>> argExprs = args.get(mn);

			Set<Integer> dead = new TreeSet<>(INTEGER_ORDERER);
			
			for(int i=0; i < argExprs.size(); i++) {
				List<Expr> l = argExprs.get(i);
				ConstantExpression c = getConstantValue(l);
				
				if(c != null) {
					LocalsPool pool = cfg.getLocals();
					int resolvedIndex = paramIndices.get(mn)[i];
					VersionedLocal vl = pool.get(resolvedIndex, 0, false);
					AbstractCopyStatement def = pool.defs.get(vl);
					
					boolean removeDef = true;
					
					/* demote the def from a synthetic
					 * copy to a normal one. */
					VarExpression dv = def.getVariable().copy();
					
					VersionedLocal spill = pool.makeLatestVersion(vl);
					dv.setLocal(spill);
					
					CopyVarStatement copy = new CopyVarStatement(dv, c.copy());
					BasicBlock b = def.getBlock();
					def.delete();
					def = copy;
					b.add(copy);
					
					pool.defs.remove(vl);
					pool.defs.put(spill, copy);
					
					Set<VarExpression> spillUses = new HashSet<>();
					pool.uses.put(spill, spillUses);
					
					Iterator<VarExpression> it = pool.uses.get(vl).iterator();
					while(it.hasNext()) {
						VarExpression v = it.next();
						
						if(v.getParent() == null) {
							/* the use is in a phi, we can't
							 * remove the def. */
							removeDef = false;
							spillUses.add(v);
							v.setLocal(spill);
						} else {
							CodeUnit par = v.getParent();
							par.overwrite(c.copy(), par.indexOf(v));
						}
					}

					pool.uses.remove(vl);
					
					if(removeDef) {
						def.delete();
					}
					
					dead.add(i);
				} else if(isMultiVal(l)) {
//					System.out.printf("Multivalue param for %s @ arg%d:   %s.%n", mn, i, l);
				}
			}
			
			if(dead.size() > 0) {
				kp += dead.size();
				
				mn.desc = buildDesc(Type.getArgumentTypes(mn.desc), Type.getReturnType(mn.desc), dead);
				for(Expr call : calls.get(mn)) {
					/* since the callgrapher finds all
					 * the methods in a hierarchy and considers
					 * it as a single invocation, a certain
					 * invocation may be considered multiple times. */
					if(processed.contains(call)) {
						continue;
					}
					processed.add(call);
					if(call.getOpcode() == Opcode.INIT_OBJ) {
						InitialisedObjectExpression init = (InitialisedObjectExpression) call;

						CodeUnit parent = init.getParent();
						Expr[] newArgs = buildArgs(init.getArgumentExpressions(), 0, dead);
						InitialisedObjectExpression init2 = new InitialisedObjectExpression(init.getType(), init.getOwner(), mn.desc, newArgs);

						parent.overwrite(init2, parent.indexOf(init));
					} else if(call.getOpcode() == Opcode.INVOKE) {
						InvocationExpression invoke = (InvocationExpression) call;

						CodeUnit parent = invoke.getParent();
						
						Expr[] newArgs = buildArgs(invoke.getArgumentExpressions(), invoke.getCallType() == Opcodes.INVOKESTATIC ? 0 : -1, dead);
						InvocationExpression invoke2 = new InvocationExpression(invoke.getCallType(), newArgs, invoke.getOwner(), invoke.getName(), mn.desc);
						
						parent.overwrite(invoke2, parent.indexOf(invoke));
					} else {
						throw new UnsupportedOperationException(call.toString());
					}
				}
			}
		}
		
		System.out.printf("Removed %d constant paramters.%n", kp);
	}
	
	private static Expr[] buildArgs(Expr[] oldArgs, int off, Set<Integer> dead) {
		Expr[] newArgs = new Expr[oldArgs.length - dead.size()];

		int j = newArgs.length - 1;
		for(int i=oldArgs.length-1; i >= 0; i--) {
			Expr e = oldArgs[i];
			if(!dead.contains(i + off)) {
				newArgs[j--] = e;
			}
			e.unlink();
		}
		
		return newArgs;
	}
	
	private static String buildDesc(Type[] preParams, Type ret, Set<Integer> dead) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		for(int i=0; i < preParams.length; i++) {
			if(!dead.contains(i)) {
				Type t = preParams[i];
				sb.append(t.toString());
			}
		}
		sb.append(")").append(ret.toString());
		return sb.toString();
	}
	
	private static boolean isMultiVal(List<Expr> exprs) {
		if(exprs.size() <= 1) {
			return false;
		}
		
		for(Expr e : exprs) {
			if(e.getOpcode() != Opcode.CONST_LOAD) {
				return false;
			}
		}
		return true;
	}
	
	private static ConstantExpression getConstantValue(List<Expr> exprs) {
		ConstantExpression v = null;
		
		for(Expr e : exprs) {
			if(e.getOpcode() == Opcode.CONST_LOAD) {
				ConstantExpression c = (ConstantExpression) e;
				if(v == null) {
					v = c;
				} else {
					if(c.getConstant() != null && c.getConstant().equals(v.getConstant())) {
						v = c;
					} else {
						return null;
					}
				}
			} else {
				return null;
			}
		}
		
		return v;
	}
}