package org.mapleir.ir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.cfg.builder.SSAGenPass;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.topdank.banalysis.asm.insn.InstructionPrinter;

public class DVBTest {
	public static boolean FLAG = false;
	
	public static void main(String[] args) {
		boolean[] statuses = new boolean[23];
		for (int i = 2; i <= 23; i++) {
			try {
				InputStream is = new FileInputStream(new File(String.format("res/dvb/DVB%04d.class", i)));
				ClassReader cr = new ClassReader(is);
				ClassNode cn = new ClassNode() {
					@Override
					public MethodVisitor visitMethod(final int access, final String name, final String desc,
							final String signature, final String[] exceptions) {
						MethodVisitor origVisitor = super.visitMethod(access, name, desc, signature, exceptions);
						return new JSRInlinerAdapter(origVisitor, access, name, desc, signature, exceptions);
					}
				};
				cr.accept(cn, 0);
				
				if(!cn.name.contains("DVB0003")) {
					continue;
				}

				Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
				while (it.hasNext()) {
					MethodNode m = it.next();
					try {
						SSAGenPass.DO_SPLIT = true;
						SSAGenPass.ULTRANAIVE = false;
						SSAGenPass.SKIP_SIMPLE_COPY_SPLIT = true;
						SSAGenPass.PRUNE_EDGES = true;
						
						System.out.println(m);
						System.out.println();
						System.out.println("Original bytecode:");
						InstructionPrinter.consolePrint(m);
						System.out.println();

						ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
						new BoissinotDestructor(cfg, 0);
						cfg.getLocals().realloc(cfg);
						

						System.out.println("IR:");
						System.out.println(cfg);
						
						ControlFlowGraphDumper.dump(cfg, m);
						System.out.println();
						System.out.println("Processed bytecode:");
						InstructionPrinter.consolePrint(m);
					} catch(RuntimeException e) {
						System.err.println("Error during " + m);
						for(TryCatchBlockNode tc : m.tryCatchBlocks) {
							System.err.println(tc.start + " " + tc.end + " " + tc.handler + "  " + tc.type);
						}
						for(String s : InstructionPrinter.getLines(m)) {
							System.err.println(s);
						}
						e.printStackTrace();
						statuses[i - 1] = false;
					}
				}
				try {
					ClassWriter clazz = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
					cn.accept(clazz);
					byte[] saved = clazz.toByteArray();
					FileOutputStream out = new FileOutputStream(
							new File(String.format("res/dvb/processed_DVB%04d.class", i)));
					out.write(saved, 0, saved.length);
					out.close();
					statuses[i - 1] = true;
				} catch(Exception e) {
					System.err.println("Error during " + cn);
					e.printStackTrace();
					statuses[i - 1] = false;
				}
			} catch (Exception e) {
				e.printStackTrace();
				// System.exit(-1);
				statuses[i - 1] = false;
			}
		}

		System.out.println("for the wiki");
		for (int i = 0; i < statuses.length; i++)
			System.out.printf("|%04d|%s|n/a|\n", i + 1, statuses[i] ? "PASS" : "FAIL");
	}
}
