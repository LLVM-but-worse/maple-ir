package diamondlookup._4;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.InstalledRuntimeClassSource;
import org.mapleir.res.InvocationResolver4;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import diamondlookup._5.J2;
import diamondlookup._5.K2;
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
				diamondlookup._5.N.class, diamondlookup._5.O.class, V.class, V2.class, J2.class, K2.class, J2.class,
				};
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
		return resolver.resolve(app.findClassNode(name(receiver)), name, desc, false);
	}
	
	@Test
	public void chaintests() throws Exception {
		for (Class<?> c : classes) {
			// if(!Modifier.isAbstract(c.getModifiers())) {

//			if(c != J.class)
//				continue;
			System.out.println(c);
			for (Method m : c.getMethods()) {
//				System.out.println(Arrays.toString(c.getDeclaredMethods()));
//				System.out.println(Arrays.toString(c.getMethods()));
//				if(!Modifier.isAbstract(m.getModifiers())) {
					
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
					 if(col.size() > 1) {
						 // miranda
						 assertTrue(res.owner.name.equals(c.getName().replace(".", "/")));
					 } else {
						 assertTrue(res.owner.name.equals(real.getName().replace(".", "/")));
					 }
				}
//			}
			System.out.println();
		}
	}

	@After
	public void tearDown() throws Exception {
		app = null;
		resolver = null;
	}
	
	private static String name(Class<?> c) {
		return c.getName().replace(".", "/");
	}
}
