package diamondlookup.maple;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import diamondlookup.*;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mapleir.InvocationResolver2;
import org.mapleir.SimpleInvocationResolver;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.InstalledRuntimeClassSource;
import org.mapleir.app.service.InvocationResolver;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;

public class InvocationResolverTest {

	ApplicationClassSource app;
	InvocationResolver resolver;
	
	@Before
	public void setUp() throws Exception {
		Collection<ClassNode> classes = new HashSet<>();
		/* load the app code */
		for (Class<?> c : new Class<?>[] { ISpeak.class, ISpeak2.class, ISpeak3.class, ISpeak4.class, ISpeak5.class,
				EmptySpeakImpl.class, EmptySpeakImplChild.class, EmptySpeakImplChild2.class, EmptySpeakImplChild3.class,
				DiamondLookupTest.class }) {
			
			ClassReader cr = new ClassReader(name(c));
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);
			
			classes.add(cn);
		}
		
		app = new ApplicationClassSource("diamond-lookup-testapp", classes);
		app.addLibraries(new InstalledRuntimeClassSource(app));
		app.getClassTree();
		
		resolver = new InvocationResolver2(app);
	}

	@Test
	public void testResolveVirtualCalls() {
		ClassNode cn = app.findClassNode(name(DiamondLookupTest.class));
		for(MethodNode m : cn.methods) {
			if(m.name.equals("test")) {
				ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
				for(Stmt stmt : cfg.stmts()) {
					for(Expr e : stmt.enumerateOnlyChildren()) {
						if(e.getOpcode() == Opcode.INVOKE) {
							InvocationExpr ie = (InvocationExpr) e;
							
							if(ie.getOwner().equals("org/junit/Assert") && ie.getName().equals("assertEquals") && ie.getDesc().equals("(Ljava/lang/Object;Ljava/lang/Object;)V")) {
								InvocationExpr arg1 = (InvocationExpr) ie.getArgumentExprs()[0];
								String arg2 = (String) ((ConstantExpr) ie.getArgumentExprs()[1]).getConstant();

								Class correctResolution;
								List<String> callResults = arg1.resolveTargets(resolver).stream().map(mn -> mn.owner.name.substring(mn.owner.name.lastIndexOf('/') + 1) + " Speaking!").collect(Collectors.toList());
								assertThat(callResults, hasItem(arg2));
							}
						}
					}
				}
			}
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
