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
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class DVBTest {
	public static void main(String[] args) {
		boolean[] statuses = new boolean[23];
		for (int i = 2; i <= 23; i++) {
			System.out.println(i);
			try {
				InputStream is = new FileInputStream(new File(String.format("res/dvb/DVB%04d.class", i)));
				ClassReader cr = new ClassReader(is);
				ClassNode cn = new ClassNode();
				cr.accept(cn, 0);

				Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
				while (it.hasNext()) {
					MethodNode m = it.next();
					SSAGenPass.DO_SPLIT = true;
					SSAGenPass.ULTRANAIVE = false;
					SSAGenPass.SKIP_SIMPLE_COPY_SPLIT = true;
					SSAGenPass.PRUNE_EDGES = true;

					ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
					new BoissinotDestructor(cfg, 0);
					cfg.getLocals().realloc(cfg);
					MethodNode m2 = new MethodNode(m.owner, m.access, m.name, m.desc, m.signature,
							m.exceptions.toArray(new String[0]));
					ControlFlowGraphDumper.dump(cfg, m2);
					cn.methods.add(m2);
					cn.methods.remove(m);
				}
				ClassWriter clazz = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
				cn.accept(clazz);
				byte[] saved = clazz.toByteArray();
				FileOutputStream out = new FileOutputStream(
						new File(String.format("res/dvb/processed_DVB%04d.class", i)));
				out.write(saved, 0, saved.length);
				out.close();
				statuses[i - 1] = true;
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
