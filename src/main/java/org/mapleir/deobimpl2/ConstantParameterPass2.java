package org.mapleir.deobimpl2;

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
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.mapleir.stdlib.klass.library.ApplicationClassSource;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ConstantParameterPass2 implements IPass, Opcode {
	
	private final Map<MethodNode, Set<Expr>> calls;
	private final Map<MethodNode, int[]> paramIndices;
	
	private final IndexMap<Set<ConstantExpr>> semiConstantParameters;
	private final IndexMap<ConstantExpr> constantParameters;
	private final Set<MethodNode> processMethods;
	private final Set<Expr> processedExprs;
	private final Set<MethodNode> cantfix;

	public ConstantParameterPass2() {
		calls = new HashMap<>();
		paramIndices = new HashMap<>();
		
		semiConstantParameters = new IndexMap<>(new SetCreator<>());
		constantParameters = new IndexMap<>();
		processMethods = new HashSet<>();
		processedExprs = new HashSet<>();
		cantfix = new HashSet<>();
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		calls.clear();
		paramIndices.clear();
		semiConstantParameters.clear();
		constantParameters.clear();
		processMethods.clear();
		processedExprs.clear();
		cantfix.clear();
		
		ApplicationClassSource source = cxt.getApplication();
		
		NullPermeableHashMap<MethodNode, Set<Integer>> nonConstant = new NullPermeableHashMap<>(new SetCreator<>());
		
		Map<MethodNode, List<List<Expr>>> parameterInputs = new HashMap<>();
		
		IRCallTracer tracer = new IRCallTracer(cxt) {
			@Override
			protected void visitMethod(MethodNode m) {
				if(source.isLibraryClass(m.owner.name)) {
					return;
				}
				
				boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
				
				int paramCount = Type.getArgumentTypes(m.desc).length;
				int off = (isStatic ? 0 : 1);
				int synthCount = paramCount + off;
				List<List<Expr>> lists = new ArrayList<>(synthCount);
				
				/* Create a mapping between the actual variable table
				 * indices and the parameter indices in the method
				 * descriptor. */
				int[] idxs = new int[synthCount];
				
				ControlFlowGraph cfg = cxt.getIR(m);
				BasicBlock entry = cfg.getEntries().iterator().next();
				
				/* static:
				 *  first arg = 0
				 *
				 * non-static:
				 *  this = 0
				 *  first arg = 1*/
//				int idx = 0;
				int paramIndex = 0;
				for(Stmt stmt : entry) {
					if(stmt.getOpcode() == LOCAL_STORE) {
						CopyVarStmt cvs = (CopyVarStmt) stmt;
						if(cvs.isSynthetic()) {
							int varIndex = cvs.getVariable().getLocal().getIndex();
							if (!isStatic && varIndex == 0)
								continue;
							idxs[paramIndex++] = varIndex;
//							if(l.getIndex() == 0 && paramIndex != 0) {
//								throw new IllegalStateException(l + " @" + paramIndex);
//							} else if(l.getIndex() == 0 && paramIndex == 0) {
//								if(!isStatic) {
//									continue;
//								}
//							}
//
//							try {
//								idxs[paramIndex + off] = l.getIndex();
//							} catch(RuntimeException e) {
//								System.out.println(m + " static: " + isStatic);
//								System.out.println(l + " @" + paramIndex);
//								System.err.println(cfg);
//								throw e;
//							}
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
				if(source.isLibraryClass(callee.owner.name)) {
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
		
		
		Map<MethodNode, Set<MethodNode>> chainMap = new HashMap<>();
		
		for(MethodNode mn : cxt.getActiveMethods()) {
			tracer.trace(mn);
			makeUpChain(cxt, mn, chainMap);
		}
		
		for(MethodNode method : cxt.getActiveMethods()) {		
			List<List<Expr>> argExprs = parameterInputs.get(method);
			
			for(int parameterIndex=0; parameterIndex < argExprs.size(); parameterIndex++) {
				List<Expr> allParameterInputs = argExprs.get(parameterIndex);
				ConstantExpr constantVal = getConstantValue(allParameterInputs);
				
				if(constantVal != null) {
					constantParameters.getNonNull(method).put(parameterIndex, constantVal);
				} else if(isSemiConstantSet(allParameterInputs)) {
					Map<Integer, Set<ConstantExpr>> map = semiConstantParameters.getNonNull(method);
					
					if(map.containsKey(parameterIndex)) {
						throw new IllegalStateException(map + " : " + parameterIndex + " for " + method);
					} else {
						Set<ConstantExpr> consts = new HashSet<>();
						for(Expr c : allParameterInputs) {
							consts.add((ConstantExpr) c);
						}
						map.put(parameterIndex, consts);
					}
				} else {
					nonConstant.getNonNull(method).add(parameterIndex);
				}
			}
		}
		
//		Set<MethodNode> invalid = new HashSet<>();
		IndexMap<Set<ConstantExpr>> upconsts = new IndexMap<>();
		Map<MethodNode, Set<Integer>> killed = new HashMap<>();
		
		for(Entry<MethodNode, NullPermeableHashMap<Integer, ConstantExpr>> e : constantParameters.entrySet()) {
			MethodNode method = e.getKey();
			
//			System.out.println("checking " + method + " " + Modifier.isStatic(method.access));
//			System.out.println(" has: " + chainMap.get(method));
			
			Set<Integer> nonConst = new HashSet<>();
			NullPermeableHashMap<Integer, Set<ConstantExpr>> vals = new NullPermeableHashMap<>(new SetCreator<>());
			NullPermeableHashMap<Integer, HashSet<Object>> objVals = new NullPermeableHashMap<>(HashSet::new);
			
			for(MethodNode m : chainMap.get(e.getKey())) {
				Set<Integer> nonConstParams = nonConstant.get(m);
				
				if(nonConstParams != null) {
					nonConst.addAll(nonConstParams);
				}
				
				Map<Integer, ConstantExpr> map = constantParameters.get(m);
				
				if(map == null) {
					continue;
				}

				for(Entry<Integer, ConstantExpr> e2 : map.entrySet()) {
					if (objVals.getNonNull(e2.getKey()).add(e2.getValue().getConstant()))
						vals.getNonNull(e2.getKey()).add(e2.getValue());
				}
			}

			for(Integer i : nonConst){
				vals.remove(i);
			}
			
			killed.put(method, nonConst);
			upconsts.getNonNull(method).putAll(vals);
		}
		
		for(Entry<MethodNode, NullPermeableHashMap<Integer, Set<ConstantExpr>>> e : upconsts.entrySet()) {
			Set<Integer> kill = killed.get(e.getKey());
			
			for(Integer i : kill) {
				e.getValue().remove(i);
			}
		}
		
		
		int killedTotal = 0;
		for(Entry<MethodNode, NullPermeableHashMap<Integer, Set<ConstantExpr>>> e : upconsts.entrySet()) {
			MethodNode m = e.getKey();
			ControlFlowGraph cfg = cxt.getIR(m);
			
			for(Entry<Integer, Set<ConstantExpr>> e2 : e.getValue().entrySet()) {
				if(e2.getValue().size() != 1) {
					System.err.println(" ?? " + m + " -> " + e2.getValue());
					throw new RuntimeException();
				}
				
				String newDesc = buildDesc(Type.getArgumentTypes(m.desc), Type.getReturnType(m.desc), e.getValue().keySet());
				boolean killSynth = checkConflicts(cxt, m, newDesc);
				inlineConstant(cfg, m, e2.getKey(), e2.getValue().iterator().next(), killSynth);
			}
		}
		
		
		for(;;) {
			int killedBeforePass = killedTotal;
			
			for(Entry<MethodNode, NullPermeableHashMap<Integer, Set<ConstantExpr>>> e : upconsts.entrySet()) {
				MethodNode mn = e.getKey();
				killedTotal += fixDeadParameters(cxt, mn, getVirtualChain(cxt, mn.owner, mn.name, mn.desc), e.getValue().keySet());
			}
			
			if(killedBeforePass == killedTotal) {
				break;
			}
		}
		
		System.out.printf("  removed %d constant paramters.%n", killedTotal);
		
		return killedTotal;
	}
	
	private int fixDeadParameters(IContext cxt, MethodNode mn, Set<MethodNode> chain, Set<Integer> deadIndices) {
		if(processMethods.contains(mn)) {
			return 0;
		}

		String newDesc = buildDesc(Type.getArgumentTypes(mn.desc), Type.getReturnType(mn.desc), deadIndices);
		
//		System.out.println(mn + " -> " + newDesc);
		
		boolean isStatic = Modifier.isStatic(mn.access);
		if (checkConflicts(cxt, mn, newDesc)) {
			if (!isStatic && !mn.name.equals("<init>")) {
				chain.add(mn);
				remapMethods(chain, newDesc, deadIndices);
			} else {
				remapMethod(mn, newDesc, deadIndices);
			}
			return deadIndices.size();
		} else {
//			if (!isStatic && !mn.name.equals("<init>")) {
//				cantfix.addAll(chain);
//			}
			if(chain != null) {
				cantfix.addAll(chain);
			}
			cantfix.add(mn);
			return 0;
		}
	}
	
	private boolean checkConflicts(IContext cxt, MethodNode mn, String newDesc) {
		InvocationResolver resolver = cxt.getInvocationResolver();
		if(Modifier.isStatic(mn.access)) {
			MethodNode conflict = resolver.findStaticCall(mn.owner.name, mn.name, newDesc);
			if(conflict != null) {
//				System.out.printf("  can't remap(s) %s because of %s.%n", mn, conflict);
				return false;
			}
		} else {
			if(mn.name.equals("<init>")) {
				MethodNode conflict = resolver.findVirtualCall(mn.owner, mn.name, newDesc);
				if(conflict != null) {
//					System.out.printf("  can't remap(i) %s because of %s.%n", mn, conflict);
					return false;
				}
			} else {
				Set<MethodNode> conflicts = getVirtualChain(cxt, mn.owner, mn.name, newDesc);
				if(conflicts.size() > 0) {
//					System.out.printf("  can't remap(v) %s because of %s.%n", mn, conflicts);
					return false;
				}
			}
		}
		return true;
	}
	
	private void remapMethods(Set<MethodNode> methods, String newDesc, Set<Integer> deadSet) {
		Map<Integer, ConstantExpr> prev = null;

		for(MethodNode mn : methods) {
			Map<Integer, ConstantExpr> map = constantParameters.get(mn);
			
			if(prev != null) {
				if(map != null && !prev.equals(map)) {
					System.err.println("p: " + prev);
					System.err.println("m: " + map);
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
	
	private Set<MethodNode> getVirtualChain(IContext cxt, ClassNode cn, String name, String desc) {		
		Set<MethodNode> set = new HashSet<>();
		for(ClassNode c : cxt.getApplication().getStructures().getVirtualReachableBranches(cn)) {
			MethodNode mr = cxt.getInvocationResolver().findVirtualCall(c, name, desc);
			if(mr != null) {
				set.add(mr);
			}
		}
		return set;
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
	
	private void inlineConstant(ControlFlowGraph cfg, MethodNode mn, int parameterIndex, ConstantExpr c, boolean killSynth) {
		boolean isStatic = Modifier.isStatic(mn.access);
//		System.out.println(mn + " " + isStatic + " @" + parameterIndex);
//		System.out.println("  c: " + c);
		
		Type[] params = Type.getArgumentTypes(mn.desc);
		LocalsPool pool = cfg.getLocals();
		int argLocalIndex = paramIndices.get(mn)[parameterIndex];
		
		VersionedLocal argLocal = pool.get(argLocalIndex, 0, false);
		AbstractCopyStmt argDef = pool.defs.get(argLocal);
		
		if(!argDef.getType().equals(params[parameterIndex])) {
			System.err.println(cfg);
			System.err.println(mn.desc +" @" +parameterIndex +"   (" + isStatic + ")");
			System.err.println("  argindex: " + argLocalIndex);
			System.err.println("  " + Arrays.toString(params));
			System.err.println("  " + argDef);
			System.err.println("  " + Arrays.toString(paramIndices.get(mn)));
			System.err.println("  " + argDef.getType() + " vs " + params[parameterIndex]);
			throw new RuntimeException();
		}
		boolean removeDef = true;
		
		/* demote the def from a synthetic
		 * copy to a normal one. */
		try {
			argDef.getVariable().copy();
		} catch(RuntimeException e) {
			System.err.println(cfg);
			System.err.println(isStatic);
			System.err.println(argLocal + " : " + argDef);
			System.err.println("Param index: " + parameterIndex);
			System.err.println("Arg index: " + argLocalIndex);
			System.err.println(c);
			e.printStackTrace();
			throw e;
		}
		VarExpr dv = argDef.getVariable().copy();
		
		VersionedLocal spill = pool.makeLatestVersion(argLocal);
		dv.setLocal(spill);
		
		CopyVarStmt copy = new CopyVarStmt(dv, c.copy());
		BasicBlock b = argDef.getBlock();
		if (killSynth) {
			argDef.delete();
			pool.defs.remove(argLocal);
		}
		argDef = copy;
		b.add(copy);
		pool.defs.put(spill, copy);
		
		Set<VarExpr> spillUses = new HashSet<>();
		pool.uses.put(spill, spillUses);
		
		/* Replace each use of the parameter variable with
		 * the constant. */
		Iterator<VarExpr> it = pool.uses.getNonNull(argLocal).iterator();
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
		
	private void makeUpChain(IContext cxt, MethodNode m1, Map<MethodNode, Set<MethodNode>> chainMap) {		
		Set<MethodNode> chain = new HashSet<>();
		chain.add(m1);
		
		if(!Modifier.isStatic(m1.access)) {
			if(!m1.name.equals("<init>")) {
				chain.addAll(getVirtualChain(cxt, m1.owner, m1.name, m1.desc));
			}
		}
		
		chainMap.put(m1, chain);
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
	
	@SuppressWarnings("serial")
	public static class IndexMap<N> extends NullPermeableHashMap<MethodNode, NullPermeableHashMap<Integer, N>> {
		IndexMap() {
			super(NullPermeableHashMap::new);
		}
		
		IndexMap(ValueCreator<N> c) {
			super(() -> new NullPermeableHashMap<>(c));
		}
	}
}