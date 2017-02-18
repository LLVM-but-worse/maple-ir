package testcases;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.IRCallTracer;
import org.mapleir.ir.ControlFlowGraphDumper;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.call.CallTracer;
import org.mapleir.stdlib.deob.PassGroup;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import testcases.condbranch.ConditionalBranch1;

public class Test {

	private final ClassNode[] classNodes;
	private final PassGroup group;
	private final List<MethodProfile> profiles;
	
	public Test(ClassNode[] classNodes, PassGroup group, List<MethodProfile> profiles) {
		this.classNodes = classNodes;
		this.group = group;
		this.profiles = profiles;
	}

	public static void main(String[] args) throws Throwable {
		Test t = load(ConditionalBranch1.class);

		Set<ClassNode> classSet = new HashSet<>();
		for(ClassNode c : t.classNodes) {
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
		for(ClassNode cn : t.classNodes) {
			for(MethodNode m : cn.methods) {
				tracer.trace(m);
			}
		}
		
		t.group.accept(cxt, null, new ArrayList<>());
		
		for(ClassNode cn : t.classNodes) {
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
		
		// recompile
		for(Entry<MethodNode, ControlFlowGraph> e : cfgs.entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
			BoissinotDestructor.leaveSSA(cfg);
			cfg.getLocals().realloc(cfg);
			ControlFlowGraphDumper.dump(cfg, mn);
		}
		
		ClassLoader cl = createLoader(classSet);
		
		System.out.println("\n\nPost profiling::");
		
		for(MethodProfile p : t.profiles) {
			Method m = p.getCallee(cl);

			m.setAccessible(true);
			p.postResult = m.invoke(null, new Object[]{});
			
			if(p.preResult.equals(p.postResult)) {
				System.out.printf("  %s.%s passed (val=%s)%n", p.owner, p.name, p.preResult);
			} else {
				System.err.printf("  %s.%s failed (pre=%s, post=%s)%n", p.owner, p.name, p.preResult, p.postResult);
			}
		}
	}
	
	private static ClassLoader createLoader(Collection<ClassNode> classes) {
		Map<String, ClassNode> map = new HashMap<>();
		for(ClassNode cn : classes) {
			map.put(cn.name, cn);
		}
		
		return new ClassLoader() {
			@Override
			public Class<?> findClass(String name) throws ClassNotFoundException {
				String bname = name.replace(".", "/");
				
				if(map.containsKey(bname)) {
					return define(map.get(bname), name);
				} else {
					return super.findClass(name);
				}
			}
			
			private Class<?> define(ClassNode cn, String name) {
				ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
				cn.accept(cw);
				
				byte[] bs = cw.toByteArray();
				
				return defineClass(name, bs, 0, bs.length);
			}
		};
	}
	private static Test load(Class<?> c) throws Throwable {
		Method getClasses = c.getDeclaredMethod("getClasses", new Class<?>[]{});
		Class<?>[] vals = (Class<?>[]) getClasses.invoke(null, new Object[]{});
		
		ClassNode[] cns = new ClassNode[vals.length];
		for(int i=0; i < vals.length; i++) {
			ClassNode cn = new ClassNode();
			ClassReader cr = new ClassReader(vals[i].getName());
			cr.accept(cn, 0);
			cns[i] = cn;
		}
		

		Method getPasses = c.getDeclaredMethod("getPasses", new Class<?>[]{});
		PassGroup passes = (PassGroup) getPasses.invoke(null, new Object[]{});
		
		return new Test(cns, passes, profile(vals));
	}
	
	private static List<MethodProfile> profile(Class<?>[] classes) throws Throwable {
		List<MethodProfile> profiles = new ArrayList<>();
		
		for(Class<?> c : classes) {
			for(Method m : c.getDeclaredMethods()) {
				CheckReturn anno = m.getDeclaredAnnotation(CheckReturn.class);
				
				if(anno != null) {
					
					if(m.getParameterTypes().length > 0) {
						System.err.println("Invalid: " + m);
						continue;
					}
					
					MethodProfile p = new MethodProfile(c.getName(), m.getName());
					profiles.add(p);
					
					m.setAccessible(true);
					p.preResult = m.invoke(null, new Object[]{});
				}
			}
		}
		
		return profiles;
	}

	private static class MethodProfile {
		private final String owner;
		private final String name;
		
		private Object preResult;
		private Object postResult;
		
		public MethodProfile(String owner, String name) {
			this.owner = owner;
			this.name = name;
		}
		
		public Method getCallee(ClassLoader cl) throws Throwable, SecurityException {
			Class<?> c = cl.loadClass(owner);
			return c.getDeclaredMethod(name, new Class<?>[0]);
		}
	}
	
	// external
//	public static String[] getNames(Class<?>... cls) {
//		String[] ss = new String[cls.length];
//		for(int i=0; i < cls.length; i++) {
//			ss[i] = cls[i].getName();
//		}
//		return ss;
//	}
}