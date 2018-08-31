package org.mapleir.dot4j.attr.builtin;

import static org.mapleir.dot4j.attr.Attrs.*;

import org.mapleir.dot4j.attr.Attrs;

public class Font {

	private Font() {
	}

	public static Attrs config(String name, int size) {
		return attrs(name(name), size(size));
	}

	public static Attrs name(String name) {
		return attr("fontname", name);
	}

	public static Attrs size(int size) {
		return attr("fontsize", size);
	}
}
