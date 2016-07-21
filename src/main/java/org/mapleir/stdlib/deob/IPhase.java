package org.mapleir.stdlib.deob;

import java.util.List;

import org.mapleir.stdlib.IContext;

public interface IPhase {
	
	String getId();
	
	void accept(IContext cxt, IPhase prev, List<IPhase> completed);
}