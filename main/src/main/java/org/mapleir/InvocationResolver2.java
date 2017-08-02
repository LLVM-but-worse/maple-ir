package org.mapleir;

import java.util.*;
import java.util.stream.Collectors;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.ClassTree;
import org.mapleir.app.service.InvocationResolver;
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MethodNode resolveVirtualInitCall(String owner, String desc) {
		// TODO Auto-generated method stub
		return null;
	}

	// resolve a virtual call matching owner+name+desc to a receiver instance of type receiver.
	// receiver should be equal to or a subclass of the method's owner.
	public MethodNode resolveVirtualCall(String name, String desc, ClassNode receiver) {
		List<ClassNode> topoorder = classTree.getAllParents(receiver);
		Collections.reverse(topoorder);
		System.out.println(topoorder);

		// sanity check parent chain back up to Object before anything else.
		int idx = 0;
		ClassNode checkCn = receiver;
		do {
			assert(topoorder.get(idx) == checkCn);
			checkCn = classTree.getSuper(checkCn);
			idx++;
		} while (checkCn != classTree.getRootNode());

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
	public Set<MethodNode> resolveVirtualCalls(String owner, String name, String desc, boolean strict) {
		return resolveVirtualCalls(name, desc, classTree.getAllChildren(app.findClassNode(owner)));
	}

	@Override
	public FieldNode findStaticField(String owner, String name, String desc) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FieldNode findVirtualField(String owner, String name, String desc) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<MethodNode> getHierarchyMethodChain(ClassNode cn, String name, String desc, boolean exact) {
		// TODO Auto-generated method stub
		return null;
	}
}
