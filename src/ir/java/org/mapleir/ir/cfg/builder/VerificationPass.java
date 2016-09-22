package org.mapleir.ir.cfg.builder;

import java.util.Arrays;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.stmt.Statement;

public class VerificationPass extends ControlFlowGraphBuilder.BuilderPass {

	private final String prev;

	public VerificationPass(ControlFlowGraphBuilder builder, String prev) {
		super(builder);
		this.prev = prev;
	}

	static void print(Statement s, String ind) {
		System.out.println(ind + s);
		System.out.println(ind + "c: " + Arrays.toString(s.children));
		for(Statement c : s.getChildren()) {
			print(c, ind + "  ");
		}
	}
	
	@Override
	public void run() {
		for (BasicBlock b : builder.graph.vertices()) {
			for (Statement s : b) {
				print(s, "");
				try {
					s.checkConsistency();
				} catch (RuntimeException e) {
					throw new RuntimeException(s.toString() + " in #" + b.getId() + "\nAfter " + prev, e);
				}
			}
		}
	}
}
