package org.mapleir;

import org.mapleir.context.IRCache;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.LocalsReallocator;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.codegen.ControlFlowGraphDumper;
import org.mapleir.ir.utils.CFGUtils;
import org.mapleir.asm.ClassHelper;
import org.mapleir.asm.InsnListUtils;
import org.mapleir.asm.ClassNode;
import org.mapleir.asm.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class CleanBoot {

    public static void main(String[] args) throws Exception {
        ClassNode cn = ClassHelper.create(new FileInputStream(new File("res", "BiteCode.class")));
        IRCache irFactory = new IRCache();
        for (MethodNode mn : cn.getMethods()) {
            ControlFlowGraph cfg = irFactory.getNonNull(mn);

            System.out.println(cfg);
            CFGUtils.easyDumpCFG(cfg, "pre-destruct");
            cfg.verify();

            BoissinotDestructor.leaveSSA(cfg);

            CFGUtils.easyDumpCFG(cfg, "pre-reaalloc");
            LocalsReallocator.realloc(cfg);
            CFGUtils.easyDumpCFG(cfg, "post-reaalloc");
            System.out.println(cfg);
            cfg.verify();
            System.out.println("Rewriting " + mn.getName());
            (new ControlFlowGraphDumper(cfg, mn)).dump();
            System.out.println(InsnListUtils.insnListToString(mn.node.instructions));
        }
        ClassHelper.dump(cn, new FileOutputStream(new File("out", "Bad.class")));
    }
}
