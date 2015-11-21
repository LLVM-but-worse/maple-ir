package org.objectweb.asm.commons.cfg.tree.util;

import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.DCONST_1;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.I2L;
import static org.objectweb.asm.Opcodes.I2S;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.ICONST_M1;
import static org.objectweb.asm.Opcodes.INVOKEDYNAMIC;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.LXOR;
import static org.objectweb.asm.Opcodes.MONITOREXIT;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.cfg.Block;
import org.objectweb.asm.commons.cfg.tree.NodeTree;
import org.objectweb.asm.commons.cfg.tree.node.AbstractNode;
import org.objectweb.asm.commons.cfg.tree.node.ArithmeticNode;
import org.objectweb.asm.commons.cfg.tree.node.ConstantNode;
import org.objectweb.asm.commons.cfg.tree.node.ConversionNode;
import org.objectweb.asm.commons.cfg.tree.node.FieldMemberNode;
import org.objectweb.asm.commons.cfg.tree.node.IincNode;
import org.objectweb.asm.commons.cfg.tree.node.JumpNode;
import org.objectweb.asm.commons.cfg.tree.node.MethodMemberNode;
import org.objectweb.asm.commons.cfg.tree.node.NumberNode;
import org.objectweb.asm.commons.cfg.tree.node.TypeNode;
import org.objectweb.asm.commons.cfg.tree.node.VariableNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * @author Tyler Sedlar
 */
public class TreeBuilder {

    public static final int[] CDS, PDS;

