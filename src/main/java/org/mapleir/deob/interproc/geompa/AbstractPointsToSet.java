package org.mapleir.deob.interproc.geompa;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.mapleir.context.app.ApplicationClassSource;
import org.mapleir.deob.interproc.geompa.PointsToFunctor.BooleanPointsToFunctor;
import org.objectweb.asm.Type;

public abstract class AbstractPointsToSet {

	protected final ApplicationClassSource source;
	protected Type type;

	public AbstractPointsToSet(ApplicationClassSource source, Type type) {
		this.source = source;
		this.type = type;
	}
	
	public abstract boolean forAll(PointsToFunctor<Boolean> f);

	public abstract boolean add(PointsToNode n);

	public abstract boolean contains(PointsToNode n);

	public abstract boolean isEmpty();

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public boolean hasNonEmptyIntersection(AbstractPointsToSet other) {
		return forAll(new BooleanPointsToFunctor() {
			@Override
			public void apply(PointsToNode n) {
				if (other.contains(n)) {
					res = true;
				}
			}
		});
	}

	public Set<Type> getPossibleTypes() {
		Set<Type> result = new HashSet<>();
		forAll(new BooleanPointsToFunctor() {
			@Override
			public void apply(PointsToNode n) {
				Type t = n.getType();
				if (!Modifier.isAbstract(source.findClassNode(t.getClassName()).access)) {
					result.add(t);
				}
			}
		});
		return result;
	}

	public int size() {
		final int[] ret = new int[1];
		forAll(new BooleanPointsToFunctor() {
			@Override
			public void apply(PointsToNode n) {
				ret[0]++;
			}
		});
		return ret[0];
	}
	
	public boolean isSuperSetOf(AbstractPointsToSet other) {
		return forAll(new BooleanPointsToFunctor(true) {
			@Override
			public void apply(PointsToNode n) {
				res &= other.contains(n);
			}
		});
	}

	public boolean mergeWith(AbstractPointsToSet other) {
		return addAll(other, null);
	}

	public boolean addAll(AbstractPointsToSet other, AbstractPointsToSet exclude) {
		return other.forAll(new BooleanPointsToFunctor() {
			@Override
			public void apply(PointsToNode n) {
				if (exclude == null || !exclude.contains(n)) {
					res = add(n) | res;
				}
			}
		});
	}

	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer();
		forAll(new BooleanPointsToFunctor() {
			@Override
			public void apply(PointsToNode n) {
				ret.append("" + n + ",");
			}
		});
		return ret.toString();
	}
}