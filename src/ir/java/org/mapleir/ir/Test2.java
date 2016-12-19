package org.mapleir.ir;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.ConstantExpression;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.InitialisedObjectExpression;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.klass.ClassTree;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarContents;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.out.JarDumper;

import jdk.internal.org.objectweb.asm.Type;

public class Test2 {

	private static int i = 0;
	private static Map<MethodNode, ControlFlowGraph> cfgs;
	private static Map<MethodNode, List<List<Expression>>> args;
	private static Map<MethodNode, int[]> paramIndices;
	
	public static void main(String[] _args) throws IOException {
		cfgs = new HashMap<>();
		args = new HashMap<>();
		paramIndices = new HashMap<>();
		
		JarInfo jar = new JarInfo(new File("res/gamepack107.jar"));
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(jar);
		dl.download();
		JarContents<ClassNode> contents = dl.getJarContents();
		ClassTree tree = new ClassTree(contents.getClassContents());
		
		System.out.println("davai");
		Set<MethodNode> entrySet = findEntries(tree);
		long start = System.nanoTime();
		for(MethodNode m : entrySet) {
			build(tree, m);
		}
		long time = System.nanoTime() - start;
		System.out.println("processed " + i + " methods.");
		System.out.println(" took: " + (double)((double)time/1_000_000_000L) + "s.");
		System.out.println("checking for constant params");
		
		for(Entry<MethodNode, ControlFlowGraph> e : cfgs.entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
			if(!mn.toString().equals("co.p(DII)V")) {
				continue;
			}
//			System.out.println(mn + " isStat: " + ((mn.access & Opcodes.ACC_STATIC) != 0));
//			System.out.println(cfg);
			List<List<Expression>> argExprs = args.get(mn);
			
			for(int i=0; i < argExprs.size(); i++) {
				List<Expression> l = argExprs.get(i);
				ConstantExpression c = getConstantValue(l);
				
				if(c != null) {
					System.out.printf("Constant value for %s @ arg%d of: %s    , agreement: %d.%n", mn, i, c, l.size());
//					System.out.println("   resIdx: " + resolveIndex(mn, i));
					LocalsPool pool = cfg.getLocals();
					VersionedLocal vl = pool.get(resolveIndex(mn, i), 0, false);
					AbstractCopyStatement def = pool.defs.get(vl);
					
					boolean removeDef = true;
					
					/* demote the def from a synthetic
					 * copy to a normal one. */
					CopyVarStatement copy = new CopyVarStatement(def.getVariable().copy(), c.copy());
					BasicBlock b = def.getBlock();
					def.delete();
					def = copy;
					b.add(copy);
//					int index = b.indexOf(copy);
//					if(b.size() > 1 && !((CopyVarStatement) b.get(index - 1)).isSynthetic()) {
//						System.err.println(cfg);
//						throw new RuntimeException(mn.toString() + ", " + index + "::  " + b.get(index - 1));
//					}
					pool.defs.put(vl, copy);
					
					Iterator<VarExpression> it = pool.uses.get(vl).iterator();
					while(it.hasNext()) {
						VarExpression v = it.next();
						
						if(v.getParent() == null) {
							/* the use is in a phi, we can't
							 * remove the def. */
							removeDef = false;
						} else {
							Statement par = v.getParent();
							par.overwrite(c.copy(), par.indexOf(v));
							it.remove();
						}
					}
					
					if(removeDef) {
						def.delete();
						pool.defs.remove(vl);
						pool.uses.remove(vl);
					}
				} else if(isMultiVal(l)) {
					System.out.printf("Multivalue param for %s @ arg%d:   %s.%n", mn, i, l);
				}
			}
			
//			System.out.println(cfg);
			
			BoissinotDestructor.leaveSSA(cfg);
			cfg.getLocals().realloc(cfg);
			ControlFlowGraphDumper.dump(cfg, mn);
		}
		
		JarDumper dumper = new CompleteResolvingJarDumper(contents);
		dumper.dump(new File("out/osb.jar"));
	}
	
	private static int resolveIndex(MethodNode m, int orderIdx) {
		int[] idxs = paramIndices.get(m);
		return idxs[orderIdx];
	}
	
	private static boolean isMultiVal(List<Expression> exprs) {
		if(exprs.size() <= 1) {
			return false;
		}
		
		for(Expression e : exprs) {
			if(e.getOpcode() != Opcode.CONST_LOAD) {
				return false;
			}
		}
		return true;
	}
	
