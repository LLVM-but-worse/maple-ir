package org.mapleir.stdlib.collections.graph;

import org.mapleir.stdlib.cfg.util.LabelHelper;

public interface FastGraphVertex {

	String getId();
	
	default int getIndex() {
		return LabelHelper.numeric(getId());
	}
}