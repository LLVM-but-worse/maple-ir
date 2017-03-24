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
	
	public static Set<MethodNode> getHierarchyMethodChain(IContext cxt, ClassNode cn, String name, String desc, boolean verify) {
		ApplicationClassSource app = cxt.getApplication();
		ClassTree structures = app.getStructures();
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		Set<MethodNode> foundMethods = new HashSet<>();
		Collection<ClassNode> toSearch = structures.getAllBranches(cn);
		for (ClassNode viable : toSearch) {
			MethodNode found = resolver.findMethod(viable, name, desc, true, true);
			if (found != null) {
				foundMethods.add(found);
			}
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
	
	private MethodNode findMethod(ClassNode cn, String name, String desc, boolean congruentReturn, boolean allowAbstract) {
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
						
						throw new IllegalStateException(String.format("%s contains %s(br=%b) and %s(br=%b)", cn.name, findM, m, (findM.access & Opcodes.ACC_BRIDGE) != 0, (m.access & Opcodes.ACC_BRIDGE) != 0));
					}
					
					findM = m;
				}
			}
		}
		
		return findM;
	}

	public Set<MethodNode> findCongruentMethods(ClassNode cn, String name, String desc, boolean allowAbstract) {
		Set<MethodNode> res = new HashSet<>();
		for(MethodNode m : cn.methods) {
			if(!Modifier.isStatic(m.access) && (allowAbstract || !Modifier.isAbstract(m.access))) {
				if(m.name.equals(name) && (m.desc.equals(desc) || isCongruent(desc, m.desc))) {
					res.add(m);
				}
			}
		}
		return res;
	}
	
	public MethodNode findExactClassMethod(ClassNode cn, String name, String desc, boolean allowAbstract) {
		return findMethod(cn, name, desc, false, allowAbstract);
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