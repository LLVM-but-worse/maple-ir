package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.tree.MethodNode;

public interface ClientAccessibilityOracle {
	/**
	 * Determines whether the method is accessible for a potential library user.
	 */
	boolean isAccessible(MethodNode method);
	
	/**
	 * Determines whether the field is accessible for a potential library user.
	 */
	boolean isAccessible(MapleField field);
	
}
