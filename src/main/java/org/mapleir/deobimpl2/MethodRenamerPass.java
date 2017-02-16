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
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class MethodRenamerPass implements IPass {

	@Override
	public boolean isIncremental() {
		return false;
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		ClassTree tree = cxt.getClassTree();
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		Map<MethodNode, String> remapped = new HashMap<>();

		int totalMethods = 0;
		
		for(ClassNode cn : tree.getClasses().values()) {
			totalMethods += cn.methods.size();
		}
		
		int i = RenamingUtil.computeMinimum(totalMethods);
		
		for(ClassNode cn : tree.getClasses().values()) {
			for(MethodNode m : cn.methods) {
				if(remapped.containsKey(m)) {
					continue;
				}
				
				if(Modifier.isStatic(m.access)) {
					if(!m.name.equals("<clinit>")) {
						String newName = RenamingUtil.createName(i++);
						remapped.put(m, newName);
					}
				} else {
					if(!m.name.equals("<init>")) {
						Set<ClassNode> classes = tree.getAllBranches(m.owner, true);
						Set<MethodNode> methods = getVirtualMethods(cxt, classes, m.name, m.desc);
						
						if(canRename(cxt, methods)) {
							String newName = RenamingUtil.createName(i++);
							
							for(MethodNode o : methods) {
								if(remapped.containsKey(o)) {
									throw new IllegalStateException(String.format("m: %s, o: %s, ms: %s, remap[o]: %s", m, o, methods, remapped.get(o)));
								}
								remapped.put(o, newName);
							}
						} else {
							System.out.println("  can't rename: " + methods);
						}
					}
				}
			}
		}
		
		for(ClassNode cn : tree.getClasses().values()) {
			{
				if(cn.outerMethod != null) {
//					ClassNode owner = tree.getClass(cn.outerClass);
					System.out.println("Outer: " + cn.outerClass + "." + cn.outerMethod + " " + cn.outerMethodDesc);
					cn.outerClass = null;
					cn.outerMethod = null;
					cn.outerMethodDesc = null;
					//					System.out.println(owner.name);
//					do {
//						for(MethodNode m : owner.methods) {
//							System.out.println(m);
//							if(m.name.equals(cn.outerMethod) && m.desc.equals(cn.outerMethodDesc)) {
//								System.out.println("m: " + m);
//							}
//						}
//						owner = tree.getClass(owner.superName);
//						System.out.println(cn.superName);
//						System.out.println(owner);
//					} while(owner != null);
				}
			}
			
			for(MethodNode m : cn.methods) {
				
				ControlFlowGraph cfg = cxt.getIR(m);
				
				for(BasicBlock b : cfg.vertices()) {
					for(Stmt stmt : b) {
						for(Expr e : stmt.enumerateOnlyChildren()) {
							
							if(e.getOpcode() == Opcode.INVOKE) {
								InvocationExpr invoke = (InvocationExpr) e;
								
								if(invoke.getOwner().startsWith("[")) {
									System.err.println("  ignore array object invoke: " + invoke + ", owner: " + invoke.getOwner());
									continue;
								}
								
								if(invoke.getInstanceExpression() == null) {
									MethodNode site = resolver.resolveStaticCall(invoke.getOwner(), invoke.getName(), invoke.getDesc());
									
									if(site != null) {
										if(remapped.containsKey(site)) {
											invoke.setName(remapped.get(site));
										} else {
											if(mustMark(tree, invoke.getOwner())) {
												System.err.println("  invalid site(s): " + invoke);
											}
										}
									} else {
										if(mustMark(tree, invoke.getOwner())) {
											System.err.println("  can't resolve(s) " + invoke);
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
												System.err.println("  invalid site(v): " + invoke + ", " + sites);
											}
										}
									} else {
										if(mustMark(tree, invoke.getOwner())) {
											System.err.println("  can't resolve(v) " + invoke + ", owner: " + invoke.getOwner());
										}
									}
								}
							} else if(e.getOpcode() == Opcode.DYNAMIC_INVOKE) {
								throw new UnsupportedOperationException();
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
		
		System.out.printf("  Remapped %d/%d methods.%n", remapped.size(), totalMethods);
		
		return remapped.size();
	}
	
	private boolean mustMark(ClassTree tree, String owner) {
		ClassNode cn = tree.findClass(owner);
		return cn == null || !tree.isJDKClass(cn);
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
}