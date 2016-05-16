package org.rsdeob.stdlib.cfg;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.cfg.util.LabelHelper;

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