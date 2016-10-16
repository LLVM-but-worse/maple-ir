package org.topdank.banalysis.asm.insn;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.objectweb.asm.tree.*;
import org.topdank.banalysis.asm.desc.OpcodeInfo;

/**
 * Prints out ASM {@link MethodNode}s instructions. <br>
 * @author Bibl
 * 
 */
public class InstructionPrinter {
	
	/** The MethodNode to print **/
	protected MethodNode mNode;
	
	protected int[] pattern;
	protected boolean match;
	protected InstructionSearcher searcher;
	
	protected List<AbstractInsnNode> matchedInsns;
	protected Map<LabelNode, Integer> labels;
	
	public InstructionPrinter(MethodNode m) {
		mNode = m;
		labels = new HashMap<>();
		// matchedInsns = new ArrayList<AbstractInsnNode>(); // ingnored because match = false
		match = false;
	}
	
	public InstructionPrinter(MethodNode m, InstructionPattern pattern) {
		mNode = m;
		labels = new HashMap<>();
		searcher = new InstructionSearcher(m.instructions, pattern);
		match = searcher.search();
		if (match) {
			matchedInsns = new ArrayList<>();
			for(AbstractInsnNode[] ains : searcher.getMatches()) {
				for(AbstractInsnNode ain : ains) {
					matchedInsns.add(ain);
				}
			}
		}
	}
	
	/**
	 * Creates the print
	 * @return The print as an ArrayList
	 */
	public ArrayList<String> createPrint() {
		ArrayList<String> info = new ArrayList<>();
		ListIterator<?> it = mNode.instructions.iterator();
		while (it.hasNext()) {
			AbstractInsnNode ain = (AbstractInsnNode) it.next();
			String line = "";
			if (ain instanceof VarInsnNode) {
				line = printVarInsnNode((VarInsnNode) ain, it);
			} else if (ain instanceof IntInsnNode) {
				line = printIntInsnNode((IntInsnNode) ain, it);
			} else if (ain instanceof FieldInsnNode) {
				line = printFieldInsnNode((FieldInsnNode) ain, it);
			} else if (ain instanceof MethodInsnNode) {
				line = printMethodInsnNode((MethodInsnNode) ain, it);
			} else if (ain instanceof LdcInsnNode) {
				line = printLdcInsnNode((LdcInsnNode) ain, it);
			} else if (ain instanceof InsnNode) {
				line = printInsnNode((InsnNode) ain, it);
			} else if (ain instanceof JumpInsnNode) {
				line = printJumpInsnNode((JumpInsnNode) ain, it);
			} else if (ain instanceof LineNumberNode) {
				line = printLineNumberNode((LineNumberNode) ain, it);
			} else if (ain instanceof LabelNode) {
				line = printLabelnode((LabelNode) ain);
			} else if (ain instanceof TypeInsnNode) {
				line = printTypeInsnNode((TypeInsnNode) ain);
			} else if (ain instanceof FrameNode) {
				line = printFrameNode((FrameNode) ain);
			} else if (ain instanceof IincInsnNode) {
				line = printIincInsnNode((IincInsnNode) ain);
			} else if (ain instanceof TableSwitchInsnNode) {
				line = printTableSwitchInsnNode((TableSwitchInsnNode) ain);
			} else if (ain instanceof LookupSwitchInsnNode) {
				line = printLookupSwitchInsnNode((LookupSwitchInsnNode) ain);
			} else if (ain instanceof MultiANewArrayInsnNode) {
				line = printMultiANewArrayInsnNode((MultiANewArrayInsnNode) ain);
			} else {
				line += "UNKNOWN-NODE: " + nameOpcode(ain.opcode()) + " " + ain.toString();
			}
			if (!line.equals("")) {
				if (match)
					if (matchedInsns.contains(ain))
						line = "   -> " + line;
				
				info.add(line);
			}
		}
		return info;
	}
	
	protected String printFrameNode(FrameNode fn) {
		return "";
	}

	protected String printMultiANewArrayInsnNode(MultiANewArrayInsnNode main) {
		return nameOpcode(main.opcode()) + " " + main.dims + "x " + main.desc;
	}

	protected String printVarInsnNode(VarInsnNode vin, ListIterator<?> it) {
		return nameOpcode(vin.opcode()) + " " + vin.var;
	}
	
