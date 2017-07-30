package org.mapleir.deob.interproc.geompa;

public interface ClientAccessibilityOracle {
	/**
	 * Determines whether the method is accessible for a potential library user.
	 */
	boolean isAccessible(MapleMethod method);
	
	/**
	 * Determines whether the field is accessible for a potential library user.
	 */
	boolean isAccessible(MapleField field);
	
}
