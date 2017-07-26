package org.mapleir.deob.interproc.geompa.graph;

import org.mapleir.deob.interproc.geompa.GeomPointsTo;
import org.mapleir.deob.interproc.geompa.PointsToNode;
import org.objectweb.asm.Type;

public class ValNode extends PointsToNode {

	public ValNode(GeomPointsTo pointsTo, Type type) {
		super(pointsTo, type);
	}
}