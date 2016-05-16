package org.rsdeob.deobimpl;

import java.util.List;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.deob.IPhase;

public class OpaquePredicateRemoverPhase2 implements IPhase {
	public static final String KEY_ID = OpaquePredicateRemoverPhase.class.getCanonicalName();

	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
		for(ClassNode cn : cxt.getNodes().values()) {
			for(MethodNode m : cn.methods) {
				ControlFlowGraph cfg = cxt.createControlFlowGraph(m);
				
			}
		}
	}
}