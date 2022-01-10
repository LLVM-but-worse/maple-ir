package org.mapleir.deob.passes.fixer;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.mapleir.asm.ClassHelper;
import org.mapleir.asm.ClassNode;
import org.mapleir.deob.IPass;
import org.mapleir.deob.PassContext;
import org.mapleir.deob.PassResult;
import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ExceptionFixerPass implements IPass {
    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public PassResult accept(PassContext cxt) {
        final AtomicInteger counter = new AtomicInteger();

        for (ControlFlowGraph value : cxt.getAnalysis().getIRCache().values()) {
            for (ExceptionRange<BasicBlock> range : value.getRanges()) {
                if (range.getTypes().size() <= 1)
                    continue;

                ClassNode superType = null;
                for (Type type : range.getTypes()) {
                    ClassNode classNode = cxt.getAnalysis().getApplication().findClassNode(type.getClassName());

                    if (classNode == null) {
                        try {
                            classNode = ClassHelper.create(type.getClassName());
                        } catch (IOException e) {
                            continue;
                        }
                    }

                    if (superType == null) {
                        superType = classNode;
                    } else {
                        superType = cxt.getAnalysis()
                                .getApplication()
                                .getClassTree()
                                .getCommonSuperType(superType.getName(), classNode.getName());
                    }
                }

                final Set<Type> types = new HashSet<>(Collections.singleton(
                        superType == null
                                ? TypeUtils.OBJECT_TYPE
                                : Type.getObjectType(superType.getName())
                ));
                range.setTypes(types);

                counter.incrementAndGet();
            }
        }

        logger.info("[*] Successfully fixed" + counter.get() + " exception ranges!");
        return PassResult.with(cxt, this).finished().make();
    }
}
