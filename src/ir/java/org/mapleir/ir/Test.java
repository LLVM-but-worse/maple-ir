package org.mapleir.ir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.ControlFlowGraphBuilder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Test {

	public static void main(String[] args) throws IOException {
//		ClassReader cr = new ClassReader(AnalyticsTest.class.getCanonicalName());
//		ClassNode cn = new ClassNode();
//		cr.accept(cn, 0);
		
		InputStream i = new FileInputStream(new File("res/a.class"));
		ClassReader cr = new ClassReader(i);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		
		Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();

			if(!m.toString().equals("a/a/f/a.<init>()V")) {
				continue;
			}
			
			System.out.println("Processing " + m + "\n");
			ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
			System.out.println(cfg);

			BoissinotDestructor destructor = new BoissinotDestructor(cfg);
		}
	}
}