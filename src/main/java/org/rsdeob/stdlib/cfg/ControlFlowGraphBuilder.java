package org.rsdeob.stdlib.cfg;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.tree.AbstractInsnNode.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.rsdeob.stdlib.cfg.FlowEdge.TryCatchEdge;
import org.rsdeob.stdlib.cfg.expr.ArithmeticExpression;
import org.rsdeob.stdlib.cfg.expr.ArithmeticExpression.Operator;
import org.rsdeob.stdlib.cfg.expr.ArrayLengthExpression;
import org.rsdeob.stdlib.cfg.expr.ArrayLoadExpression;
import org.rsdeob.stdlib.cfg.expr.CastExpression;
import org.rsdeob.stdlib.cfg.expr.CaughtExceptionExpression;
import org.rsdeob.stdlib.cfg.expr.ComparisonExpression;
import org.rsdeob.stdlib.cfg.expr.ComparisonExpression.ValueComparisonType;
import org.rsdeob.stdlib.cfg.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.expr.Expression;
import org.rsdeob.stdlib.cfg.expr.FieldLoadExpression;
import org.rsdeob.stdlib.cfg.expr.InstanceofExpression;
import org.rsdeob.stdlib.cfg.expr.InvocationExpression;
import org.rsdeob.stdlib.cfg.expr.NegationExpression;
import org.rsdeob.stdlib.cfg.expr.NewArrayExpression;
import org.rsdeob.stdlib.cfg.expr.StackLoadExpression;
import org.rsdeob.stdlib.cfg.expr.UninitialisedObjectExpression;
import org.rsdeob.stdlib.cfg.stat.ArrayStoreStatement;
import org.rsdeob.stdlib.cfg.stat.BlockHeaderStatement;
import org.rsdeob.stdlib.cfg.stat.ConditionalJumpStatement;
import org.rsdeob.stdlib.cfg.stat.ConditionalJumpStatement.ComparisonType;
import org.rsdeob.stdlib.cfg.stat.FieldStoreStatement;
import org.rsdeob.stdlib.cfg.stat.MonitorStatement;
import org.rsdeob.stdlib.cfg.stat.MonitorStatement.MonitorMode;
import org.rsdeob.stdlib.cfg.stat.PopStatement;
import org.rsdeob.stdlib.cfg.stat.ReturnStatement;
import org.rsdeob.stdlib.cfg.stat.StackDumpStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.stat.SwitchStatement;
import org.rsdeob.stdlib.cfg.stat.ThrowStatement;
import org.rsdeob.stdlib.cfg.stat.UnconditionalJumpStatement;
import org.rsdeob.stdlib.cfg.util.ExpressionStack;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.cfg.util.LabelHelper;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.cfg.util.TypeUtils.ArrayType;

public class ControlFlowGraphBuilder {

//	private static final Pass[] ROOT_PASSES = new Pass[] {new VariableMergerPass(), new UnusedVariablesPass(), new NewObjectPass()};
	
	private final MethodNode method;
	private final ControlFlowGraph graph;
	
	private ControlFlowGraphBuilder(MethodNode method) {
		this.method = method;
		graph = new ControlFlowGraph(method);
	}
	
	private void prepareCode() {
		InsnList insns = method.instructions;
		AbstractInsnNode first = insns.getFirst();
		if(!(first instanceof LabelNode)) {
			LabelNode nFirst = new LabelNode();
			insns.insert(first, nFirst);
			first = nFirst;
		}
	}
	
	private void createBlocks() {
		BasicBlock current = null;
		
		for(AbstractInsnNode ain : method.instructions.toArray()) {
			if(ain instanceof FrameNode) {
				method.instructions.remove(ain);
			}
		}
		
		int created = 0;
		Map<AbstractInsnNode, LabelNode> newLabels = new HashMap<AbstractInsnNode, LabelNode>();
		AbstractInsnNode[] ains =  method.instructions.toArray();
		
		for(int i=0; i < ains.length; i++) {
			AbstractInsnNode ain = ains[i];
			boolean isLabel;
			if((isLabel = ain instanceof LabelNode) || ain instanceof JumpInsnNode) {
				
				LabelNode label = null;
				if(isLabel) {
					label = (LabelNode) ain;
				} else {
					label = new LabelNode();
					AbstractInsnNode prev = ain.getPrevious();
					if(prev == null) {
						throw new IllegalStateException("startblock double jump");
					} else {
						newLabels.put(prev, label);
					}
					
					current.addInsn(ain);
				}
				
				current = new BasicBlock(graph, LabelHelper.createBlockName(++created), label);
				graph.addVertex(current);
				
				if(created == 1) {
					graph.setEntry(current);
				}
			} else {
				current.addInsn(ain);
			}
		}
		
		for(Map.Entry<AbstractInsnNode, LabelNode> e : newLabels.entrySet()) {
			method.instructions.insert(e.getKey(), e.getValue());
		}
	}
	
