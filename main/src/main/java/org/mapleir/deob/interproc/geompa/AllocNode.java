package org.mapleir.deob.interproc.geompa;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mapleir.ir.TypeCone;
import org.objectweb.asm.tree.MethodNode;

public class AllocNode extends PointsToNode implements Context {

	protected Object newExpr;
	protected Map<SparkField, AllocDotField> fields;
	private MethodNode method;

	AllocNode(PAG pag, Object newExpr, TypeCone tc, MethodNode m) {
		super(pag, tc);
		method = m;
		// TODO: check it
		/*if (t instanceof RefType) {
			RefType rt = (RefType) t;
			if (rt.getSootClass().isAbstract()) {
				boolean usesReflectionLog = new CGOptions(PhaseOptions.v().getPhaseOptions("cg"))
						.reflection_log() != null;
				if (!usesReflectionLog) {
					throw new RuntimeException("Attempt to create allocnode with abstract type " + t);
				}
			}
		}*/
		this.newExpr = newExpr;
		if (newExpr instanceof ContextVarNode) {
			throw new RuntimeException();
		}
		pag.getAllocNodeNumberer().add(this);
	}

	/** Registers a AllocDotField as having this node as its base. */
	void addField(AllocDotField adf, SparkField field) {
		if (fields == null)
			fields = new HashMap<>();
		fields.put(field, adf);
	}
	
	public Object getNewExpr() {
		return newExpr;
	}

	public Collection<AllocDotField> getAllFieldRefs() {
		if (fields == null)
			return Collections.emptySet();
		return fields.values();
	}

	/**
	 * Returns the field ref node having this node as its base, and field as its field; null if nonexistent.
	 */
	public AllocDotField dot(SparkField field) {
		return fields == null ? null : fields.get(field);
	}

	public Set<AllocDotField> getFields() {
		if (fields == null)
			return Collections.emptySet();
		return new HashSet<>(fields.values());
	}


	public MethodNode getMethod() {
		return method;
	}

	@Override
	public String toString() {
		return "AllocNode " + getNumber() + " " + newExpr + " in method " + method;
	}
}