	protected String printIntInsnNode(IntInsnNode iin, ListIterator<?> it) {
		return nameOpcode(iin.opcode()) + " " + iin.operand;
	}
	
	protected String printFieldInsnNode(FieldInsnNode fin, ListIterator<?> it) {
		return nameOpcode(fin.opcode()) + " " + fin.owner + " " + fin.name + ":" + fin.desc;
	}
	
	protected String printMethodInsnNode(MethodInsnNode min, ListIterator<?> it) {
		return nameOpcode(min.opcode()) + " " + min.owner + " " + min.name + ":" + min.desc;
	}
	
	protected String printLdcInsnNode(LdcInsnNode ldc, ListIterator<?> it) {
		if (ldc.cst instanceof String)
			return nameOpcode(ldc.opcode()) + " \"" + ldc.cst + "\" (" + ldc.cst.getClass().getCanonicalName() + ")";
		
		return nameOpcode(ldc.opcode()) + " " + ldc.cst + " (" + ldc.cst.getClass().getCanonicalName() + ")";
	}
	
	protected String printInsnNode(InsnNode in, ListIterator<?> it) {
		return nameOpcode(in.opcode());
	}
	
	protected String printJumpInsnNode(JumpInsnNode jin, ListIterator<?> it) {
		String line = nameOpcode(jin.opcode()) + " L" + resolveLabel(jin.label);
		return line;
	}
	
	protected String printLineNumberNode(LineNumberNode lin, ListIterator<?> it) {
		return "";
	}
	
	protected String printLabelnode(LabelNode label) {
		return "L" + resolveLabel(label) + " " + label.hashCode();
	}
	
	protected String printTypeInsnNode(TypeInsnNode tin) {
		return nameOpcode(tin.opcode()) + " " + tin.desc;
	}
	
	protected String printIincInsnNode(IincInsnNode iin) {
		return nameOpcode(iin.opcode()) + " " + iin.var + " " + iin.incr;
	}
	
	protected String printTableSwitchInsnNode(TableSwitchInsnNode tin) {
		String line = nameOpcode(tin.opcode()) + " \n";
		List<?> labels = tin.labels;
		int count = 0;
		for(int i = tin.min; i <= tin.max; i++) {
			line += "           val: " + i + " -> " + "L" + resolveLabel((LabelNode) labels.get(count++)) + "\n";
		}
		line += "           default" + " -> L" + resolveLabel(tin.dflt) + "";
		return line;
	}
	
	protected String printLookupSwitchInsnNode(LookupSwitchInsnNode lin) {
		String line = nameOpcode(lin.opcode()) + ": \n";
		List<?> keys = lin.keys;
		List<?> labels = lin.labels;
		
		for(int i = 0; i < keys.size(); i++) {
			int key = (Integer) keys.get(i);
			LabelNode label = (LabelNode) labels.get(i);
			line += "           val: " + key + " -> " + "L" + resolveLabel(label) + "\n";
		}
		line += "           default" + " -> L" + resolveLabel(lin.dflt) + "";
		return line;
	}
	
	protected String nameOpcode(int opcode) {
		return "    " + OpcodeInfo.OPCODES.get(opcode).toLowerCase();
	}
	
	protected int resolveLabel(LabelNode label) {
		if (labels.containsKey(label)) {
			return labels.get(label);
		} else {
			int newLabelIndex = labels.size() + 1;
			labels.put(label, newLabelIndex);
			return newLabelIndex;
		}
	}
	
	/**
	 * Creates the print
	 * @return The print as a string array
	 */
	public String[] getLines() {
		ArrayList<String> lines = createPrint();
		return lines.toArray(new String[lines.size()]);
	}
	
	/**
	 * Static method to print
	 * @param lines To print
	 */
	public static void consolePrint(String[] lines) {
		for(String line : lines) {
			System.out.println(line);
		}
	}
	
	/**
	 * Prints out the MethodNode
	 * @param m MethodNode to print
	 */
	public static void consolePrint(MethodNode m) {
		consolePrint(new InstructionPrinter(m).getLines());
	}
	
	public static String[] getLines(MethodNode m) {
		return new InstructionPrinter(m).getLines();
	}
	
	public static void saveTo(File file, InstructionPrinter printer) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			for(String s : printer.createPrint()) {
				bw.write(s);
				bw.newLine();
			}
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}