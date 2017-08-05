package diamondlookup._4;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.ClassSource;
import org.mapleir.app.service.InstalledRuntimeClassSource;
import org.mapleir.app.service.LibraryClassSource;
import org.mapleir.app.service.LocateableClassNode;
import org.mapleir.ir.TypeUtils;
import org.mapleir.res.InvocationResolver4;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import diamondlookup._5.J2;
import diamondlookup._5.V;
import diamondlookup._5.V2;


public class InvocationResolverTest4 {

	ApplicationClassSource app;
	ApplicationClassSource rt;
	InvocationResolver4 resolver;
	Class<?>[] classes;
	
	@Before
	public void setUp() throws Exception {
		Collection<ClassNode> nodes = new HashSet<>();
		/* load the app code */
//		 classes = new Class<?>[] { ISpeak.class, ISpeak2.class,
//		 ISpeak3.class, ISpeak4.class, ISpeak5.class,
//		 EmptySpeakImpl.class, EmptySpeakImplChild.class,
//		 EmptySpeakImplChild2.class, EmptySpeakImplChild3.class};
		classes = new Class<?>[] { diamondlookup._5.A.class, diamondlookup._5.B.class, diamondlookup._5.I1.class,
				diamondlookup._5.I2.class, diamondlookup._5.I3.class, diamondlookup._5.J.class,
				diamondlookup._5.K.class, diamondlookup._5.X.class, diamondlookup._5.Y.class, diamondlookup._5.M.class,
				diamondlookup._5.N.class, diamondlookup._5.O.class, V.class, V2.class, J2.class };
//		classes = new Class<?>[] { A.class, B.class, K.class, L1.class, L2.class, L3.class, R1.class, R2.class,
//				R3.class, W.class, X.class };
				
		for (Class<?> c : classes) {
			ClassReader cr = new ClassReader(name(c));
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);

			nodes.add(cn);
		}
		
		app = new ApplicationClassSource("diamond-lookup-testapp", nodes);
		app.addLibraries(new InstalledRuntimeClassSource(app));
		app.getClassTree();
		
		{
//			System.out.println("loading rt");
//			File rtjar = new File("res/rt.jar");
//			SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(rtjar));
//			dl.download();
//			System.out.println("got");
//			rt = new ApplicationClassSource("rt-src", dl.getJarContents().getClassContents());
//			rt.addLibraries(new InstalledRuntimeClassSource(rt));// for outside classes
//			rt.getClassTree();
//			System.out.println("made rt tree");
		}
		
