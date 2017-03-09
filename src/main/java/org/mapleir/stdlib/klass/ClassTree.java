package org.mapleir.stdlib.klass;

import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.app.LocateableClassNode;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.klass.ClassTree.InheritanceEdge;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * A graph to represent the inheritance tree.
 * The graph follows the convention of anti-arborescence, i.e. edges point towards the root (Object).
 * @see <a href=https://en.wikipedia.org/wiki/Tree_(graph_theory)>Wikipedia: Tree</a>
 */
public class ClassTree extends FastDirectedGraph<ClassNode, InheritanceEdge> {
	private final ApplicationClassSource source;
	private final ClassNode rootNode;
	
	public ClassTree(ApplicationClassSource source) {
		this.source = source;
		rootNode = findClass("java/lang/Object");
	}
	
	public Iterable<ClassNode> getParents(ClassNode cn) {
		// this avoids any stupid anonymous Iterable<ClassNode> and Iterator bullcrap
		// and also avoids computing a temporary set, so it is performant
		return () -> getEdges(cn).stream().map(e -> e.dst).iterator();
	}
	
	public Iterable<ClassNode> getInterfaces(ClassNode cn) {
		return () -> getEdges(cn).stream().filter(e -> e instanceof ImplementsEdge).map(e -> e.dst).iterator();
	}
	
	public ClassNode getSuper(ClassNode cn) {
		if (cn == rootNode)
			return null;
		for (InheritanceEdge edge : getEdges(cn))
			if (edge instanceof ExtendsEdge)
				return edge.dst;
		throw new IllegalStateException("Couldn't find parent class?");
	}
	
	public Iterable<ClassNode> getChildren(ClassNode cn) {
		return () -> getReverseEdges(cn).stream().map(e -> e.src).iterator();
	}
	
	@Override
	public boolean addVertex(ClassNode cn) {
		if (!super.addVertex(cn))
			return false;
		ClassNode sup = cn.superName != null ? findClass(cn.superName) : rootNode;
		super.addEdge(cn, new ExtendsEdge(cn, sup));
		
		for (String s : cn.interfaces) {
			ClassNode iface = findClass(s);
			super.addEdge(cn, new ImplementsEdge(cn, iface));
		}
		return true;
	}
	
	@Override
	public boolean excavate(ClassNode classNode) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean jam(ClassNode pred, ClassNode succ, ClassNode classNode) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public InheritanceEdge clone(InheritanceEdge edge, ClassNode oldN, ClassNode newN) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public InheritanceEdge invert(InheritanceEdge edge) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public FastGraph<ClassNode, InheritanceEdge> copy() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public String toString() {
		TabbedStringWriter sw = new TabbedStringWriter();
		for(ClassNode cn : vertices()) {
			blockToString(sw, this, cn);
		}
		return sw.toString();
	}
	
	protected ClassNode findClass(String name) {
		LocateableClassNode n = source.findClass(name);
		if(n != null) {
			ClassNode cn = n.node;
			return cn;
		} else {
			throw new RuntimeException(String.format("Class not found %s", name));
		}
	}
	
	public static void blockToString(TabbedStringWriter sw, ClassTree ct, ClassNode cn) {
		sw.print(String.format("%s", cn.getId()));
		sw.tab();
		for(InheritanceEdge e : ct.getEdges(cn)) {
			sw.print("\n^ " + e.toString());
		}
		for(InheritanceEdge p : ct.getReverseEdges(cn)) {
			sw.print("\nV " + p.toString());
		}
		sw.untab();
		sw.print("\n");
	}
	
	public static abstract class InheritanceEdge extends FastGraphEdge<ClassNode> {
		public InheritanceEdge(ClassNode child, ClassNode parent) {
			super(child, parent);
		}
		
		@Override
		public String toString() {
			return String.format("#%s inherits #%s", src.getId(), dst.getId());
		}
	}

	public static class ExtendsEdge extends InheritanceEdge {
		public ExtendsEdge(ClassNode child, ClassNode parent) {
			super(child, parent);
		}
		
		@Override
		public String toString() {
			return String.format("#%s extends #%s", src.getId(), dst.getId());
		}
	}

	public static class ImplementsEdge extends InheritanceEdge {
		public ImplementsEdge(ClassNode child, ClassNode parent) {
			super(child, parent);
		}
		
		@Override
		public String toString() {
			return String.format("#%s implements #%s", src.getId(), dst.getId());
		}
	}
}