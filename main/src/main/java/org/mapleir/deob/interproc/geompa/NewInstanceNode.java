package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.Type;

public class NewInstanceNode extends PointsToNode {
	
	// TODO: originally soot.Value
	private final Object value;

    NewInstanceNode( PAG pag, Object value, Type type ) {
    	super(pag, type);
    	this.value = value;
    }
    
    public Object getValue() {
    	return value;
    }
	
}