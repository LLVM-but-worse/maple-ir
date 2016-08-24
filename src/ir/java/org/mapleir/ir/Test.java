package org.mapleir.ir;

import static org.mapleir.ir.dot.ControlFlowGraphDecorator.OPT_DEEP;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.dot.ControlFlowGraphDecorator;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.graph.dot.BasicDotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
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

	void test111() {
		int x = 1;
		int y = 2;
		do {
			int z = x;
			x = y;
			y = z;
		} while(!p());

		System.out.println(x);
		System.out.println(y);
	}

	void test112() {
		int x = 1;
		int y = 2;
		
		while(!p()) {
			int z = x;
			x = y;
			y = z;
		}

		System.out.println(x);
		System.out.println(y);
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
			
			if(!m.toString().equals("org/mapleir/ir/Test.test112()V")) {
				continue;
			}
			
			System.out.println("Processing " + m + "\n");
			ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);

			System.out.println(cfg);
			
			try {
				BoissinotDestructor destructor = new BoissinotDestructor(cfg);
			} catch(RuntimeException e) {
				throw new RuntimeException("\n" + cfg.toString(), e);
			}

			BasicDotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config = new BasicDotConfiguration<>(DotConfiguration.GraphType.DIRECTED);
			DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> writer = new DotWriter<>(config, cfg);
			writer.removeAll().add(new ControlFlowGraphDecorator().setFlags(OPT_DEEP))
				.setName("destructed")
				.export();
			
//			System.out.println();
//			System.out.println();
//			System.out.println();
			System.out.println(cfg);
		}
	}
}