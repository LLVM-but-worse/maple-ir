package diamondlookup._4;

import java.util.Collection;
import java.util.HashSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.InstalledRuntimeClassSource;
import org.mapleir.res.InvocationResolver4;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import diamondlookup.*;


public class InvocationResolverTest4 {

	ApplicationClassSource app;
	InvocationResolver4 resolver;
	
	@Before
	public void setUp() throws Exception {
		Collection<ClassNode> classes = new HashSet<>();
		/* load the app code */
		for (Class<?> c : new Class<?>[] { ISpeak.class, ISpeak2.class, ISpeak3.class, ISpeak4.class, ISpeak5.class,
			EmptySpeakImpl.class, EmptySpeakImplChild.class, EmptySpeakImplChild2.class, EmptySpeakImplChild3.class}) {
//		for (Class<?> c : new Class<?>[] { diamondlookup._5.A.class, diamondlookup._5.B.class,
//				diamondlookup._5.I1.class, diamondlookup._5.I2.class, diamondlookup._5.I3.class,
//				diamondlookup._5.J.class, diamondlookup._5.K.class, diamondlookup._5.X.class,
//				diamondlookup._5.Y.class }) {
			//		for (Class<?> c : new Class<?>[] {A.class, B.class, K.class, L1.class, L2.class, L3.class, R1.class, R2.class, R3.class, W.class, X.class}) {
			ClassReader cr = new ClassReader(name(c));
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);
			
			classes.add(cn);
		}
		
		app = new ApplicationClassSource("diamond-lookup-testapp", classes);
		app.addLibraries(new InstalledRuntimeClassSource(app));
		app.getClassTree();
		
		resolver = new InvocationResolver4(app);
	}

	public boolean isSuperOf(Class<?> c1, Class<?> c2) {
		return resolver.isSuperOf(app.findClassNode(name(c1)), app.findClassNode(name(c2)));
	}
	
	@Test
	public void chaintests() {
		
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
}
