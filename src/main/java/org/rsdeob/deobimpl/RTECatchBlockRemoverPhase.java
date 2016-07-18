package org.rsdeob.deobimpl;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;
import org.rsdeob.stdlib.collections.graph.util.GraphUtils;
import org.rsdeob.stdlib.deob.IPhase;

public class RTECatchBlockRemoverPhase implements IPhase, Opcodes {

	public static final String KEY_ID = RTECatchBlockRemoverPhase.class.getCanonicalName();
	private static final String RUNTIME_EXCEPTION_TYPE = RuntimeException.class.getCanonicalName().replace(".", "/");
	
	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
		int catchblockkills = 0;
		
		for(ClassNode cn : cxt.getNodes().values()) {
			for(MethodNode m : cn.methods) {
				if(m.instructions.size() == 0)
					continue;
				
				ControlFlowGraph cfg = cxt.createControlFlowGraph(m);				
				boolean change = false;
				for(ExceptionRange<BasicBlock> r : cfg.getRanges()) {
					if(r.getTypes().contains(RUNTIME_EXCEPTION_TYPE)) {
						// new java/lang/StringBuilder
						// dup
						// invokespecial java/lang/StringBuilder <init>(()V);
						// ldc "fh.c(" (java.lang.String)
						// invokevirtual java/lang/StringBuilder append((Ljava/lang/String;)Ljava/lang/StringBuilder;);
						// ldc 41 (java.lang.Integer)
						// invokevirtual java/lang/StringBuilder append((C)Ljava/lang/StringBuilder;);
						// invokevirtual java/lang/StringBuilder toString(()Ljava/lang/String;);
						// invokestatic df f((Ljava/lang/Throwable;Ljava/lang/String;)Lep;);
						// athrow

						BasicBlock b = r.getHandler();
						if(b.cleanSize() == 10) {
							if(b.realFirst().opcode() == Opcodes.NEW && b.realLast().opcode() == Opcodes.ATHROW) {
								// assume the rest of the instructions are the same
								cfg.removeVertex(b);
								m.tryCatchBlocks.remove(r.getNode());
								change = true;
								catchblockkills++;
							}
						}
					}
				}
				
				if(change) {
					m.instructions = GraphUtils.recreate(cfg, new ArrayList<>(cfg.vertices()), false);
				}
			}
		}
		
//		// dog wrote this
//		for (ClassNode cn : cxt.getNodes().values()) {
//			for (MethodNode mn : cn.methods) {
//				List<TryCatchBlockNode> remove = mn.tryCatchBlocks.stream().filter(tcb -> tcb.type != null && tcb.type.contains("RuntimeException")).collect(Collectors.toList());
//
//				//	if (remove.size() > 1)
//				//	continue;
//
//				List<TryCatchBlockNode> skip = new LinkedList<>();
//				for (TryCatchBlockNode tcb : remove) {
//					if (skip.contains(tcb)) {
//						mn.tryCatchBlocks.remove(tcb);
//						catchblockkills++;
//						continue;
//					}
//					skip.addAll(remove.stream().filter(check -> check != tcb && check.handler == tcb.handler).collect(Collectors.toList()));
//					AbstractInsnNode cur = tcb.handler.getNext();
//					while (!isCodeKill(cur.opcode()))
//						cur = cur.getNext();
//					if (cur.opcode() == ATHROW) {
//						cur = tcb.handler.getNext();
//						while (!isCodeKill(cur.opcode())) {
//							AbstractInsnNode temp = cur;
//							cur = cur.getNext();
//							mn.instructions.remove(temp);
//						}
//						mn.instructions.remove(cur);
//						mn.tryCatchBlocks.remove(tcb);
//						catchblockkills++;
//					}
//				}
//			}
//		}

		System.out.printf("   Removed %d redundant exceptions.%n", catchblockkills);
	}

//	private static boolean isCodeKill(int opcode) {
//		return (opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW;
//	}
}