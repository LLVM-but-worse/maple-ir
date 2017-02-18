package testcases;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mapleir.IRCallTracer;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.call.CallTracer;
import org.mapleir.stdlib.deob.PassGroup;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import testcases.condbranch.ConditionalBranch1;

public class Test {

	private final ClassNode[] classes;
	private final PassGroup group;
	
	public Test(ClassNode[] classes, PassGroup passes) {
		this.classes = classes;
		group = passes;
	}

	public static void main(String[] args) throws Throwable {
		Test t = load(ConditionalBranch1.class);

		Set<ClassNode> classSet = new HashSet<>();
		for(ClassNode c : t.classes) {
			classSet.add(c);
		}
		ClassTree tree = new ClassTree(classSet);
		
		Map<MethodNode, ControlFlowGraph> cfgs = new HashMap<>();
		InvocationResolver resolver = new InvocationResolver(tree);
		
		IContext cxt = new IContext() {
			@Override
			public ClassTree getClassTree() {
				return tree;
			}

			@Override
			public ControlFlowGraph getIR(MethodNode m) {
				if(cfgs.containsKey(m)) {
					return cfgs.get(m);
				} else {
					ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
					cfgs.put(m, cfg);
					return cfg;
				}
			}

			@Override
			public Set<MethodNode> getActiveMethods() {
				return cfgs.keySet();
			}

			@Override
			public InvocationResolver getInvocationResolver() {
				return resolver;
			}
		};
		
		CallTracer tracer = new IRCallTracer(cxt) {
			@Override
			protected void processedInvocation(MethodNode caller, MethodNode callee, Expr call) {
			}
		};
		for(ClassNode cn : t.classes) {
			for(MethodNode m : cn.methods) {
				tracer.trace(m);
			}
		}
		
		t.group.accept(cxt, null, new ArrayList<>());
		
		for(ClassNode cn : t.classes) {
			for(MethodNode m : cn.methods) {
				if(m.visibleAnnotations != null && m.visibleAnnotations.size() > 0) {
					List<AnnotationNode> annos = m.visibleAnnotations;
					
					for(AnnotationNode n : annos) {
						if(n.desc.equals("Ltestcases/TestMethod;")) {
							System.out.println();
							System.out.println();
							System.out.printf("%s, static=%b.%n", m, Modifier.isStatic(m.access));
							System.out.println(cxt.getIR(m));
						}
					}
				}
			}
		}
	}

	private static Test load(Class<?> c) throws Throwable {
		Method getClasses = c.getDeclaredMethod("getClasses", new Class<?>[]{});
		String[] vals = (String[]) getClasses.invoke(null, new Object[]{});
		
		ClassNode[] cns = new ClassNode[vals.length];
		for(int i=0; i < vals.length; i++) {
			ClassNode cn = new ClassNode();
			ClassReader cr = new ClassReader(vals[i]);
			cr.accept(cn, 0);
			cns[i] = cn;
		}
		

		Method getPasses = c.getDeclaredMethod("getPasses", new Class<?>[]{});
		PassGroup passes = (PassGroup) getPasses.invoke(null, new Object[]{});
		
		return new Test(cns, passes);
	}
	
	// external
	public static String[] getNames(Class<?>... cls) {
		String[] ss = new String[cls.length];
		for(int i=0; i < cls.length; i++) {
			ss[i] = cls[i].getName();
		}
		return ss;
	}
}