package org.mapleir.ir.printer;

import org.mapleir.stdlib.util.TabbedStringWriter;

public interface Printer<E> {

	void print(TabbedStringWriter sw, E e);
	
	default String print(E e) {
		TabbedStringWriter sw = new TabbedStringWriter();
		print(sw, e);
		return sw.toString();
	}
}