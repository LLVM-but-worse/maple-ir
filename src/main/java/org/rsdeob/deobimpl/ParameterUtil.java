package org.rsdeob.deobimpl;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public class ParameterUtil {

	private static final Set<String> DUMMY_PARAM_TYPES = new HashSet<String>();

	static {
		DUMMY_PARAM_TYPES.add("I");
		DUMMY_PARAM_TYPES.add("S");
		DUMMY_PARAM_TYPES.add("B");
	}
	
	public static boolean isDummy(Type desc) {
		return isDummy(desc.getDescriptor());
	}
	
	public static boolean isDummy(String desc) {
		return DUMMY_PARAM_TYPES.contains(desc);
	}
	
	public static boolean isDummy(MethodNode mn) {
		Type[] args = Type.getArgumentTypes(mn.desc);
		if(args.length <= 0) {
			return false;
		}
		Type last = args[args.length - 1];
		String desc = last.getDescriptor();
		return isDummy(desc);
	}
	
	public static int calculateLastParameterIndex(Type[] args, boolean stat) {
		// starting index for static method = 0,
		// starting index for virtual method = 1 (this = 0)
		// but we have to start at (that - 1) as the first
		// parameter index will add to it.
		int c = stat ? -1 : 0;
		for(int i=0; i < args.length; i++) {
			switch(args[i].getDescriptor()) {
				case "D":
				case "J":
					c += 2;
					break;
				default:
					c += 1;
					break;
			}
		}
		return c;
	}
}