    static {
    	CDS = new int[]{
    			0/*      NOP      */, 0/*  ACONST_NULL  */, 0/*   ICONST_M1   */, 0/*    ICONST_0   */, 0/*    ICONST_1   */, 
    			0/*    ICONST_2   */, 0/*    ICONST_3   */, 0/*    ICONST_4   */, 0/*    ICONST_5   */, 0/*    LCONST_0   */, 
    			0/*    LCONST_1   */, 0/*    FCONST_0   */, 0/*    FCONST_1   */, 0/*    FCONST_2   */, 0/*    DCONST_0   */, 
    			0/*    DCONST_1   */, 0/*     BIPUSH    */, 0/*     SIPUSH    */, 0/*      LDC      */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     ILOAD     */, 0/*     LLOAD     */, 0/*     FLOAD     */, 0/*     DLOAD     */, 
    			0/*     ALOAD     */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 2/*     IALOAD    */, 2/*     LALOAD    */, 2/*     FALOAD    */, 2/*     DALOAD    */, 
    			2/*     AALOAD    */, 2/*     BALOAD    */, 2/*     CALOAD    */, 2/*     SALOAD    */, 1/*     ISTORE    */, 
    			2/*     LSTORE    */, 1/*     FSTORE    */, 2/*     DSTORE    */, 1/*     ASTORE    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 3/*    IASTORE    */, 
    			4/*    LASTORE    */, 3/*    FASTORE    */, 4/*    DASTORE    */, 3/*    AASTORE    */, 3/*    BASTORE    */, 
    			3/*    CASTORE    */, 3/*    SASTORE    */, 1/*      POP      */, 2/*      POP2     */, 1/*      DUP      */, 
    			2/*     DUP_X1    */, 3/*     DUP_X2    */, 2/*      DUP2     */, 3/*    DUP2_X1    */, 4/*    DUP2_X2    */, 
    			2/*      SWAP     */, 2/*      IADD     */, 4/*      LADD     */, 2/*      FADD     */, 4/*      DADD     */, 
    			2/*      ISUB     */, 4/*      LSUB     */, 2/*      FSUB     */, 4/*      DSUB     */, 2/*      IMUL     */, 
    			4/*      LMUL     */, 2/*      FMUL     */, 4/*      DMUL     */, 2/*      IDIV     */, 4/*      LDIV     */, 
    			2/*      FDIV     */, 4/*      DDIV     */, 2/*      IREM     */, 4/*      LREM     */, 2/*      FREM     */, 
    			4/*      DREM     */, 1/*      INEG     */, 2/*      LNEG     */, 1/*      FNEG     */, 2/*      DNEG     */, 
    			2/*      ISHL     */, 3/*      LSHL     */, 2/*      ISHR     */, 3/*      LSHR     */, 2/*     IUSHR     */, 
    			3/*     LUSHR     */, 2/*      IAND     */, 4/*      LAND     */, 2/*      IOR      */, 4/*      LOR      */, 
    			2/*      IXOR     */, 4/*      LXOR     */, 0/*      IINC     */, 1/*      I2L      */, 1/*      I2F      */, 
    			1/*      I2D      */, 2/*      L2I      */, 2/*      L2F      */, 2/*      L2D      */, 1/*      F2I      */, 
    			1/*      F2L      */, 1/*      F2D      */, 2/*      D2I      */, 2/*      D2L      */, 2/*      D2F      */, 
    			1/*      I2B      */, 1/*      I2C      */, 1/*      I2S      */, 4/*      LCMP     */, 2/*     FCMPL     */, 
    			2/*     FCMPG     */, 4/*     DCMPL     */, 4/*     DCMPG     */, 1/*      IFEQ     */, 1/*      IFNE     */, 
    			1/*      IFLT     */, 1/*      IFGE     */, 1/*      IFGT     */, 1/*      IFLE     */, 2/*   IF_ICMPEQ   */, 
    			2/*   IF_ICMPNE   */, 2/*   IF_ICMPLT   */, 2/*   IF_ICMPGE   */, 2/*   IF_ICMPGT   */, 2/*   IF_ICMPLE   */, 
    			2/*   IF_ACMPEQ   */, 2/*   IF_ACMPNE   */, 0/*      GOTO     */, 0/*      JSR      */, 0/*      RET      */, 
    			1/*  TABLESWITCH  */, 1/*  LOOKUPSWITCH */, 1/*    IRETURN    */, 2/*    LRETURN    */, 1/*    FRETURN    */, 
    			2/*    DRETURN    */, 1/*    ARETURN    */, 0/*     RETURN    */, 0/*   GETSTATIC   */, 0/*   PUTSTATIC   */, 
    			0/*    GETFIELD   */, 0/*    PUTFIELD   */, 0/* INVOKEVIRTUAL */, 0/* INVOKESPECIAL */, 0/*  INVOKESTATIC */, 
    			0/*INVOKEINTERFACE*/, 0/* INVOKEDYNAMIC */, 0/*      NEW      */, 1/*    NEWARRAY   */, 1/*   ANEWARRAY   */, 
    			1/*  ARRAYLENGTH  */, 1/*     ATHROW    */, 1/*   CHECKCAST   */, 1/*   INSTANCEOF  */, 1/*  MONITORENTER */, 
    			1/*  MONITOREXIT  */, 0/*     UNUSED    */, 0/* MULTIANEWARRAY*/, 1/*     IFNULL    */, 1/*   IFNONNULL   */, };
    		PDS = new int[]{
    			0/*      NOP      */, 1/*  ACONST_NULL  */, 1/*   ICONST_M1   */, 1/*    ICONST_0   */, 1/*    ICONST_1   */, 
    			1/*    ICONST_2   */, 1/*    ICONST_3   */, 1/*    ICONST_4   */, 1/*    ICONST_5   */, 2/*    LCONST_0   */, 
    			2/*    LCONST_1   */, 1/*    FCONST_0   */, 1/*    FCONST_1   */, 1/*    FCONST_2   */, 2/*    DCONST_0   */, 
    			2/*    DCONST_1   */, 1/*     BIPUSH    */, 1/*     SIPUSH    */, 1/*      LDC      */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 1/*     ILOAD     */, 2/*     LLOAD     */, 1/*     FLOAD     */, 2/*     DLOAD     */, 
    			1/*     ALOAD     */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 1/*     IALOAD    */, 2/*     LALOAD    */, 1/*     FALOAD    */, 2/*     DALOAD    */, 
    			1/*     AALOAD    */, 1/*     BALOAD    */, 1/*     CALOAD    */, 1/*     SALOAD    */, 0/*     ISTORE    */, 
    			0/*     LSTORE    */, 0/*     FSTORE    */, 0/*     DSTORE    */, 0/*     ASTORE    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 
    			0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*     UNUSED    */, 0/*    IASTORE    */, 
    			0/*    LASTORE    */, 0/*    FASTORE    */, 0/*    DASTORE    */, 0/*    AASTORE    */, 0/*    BASTORE    */, 
    			0/*    CASTORE    */, 0/*    SASTORE    */, 0/*      POP      */, 0/*      POP2     */, 2/*      DUP      */, 
    			3/*     DUP_X1    */, 4/*     DUP_X2    */, 4/*      DUP2     */, 5/*    DUP2_X1    */, 6/*    DUP2_X2    */, 
    			2/*      SWAP     */, 1/*      IADD     */, 2/*      LADD     */, 1/*      FADD     */, 2/*      DADD     */, 
    			1/*      ISUB     */, 2/*      LSUB     */, 1/*      FSUB     */, 2/*      DSUB     */, 1/*      IMUL     */, 
    			2/*      LMUL     */, 1/*      FMUL     */, 2/*      DMUL     */, 1/*      IDIV     */, 2/*      LDIV     */, 
    			1/*      FDIV     */, 2/*      DDIV     */, 1/*      IREM     */, 2/*      LREM     */, 1/*      FREM     */, 
    			2/*      DREM     */, 1/*      INEG     */, 2/*      LNEG     */, 1/*      FNEG     */, 2/*      DNEG     */, 
    			1/*      ISHL     */, 2/*      LSHL     */, 1/*      ISHR     */, 2/*      LSHR     */, 1/*     IUSHR     */, 
    			2/*     LUSHR     */, 1/*      IAND     */, 2/*      LAND     */, 1/*      IOR      */, 2/*      LOR      */, 
    			1/*      IXOR     */, 2/*      LXOR     */, 0/*      IINC     */, 2/*      I2L      */, 1/*      I2F      */, 
    			2/*      I2D      */, 1/*      L2I      */, 1/*      L2F      */, 2/*      L2D      */, 1/*      F2I      */, 
    			2/*      F2L      */, 2/*      F2D      */, 1/*      D2I      */, 2/*      D2L      */, 1/*      D2F      */, 
    			1/*      I2B      */, 1/*      I2C      */, 1/*      I2S      */, 1/*      LCMP     */, 1/*     FCMPL     */, 
    			1/*     FCMPG     */, 1/*     DCMPL     */, 1/*     DCMPG     */, 0/*      IFEQ     */, 0/*      IFNE     */, 
    			0/*      IFLT     */, 0/*      IFGE     */, 0/*      IFGT     */, 0/*      IFLE     */, 0/*   IF_ICMPEQ   */, 
    			0/*   IF_ICMPNE   */, 0/*   IF_ICMPLT   */, 0/*   IF_ICMPGE   */, 0/*   IF_ICMPGT   */, 0/*   IF_ICMPLE   */, 
    			0/*   IF_ACMPEQ   */, 0/*   IF_ACMPNE   */, 0/*      GOTO     */, 1/*      JSR      */, 0/*      RET      */, 
    			0/*  TABLESWITCH  */, 0/*  LOOKUPSWITCH */, 0/*    IRETURN    */, 0/*    LRETURN    */, 0/*    FRETURN    */, 
    			0/*    DRETURN    */, 0/*    ARETURN    */, 0/*     RETURN    */, 0/*   GETSTATIC   */, 0/*   PUTSTATIC   */, 
    			0/*    GETFIELD   */, 0/*    PUTFIELD   */, 0/* INVOKEVIRTUAL */, 0/* INVOKESPECIAL */, 0/*  INVOKESTATIC */, 
    			0/*INVOKEINTERFACE*/, 0/* INVOKEDYNAMIC */, 1/*      NEW      */, 1/*    NEWARRAY   */, 1/*   ANEWARRAY   */, 
    			1/*  ARRAYLENGTH  */, 1/*     ATHROW    */, 1/*   CHECKCAST   */, 1/*   INSTANCEOF  */, 0/*  MONITORENTER */, 
    			0/*  MONITOREXIT  */, 0/*     UNUSED    */, 0/* MULTIANEWARRAY*/, 0/*     IFNULL    */, 0/*   IFNONNULL   */, };
    }

//    public static void main(String[] args) {
//    	System.out.println(CDS.length);
//    	System.out.println(Printer.OPCODES.length);
//    	
//    	int max = 0;
//    	for(int i=0; i < Printer.OPCODES.length; i++) {
//    		max = Math.max(Printer.OPCODES[i].length(), max);
//    	}
//    	
//    	System.out.print("CDS = new int[]{");
//		for(int i=0; i < CDS.length; i++) {
//			if((i % 5) == 0) {
//				System.out.print("\n\t");
//			}
//			
//			String str = Printer.OPCODES[i];
//			if(str.length() == 0) {
//				str = "UNUSED";
//			}
//			System.out.print(CDS[i] + "/*" + fill(str, max) + "*/, ");
//		}
//		System.out.println("};");
//		
//    	System.out.print("PDS = new int[]{");
//		for(int i=0; i < PDS.length; i++) {
//			if((i % 5) == 0) {
//				System.out.print("\n\t");
//			}
//			String str = Printer.OPCODES[i];
//			if(str.length() == 0) {
//				str = "UNUSED";
//			}
//			System.out.print(PDS[i] + "/*" + fill(str, max) + "*/, ");
//		}
//		System.out.println("};");
//	}
//    static String fill(String str, int len) {
//    	int l = len - str.length();
//    	StringBuilder sb = new StringBuilder(str);
//    	for(int i=0; i < l; i++) {
//    		if((i % 2) == 0) {
//    			sb.insert(0, ' ');
//    		} else {
//    			sb.append(' ');
//    		}
//    	}
//    	return sb.toString();
//    }

