package org.mapleir.testsuite.api.config;

import org.mapleir.ir.cfg.ControlFlowGraph;

public interface Feature {

	boolean enable(/*Suite*/ ControlFlowGraph ir);
}