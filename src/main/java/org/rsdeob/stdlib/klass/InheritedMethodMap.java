package org.rsdeob.stdlib.klass;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Bibl (don't ban me pls)
 * @created 25 May 2015 (actually before this)
 */
public class InheritedMethodMap {
	private final Map<MethodNode, ChainData> methods;

	private int mCount = 0;
	private int aCount = 0;

	public InheritedMethodMap(ClassTree tree, boolean allowStatic) {
		methods = new HashMap<MethodNode, ChainData>();
		build(tree, allowStatic);
	}

	public InheritedMethodMap(ClassTree tree) {
		this(tree, false);
	}

	private void build(ClassTree tree, boolean allowStatic) {
		for (ClassNode node : tree.getClasses().values()) {
			for (MethodNode m : node.methods) {
				boolean isStatic = Modifier.isStatic(m.access);
				if (allowStatic || (!isStatic)) {
					Set<MethodNode> supers = tree.getMethodsFromSuper(node, m.name, m.desc, isStatic );
					Set<MethodNode> delegates = tree.getMethodsFromDelegates(node, m.name, m.desc, isStatic);
					ChainData data = new ChainData(m, supers, delegates);
					this.methods.put(m, data);

					mCount++;
					aCount += data.getAggregates().size();
				}
			}
		}

		// for(ChainData data : methods.values()){
		// System.out.println(data);
		// }
	}

	public ChainData getData(MethodNode m) {
		return methods.get(m);
	}

	@Override
	public String toString() {
		return String.format("%d methods connected with %d others.", mCount, aCount);
	}
}