package org.mapleir.stdlib.cfg.edge;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.objectweb.asm.util.Printer;

public abstract class JumpEdge<N extends FastGraphVertex> extends FlowEdge<N> {
	
	public final int opcode;
	
	public JumpEdge(N src, N dst, int opcode) {
		super(src, dst);
		this.opcode = opcode;
	}
	
	@Override
	public String toGraphString() {
		return Printer.OPCODES[opcode];
	}
	
	@Override
	public String toString() {
		return String.format("Jump[%s] #%s -> #%s", Printer.OPCODES[opcode], src.getId(), dst.getId());
	}
	
	@Override
	public String toInverseString() {
		return String.format("Jump[%s] #%s <- #%s", Printer.OPCODES[opcode], dst.getId(), src.getId());
	}
}