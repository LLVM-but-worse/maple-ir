package org.mapleir.deob.interproc.geompa;

import org.mapleir.ir.TypeCone;

public class NewInstanceNode extends PointsToNode {
	
	// TODO: originally soot.Value
	private final Object value;

    NewInstanceNode( PAG pag, Object value, TypeCone tc ) {
    	super(pag, tc);
    	this.value = value;
    }
    
    public Object getValue() {
    	return value;
    }
	
}