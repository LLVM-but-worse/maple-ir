package org.mapleir.deobimpl2;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class MethodRenamerPass implements ICompilerPass {

	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		ClassTree tree = cxt.getClassTree();
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		Map<MethodNode, String> remapped = new HashMap<>();
		
		/* 703 is aaa. */
		int i = 703;
		
		int totalMethods = 0;
		
		for(ClassNode cn : tree.getClasses().values()) {
			totalMethods += cn.methods.size();
			
			for(MethodNode m : cn.methods) {
				if(remapped.containsKey(m)) {
					continue;
				}

				if(Modifier.isStatic(m.access)) {
					if(!m.name.equals("<clinit>")) {
						String newName = createName(i++);
						remapped.put(m, newName);
					}
				} else {
					if(!m.name.equals("<init>")) {
						Set<ClassNode> classes = tree.getAllBranches(m.owner, true);
						Set<MethodNode> methods = getVirtualMethods(cxt, classes, m.name, m.desc);
						
						if(canRename(cxt, methods)) {
							String newName = createName(i++);
							
							for(MethodNode o : methods) {
								if(remapped.containsKey(o)) {
									throw new IllegalStateException(String.format("m: %s, o: %s, ms: %s, remap[o]: %s", m, o, methods, remapped.get(o)));
								}
								remapped.put(o, newName);
							}
						} else {
							// System.out.println("Can't rename: " + methods);
						}
					}
				}
			}
		}
		
		for(ClassNode cn : tree.getClasses().values()) {
			for(MethodNode m : cn.methods) {
				
				ControlFlowGraph cfg = cxt.getIR(m);
				
				for(BasicBlock b : cfg.vertices()) {
					for(Stmt stmt : b) {
						for(Expr e : stmt.enumerateOnlyChildren()) {
							
							if(e.getOpcode() == Opcode.INVOKE) {
								InvocationExpression invoke = (InvocationExpression) e;

								if(invoke.getInstanceExpression() == null) {
									MethodNode site = resolver.resolveStaticCall(invoke.getOwner(), invoke.getName(), invoke.getDesc());
									
									if(site != null) {
										if(remapped.containsKey(site)) {
											invoke.setName(remapped.get(site));
										} else {
											if(!tree.isJDKClass(tree.findClass(invoke.getOwner()))) {
												System.err.println("Invalid site(s): " + invoke);
											}
										}
									} else {
										if(!tree.isJDKClass(tree.findClass(invoke.getOwner()))) {
											System.err.println("Can't resolve(s) " + invoke);
										}
									}
								} else {
									Set<MethodNode> sites = resolver.resolveVirtualCalls(invoke.getOwner(), invoke.getName(), invoke.getDesc());
									
									if(sites.size() > 0) {
										/* all of the sites must be linked by the same name,
										 * so we can use any to find the new name. */
										MethodNode site = sites.iterator().next();
										if(remapped.containsKey(site)) {
											invoke.setName(remapped.get(site));
										} else {
											if(!site.name.equals("<init>") && canRename(cxt, sites)) {
												System.err.println("Invalid site(v): " + invoke + ", " + sites);
											}
										}
									} else {
										if(!tree.isJDKClass(tree.findClass(invoke.getOwner()))) {
											System.err.println("Can't resolve(v) " + invoke + ", owner: " + invoke.getOwner());
										}
									}
								}
							}
						}
					}
				}
			}
		}
		
		/* Rename the methods after as we need to resolve
		 * them using the old names during the invocation 
		 * analysis above. */
		for(Entry<MethodNode, String> e : remapped.entrySet()) {
			e.getKey().name = e.getValue();
		}
		
		System.out.printf("  Rename %d/%d methods.%n", remapped.size(), totalMethods);
	}
	
	private boolean canRename(IContext cxt, Set<MethodNode> methods) {
		for(MethodNode m : methods) {
			if(cxt.getClassTree().isJDKClass(m.owner)) {
				/* inherited from runtime class */
				return false;
			}
		}
		return true;
	}
	
	private Set<MethodNode> getVirtualMethods(IContext cxt, Set<ClassNode> classes, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		for(ClassNode cn : classes) {
			for(MethodNode m : cn.methods) {
				if(!Modifier.isStatic(m.access)) {
					if(m.name.equals(name) && m.desc.equals(desc)) {
						set.add(m);
					}
				}
			}
		}
		return set;
	}
	
	private static String createName(int n) {
		char[] buf = new char[(int) Math.floor(Math.log(25 * (n + 1)) / Math.log(26))];
		for (int i = buf.length - 1; i >= 0; i--) {
			buf[i] = (char) ('a' + (--n) % 26);
			n /= 26;
		}
		return new String(buf);
	}
}