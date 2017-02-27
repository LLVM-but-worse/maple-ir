package org.mapleir.deobimpl2;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.deobimpl2.util.RenamingUtil;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.mapleir.stdlib.klass.library.ApplicationClassSource;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class MethodRenamerPass implements IPass {

	@Override
	public boolean isSingletonPass() {
		return false;
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		ApplicationClassSource source = cxt.getApplication();
		
		Map<MethodNode, String> remapped = new HashMap<>();

		int totalMethods = 0;
		
		for(ClassNode cn : source.iterate()) {
			totalMethods += cn.methods.size();
		}
		
		int i = RenamingUtil.computeMinimum(totalMethods);
		
		for(ClassNode cn : source.iterate()) {
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
						Set<ClassNode> classes = source.getStructures().getAllBranches(m.owner, true);
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
		
		rename(cxt, remapped, true);
		System.out.printf("  Remapped %d/%d methods.%n", remapped.size(), totalMethods);
		
		return remapped.size();
	}
	
	public static void rename(IContext cxt, Map<MethodNode, String> remapped, boolean warn) {
		ApplicationClassSource source = cxt.getApplication();
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		for(ClassNode cn : source.iterate()) {
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
									MethodNode site = resolver.findStaticCall(invoke.getOwner(), invoke.getName(), invoke.getDesc());
									
									if(site != null) {
										if(remapped.containsKey(site)) {
											invoke.setName(remapped.get(site));
										} else {
											if(warn && mustMark(source, invoke.getOwner())) {
												System.err.println("  invalid site(s): " + invoke);
											}
										}
									} else {
										if(mustMark(source, invoke.getOwner())) {
											System.err.println("  can't resolve(s) " + invoke);
										}
									}
								} else {
//									 Set<MethodNode> sites = resolver.resolveVirtualCalls(invoke.getOwner(), invoke.getName(), invoke.getDesc());
									Set<ClassNode> classes = source.getStructures().getAllBranches(cn, true);
									Set<MethodNode> sites = getVirtualMethods(cxt, classes, invoke.getName(), invoke.getDesc());
									if(sites.size() > 0) {
										/* all of the sites must be linked by the same name,
										 * so we can use any to find the new name. */
										
										boolean anyContains = false;
										boolean allContains = true;
										for(MethodNode s : sites) {
											anyContains |= remapped.containsKey(s);
											allContains &= remapped.containsKey(s);
										}
										
										if(anyContains && !allContains) {
											System.err.println("mismatch: ");
											System.err.println(classes);
											System.err.println(sites);
											throw new RuntimeException();
										}
										
										MethodNode site = sites.iterator().next();
										if(remapped.containsKey(site)) {
											invoke.setName(remapped.get(site));
										} else {
											if(warn && !site.name.equals("<init>") && canRename(cxt, sites)) {
												System.err.println("  invalid site(v): " + invoke + ", " + sites);
											}
										}
									} else {
										if(mustMark(source, invoke.getOwner())) {
											System.err.println("  can't resolve(v) " + invoke + ", owner: " + invoke.getOwner());
											System.err.println("  classes: " + classes);
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
			System.out.println(e.getKey());
			e.getKey().name = e.getValue();
			System.out.println("  post: " + e.getKey());
		}
	}
	
	private static boolean mustMark(ApplicationClassSource tree, String owner) {
		ClassNode cn = tree.findClassNode(owner);
		return cn == null || !tree.isLibraryClass(owner);
	}
	
	private static boolean canRename(IContext cxt, Set<MethodNode> methods) {
		for(MethodNode m : methods) {
			if(cxt.getApplication().isLibraryClass(m.owner.name)) {
				/* inherited from runtime class */
				return false;
			}
		}
		return true;
	}
	
	private static Set<MethodNode> getVirtualMethods(IContext cxt, Set<ClassNode> classes, String name, String desc) {
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