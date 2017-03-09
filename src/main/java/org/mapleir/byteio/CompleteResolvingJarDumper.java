package org.mapleir.byteio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.klass.ClassHelper;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.blocksplit.SplitMethodWriterDelegate;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarContents;
import org.topdank.byteengineer.commons.data.JarResource;
import org.topdank.byteio.out.JarDumper;
import org.topdank.byteio.util.Debug;

/**
 * Dumps ClassNodes and JarResources back into a file on the local system.
 *
 * @author Bibl
 */
public class CompleteResolvingJarDumper implements JarDumper {

	private final JarContents<?> contents;
	private final ApplicationClassSource source;
	/**
	 * Creates a new JarDumper.
	 *
	 * @param contents Contents of jar.
	 */
	public CompleteResolvingJarDumper(JarContents<ClassNode> contents, ApplicationClassSource source) {
		this.contents = contents;
		this.source = source;
	}

	/**
	 * Dumps the jars contents.
	 *
	 * @param file File to dump it to.
	 */
	@Override
	public void dump(File file) throws IOException {
		if (file.exists())
			file.delete();
		file.createNewFile();
		JarOutputStream jos = new JarOutputStream(new FileOutputStream(file));
		int classesDumped = 0;
		int resourcesDumped = 0;
		for (ClassNode cn : contents.getClassContents()) {
			classesDumped += dumpClass(jos, cn.name, cn);
		}
		for (JarResource res : contents.getResourceContents()) {
			resourcesDumped += dumpResource(jos, res.getName(), res.getData());
		}
		if(!Debug.debugging)
			System.out.println("Dumped " + classesDumped + " classes and " + resourcesDumped + " resources to " + file.getAbsolutePath());
		
		jos.close();
	}

	/**
	 * Writes the {@link ClassNode} to the Jar.
	 *
	 * @param out The {@link JarOutputStream}.
	 * @param cn The ClassNode.
	 * @param name The entry name.
	 * @throws IOException If there is a write error.
	 * @return The amount of things dumped, 1 or if you're not dumping it 0.
	 */
	@Override
	public int dumpClass(JarOutputStream out, String name, ClassNode cn) throws IOException {
		JarEntry entry = new JarEntry(cn.name + ".class");
		out.putNextEntry(entry);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES, new SplitMethodWriterDelegate()) {
			// this method in ClassWriter uses the systemclassloader as
			// a stream location to load the super class, however, most of
			// the time the class is loaded/read and parsed by us so it
			// isn't defined in the system classloader. in certain cases
			// we may not even want it to be loaded/resolved and we can
			// bypass this by implementing the hierarchy scanning algorithm
			// with ClassNodes rather than Classes.
		    @Override
			protected String getCommonSuperClass(String type1, String type2) {
		    	ClassNode ccn = source.findClassNode(type1);
		    	ClassNode dcn = source.findClassNode(type2);
		    	
		    	if(ccn == null) {
//		    		return "java/lang/Object";
		    		ClassNode c = ClassHelper.create(type1);
		    		if(c == null) {
		    			return "java/lang/Object";
		    		}
		    		throw new UnsupportedOperationException(c.toString());
		    		// classTree.build(c);
		    		// return getCommonSuperClass(type1, type2);
		    	}
		    	
		    	if(dcn == null) {

//		    		return "java/lang/Object";
		    		ClassNode c = ClassHelper.create(type2);
		    		if(c == null) {
		    			return "java/lang/Object";
		    		}
		    		throw new UnsupportedOperationException(c.toString());
		    		// classTree.build(c);
		    		// return getCommonSuperClass(type1, type2);
		    	}
		    	
				// TODO: MUST BE CONVERTED TO ACCOUNT FOR DIRECT SUPERS, NOT ALL
		        Set<ClassNode> c = source.getStructures().getSupers(ccn);
		        Set<ClassNode> d = source.getStructures().getSupers(dcn);
		        
		        if(c.contains(dcn))
		        	return type1;
		        
		        if(d.contains(ccn))
		        	return type2;
		        
		        if(Modifier.isInterface(ccn.access) || Modifier.isInterface(dcn.access)) {
		        	// enums as well?
		        	return "java/lang/Object";
		        } else {
		        	do {
		        		ClassNode nccn = source.findClassNode(ccn.superName);
		        		if(nccn == null)
		        			break;
		        		ccn = nccn;
		        		c = source.getStructures().getSupers(ccn);
		        	} while(!c.contains(dcn));
		        	return ccn.name;
		        }
		    }
		};
		
		for(MethodNode m : cn.methods) {
			if(m.instructions.size() > 10000) {
				System.out.println(m + " @" + m.instructions.size());
			}
		}
		
		cn.accept(writer);
		out.write(writer.toByteArray());
		return 1;
	}

	/**
	 * Writes a resource to the Jar.
	 *
	 * @param out The {@link JarOutputStream}.
	 * @param name The name of the file.
	 * @param file File as a byte[].
	 * @throws IOException If there is a write error.
	 * @return The amount of things dumped, 1 or if you're not dumping it 0.
	 */
	@Override
	public int dumpResource(JarOutputStream out, String name, byte[] file) throws IOException {
		JarEntry entry = new JarEntry(name);
		out.putNextEntry(entry);
		out.write(file);
		return 1;
	}
}