package org.rsdeob.stdlib.deob;

import java.util.List;

import org.rsdeob.stdlib.IContext;

public interface IPhase {
	
	String getId();
	
	void accept(IContext cxt, IPhase prev, List<IPhase> completed);
}