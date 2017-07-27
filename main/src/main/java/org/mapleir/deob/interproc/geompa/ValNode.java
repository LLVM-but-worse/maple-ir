package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.Type;

public class ValNode extends PointsToNode {

	public ValNode(PAG pointsTo, Type type) {
		super(pointsTo, type);
	}
}