    public static TreeSize getTreeSize(AbstractInsnNode ain) {
        int consumes = 0, produces = 0;
        if (ain instanceof InsnNode || ain instanceof IntInsnNode || ain instanceof VarInsnNode ||
                ain instanceof JumpInsnNode || ain instanceof TableSwitchInsnNode ||
                ain instanceof LookupSwitchInsnNode) {
            consumes = CDS[ain.opcode()];
            produces = PDS[ain.opcode()];
        } else if (ain instanceof FieldInsnNode) {
            FieldInsnNode fin = (FieldInsnNode) ain;
            char type = fin.desc.charAt(0);
            switch (fin.opcode()) {
                case GETFIELD: {
                	// requires object
                    // if double or long, produces 2wide else 1wide
                    consumes = 1;
                    produces = type == 'D' || type == 'J' ? 2 : 1;
                    break;
                }
                case GETSTATIC: {
                	// doesn't require object
                    // if double or long, produces 2wide else 1wide
                    consumes = 0;
                    produces = type == 'D' || type == 'J' ? 2 : 1;
                    break;
                }
                case PUTFIELD: {
                	// if double or long, consumes object and 2wide else object and 1wide
                	// doesn't produce anything
                    consumes = type == 'D' || type == 'J' ? 3 : 2;
                    produces = 0;
                    break;
                }
                case PUTSTATIC: {
                	/// if double or long, consumes 2wide else 1wide
                	// doesn't produce anything
                    consumes = type == 'D' || type == 'J' ? 2 : 1;
                    produces = 0;
                    break;
                }
                default: {
                	throw new IllegalArgumentException(ain.toString());
                    // consumes = 0;
                    // produces = 0;
                    // break;
                }
            }
        } else if (ain instanceof MethodInsnNode) {
            MethodInsnNode min = (MethodInsnNode) ain;
            int as = Type.getArgumentsAndReturnSizes(min.desc);
            consumes = (as >> 2) - (min.opcode() == INVOKEDYNAMIC || min.opcode() == INVOKESTATIC ? 1 : 0);
            produces = as & 0x03;
        } else if (ain instanceof LdcInsnNode) {
            Object cst = ((LdcInsnNode) ain).cst;
            produces = cst instanceof Double || cst instanceof Long ? 2 : 1;
        } else if (ain instanceof MultiANewArrayInsnNode) {
            consumes = ((MultiANewArrayInsnNode) ain).dims;
            produces = 1;
        }
        return new TreeSize(consumes, produces);
    }

