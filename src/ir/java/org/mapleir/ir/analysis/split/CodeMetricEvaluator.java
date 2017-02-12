package org.mapleir.ir.analysis.split;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;

public class CodeMetricEvaluator {

	public static void computeInvocationSizes(ControlFlowGraph cfg, boolean isStatic) {
		for(BasicBlock b : cfg.vertices()) {
			computeInvocationSize(b, isStatic);
		}
	}
	
    private static void computeInvocationSize(BasicBlock b, boolean isStatic) {

    }
}