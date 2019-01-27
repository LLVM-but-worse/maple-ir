package org.mapleir;

import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.LocalsReallocator;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.stdlib.util.JavaClassCompiler;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.asm.ASMFactory;
import org.topdank.byteengineer.commons.asm.DefaultASMFactory;

import java.io.IOException;

public class CompilationDemo {
	public static void main(String[] args) throws IOException {
		// File f = new File("res/Bad.jar");
		// SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		// dl.download();
		// for (ClassNode cn : dl.getJarContents().getClassContents().namedMap().values()) {

		String className = "HelloWorld";
		JavaClassCompiler compiler = new JavaClassCompiler();
		byte[] bytes = compiler.compile(className, "public class " + className + " { public static void main(String[] args) { System.out.println(\"Hello world\"); } }");
		if (bytes == null) {
			System.out.println("Compilation failed!");
		} else {
			ASMFactory cnFactory = new DefaultASMFactory();
			ClassNode cn = cnFactory.create(bytes, className);
			for(MethodNode mn : cn.methods) {
				System.out.println(mn.getJavaDesc());
				ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(mn, false);
				ControlFlowGraph cfg = builder.buildImpl();
				System.out.println(cfg);
				BoissinotDestructor.leaveSSA(cfg);
				LocalsReallocator.realloc(cfg);
				System.out.println(cfg);
			}
		}
	}
}
