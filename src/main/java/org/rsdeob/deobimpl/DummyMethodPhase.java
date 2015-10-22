package org.rsdeob.deobimpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.collections.DirectedGraph;
import org.rsdeob.stdlib.deob.IPhase;
import org.rsdeob.stdlib.klass.ClassTree;

public abstract class DummyMethodPhase implements IPhase {

	public static final String KEY_ID = DummyMethodPhase.class.getCanonicalName();

	private int used = 0;
	
	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
		int total = 0;
		
		ClassTree tree = new ClassTree(cxt.getNodes().values());
		Map<ClassNode, DirectedGraph<MethodNode, MethodNode>> callgraphs = new HashMap<ClassNode, DirectedGraph<MethodNode, MethodNode>>();

		List<MethodNode> entries = new ArrayList<>();
		// find methods we want to keep and build callgraphs for all of the nodes
		for (ClassNode cn : tree) {
			total += cn.methods.size();
			entries.addAll(findRealCandidates(tree, cn));
			callgraphs.put(cn, new DirectedGraph<MethodNode, MethodNode>());
		}
		System.out.printf("   %d protected methods.%n", entries.size());
		// follow calls and graph called methods appending them to the reserved list.
		entries.forEach(e -> search(callgraphs, tree, e));

		// remove the ungraphed/unreserved methods
		for (ClassNode cn : tree) {
			ListIterator<MethodNode> lit = cn.methods.listIterator();
			while (lit.hasNext()) {
				MethodNode mn = lit.next();
				DirectedGraph<MethodNode, MethodNode> cg = callgraphs.get(cn);
				if (!cg.containsVertex(mn)) {
					lit.remove();
				}
			}
		}
		
		System.out.printf("   Found %d/%d used methods (removed %d dummy methods).%n", used, total, total - used);
	}

	private void search(Map<ClassNode, DirectedGraph<MethodNode, MethodNode>> callgraphs, ClassTree tree, MethodNode vertex) {
		DirectedGraph<MethodNode, MethodNode> cg = callgraphs.get(vertex.owner);
		if (cg == null) {
			throw new NullPointerException("unbuilt callgraph for " + vertex.owner);
		}

		if (cg.containsVertex(vertex))
			return;
		cg.addVertex(vertex);
		
		used++;
		
		outer: for (AbstractInsnNode ain : vertex.instructions.toArray()) {
			if (ain instanceof MethodInsnNode) {
				MethodInsnNode min = (MethodInsnNode) ain;
				if (tree.containsKey(min.owner)) {
					ClassNode cn = tree.getClass(min.owner);
					MethodNode edge = cn.getMethod(min.name, min.desc, min.opcode() == Opcodes.INVOKESTATIC);
					if (edge != null) {
						cg.addEdge(vertex, edge); // method is called, graph it
						search(callgraphs, tree, edge); // search outgoing calls from that method
						continue;
					}
					// do the same for all supertypes and superinterfaces
					for (ClassNode superNode : tree.getSupers(cn)) {
						MethodNode superedge = superNode.getMethod(min.name, min.desc, min.opcode() == Opcodes.INVOKESTATIC);
						if (superedge != null) {
							cg.addEdge(vertex, superedge);
							search(callgraphs, tree, superedge);
							continue outer;
						}
					}
				}
			}
		}
	}

	public List<MethodNode> findRealCandidates(ClassTree tree, ClassNode cn) {
		List<MethodNode> methods = new ArrayList<MethodNode>();
		for (MethodNode mn : cn.methods) {
			if (protectedMethod(tree, mn)) {
				methods.add(mn);
			}
		}
		return methods;
	}

	// entries.addAll(cs.getMethods(m -> m.name.length() > 2)); // need to do
	// this to check methods inherited from jdk
	// entries.addAll(cs.getMethods(cs::isInherited)); // inherited methods from
	// within the client
	protected abstract boolean protectedMethod(ClassTree tree, MethodNode mn);
}