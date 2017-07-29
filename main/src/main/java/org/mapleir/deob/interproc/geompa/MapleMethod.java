package org.mapleir.deob.interproc.geompa;

import org.mapleir.stdlib.collections.map.CachedKeyedValueCreator;
import org.mapleir.stdlib.collections.map.KeyedValueCreator;
import org.objectweb.asm.tree.MethodNode;

public class MapleMethod implements MapleMethodOrMethodContext {

	public static final KeyedValueCreator<MethodNode, MapleMethod> CREATOR = new CachedKeyedValueCreator<MethodNode, MapleMethod>() {
		@Override
		protected MapleMethod create0(MethodNode k) {
			return new MapleMethod(k);
		}
	};
	
	public static MapleMethod get(MethodNode m) {
		return CREATOR.create(m);
	}
	
	private final MethodNode method;
	
	private MapleMethod(MethodNode method) {
		this.method = method;
	}

	@Override
	public MapleMethod method() {
		return this;
	}
	
	public MethodNode getMethodNode() {
		return method;
	}

	@Override
	public Context context() {
		return null;
	}
}
