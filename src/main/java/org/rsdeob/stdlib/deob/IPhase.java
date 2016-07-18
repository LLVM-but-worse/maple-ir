package org.rsdeob.stdlib.deob;

import org.rsdeob.stdlib.IContext;

import java.util.List;

public interface IPhase {
	
	String getId();
	
	void accept(IContext cxt, IPhase prev, List<IPhase> completed);
}