package org.rsdeob.deobimpl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.rsdeob.stdlib.IContext;
import org.rsdeob.stdlib.deob.IPhase;
import org.rsdeob.stdlib.klass.ClassTree;
import org.rsdeob.stdlib.klass.InheritedMethodMap;
import org.rsdeob.stdlib.klass.MethodCache;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;

/**
 * <p>
 * A transformer that finds dummy parameters on methods that are inserted by
 * Jagex's obfuscater. Strict parameter validation is done to ensure that the
 * parameter is indeed a dummy and is not actually a real parameter.
 * 
 * <p>
 * Firstly all of the methods in the gamepack are checked to see if their last
 * parameter is a either a byte, short or integer as these are currently the
 * only dummy parameter types that the obfuscater adds.
 * 
 * <p>
 * We then have to calculate the index of the local parameter in the locals
 * depending on whether the method is static or not. Then we go through the
 * method and check for inc instructions or var instructions to see if the
 * parameter is used. If the parameter is used even once, we remove it from the
 * Set. <br>
 * Note that some dummy parameters are used to fulfil opaque predicate checks
 * and so these must be removed before calling this transformer.
 * 
 * <p>
 * Then a chain is built of all of the methods so that each method is linked
 * with it's parent method (if it is overridden). This allows us to check if the
 * parameter is used in a super or subclass. If this fails, the entire method
 * chain is dropped and none of those methods are changed.
 * 
 * <p>
 * Then once we are sure that we can change the method description, we remove
 * the last parameter and then search the entire code for calls to the old
 * method. For each call we find, we change the description of the method and
 * then add a POP before the call to make sure that the value that was
 * previously passed is taken from the stack before the method is called. <br>
 * 
 * FIXME: *b city voice*
 * "eyo nigga i think this shit here is broke, right quick nigga"
 * 
 * @author Bibl (don't ban me pls)
 * @created 31 May 2015
 */
public class EmptyParameterFixerPhase implements IPhase {

	public static final String KEY_ID = EmptyParameterFixerPhase.class.getCanonicalName();

	private int startSize, endSize;
	private int callnames, unchanged, nulls;
	private final List<MethodNode> skipped = new ArrayList<MethodNode>();
	private final Set<MethodNode> changed = new HashSet<MethodNode>();

	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
		ClassTree tree = new ClassTree(cxt.getNodes());
		
		Map<MethodNode, String> map = new HashMap<MethodNode, String>();

		for (ClassNode cn : tree) {
			for (MethodNode m : cn.methods) {
				if (m.name.equals("<init>") || m.desc.equals("<clinit>") || !ParameterUtil.isDummy(m))
					continue;

				if (m.name.length() > 2) {
					skipped.add(m);
					continue;
				}

				int targetVar = ParameterUtil.calculateLastParameterIndex(Type.getArgumentTypes(m.desc), Modifier.isStatic(m.access));
				// System.out.printf("%s (%b) -> %d.%n", m, Modifier.isStatic(m.access), targetVar);
				if (isUnused(m, targetVar)) {
					String newDesc = newDesc(m.desc);
					// fixes.put(m.key(), newDesc);
					map.put(m, newDesc);
					// System.out.println(m.desc + " -> " + newDesc);
				}
			}
		}

		InheritedMethodMap mmp = new InheritedMethodMap(tree, true);
		MethodCache cache = new MethodCache(tree.getClasses().values());

		startSize = map.size();

		/*
		 * We have to validate the methods. Since a method can be overridden or
		 * inherited, before we change the description of one method, we have to
		 * check the super and delegate methods to verify that they also have
		 * their last parameter as a dummy parameter.
		 */
		Set<MethodNode> invalid = new HashSet<MethodNode>();
		Collection<MethodNode> methods = map.keySet();
		for (MethodNode m : methods) {
			Set<MethodNode> ms = mmp.getData(m).getAggregates();
			if (ms.size() > 0) {
				/*
				 * Since we've gone through all the classes already and found
				 * the dummy parameter methods, if any of the aggregate methods
				 * aren't in the collected set, then we can't reduce any of
				 * them.
				 */
				add(invalid, m, ms);
			}
		}

		// FIXME: Is something going wrong?

		System.out.printf("   Check 1: discarding %d methods.%n", invalid.size());