		resolver = new InvocationResolver4(app);
	}

	public boolean isSuperOf(Class<?> c1, Class<?> c2) {
		return resolver.isSuperOf(app.findClassNode(name(c1)), app.findClassNode(name(c2)));
	}
	

	public MethodNode resolve(Class<?> receiver, String name, String desc) {
		return resolver.resolve(app.findClassNode(name(receiver)), name, desc);
	}
	
	@Test
	public void chaintests() throws Exception {

//		for(ClassNode cn : rt.getClassTree().vertices()) {
//			if(rt.getClassTree().getChildren(cn).size() == 0) {
//				System.out.println("leaf " + cn);
//			}
//		}
		
//		assertTrue(isSuperOf(R3.class, R1.class));
//		assertTrue(isSuperOf(R3.class, R2.class));
//		assertFalse(isSuperOf(R1.class, R3.class));
//		assertFalse(isSuperOf(R2.class, R3.class));
//		
//		// L and R are always unrelated (diff branches from obj)
//		for(Class<?> r : new Class<?>[] {R1.class, R2.class, R3.class}) {
//			for(Class<?> l : new Class<?>[] {L1.class, L2.class, L3.class}) {
//				assertFalse(isSuperOf(r, l));
//				assertFalse(isSuperOf(l, r));
//			}
//		}
		
		// long start = System.nanoTime();
//		resolver.computeTimes();
//		resolver.computeStats();
		resolver.v3();

		for (Class<?> c : classes) {
			// if(!Modifier.isAbstract(c.getModifiers())) {

//			if(c != J.class)
//				continue;
			System.out.println(c);
			for (Method m : c.getMethods()) {
//				System.out.println(Arrays.toString(c.getDeclaredMethods()));
//				System.out.println(Arrays.toString(c.getMethods()));
				if(!Modifier.isAbstract(m.getModifiers())) {
					
					Collection<Method> col = new HashSet<>();
					for(Method m2 : c.getMethods()) {
						if(m2.getName().equals(m.getName()) && Arrays.equals(m2.getParameterTypes(), m.getParameterTypes())) {
							col.add(m2);
						}
					}
					
					if(col.size() > 1) {
						System.out.println("contention: " + col);
					}
					
					StringBuilder descBuilder = new StringBuilder();
					 descBuilder.append('(');
					 for(Class<?> paramClass : m.getParameterTypes()) {
						 descBuilder.append(Type.getType(paramClass).getDescriptor());
					 }
					 descBuilder.append(')').append(Type.getType(m.getReturnType()).getDescriptor());
					 
					 Class<?> real = m.getDeclaringClass();
					 MethodNode res = resolve(c, m.getName(), descBuilder.toString());
					 
					 if(real != Object.class) {
						 System.out.println(m + " -> " + res);
					 }
					 assertTrue(res.owner.name.equals(real.getName().replace(".", "/")));
				}
			}
			System.out.println();
			
			//
			// System.out.println(c + " -> " + res);
			// assertTrue(res.owner.name.equals(real.getName().replace(".", "/")));
			// }
		}

//		System.out.println(app.getClassTree());
//		for(ClassNode c : app.getClassTree().vertices()) {
//			System.out.println(c + " " + resolver.colours.get(c));
//		}
		
//		for(List<ClassNode> l : resolver.chains) {
//			System.out.println(l);
//		}
		
//		app.getClassTree().makeDotWriter().setName("classtree1").export();
//		
//		// long el = System.nanoTime() - start;
//		// System.out.println((double) el/1_000_000 + "ms");
//		assertEquals(resolver.timer, app.getClassTree().size() * 2);
//		assertTrue(t(L3.class, L1.class));
//		assertTrue(t(L2.class, L1.class));
//		
//		assertFalse(t(L2.class, L3.class));
//		assertFalse(t(L3.class, L2.class));
//
//		assertTrue(t(A.class, L2.class));
//		assertTrue(t(A.class, L1.class));
//		
//		assertTrue(t(B.class, L1.class));
//		assertTrue(t(B.class, L2.class));
//		assertTrue(t(B.class, R1.class));
	}
	
	private boolean t(Class<?> u, Class<?> v) {
		return resolver.isDirectChainRelated(app.findClassNode(name(u)), app.findClassNode(name(v)));
	}

	@After
	public void tearDown() throws Exception {
		app = null;
		resolver = null;
	}
	
	private static String name(Class<?> c) {
		return c.getName().replace(".", "/");
	}
	
	private static ApplicationClassSource mock(ApplicationClassSource app) {
		AppSourceCloneBuilder builder = new AppSourceCloneBuilder(app);
		return builder.appClone;
	}
	
	@Deprecated
	private static class AppSourceCloneBuilder {
		private final ApplicationClassSource app;
		private final Map<ClassNode, ClassNode> rebuildCache;
		private final ApplicationClassSource appClone;
		
		public AppSourceCloneBuilder(ApplicationClassSource app) {
			this.app = app;
			rebuildCache = new HashMap<>();
			
			// System.out.println("pre:");
			// printApp(app);
			appClone = rebuild(app);
			// System.out.println("post:");
			// printApp(appClone);
		
		}
		
		static void printApp(ApplicationClassSource app) {
			print(app);
			for(LibraryClassSource lib : app.getLibraries()) {
				print(lib);
			}
		}
		
		static void print(ClassSource source) {
			System.out.println("from " + source);
			for(ClassNode cn : source.iterate()) {
				System.out.println(" " + cn);
			}
		}
		
		private ClassNode rebuild(ClassNode c) {
			if(rebuildCache.containsKey(c)) {
				throw new IllegalStateException(String.format("Already processed %s (competing defs?)", c));
			} else {
				ClassNode newKlass = new ClassNode();
				newKlass.version = c.version;
				newKlass.access = c.access;
				newKlass.name = c.name;
				newKlass.superName = c.superName;
				c.interfaces.addAll(c.interfaces);
				newKlass.signature = c.signature;
				
				for(MethodNode m : c.methods) {
					MethodNode newMethod = new MethodNode(newKlass);
					newMethod.access = m.access;
					newMethod.name = m.name;
					newMethod.desc = m.desc;
					newMethod.signature = m.signature;
					if(newMethod.exceptions == null) {
						newMethod.exceptions = new ArrayList<>();
					}
					newMethod.exceptions.addAll(m.exceptions);
					
					if(!Modifier.isAbstract(m.access)) {
						Type returnType = Type.getReturnType(m.desc);
						
						InsnList insns = newMethod.instructions;
						
						if(returnType == Type.VOID_TYPE) {
							insns.add(new InsnNode(Opcodes.RETURN));
						} else {
							switch(returnType.getSort()) {
								case Type.INT: 
								case Type.SHORT:
								case Type.BYTE:
								case Type.BOOLEAN:
								case Type.CHAR: 
								case Type.FLOAT:
								case Type.DOUBLE:
								case Type.LONG: {
									insns.add(new InsnNode(Opcodes.ICONST_0));
									for(int opc : TypeUtils.getPrimitiveCastOpcodes(Type.INT_TYPE, returnType)) {
										insns.add(new InsnNode(opc));
									}
									insns.add(new InsnNode(TypeUtils.getReturnOpcode(returnType)));
									break;
								}
								case Type.ARRAY:
								case Type.OBJECT: {
									insns.add(new InsnNode(Opcodes.ACONST_NULL));
									insns.add(new InsnNode(Opcodes.ARETURN));
									break;
								}
								default: {
									throw new IllegalStateException(m.toString());
								}
							}
						}
					}
					
					newKlass.methods.add(newMethod);
					// don't need to mock fields
				}
				
				rebuildCache.put(c, newKlass);
				return newKlass;
			}
		}
		
		private Collection<ClassNode> rebuildSource(ClassSource src) {
			Collection<ClassNode> nodes = new HashSet<>();
			
			for(ClassNode c : src.iterate()) {
				LocateableClassNode locNode = src.findClass(c.name);
				
				if(locNode.source != src) {
					throw new IllegalStateException(String.format("iterate of %s returned %s", src, locNode));
				}
				
				nodes.add(rebuild(c));
			}
			return nodes;
		}
		
		private ApplicationClassSource rebuild(ApplicationClassSource source) {
			ApplicationClassSource appClone = new ApplicationClassSource(source.getName() + "-clone", rebuildSource(source));
			for(LibraryClassSource lib : source.getLibraries()) {
				LibraryClassSource cloneLib = new LibraryClassSource(appClone, rebuildSource(lib)) {
					@Override
					public String toString() {
						return lib.toString() + "-clone";
					}
				};
				appClone.addLibraries(cloneLib);
			}
			return appClone;
		}
	}
}
