package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.Type;

public abstract class PointsToNode {
	private GeomPointsTo pointsTo;
	private Type type;
	
	public PointsToNode(GeomPointsTo pointsTo, Type type) {
		this.pointsTo = pointsTo;
		this.type = type;
	}

	public Type getType() {
		return type;
	}
	
	public void setType(Type type) {
		this.type = type;
	}
}