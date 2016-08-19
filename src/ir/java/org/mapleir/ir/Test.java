package org.mapleir.ir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.mapleir.AnalyticsTest;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Test {

	void loopTest() {
		int x = 1;
		do {
			if (x > 5)
				x--;
			else
				x++;
		} while(!p());
		System.out.println(x);
	}
	
	boolean p() {
		return true;
	}
	
	public static void main(String[] args) throws IOException {
		ClassReader cr = new ClassReader(Test.class.getCanonicalName());
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		
//		InputStream i = new FileInputStream(new File("res/a.class"));
//		ClassReader cr = new ClassReader(i);
//		ClassNode cn = new ClassNode();
//		cr.accept(cn, 0);
		
		Iterator<MethodNode> it = new ArrayList<>(cn.methods).listIterator();
		while(it.hasNext()) {
			MethodNode m = it.next();

//			if(!m.toString().equals("a/a/f/a.<init>()V")) {
//				continue;
//			}
			
			if(!m.toString().equals("org/mapleir/ir/Test.loopTest()V")) {
				continue;
			}
			
			System.out.println("Processing " + m + "\n");
			ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);

			try {
				BoissinotDestructor destructor = new BoissinotDestructor(cfg);
			} catch(RuntimeException e) {
				throw new RuntimeException("\n" + cfg.toString(), e);
			}
			
			System.out.println();
			System.out.println();
			System.out.println();
			System.out.println(cfg);
		}
	}
}