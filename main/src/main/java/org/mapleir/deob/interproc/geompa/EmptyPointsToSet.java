package org.mapleir.deob.interproc.geompa;

import java.util.Collections;
import java.util.Set;

import org.mapleir.app.service.ApplicationClassSource;
import org.objectweb.asm.Type;

public class EmptyPointsToSet extends AbstractPointsToSet {

	public static final EmptyPointsToSet INSTANCE = new EmptyPointsToSet(null, null);
	
	private EmptyPointsToSet(ApplicationClassSource source, Type type) {
		super(source, type);
	}

	@Override
	public Set<Type> getPossibleTypes() {
		return Collections.emptySet();
	}
	
	@Override
	public boolean forAll(PointsToFunctor<Boolean> f) {
		return false;
	}
	
	public boolean hasNonEmptyIntersection() {
		return false;
	}
	
	@Override
	public boolean addAll(AbstractPointsToSet other, AbstractPointsToSet exclude) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean add(PointsToNode n) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean contains(PointsToNode n) {
		return false;
	}

	@Override
	public boolean isEmpty() {
		return true;
	}
	
	@Override
	public String toString() {
		return "{}";
	}
}