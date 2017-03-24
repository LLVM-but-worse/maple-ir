package org.mapleir.stdlib.klass;

import org.mapleir.deobimpl2.cxt.IContext;
import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class InvocationResolver {

	private final ApplicationClassSource app;
	
	public InvocationResolver(ApplicationClassSource app) {
		this.app = app;
	}
	
	private boolean areTypesCongruent(Type expected, Type actual) {
		if (expected.equals(actual))
			return true;
		
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
	
	private boolean doArgumentsMatch(String expected, String actual) {
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
		return true;
	}
	
	private boolean areReturnTypesCongruent(String a, String b) {
		return areTypesCongruent(Type.getReturnType(a), Type.getReturnType(b));
	}
	
	private boolean areDescsCongruent(String a, String b) {
		return doArgumentsMatch(a, b) && areReturnTypesCongruent(a, b);
	}
	
	public boolean isVirtualDerivative(MethodNode candidate, String name, String desc) {
		return candidate.name.equals(name) && areDescsCongruent(desc, candidate.desc);
	}
	
	public boolean areMethodsCongruent(MethodNode candidate, String name, String desc, boolean isStatic) {
		if(Modifier.isStatic(candidate.access) != isStatic) {
			return false;
		}
		
		if(isStatic) {
			return candidate.name.equals(name) && candidate.desc.equals(desc);
		} else {
			return isVirtualDerivative(candidate, name, desc);
		}
	}
	
	public boolean areMethodsCongruent(MethodNode candidate, MethodNode target, boolean isStatic) {
		return areMethodsCongruent(candidate, target.name, target.desc, isStatic);
	}
	
	public static boolean isBridge(int access) {
		return (access & Opcodes.ACC_BRIDGE) != 0;
	}
	
	private static int ANY_TYPES = 0;
	private static int CONGRUENT_TYPES = 1;
	private static int EXACT_TYPES = 2;
	
	private static int VIRTUAL_METHOD = ~Modifier.STATIC & ~Modifier.ABSTRACT & ~Opcodes.ACC_BRIDGE;
	
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
	
	/**
	 * Finds methods in cn matching name and desc.
	 * @param cn
	 * @param name
	 * @param desc
	 * @param returnTypes One of ANY_TYPES, CONGRUENT_TYPES, or EXACT_TYPES
	 * @param mask Mask of allowed attributes for modifiers; bit 1 = allowed, 0 = not allowed
	 * @return
	 */
	private Set<MethodNode> findMethod(ClassNode cn, String name, String desc, int returnTypes, int mask) {
		Set<MethodNode> findM = new HashSet<>();

		for(MethodNode m : cn.methods) {
			if(((m.access ^ mask) & m.access) == 0) { // no bits set in m.access that aren't in flags
				assert(!Modifier.isStatic(m.access));
				if (Modifier.isStatic(m.access))
					throw new IllegalStateException("B0i");
				if ((mask & Modifier.ABSTRACT) == 0 && Modifier.isAbstract(m.access))
					throw new IllegalStateException("B0i");
				if ((mask & Opcodes.ACC_BRIDGE) == 0 && (m.access & Opcodes.ACC_BRIDGE) != 0)
					throw new IllegalStateException("B0i");

				if (!m.name.equals(name))
					continue;
				if (!doArgumentsMatch(desc, m.desc))
					continue;
				if (returnTypes == CONGRUENT_TYPES) {
					if (!areReturnTypesCongruent(desc, m.desc))
						continue;
				} else if (returnTypes == EXACT_TYPES) {
					if (!desc.equals(m.desc))
						continue;
				}
				
				// sanity check
				if (returnTypes == EXACT_TYPES && !isBridge(mask) && !findM.isEmpty()) {
					System.err.println("==findM==");
					debugCong(desc, findM.iterator().next().desc);
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
					
					throw new IllegalStateException(String.format("%s contains %s(br=%b) and %s(br=%b)", cn.name, findM, isBridge(findM.iterator().next().access), m, isBridge(m.access)));
				}
				findM.add(m);
			}
		}
		
		return findM;
	}
	
	// todo: refactor to just proxy to findExactClassMethod
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
	
	public MethodNode findExactClassMethod(ClassNode cn, String name, String desc, boolean allowAbstract) {
		Set<MethodNode> found = findMethod(cn, name, desc, EXACT_TYPES, VIRTUAL_METHOD | (allowAbstract ? Modifier.ABSTRACT : 0));
		return found.isEmpty() ? null : found.iterator().next();
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
				m = findExactClassMethod(cn, name, desc, false);
				
				if(m == null) {
					if(strict) {
						throw new IllegalStateException(String.format("No call to %s.<init> %s", owner, desc));
					}
				} else {
					set.add(m);
				}
				return set;
			}
			
			for(ClassNode c : app.getStructures().getAllChildren(cn)) {
				m = findExactClassMethod(c, name, desc, true);
				
				if(m != null) {
					set.add(m);
				}
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
					m = findExactClassMethod(c, name, desc, true);
					if(m != null) {
						lvlSites.add(m);
					}
				}
				
				if(lvlSites.size() > 1 && strict) {
					int c = 0;
					for(MethodNode mn : lvlSites) {
						if(!Modifier.isAbstract(mn.access)) {
							c++;
						}
					}
					if(c > 1) {
						System.out.printf("(warn) resolved %s.%s %s to %s (c=%d).%n", owner, name, desc, lvlSites, c);
					}
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
	
	public Set<MethodNode> resolveVirtualCalls(MethodNode m, boolean strict) {
		return resolveVirtualCalls(m.owner.name, m.name, m.desc, strict);
	}
	
	public MethodNode resolveStaticCall(String owner, String name, String desc) {
		ClassNode cn = app.findClassNode(owner);
		MethodNode mn = null;
		if(cn != null) {
			// todo: replace with findMethod
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
				return resolveStaticCall(cn.superName, name, desc);
			} else {
				return mn;
			}
		} else {
			return null;
		}
	}
	
	public static Set<MethodNode> getHierarchyMethodChain(IContext cxt, ClassNode cn, String name, String desc, boolean verify) {
		ApplicationClassSource app = cxt.getApplication();
		ClassTree structures = app.getStructures();
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		Set<MethodNode> foundMethods = new HashSet<>();
		Collection<ClassNode> toSearch = structures.getAllBranches(cn);
		for (ClassNode viable : toSearch) {
			foundMethods.addAll(resolver.findMethod(viable, name, desc, EXACT_TYPES, VIRTUAL_METHOD | Modifier.ABSTRACT | Opcodes.ACC_BRIDGE));
		}
		if (verify && foundMethods.isEmpty()) {
			System.err.println("cn: " + cn);
			System.err.println("name: " + name);
			System.err.println("desc: " + desc);
			System.err.println("Searched: " + toSearch);
			System.err.println("Children: " + structures.getAllChildren(cn));
			System.err.println("Parents: " + structures.getAllParents(cn));
			throw new IllegalArgumentException("You must be really dense because that method doesn't even exist.");
		}
		return foundMethods;
	}
}