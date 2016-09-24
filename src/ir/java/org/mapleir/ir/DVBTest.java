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

public class DVBTest {
	public static boolean FLAG = false;
	
	public static void main(String[] args) throws Exception {
		for (int i = 1; i <= 23; i++) {
			InputStream is = new FileInputStream(new File(String.format("res/dvb/DVB%04d.class", i)));
			ClassReader cr = new ClassReader(is);
			ClassNode cn = new ClassNode() {
				 @Override
				public MethodVisitor visitMethod(final int access, final String name,
				            final String desc, final String signature, final String[] exceptions) {
					 MethodVisitor origVisitor = super.visitMethod(access, name, desc, signature, exceptions);
					 return new JSRInlinerAdapter(origVisitor, access, name, desc, signature, exceptions);
				 }
			};
			
			cr.accept(cn, 0);

			Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
			while (it.hasNext()) {
				MethodNode m = it.next();
				if(!m.toString().contains("DVB0001.main([Ljava/lang/String;)V")) {
//					continue;
				}
				System.out.println(m);
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
		}
	}
}