	private static ConstantExpression getConstantValue(List<Expression> exprs) {
		ConstantExpression v = null;
		
		for(Expression e : exprs) {
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
	
	private static void build(ClassTree tree, MethodNode m) {
		if(cfgs.containsKey(m)) {
			return;
		}
		
		i++;
		// System.out.printf("%s#%d: %s  [%d]%n", pre, i++, m, m.instructions.size());
		
		ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
		cfgs.put(m, cfg);
//		System.out.println(cfg);
		
		
		Type[] paramTypes = Type.getArgumentTypes(m.desc);
		List<List<Expression>> lists = new ArrayList<>(paramTypes.length);
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
		
		for(BasicBlock b : cfg.vertices()) {
			for(Statement stmt : b) {
				for(Statement c : stmt.enumerate()) {
					if(c.getOpcode() == Opcode.INVOKE) {
						InvocationExpression invoke = (InvocationExpression) c;
						
						boolean isStatic = (invoke.getCallType() == Opcodes.INVOKESTATIC);
						String owner = invoke.getOwner();
						String name = invoke.getName();
						String desc = invoke.getDesc();
						
						if(isStatic) {
							MethodNode call = resolveStaticCall(tree, owner, name, desc);
							if(call != null) {
								build(tree, call);

								Expression[] params = invoke.getParameterArguments();
								for(int i=0; i < params.length; i++) {
									args.get(call).get(i).add(params[i]);
								}
							}
						} else {
							for(MethodNode vtarg : resolveVirtualCalls(tree, owner, name, desc)) {
								build(tree, vtarg);
								
								Expression[] params = invoke.getParameterArguments();
								for(int i=0; i < params.length; i++) {
									args.get(vtarg).get(i).add(params[i]);
								}
							}
						}
					} else if(c.getOpcode() == Opcode.INIT_OBJ) {
						InitialisedObjectExpression init = (InitialisedObjectExpression) c;
						MethodNode call = resolveVirtualInitCall(tree, init.getOwner(), init.getDesc());
						if(call != null) {
							build(tree, call);
							
							Expression[] params = init.getArgumentExpressions();
							for(int i=0; i < params.length; i++) {
								args.get(call).get(i).add(params[i]);
							}
						}
					}
				}
			}
		}
	}
	
	private static MethodNode resolveVirtualInitCall(ClassTree tree, String owner, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = tree.getClass(owner);
		
		if(cn != null) {
			for(MethodNode m : cn.methods) {
				if((m.access & Opcodes.ACC_STATIC) == 0) {
					if(m.name.equals("<init>") && m.desc.equals(desc)) {
						set.add(m);
					}
				}
			}
			
			if(set.size() == 1) {
				return set.iterator().next();
			} else {
				throw new IllegalStateException(set.toString());
			}
		} else {
			return null;
		}
	}
	
	private static Set<MethodNode> resolveVirtualCalls(ClassTree tree, ClassNode cn, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		for(MethodNode m : cn.methods) {
			if((m.access & Opcodes.ACC_STATIC) == 0) {
				if(m.name.equals(name) && m.desc.equals(desc)) {
					set.add(m);
				}
			}
		}
		return set;
	}
	
	private static Set<MethodNode> resolveVirtualCalls(ClassTree tree, String owner, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = tree.getClass(owner);
		
		if(cn != null) {
			set.addAll(resolveVirtualCalls(tree, cn, name, desc));
			
			for(ClassNode superC : tree.getSupers(cn)) {
				set.addAll(resolveVirtualCalls(tree, superC, name, desc));
			}
			
			for(ClassNode subC : tree.getDelegates(cn)) {
				set.addAll(resolveVirtualCalls(tree, subC, name, desc));
			}
		}
		
		return set;
	}
	
	private static MethodNode resolveStaticCall(ClassTree tree, String owner, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = tree.getClass(owner);
		
		if(cn != null) {
			for(MethodNode m : cn.methods) {
				if((m.access & Opcodes.ACC_STATIC) != 0) {
					if(m.name.equals(name) && m.desc.equals(desc)) {
						set.add(m);
					}
				}
			}
			
			if(set.size() == 0) {
				return resolveStaticCall(tree, cn.superName, name, desc);
			} else if(set.size() == 1) {
				return set.iterator().next();
			} else {
				throw new IllegalStateException(owner + "." + name + " " + desc + ",   " + set.toString());
			}
		} else {
			return null;
		}
	}
	
	private static Set<MethodNode> findEntries(ClassTree tree) {
		Set<MethodNode> set = new HashSet<>();
		for(ClassNode cn : tree.getClasses().values())  {
			for(MethodNode m : cn.methods) {
				if(m.name.length() > 2) {
					set.add(m);
				}
			}
		}
		return set;
	}
	
//	for(AbstractInsnNode ain : m.instructions.toArray()) {
//	if(ain.type() == AbstractInsnNode.METHOD_INSN) {
//		MethodInsnNode min = (MethodInsnNode) ain;
//		
//		if(min.opcode() == Opcodes.INVOKESPECIAL) {
//			MethodNode call = resolveVirtualInitCall(tree, min.owner, min.desc);
//			if(call != null) {
//				build(tree, call, pre + "   ");
//			}
//		} else if(min.opcode() == Opcodes.INVOKESTATIC) {
//			MethodNode call = resolveStaticCall(tree, min.owner, min.name, min.desc);
//			if(call != null) {
//				build(tree, call, pre + "   ");
//			}
//		} else {
//			for(MethodNode call : resolveVirtualCalls(tree, min.owner, min.name, min.desc)) {
//				build(tree, call, pre + "   ");
//			}
//		}
//	}
//}
}