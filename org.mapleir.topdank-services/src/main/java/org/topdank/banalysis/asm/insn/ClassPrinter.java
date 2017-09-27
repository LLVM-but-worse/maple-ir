package org.topdank.banalysis.asm.insn;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class ClassPrinter {

	private static final Map<Integer, String> ACCESS_MODIFIERS = new HashMap<Integer, String>();
	
	static{
		for(Field f : Opcodes.class.getDeclaredFields()){
			if(f.getName().startsWith("ACC_")){
				try{
					int val = f.getInt(null);
					ACCESS_MODIFIERS.put(val, f.getName().substring(4).toLowerCase());
				} catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	}
	
	public static String print(ClassNode cn){
		StringBuilder sb = new StringBuilder();
		printModifiers(sb, cn.access);
		sb.append(cn.name).append(" extends ").append(cn.superName).append(" ");
		if(cn.interfaces.size() > 0){
			sb.append("implements ");
			Iterator<String> it = cn.interfaces.iterator();
			while(it.hasNext()){
				String intf = it.next();
				sb.append(intf);
				if(it.hasNext())
					sb.append(",");
				sb.append(" ");
			}
		}
		
		sb.append("{").append("\n");
		sb.append("\n");
		for(FieldNode f : cn.fields){
			sb.append("    ");
			printModifiers(sb, f.access);
			sb.append(f.name).append(" ").append(f.desc).append("\n");
		}
		
		sb.append("\n");
		
		for(MethodNode m : cn.methods){
			sb.append("    ");
			printModifiers(sb, m.access);
			sb.append(m.name).append(" ").append(m.desc).append(" {").append("\n");
			for(String s : InstructionPrinter.getLines(m)){
				sb.append("       ").append(s).append("\n");
			}
			sb.append("    }").append("\n\n");
		}
		
		sb.append("}");
		
		return sb.toString();
	}
	
	private static void printModifiers(StringBuilder sb, int access){
		StringBuilder sb2 = new StringBuilder();
		
		for(Entry<Integer, String> e : ACCESS_MODIFIERS.entrySet()){
			int m = e.getKey();
			if((access & m) == m){
				sb.append(e.getValue()).append(" ");
			}
		}
		
		if(sb.charAt(sb.length() - 1) != ' ')
			sb.append(" ");
		
		sb.append(sb2.toString());
	}
}