	private void linkBlocks() {
		List<BasicBlock> blocks = new ArrayList<>(graph.blocks());
		
		for(int index=0; index < blocks.size(); index++) {
			BasicBlock block = blocks.get(index);
			AbstractInsnNode last = block.last();
			if(last == null) {
				if((index + 1) < blocks.size()) {
					BasicBlock next = blocks.get(index + 1);
					if(next != null) {
						graph.addEdge(block, new FlowEdge.ImmediateEdge(block, next));
					} else {
						throw new UnsupportedOperationException("edge over flow for block " + block.getId());
					}
				}
				continue;
			}
			
			switch(last.type()) {
				case AbstractInsnNode.JUMP_INSN: {
					JumpInsnNode jin = (JumpInsnNode)last;
					BasicBlock target = graph.getBlock(jin.label);
					if(jin.opcode() == GOTO || jin.opcode() == JSR) {
						graph.addEdge(block, new FlowEdge.UnconditionalJumpEdge(block, target, jin));
					} else {
						graph.addEdge(block, new FlowEdge.ConditionalJumpEdge(block, target, jin));
						// add fall through as successor
						BasicBlock next = blocks.get(index + 1);
						if(next != null) {
							graph.addEdge(block, new FlowEdge.ImmediateEdge(block, next));
						} else {
							throw new UnsupportedOperationException("edge over flow for block " + block.getId());
						}
					}
					break;
				}
				case AbstractInsnNode.LOOKUPSWITCH_INSN: {
					LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) last;
					for(int i=0; i < lsin.keys.size(); i++) {
						graph.addEdge(block, new FlowEdge.SwitchEdge(block,graph.getBlock(lsin.labels.get(i)), lsin, lsin.keys.get(i)));
					}
					graph.addEdge(block, new FlowEdge.DefaultSwitchEdge(block,graph.getBlock(lsin.dflt), lsin));
					break;
				}
				case AbstractInsnNode.TABLESWITCH_INSN: {
					TableSwitchInsnNode tsin = (TableSwitchInsnNode) last;
					for(int i=tsin.min; i <= tsin.max; i++) {
						graph.addEdge(block, new FlowEdge.SwitchEdge(block,graph.getBlock(tsin.labels.get(i - tsin.min)), tsin, i));
					}
					graph.addEdge(block, new FlowEdge.DefaultSwitchEdge(block,graph.getBlock(tsin.dflt), tsin));
					break;
				}
				default: {
					if(!GraphUtils.isExitOpcode(last.opcode())) {
						BasicBlock next = blocks.get(index + 1);
						if(next != null) {
							graph.addEdge(block, new FlowEdge.ImmediateEdge(block, next));
						} else {
							throw new UnsupportedOperationException("edge over flow for block " + block.getId());
						}
					}
					break;
				}
			}
		}
		
		Map<String, ExceptionRange> ranges = new HashMap<>();
		for(TryCatchBlockNode tc : method.tryCatchBlocks) {
			int start = LabelHelper.numeric(graph.getBlock(tc.start).getId());
			int end = LabelHelper.numeric(graph.getBlock(tc.end).getId()) - 1;
			
			List<BasicBlock> range = GraphUtils.range(blocks, start, end);
			BasicBlock handler = graph.getBlock(tc.handler);
			String key = String.format("%s:%s:%s", LabelHelper.createBlockName(start), LabelHelper.createBlockName(end), handler.getId());
			
			ExceptionRange erange;
			if(ranges.containsKey(key)) {
				erange = ranges.get(key);
			} else {
				erange = new ExceptionRange(tc);
				erange.setHandler(handler);
				erange.addBlocks(range);
				ranges.put(key, erange);
				
				if(!erange.isContiguous()) {
					System.out.println(erange + " not contiguous");
				}
				graph.addRange(erange);
			}
			
			erange.addType(tc.type);
			
			ListIterator<BasicBlock> lit = range.listIterator();
			while(lit.hasNext()) {
				BasicBlock block = lit.next();
				graph.addEdge(block, new FlowEdge.TryCatchEdge(block, erange));
			}
		}
		
