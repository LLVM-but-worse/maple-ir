package org.mapleir.ir.antlr.analysis;

import org.mapleir.ir.cfg.ControlFlowGraph;

public class CodeFixup {

    public static void run(ControlFlowGraph cfg, TypeAnalysis typeAnalysis) {
        /* Due to the loss of type information in the IR textcode,
         * we need to type the code and fill in artifacts that are
         * context-dependent on these types.*/
    }
}
