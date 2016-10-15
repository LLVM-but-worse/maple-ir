package org.mapleir.stdlib.call;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.mapleir.stdlib.call.CallGraph.Invocation;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.klass.ClassTree;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class CallGraph extends FastDirectedGraph<MethodNode, Invocation> {
	
	private final CallgraphAdapter adapter;
	private final ClassTree classTree;
	
	public CallGraph(CallgraphAdapter adapter, ClassTree classTree) {
		this.adapter = adapter;
		this.classTree = classTree;
		
		reduce();
	}
	
	public ClassTree getTree() {
		return classTree;
	}

	private List<MethodNode> findEntries(ClassTree tree, ClassNode cn) {
		List<MethodNode> methods = new ArrayList<>();
		for (MethodNode mn : cn.methods) {
			if (adapter.shouldMap(this, mn)) {
				methods.add(mn);
			}
		}
		return methods;
	}
	
	private void reduce() {
		// TODO: removal analytics/information.
		Set<MethodNode> prot = new HashSet<>();
		int total = 0, removed = 0;
		int lastRemoved = 0, i = 1;

		do {
			lastRemoved = removed;
			List<MethodNode> entries = new ArrayList<>();
			for(ClassNode cn : classTree.getClasses().values()) {
				if (i == 1) {
					total += cn.methods.size();
				}
				entries.addAll(findEntries(classTree, cn));
			}
			prot.addAll(entries);
			for(MethodNode m : entries) {
				traverse(m);
			}
			
			for (ClassNode cn : classTree) {
				ListIterator<MethodNode> lit = cn.methods.listIterator();
				while (lit.hasNext()) {
					MethodNode mn = lit.next();
					if(!entries.contains(mn) && !containsReverseVertex(mn)) {
						lit.remove();
						removed++;
					}
				}
			}
			
			int d = removed - lastRemoved;
			if(d > 0) {
				clear();
				System.out.printf("   Pass %d: removed %d methods%n", i, d);
			}
			
			i++;
		} while((removed - lastRemoved) != 0);

		System.out.printf("   %d protected methods.%n", prot.size());
		System.out.printf("   Found %d/%d used methods (removed %d dummy methods).%n", (total - removed), total, removed);
	}
	
	private void traverse(MethodNode m) {
		if(containsVertex(m) && getEdges(m).size() > 0) {
			return;
		}
		
		addVertex(m);
		
		outer: for(AbstractInsnNode ain : m.instructions.toArray()) {
			if(ain instanceof MethodInsnNode) {
				MethodInsnNode min = (MethodInsnNode) ain;
				if (classTree.containsKey(min.owner)) {
					ClassNode cn = classTree.getClass(min.owner);
					MethodNode edge = cn.getMethod(min.name, min.desc, min.opcode() == Opcodes.INVOKESTATIC);
					if (edge != null) {
						Invocation invocation = new Invocation(m, edge);
						addEdge(m, invocation);
						traverse(edge);
						continue;
					}
					for (ClassNode superNode : classTree.getSupers(cn)) {
						MethodNode superedge = superNode.getMethod(min.name, min.desc, min.opcode() == Opcodes.INVOKESTATIC);
						if (superedge != null) {
							Invocation invocation = new Invocation(m, superedge);
							addEdge(m, invocation);
							traverse(superedge);
							continue outer;
						}
					}
				}
			}
		}
	}

	public static class Invocation extends FastGraphEdge<MethodNode> {
		public Invocation(MethodNode caller, MethodNode callee) {
			super(caller, callee);
		}
	}
	
	public static interface CallgraphAdapter {
		boolean shouldMap(CallGraph graph, MethodNode m);
	}

	@Override
	public Invocation clone(Invocation edge, MethodNode n, MethodNode newN) {
		// TODO:
		throw new UnsupportedOperationException();
	}
	
	@Override
	public Invocation invert(Invocation edge) {
		// TODO:
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean excavate(MethodNode n) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean jam(MethodNode prev, MethodNode succ, MethodNode n) {
		throw new UnsupportedOperationException();
	}

	@Override
	public FastGraph<MethodNode, Invocation> copy() {
		throw new UnsupportedOperationException();
	}
}