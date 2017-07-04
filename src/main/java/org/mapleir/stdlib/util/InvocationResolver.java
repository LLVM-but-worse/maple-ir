package org.mapleir.stdlib.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.context.app.ApplicationClassSource;
import org.mapleir.context.app.ClassTree;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class InvocationResolver {

	private final ApplicationClassSource app;
	
	public InvocationResolver(ApplicationClassSource app) {
		this.app = app;
	}
	
	/**
	 * Two method descriptors have matching arguments iff the argument and return types are equal.
	 * A call can resolve to a method iff its descriptor are matching the one specified in the call.
	 * @param a descriptor A
	 * @param b descriptor B
	 * @return true if their arguments match exactly.
	 */
	private boolean doDescsMatch(String a, String b) {
		return Arrays.equals(Type.getArgumentTypes(a), Type.getArgumentTypes(b))
				&& Type.getReturnType(a).equals(Type.getReturnType(b));
	}
	
	/**
	 * Two types are congruent if they are primitive and the same, or if one is a subclass of another.
	 * @param a type A
	 * @param b type B
	 * @return true if type A and B are congruent
	 */
	private boolean areTypesCongruent(Type a, Type b) {
		if (a.equals(b)) {
			return true;
		}
		
		boolean eArr = a.getSort() == Type.ARRAY;
		boolean aArr = b.getSort() == Type.ARRAY;
		if(eArr != aArr) {
			return false;
		}
		
		if(eArr) {
			a = a.getElementType();
			b = b.getElementType();
		}
		
		if(TypeUtils.isPrimitive(a) || TypeUtils.isPrimitive(b)) {
			return false;
		}
		if(a == Type.VOID_TYPE || b == Type.VOID_TYPE) {
			return false;
		}
		
		ClassNode cnA = app.findClassNode(a.getInternalName());
		ClassNode cnB = app.findClassNode(b.getInternalName());
		
		ClassTree tree = app.getClassTree();
		return tree.getAllParents(cnB).contains(cnA) ||
               tree.getAllParents(cnA).contains(cnB);
	}
	
	
	public static boolean isBridge(int access) {
		return (access & Opcodes.ACC_BRIDGE) != 0;
	}
	
	private static final int ANY_TYPES = 0;
	private static final int CONGRUENT_TYPES = 1;
	private static final int EXACT_TYPES = 2;
	private static final int LOOSELY_RELATED_TYPES = 3;
	
	private static final int VIRTUAL_METHOD = ~Modifier.STATIC & ~Modifier.ABSTRACT & ~Opcodes.ACC_BRIDGE;
	
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
		System.err.println(app.getClassTree().getAllParents(aCn));
		System.err.println("eCn parent of aCn?: " + app.getClassTree().getAllParents(aCn).contains(eCn));
		System.err.println("aCn child of eCn?: " + app.getClassTree().getAllChildren(eCn).contains(aCn));
	}
	
	/**
	 * Finds methods in cn matching name and desc.
	 * @param cn ClassNode
	 * @param name name of method
	 * @param desc method descriptor
	 * @param returnTypes One of ANY_TYPE, CONGRUENT_TYPES, EXACT_TYPES, or LOOSELY_RELATED_TYPES
	 * @param allowedMask Mask of allowed attributes for modifiers; bit 1 = allowed, 0 = not allowed
	 * @param requiredMask Mask of required attributes for modifiers; bit 1 = allowed, 0 = not allowed
	 * @return Set of methods matching specifications
	 */
	private Set<MethodNode> findMethods(ClassNode cn, String name, String desc, int returnTypes, int allowedMask, int requiredMask) {
		allowedMask |= requiredMask;
		Set<MethodNode> findM = new HashSet<>();
		
		Type[] expectedParams = Type.getArgumentTypes(desc);
		
		for(MethodNode m : cn.methods) {
			// no bits set in m.access that aren't in allowedMask
			// no bits unset in m.access that are in requiredMask
			if(((m.access ^ allowedMask) & m.access) == 0 && ((m.access ^ requiredMask) & requiredMask) == 0) {
				if (!Modifier.isStatic(allowedMask) && Modifier.isStatic(m.access))
					throw new IllegalStateException("B0i");
				if (!Modifier.isAbstract(allowedMask) && Modifier.isAbstract(m.access))
					throw new IllegalStateException("B0i");
				if (!isBridge(allowedMask) && isBridge(m.access))
					throw new IllegalStateException("B0i");
				if (Modifier.isStatic(requiredMask) && !Modifier.isStatic(m.access))
					throw new IllegalStateException("B0i");

				if (!m.name.equals(name) || !Arrays.equals(expectedParams, Type.getArgumentTypes(m.desc))) {
					continue;
				}

				switch(returnTypes) {
					case ANY_TYPES:
						break;
					case CONGRUENT_TYPES:
						if (!areTypesCongruent(Type.getReturnType(desc), Type.getReturnType(m.desc))) {
							continue;
						}
						break;
					case EXACT_TYPES:
						if (!desc.equals(m.desc)) {
							continue;
						}
						break;
					case LOOSELY_RELATED_TYPES:
						if(!areTypesLooselyRelated(Type.getReturnType(desc), Type.getReturnType(m.desc))) {
							continue;
						}
						break;
				}
				
				// sanity check
				if (returnTypes == EXACT_TYPES && !isBridge(allowedMask) && !findM.isEmpty()) {
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
	
	/**
	 * returns true if::
	 *  both types are primitives of the same
	 *   race.
	 * or
	 *  the types are both objects.
	 * or
	 *  they are both array types and have the
	 *  same number of dimensions and have
	 *  loosely or strongly (T I G H T L Y)
	 *  related element types.
	 */
	private boolean areTypesLooselyRelated(Type t1, Type t2) {
		/* getSort() returns V, Z, C, B, I, F, J, D, array or object */
		if(t1.getSort() == t2.getSort()) {
			if(t1.getSort() == Type.ARRAY) {
				/* both arrays */
				
				if(t1.getDimensions() != t2.getDimensions()) {
					return false;
				}
				
				return areTypesLooselyRelated(t1.getElementType(), t2.getElementType());
			}
			
			/* either strongly related (prims) or
			 * loosely (object types) */
			return true;
		} else {
			/* no chance m8 */
			return false;
		}
	}
	
	public MethodNode findExactMethod(ClassNode cn, String name, String desc, int allowedMask, int requiredMask) {
		Set<MethodNode> found = findMethods(cn, name, desc, EXACT_TYPES, allowedMask, requiredMask);
		if(found.size() == 0) {
			return null;
		} else if(found.size() == 1) {
			return found.iterator().next();
		} else {
			throw new IllegalStateException(found.toString());
		}
	}
	
	public MethodNode findExactVirtualMethod(ClassNode cn, String name, String desc, boolean allowAbstract) {
		return findExactMethod(cn, name, desc, VIRTUAL_METHOD | (allowAbstract ? Modifier.ABSTRACT : 0), 0);
	}
	
	public MethodNode resolveStaticCall(String owner, String name, String desc) {
		ClassNode cn = app.findClassNode(owner);
		if (cn == null) {
			return null;
		}
		
		MethodNode mn = findExactMethod(cn, name, desc, VIRTUAL_METHOD | Modifier.ABSTRACT | Modifier.STATIC, Modifier.STATIC);
		if(mn == null) {
			return resolveStaticCall(cn.superName, name, desc);
		} else {
			return mn;
		}
	}
	
	public MethodNode resolveVirtualInitCall(String owner, String desc) {
		Set<MethodNode> set = new HashSet<>();
		ClassNode cn = app.findClassNode(owner);
		if (cn == null)
			return null;
		
		MethodNode ret = findExactVirtualMethod(cn, "<init>", desc, false);
		if (ret == null)
			throw new IllegalStateException(String.format("Looking for: %s.<init>%s, got: %s", owner, desc, set));
		return ret;
	}
	
	public Set<MethodNode> resolveVirtualCalls(String owner, String name, String desc, boolean strict) {
		Set<MethodNode> set = new HashSet<>();
		ClassNode cn = app.findClassNode(owner);
		if (cn == null) { // No class = no call.
			return set;
		}
	
		/*
		Due to type uncertainties, we do not know the
		exact type of the object that this call was
		invoked on. We do, however, know that the
		minimum type it must be is either a subclass
		of this class, i.e.
	
		C extends B, B extends A,
		A obj = new A, B or C();
		obj.invoke();
	
		In this scenario, the call to A.invoke() could
		be a call to A.invoke(), B.invoke() or c.invoke().
		So we include all of these cases at the start.
		This issue is called underriding.
	
		The alternative to being a subclass call, is
		a call in the class A or the closest
		superclass method to it, i.e.
	
		C extends B, B extends A1 implements A2, A3,
		B obj = new B or C();
		obj.invoke();
	
		Here we know that obj cannot be an exact
		object of class A, however:
		if obj is an instance of class B or C and class
		B and C do not define the invoke() method, we
		may be attempting to call A1, A2 or A3.invoke()
		so we must look up the class hierarchy to find
		the closest possible invocation site.

		The subtlety here however is that in Java8
		interface default methods have a lower resolution
		precedence than class override methods, so we
		only scan those if no class override is found.
		 */
		MethodNode m;
		
		// Special handling for init
		if(name.equals("<init>")) {
			m = findExactVirtualMethod(cn, name, desc, false);
			
			if(m == null) {
				if(strict) {
					throw new IllegalStateException(String.format("No call to %s.<init> %s", owner, desc));
				}
			} else {
				set.add(m);
			}
			return set;
		}
		
		// Underride search
		for(ClassNode c : app.getClassTree().getAllChildren(cn)) {
			m = findExactVirtualMethod(c, name, desc, true);
			
			if(m != null) {
				set.add(m);
			}
		}
		
		// Superclass scan
		for (ClassNode curClass = cn; curClass != null; curClass = app.findClassNode(curClass.superName)) {
			m = findExactVirtualMethod(curClass, name, desc, true);
			if(m != null) {
				// we've found the method in the current class;
				// there's not point exploring up the class hierarchy
				// since the superclass methods are all shadowed
				// by this call site.
				set.add(m);
				return set;
			}
		}
		
		// Interface scan
		m = null;
		Set<ClassNode> lvl = new HashSet<>();
		lvl.add(cn);
		for(;;) {
			if(lvl.size() == 0) {
				break;
			}
			
			Set<MethodNode> lvlSites = new HashSet<>();
			for(ClassNode c : lvl) {
				m = findExactVirtualMethod(c, name, desc, true);
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
				if (lvlSites.size() > 1) {
					System.err.printf("(warn) ambiguous override resolution: %s.%s%s to %s.%n", owner, name, desc, lvlSites);
				}
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
		
		return set;
	}
	
	public Set<MethodNode> resolveVirtualCalls(MethodNode m, boolean strict) {
		return resolveVirtualCalls(m.owner.name, m.name, m.desc, strict);
	}
	
	/**
	 * Find methods matching the name and desc in all branches related to the class. Note this is much broader
	 * than actual resolution, and is best used for renaming purposes.
	 * @param cxt class tree provider
	 * @param cn base class
	 * @param name method name
	 * @param desc method descriptor
	 * @param exact if true, match return types exactly; otherwise congruent types are also matched
	 * @param verify verify results
	 * @return all matching methods
	 */
	public static Set<MethodNode> getHierarchyMethodChain(AnalysisContext cxt, ClassNode cn, String name, String desc, boolean exact, boolean verify) {
		ApplicationClassSource app = cxt.getApplication();
		ClassTree structures = app.getClassTree();
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		Set<MethodNode> foundMethods = new HashSet<>();
		Collection<ClassNode> toSearch = structures.getAllBranches(cn);
		for (ClassNode viable : toSearch) {
			foundMethods.addAll(resolver.findMethods(viable, name, desc, exact? EXACT_TYPES : CONGRUENT_TYPES, VIRTUAL_METHOD | Modifier.ABSTRACT | Opcodes.ACC_BRIDGE, 0));
		}
//		if (verify && foundMethods.isEmpty()) {
//			System.err.println("cn: " + cn);
//			System.err.println("name: " + name);
//			System.err.println("desc: " + desc);
//			System.err.println("Searched: " + toSearch);
//			System.err.println("Children: " + structures.getAllChildren(cn));
//			System.err.println("Parents: " + structures.getAllParents(cn));
//			throw new IllegalArgumentException("You must be really dense because that method doesn't even exist.");
//		}
		return foundMethods;
	}
	
	public FieldNode findField(ApplicationClassSource source, String owner, String name, String desc, boolean isStatic) {
		if(isStatic) {
			return findStaticField(source, owner, name, desc);
		} else {
			return findVirtualField(source, owner, name, desc);
		}
	}
	
	public FieldNode findStaticField(ApplicationClassSource app, String owner, String name, String desc) {
		Set<FieldNode> set = new HashSet<>();
		
		ClassNode cn = app.findClassNode(owner);
		
		/* we do this because static fields can be in
		 * interfaces. */
		if(cn != null) {
			Set<ClassNode> lvl = new HashSet<>();
			lvl.add(cn);
			for(;;) {
				if(lvl.size() == 0) {
					break;
				}
				
				Set<FieldNode> lvlSites = new HashSet<>();
				
				for(ClassNode c : lvl) {
					for(FieldNode f : c.fields) {
						if(Modifier.isStatic(f.access) && f.name.equals(name) && f.desc.equals(desc)) {
							lvlSites.add(f);
						}
					}
				}
				
				if(lvlSites.size() > 1) {
					System.out.printf("(warn) resolved %s.%s %s to %s.%n", owner, name, desc, lvlSites);
				}
				
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
		
		if(set.size() > 1) {
			throw new UnsupportedOperationException(String.format("multi dispatch?: %s.%s %s results:%s", owner, name, desc, set));
		} else if(set.size() == 1) {
			return set.iterator().next();
		} else {
			return null;
		}
	}
	
	private FieldNode findVirtualField(ApplicationClassSource source, String owner, String name, String desc) {
		ClassNode cn = source.findClassNode(owner);
		
		if(cn != null) {
			do {
				for(FieldNode f : cn.fields) {
					if(!Modifier.isStatic(f.access) && f.name.equals(name) && f.desc.equals(desc)) {
						return f;
					}
				}
				
				cn = source.findClassNode(cn.superName);
			} while(cn != null);
		}
		
		return null;
	}
}