package org.rsdeob.deobimpl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.cfg.tree.NodeVisitor;
import org.objectweb.asm.commons.cfg.tree.node.AbstractNode;
import org.objectweb.asm.commons.cfg.tree.node.JumpNode;
import org.objectweb.asm.commons.cfg.tree.node.NumberNode;
import org.objectweb.asm.commons.cfg.tree.util.TreeBuilder;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.deob.IPhase;

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
public class ConstantComparisonReordererPhase implements IPhase, Opcodes {
	
	public static final String KEY_ID = ConstantComparisonReordererPhase.class.getCanonicalName();
	private static final TreeBuilder TREE_BUILDER = new TreeBuilder();
	
	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
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
			swap.method.instructions.remove(swap.insns[0]);
			swap.method.instructions.insert(swap.insns[1], swap.insns[0]);
			
			if(swap.type == OperandSwapType.NULL) {
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
					OperandSwap swap = new OperandSwap(first.method(), new AbstractInsnNode[]{first.insn(), jn.child(1).insn()}, OperandSwapType.NULL);
					swaps.add(swap);
				}
			} else if(jn.opcode() == IF_ICMPEQ || jn.opcode() == IF_ICMPNE) {
				NumberNode nn = jn.firstNumber();
				if(nn != null && nn == first) { // ref check
					OperandSwap swap = new OperandSwap(first.method(), new AbstractInsnNode[]{first.insn(), jn.child(1).insn()}, OperandSwapType.CST);
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
		final MethodNode method;
		// [0] = the constant insn
		// [1] = the jump insn
		final AbstractInsnNode[] insns;
		final OperandSwapType type;

		OperandSwap(MethodNode method, AbstractInsnNode[] insns, OperandSwapType type) {
			this.method = method;
			this.insns = insns;
			this.type = type;
		}
	}
	
	enum OperandSwapType {
		NULL, CST;
	}
}