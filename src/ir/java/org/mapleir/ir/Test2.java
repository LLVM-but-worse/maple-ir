package org.mapleir.ir;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ConstantExpression;
import org.mapleir.ir.code.expr.InitialisedObjectExpression;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.klass.ClassTree;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarContents;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.out.JarDumper;

public class Test2 {

	private static final Comparator<Integer> INTEGER_ORDERER = new Comparator<Integer>() {
		@Override
		public int compare(Integer o1, Integer o2) {
			return Integer.compareUnsigned(o1, o2);
		}
	};
	
	private static int j = 0;
	private static Map<MethodNode, ControlFlowGraph> cfgs;
	private static Map<MethodNode, Set<Expr>> calls;
	private static Map<MethodNode, List<List<Expr>>> args;
	private static Map<MethodNode, int[]> paramIndices;
	private static Set<Expr> processed;
	
	public static void main(String[] _args) throws IOException {
		cfgs = new HashMap<>();
		calls = new HashMap<>();
		args = new HashMap<>();
		paramIndices = new HashMap<>();
		processed = new HashSet<>();
		
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
		
//		for(ClassNode cn : tree.getClasses().values()) {
//			for(MethodNode m : cn.methods) {
//				
//				if(m.instructions.size() > 15000) {
//					System.out.println("this: " + m);
//				}
//				
//				if(m.toString().equals("dx.<clinit>()V")) {
//					build(tree, m);
//					
//					ControlFlowGraph cfg = cfgs.get(m);
//					LocalsPool pool = cfg.getLocals();
////					svar0_1145
////					for(Entry<VersionedLocal, Set<VarExpression>> u : pool.uses.entrySet()) {
////						System.out.println(u.getKey() + " = " + u.getValue());
////					}
////					System.out.println(cfg);
//					BoissinotDestructor.leaveSSA(cfg);
//					cfg.getLocals().realloc(cfg);
//					ControlFlowGraphDumper.dump(cfg, m);
////					InstructionPrinter.consolePrint(m);
//					
////					ControlFlowGraphBuilder.build(m);
////					System.out.println(cfg);
//				}
//			}
//		}

//		JarDumper dumper = new CompleteResolvingJarDumper(contents);
//		dumper.dump(new File("out/osb.jar"));
		
				
//		if("".equals("")) {
//			return;
//		}
		
		long time = System.nanoTime() - start;
		System.out.println("processed " + j + " methods.");
		System.out.println(" took: " + (double)((double)time/1_000_000_000L) + "s.");
		System.out.println("checking for constant params");
		
		j = 0;
		start = System.nanoTime();
		
		for(Entry<MethodNode, ControlFlowGraph> e : cfgs.entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
//			if(!mn.toString().equals("cn.f(Ljava/lang/String;ZZI)V")) {
//				continue;
//			}
//			System.out.println(mn + " isStat: " + ((mn.access & Opcodes.ACC_STATIC) != 0));
//			System.out.println(cfg);
			List<List<Expr>> argExprs = args.get(mn);

			Set<Integer> dead = new TreeSet<>(INTEGER_ORDERER);
			
			for(int i=0; i < argExprs.size(); i++) {
				List<Expr> l = argExprs.get(i);
				ConstantExpression c = getConstantValue(l);
				
				if(c != null) {
					j++;
//					System.out.println(cfg);
//					System.out.printf("Constant value for %s @ arg%d of: %s    , agreement: %d.%n", mn, i, c, l.size());
//					System.out.println("   resIdx: " + resolveIndex(mn, i));
					LocalsPool pool = cfg.getLocals();
//					System.out.println(mn);
//					System.out.println("  idxs: " + Arrays.toString(paramIndices.get(mn)) + " @ arg" + i);
					VersionedLocal vl = pool.get(resolveIndex(mn, i), 0, false);
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
//					System.out.println(cfg);
				} else if(isMultiVal(l)) {
//					System.out.printf("Multivalue param for %s @ arg%d:   %s.%n", mn, i, l);
				}
			}
			
			if(dead.size() > 0) {
				mn.desc = buildDesc(Type.getArgumentTypes(mn.desc), Type.getReturnType(mn.desc), dead);
				
				for(Expr call : calls.get(mn)) {
					/* since the callgrapher finds all
					 * the methods in a hierarchy and considers
					 * it as a single invocation, a certain
					 * invocation may be called mulitple times. */
					if(processed.contains(call)) {
						continue;
					}
					processed.add(call);
					if(call.getOpcode() == Opcode.INIT_OBJ) {
						InitialisedObjectExpression init = (InitialisedObjectExpression) call;

						CodeUnit parent = init.getParent();
//						System.out.println("init: " + init);
						Expr[] newArgs = buildArgs(init.getArgumentExpressions(), 0, dead);
						InitialisedObjectExpression init2 = new InitialisedObjectExpression(init.getType(), init.getOwner(), mn.desc, newArgs);

						parent.overwrite(init2, parent.indexOf(init));
					} else if(call.getOpcode() == Opcode.INVOKE) {
						InvocationExpression invoke = (InvocationExpression) call;
//						System.out.println("invoke: " + mn);

						CodeUnit parent = invoke.getParent();
						
						Expr[] newArgs = buildArgs(invoke.getArgumentExpressions(), invoke.getCallType() == Opcodes.INVOKESTATIC ? 0 : -1, dead);
						InvocationExpression invoke2 = new InvocationExpression(invoke.getCallType(), newArgs, invoke.getOwner(), invoke.getName(), mn.desc);
						
						parent.overwrite(invoke2, parent.indexOf(invoke));
					} else {
						throw new UnsupportedOperationException(call.toString());
					}
				}
			}

//			System.out.println(mn + "  " + dead);
			// System.out.println(cfg);

			BoissinotDestructor.leaveSSA(cfg);
			cfg.getLocals().realloc(cfg);
			ControlFlowGraphDumper.dump(cfg, mn);
			
//			try {
//			} catch(RuntimeException e1) {
//				FileWriter fw = new FileWriter(new File("C:/Users/Bibl/Desktop/error.txt"));
//				fw.write("method: " + mn.toString() + "\n");
//				LocalsPool pool = cfg.getLocals();
//				fw.write("\n\nuses::\n");
//				for(Entry<VersionedLocal, Set<VarExpression>> s : pool.uses.entrySet()) {
//					fw.write(s.getKey() +": " + s.getValue() + "\n");
//				}
//				fw.write("\n\ndefs::\n");
//				for(Entry<VersionedLocal, AbstractCopyStatement> s : pool.defs.entrySet()) {
//					fw.write(s.getKey() +": " + s.getValue() + "\n");
//				}
//				fw.write("\n\n\n\n\n\n");
//				fw.write(cfg.toString());
//				fw.close();
//				e1.printStackTrace();
//				return;
//			}
		}
		
		time = System.nanoTime() - start;
		System.out.println("processed " + j + " dead params.");
		System.out.println(" took: " + (double)((double)time/1_000_000_000L) + "s.");
		System.out.println("rewriting jar.");
		
		JarDumper dumper = new CompleteResolvingJarDumper(contents);
		dumper.dump(new File("out/osb.jar"));
	}
	
