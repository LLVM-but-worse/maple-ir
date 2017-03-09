package org.mapleir.deobimpl2;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.deobimpl2.util.IPConstAnalysis;
import org.mapleir.deobimpl2.util.IPConstAnalysis.ChildVisitor;
import org.mapleir.deobimpl2.util.RenamingUtil;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ConstantParameterPass implements IPass, Opcode {
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		Map<MethodNode, Set<MethodNode>> chainMap = new HashMap<>();
		for(MethodNode mn : cxt.getCFGS().getActiveMethods()) {
			makeUpChain(cxt, mn, chainMap);
		}
		
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		Map<MethodNode, List<Set<Object>>> rawConstantParameters = new HashMap<>();
		Map<MethodNode, boolean[]> chainedNonConstant = new HashMap<>();
		Map<MethodNode, boolean[]> specificNonConstant = new HashMap<>();
		
		ChildVisitor vis = new IPConstAnalysis.ChildVisitor() {
			@Override
			public void postVisitMethod(IPConstAnalysis analysis, MethodNode m) {
				int pCount = Type.getArgumentTypes(m.desc).length;
				
				/* init map entries */
				if(!chainedNonConstant.containsKey(m)) {
					for(MethodNode assoc : chainMap.get(m)) {
						boolean[] arr = new boolean[pCount];
						chainedNonConstant.put(assoc, arr);
					}
					
					for(MethodNode assoc : chainMap.get(m)) {
						boolean[] arr = new boolean[pCount];
						specificNonConstant.put(assoc, arr);
					}
				}
				
				if(Modifier.isStatic(m.access)) {
					if(!rawConstantParameters.containsKey(m)) {
						List<Set<Object>> l = new ArrayList<>(pCount);
						rawConstantParameters.put(m, l);
						
						for(int i=0; i < pCount; i++) {
							l.add(new HashSet<>());
						}
					}
				} else {
					// TODO: cache
					for(MethodNode site : resolver.resolveVirtualCalls(m.owner.name, m.name, m.desc, true)) {
						if(!rawConstantParameters.containsKey(site)) {
							List<Set<Object>> l = new ArrayList<>(pCount);
							rawConstantParameters.put(site, l);
							
							for(int i=0; i < pCount; i++) {
								l.add(new HashSet<>());
							}
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
							rawConstantParameters.get(callee).get(i).add(((ConstantExpr) e).getConstant());
						} else {
							/* only chain callsites *can* have this input */
							for(MethodNode site : resolver.resolveVirtualCalls(callee.owner.name, callee.name, callee.desc, true)) {
								rawConstantParameters.get(site).get(i).add(((ConstantExpr) e).getConstant());
							}
						}
					} else {
						// FIXME:
						/* whole branch tainted */
						for(MethodNode associated : chainMap.get(callee)) {
							chainedNonConstant.get(associated)[i] = true;
						}
						
						/* callsites tainted */
						if(Modifier.isStatic(callee.access)) {
							specificNonConstant.get(callee)[i] = true;
						} else {
							/* only chain callsites *can* have this input */
							for(MethodNode site : resolver.resolveVirtualCalls(callee.owner.name, callee.name, callee.desc, true)) {
								specificNonConstant.get(site)[i] = true;
							}
						}
					}
				}
			}
		};
		
		IPConstAnalysis constAnalysis = IPConstAnalysis.create(cxt, vis);
		
		ApplicationClassSource app = cxt.getApplication();
		ClassTree structures = app.getStructures();
		
		/* remove all calls to library methods since we can't
		 * handle them. */
		Iterator<Entry<MethodNode, List<Set<Object>>>> it = rawConstantParameters.entrySet().iterator();
		while(it.hasNext()) {
			Entry<MethodNode, List<Set<Object>>> en = it.next();
			
			MethodNode m = en.getKey();

			if(app.isLibraryClass(m.owner.name)) {
				it.remove();
				continue;
			}
			
			// TODO: MUST BE CONVERTED TO ACCOUNT FOR DIRECT SUPERS, NOT ALL
			superFor: for(ClassNode cn : structures.getAllParents(m.owner)) {
				if(app.isLibraryClass(cn.name)) {
					for(MethodNode m1 : cn.methods) {
						if(m1.name.equals(m.name) && m1.desc.equals(m.desc)) {
							it.remove();
							break superFor;
						}
					}
				}
			}
		}
		
		/* aggregate constant parameters indices with their chained
		 * methods such that the map contains only constant parameter
		 * indices that we can actually remove while keeping a valid chain.
		 * 
		 * We do this as we can have methods from different branches that
		 * are cousin-related but have different constant parameter values.
		 * In these cases we can still inline the constants (different constants)
		 * and change the descriptions, keeping the chain. */
		
		Map<MethodNode, boolean[]> filteredConstantParameters = new HashMap<>();
		
		for(Entry<MethodNode, List<Set<Object>>> en : rawConstantParameters.entrySet()) {
			MethodNode m = en.getKey();

			List<Set<Object>> objParams = en.getValue();
			boolean[] tainted = chainedNonConstant.get(m);
			
			if(filteredConstantParameters.containsKey(m)) {
				/* note: if this method is contained in the
				 * map all of it's cousin-reachable methods
				 * must also be and furthermore the dead map
				 * for the entire chain is the same array. 
				 * 
				 * we need to now merge the current dead map
				 * with the one specifically for this method.*/
				
				boolean[] thisDeadMap = makeDeadMap(objParams, tainted);
				boolean[] prevDeadMap = filteredConstantParameters.get(m);
				
				if(thisDeadMap.length != prevDeadMap.length) {
					throw new IllegalStateException(String.format("m: %s, chain:%s, %d:%d", m, chainMap.get(m), thisDeadMap.length, prevDeadMap.length));
				}
				
				/* each dead map contains true values for an
				 * index if that index is a constant parameter. */
				for(int i=0; i < prevDeadMap.length; i++) {
					prevDeadMap[i] &= thisDeadMap[i];
				}
			} else {
				boolean[] deadParams = makeDeadMap(objParams, tainted);
				
				for(MethodNode chm : chainMap.get(m)) {
					filteredConstantParameters.put(chm, deadParams);
				}
			}
			
			ControlFlowGraph cfg = cxt.getCFGS().getIR(m);
			
			// boolean b = false;
			
			boolean[] specificTaint = specificNonConstant.get(m);
			
			for(int i=0; i < objParams.size(); i++) {
				Set<Object> set = objParams.get(i);
				
				/* since these are callsite specific
				 * constant parameters, we can inline
				 * them even if we can't eliminate the
				 * parameter for the whole chain later.
				 * 
				 * doing this here also means that when
				 * we rebuild descriptors later, if the
				 * parameter */
				
				if(!specificTaint[i] && set.size() == 1) {
					/*if(!b) {
						System.out.printf("%s (%b)%n", m, Modifier.isStatic(m.access));
						System.out.printf("   calls:%n");
						List<List<Expr>> lists = constAnalysis.getInputs(m);
						for(int j=0; j < lists.size(); j++) {
							System.out.printf("       @%d: %s%n", j, lists.get(j));
						}
						b = true;
					}
					System.out.printf("  inline: @%d/%d = %s%n", i, constAnalysis.getLocalIndex(m, i), set.iterator().next());*/
					
					inlineConstant(cfg, constAnalysis.getLocalIndex(m, i), set.iterator().next());
				}
			}
		}
		
		Map<MethodNode, String> remap = new HashMap<>();
		Set<MethodNode> toRemove = new HashSet<>();
		
		Set<Set<MethodNode>> mustRename = new HashSet<>();
		
		for(Entry<MethodNode, boolean[]> en : filteredConstantParameters.entrySet()) {
			MethodNode m = en.getKey();
			
			if(!remap.containsKey(m) && !toRemove.contains(m)) {
				boolean[] deadMap = en.getValue();
				
				boolean notSame = false;
				for(boolean b : deadMap) {
					notSame |= b;
				}
				
				if(!notSame) {
					/* eliminate all branches (same congruence class) */
					for(MethodNode n : chainMap.get(m)) {
						toRemove.add(n);
					}
					continue;
				}
				
				Type[] params = Type.getArgumentTypes(m.desc);
				Type ret = Type.getReturnType(m.desc);
				String desc = buildDesc(params, ret, deadMap);
				
				Set<MethodNode> conflicts = new HashSet<>();
				
				for(MethodNode chm : chainMap.get(m)) {
					remap.put(chm, desc);
					
					if(Modifier.isStatic(m.access)) {
						MethodNode mm = resolver.findStaticCall(chm.owner.name, chm.name, desc);
						if(mm != null) {
							conflicts.add(mm);
						}
					} else {
						if(chm.name.equals("<init>")) {
							conflicts.addAll(resolver.resolveVirtualCalls(chm.owner.name, "<init>", desc, false));
						} else {
							conflicts.addAll(resolver.getVirtualChain(m.owner, m.name, desc));
						}
					}
				}
				
				if(conflicts.size() > 0) {
					Set<MethodNode> chain = chainMap.get(m);
					
					/* rename the smallest conflict set */
//					if(chain.size() < conflicts.size()) {
//						
//					} else {
//						mustRename.add(conflicts);
//					}
					mustRename.add(chain);
				}
			}
		}
		
		remap.keySet().removeAll(toRemove);
		
		int k = RenamingUtil.numeric("aaaaa");
		Map<MethodNode, String> methodNameRemap = new HashMap<>();
		for(Set<MethodNode> set : mustRename) {
			// MethodNode first = set.iterator().next();
			// String newName = "rename_" + first.name;
			String newName = RenamingUtil.createName(k++);
			System.out.printf(" renaming %s to %s%n", set, newName);
			System.out.println("   recom " + computeChain(cxt, set.iterator().next()));
			Set<MethodNode> s2 = new HashSet<>();
			for(MethodNode m : set) {
				s2.addAll(chainMap.get(m));
			}
			
			if(!s2.equals(set)) {
				System.err.println(set);
				System.err.println(s2);
				throw new IllegalStateException();
			}
			
			for(MethodNode m : set) {
				methodNameRemap.put(m, newName);
			}
		}
		
		if(mustRename.size() > 0) {
			MethodRenamerPass.rename(cxt, methodNameRemap, false);
		}
		
		Set<MethodNode> visitedMethods = new HashSet<>();
		Set<Expr> visitedExprs = new HashSet<>();
		
		int killedTotal = 0;
		for(;;) {
			int killedBeforePass = killedTotal;
			
			for(Entry<MethodNode, String> en : remap.entrySet()) {
				MethodNode m = en.getKey();
				String newDesc = en.getValue();
				
				if(!visitedMethods.contains(m)) {
					Set<MethodNode> chain = chainMap.get(m);
					
					for(MethodNode n : chain) {
						n.desc = newDesc;
						
						for(Expr call : constAnalysis.getCallsTo(n)) {
							/* since the callgrapher finds all
							 * the methods in a hierarchy and considers
							 * it as a single invocation, a certain
							 * invocation may be considered multiple times. */
							if(visitedExprs.contains(call)) {
								continue;
							}
							
//							if(call.getOpcode() == INVOKE) {
//								InvocationExpr invoke = (InvocationExpr) call;
//								invoke.setDesc(newDesc);
//							} else if(call.getOpcode() == INIT_OBJ) {
//								InitialisedObjectExpr invoke = (InitialisedObjectExpr) call;
//								invoke.setDesc(newDesc);
//							} else {
//								throw new UnsupportedOperationException(String.format("%s -> %s", call.toString(), n));
//							}
							
							visitedExprs.add(call);
							patchCall(newDesc, call, filteredConstantParameters.get(n));
							
							killedTotal += chain.size();
						}
					}
					
					visitedMethods.addAll(chain);
				}
			}
			
			if(killedBeforePass == killedTotal) {
				break;
			}
		}
		
		System.out.printf("  removed %d constant parameters.%n", killedTotal);
		return killedTotal;
	}
	
	private void patchCall(String newDesc, Expr call, boolean[] dead) {
		if(call.getOpcode() == Opcode.INIT_OBJ) {
			InitialisedObjectExpr init = (InitialisedObjectExpr) call;

			CodeUnit parent = init.getParent();
			Expr[] newArgs = buildArgs(init.getArgumentExpressions(), false, dead);
			InitialisedObjectExpr init2 = new InitialisedObjectExpr(init.getOwner(), newDesc, newArgs);

			parent.overwrite(init2, parent.indexOf(init));
		} else if(call.getOpcode() == Opcode.INVOKE) {
			InvocationExpr invoke = (InvocationExpr) call;

			CodeUnit parent = invoke.getParent();
			
			Expr[] newArgs = buildArgs(invoke.getArgumentExpressions(), invoke.getCallType() != Opcodes.INVOKESTATIC, dead);
			InvocationExpr invoke2 = new InvocationExpr(invoke.getCallType(), newArgs, invoke.getOwner(), invoke.getName(), newDesc);
			
			parent.overwrite(invoke2, parent.indexOf(invoke));
		} else {
			throw new UnsupportedOperationException(call.toString());
		}
	}
	
	private static Expr[] buildArgs(Expr[] oldArgs, boolean obj, boolean[] dead) {
		int off = obj ? 1 : 0;
		
		if(dead.length != (oldArgs.length - off)) {
			throw new IllegalStateException();
		}
		
		List<Expr> newArgs = new ArrayList<>(oldArgs.length);
		for(int i=dead.length-1; i >= 0; i--) {
			Expr e = oldArgs[i + off];
			if(!dead[i]) {
				newArgs.add(0, e);
			}
			e.unlink();
		}
		
		if(obj) {
			Expr e = oldArgs[0];
			newArgs.add(0, e);
			e.unlink();
		}
		
		return newArgs.toArray(new Expr[0]);
	}
	
	private static boolean[] makeDeadMap(List<Set<Object>> objParams, boolean[] tainted) {
		boolean[] removable = new boolean[objParams.size()];
		
		for(int i=0; i < objParams.size(); i++) {
			Set<Object> s = objParams.get(i);
			
			removable[i] = s.size() <= 1 && !tainted[i];
		}
		
		return removable;
	}
	
	private static String buildDesc(Type[] preParams, Type ret, boolean[] dead) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		for(int i=0; i < preParams.length; i++) {
			if(!dead[i]) {
				Type t = preParams[i];
				sb.append(t.toString());
			}
		}
		sb.append(")").append(ret.toString());
		return sb.toString();
	}
	
	private void inlineConstant(ControlFlowGraph cfg, int argLocalIndex, Object o) {	
		LocalsPool pool = cfg.getLocals();
		
		VersionedLocal argLocal = pool.get(argLocalIndex, 0, false);
		AbstractCopyStmt argDef = pool.defs.get(argLocal);
		
		boolean removeDef = false;
		
		// FIXME:
		ConstantExpr c = new ConstantExpr(o, argDef.getType() == Type.BOOLEAN_TYPE ? Type.BYTE_TYPE : argDef.getType());
		
		/* demote the def from a synthetic
		 * copy to a normal one. */
		argDef.getVariable().copy();
		VarExpr dv = argDef.getVariable().copy();
		
		VersionedLocal spill = pool.makeLatestVersion(argLocal);
		dv.setLocal(spill);
		
		CopyVarStmt copy = new CopyVarStmt(dv, c.copy());
		BasicBlock b = argDef.getBlock();
		argDef.delete();
		pool.defs.remove(argLocal);
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
	
	private Set<MethodNode> computeChain(IContext cxt, MethodNode m) {
		Set<MethodNode> chain = new HashSet<>();
		chain.add(m);
		
		if(!Modifier.isStatic(m.access)) {
			if(!m.name.equals("<init>")) {
				chain.addAll(cxt.getInvocationResolver().getVirtualChain(m.owner, m.name, m.desc));
			}
		}
		
		return chain;
	}
		
	private void makeUpChain(IContext cxt, MethodNode m, Map<MethodNode, Set<MethodNode>> chainMap) {
		if(chainMap.containsKey(m)) {
			/*Set<MethodNode> chain = chainMap.get(m);
			Set<MethodNode> comp = computeChain(cxt, m);
			if(!chain.equals(comp)) {
				throw new IllegalStateException(m + "\n chain: " + chain +"\n comp: " + comp);
			}*/
		} else {
			Set<MethodNode> chain = computeChain(cxt, m);
			for(MethodNode chm : chain) {
				chainMap.put(chm, chain);
			}
		}
	}
}