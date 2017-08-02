package org.mapleir;

import java.io.File;

import org.mapleir.app.service.ApplicationClassSource;
import org.objectweb.asm.tree.ClassNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

public class UnlinkedClassTreeBoot {

	public static void main(String[] args) throws Exception {
		File f = new File("res/allatori6.1san.jar");
		System.out.println("Preparing to run on " + f.getAbsolutePath());
		
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(new JarInfo(f));
		dl.download();
		String name = f.getName().substring(0, f.getName().length() - 4);
		ApplicationClassSource app = new ApplicationClassSource(name, dl.getJarContents().getClassContents());
		
	}
}