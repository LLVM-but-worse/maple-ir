package org.rsdeob.stdlib.cfg;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.tree.AbstractInsnNode.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.objectweb.asm.tree.AbstractInsnNode;
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
	public final ControlFlowGraph graph;
	int count = 0;
	BitSet finished;
	InsnList insns;
	LinkedList<LabelNode> queue;
	
	public ControlFlowGraphBuilder(MethodNode method) {
		this.method = method;
		graph = new ControlFlowGraph(method);
		insns = method.instructions;
		/* a block can exist in the map in the graph 
		 * but not be populated yet.
		 * we do this so that when a flow function is reached, 
		 * we can create the block reference and then handle
		 * the creation mechanism later. */
		finished = new BitSet();

		prepare();
		queue = new LinkedList<>();
		LabelNode firstLabel = (LabelNode) insns.getFirst();
		BasicBlock entry = makeBlock(++count, firstLabel);
		graph.setEntry(entry);
		queue.add(firstLabel);
		
		for(TryCatchBlockNode tc : method.tryCatchBlocks) {
			queue.addLast(tc.start);
			queue.addLast(tc.end);
			queue.addLast(tc.handler);
		}
	}


	void prepare() {
		AbstractInsnNode first = insns.getFirst();
		if(!(first instanceof LabelNode)) {
			LabelNode nFirst = new LabelNode();
			insns.insert(first, nFirst);
			first = nFirst;
		}
	}
	
	void setranges(List<BasicBlock> order) {
		Map<String, ExceptionRange> ranges = new HashMap<>();
		for(TryCatchBlockNode tc : method.tryCatchBlocks) {
			int start = LabelHelper.numeric(graph.getBlock(tc.start).getId());
			int end = LabelHelper.numeric(graph.getBlock(tc.end).getId()) - 1;
			
			List<BasicBlock> range = GraphUtils.range(order, start, end);
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
	}
	
	BasicBlock makeBlock(int index, LabelNode label) {
		BasicBlock b = new BasicBlock(graph, LabelHelper.createBlockName(index), label);
		queue.add(label);
		graph.addVertex(b);
		return b;
	}
	
	BasicBlock resolveTarget(LabelNode label, InsnList insns) {
		BasicBlock block = graph.getBlock(label);
		if(block == null) {
			block = makeBlock(++count, label);
		}
		return block;
	}
	
	void process(LabelNode label) {
		/* it may not be properly initialised yet, however. */
		BasicBlock block = graph.getBlock(label);
		
		/* if it is, we don't need to process it. */
		if(block != null && finished.get(LabelHelper.numeric(block.getId()))) {
			return;
		}
		
		if(block == null) {
			block = makeBlock(++count, label);
		}
		
		/* populate instructions. */
		int codeIndex = insns.indexOf(label);
		finished.set(LabelHelper.numeric(block.getId()));
		while(codeIndex <= insns.size()) {
			AbstractInsnNode ain = insns.get(++codeIndex);
			int type = ain.type();
			
			if(ain.opcode() != -1) {
				// System.out.println("Adding: " + Printer.OPCODES[ain.opcode()]);
				block.addInsn(ain);
			}
			
			if(type == LABEL) {
				// split into new block
				BasicBlock immediate = resolveTarget((LabelNode) ain, insns);
				graph.addEdge(block, new FlowEdge.ImmediateEdge(block, immediate));
				break;
			} else  if(type == JUMP_INSN) {
				JumpInsnNode jin = (JumpInsnNode) ain;
				BasicBlock target = resolveTarget(jin.label, insns);
				
				if(jin.opcode() == JSR) {
					throw new UnsupportedOperationException("jsr " + method);
				} else if(jin.opcode() == GOTO) {
					graph.addEdge(block, new FlowEdge.UnconditionalJumpEdge(block, target, jin));
				} else {
					graph.addEdge(block, new FlowEdge.ConditionalJumpEdge(block, target, jin));
					int nextIndex = codeIndex + 1;
					AbstractInsnNode nextInsn = insns.get(nextIndex);
					if(!(nextInsn instanceof LabelNode)) {
						LabelNode newLabel = new LabelNode();
						insns.insert(nextInsn, newLabel);
						nextInsn = newLabel;
					}
					
					// create immediate successor reference if it's not already done
					BasicBlock immediate = resolveTarget((LabelNode) nextInsn, insns);
					graph.addEdge(block, new FlowEdge.ImmediateEdge(block, immediate));
				}
				break;
			} else if(type == LOOKUPSWITCH_INSN) {
				LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) ain;
				
				for(int i=0; i < lsin.keys.size(); i++) {
					BasicBlock target = resolveTarget(lsin.labels.get(i), insns);
					graph.addEdge(block, new FlowEdge.SwitchEdge(block, target, lsin, lsin.keys.get(i)));
				}
				
				BasicBlock dflt = resolveTarget(lsin.dflt, insns);
				graph.addEdge(block, new FlowEdge.DefaultSwitchEdge(block, dflt, lsin));
				break;
			} else if(type == TABLESWITCH_INSN) {
				TableSwitchInsnNode tsin = (TableSwitchInsnNode) ain;
				for(int i=tsin.min; i <= tsin.max; i++) {
					BasicBlock target = resolveTarget(tsin.labels.get(i - tsin.min), insns);
					graph.addEdge(block, new FlowEdge.SwitchEdge(block, target, tsin, i));
				}
				BasicBlock dflt = resolveTarget(tsin.dflt, insns);
				graph.addEdge(block, new FlowEdge.DefaultSwitchEdge(block, dflt, tsin));
				break;
			} else if(GraphUtils.isExitOpcode(ain.opcode())) {
				break;
			}
		}
	}
	
	public void processQueue() {
		while(!queue.isEmpty()) {
			LabelNode label = queue.removeFirst();
			process(label);
		}
		
		List<BasicBlock> blocks = new ArrayList<>(graph.blocks());
		Collections.sort(blocks, new Comparator<BasicBlock>() {
			@Override
			public int compare(BasicBlock o1, BasicBlock o2) {
				int i1 = insns.indexOf(o1.getLabel());
				int i2 = insns.indexOf(o2.getLabel());
				return Integer.compare(i1, i2);
			}
		});
		GraphUtils.naturaliseGraph(graph, blocks);
		setranges(blocks);
	}
	
	public static ControlFlowGraph create(MethodNode method) {
		ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder(method);
		builder.processQueue();
		return builder.graph;
	}
}