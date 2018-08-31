package org.mapleir.dot4j.attr;

public interface Attrs {

	Attrs apply(MapAttrs mapAttrs);
	
	default Attrs apply(Attrs attrs) {
		if(attrs instanceof MapAttrs) {
			return apply((MapAttrs) attrs);
		} else {
			throw new UnsupportedOperationException();
		}
	}

	default Object get(String key) {
		return apply(new MapAttrs()).get(key);
	}
	
	default boolean isEmpty() {
		return apply(new MapAttrs()).isEmpty();
	}
	
	static Attrs attr(String key, Object value) {
		return new MapAttrs().put(key, value);
	}
	
	static Attrs attrs(Attrs... attrss) {
		MapAttrs mapAttrs = new MapAttrs();
		for(Attrs attrs : attrss) {
			attrs.apply(mapAttrs);
		}
		return mapAttrs;
	}
}
