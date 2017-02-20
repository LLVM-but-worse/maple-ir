package org.mapleir.stdlib.klass;

import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Bibl (don't ban me pls)
 * @created 25 May 2015 (actually before this)
 */
public class ChainData {
	
	private final MethodNode centre;
	private final Set<MethodNode> supers;
	private final Set<MethodNode> delegates;
	private final Set<MethodNode> aggregates;

	public ChainData(MethodNode m, Set<MethodNode> supers, Set<MethodNode> delegates) {
		this.centre    = m;
		this.supers    = supers;
		this.delegates = delegates;
		
		this.supers.remove(m);
		this.delegates.remove(m);
		
		aggregates     = new HashSet<>();
		aggregates.addAll(supers);
		aggregates.addAll(delegates);
	}

	public Set<MethodNode> getSupers() {
		return supers;
	}

	public Set<MethodNode> getDelegates() {
		return delegates;
	}

	public Set<MethodNode> getAggregates() {
		return aggregates;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("Centre: ").append(centre.key()).append("   (").append(supers.size()).append(", ").append(delegates.size()).append(")");
		
		boolean sups = supers.size() > 0;
		boolean dels = delegates.size() > 0;
		if(sups || dels){
			sb.append("\n");
		}
		
		if(sups){
			sb.append("   >S>U>P>E>R>S>\n");
			Iterator<MethodNode> it = supers.iterator();
			while(it.hasNext()){
				MethodNode sup = it.next();
				sb.append("    ").append(sup.key());
				if(it.hasNext() || dels)
					sb.append("\n");
			}
		}
		
		if(dels){
			sb.append("   >D>E>L>E>G>A>T>E>S>\n");
			Iterator<MethodNode> it = delegates.iterator();
			while(it.hasNext()){
				MethodNode del = it.next();
				sb.append("    ").append(del.key());
				if(it.hasNext())
					sb.append("\n");
			}
		}
		
		return sb.toString();
	}
}