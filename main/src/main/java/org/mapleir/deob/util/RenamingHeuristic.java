package org.mapleir.deob.util;

public interface RenamingHeuristic {
	boolean shouldRename(String name, int access);

	RenamingHeuristic RENAME_NONE = (name, access) -> false;
	RenamingHeuristic RENAME_ALL = (name, access) -> true;
	RenamingHeuristic ALLATORI = (name, access) -> name.toLowerCase().equals("iiiiiiiiii");
}
