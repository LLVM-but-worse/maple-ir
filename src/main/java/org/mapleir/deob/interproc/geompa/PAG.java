package org.mapleir.deob.interproc.geompa;

import org.mapleir.context.AnalysisContext;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.collections.map.KeyedValueCreator;
import org.objectweb.asm.Type;

import jdk.nashorn.internal.ir.VarNode;

public class PAG {

	private final AnalysisContext context;
	private KeyedValueCreator<Type, AbstractPointsToSet> pointsToSetCreator;
	
	public PAG(AnalysisContext context) {
		this.context = context;
		pointsToSetCreator = (k) -> new DefaultPointsToSet(context.getApplication(), k);
	}
	
	public LocalVarNode findLocalVarNode(Object value) {
		if (opts.rta()) {
			value = null;
		} else if (value instanceof Local) {
			return localToNodeMap.get((Local) value);
		}
		return valToLocalVarNode.get(value);
}
	
	/** Returns the set of objects pointed to by variable l. */
	public AbstractPointsToSet reachingObjects(Local l) {
		PointsToNode n = findLocalVarNode(l);
		if (n == null) {
			return EmptyPointsToSet.INSTANCE;
		}
		return n.getP2Set();
	}

	/** Returns the set of objects pointed to by variable l in context c. */
	public PointsToSet reachingObjects(Context c, Local l) {
		VarNode n = findContextVarNode(l, c);
		if (n == null) {
			return EmptyPointsToSet.v();
		}
		return n.getP2Set();
	}

	/** Returns the set of objects pointed to by static field f. */
	public PointsToSet reachingObjects(SootField f) {
		if (!f.isStatic())
			throw new RuntimeException("The parameter f must be a *static* field.");
		VarNode n = findGlobalVarNode(f);
		if (n == null) {
			return EmptyPointsToSet.v();
		}
		return n.getP2Set();
	}

	/**
	 * Returns the set of objects pointed to by instance field f of the objects
	 * in the PointsToSet s.
	 */
	public PointsToSet reachingObjects(PointsToSet s, final SootField f) {
		if (f.isStatic())
			throw new RuntimeException("The parameter f must be an *instance* field.");

		return reachingObjectsInternal(s, f);
	}

	/**
	 * Returns the set of objects pointed to by elements of the arrays in the
	 * PointsToSet s.
	 */
	public PointsToSet reachingObjectsOfArrayElement(PointsToSet s) {
		return reachingObjectsInternal(s, ArrayElement.v());
	}

	private PointsToSet reachingObjectsInternal(PointsToSet s, final SparkField f) {
		if (getOpts().field_based() || getOpts().vta()) {
			VarNode n = findGlobalVarNode(f);
			if (n == null) {
				return EmptyPointsToSet.v();
			}
			return n.getP2Set();
		}
		if ((getOpts()).propagator() == SparkOptions.propagator_alias) {
			throw new RuntimeException(
					"The alias edge propagator does not compute points-to information for instance fields! Use a different propagator.");
		}
		PointsToSetInternal bases = (PointsToSetInternal) s;
		final PointsToSetInternal ret = setFactory.newSet((f instanceof SootField) ? ((SootField) f).getType() : null,
				this);
		bases.forall(new P2SetVisitor() {
			public final void visit(Node n) {
				Node nDotF = ((AllocNode) n).dot(f);
				if (nDotF != null)
					ret.addAll(nDotF.getP2Set(), null);
			}
		});
		return ret;
}
}