	private static Expr[] buildArgs(Expr[] oldArgs, int off, Set<Integer> dead) {
		Expr[] newArgs = new Expr[oldArgs.length - dead.size()];
//		System.out.println("oldArgs: " + Arrays.toString(oldArgs));
//		System.out.println("dead: " + dead);
		
		int j = newArgs.length - 1;
		for(int i=oldArgs.length-1; i >= 0; i--) {
			Expr e = oldArgs[i];
//			System.out.println("   i: " + i + ",  expr: " + e);
			if(!dead.contains(i + off)) {
//				System.out.println("   isdead");
				newArgs[j--] = e;
			}
			e.unlink();
		}
//		System.out.println();
		
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
	
	private static int resolveIndex(MethodNode m, int orderIdx) {
		int[] idxs = paramIndices.get(m);
		return idxs[orderIdx];
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
	
	private static void build(ClassTree tree, MethodNode m) {
		if(cfgs.containsKey(m)) {
			return;
		} else if(tree.isJDKClass(m.owner)) {
			return;
		}
		
		j++;
//		System.out.printf("#%s  [%d]%n", m, m.instructions.size());
		
		ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
		cfgs.put(m, cfg);
//		System.out.println(cfg);
		
//		if(m.toString().equals("dx.<clinit>()V")) {
//			return;
//		}
		
		
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
		
		for(BasicBlock b : cfg.vertices()) {
			for(Stmt stmt : b) {
				for(Expr c : stmt.enumerateOnlyChildren()) {
					if(c.getOpcode() == Opcode.INVOKE) {
						InvocationExpression invoke = (InvocationExpression) c;
						
						boolean isStatic = (invoke.getCallType() == Opcodes.INVOKESTATIC);
						String owner = invoke.getOwner();
						String name = invoke.getName();
						String desc = invoke.getDesc();
						
						if(isStatic) {
							MethodNode call = resolveStaticCall(tree, owner, name, desc);
							if(call != null) {
//								System.out.println(m + " scalls " + call);
//								System.out.println("          invoke: " + invoke);
								build(tree, call);

								calls.get(call).add(invoke);
								
								Expr[] params = invoke.getParameterArguments();
								for(int i=0; i < params.length; i++) {
									args.get(call).get(i).add(params[i]);
								}
							}
						} else {
							for(MethodNode vtarg : resolveVirtualCalls(tree, owner, name, desc)) {
								if(vtarg != null) {
//									System.out.println(m + " vcalls " + vtarg);
//									System.out.println("           invoke: " + invoke);
									build(tree, vtarg);
									
									calls.get(vtarg).add(invoke);
									
									Expr[] params = invoke.getParameterArguments();
									for(int i=0; i < params.length; i++) {
										args.get(vtarg).get(i).add(params[i]);
									}
								}
							}
						}
					} else if(c.getOpcode() == Opcode.INIT_OBJ) {
						InitialisedObjectExpression init = (InitialisedObjectExpression) c;
						MethodNode call = resolveVirtualInitCall(tree, init.getOwner(), init.getDesc());
						if(call != null) {
//							System.out.println(m + " icalls " + call);
							build(tree, call);
							
							calls.get(call).add(init);
							
							Expr[] params = init.getArgumentExpressions();
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
	
	private static MethodNode resolveVirtualCall(ClassTree tree, ClassNode cn, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		for(MethodNode m : cn.methods) {
			if((m.access & Opcodes.ACC_STATIC) == 0) {
				if(m.name.equals(name) && m.desc.equals(desc)) {
					set.add(m);
				}
			}
		}
		
		if(set.size() > 1) {
			throw new IllegalStateException(cn.name + "." + name + " " + desc + " => " + set);
		}
		
		if(set.size() == 1) {
			return set.iterator().next();
		} else {
			return null;
		}
	}
	
	private static Set<MethodNode> resolveVirtualCalls(ClassTree tree, String owner, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = tree.getClass(owner);
		
		if(cn != null) {
			MethodNode m = resolveVirtualCall(tree, cn, name, desc);
			if(m != null) {
				set.add(m);
			}
			
			for(ClassNode subC : tree.getSupers(cn)) {
				m = resolveVirtualCall(tree, subC, name, desc);
				if(m != null) {
					set.add(m);
				}
			}
			
			for(ClassNode subC : tree.getDelegates(cn)) {
				m = resolveVirtualCall(tree, subC, name, desc);
				if(m != null) {
					set.add(m);
				}
			}
			
			return set;
			// throw new IllegalStateException(cn.name + "." + name + " " + desc);
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