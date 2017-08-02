package org.mapleir;

import java.io.File;
import java.io.IOException;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.ClassTree;
import org.mapleir.app.service.InstalledRuntimeClassSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.topdank.byteengineer.commons.asm.ASMFactory;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

public class UnlinkedClassTreeBoot {

	static int k = 1;
	
	static long last = -1;
	
	static void start() {
		last = System.nanoTime();
	}
	
	static long stop() {
		long el = System.nanoTime() - last;
		if(last == -1) {
			throw new IllegalStateException();
		}
		
		last = -1;
		return el;
	}
	
	public static void main(String[] args) throws Exception {
//		File f = new File("res/allatori6.1san.jar");
		System.in.read();
		File f = new File("res/rt.jar");
		System.out.println("Preparing to run on " + f.getAbsolutePath());
		
		start();
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new ASMFactory<ClassNode>() {
			@Override
			public ClassNode create(byte[] bytes, String name) throws IOException {
//				System.out.println(name);
				ClassReader cr = new ClassReader(bytes);
				ClassNode cn = new ClassNode();
				cr.accept(cn,0);
				return cn;
			}

			@Override
			public byte[] write(ClassNode c) {
				// TODO Auto-generated method stub
				return null;
			}
		}, new JarInfo(f));
		dl.download();
		System.out.println("loading: " + conv(stop()) + "ms");
		String name = f.getName().substring(0, f.getName().length() - 4);
		ApplicationClassSource app = new ApplicationClassSource(name, dl.getJarContents().getClassContents());
		app.addLibraries(new InstalledRuntimeClassSource(app));

		double[] rs = testClassTree(app);
		System.out.println("total: " + rs[0] +"ms");
		System.out.println("avg: " + rs[1] + "ms");
	}

// @20k allatori6.1san.jar
//	total: 3802.116123ms
//	avg: 0.190105ms
//	total: 3946.816691ms
//	avg: 0.19734ms
//	total: 3702.050155ms
//	avg: 0.185102ms
	
// @1 rt.jar 200ms
// but jar load = 24610ms
// with SKIP_CODE
// loading: 22652.387614ms
// loading: 15570.655749ms
	private static double[] testClassTree(ApplicationClassSource app) {
		long start = System.nanoTime();
		for(int i=0; i < k; i++) {
			ClassTree tree = new ClassTree(app);
			System.out.println(tree.size());
			
		}
		long el = System.nanoTime() - start;
		return new double[] {conv(el), conv(el/k)};
	}
	
	private static double conv(long l) {
		// ns to ms
		return (double) l / 1_000_000;
	}
}