package org.rsdeob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.ControlFlowGraphDeobfuscator;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGenerator;
import org.rsdeob.stdlib.cfg.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.cfg.util.GraphUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("Duplicates")
public class BootEcx implements Opcodes {
	public static void main(String[] args) throws Exception {
		InputStream i = new FileInputStream(new File("res/a.class"));
		ClassReader cr = new ClassReader(i);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);

		Iterator<MethodNode> it = cn.methods.listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();

//			if(!m.toString().equals("a/a/f/a.H(La/a/f/o;J)V")) {
//				continue;
//			}
			
			System.out.println("\n\n\nProcessing " + m + ": ");

			// System.out.println("Instruction listing for " + m + ": ");
			// InstructionPrinter.consolePrint(m);

			ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(m);
			ControlFlowGraph cfg = builder.build();
			
			ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
			List<BasicBlock> blocks = deobber.deobfuscate(cfg);
			deobber.removeEmptyBlocks(cfg, blocks);
			GraphUtils.naturaliseGraph(cfg, blocks);

			System.out.println("Execution log of " + m + ":");
			StatementGenerator gen = new StatementGenerator(cfg);
			gen.init(m.maxLocals);
			gen.createExpressions();
			RootStatement root = gen.buildRoot();

			System.out.println("Cfg:");
			System.out.println(cfg);
			System.out.println();
			
			System.out.println("IR representation of " + m + ":");
			System.out.println(root);
			System.out.println();

			System.out.println("Sg:");
			System.out.println(new StatementGraphBuilder().create(cfg));
			System.out.println();

//			DataFlowAnalyzer dfa = new DataFlowAnalyzer(cfg, false);
//			Map<BasicBlock, DataFlowState> df = dfa.computeForward();
//			System.out.println("Data flow for " + m + ":");
//			for (BasicBlock b : df.keySet()) {
//				DataFlowState state = df.get(b);
//				System.out.println("Data flow for block " + b.getId() + ":");
//				System.out.println("In: ");
//				for (CopyVarStatement copy : state.in.values())
//					System.out.println("  " + copy);
//				System.out.println();
//
//				System.out.println("Out: ");
//				for (CopyVarStatement copy : state.out.values())
//					System.out.println("  " + copy);
//				System.out.println();

//				System.out.println("Gen: ");
//				for (CopyVarStatement copy : state.gen)
//					System.out.println("  " + copy);
//				System.out.println();
//
//				System.out.println("Kill: ");
//				for (CopyVarStatement copy : state.kill)
//					System.out.println("  " + copy);
//				System.out.println();
//			}

			System.out.println("End of processing log for " + m);
			System.out.println("============================================================");
			System.out.println("============================================================\n\n");
			break;
		}
		
		ClassWriter clazz = new ClassWriter(0);
		cn.accept(clazz);
		byte[] saved = clazz.toByteArray();
		FileOutputStream out = new FileOutputStream(new File("out/testclass.class"));
		out.write(saved, 0, saved.length);
		out.close();
	}
}