		for(BasicBlock b : new ArrayList<BasicBlock>(blocks)) {
			if(b.getPredecessors().size() == 0 && b.size() == 0) {
				graph.removeVertex(b);
			}
		}
	}
	
	class ExpressionBuilder {
		MethodNode m;
		Set<BasicBlock> updatedStacks;
		Set<BasicBlock> analysedBlocks;
		LinkedList<BasicBlock> queue;
		int stackBase;
		int maxStack;
		RootStatement root;
//		VarVersionsMap variables;
		
		ExpressionBuilder(MethodNode m) {
			this.m = m;
			updatedStacks = new HashSet<>();
			analysedBlocks = new HashSet<>();
			queue = new LinkedList<>();
			stackBase = m.maxLocals;
			maxStack = m.maxStack;
//			variables = new VarVersionsMap(graph);
			root = new RootStatement(m/*, variables*/);
			
			queueEntryBlocks();
		}
		
		RootStatement buildRoot() {
			for(BasicBlock b : graph.blocks()) {
				BlockHeaderStatement bstmt = new BlockHeaderStatement(b);
				root.write(bstmt);
				root.getBlockStatements().put(b, bstmt);
				for (Statement n : b.getStatements()) {
					root.write(n);
				}
			}
			return root;
		}
		
//		void analyseRoot(RootStatement root) {
//			int its = 0;
//			while (its < 32) {
//				int changes = 0;
//				for (Pass p : ROOT_PASSES) {
//					changes += p.run(root);
//				}
//				if (changes <= 0) {
//					break;
//				}
//				its++;
//			}
//		}
		
		void queueEntryBlocks() {
			// entry blocks by definition have a certain
			// input stack (empty or a jvm created exception
			// object), so they are assumed to be 'updated'
			// which means that they can't be changed, only
			// used as references for predecessor edges to
			// check whether they are coherent when merging
			// into them.
			
			BasicBlock entry = graph.getEntry();
			entry.setInputStack(new ExpressionStack(1024 * 8));
			queue.add(entry);
			updatedStacks.add(entry);
			
			for(TryCatchBlockNode tc : m.tryCatchBlocks) {
				LabelNode label = tc.handler;
				BasicBlock handler = graph.getBlock(label);
				ExpressionStack stack = new ExpressionStack(1024 * 8);
				stack.push(new CaughtExceptionExpression(tc.type));
				handler.setInputStack(stack);
				
				queue.addLast(handler);
				updatedStacks.add(handler);
			}
		}
		
		void createExpressions() {
			while(queue.size() > 0) {
				BasicBlock b = queue.removeFirst();
				if(!analysedBlocks.contains(b)) {
					analysedBlocks.add(b);

					ExpressionStack stack = process(b);
					
					// check merge exit stack with next input stack
					if(b.getImmediate() != null) {
						queue.addFirst(b.getImmediate());
					}

					createStackVariables(b, stack);
					for(FlowEdge _succ : b.getSuccessors()) {
						if(!(_succ instanceof TryCatchEdge)) {
							BasicBlock succ = _succ.dst;
							updateTargetStack(succ, stack);
						}
					}
				}
			}
		}
		
		void updateTargetStack(BasicBlock target, ExpressionStack stack) {
			// called just before a jump to a successor block may
			// happen. any operations, such as comparisons, that
			// happen before the jump are expected to have already
			// popped the left and right arguments from the stack before
			// checking the merge state.
			if(!updatedStacks.contains(target)) {
				// unfinalised block found.
				target.setInputStack(stack.copy());
				updatedStacks.add(target);

				queue.addLast(target);
			} else if(!canSucceed(target.getInputStack(), stack)) {
				// if the targets input stack is finalised and
				// the new stack cannot merge into it, then there 
				// is an error in the bytecode (verifier error).
				throw new IllegalStateException("Stack coherency mistmatch.");
			}
		}
		
		boolean canSucceed(ExpressionStack s, ExpressionStack succ) {
			if (s.size() != succ.size()) {
				return false;
			}
			ExpressionStack c0 = s.copy();
			ExpressionStack c1 = succ.copy();
			while (c0.size() > 0) {
				Expression expr1 = c0.pop1();
				Expression expr2 = c1.pop1();
				if (!(expr1 instanceof StackLoadExpression) || !(expr2 instanceof StackLoadExpression)) {
					return false;
				}
				if (((StackLoadExpression)expr1).getIndex() != ((StackLoadExpression)expr2).getIndex()) {
					return false;
				}
				if (!expr1.getType().getDescriptor().equals(expr2.getType().getDescriptor())) {
					return false;
				}
			}
			return true;
		}
		
		ExpressionStack process(BasicBlock b) {
			updatedStacks.add(b);
			
			ExpressionStack stack = b.getInputStack().copy();
			
			for(AbstractInsnNode ain : b.getInsns()) {
				int opcode = ain.opcode();
				if(opcode == -1) {
					if(ain instanceof LabelNode) {
						throw new IllegalStateException("Block should not contain label.");
					}
				} else {
//					System.out.println("========EXEC " + Printer.OPCODES[opcode]);
//					System.out.println(" Pre: " + stack);
					switch(ain.type()) {
						case INT_INSN: {
							IntInsnNode iin = (IntInsnNode) ain;
							if(opcode == BIPUSH || opcode == SIPUSH) {
								stack.push(new ConstantExpression(iin.operand));
							} else if(opcode == NEWARRAY) {
								Type type = TypeUtils.getPrimitiveArrayType(iin.operand);
								Expression[] bounds = new Expression[]{stack.pop1()};
								stack.push(new NewArrayExpression(bounds, type));
							}
							break;
						}
						case INSN: {
							// InsnNode in = (InsnNode) ain;
							if(opcode == ACONST_NULL) {
								stack.push(new ConstantExpression(null));
							} else if (opcode >= ICONST_M1 && opcode <= ICONST_5) {
								stack.push(new ConstantExpression((opcode - ICONST_M1) - 1));
							} else if (opcode == LCONST_0 || opcode == LCONST_1) {
								stack.push(new ConstantExpression(opcode == LCONST_0 ? 0L : 1L));
							} else if (opcode >= FCONST_0 && opcode <= FCONST_2) {
								stack.push(new ConstantExpression((float)(opcode - FCONST_0)));
							} else if (opcode == DCONST_0 || opcode == DCONST_1) {
								stack.push(new ConstantExpression(opcode == DCONST_0 ? 0D : 1D));
							} else if(opcode >= LCMP && opcode <= DCMPG) {
								// we don't need to update any target
								// stacks here because these instructions
								// only do comparisons.
								Expression right = stack.pop1();
								Expression left = stack.pop1();
								ValueComparisonType type = ValueComparisonType.resolve(opcode);
								ComparisonExpression expr = new ComparisonExpression(left, right, type);
								stack.push(expr);
							} else if(opcode >= IRETURN && opcode <= ARETURN) {
								if (stack.size() > 1) {
									createStackVariables(b, stack);
								}
								Expression expr = stack.pop1();
								b.getStatements().add(new ReturnStatement(Type.getReturnType(m.desc), expr));
							} else if(opcode == RETURN) {
//								createStackVariables(b, stack);
								b.getStatements().add(new ReturnStatement());
							} else if(opcode == ATHROW) {
								if (stack.size() > 1) {
									createStackVariables(b, stack);
								}
								Expression expr = stack.pop1();
								b.getStatements().add(new ThrowStatement(expr));
							} else if(opcode == MONITORENTER || opcode == MONITOREXIT) {
								if (stack.size() > 1) {
									createStackVariables(b, stack);
								}
								Expression expr = stack.pop1();
								MonitorMode mode = (opcode == MONITORENTER) ? MonitorMode.ENTER : MonitorMode.EXIT;
								b.getStatements().add(new MonitorStatement(expr, mode));
							} else if(opcode >= IADD && opcode <= DREM) {
								int index = (int)Math.floor((opcode - IADD) / 4) + Operator.ADD.ordinal();
								Operator op = Operator.values()[index];
								stack.push(new ArithmeticExpression(stack.pop1(), stack.pop1(), op));
							} else if(opcode >= INEG && opcode <= DNEG) {
								stack.push(new NegationExpression(stack.pop1()));
							} else if(opcode >= ISHL && opcode <= LUSHR) {
								int index = (int)Math.floor((opcode - ISHL) / 2) + Operator.SHL.ordinal();
								Operator op = Operator.values()[index];
								stack.push(new ArithmeticExpression(stack.pop1(), stack.pop1(), op));
							} else if(opcode == IAND || opcode == LAND) {
								stack.push(new ArithmeticExpression(stack.pop1(), stack.pop1(), Operator.AND));
							} else if(opcode == IOR || opcode == LOR) {
								stack.push(new ArithmeticExpression(stack.pop1(), stack.pop1(), Operator.OR));
							} else if(opcode == IXOR || opcode == LXOR) {
								stack.push(new ArithmeticExpression(stack.pop1(), stack.pop1(), Operator.XOR));
							} else if(opcode == ARRAYLENGTH) {
								stack.push(new ArrayLengthExpression(stack.pop1()));
							} else if(opcode >= IALOAD && opcode <= SALOAD) {
								Expression index = stack.pop1();
								Expression array = stack.pop1();
								stack.push(new ArrayLoadExpression(array, index, ArrayType.resolve(opcode)));
							} else if(opcode >= IASTORE && opcode <= SASTORE) {
								if (stack.size() > 3) {
									createStackVariables(b, stack);
								}
								
								Expression value = stack.pop1();
								Expression index = stack.pop1();
								Expression array = stack.pop1();	
								b.getStatements().add(new ArrayStoreStatement(array, index, value, ArrayType.resolve(opcode)));
							} else if(opcode >= I2L && opcode <= I2S) {
								stack.push(new CastExpression(stack.pop1(), TypeUtils.getCastType(opcode)));
							} else if(opcode == POP) {
								if(stack.size() > 1) {
									createStackVariables(b, stack);
								}
								
								Expression expr = stack.pop1();
								b.getStatements().add(new PopStatement(expr));
							} else if(opcode == POP2) {
								 if(stack.peek().getType().getSize() == 2) {
									 if(stack.size() > 1) {
										 createStackVariables(b, stack);
									 }
									 
									 Expression expr = stack.pop1();
									 b.getStatements().add(new PopStatement(expr));
								 } else {
									 if(stack.size() > 2) {
										 createStackVariables(b, stack);
									 }
									 
									 Expression expr1 = stack.pop1();
									 Expression expr2 = stack.pop1();
									 b.getStatements().add(new PopStatement(expr1));
									 b.getStatements().add(new PopStatement(expr2));
								 }
							} else if (opcode == DUP) {
								createStackVariables(b, stack);
								stack.push(stack.peek().copy());
							} else if (opcode == Opcodes.DUP2) {
								if (stack.peek(0).getType().getSize() == 1) {
									createStackVariables(b, stack);
									Expression expr2 = stack.pop1();
									Expression expr1 = stack.pop1();
									stack.push(expr1);
									stack.push(expr2);
									stack.push(expr1.copy());
									stack.push(expr2.copy());
								} else {
									createStackVariables(b, stack);
									stack.push(stack.peek().copy());
								}
							} else if (opcode == Opcodes.DUP_X1) {
//								createDupVars(stack, b, -2, -1);
								createStackVariables(b, stack, true);
								// stack = [x, y, ...]
								Expression expr2 = stack.pop1();
								Expression expr1 = stack.pop1();
								// stack = [x, y, copy(x), ...]
								stack.push(expr2.copy());
								stack.push(expr1);
								stack.push(expr2);
//								System.out.println("\ndup_x1 stack: " + stack);
							} else if (opcode == Opcodes.DUP_X2) {
//								if (stack.peek(1).getType().getSize() == 2) {
////									createDupVars(stack, b, -2, -1);
//									createStackVariables(b, stack);
//									// stack = [xx, yy, ...]
//									Expression expr2 = stack.pop();
//									Expression expr1 = stack.pop();
//									// stack = [xx, yy, xx, ...]
//									stack.push(expr2.copy());
//									stack.push(expr1);
//									stack.push(expr2);
//								} else {
//									createDupVars(stack, b, -3, -1);
								createStackVariables(b, stack, true);
								// stack = [x, y, z, ...]
								Expression expr2 = stack.pop1();
								Expression expr1 = stack.pop1();
								Expression expr0 = stack.pop1();
								// stack = [x, y, z, copy(x), ...]
								stack.push(expr2.copy());
								stack.push(expr1);
								stack.push(expr0);
								stack.push(expr2);

//								System.out.println("\ndup_x2(1) stack: " + stack);
//								}
							} else if (opcode == Opcodes.DUP2_X1) {
								if (stack.peek(0).getType().getSize() == 1) {
									createStackVariables(b, stack);
									Expression expr2 = stack.pop1();
									Expression expr1 = stack.pop1();
									Expression expr0 = stack.pop1();
									stack.push(expr1.copy());
									stack.push(expr2.copy());
									stack.push(expr0);
									stack.push(expr1);
									stack.push(expr2);
								} else {
									createStackVariables(b, stack);
									Expression expr2 = stack.pop1();
									Expression expr1 = stack.pop1();
									stack.push(expr2.copy());
									stack.push(expr1);
									stack.push(expr2);
								}
							} else if (opcode == Opcodes.DUP2_X2) {
								if (stack.peek(0).getType().getSize() == 2 && stack.peek(1).getType().getSize() == 2) {
									createStackVariables(b, stack);
									Expression expr2 = stack.pop1();
									Expression expr1 = stack.pop1();
									stack.push(expr2.copy());
									stack.push(expr1);
									stack.push(expr2);
								} else if (stack.peek(0).getType().getSize() == 2 && stack.peek(1).getType().getSize() == 1) {
									createStackVariables(b, stack);
									Expression expr2 = stack.pop1();
									Expression expr1 = stack.pop1();
									Expression expr0 = stack.pop1();
									stack.push(expr2.copy());
									stack.push(expr0);
									stack.push(expr1);
									stack.push(expr2);
								} else if (stack.peek(0).getType().getSize() == 1 && stack.peek(1).getType().getSize() == 2) {
									createStackVariables(b, stack);
									Expression expr2 = stack.pop1();
									Expression expr1 = stack.pop1();
									Expression expr0 = stack.pop1();
									stack.push(expr1.copy());
									stack.push(expr2.copy());
									stack.push(expr0);
									stack.push(expr1);
									stack.push(expr2);
								} else {
									createStackVariables(b, stack);
									Expression expr3 = stack.pop1();
									Expression expr2 = stack.pop1();
									Expression expr1 = stack.pop1();
									Expression expr0 = stack.pop1();
									stack.push(expr2.copy());
									stack.push(expr3.copy());
									stack.push(expr0);
									stack.push(expr1);
									stack.push(expr2);
									stack.push(expr3);
								}
							} else if (opcode == Opcodes.SWAP) {
								createStackVariables(b, stack);
								Expression expr2 = stack.pop1();
								Expression expr1 = stack.pop1();
								stack.push(expr2);
								stack.push(expr1);
							}
							break;
						}
						case TYPE_INSN: {
							TypeInsnNode tin = (TypeInsnNode) ain;
							if(opcode == CHECKCAST) {
								stack.push(new CastExpression(stack.pop1(), Type.getType("L" + tin.desc + ";")));
							} else if(opcode == INSTANCEOF) {
								stack.push(new InstanceofExpression(stack.pop1(), Type.getType("L" + tin.desc + ";")));
							} else if(opcode == NEW) {
								stack.push(new UninitialisedObjectExpression(Type.getType("L" + tin.desc + ";")));
							} else if(opcode == ANEWARRAY) {
								Type type = Type.getType("[L" + tin.desc + ";");
								Expression[] bounds = new Expression[]{stack.pop1()};
								stack.push(new NewArrayExpression(bounds, type));
							}
							break;
						}
						case MULTIANEWARRAY_INSN: {
							MultiANewArrayInsnNode main = (MultiANewArrayInsnNode) ain;
							Expression[] bounds = new Expression[main.dims];
							for (int i = main.dims - 1; i >= 0; i--)
								bounds[i] = stack.pop1();
							Type type = Type.getType(main.desc);
							stack.push(new NewArrayExpression(bounds, type));
							break;
						}
						case METHOD_INSN: {
							MethodInsnNode min = (MethodInsnNode) ain;
							if(opcode == INVOKEDYNAMIC) {
								throw new UnsupportedOperationException("INVOKEDYNAMIC");
							} else {
								Expression[] argumentExpressions = new Expression[Type.getArgumentTypes(min.desc).length + (opcode == INVOKESTATIC ? 0 : 1)];
								for (int i = argumentExpressions.length - 1; i >= 0; i--) {
									argumentExpressions[i] = stack.pop1();
								}
								InvocationExpression invocationExpression = new InvocationExpression(opcode, argumentExpressions, min.owner, min.name, min.desc);
//								System.out.println("\nCreated invocation: " + invocationExpression);
//								System.out.println("Using args: " + Arrays.toString(argumentExpressions));

								if(invocationExpression.getType() == Type.VOID_TYPE) {
									createStackVariables(b, stack);
									b.getStatements().add(new PopStatement(invocationExpression));
								} else {
									stack.push(invocationExpression);
								}
							}
							break;
						}
						case VAR_INSN: {
							VarInsnNode vin = (VarInsnNode) ain;
							if(opcode >= ILOAD && opcode <= ALOAD) {
								Type type = TypeUtils.getLoadType(opcode);
								StackLoadExpression expr = new StackLoadExpression(vin.var, type);
								stack.push(expr);
							} else if(opcode >= ISTORE && opcode <= ASTORE) {
								if (stack.size() > 1) {
									createStackVariables(b, stack);
								}
								Expression expr = stack.pop1();
//								System.out.println("Storing " + Printer.OPCODES[opcode] + " ," + expr + " into " + vin.var);
								Type type = TypeUtils.getStoreType(opcode);
								b.getStatements().add(new StackDumpStatement(expr, vin.var, type));
							}
							break;
						}
						case FIELD_INSN: {
							FieldInsnNode fin = (FieldInsnNode) ain;
							if(opcode == PUTFIELD) {
								if (stack.size() > 2) {
									createStackVariables(b, stack);
								}
								
								Expression value = stack.pop1();
								Expression instance = stack.pop1();
								b.getStatements().add(new FieldStoreStatement(instance, value, fin.owner, fin.name, fin.desc));
							} else if(opcode == PUTSTATIC) {
								if (stack.size() > 1) {
									createStackVariables(b, stack);
								}
								
								Expression value = stack.pop1();
								b.getStatements().add(new FieldStoreStatement(null, value, fin.owner, fin.name, fin.desc));
							} else {
								Expression instanceExpr = null;
								if(opcode == GETFIELD) {
									instanceExpr = stack.pop1();
								}
								
								FieldLoadExpression fExpr = new FieldLoadExpression(instanceExpr, fin.owner, fin.name, fin.desc);
								stack.push(fExpr);
							}
							break;
						}
						case IINC_INSN: {
							createStackVariables(b, stack);
							IincInsnNode iinc = (IincInsnNode) ain;
							int index = iinc.var;
							StackLoadExpression loadExpr = new StackLoadExpression(index, Type.INT_TYPE);
							ArithmeticExpression incExpr = new ArithmeticExpression(new ConstantExpression(iinc.incr), loadExpr, Operator.ADD);
							b.getStatements().add(new StackDumpStatement(incExpr, index, Type.INT_TYPE));
							break;
						}
						case LDC_INSN: {
							LdcInsnNode ldc = (LdcInsnNode) ain;
							stack.push(new ConstantExpression(ldc.cst));
							break;
						}
						case TABLESWITCH_INSN: {
							if (stack.size() > 1) {
								createStackVariables(b, stack);
							}
							TableSwitchInsnNode tsin = (TableSwitchInsnNode) ain;
							Expression expr = stack.pop1();
							
							LinkedHashMap<Integer, BasicBlock> targets = new LinkedHashMap<>();
							for(int i=tsin.min; i <= tsin.max; i++) {
								BasicBlock targ = graph.getBlock(tsin.labels.get(i - tsin.min));
								targets.put(i, targ);
								updateTargetStack(targ, stack);
							}
							
							BasicBlock dflt = graph.getBlock(tsin.dflt);
							updateTargetStack(dflt, stack);
							
							b.getStatements().add(new SwitchStatement(expr, targets, dflt));
							break;
						}
						case LOOKUPSWITCH_INSN: {
							if (stack.size() > 1) {
								createStackVariables(b, stack);
							}
							LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) ain;
							Expression expr = stack.pop1();

							LinkedHashMap<Integer, BasicBlock> targets = new LinkedHashMap<>();
							for(int i=0; i < lsin.keys.size(); i++) {
								int key = lsin.keys.get(i);
								BasicBlock targ = graph.getBlock(lsin.labels.get(i));
								targets.put(key, targ);
								updateTargetStack(targ, stack);
							}
							
							BasicBlock dflt = graph.getBlock(lsin.dflt);
							updateTargetStack(dflt, stack);
							
							b.getStatements().add(new SwitchStatement(expr, targets, dflt));
							break;
						}
						case JUMP_INSN: {
							JumpInsnNode jin = (JumpInsnNode) ain;
							BasicBlock target = graph.getBlock(jin.label);
							
							if(opcode == GOTO) {
								createStackVariables(b, stack);
								updateTargetStack(target, stack);
								b.getStatements().add(new UnconditionalJumpStatement(target));
							} else if(opcode == IFNULL || opcode == IFNONNULL) {
								if(stack.size() > 1) {
									createStackVariables(b, stack);
								}
								Expression left = stack.pop1();
								updateTargetStack(target, stack);
								
								ConstantExpression right = new ConstantExpression(null);
								ComparisonType type = (opcode == IFNULL ? ComparisonType.EQ : ComparisonType.NE);
								b.getStatements().add(new ConditionalJumpStatement(left, right, target, type));
							} else if(opcode >= IF_ICMPEQ && opcode <= IF_ACMPNE) {
								if(stack.size() > 2) {
									createStackVariables(b, stack);
								}
								Expression right = stack.pop1();
								Expression left = stack.pop1();
								updateTargetStack(target, stack);
								ComparisonType type = ComparisonType.getType(opcode);
								b.getStatements().add(new ConditionalJumpStatement(left, right, target, type));
							} else if(opcode >= IFEQ && opcode <= IFLE) {
								if(stack.size() > 1) {
									createStackVariables(b, stack);
								}
								Expression left = stack.pop1();
								updateTargetStack(target, stack);
								
								ConstantExpression right = new ConstantExpression(0);
								ComparisonType type = ComparisonType.getType(opcode);
								b.getStatements().add(new ConditionalJumpStatement(left, right, target, type));
							}
							break;
						}
					}

//					System.out.println("========POST=====: " + stack);
				}
			}
			
			return stack;
		}
		
