package org.mapleir.deob.passes.rename;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.mapleir.app.client.SimpleApplicationContext;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.InvocationResolver;
import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.IPass;
import org.mapleir.deob.PassContext;
import org.mapleir.deob.PassResult;
import org.mapleir.deob.util.RenamingHeuristic;
import org.mapleir.deob.util.RenamingUtil;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.DynamicInvocationExpr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.mapleir.asm.ClassNode;
import org.mapleir.asm.MethodNode;
import org.objectweb.asm.Handle;

public class MethodRenamerPass implements IPass {

	private final RenamingHeuristic heuristic;
	public final Map<MethodNode, String> remapped = new HashMap<>();
	public final Map<MethodNode, String> oldNames = new HashMap<>();

	public MethodRenamerPass(RenamingHeuristic heuristic) {
		this.heuristic = heuristic;
	}
	
	@Override
	public PassResult accept(PassContext pcxt) {
		AnalysisContext cxt = pcxt.getAnalysis();
		ApplicationClassSource source = cxt.getApplication();
		InvocationResolver resolver = cxt.getInvocationResolver();

		int totalMethods = 0;
		
		for(ClassNode cn : source.iterate()) {
			totalMethods += cn.getMethods().size();
		}
		
		int i = RenamingUtil.computeMinimum(totalMethods) + RenamingUtil.numeric("aaaaa");
		
		// Map<MethodNode, Set<MethodNode>> debugMap = new HashMap<>();
		
		for(ClassNode cn : source.iterate()) {
			for(MethodNode m : cn.getMethods()) {
				if (!heuristic.shouldRename(m.getName(), m.node.access)) {
				// 	System.out.println("Heuristic bypass meth " + m.name);
					continue;
				}
				if(remapped.containsKey(m)) {
					continue;
				}
				
				if(Modifier.isStatic(m.node.access)) {
					if(!m.getName().equals("<clinit>") && !SimpleApplicationContext.isMainMethod(m)) {
						String newName = "m_" + RenamingUtil.createName(i++);
						remapped.put(m, newName);
						oldNames.put(m, m.getName());
					}
				} else {
					if(!m.getName().equals("<init>")) {
						// Set<ClassNode> classes = source.getStructures().dfsTree(m.owner, true, true, true);
						// Set<MethodNode> methods = getVirtualMethods(cxt, classes, m.name, m.desc);
						Set<MethodNode> methods = resolver.getHierarchyMethodChain(m.owner, m.getName(), m.node.desc, true);
						if(canRename(cxt, methods)) {
							String newName = "m_" + RenamingUtil.createName(i++);
							
							for(MethodNode o : methods) {
								// Set<MethodNode> s2 = InvocationResolver.getHierarchyMethodChain(cxt, o.owner, o.name, m.desc, true);

								/*if(!methods.equals(s2)) {
									System.err.printf("m: %s%n", m);
									System.err.printf("o: %s%n", o);
									System.err.println("this ms::");
									for(MethodNode s : methods) {
										System.err.printf("   %s%n", s);
									}
									System.err.println("o ms::");
									for(MethodNode s : s2) {
										System.err.printf("   %s%n", s);
									}
									throw new IllegalStateException();
								}*/

								/*if(remapped.containsKey(o)) {
									System.err.printf("m: %s%n", m);
									System.err.printf("o: %s%n", o);
									System.err.println("this ms::");
									for(MethodNode s : methods) {
										System.err.printf("   %s%n", s);
									}
									System.err.println("o ms::");
									for(MethodNode s : InvocationResolver.getHierarchyMethodChain(cxt, o.owner, o.name, m.desc, true)) {
										System.err.printf("   %s%n", s);
									}
									System.err.println(" o debugset::");
									for(MethodNode s : debugMap.get(o)) {
										System.err.printf("   %s%n", s);
									}
									System.err.printf("on: %s%n", remapped.get(o));
									System.err.printf("nn: %s%n", newName);
									throw new IllegalStateException();
								}*/
								remapped.put(o, newName);
								oldNames.put(m, m.getName());
							}

							/*for(MethodNode hm : methods) {
								debugMap.put(hm, methods);
							}*/
						} else {
							System.out.println("  can't rename: " + methods);
						}
					}
				}
			}
		}

		remapped.forEach((key, value) -> System.out.println(key + " --> " + value));
		oldNames.putAll(rename(cxt, remapped, true));
		System.out.printf("  Remapped %d/%d methods.%n", remapped.size(), totalMethods);

		return PassResult.with(pcxt, this).finished().make();
	}

	public static MethodNode stupidlyFindMethodNodeByName(Map<MethodNode, String> oldNames, String name) {
		Set<MethodNode> c = oldNames.entrySet().stream().filter(e -> e.getValue().equals(name)).map(Entry::getKey).collect(Collectors.toUnmodifiableSet());
		if (c.size() > 1) {
			throw new RuntimeException("expected 1 matching method for " + name + " , but found " + c.size());
		}
		if (c.isEmpty()) {
			return null;
		}
		return c.iterator().next();
	}