		/*
		 * If we want a deob for src code, then we don't want to be aggressive
		 * as we won't get recompilable code (duplicate parameter methods) but
		 * for analysis, it's fine.
		 */
		boolean aggressive = false;
		if (!aggressive) {
			for (Entry<MethodNode, String> e : map.entrySet()) {
				MethodNode m = e.getKey();
				String newKey = calculateParamKey(m.name, e.getValue());
				Set<MethodNode> ms = mmp.getData(m).getAggregates();

				Set<MethodNode> collisions = checkCollisions(map, m, newKey);
				if (!collisions.isEmpty()) {
					add(invalid, m, collisions, ms);
					// continue topFor;
				}

				if (ms.size() > 0) {
					for (MethodNode m1 : ms) {
						/* Collision, don't rename any of the methods. */
						collisions = checkCollisions(map, m1, newKey);
						if (!collisions.isEmpty()) {
							add(invalid, m1, collisions, ms);
						}
					}
				}
			}
		}

		System.out.printf("   Check 2: discarding %d methods.%n", invalid.size());
		System.out.println("   Got: " + invalid);

		for (MethodNode m : invalid) {
			map.remove(m);
		}

		endSize = map.size();

		invalid.clear();

		// System.out.printf("start=%d, end=%d.%n", startSize, map.size());
		fix(tree, mmp, cache, map);

