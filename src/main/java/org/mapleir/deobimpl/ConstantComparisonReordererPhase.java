package org.mapleir.deobimpl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.cfg.tree.NodeVisitor;
import org.objectweb.asm.commons.cfg.tree.node.AbstractNode;
import org.objectweb.asm.commons.cfg.tree.node.JumpNode;
import org.objectweb.asm.commons.cfg.tree.node.NumberNode;
import org.objectweb.asm.commons.cfg.tree.util.TreeBuilder;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Swaps instructions to change null checks as 
 * Jagex's obfuscator often swaps a null check such as <br>
 * 
 * <code>if(obj != null)</code> <br>
 * to <br>
 * <code>if(null != obj)</code> <br>
 * 
 * <p>
 * We do this as it makes analysis a bit easier.
 * 
 * @author Bibl (don't ban me pls)
 * @created 31 May 2015
 */
public class ConstantComparisonReordererPhase implements ICompilerPass, Opcodes {
	
	public static final String KEY_ID = ConstantComparisonReordererPhase.class.getCanonicalName();
	private static final TreeBuilder TREE_BUILDER = new TreeBuilder();
	
	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		NodeVisitorImpl nv = new NodeVisitorImpl();
		
		for(ClassNode cn : cxt.getNodes().values()) {
			for(MethodNode mn : cn.methods) {
				TREE_BUILDER.build(mn).accept(nv);
			}
		}
		
		int n = 0;
		int c = 0;
		for(OperandSwap swap : nv.swaps) {
			/* Remove the aconst_null or the constant and add it after. */
			swap.method.instructions.remove(swap.cst);
			swap.method.instructions.insertBefore(swap.jmp, swap.cst);
			
			if(swap.type == OperandSwap.NULL) {
				n++;
			} else {
				c++;
			}
		}
		
		System.out.printf("   Swapped %d null check operands.%n", n);
		System.out.printf("   Swapped %d cst check operands.%n", c);
		
		nv.swaps.clear();
	}
	
	class NodeVisitorImpl extends NodeVisitor {
		final Set<OperandSwap> swaps = new HashSet<OperandSwap>();
		@Override
		public void visitJump(JumpNode jn) {
			
			/*
			 * aconst_null
			 * getstatic Client.jz:Widget
			 * if_acmpeq L10
			 * 
			 *     to
			 * 
			 * getstatic Client.jz:Widget
			 * aconst_null
			 */
			AbstractNode first = jn.child(0);
			if(jn.opcode() == IF_ACMPEQ || jn.opcode() == IF_ACMPNE) {
				if(first.opcode() == ACONST_NULL) {
					OperandSwap swap = new OperandSwap(first.method(), jn.insn(), first.insn(), OperandSwap.NULL);
					swaps.add(swap);
				}
			} else if(jn.opcode() == IF_ICMPEQ || jn.opcode() == IF_ICMPNE) {
				NumberNode nn = jn.firstNumber();
				if(nn != null && nn == first) { // ref check
					OperandSwap swap = new OperandSwap(first.method(), jn.insn(), first.insn(), OperandSwap.CST);
					swaps.add(swap);
				}
			}/* else if(jn.opcode() == IFNONNULL) {
				*
				 * L1: aconst_null
				 *     ifnonnull L3
				 * L2: do stuff
				 * L3: thing thing
				 * 
				 *  if(null != null)
				 *      thing thing
				 *  else
				 *      do stuff
				 *  
				 *  
				 *  L1: aconst_null
				 *      ifnull L2
				 *  
				 *
				LabelNode target = jn.insn().label;
				
			}*/
		}
	}
	
	class OperandSwap {
		public static final int NULL = 0x1, CST = 0x2;
		final MethodNode method;
		final AbstractInsnNode jmp;
		final AbstractInsnNode cst;
		final int type;

		OperandSwap(MethodNode method, AbstractInsnNode jmp, AbstractInsnNode cst, int type) {
			this.method = method;
			this.jmp = jmp;
			this.cst = cst;
			this.type = type;
		}
	}
}