	public static Map<MethodNode, String> rename(AnalysisContext cxt, Map<MethodNode, String> remapped, boolean warn) {
		ApplicationClassSource source = cxt.getApplication();
		InvocationResolver resolver = cxt.getInvocationResolver();

		Map<MethodNode, String> oldNames = new HashMap<>();
		for(Entry<MethodNode, String> e : remapped.entrySet()) {
			oldNames.put(e.getKey(), e.getKey().node.name);
		}
		
		for(ClassNode cn : source.iterate()) {
			{
				if(cn.node.outerMethod != null) {
//					ClassNode owner = tree.getClass(cn.node.outerClass);
					System.out.println("Outer: " + cn.node.outerClass + "." + cn.node.outerMethod + " " + cn.node.outerMethodDesc);
					cn.node.outerClass = null;
					cn.node.outerMethod = null;
					cn.node.outerMethodDesc = null;
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
			
			Set<Expr> visited = new HashSet<>();
			
			for(MethodNode m : cn.getMethods()) {
				ControlFlowGraph cfg = cxt.getIRCache().getFor(m);
				
				for(BasicBlock b : cfg.vertices()) {
					for(Stmt stmt : b) {
						for(Expr e : stmt.enumerateOnlyChildren()) {
							
							if(e.getOpcode() == Opcode.INVOKE) {
								InvocationExpr invoke = (InvocationExpr) e;

								if(visited.contains(invoke)) {
									throw new RuntimeException(invoke.toString());
								}
								visited.add(invoke);
								
								if(invoke.getOwner().startsWith("[")) {
									System.err.println("  ignore array object invoke: " + invoke + ", owner: " + invoke.getOwner());
									continue;
								}

								if (invoke.isDynamic()) {
									DynamicInvocationExpr die = (DynamicInvocationExpr) invoke;
									// Resolved call target
									String oldBoundName = die.getBoundName();
									// try proper resolution ("proper" being just shitty heuristics)
									for (MethodNode targetMn : die.resolveTargets(resolver)) {
										if (remapped.containsKey(targetMn)) {
											die.setBoundName(remapped.get(targetMn));
										}
									}
									// uhhhhhhhhhh this is not good. this is just a textual comparison and does not properly resolve the call!!!!!!!
									// but because the call resolution is, in theory, dynamically defined, there is no way to properly resolve it statically!
									// we can write heuristics for common cases, like LambdaMetafactory (see DynamicInvocationExpression.resolveTargets),
									// but there's no good way to deal with this problem!
									// This is the same problem as not being able to fix reflective references!
									MethodNode targetMn;
									targetMn = stupidlyFindMethodNodeByName(oldNames, die.getBoundName());
									if (targetMn != null) {
										assert targetMn.getDesc().equals(die.getDesc());
										die.setBoundName(remapped.get(targetMn));
									}

									// Bootstrap method
									String oldName = die.getName();
									// are all bootstrap methods static? I don't know
									targetMn = resolver.resolveStaticCall(die.getBootstrapOwner(), die.getBootstrapName(), die.getBootstrapDesc());
									if (targetMn != null) {
										die.setName(remapped.get(targetMn));
									}
									System.err.println("Renamed invokedynamic: " + oldBoundName + " -> " + die.getBoundName() + " , " + oldName + " -> " + die.getName());
								} else if(invoke.isStatic()) {
									MethodNode site = resolver.resolveStaticCall(invoke.getOwner(), invoke.getName(), invoke.getDesc());
									
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
											System.err.printf("  can't resolve(s) %s ; %s.%s %s%n", invoke, invoke.getOwner(), invoke.getName(), invoke.getDesc());
										}
									}
								} else {
//									 Set<MethodNode> sites = resolver.resolveVirtualCalls(invoke.getOwner(), invoke.getName(), invoke.getDesc());
									// Set<ClassNode> classes = source.getStructures().dfsTree(cn, true, true, true);
									// Set<MethodNode> sites = getVirtualMethods(cxt, classes, invoke.getName(), invoke.getDesc());
									Set<MethodNode> sites = resolver.getHierarchyMethodChain(source.findClassNode(invoke.getOwner()), invoke.getName(), invoke.getDesc(), true);
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
											// System.err.println(classes);
											System.err.println(sites);
											throw new RuntimeException();
										}

										MethodNode site = sites.iterator().next();
										if(remapped.containsKey(site)) {
											invoke.setName(remapped.get(site));
										} else {
											if(warn && !site.getName().equals("<init>") && canRename(cxt, sites)) {
												System.err.println("  invalid site(v): " + invoke + ", " + sites);
											}
										}
									} else {
										if(mustMark(source, invoke.getOwner())) {
											System.err.println("  can't resolve(v) " + invoke + ", owner: " + invoke.getOwner() + " desc " + invoke.getDesc());
											// System.err.println("  classes: " + classes);
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
			System.out.printf("%s -> %s%n", e.getKey(), e.getValue());
			e.getKey().node.name = e.getValue();
		}

		return oldNames;
	}
	
	private static boolean mustMark(ApplicationClassSource tree, String owner) {
		ClassNode cn = tree.findClassNode(owner);
		return cn == null || !tree.isLibraryClass(owner);
	}
	
	private static boolean canRename(AnalysisContext cxt, Set<MethodNode> methods) {
		for(MethodNode m : methods) {
			if(cxt.getApplication().isLibraryClass(m.getOwner())) {
				/* inherited from runtime class */
				return false;
			}
		}
		return true;
	}
}
