package org.mapleir;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.byteio.CompleteResolvingJarDumper;
import org.mapleir.deobimpl2.ConstantExpressionReordererPhase;
import org.mapleir.ir.ControlFlowGraphDumper;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InitialisedObjectExpression;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.klass.ClassTree;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;
import org.topdank.byteio.out.JarDumper;

public class Boot {

	private static Map<MethodNode, ControlFlowGraph> cfgs;
	private static long timer;
	
	private static double lap() {
		long now = System.nanoTime();
		long delta = now - timer;
		timer = now;
		return (double)delta / 1_000_000_000L;
	}
	
	public static void main(String[] args) throws IOException {
		cfgs = new HashMap<>();
		
		/* if(args.length < 1) {
			System.err.println("Usage: <rev>");
			System.exit(1);
			return;
		} */
		
		File f = locateRevFile(107);
		System.out.println("Preparing to run on " + f.getAbsolutePath());
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
	
		ClassTree tree = new ClassTree(dl.getJarContents().getClassContents());
		
		lap();
		
		for(MethodNode m : findEntries(tree)) {
			build(tree, m);
		}
		
		System.out.println("Built " + cfgs.size() + " graphs. (" + lap() + " s.)");
		
		for(ClassNode cn : tree.getClasses().values()) {
			ListIterator<MethodNode> lit = cn.methods.listIterator();
			while(lit.hasNext()) {
				MethodNode m = lit.next();
				if(!cfgs.containsKey(m)) {
					lit.remove();
				}
			}
		}
		
		System.out.println("Pruned callgraphs. (" + lap() + " s.)");
		
		IContext cxt = new IContext() {
			@Override
			public ClassTree getClassTree() {
				return tree;
			}

			@Override
			public ControlFlowGraph getIR(MethodNode m) {
				return cfgs.get(m);
			}

			@Override
			public Set<MethodNode> getActiveMethods() {
				return cfgs.keySet();
			}
		};
		ConstantExpressionReordererPhase phase = new ConstantExpressionReordererPhase();
		phase.accept(cxt, null, new ArrayList<>());
		
		System.out.println("Transformed methods. (" + lap() + " s.)");
		
		for(Entry<MethodNode, ControlFlowGraph> e : cfgs.entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
			BoissinotDestructor.leaveSSA(cfg);
			cfg.getLocals().realloc(cfg);
			ControlFlowGraphDumper.dump(cfg, mn);
		}
		
		System.out.println("Ran SSA destructor on " + cfgs.size() + " methods (" + lap() + " s.)");
		
		System.out.println("Rewriting jar.");
		
		JarDumper dumper = new CompleteResolvingJarDumper(dl.getJarContents());
		dumper.dump(new File("out/osb.jar"));
	}
	
	private static File locateRevFile(int rev) {
		return new File("res/gamepack" + rev + ".jar");
	}
	
	private static Set<MethodNode> findEntries(ClassTree tree) {
		Set<MethodNode> set = new HashSet<>();
		for(ClassNode cn : tree.getClasses().values())  {
			for(MethodNode m : cn.methods) {
				if(m.name.length() > 2) {
					set.add(m);
				}
			}
		}
		return set;
	}
	
	private static void build(ClassTree tree, MethodNode m) {
		if(cfgs.containsKey(m)) {
			return;
		} else if(tree.isJDKClass(m.owner)) {
			return;
		}
		
		ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
		cfgs.put(m, cfg);
		
		for(BasicBlock b : cfg.vertices()) {
			for(Stmt stmt : b) {
				for(Expr c : stmt.enumerateOnlyChildren()) {
					if(c.getOpcode() == Opcode.INVOKE) {
						InvocationExpression invoke = (InvocationExpression) c;
						
						boolean isStatic = (invoke.getCallType() == Opcodes.INVOKESTATIC);
						String owner = invoke.getOwner();
						String name = invoke.getName();
						String desc = invoke.getDesc();
						
						if(isStatic) {
							MethodNode call = resolveStaticCall(tree, owner, name, desc);
							if(call != null) {
								build(tree, call);
							}
						} else {
							for(MethodNode vtarg : resolveVirtualCalls(tree, owner, name, desc)) {
								if(vtarg != null) {
									build(tree, vtarg);
								}
							}
						}
					} else if(c.getOpcode() == Opcode.INIT_OBJ) {
						InitialisedObjectExpression init = (InitialisedObjectExpression) c;
						MethodNode call = resolveVirtualInitCall(tree, init.getOwner(), init.getDesc());
						if(call != null) {
							build(tree, call);
						}
					}
				}
			}
		}
	}
	
	private static MethodNode resolveVirtualInitCall(ClassTree tree, String owner, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = tree.getClass(owner);
		
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
				throw new IllegalStateException(set.toString());
			}
		} else {
			return null;
		}
	}
	
	private static MethodNode resolveVirtualCall(ClassTree tree, ClassNode cn, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		for(MethodNode m : cn.methods) {
			if((m.access & Opcodes.ACC_STATIC) == 0) {
				if(m.name.equals(name) && m.desc.equals(desc)) {
					set.add(m);
				}
			}
		}
		
		if(set.size() > 1) {
			throw new IllegalStateException(cn.name + "." + name + " " + desc + " => " + set);
		}
		
		if(set.size() == 1) {
			return set.iterator().next();
		} else {
			return null;
		}
	}
	
	private static Set<MethodNode> resolveVirtualCalls(ClassTree tree, String owner, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = tree.getClass(owner);
		
		if(cn != null) {
			MethodNode m = resolveVirtualCall(tree, cn, name, desc);
			if(m != null) {
				set.add(m);
			}
			
			for(ClassNode subC : tree.getSupers(cn)) {
				m = resolveVirtualCall(tree, subC, name, desc);
				if(m != null) {
					set.add(m);
				}
			}
			
			for(ClassNode subC : tree.getDelegates(cn)) {
				m = resolveVirtualCall(tree, subC, name, desc);
				if(m != null) {
					set.add(m);
				}
			}
			
			return set;
			// throw new IllegalStateException(cn.name + "." + name + " " + desc);
		}
		
		return set;
	}
	
	private static MethodNode resolveStaticCall(ClassTree tree, String owner, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = tree.getClass(owner);
		
		if(cn != null) {
			for(MethodNode m : cn.methods) {
				if((m.access & Opcodes.ACC_STATIC) != 0) {
					if(m.name.equals(name) && m.desc.equals(desc)) {
						set.add(m);
					}
				}
			}
			
			if(set.size() == 0) {
				return resolveStaticCall(tree, cn.superName, name, desc);
			} else if(set.size() == 1) {
				return set.iterator().next();
			} else {
				throw new IllegalStateException(owner + "." + name + " " + desc + ",   " + set.toString());
			}
		} else {
			return null;
		}
	}
}