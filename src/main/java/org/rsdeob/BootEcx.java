package org.rsdeob;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ControlFlowGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGenerator;
import org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowAnalyzer;
import org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowState;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

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

//			if(m.toString().contains("<init>")) {
//				continue;
//			}

			System.out.println("\n\n\nProcessing " + m + ": ");

			// System.out.println("Instruction listing for " + m + ": ");
			// InstructionPrinter.consolePrint(m);

			ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(m);
			ControlFlowGraph cfg = builder.build();

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

//			System.out.println("Sg:");
//			System.out.println(StatementGraphBuilder.create(cfg));
//			System.out.println();

			DataFlowAnalyzer dfa = new DataFlowAnalyzer(cfg, true);
			Map<Statement, DataFlowState> df = dfa.computeForward();
			System.out.println("Data flow for " + m + ":");
			for (Statement b : df.keySet()) {
				DataFlowState state = df.get(b);
				System.out.println(b + " (" + b.getId() + ") :");
				System.out.println("In: ");
				for (CopyVarStatement copy : state.in.values())
					System.out.println("  " + copy);
				System.out.println();

				System.out.println("Out: ");
				for (CopyVarStatement copy : state.out.values())
					System.out.println("  " + copy);

				System.out.println();
			}

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