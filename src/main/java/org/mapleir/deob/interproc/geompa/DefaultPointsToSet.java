package org.mapleir.deob.interproc.geompa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.mapleir.context.app.ApplicationClassSource;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.Type;

public class DefaultPointsToSet extends AbstractPointsToSet {

	private final Set<PointsToNode> set;
	
	public DefaultPointsToSet(ApplicationClassSource source, Type type) {
		super(source, type);
		set = new HashSet<>(4);
	}
	
	@Override
	public final boolean addAll(AbstractPointsToSet other, AbstractPointsToSet exclude) {
		if (other instanceof DefaultPointsToSet && exclude == null && (type == null || type.equals(other.type))) {
			return set.addAll(((DefaultPointsToSet) other).set);
		} else {
			return super.addAll(other, exclude);
		}
	}

	@Override
	public boolean forAll(PointsToFunctor<Boolean> f) {
		for (Iterator<PointsToNode> it = new ArrayList<>(set).iterator(); it.hasNext();) {
			f.apply(it.next());
		}
		return f.getResult();
	}

	@Override
	public boolean add(PointsToNode n) {
		// i.e. [this.type] = [n.type] is a statically
		// determinable valid class cast. 
		if(TypeUtils.castNeverFails(source, n.getType(), type)) {
			return set.add(n);
		} else {
			return false;
		}
	}

	@Override
	public boolean contains(PointsToNode n) {
		return set.contains(n);
	}

	@Override
	public boolean isEmpty() {
		return set.isEmpty();
	}
}