//		void createDupVars(ExpressionStack stack, BasicBlock block, int offset, int copyoffset) {			
//			int base = stackBase;
//			LinkedList<Expression> stackExprs = new LinkedList<Expression>();
//			for (int i = -1; i >= offset; i--) {
//				Expression expr = stack.pop();
//				Type type = TypeUtils.findBaseType(expr.getType());
//				int index = base + (i + 1);
//				StackLoadExpression stackVar = new StackLoadExpression(index, type);
//				block.getStatements().add(new StackDumpStatement(stackVar, index, type));
//				stackExprs.add(0, stackVar);
//			}
//
//			Expression copyExpr = stackExprs.get(stackExprs.size() + copyoffset).copy();
//			Type type = TypeUtils.findBaseType(copyExpr.getType());
//			int index = base + offset;
//			StackLoadExpression var = new StackLoadExpression(index, type);
//			block.getStatements().add(new StackDumpStatement(var, index, type));
//			stackExprs.add(0, var);
//
//			for (Expression expr : stackExprs) {
//				stack.push(expr);
//			}
//			
//			createStackVariables(block, stack);
//		}
		
//		int nextIndex = 0;
//		
//		StackLoadExpression newStackLocal(int index, Type type) {
//			if (index >= nextIndex) {
//				nextIndex = index + 1;
//			}
//			return new StackLoadExpression(index, type, true);
//		}
		
		void createStackVariables(BasicBlock block, ExpressionStack stack) {
			createStackVariables(block, stack, false);
		}
		
		void createStackVariables(BasicBlock block, ExpressionStack stack, boolean dup) {
//			System.out.println();
//			StackTraceElement ste = new Exception().getStackTrace()[1];
//			System.out.println("Exprs: " + stack.size() + " from " + ste);
//			System.out.println("prestack: " + stack);


			int stackIndex = stackBase;
			Expression[] exprs = new Expression[stack.size()];
			for (int i = exprs.length - 1; i >= 0; i--) {
				exprs[i] = stack.pop1();
				stackIndex += exprs[i].getType().getSize();
			}
//			for(int i=0; i < exprs.length; i++) {
//				exprs[i] = stack.pop();
//			}

			for (int i = 0; i < exprs.length; i++) {
				Expression e = exprs[i];
				Type type = TypeUtils.asSimpleType(e.getType());
//				System.out.printf("Creating stackvar, var.idx=%d(base=%d, i=%d), type.base=%s, e.type=%s, e=%s.%n", stackIndex, stackBase, i, type, e.getType(), e);
				
				block.getStatements().add(new StackDumpStatement(e, stackIndex, type));
				stack.push(new StackLoadExpression(stackIndex, type, true));
				
				// create stack var
//				if(!(e instanceof StackLoadExpression) || ((StackLoadExpression) e).getIndex() != stackIndex) {
//					StackLoadExpression sle = new StackLoadExpression(stackIndex, type);
//					stack.push(sle);
//					block.getStatements().add(new StackDumpStatement(e, stackIndex, type));
//				} else if(!(e instanceof StackLoadExpression) || !((StackLoadExpression) e).isStackVariable() || ((StackLoadExpression) e).getIndex() != stackIndex) {
//					StackLoadExpression sle = new StackLoadExpression(nextIndex++, type);
//					stack.push(sle);
//					block.getStatements().add(new StackDumpStatement(e, stackIndex, type));
//				}
				
//				System.out.println("   code statement: (" + block.getStatements().get(block.getStatements().size() - 1) + ")");
				
				stackIndex -= e.getType().getSize();
			}
//			System.out.println("poststack: " + stack);
		}
		
		Statement getLastStatement(BasicBlock b) {
			return b.getStatements().get(b.getStatements().size() - 1);
		}
	}
	
	public static ControlFlowGraph create(MethodNode method) {
		ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(method);
		
		try {
			builder.prepareCode();
			builder.createBlocks();
			builder.linkBlocks();

			ControlFlowGraph graph = builder.graph;
			
			ControlFlowGraphDeobfuscator deobber = new ControlFlowGraphDeobfuscator();
			List<BasicBlock> naturalOrder = deobber.deobfuscate(graph);
			deobber.removeEmptyBlocks(graph, naturalOrder);
			
			{
				// prune
				BasicBlock entry = graph.getEntry();
				ListIterator<BasicBlock> it = naturalOrder.listIterator();
				while(it.hasNext()) {
					BasicBlock b = it.next();
					if((entry != b) && b.getPredecessors().size() == 0) {
						graph.removeVertex(b);
						it.remove();
					}
				}
				
				// copy edge sets
				Collection<Set<FlowEdge>> _edgeSets = graph.edges();
				Set<FlowEdge> edges = new HashSet<>();
				for(Set<FlowEdge> set : _edgeSets) {
					edges.addAll(set);
				}
				// clean graph
				graph.clear();
				// rename and add blocks
				int label = 1;
				for(BasicBlock b : naturalOrder) {
					String id = LabelHelper.createBlockName(label);
					label++;
					
					b.rename(id);
					graph.addVertex(b);
				}
				
				for(FlowEdge e : edges) {
					BasicBlock src = e.src;
					graph.addEdge(src, e);
				}
			}
						
//			ExpressionBuilder expressionBuilder = builder.new ExpressionBuilder(method);
//			expressionBuilder.createExpressions();
//			graph.setRoot(expressionBuilder.buildRoot());
//			
//			expressionBuilder.variables.build();
			
//			for(BasicBlock b : graph.blocks()) {
//				System.out.println(b);
//				System.out.println(b.getState());
//				for(Statement stmt : b.getStatements()) {
//					if(stmt instanceof IStackDumpNode) {
//						if(((IStackDumpNode) stmt).isRedundant()) {
//							continue;
//						}
//					} else if (stmt instanceof StackLoadExpression) {
//						if(((StackLoadExpression) stmt).isStackVariable()) {
//							System.out.println("   st: [STACKVAR]" + stmt);
//							continue;
//						}
//					}
//					System.out.println("   st: " + stmt);
//				}
//			}
			
//			expressionBuilder.analyseRoot(graph.getRoot());
		} catch(RuntimeException e) {
			throw new RuntimeException(method.toString(), e);
		}
		
		return builder.graph;
	}
}