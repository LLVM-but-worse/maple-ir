package org.rsdeob.deobimpl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.cfg.tree.NodeVisitor;
import org.objectweb.asm.commons.cfg.tree.node.JumpNode;
import org.objectweb.asm.commons.cfg.tree.node.NumberNode;
import org.objectweb.asm.commons.cfg.tree.node.VariableNode;
import org.objectweb.asm.commons.cfg.tree.util.TreeBuilder;
import org.objectweb.asm.tree.*;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.deob.IPhase;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;

/**
 * Jagex's Obfuscater (at least for a while) has been
 * notoriously inserting opaque predicates into the code. <br>
 * In short, these are checks or conditions that should always
 * equate to true and are usually done by checking a value that
 * is passed as a parameter. Therefore most people that want to
 * call a method must search for the value to pass to ensure
 * the predicates false code is not executed. It is currently not
 * known if Jagex care about these failures, however, the obfuscater
 * guarantees that any calls that it makes will never fail (unless
 * the call is inside a dummy method).
 * 
 * <p>
 * There are currently two (at least known) types of predicate actions
 * that are inserted by the obfuscater. Both involve a simple if
 * statement that check if an integer value, which is passed as a
 * parameter to a method is correct. <br>
 * One such action is throwing an IllegalStateException: <br>
 * <code>
 *	if(var5 >= 1389541124) {
 *		throw new IllegalStateException();
 *	}
 * </code> <br>
 * And the other is: <br>
 * <code>
 *	if(var5 >= 1389541124) {
 *		return;
 *	}
 * </code> <br>
 * 
 * Both of these increase code complexity and confuse someone
 * reading the code. It also masks the dummy parameter that
 * the method uses for the predicate value, making it look
 * as if the parameter is used for a legitimate purpose, when
 * in fact it can be removed.
 * </p>
 * 
 * <p>
 * Removing opaque predicates is relatively simple. The only opaque
 * check that is currently inserted is done by checking the value
 * of an argument passed to the method. The comparison instruction
 * is also the same for all checks in the method and the action code
 * is not randomised or shuffled.
 * </p>
 * 
 * <p>
 * We start by traversing through the AST to collate
 * comparison instructions that check the value of the last
 * parameter (if it is an integer value).
 * </p>
 * 
 * <p>
 * We then verify that all of the comparison instructions are
 * using the same opcode and are comparing the same number. Note that this
 * works now, but if the obfuscater becomes more sophisticated, we will
 * need to change this.
 * </p>
 * 
 * <p>
 * We then check the action of the predicates failure to check that it is
 * either a return instruction (or variant?) or if it throws an IllegalStateException.
 * </p>
 * 
 * <p>
 * Finally, we remove the operand loading instructions and the comparison
 * instruction, as well as the predicate fail case action instructions.
 * We then add a GOTO which jumps to the target of the old jump to correct
 * the flow. There is probably a better way to do this and there is most
 * certainly something that I'm missing, but this shouldn't be a problem
 * once the empty goto remover is fixed.
 * </p>
 * 
 * FIXME: *b city voice* "eyo nigga i think this shit here is broke, right quick nigga"
 * 
 * @author Bibl (don't ban me pls)
 * @created 30 May 2015
 */
public class OpaquePredicateRemoverPhase implements IPhase, Opcodes {

	public static final String KEY_ID = OpaquePredicateRemoverPhase.class.getCanonicalName();
	private static final TreeBuilder TREE_BUILDER = new TreeBuilder();

	public final Map<MethodNode, Opaque> foundPredicates = new HashMap<MethodNode, Opaque>();

	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
		int count = 0, mcount = 0;
		int mdiscard = 0, typediscard = 0;

