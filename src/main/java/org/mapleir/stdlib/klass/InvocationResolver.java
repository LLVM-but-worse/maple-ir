package org.mapleir.stdlib.klass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.algorithms.ExtendedDfs;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class InvocationResolver {

	private final ApplicationClassSource app;
	
	public InvocationResolver(ApplicationClassSource app) {
		this.app = app;
	}
	
	public MethodNode resolveVirtualInitCall(String owner, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = app.findClassNode(owner);
		
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
				throw new IllegalStateException(String.format("Looking for: %s.<init>%s, got: %s", owner, desc, set));
			}
		} else {
			return null;
		}
	}
	
	private boolean checkOveride(Type expected, Type actual) {
		boolean eArr = expected.getSort() == Type.ARRAY;
		boolean aArr = actual.getSort() == Type.ARRAY;
		if(eArr != aArr) {
			return false;
		}
		
		if(eArr) {
			expected = expected.getElementType();
			actual = actual.getElementType();
		}
		
		if(TypeUtils.isPrimitive(expected) || TypeUtils.isPrimitive(actual)) {
			return false;
		}
		if(expected == Type.VOID_TYPE || actual == Type.VOID_TYPE) {
			return false;
		}
		
		ClassNode eCn = app.findClassNode(expected.getInternalName());
		ClassNode aCn = app.findClassNode(actual.getInternalName());
		
		return app.getStructures().getAllParents(aCn).contains(eCn);
	}

	
	private void debugCong(String expected, String actual) {
		Type eR = Type.getReturnType(expected);
		Type aR = Type.getReturnType(actual);
		
		System.err.println("eR: " + eR);
		System.err.println("aR: " + aR);
		System.err.println("eq: " + (eR == aR));

		ClassNode eCn = app.findClassNode(eR.getInternalName());
		ClassNode aCn = app.findClassNode(aR.getInternalName());
		
		System.err.println("eCn: " + eCn.name);
		System.err.println("aCn: " + aCn.name);
		System.err.println(app.getStructures().getAllParents(aCn));
		System.err.println("eCn parent of aCn?: " + app.getStructures().getAllParents(aCn).contains(eCn));
		System.err.println("aCn child of eCn?: " + app.getStructures().getAllChildren(eCn).contains(aCn));
	}
	
	private boolean isCongruent(String expected, String actual) {
		Type[] eParams = Type.getArgumentTypes(expected);
		Type[] aParams = Type.getArgumentTypes(actual);
		
		if(eParams.length != aParams.length) {
			return false;
		}
		
		for(int i=0; i < eParams.length; i++) {
			Type e = eParams[i];
			Type a = aParams[i];
			
			if(!e.equals(a)) {
				return false;
			}
		}
		
		Type eR = Type.getReturnType(expected);
		Type aR = Type.getReturnType(actual);
		
		return eR.equals(aR) || checkOveride(eR, aR);
	}
	
	public boolean isVirtualDerivative(MethodNode candidate, String name, String desc) {
		return candidate.name.equals(name) && isCongruent(desc, candidate.desc);
	}
	
	public boolean isStrictlyEqual(MethodNode candidate, MethodNode target, boolean isStatic) {
		return isStrictlyEqual(candidate, target.name, target.desc, isStatic);
	}
	
	public boolean isStrictlyEqual(MethodNode candidate, String name, String desc, boolean isStatic) {
		if(Modifier.isStatic(candidate.access) != isStatic) {
			return false;
		}
		
		if(isStatic) {
			return candidate.name.equals(name) && candidate.desc.equals(desc);
		} else {
			return isVirtualDerivative(candidate, name, desc);
		}
	}
	
	private MethodNode findDerivativeMethod(ClassNode cn, String name, String desc, boolean congruentReturn, boolean allowAbstract) {
		MethodNode findM = null;

		for(MethodNode m : cn.methods) {
			if(!Modifier.isStatic(m.access) && (allowAbstract || !Modifier.isAbstract(m.access))) {
				if(m.name.equals(name) && (congruentReturn ? isCongruent(desc, m.desc) : m.desc.equals(desc))) {
					if(findM != null) {
						System.err.println("==findM==");
						debugCong(desc, findM.desc);
						System.err.println("==m==");
						debugCong(desc, m.desc);
						
						{
							ClassWriter cw = new ClassWriter(0);
							cn.accept(cw);
							byte[] bs = cw.toByteArray();
							
							try {
								FileOutputStream fos = new FileOutputStream(new File("out/broken.class"));
								fos.write(bs);
								fos.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							
						}
						
						throw new IllegalStateException(String.format("%s contains %s and %s", cn.name, findM, m));
					}
					
					findM = m;
				}
			}
		}
		
		return findM;
	}

	public MethodNode findClassMethod(ClassNode cn, String name, String desc, boolean congruentReturn, boolean allowAbstract) {
		/* find exact method first */
		MethodNode m = findDerivativeMethod(cn, name, desc, false, allowAbstract);
		if(m != null) {
			return m;
		} else if(congruentReturn) {
			m = findDerivativeMethod(cn, name, desc, true, allowAbstract);
		}
		
		return m;
	}
	

	public Set<MethodNode> resolveVirtualCalls(MethodNode m, boolean strict) {
		return resolveVirtualCalls(m.owner.name, m.name, m.desc, strict);
	}
	
	public Set<MethodNode> resolveVirtualCalls(String owner, String name, String desc, boolean strict) {
		Set<MethodNode> set = new HashSet<>();
		ClassNode cn = app.findClassNode(owner);
		
		if(cn != null) {
			
			/* Due to type uncertainties, we do not know the
			 * exact type of the object that this call was
			 * invoked on. We do, however, know that the
			 * minimum type it must be is either a subclass
			 * of this class, i.e.
			 * 
			 * C extends B, B extends A,
			 * A obj = new A, B or C();
			 * obj.invoke();
			 * 
			 * In this scenario, the call to A.invoke() could
			 * be a call to A.invoke(), B.invoke() or c.invoke().
			 * So we include all of these cases at the start.
			 * 
			 * The alternative to being a subclass call, is
			 * a call in the class A or the closest
			 * superclass method to it, i.e.
			 * 
			 * C extends B, B extends A1 implements A2, A3,
			 * B obj = new B or C();
			 * obj.invoke();
			 * 
			 * Here we know that obj cannot be an exact 
			 * object of class A, however:
			 *  if obj is an instance of class B or C and class
			 *  B and C do not define the invoke() method, we 
			 *  may be attempting to call A1, A2 or A3.invoke()
			 *  so we must look up the class hierarchy to find
			 *  the closest possible invocation site.
			 */
			
			MethodNode m;
			
			if(name.equals("<init>")) {
				m = findClassMethod(cn, name, desc, false, false);
				
				if(m == null) {
					if(strict) {
						throw new IllegalStateException(String.format("No call to %s.<init> %s", owner, desc));
					}
				} else {
					set.add(m);
				}
				return set;
			}
			
			{
				Collection<ClassNode> supers = app.getStructures().getAllParents(cn);
				System.out.println(supers);
				System.out.println(" size: " + supers.size());
				
				Set<MethodNode> heads = new HashSet<>();
				NullPermeableHashMap<ClassNode, Set<ClassNode>> closestParents = new NullPermeableHashMap<>(new SetCreator<>());
				Deque<ClassNode> stack = new LinkedList<>();
				
				new ExtendedDfs<ClassNode>(app.getStructures(), cn, 0) {
					@Override
					protected void dfs(ClassNode par, ClassNode cn) {
						/* enter */
						stack.push(cn);
						
						MethodNode mn = findClassMethod(cn, name, desc, false, true);
						
						if(mn != null) {
							heads.add(mn);
							
							/* push current node off the stack
							 * as it's not selfparented and 
							 * we're done with the branch. */
							stack.pop();
							
							for(ClassNode c : stack) {
								closestParents.getNonNull(c).add(cn);
							}
						} else {
							/* not here, search supers */
							super.dfs(par, cn);
							if(stack.peekFirst() != cn) {
								throw new RuntimeException(cn + " : " + stack.peek());
							} else {
								/* done branch naturally */
								stack.pop();
							}
						}
					}
				};
				
				for(MethodNode mn : heads) {
					Deque<ClassNode> rtypes = new LinkedList<>();
					Type retType = Type.getReturnType(mn.desc);
					
					if(!TypeUtils.isPrimitive(retType) && !mn.desc.endsWith("V")) {
						ClassNode retClass = app.findClassNode(retType.getInternalName());
						Set<ClassNode> retClasses = new HashSet<>(app.getStructures().getAllChildren(retClass));
						retClasses.add(retClass);
						
						System.out.println("m: " + mn);
						System.out.println("  classes: " + retClasses);
						new ExtendedDfs<ClassNode>(app.getStructures(), mn.owner, ExtendedDfs.REVERSE) {
							@Override
							protected void dfs(ClassNode par, ClassNode cn) {
								if(cn != mn.owner) {
									System.out.println("c: "+ cn);
								}
								super.dfs(par, cn);
							}
						};
					}
				}
			}
			
			Collection<ClassNode> ch = app.getStructures().getAllChildren(cn);
			if(!ch.contains(cn)) {
				ch.add(cn);
			}
			// TODO: Use existing SimpleDFS code
			try {
				for(ClassNode c : ch) {
					m = findClassMethod(c, name, desc, true, false);
					
					if(m != null) {
						set.add(m);
					}
				}
			} catch(RuntimeException e) {
				System.err.println("call:");
				System.err.println(owner);
				System.err.println(name);
				System.err.println(desc);
				System.err.println(strict);
				throw e;
			}
			
			m = null;
			
			Set<ClassNode> lvl = new HashSet<>();
			lvl.add(cn);
			for(;;) {
				if(lvl.size() == 0) {
					break;
				}
				
				Set<MethodNode> lvlSites = new HashSet<>();
				for(ClassNode c : lvl) {
					m = findClassMethod(c, name, desc, false, false);
					if(m != null) {
						lvlSites.add(m);
					}
				}
				
				if(lvlSites.size() > 1 && strict) {
					System.out.printf("(warn) resolved %s.%s %s to %s.%n", owner, name, desc, lvlSites);
				}
				
				/* we've found the method in the current
				 * class; there's not point exploring up
				 * the class hierarchy since the superclass
				 * methods are all shadowed by this call site.
				 */
				if(lvlSites.size() > 0) {
					set.addAll(lvlSites);
					break;
				}
				
				// TODO: use queues here instead.
				Set<ClassNode> newLvl = new HashSet<>();
				for(ClassNode c : lvl) {
					ClassNode sup = app.findClassNode(c.superName);
					if(sup != null) {
						newLvl.add(sup);
					}
					
					for(String iface : c.interfaces) {
						ClassNode ifaceN = app.findClassNode(iface);
						
						if(ifaceN != null) {
							newLvl.add(ifaceN);
						}
					}
				}
				
				lvl.clear();
				lvl = newLvl;
			}
		}
		
		return set;
	}
	
	public MethodNode findStaticCall(String owner, String name, String desc) {
		ClassNode cn = app.findClassNode(owner);
		MethodNode mn = null;
		if(cn != null) {
			for(MethodNode m : cn.methods) {
				if((m.access & Opcodes.ACC_STATIC) != 0) {
					if(m.name.equals(name) && m.desc.equals(desc)) {
						if (mn != null)
							throw new IllegalStateException(owner + "." + name + " " + desc + ",   " + mn + "," + m);
						mn = m;
					}
				}
			}
			if(mn == null) {
				return findStaticCall(cn.superName, name, desc);
			} else {
				return mn;
			}
		} else {
			return null;
		}
	}
}