package org.mapleir.deobimpl;

import java.util.List;

import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.call.CallGraph;
import org.mapleir.stdlib.call.CallGraph.CallgraphAdapter;
import org.mapleir.stdlib.deob.IPhase;
import org.mapleir.stdlib.klass.ClassTree;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class DummyMethodPhase implements IPhase {

	public static final String KEY_ID = DummyMethodPhase.class.getCanonicalName();
	private CallGraph callgraph;
	
	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
		callgraph = new CallGraph(new CallgraphAdapter() {
			@Override
			public boolean shouldMap(CallGraph graph, MethodNode m) {
				return protectedMethod(graph.getTree(), m);
			}
		}, new ClassTree(cxt.getNodes().values()));
	}
	
	public CallGraph getCallgraph() {
		return callgraph;
	}
	
	protected boolean protectedMethod(ClassTree tree, MethodNode mn) {
		return mn.name.length() > 2 || isInherited(tree, mn.owner, mn);
	}
	
	private MethodNode getMethodFromSuper(ClassTree tree, ClassNode cn, String name, String desc, boolean isStatic) {
		for (ClassNode super_ : tree.getSupers(cn)) {
			for (MethodNode mn : super_.methods) {
				if (mn.name.equals(name) && mn.desc.equals(desc) && ((mn.access & Opcodes.ACC_STATIC) != 0) == isStatic) {
					return mn;
				}
			}
		}
		return null;
	}
	
	private boolean isInherited(ClassTree tree, ClassNode cn, String name, String desc, boolean isStatic) {
		return getMethodFromSuper(tree, cn, name, desc, isStatic) != null;
	}

	private boolean isInherited(ClassTree tree, ClassNode owner, MethodNode mn) {
		if(owner == null) {
			throw new NullPointerException();
		}
		return mn.owner.name.equals(owner.name) && isInherited(tree, owner, mn.name, mn.desc, (mn.access & Opcodes.ACC_STATIC) != 0);
	}
}