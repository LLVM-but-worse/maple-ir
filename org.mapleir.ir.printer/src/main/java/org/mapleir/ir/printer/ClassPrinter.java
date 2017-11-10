package org.mapleir.ir.printer;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.tree.ClassNode;

public class ClassPrinter implements Printer<ClassNode> {

	@Override
	public void print(TabbedStringWriter sw, ClassNode e) {
		sw.print(".class ").print(e.name).print(" {").tab();
		sw.newline();
		emitDirective(sw, "superName", e.superName);
		sw.newline();
		emitDirective(sw, "interfaces", e.interfaces);
		sw.newline();
		emitDirective(sw, "version", e.version);
		sw.newline();
		emitDirective(sw, "access", e.access);
		sw.untab().newline().print("}");
	}
	
	private void emitDirective(TabbedStringWriter sw, String key, Object value) {
		sw.print(".set ").print(key).print(" ");
		emitDirectiveValue(sw, value);
	}
	
	private void emitDirectiveValue(TabbedStringWriter sw, Object value) {
		if(value instanceof Map) {
			Map<?, ?> map = (Map<?, ?>) value;
			if(map.size() > 0) {
				sw.print("{").tab();
				
				Iterator<?> it = map.entrySet().iterator();
				while(it.hasNext()) {
					Entry<?, ?> e = (Entry<?, ?>) it.next();
					sw.newline().print(String.valueOf(e.getKey())).print(" = ");
					emitDirectiveValue(sw, e.getValue());
					
					if(it.hasNext()) {
						sw.print(", ");
					}
				}
				
				sw.print("}");
			} else {
				sw.print(" {}");
			}
		} else if(value instanceof Collection) {
			sw.print("[");
			Iterator<?> it = ((Collection<?>) value).iterator();
			while(it.hasNext()) {
				emitDirectiveValue(sw, it.next());
				
				if(it.hasNext()) {
					sw.print(", ");
				}
			}
			sw.print("]");
		} else {
			sw.print(value.toString());
		}
	}
}