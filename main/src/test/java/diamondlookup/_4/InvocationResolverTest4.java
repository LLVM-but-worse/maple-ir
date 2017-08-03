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

public class InvocationResolverTest4 {

	ApplicationClassSource app;
	InvocationResolver4 resolver;
	
	@Before
	public void setUp() throws Exception {
		Collection<ClassNode> classes = new HashSet<>();
		/* load the app code */
		for (Class<?> c : new Class<?>[] {A.class, B.class, K.class, L1.class, L2.class, L3.class, R1.class, R2.class, R3.class, W.class, X.class}) {
			ClassReader cr = new ClassReader(name(c));
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);
			
			classes.add(cn);
		}
		
		app = new ApplicationClassSource("diamond-lookup-testapp", classes);
		app.addLibraries(new InstalledRuntimeClassSource(app));
		System.out.println(app.getClassTree());
		
		resolver = new InvocationResolver4(app);
	}

	@Test
	public void chaintests() {
		System.out.println(app.findClassNode("java/lang/Object") == app.getClassTree().getRootNode());
//		for(ClassNode c : app.getClassTree().vertices()) {
//			System.out.println(c);
//			
//			for(InheritanceEdge e : app.getClassTree().getEdges(c)) {
//				System.out.println(e);
//			}
//			
//			for(InheritanceEdge e : app.getClassTree().getReverseEdges(c)) {
//				System.out.println(e);
//			}
//		}
		long start = System.nanoTime();
		resolver.computeTimes();
		long el = System.nanoTime() - start;
		System.out.println((double) el/1_000_000 + "ms");
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
