package org.mapleir.state.structures;

import org.mapleir.state.ApplicationClassSource;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.tree.ClassNode;

// A graph to represnt the inheritance tree. We define the FORWARD EDGE direction to be from parent to child.
public class ClassTree extends FastDirectedGraph<ClassNode, InheritanceEdge> {
	private final ClassResolver resolver;
	private final ClassNode cnObject;
	
	public ClassTree(ApplicationClassSource source) {
		super();
		resolver = source.getResolver();
		cnObject = resolver.findClass("java/lang/Object");
	}
	
	public Iterable<ClassNode> getParents(ClassNode cn) {
		// this avoids any stupid anonymous Iterable<ClassNode> and Iterator bullcrap
		// and also avoids computing a temporary set, so it is performant
		return () -> getReverseEdges(cn).stream().map(e -> e.src).iterator();
	}
	
	public Iterable<ClassNode> getChildren(ClassNode cn) {
		return () -> getEdges(cn).stream().map(e -> e.dst).iterator();
	}
	
	@Override
	public boolean addVertex(ClassNode cn) {
		if (!super.addVertex(cn))
			return false;
		ClassNode sup = cn.superName != null ? resolver.findClass(cn.superName) : cnObject;
		super.addEdge(sup, new InheritanceEdge(sup, cn));
		
		for (String s : cn.interfaces) {
			ClassNode iface = resolver.findClass(s);
			super.addEdge(iface, new InheritanceEdge(iface, cn));
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
	
	public static void blockToString(TabbedStringWriter sw, ClassTree ct, ClassNode cn) {
		sw.print(String.format("%s", cn.getId()));
		sw.tab();
		for(InheritanceEdge e : ct.getEdges(cn)) {
			sw.print("\n-> " + e.toString());
		}
		for(InheritanceEdge p : ct.getReverseEdges(cn)) {
			sw.print("\n<- " + p.toString());
		}
		sw.untab();
		sw.print("\n");
	}
}

class InheritanceEdge extends FastGraphEdge<ClassNode> {
	public InheritanceEdge(ClassNode src, ClassNode dst) {
		super(src, dst);
	}
	
	@Override
	public String toString() {
		return String.format("#%s parents #%s", src.getId(), dst.getId());
	}
}

