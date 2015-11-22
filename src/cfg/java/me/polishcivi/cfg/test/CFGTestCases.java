package me.polishcivi.cfg.test;

import java.io.File;
import java.io.IOException;

import me.polishcivi.cfg.graph.basicblock.BasicBlockGraph;
import me.polishcivi.cfg.graph.bytecode.InstructionGraph;
import me.polishcivi.cfg.utils.DOTExporter;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Created by polish on 21.11.15.
 */
@SuppressWarnings("ALL")
public class CFGTestCases {

    public static void simpleJump(int var0) {
        if (var0 == 0) {
            System.out.println("var0 == 0");
        } else {
            System.out.println("var0 != 0");
        }
        System.out.println("ret");
    }

    public static void multipleReturns(int var0) {
        if (var0 == 0) {
            System.out.println("var0 == 0");
        } else {
            System.out.println("var0 != 0");
            if (var0 == 3) {
                System.out.println("Fast return");
                return;
            }
        }
        System.out.println("ret");
    }

    public static void ternaryOperator(int var0) {
        int v = var0 == 1 ? 0 : 1;
    }

    public static void nestedTernaryOperator(int var0, int var3) {
        int v = var0 == 1 ? var0 == 1 ? var0 == 1 ? 1 : 2 : 2 : var0 == 1 ? 1 : var3 == 1 ? 1 : 2;
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        final ClassReader reader = new ClassReader(CFGTestCases.class.getName());
        final ClassNode node = new ClassNode();
        reader.accept(node, 0);

        final File root = new File("cfg_test");
        if (!root.exists() && !root.mkdir()) {
            throw new IOException("Couldn't create directory " + root.getAbsolutePath());
        }
        final File full = new File(root, "full");
        if (!full.exists() && !full.mkdir()) {
            throw new IOException("Couldn't create directory " + full.getAbsolutePath());
        }
        final File basicblock = new File(root, "basicblock");
        if (!basicblock.exists() && !basicblock.mkdir()) {
            throw new IOException("Couldn't create directory " + basicblock.getAbsolutePath());
        }

        for (MethodNode method : node.methods) {
            InstructionGraph instructionGraph = new InstructionGraph(method);
            BasicBlockGraph basicBlockGraph = new BasicBlockGraph(instructionGraph);
            DOTExporter.exportDOT(new File(full, "cfg - " + cleanse(method.name) + ".dot"), "test - " + cleanse(method.name + method.desc), instructionGraph);
            DOTExporter.exportDOT(new File(basicblock, "cfg - " + cleanse(method.name) + ".dot"), "test - " + cleanse(method.name + method.desc), basicBlockGraph);
        }
    }
    
    private static String cleanse(String str) {
    	return str.replaceAll("[\\\\/:\"?*\\<\\>\\|]", "");
    }
}
