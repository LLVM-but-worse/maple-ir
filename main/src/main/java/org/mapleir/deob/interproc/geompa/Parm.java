package org.mapleir.deob.interproc.geompa;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public class Parm implements SparkField {
	
	private static final Map<Pair<MethodNode, Integer>, Parm> cache = new HashMap<>();
	
	private final int index;
	private final MethodNode method;
	private int number = 0;

	private Parm(MethodNode m, int i) {
		index = i;
		method = m;
		// Scene.v().getFieldNumberer().add(this);
	}

	public static Parm v(MethodNode m, int index) {
		Pair<MethodNode, Integer> p = new Pair<>(m, new Integer(index));
		Parm ret = cache.get(p);
		if (ret == null) {
			cache.put(p, ret = new Parm(m, index));
		}
		return ret;
	}

	/*public static final void delete() {
		G.v().Parm_pairToElement = null;
	}*/


	public final int getNumber() {
		return number;
	}

	public final void setNumber(int number) {
		this.number = number;
	}

	public int getIndex() {
		return index;
	}

	@Override
	public Type getType() {
		if (index == PointsToAnalysis.RETURN_NODE)
			return Type.getReturnType(method.desc);

		return Type.getArgumentTypes(method.desc)[index];
	}
	@Override
	public String toString() {
		return "Parm " + index + " to " + method;
	}
}