package org.mapleir;

import java.util.*;
import java.util.stream.Collectors;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.ClassTree;
import org.mapleir.app.service.InvocationResolver;
import org.mapleir.stdlib.collections.graph.algorithms.SimpleDfs;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class InvocationResolver2 implements InvocationResolver {
	private final ApplicationClassSource app;
	private final ClassTree classTree;

	public InvocationResolver2(ApplicationClassSource app) {
		this.app = app;
		classTree = app.getClassTree();
	}

	@Override
	public MethodNode resolveStaticCall(String owner, String name, String desc) {
		// static methods resolve absolutely.
		return app.findClassNode(owner).getMethod(name, desc, true);
	}

	@Override
	public MethodNode resolveVirtualInitCall(String owner, String desc) {
		return resolveVirtualCall("<init>", desc, app.findClassNode(owner));
	}

	// resolve a virtual call matching owner+name+desc to a receiver instance of type receiver.
	// receiver should be equal to or a subclass of the method's owner.
	public MethodNode resolveVirtualCall(String name, String desc, ClassNode receiver) {
		List<ClassNode> topoorder = classTree.getAllParents(receiver);

		// sanity check parent chain back up to Object before anything else.
		int idx = 0;
		for (ClassNode checkCn : classTree.iterateInheritanceChain(receiver)) {
			assert(topoorder.get(idx) == checkCn);
			idx++;
		}

		for (ClassNode cn : topoorder) {
			MethodNode m = cn.getMethod(name, desc, false);
			if (m != null)
				return m;
		}
		return null;
	}

	public Set<MethodNode> resolveVirtualCalls(String name, String desc, Collection<ClassNode> candidateReceivers) {
		return candidateReceivers.stream().map(receiver -> resolveVirtualCall(name, desc, receiver)).filter(Objects::nonNull).collect(Collectors.toSet());
	}

	@Override
	public Set<MethodNode> resolveVirtualCalls(String owner, String name, String desc, @Deprecated  boolean strict) {
		ClassNode ownerCn = app.findClassNode(owner);
		Set<MethodNode> result = new HashSet<>();
		result.add(resolveVirtualCall(name, desc, ownerCn));
		classTree.getAllChildren(ownerCn).stream().map(childCn -> childCn.getMethod(name, desc, false)).filter(Objects::nonNull).forEach(result::add);
		return result;
	}

	private FieldNode findExactField(ClassNode cn, String name, String desc) {
		return cn.fields.stream().filter(field -> field.name.equals(name) && field.desc.equals(desc)).findFirst().orElse(null);
	}

	@Override
	public FieldNode findStaticField(String owner, String name, String desc) {
		return findExactField(app.findClassNode(owner), name, desc);
	}

	@Override
	public FieldNode findVirtualField(String owner, String name, String desc) {
		// interfaces are not an issue here because all interface fields are public static final.
		for (ClassNode cn : classTree.iterateInheritanceChain(app.findClassNode(owner))) {
			FieldNode f = findExactField(cn, name, desc);
			if (f != null)
				return f;
		}
		return null;
	}

	@Override
	public Set<MethodNode> getHierarchyMethodChain(ClassNode cn, String name, String desc, boolean exact) {
		return (new SimpleInvocationResolver(app)).getHierarchyMethodChain(cn, name, desc, exact);
	}
}
