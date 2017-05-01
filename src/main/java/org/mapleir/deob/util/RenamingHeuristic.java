package org.mapleir.deob.util;

public interface RenamingHeuristic {
	boolean shouldRename(String name, int access);
	
	RenamingHeuristic RENAME_ALL = (name, access) -> true;
}