		for (ClassNode cn : cxt.getNodes().values()) {
			for (MethodNode mn : cn.methods) {
				if (ParameterUtil.isDummy(mn)) {
					PairCollector visitor = new PairCollector();
					visitor.targetVar = ParameterUtil.calculateLastParameterIndex(Type.getArgumentTypes(mn.desc), Modifier.isStatic(mn.access));
					TREE_BUILDER.build(mn).accept(visitor);

					Set<ComparisonPair> pairs = visitor.pairs;
					if (pairs.size() > 0) {
						if (valid(pairs)) {
							/*
							 * iload4 ldc 1797324181 (java.lang.Integer)
							 * if_icmpeq 
							 * L3 
							 * new java/lang/IllegalStateException
							 * dup 
							 * invokespecial java/lang/IllegalStateException <init>(()V); 
							 * athrow
							 */
							Map<ComparisonPair, List<AbstractInsnNode>> map = new HashMap<ComparisonPair, List<AbstractInsnNode>>();
							boolean b = false;

							for (ComparisonPair pair : pairs) {
								List<AbstractInsnNode> block = block(pair);

								if (block == null) {
									b = true;
									break;
								}
								// TODO: Account for meta instructions.
								if (block.size() == 1) {
									if (block.get(0).opcode() != RETURN) {
										b = true;
										break;
									}
								} else {
									if (block.get(block.size() - 1).opcode() != ATHROW) {
										b = true;
										break;
									} else {
										AbstractInsnNode t = block.get(0);
										if (t instanceof TypeInsnNode) {
											TypeInsnNode tin = (TypeInsnNode) t;
											if (!tin.desc.equals("java/lang/IllegalStateException")) {
												b = true;
												break;
											}
										} else {
											b = true;
											break;
										}
									}
								}

								map.put(pair, block);
							}

							if (!b) {
								for (Entry<ComparisonPair, List<AbstractInsnNode>> e : map.entrySet()) {
									Jump jump = e.getKey().jump;

									if (!foundPredicates.containsKey(jump.jin.method)) {
										foundPredicates.put(jump.jin.method, new Opaque(jump.jin.opcode(), e.getKey().num));
									}

									/*
									 * Redirect the false jump location of the
									 * jump and force it to the target.
									 */
									mn.instructions.insert(jump.jin, new JumpInsnNode(GOTO, jump.jin.label));
									mn.instructions.remove(jump.jin);

									for (AbstractInsnNode a : jump.insns) {
										mn.instructions.remove(a);
									}

									for (AbstractInsnNode a : e.getValue()) {
										mn.instructions.remove(a);
									}
									count++;
								}
								mcount++;
							} else {
								typediscard++;
							}
						} else {
							// System.out.printf("%s (%d) %s.%n", mn, visitor.targetVar, pairs);
							mdiscard++;
						}
					}

					visitor.pairs.clear();
				}
			}
		}

		System.out.printf("   Removed %d opaque predicates (%d methods).%n", count, mcount);
		System.out.printf("   %d method discards and %d type discards.%n", mdiscard, typediscard);
	}

	private static List<AbstractInsnNode> block(ComparisonPair p) {
		List<AbstractInsnNode> ains = new ArrayList<AbstractInsnNode>();
		AbstractInsnNode ain = p.jump.jin.getNext();
		while (true) {
			if (ain == null)
				return null;

			ains.add(ain);

			if (ain.opcode() == ATHROW || ain.opcode() == RETURN) {
				return ains;
			} else if (ain.type() == AbstractInsnNode.JUMP_INSN || ain.type() == AbstractInsnNode.LOOKUPSWITCH_INSN
					|| ain.type() == AbstractInsnNode.TABLESWITCH_INSN) {
				return null;
			}

			ain = ain.getNext();
		}
	}

	private static boolean valid(Set<ComparisonPair> psets) {
		int num = -1;
		int jop = -1;
		/*
		 * Check to see if the comparison opcodes and the number being compared
		 * is the same. (we need to make sure that the parameter is actually a
		 * valid opaque).
		 */
		for (ComparisonPair p : psets) {
			if (num == -1) {
				num = p.num;
			} else if (num != p.num) {
				return false;
			}

			if (jop == -1) {
				jop = p.jump.jin.opcode();
			} else if (p.jump.jin.opcode() != jop) {
				return false;
			}
		}
		return true;
	}

	class PairCollector extends NodeVisitor {
		Set<ComparisonPair> pairs = new HashSet<ComparisonPair>();
		int targetVar;

		@Override
		public void visitJump(JumpNode jn) {
			/* jn.method().key().equals("aj.u(IS)V") && */
			if (jn.opcode() != GOTO && jn.children() == 2) {
				// eg.
				// iload2
				// sipush 1338
				NumberNode nn = jn.firstNumber();
				VariableNode vn = jn.firstVariable();

				if (nn != null && vn != null && vn.var() == targetVar) {
					Jump jump = new Jump(jn.insn(), nn.insn(), vn.insn());
					ComparisonPair pair = new ComparisonPair(nn.number(), jump);

					pairs.add(pair);
				}
			}
		}
	}

	class ComparisonPair {
		final int num;
		final Jump jump;

		ComparisonPair(int num, Jump jump) {
			this.num = num;
			this.jump = jump;
		}

		@Override
		public String toString() {
			return "ComparisonPair [num=" + num + ", jump=" + jump + "]";
		}
	}

	class Jump {
		final JumpInsnNode jin;
		final AbstractInsnNode[] insns;

		Jump(JumpInsnNode jin, AbstractInsnNode... insns) {
			this.jin = jin;
			this.insns = insns;
		}

		@Override
		public String toString() {
			return "Jump [jin=" + jin + ", insns=" + Arrays.toString(insns) + "]";
		}
	}

	public class Opaque {
		final int opcode;
		final int num;

		Opaque(int opcode, int num) {
			this.opcode = opcode;
			this.num = num;
		}

		public int getOpcode() {
			return opcode;
		}

		public int getNum() {
			return num;
		}
	}
}