		System.out.printf("   Skipped methods: %s.%n", skipped);
		System.out.printf("   map.start=%d, map.end=%d.%n", startSize, endSize);
		System.out.printf("   %d empty parameter methods changed.%n", endSize);
		System.out.printf("   %d calls changed.%n", callnames);
		System.out.printf("   %d unchanged calls and %d nulls.%n", unchanged, nulls);

	}

	@SafeVarargs
	public static <T> void add(Set<T> set, T t, Set<T>... sets) {
		for (Set<T> s : sets) {
			set.addAll(s);
		}
		set.add(t);
	}

	/*
	 * 02/07/15, 9:01, we need to change this to only take parameters into
	 * account and not the return type since we can have two methods which both
	 * have the same parameters but different return types, failing the
	 * collision test.
	 */
	public String calculateParamKey(MethodNode m) {
		return calculateParamKey(m.name, m.desc);
	}

	public String calculateParamKey(String name, String desc) {
		StringBuilder sb = new StringBuilder();
		sb.append(name);
		sb.append("(");
		for (Type arg : Type.getArgumentTypes(desc)) {
			sb.append(arg.getDescriptor());
		}
		sb.append(")");
		return sb.toString();
	}

	private Set<MethodNode> checkCollisions(Map<MethodNode, String> remapped, MethodNode m, String newParamKey) {
		// if(m.owner.name.equals("s") && m.name.equals("n")) {
		// return checkCollisions2(remapped, m, newParamKey);
		// }

		Set<MethodNode> set = new HashSet<MethodNode>();
		for (MethodNode m2 : m.owner.methods) {
			String paramKey = calculateParamKey(m2);
			if (paramKey.equals(newParamKey) && m2 != m) {
				set.add(m2);
			}

			String remappedDesc = remapped.get(m2);
			if (remappedDesc != null) {
				String remappedKey = calculateParamKey(m2.name, remappedDesc);
				if (remappedKey.equals(newParamKey) && m2 != m) {
					set.add(m2);
				}
			}

		}
		return set;
	}

	private void fix(ClassTree tree, InheritedMethodMap mmp, MethodCache cache, Map<MethodNode, String> map) {
		/*
		 * We no longer need to do this (counting) due to the prevalidation done
		 * above which proves that all of the aggregate methods of each of the
		 * methods in the map is actually a dummy parameter method.
		 * 
		 * 
		 * int count1 = 0;
		 * 
		 * for(Entry<MethodNode, String> e : map.entrySet()) { MethodNode m =
		 * e.getKey(); Set<MethodNode> ms = mmp.getData(m).getAggregates();
		 * 
		 * m.desc = e.getValue();
		 * 
		 * for(MethodNode _m : ms) { _m.desc = e.getValue(); }
		 * 
		 * count1 += (ms.size() + 1);
		 * 
		 * count1++; }
		 */

		for (Entry<MethodNode, String> e : map.entrySet()) {
			MethodNode mn = e.getKey();
			mn.desc = e.getValue();
			Set<MethodNode> agg = mmp.getData(e.getKey()).getAggregates();
			for (MethodNode m : agg) {
				m.desc = e.getValue();
			}

			changed.add(mn);
			changed.addAll(agg);
		}

		// TODO: shifting the local variable and parameter indices
		// after the removed opaque index would probably be a good idea
		// so that we don't have a floating local.
		// 
		// FIXME:  > would this need to follow double and long local var
		//     alignment rules so that they are on 2 byte boundaries?
		for (ClassNode cn : tree) {
			for (MethodNode m : cn.methods) {
				for (AbstractInsnNode ain : m.instructions.toArray()) {
					if (ain instanceof MethodInsnNode) {
						MethodInsnNode min = (MethodInsnNode) ain;
						MethodNode oldm = findMethod(min, tree, cache);

						if (oldm == null && min.owner.lastIndexOf('/') == -1) {
							// System.out.printf("%s is null (%s).%n",
							// min.key(), m.key());
							nulls++;
						} else if (oldm != null) {
							if (!min.desc.equals(oldm.desc)) {
								// TODO: follow ast and validate that the last node before
								// the virtual call is indeed a constantnode. if it isn't
								// then we can resort to a pop but otherwise there is 
								// something wrong.
								// the pop expression popper could probably weed this out but
								// it could also pop the constant within a proper calculation
								// and end up messing up the flow, so this needs to be done ASAP.
								// EDIT: fixed, thanks ImFrizzy.
								AbstractInsnNode node = min.getPrevious();
								if (node.opcode() == Opcodes.ICONST_M1 || node.opcode() == Opcodes.ICONST_0
										|| node.opcode() == Opcodes.ICONST_1 || node.opcode() == Opcodes.ICONST_2
										|| node.opcode() == Opcodes.ICONST_3 || node.opcode() == Opcodes.ICONST_4
										|| node.opcode() == Opcodes.ICONST_5 || node.opcode() == Opcodes.BIPUSH
										|| node.opcode() == Opcodes.SIPUSH || node.opcode() == Opcodes.LDC) {
									m.instructions.remove(node);
								} else {
									m.instructions.insertBefore(min, new InsnNode(Opcodes.POP));
								}
								
								callnames++;
							} else {
								unchanged++;
							}
							min.desc = oldm.desc;
						}
					}
				}
			}
		}
	}

	/**
	 * Resolves a method call to the method it is calling. If class A extends
	 * class B, then calling A.method() when the method is actually defined in B
	 * will be indirectly calling B.method(). In this case, there will be no
	 * method in A and so we have to work backwards through the class hierarchy
	 * to find it.
	 * 
	 * @param min
	 * @param tree
	 * @param cache
	 * @return
	 */
	private MethodNode findMethod(MethodInsnNode min, ClassTree tree, MethodCache cache) {
		MethodNode oldm = cache.get(min.owner, min.name, min.desc);
		if (oldm != null)
			return oldm;

		ClassNode cn = tree.getClass(min.owner);

		if (cn == null) {
			return null;
		}

		for (ClassNode sup : tree.getSupers(cn)) {
			oldm = cache.get(sup.name, min.name, min.desc);
			if (oldm != null)
				return oldm;
		}

		return null;
	}

	/**
	 * Removes the last parameter in a methods descriptor (which must be a byte,
	 * short or int (B, S, I).
	 * 
	 * @param desc
	 * @return
	 */
	private String newDesc(String desc) {
		/*
		 * Since this method is only called for methods which have a valid dummy
		 * parameter type (I, B, S) and their length is 1, we take 1 away before
		 * the ')'.
		 * 
		 * 
		 * ([BIII)V ^ dummy parameter.
		 * 
		 * sub: ([BII aft: )V
		 * 
		 * final: ([BII)V
		 */

		int index = desc.indexOf(")");
		String sub = desc.substring(0, index - 1);
		String aft = desc.substring(index);
		return sub + aft;
	}

	/**
	 * Counts the amount of times that the targetVar is used and returns a
	 * boolean value depending on whether it was used or not.
	 * 
	 * @param m
	 * @param targetVar
	 * @return
	 */
	private boolean isUnused(MethodNode m, int targetVar) {
		int count = 0;
		for (AbstractInsnNode ain : m.instructions.toArray()) {
			if (ain instanceof VarInsnNode) {
				VarInsnNode vin = (VarInsnNode) ain;
				if (vin.var == targetVar) {
					count++;
				}
			} else if (ain instanceof IincInsnNode) {
				IincInsnNode inc = (IincInsnNode) ain;
				if (inc.var == targetVar) {
					count++;
				}
			}
		}

		return count == 0;
	}
}