    private static AbstractNode createNode(AbstractInsnNode ain, NodeTree tree, TreeSize size) {
        int opcode = ain.opcode();
        if (ain instanceof IntInsnNode) {
            return new NumberNode(tree, ain, size.collapsing, size.producing);
        } else if (ain instanceof VarInsnNode) {
            return new VariableNode(tree, ain, size.collapsing, size.producing);
        } else if (ain instanceof JumpInsnNode) {
            return new JumpNode(tree, (JumpInsnNode) ain, size.collapsing, size.producing);
        } else if (ain instanceof FieldInsnNode) {
            return new FieldMemberNode(tree, ain, size.collapsing, size.producing);
        } else if (ain instanceof MethodInsnNode) {
            return new MethodMemberNode(tree, ain, size.collapsing, size.producing);
        } else if (ain instanceof LdcInsnNode) {
            Object cst = ((LdcInsnNode) ain).cst;
            if (cst instanceof Number) {
                return new NumberNode(tree, ain, size.collapsing, size.producing);
            } else {
                return new ConstantNode(tree, ain, size.collapsing, size.producing);
            }
        } else if (ain instanceof IincInsnNode) {
            return new IincNode(tree, ain, size.collapsing, size.producing);
        } else if (ain instanceof TypeInsnNode) {
            return new TypeNode(tree, ain, size.collapsing, size.producing);
        } else {
            if (opcode >= ICONST_M1 && opcode <= DCONST_1) {
                return new NumberNode(tree, ain, size.collapsing, size.producing);
            } else if (opcode >= I2L && opcode <= I2S) {
                return new ConversionNode(tree, ain, size.collapsing, size.producing);
            } else if (opcode >= IADD && opcode <= LXOR) {
                return new ArithmeticNode(tree, ain, size.collapsing, size.producing);
            } else {
                return new AbstractNode(tree, ain, size.collapsing, size.producing);
            }
        }
    }

