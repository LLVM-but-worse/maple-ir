package org.mapleir.ir;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.InitialisedObjectExpression;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.klass.ClassTree;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarContents;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

public class Test2 {

	private static int i = 0;
	private static Map<MethodNode, ControlFlowGraph> cfgs;

	public static void main(String[] args) throws IOException {
		cfgs = new HashMap<>();
		
		JarInfo jar = new JarInfo(new File("res/gamepack107.jar"));
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(jar);
		dl.download();
		JarContents<ClassNode> contents = dl.getJarContents();
		ClassTree tree = new ClassTree(contents.getClassContents());
		
		Set<MethodNode> entrySet = findEntries(tree);
		for(MethodNode m : entrySet) {
			build(tree, m, "");
		}
		
		int k = 0;
		for(ClassNode cn : tree.getClasses().values()) {
			for(MethodNode m : cn.methods) {
				k++;
			}
		}
		
		System.out.println("k: " + k);
	}
	
	private static void build(ClassTree tree, MethodNode m, String pre) {
		if(cfgs.containsKey(m)) {
			return;
		}
		
		System.out.printf("%s#%d: %s  [%d]%n", pre, i++, m, m.instructions.size());
		ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
		cfgs.put(m, cfg);
		
//		for(AbstractInsnNode ain : m.instructions.toArray()) {
//			if(ain.type() == AbstractInsnNode.METHOD_INSN) {
//				MethodInsnNode min = (MethodInsnNode) ain;
//				
//				if(min.opcode() == Opcodes.INVOKESPECIAL) {
//					MethodNode call = resolveVirtualInitCall(tree, min.owner, min.desc);
//					if(call != null) {
//						build(tree, call, pre + "   ");
//					}
//				} else if(min.opcode() == Opcodes.INVOKESTATIC) {
//					MethodNode call = resolveStaticCall(tree, min.owner, min.name, min.desc);
//					if(call != null) {
//						build(tree, call, pre + "   ");
//					}
//				} else {
//					for(MethodNode call : resolveVirtualCalls(tree, min.owner, min.name, min.desc)) {
//						build(tree, call, pre + "   ");
//					}
//				}
//			}
//		}
		
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
								build(tree, call, pre + "   ");
							}
						} else {
							for(MethodNode vtarg : resolveVirtualCalls(tree, owner, name, desc)) {
								build(tree, vtarg, pre + "   ");
							}
						}
					} else if(c.getOpcode() == Opcode.INIT_OBJ) {
						InitialisedObjectExpression init = (InitialisedObjectExpression) c;
						MethodNode call = resolveVirtualInitCall(tree, init.getOwner(), init.getDesc());
						if(call != null) {
							build(tree, call, pre + "   ");
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
}