package org.rsdeob.deobimpl;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.deob.IPhase;

public class RTECatchBlockRemoverPhase implements IPhase, Opcodes {

	public static final String KEY_ID = RTECatchBlockRemoverPhase.class.getCanonicalName();

	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
		int catchblockkills = 0;
		// dog wrote this
		for (ClassNode cn : cxt.getNodes().values()) {
			for (MethodNode mn : cn.methods) {
				List<TryCatchBlockNode> remove = mn.tryCatchBlocks.stream().filter(tcb -> tcb.type != null && tcb.type.contains("RuntimeException"))
						.collect(Collectors.toList());
				List<TryCatchBlockNode> skip = new LinkedList<>();
				for (TryCatchBlockNode tcb : remove) {
					if (skip.contains(tcb)) {
						mn.tryCatchBlocks.remove(tcb);
						catchblockkills++;
						continue;
					}
					skip.addAll(remove.stream().filter(check -> check != tcb && check.handler == tcb.handler).collect(Collectors.toList()));
					AbstractInsnNode cur = tcb.handler.getNext();
					while (!isCodeKill(cur.opcode()))
						cur = cur.getNext();
					if (cur.opcode() == ATHROW) {
						cur = tcb.handler.getNext();
						while (!isCodeKill(cur.opcode())) {
							AbstractInsnNode temp = cur;
							cur = cur.getNext();
							mn.instructions.remove(temp);
						}
						mn.instructions.remove(cur);
						mn.tryCatchBlocks.remove(tcb);
						catchblockkills++;
					}
				}
			}
		}

		System.out.printf("   Removed %d redundant exceptions.%n", catchblockkills);
	}

	private static boolean isCodeKill(int opcode) {
		return (opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW;
	}
}