    private int treeIndex = -1;

    private AbstractNode iterate(List<AbstractNode> nodes) {
        if (treeIndex < 0) {
            return null;
        }
        AbstractNode node = nodes.get(treeIndex--);
        if (node.collapsed == 0) {
            return node;
        }
        int c = node.collapsed;
        while (c != 0) {
            AbstractNode n = iterate(nodes);
            if (n == null) {
                break;
            }
            int op = n.opcode();
            if (op == MONITOREXIT && node.opcode() == ATHROW)
                n.producing = 1;
            node.addFirst(n);
            int cr = c - n.producing;
            if (cr < 0) {
                node.producing += -cr;
                n.producing = 0;
                break;
            }
            c -= n.producing;
            n.producing = 0;
        }
        return node;
    }

    public long create = 0;
    public long iterate = 0;

    public NodeTree build(MethodNode mn) {
        NodeTree tree = new NodeTree(mn);
        List<AbstractNode> nodes = new ArrayList<>();
        long start = System.nanoTime();
        for (AbstractInsnNode ain : mn.instructions.toArray()) {
            if(ain.opcode() != -1) {
            	nodes.add(createNode(ain, tree, getTreeSize(ain)));
            }
        }
        long end = System.nanoTime();
        create += (end - start);
        treeIndex = nodes.size() - 1;
        AbstractNode node;
        start = System.nanoTime();
        while ((node = iterate(nodes)) != null)
            tree.addFirst(node);
        end = System.nanoTime();
        iterate += (end - start);
        return tree;
    }

    public NodeTree build(Block block) {
        NodeTree tree = new NodeTree(block);
        List<AbstractNode> nodes = new ArrayList<>();
        long start = System.nanoTime();
        for (AbstractInsnNode ain : block.instructions)
            if(ain.opcode() != -1) {
            	nodes.add(createNode(ain, tree, getTreeSize(ain)));
            }
        long end = System.nanoTime();
        create += (end - start);
        treeIndex = nodes.size() - 1;
        AbstractNode node;
        start = System.nanoTime();
        while ((node = iterate(nodes)) != null)
            tree.addFirst(node);
        end = System.nanoTime();
        iterate += (end - start);
        return tree;
    }
}