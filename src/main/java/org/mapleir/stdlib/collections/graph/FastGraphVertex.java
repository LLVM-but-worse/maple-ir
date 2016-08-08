package org.mapleir.stdlib.collections.graph;

import org.mapleir.stdlib.cfg.util.LabelHelper;
import org.mapleir.stdlib.collections.BitSetElement;

public interface FastGraphVertex extends BitSetElement {

	String getId();
	
	@Override
	default long getIndex() {
		return LabelHelper.numeric(getId());
	}
}