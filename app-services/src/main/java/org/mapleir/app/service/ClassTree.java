package org.mapleir.app.service;

import java.util.*;

import org.mapleir.app.service.ClassTree.InheritanceEdge;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.algorithms.SimpleDfs;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * A graph to represent the inheritance tree.
 * The graph follows the convention of anti-arborescence, i.e. edges point towards the root (Object).
 * @see <a href=https://en.wikipedia.org/wiki/Tree_(graph_theory)>Wikipedia: Tree</a>
 */
// nodes point to their super interfaces/classes
// edge = (c, super)
// so a dfs goes through edges towards the root
public class ClassTree extends FastDirectedGraph<ClassNode, InheritanceEdge> {
	private final ApplicationClassSource source;
	private ClassNode rootNode;
//	private final Set<ClassNode> unsupported;
	
	public ClassTree(ApplicationClassSource source) {
		this.source = source;
		init();
//		new Exception().printStackTrace();
	}
	
	protected void init() {
		rootNode = findClass("java/lang/Object");
		addVertex(rootNode);
//		unsupported = new HashSet<>();
		
		for (ClassNode node : source.iterateWithLibraries()) {
			addVertex(node);
		}
		
	}

	public ClassNode getRootNode() {
		return rootNode;
	}
	
	public Iterable<ClassNode> iterateParents(ClassNode cn) {
		// this avoids any stupid anonymous Iterable<ClassNode> and Iterator bullcrap
		// and also avoids computing a temporary set, so it is performant
		return () -> getEdges(cn).stream().map(e -> e.dst).iterator();
	}
	
	public Iterable<ClassNode> iterateInterfaces(ClassNode cn) {
		return () -> getEdges(cn).stream().filter(e -> e instanceof ImplementsEdge).map(e -> e.dst).iterator();
	}
	
	public Iterable<ClassNode> iterateChildren(ClassNode cn) {
		return () -> getReverseEdges(cn).stream().map(e -> e.src).iterator();
	}
	
	public Collection<ClassNode> getParents(ClassNode cn) {
		return __getnodes(getEdges(cn), true);
	}
	
	public Collection<ClassNode> getChildren(ClassNode cn) {
		return __getnodes(getReverseEdges(cn), false);
	}
	
	private Collection<ClassNode> __getnodes(Collection<? extends FastGraphEdge<ClassNode>> edges, boolean dst) {
		Set<ClassNode> set = new HashSet<>();
		for(FastGraphEdge<ClassNode> e : edges) {
			set.add(dst ? e.dst : e.src);
		}
		return set;
	}

	// returns a topoorder (supers first) traversal of the graph starting from cn.
	public List<ClassNode> getAllParents(ClassNode cn) {
		if(!containsVertex(cn)) {
			return new ArrayList<>();
		}
		return SimpleDfs.topoorder(this, cn, false);
	}

	// returns a postorder traversal of the graph starting from cn following edges in opposite direction.
	public List<ClassNode> getAllChildren(ClassNode cn) {
		if(!containsVertex(cn)) {
			return new ArrayList<>();
		}
		return SimpleDfs.postorder(this, cn, true);
	}
	
	/**
	 * @param cn classnode to search out from
	 * @return every class connected to the class in any way.
	 */
	public Collection<ClassNode> getAllBranches(ClassNode cn) {
		Collection<ClassNode> results = new HashSet<>();
		Queue<ClassNode> queue = new LinkedList<>();
		queue.add(cn);
		while (!queue.isEmpty()) {
			ClassNode next = queue.remove();
			if (results.add(next) && next != rootNode) {
				queue.addAll(getAllParents(next));
				queue.addAll(getAllChildren(next));
			}
		}
		return results;
	}
	
	public ClassNode getSuper(ClassNode cn) {
		if (cn == rootNode)
			return null;
		for (InheritanceEdge edge : getEdges(cn))
			if (edge instanceof ExtendsEdge)
				return edge.dst;
		throw new IllegalStateException("Couldn't find parent class?");
	}
	
	protected ClassNode findClass(String name) {
		LocateableClassNode n = source.findClass(name);
		if(n != null) {
			return n.node;
		} else {
			throw new RuntimeException(String.format("Class not found %s", name));
		}
	}
	
	private ClassNode requestClass0(String name, String from) {
		try {
			return findClass(name);
		} catch(RuntimeException e) {
			throw new RuntimeException("request from " + from, e);
		}
	}
	
	boolean k = false;
	
	@Override
	public boolean addVertex(ClassNode cn) {
		if (!super.addVertex(cn))
			return false;
		System.out.println("add " + cn +" " +  this.hashCode());
		if(!k) {
//			new Exception().printStackTrace();
			k = true;
		}
		
		ClassNode sup = cn.superName != null ? requestClass0(cn.superName, cn.name) : rootNode;
		addEdge(cn, new ExtendsEdge(cn, sup));
		
		for (String s : cn.interfaces) {
			ClassNode iface = requestClass0(s, cn.name);
			addEdge(cn, new ImplementsEdge(cn, iface));
		}
		return true;
	}
	@Override
	public void addEdge(ClassNode v, InheritanceEdge e) {
		super.addEdge(v, e);
		System.out.println("add " + e);
	}
	
	@Override
	public Set<InheritanceEdge> getEdges(ClassNode cn) {
		if(!containsVertex(cn)) {
			addVertex(cn);
		}
		return super.getEdges(cn);
	}
	
	@Override
	public Set<InheritanceEdge> getReverseEdges(ClassNode cn) {
		if(!containsVertex(cn)) {
			addVertex(cn);
		}
		return super.getReverseEdges(cn);
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

	// Ensure extends edges are traversed first.
	@Override
	public Set<InheritanceEdge> createSet() {
		return new TreeSet<>((e1, e2) -> -Boolean.compare(!(e1 instanceof ExtendsEdge), !(e2 instanceof ExtendsEdge)));
	}
}
