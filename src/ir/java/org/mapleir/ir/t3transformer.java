package org.mapleir.ir;

import java.lang.reflect.Method;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class t3transformer implements Opcodes {

	public static void main(String[] args) throws Exception {
		ClassNode cn = new ClassNode();
		ClassReader cr = new ClassReader(t3.class.getCanonicalName());
		cr.accept(cn, 0);
		
		for(MethodNode m : cn.methods) {
			if(m.name.equals("v_aq") || m.name.equals("v_rel")) {
				for(AbstractInsnNode ain : m.instructions.toArray()) {
					if(ain.opcode() == INVOKEVIRTUAL) {
						MethodInsnNode min = (MethodInsnNode) ain;
						int op = -1;
						if(min.name.equals("lock")) {
							op = MONITORENTER;
						} else if(min.name.equals("unlock")) {
							op = MONITOREXIT;
						}
						
						if(op != -1) {
							InsnList il = make(op);
							m.instructions.insert(ain, il);
							m.instructions.remove(ain);
						}
					}
				}
			}
		}
		
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cn.accept(cw);
		byte[] bs = cw.toByteArray();
		
//		FileOutputStream fos = new FileOutputStream(new File("out/t3.class"));
//		fos.write(bs, 0, bs.length);
//		fos.close();
		
		String cname = cn.name.replaceAll("/", ".");
		ClassLoader cl = new ClassLoader() {
			{
				defineClass(cname, bs, 0, bs.length);
			}
		};
		
		Class<?> c = cl.loadClass(cname);
		Method m = c.getDeclaredMethod("main", new Class[]{String[].class});
		m.invoke(null, (Object) null);
	}
	
	private static InsnList make(int op) {
		InsnList list = new InsnList();
		list.add(new VarInsnNode(ALOAD, 0));
		list.add(new FieldInsnNode(GETSTATIC, "org/mapleir/ir/t3", "lock", "Ljava/lang/Object;"));
		list.add(new InsnNode(op));
		return list;
	}
}