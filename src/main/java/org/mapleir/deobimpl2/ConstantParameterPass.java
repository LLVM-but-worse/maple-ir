package org.mapleir.deobimpl2;

import java.lang.reflect.Modifier;
import java.util.*;

import org.mapleir.IRCallTracer;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ConstantParameterPass implements IPass, Opcode {
	
	private static final Comparator<Integer> INTEGER_ORDERER = new Comparator<Integer>() {
		@Override
		public int compare(Integer o1, Integer o2) {
			return Integer.compareUnsigned(o1, o2);
		}
	};

	public static class SemiConstantParameter {
		final int lvtIndex;
		final Set<ConstantExpr> inputs;
		
		SemiConstantParameter(int lvtIndex, Set<ConstantExpr> inputs) {
			this.lvtIndex = lvtIndex;
			this.inputs = inputs;
		}
	}
	
	private final Map<MethodNode, Set<Expr>> calls;
	/** Sets which contain the indices of constant value parameters of a method. */
	private final Map<MethodNode, Set<Integer>> constantParameterIndices;
	private final Map<MethodNode, Set<SemiConstantParameter>> semiConstantParameters;
	private final Set<MethodNode> processMethods;
	private final Set<MethodNode> cantfix;
	private final Set<Expr> processedExprs;
	private final Map<MethodNode, int[]> paramIndices;
	private final NullPermeableHashMap<MethodNode, Map<Integer, ConstantExpr>> constantParameters;
	
	public ConstantParameterPass() {
		calls = new HashMap<>();
		constantParameterIndices  = new HashMap<>();
		semiConstantParameters  = new HashMap<>();
		processMethods = new HashSet<>();
		cantfix = new HashSet<>();
		processedExprs = new HashSet<>();
		paramIndices = new HashMap<>();
		constantParameters = new NullPermeableHashMap<>(new ValueCreator<Map<Integer, ConstantExpr>>() {
			@Override
			public Map<Integer, ConstantExpr> create() {
				return new HashMap<>();
			}
		});
	}
	
	@Override
	public String getId() {
		return "Argument-Prune";
	}
	
	public Set<SemiConstantParameter> getSemiConstantValues(MethodNode m) {
		return semiConstantParameters.get(m);
	}
	
	
	private void inlineConstant(ControlFlowGraph cfg, MethodNode mn, int parameterIndex, ConstantExpr c) {
		LocalsPool pool = cfg.getLocals();
		int argLocalIndex = paramIndices.get(mn)[parameterIndex];
		
		VersionedLocal argLocal = pool.get(argLocalIndex, 0, false);
		AbstractCopyStmt argDef = pool.defs.get(argLocal);
		
		boolean removeDef = true;
		
		/* demote the def from a synthetic
		 * copy to a normal one. */
		try {
			argDef.getVariable().copy();
		} catch(RuntimeException e) {
			System.err.println(cfg);
			System.err.println(Modifier.isStatic(mn.access));
			System.err.println(argLocal + " : " + argDef);
			System.err.println("Param index: " + parameterIndex);
			System.err.println("Arg index: " + argLocalIndex);
			System.err.println(c);
			throw e;
		}
		VarExpr dv = argDef.getVariable().copy();
		
		VersionedLocal spill = pool.makeLatestVersion(argLocal);
		dv.setLocal(spill);
		
		CopyVarStmt copy = new CopyVarStmt(dv, c.copy());
		BasicBlock b = argDef.getBlock();
		argDef.delete();
		argDef = copy;
		b.add(copy);
		
		pool.defs.remove(argLocal);
		pool.defs.put(spill, copy);
		
		Set<VarExpr> spillUses = new HashSet<>();
		pool.uses.put(spill, spillUses);
		
		/* Replace each use of the parameter variable with
		 * the constant. */
		Iterator<VarExpr> it = pool.uses.get(argLocal).iterator();
		while(it.hasNext()) {
			VarExpr v = it.next();
			
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

		/* Remove the use set of the previous local
		 * since we've replaced it with a new 'spill'
		 * variable. */
		pool.uses.remove(argLocal);
		
		if(removeDef) {
			argDef.delete();
		}
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		calls.clear();
		constantParameterIndices.clear();
		semiConstantParameters.clear();
		processedExprs.clear();
		processMethods.clear();
		cantfix.clear();
		paramIndices.clear();
		constantParameters.clear();
				
		Map<MethodNode, List<List<Expr>>> parameterInputs = new HashMap<>();
		
		IRCallTracer tracer = new IRCallTracer(cxt) {
			@Override
			protected void visitMethod(MethodNode m) {
				
				if(tree.isJDKClass(m.owner)) {
					return;
				}
				
				boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
				
				int paramCount = Type.getArgumentTypes(m.desc).length;
				int copyCount = paramCount + (isStatic ? 0 : 1);
				List<List<Expr>> lists = new ArrayList<>(copyCount);
				
				/* Create a mapping between the actual variable table
				 * indices and the parameter indices in the method
				 * descriptor. */
				int[] idxs = new int[copyCount];
				/* int idx = 0;
				if((m.access & Opcodes.ACC_STATIC) == 0) {
					idx++;
				} */
				ControlFlowGraph cfg = cxt.getIR(m);
				BasicBlock entry = cfg.getEntries().iterator().next();
				
				/* static:
				 *  first arg = 0
				 * 
				 * non-static:
				 *  this = 0
				 *  first arg = 1*/
				int paramIndex = 0;
				for(Stmt stmt : entry) {
					if(stmt.getOpcode() == LOCAL_STORE) {
						CopyVarStmt cvs = (CopyVarStmt) stmt;
						if(cvs.isSynthetic()) {
							Local l = cvs.getVariable().getLocal();
							
							if(l.getIndex() == 0 && paramIndex != 0) {
								throw new IllegalStateException(l + " @" + paramIndex);
							} else if(l.getIndex() == 0 && paramIndex == 0) {
								if(!isStatic) {
									// System.out.println("non static skip: " + l);
									continue;
								}
							}
							
							try {
								idxs[paramIndex++] = l.getIndex();
							} catch(RuntimeException e) {
								System.out.println(m + " static: " + isStatic);
								System.out.println(l + " @" + paramIndex);
								System.err.println(cfg);
								throw e;
							}
							continue;
						}
					}
					
					break;
				}
				
				for(int j=0; j < paramCount; j++) {
					lists.add(new ArrayList<>());
				}
				
				/* for(int i=0; i < paramTypes.length; i++) {
					lists.add(new ArrayList<>());

					idxs[i] = idx;
					idx += paramTypes[i].getSize();
				} */
				paramIndices.put(m, idxs);
				
				parameterInputs.put(m, lists);
				calls.put(m, new HashSet<>());
			}
			
			@Override
			protected void processedInvocation(MethodNode caller, MethodNode callee, Expr e) {
				if(tree.isJDKClass(callee.owner)) {
					return;
				}
				
				calls.get(callee).add(e);
				
				Expr[] params;
				
				if(e.getOpcode() == INVOKE) {
					params = ((InvocationExpr) e).getParameterArguments();
				} else if(e.getOpcode() == INIT_OBJ) {
					params = ((InitialisedObjectExpr) e).getArgumentExpressions();
				} else {
					throw new UnsupportedOperationException(String.format("%s -> %s (%s)", caller, callee, e));
				}
				
				for(int i=0; i < params.length; i++) {
					parameterInputs.get(callee).get(i).add(params[i]);
				}
			}
		};
		

		for(MethodNode mn : cxt.getActiveMethods()) {
			tracer.trace(mn);
		}
		
		for(MethodNode method : cxt.getActiveMethods()) {
			ControlFlowGraph cfg = cxt.getIR(method);
			
			List<List<Expr>> argExprs = parameterInputs.get(method);

			Set<Integer> constantParameterIndexSet = new TreeSet<>(INTEGER_ORDERER);
			Set<SemiConstantParameter> semiConstantParameterSet = new HashSet<>();
			
			for(int parameterIndex=0; parameterIndex < argExprs.size(); parameterIndex++) {
				List<Expr> allParameterInputs = argExprs.get(parameterIndex);
				ConstantExpr constantVal = getConstantValue(allParameterInputs);
				
				if(constantVal != null) {
					/* We have found that the parameter at index 'i' for this
					 * exact method is always constant, so we will inline it
					 * into the method body. This does not affect the super
					 * and subclass methods at all, since we haven't changed
					 * the method descriptors yet.
					 * 
					 * I decided to do this because (1) we can legally do this,
					 * (2) even if the other methods render a descriptor
					 * change impossible, we will have (presumably) reduced the
					 * complexity of the method and (3) we would need to store
					 * this constant for later to handle it if we didn't do it
					 * here. */
					// inlineConstant(cfg, method, parameterIndex, constantVal);
					constantParameters.getNonNull(method).put(parameterIndex, constantVal);
					constantParameterIndexSet.add(parameterIndex);
				} else if(isSemiConstantSet(allParameterInputs)) {
					// System.out.printf("Multivalue param for %s @ arg%d:   %s.%n", mn, i, l);
					Set<ConstantExpr> valSet = new HashSet<>();
					for(Expr e : allParameterInputs) {
						valSet.add((ConstantExpr) e);
					}
					SemiConstantParameter param = new SemiConstantParameter(parameterIndex, valSet);
					semiConstantParameterSet.add(param);
				}
			}

			if(constantParameterIndexSet.size() > 0) {
				constantParameterIndices.put(method, constantParameterIndexSet);
			}
			
			if(constantParameterIndexSet.size() > 0) {
				semiConstantParameters.put(method, semiConstantParameterSet);
			}
		}
		
		int killedTotal = 0;
				
		for(;;) {
			int killedBeforePass = killedTotal;
			
			for(MethodNode mn : constantParameterIndices.keySet()) {
				killedTotal += fixDeadParameters(cxt, mn);
			}
			
			if(killedBeforePass == killedTotal) {
				break;
			}
		}
		
		cantfix.removeAll(processMethods);
		
		System.out.println("  can't fix:");
		for(MethodNode m : cantfix) {
			System.out.println("    " + m + ":: " + constantParameterIndices.get(m));
		}
		
		System.out.printf("Removed %d constant paramters.%n", killedTotal);
		
		return killedTotal;
	}
	
	private int fixDeadParameters(IContext cxt, MethodNode mn) {
		if(processMethods.contains(mn)) {
			return 0;
		}
		
		Set<Integer> deadIndices;
		Set<MethodNode> chain = null;
		
		ClassTree tree = cxt.getClassTree();
		
		if(!Modifier.isStatic(mn.access) && !mn.name.equals("<init>")) {
			chain = getVirtualChain(cxt, mn.owner, mn.name, mn.desc);
			
			if(!isActiveChain(chain)) {
				cantfix.addAll(chain);
				
				System.out.println();
				System.out.println("@" + mn);
				Set<ClassNode> cc = tree.getAllBranches(mn.owner, false);
				
				System.out.println(" class chain: " + cc);
				System.out.println("  inactive chain: " + chain);
				
				for(MethodNode m : chain) {
					System.out.println("  m: " + m + ", ds: " + constantParameterIndices.get(m));
				}
				
				for(MethodNode m : chain) {
					for(Expr c : calls.get(m)) {
						System.out.println("   1.  m: " + m + ", c: " + c);
					}
				}
				
				for(Expr c : calls.get(mn)) {
					System.out.println("   2.  c: " + c);
				}
				
				System.out.println();
				
				return 0;
				
			}
			
			/* find the common dead indices. */
			deadIndices = new HashSet<>();
			for(MethodNode m : chain) {
				Set<Integer> set = constantParameterIndices.get(m);
				if(set != null) {
					deadIndices.addAll(set);
				}
			}

			for(MethodNode m : chain) {
				Set<Integer> set = constantParameterIndices.get(m);
				if(set != null) {
					deadIndices.retainAll(set);
				}
			}
		} else {
			deadIndices = constantParameterIndices.get(mn);
		}
		
		String newDesc = buildDesc(Type.getArgumentTypes(mn.desc), Type.getReturnType(mn.desc), deadIndices);
		
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		if(Modifier.isStatic(mn.access)) {
			MethodNode conflict = resolver.resolveStaticCall(mn.owner.name, mn.name, newDesc);
			if(conflict != null) {
				// System.out.printf("  can't remap(s) %s because of %s.%n", mn, conflict);
				cantfix.add(mn);
				return 0;
			}
		} else {
			if(mn.name.equals("<init>")) {
				MethodNode conflict = resolver.resolveVirtualCall(mn.owner, mn.name, newDesc);
				if(conflict != null) {
					// System.out.printf("  can't remap(i) %s because of %s.%n", mn, conflict);
					cantfix.add(mn);
					return 0;
				}
			} else {
				Set<MethodNode> conflicts = getVirtualChain(cxt, mn.owner, mn.name, newDesc);
				if(conflicts.size() == 0) {
					remapMethods(chain, newDesc, deadIndices);
					return deadIndices.size();
				} else {
					// System.out.printf("  can't remap(v) %s because of %s.%n", mn, conflicts);
					cantfix.addAll(chain);
					return 0;
				}
			}
		}
		
		remapMethod(mn, newDesc, deadIndices);
		return deadIndices.size();
	}
	
	private void remapMethods(Set<MethodNode> methods, String newDesc, Set<Integer> deadSet) {
		Map<Integer, ConstantExpr> prev = null;

		for(MethodNode mn : methods) {
			Map<Integer, ConstantExpr> map = constantParameters.get(mn);
			
			if(prev != null) {
				if(!prev.equals(map)) {
					System.err.println("p: " + prev);
					System.out.println("m: " + map);
					throw new RuntimeException();
				}
			} else {
				prev = map;
			}
		}
		
		
		for(MethodNode mn : methods) {
//			System.out.println(" 2. descmap: " + mn + " to " + newDesc);
			mn.desc = newDesc;
			processMethods.add(mn);
			
			for(Expr call : calls.get(mn)) {
				if(processedExprs.contains(call)) {
					continue;
				}
//				System.out.println("   2. fixing: " + call + " to " + mn);
				processedExprs.add(call);
				patchCall(mn, call, deadSet);
			}
		}
	}
	
	private void remapMethod(MethodNode mn, String newDesc, Set<Integer> dead) {
//		System.out.println(" 1. descmap: " + mn + " to " + newDesc);
		mn.desc = newDesc;
		processMethods.add(mn);
		
		for(Expr call : calls.get(mn)) {
			/* since the callgrapher finds all
			 * the methods in a hierarchy and considers
			 * it as a single invocation, a certain
			 * invocation may be considered multiple times. */
			if(processedExprs.contains(call)) {
				continue;
			}
//			System.out.println("   1. fixing: " + call + " to " + mn);
			processedExprs.add(call);
			patchCall(mn, call, dead);
		}
	}
	
	private void patchCall(MethodNode to, Expr call, Set<Integer> dead) {
		if(call.getOpcode() == Opcode.INIT_OBJ) {
			InitialisedObjectExpr init = (InitialisedObjectExpr) call;

			CodeUnit parent = init.getParent();
			Expr[] newArgs = buildArgs(init.getArgumentExpressions(), 0, dead);
			InitialisedObjectExpr init2 = new InitialisedObjectExpr(init.getOwner(), to.desc, newArgs);

			parent.overwrite(init2, parent.indexOf(init));
		} else if(call.getOpcode() == Opcode.INVOKE) {
			InvocationExpr invoke = (InvocationExpr) call;

			CodeUnit parent = invoke.getParent();
			
			Expr[] newArgs = buildArgs(invoke.getArgumentExpressions(), invoke.getCallType() == Opcodes.INVOKESTATIC ? 0 : -1, dead);
			InvocationExpr invoke2 = new InvocationExpr(invoke.getCallType(), newArgs, invoke.getOwner(), invoke.getName(), to.desc);
			
			parent.overwrite(invoke2, parent.indexOf(invoke));
		} else {
			throw new UnsupportedOperationException(call.toString());
		}
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
	
	private Set<MethodNode> getVirtualChain(IContext cxt, ClassNode cn, String name, String desc) {		
		Set<MethodNode> set = new HashSet<>();
		for(ClassNode c : cxt.getClassTree().getAllBranches(cn, false)) {
			MethodNode mr = cxt.getInvocationResolver().resolveVirtualCall(c, name, desc);
			if(mr != null) {
				set.add(mr);
			}
		}
		return set;
	}
	
	private boolean isActiveChain(Set<MethodNode> chain) {
		if(chain.size() == 0) {
			throw new UnsupportedOperationException(chain.toString());
		} else if(chain.size() == 1) {
			return true;
		} else {
			Set<MethodNode> chain2 = new HashSet<>();
			chain2.addAll(chain);
			
			Iterator<MethodNode> it = chain2.iterator();
			while(it.hasNext()) {
				MethodNode m = it.next();
				if(constantParameterIndices.get(m) == null) {
					it.remove();
				}
			}
			
			if(chain2.size() == 0) {
				throw new UnsupportedOperationException(chain2.toString());
			} else if(chain2.size() == 1) {
				return true;
			}
			
			it = chain2.iterator();
			
			/* Find possible common dead indices that we can
			 * process. If there are none, then the chain is
			 * considered inactive. */
			Set<Integer> ret = new HashSet<>();
			
			ret.addAll(constantParameterIndices.get(it.next()));
			
			while(it.hasNext()) {
				ret.retainAll(constantParameterIndices.get(it.next()));
			}
			
			return ret.size() > 0;
		}
	}
	
	private static boolean isSemiConstantSet(List<Expr> exprs) {
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
	
	private static ConstantExpr getConstantValue(List<Expr> exprs) {
		ConstantExpr v = null;
		
		for(Expr e : exprs) {
			if(e.getOpcode() == Opcode.CONST_LOAD) {
				ConstantExpr c = (ConstantExpr) e;
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