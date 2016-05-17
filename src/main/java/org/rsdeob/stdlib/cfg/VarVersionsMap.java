package org.rsdeob.stdlib.cfg;

import static org.objectweb.asm.Opcodes.ACC_STATIC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.rsdeob.stdlib.cfg.expr.StackLoadExpression;
import org.rsdeob.stdlib.cfg.expr.var.StackDumpExpression;
import org.rsdeob.stdlib.cfg.stat.BlockHeaderStatement;
import org.rsdeob.stdlib.cfg.stat.ConditionalJumpStatement;
import org.rsdeob.stdlib.cfg.stat.ReturnStatement;
import org.rsdeob.stdlib.cfg.stat.StackDumpStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.stat.SwitchStatement;
import org.rsdeob.stdlib.cfg.stat.ThrowStatement;
import org.rsdeob.stdlib.cfg.stat.UnconditionalJumpStatement;

@SuppressWarnings("serial")
public class VarVersionsMap extends HashMap<Integer, List<LocalVar>> {
	
	private final ControlFlowGraph graph;
	private final BlockState[] states;
	private final List<BasicBlock> orderedBlockList;
	private LinkedList<BlockState> queue;
	
	public VarVersionsMap(ControlFlowGraph graph) {
		this.graph = graph;
		orderedBlockList = new ArrayList<>(graph.vertices());
		states = new BlockState[graph.blocks().size()];
		queue = new LinkedList<>();
		
		BlockState entryState = new BlockState(graph.getEntry());
		states[0] = entryState;
		
		MethodNode m = graph.getMethod();
		int index = 0;
		if((m.access & ACC_STATIC) == 0) {
			// instance method
			LocalVar var = putArgument(index++, 1);
			entryState.addEntry(var);
		}
		
		Type[] args = Type.getArgumentTypes(m.desc);
		for(int i=0; i < args.length; i++) {
			Type arg = args[i];
			LocalVar var = putArgument(index, arg.getSize());
			entryState.addEntry(var);
			index += arg.getSize();
		}
		
		queue.add(entryState);
	}

	public void build() {
		while(!queue.isEmpty()) {
			BlockState next = queue.removeFirst();
			buildState(next);
		}
		
		for(LocalVar var : getVars()) {
			for(Statement stmt : new HashSet<>(var.getDefinitions())) {
				if(stmt instanceof BlockHeaderStatement) {
					var.getDefinitions().remove(stmt);
				}
			}
		}
	}
	
	private void buildState(BlockState state) {
		state.init();
		
		for(TryCatchBlockNode tc : graph.getMethod().tryCatchBlocks) {
			int start = getId(tc.start);
			int end = getId(tc.end);
			int id = getId(state.getBlock().getLabel());
			
			if(id < start || id >= end) {
				continue;
			}
			
			analyseTargetState(state, graph.getBlock(tc.handler));
		}
		
		RootStatement root = graph.getRoot();
		// first statement in the block's index
		int i = root.indexOf(root.getBlockStatements().get(state.getBlock())) +1;
		
		for(; root.read(i) != null; i++) {
			Statement stmt = root.read(i);
			processStmt(state, stmt);
			
			if(stmt instanceof BlockHeaderStatement) {
				// analyse immediate successor
				analyseTargetState(state, ((BlockHeaderStatement) stmt).getBlock());
				break;
			} else if(stmt instanceof UnconditionalJumpStatement) {
				analyseTargetState(state, ((UnconditionalJumpStatement) stmt).getTarget());
				break;
			} else if(stmt instanceof ConditionalJumpStatement) {
				analyseTargetState(state, ((ConditionalJumpStatement) stmt).getTrueSuccessor());
				// don't break and fall through to analyse false successor as well
			} else if(stmt instanceof SwitchStatement) {
				SwitchStatement ss = (SwitchStatement) stmt;
				for(Entry<Integer, BasicBlock> e : ss.getTargets().entrySet()) {
					analyseTargetState(state, e.getValue());
				}
				analyseTargetState(state, ss.getDefaultTarget());
				break;
			} else if(stmt instanceof ReturnStatement ||
					stmt instanceof ThrowStatement) {
				break;
			}
		}
	}
	
	private void processStmt(BlockState state, Statement stmt) {
		for(int i=0; stmt.read(i) != null; i++) {
			processStmt(state, stmt.read(i));
		}
		
		if(stmt instanceof StackDumpExpression) {
			StackDumpExpression expr = (StackDumpExpression) stmt;
			LocalVar var = get(expr.getIndex(), expr.getType().getSize(), graph.getRoot().getBlockStatements().get(state.getBlock()), true);
			if(!var.getDefinitions().contains(expr)) {
				var.getDefinitions().add(expr);
			}
			state.set(var);
		} else if(stmt instanceof StackDumpStatement) {
			StackDumpStatement dumpStmt = (StackDumpStatement) stmt;
			LocalVar var = get(dumpStmt.getIndex(), dumpStmt.getType().getSize(), graph.getRoot().getBlockStatements().get(state.getBlock()), true);
			if(!var.getDefinitions().contains(dumpStmt)) {
				var.getDefinitions().add(dumpStmt);
			}
			state.set(var);
		} else if(stmt instanceof StackLoadExpression) {
			StackLoadExpression expr = (StackLoadExpression) stmt;
			state.merge(this, expr.getIndex());
			LocalVar exitVar = state.getExit(expr.getIndex());
			if(exitVar == null) {
				throw new IllegalStateException("no var at index " + expr.getIndex());
			}
			if(!exitVar.getUsages().contains(expr)) {
				exitVar.getUsages().add(expr);
			}
		}
	}
	
	private BlockState analyseTargetState(BlockState parent, BasicBlock target) {
		int targetIndex = getId(target.getLabel());
		
		if(states[targetIndex] == null) {
			BlockState targetState = new BlockState(target, parent);
			states[targetIndex] = targetState;
			// add to analysis queue
			queue.addLast(targetState);
			return targetState;
		} else {
			BlockState targetState = states[targetIndex];
			
			int max = Math.max(parent.getExit().length, targetState.getEntry().length);
			parent.expand(max);
			targetState.expand(max);
			
			boolean revisit = false;
			for(int i=0; i < parent.getExit().length; i++) {
				if(parent.getExit()[i] == null) {
					// was previously uninitialised, no need for merge
					continue;
				} else if(targetState.getEntry()[i] == null) {
					// exit variable state coming from the previous state
					// which hasn't been initialised yet
					targetState.getEntry()[i] = new ArrayList<>(parent.getExit()[i] /*not null here, checked above*/);
					// revisit if there are exit multiple variable
					// versions from the previous state
					revisit |= targetState.getEntry()[i].size() > 0;
				} else {
					// find mismatching variable versions
					for(LocalVar var : parent.getExit()[i]) {
						if(!targetState.getEntry()[i].contains(var)) {
							targetState.getEntry()[i].add(var);
							revisit |= true;
						}
					}
				}
			}

			// add to analysis queue
			if(revisit && !queue.contains(targetState)) {
				queue.addLast(targetState);
			}
			
			return targetState;
		}
	}
	
	private int getId(LabelNode l) {
		BasicBlock b = graph.getBlock(l);
		return orderedBlockList.indexOf(b);
	}
	
	public List<LocalVar> getVars() {
		List<LocalVar> all = new ArrayList<LocalVar>();
		for (List<LocalVar> vars : values()) {
			all.addAll(vars);
		}
		return all;
	}
	
	public LocalVar putArgument(int index, int size) {
		if(containsKey(index)) {
			throw new UnsupportedOperationException("already exists @" + index);
		} else {
			LocalVar var = new LocalVar(index, size, true);
			put(var);
			return var;
		}
	}

//	@Override
//	public List<LocalVar> remove(Object key) {
//		System.out.println("VarVersionsMap.remove()");
//		return super.zremove(key);
//	}
	
	public void put(LocalVar var) {
		if(!containsKey(var.getLocalIndex())) {
			put(var.getLocalIndex(), new ArrayList<>());
		}
		List<LocalVar> vars = get(var.getLocalIndex());
		if(!vars.contains(var)) {
			vars.add(var);
		}
	}
	
	public LocalVar get(int index, int size, Statement stmt, boolean create) {
		LocalVar var = get0(index, size, stmt);
		if(var != null) {
			return var;
		}
		
		if(create) {
			var = new LocalVar(index, size);
			var.getDefinitions().add(stmt);
			put(var);
		}

		return var;
	}
	
	private LocalVar get0(int index, int size, Statement stmt) {
		if(containsKey(index)) {
			LocalVar r = null;
			for(LocalVar var : get(index)) {
				if(var.getSize() != size) {
					throw new IllegalStateException(String.format("index=%d, size=%d, othersize=%d", index, size, var.getSize()));
				}
				if(var.getUsages().contains(stmt) || var.getDefinitions().contains(stmt)) {
					if(r != null) {
						throw new IllegalStateException(String.format("multiple definitions for index=%d, size=%d, stmt=%s, r=%s, var=%s", index, size, stmt, r, var));
					}
					r = var;
				}
			}
			return r;
		}
		return null;
	}
	
	public LocalVar updateMerge(LocalVar v1, LocalVar v2) {
		if(v1.getLocalIndex() != v2.getLocalIndex()) {
			throw new IllegalArgumentException(String.format("stack height mismatch: %d|%d", v1.getLocalIndex(), v2.getLocalIndex()));
		} else if(v1.getSize() != v2.getSize()) {
			throw new IllegalArgumentException(String.format("var type mismatch: %d|%d", v1.getLocalIndex(), v2.getLocalIndex()));
		}
		
		boolean isArgument = v1.isArgument() || v2.isArgument();
		LocalVar merged = new LocalVar(v1.getLocalIndex(), v1.getSize(), isArgument);
		merged.getDefinitions().addAll(v1.getDefinitions());
		merged.getDefinitions().addAll(v2.getDefinitions());
		merged.getUsages().addAll(v1.getUsages());
		merged.getUsages().addAll(v2.getUsages());
		
		List<LocalVar> vars = get(v1.getLocalIndex());
		vars.remove(v1);
		vars.remove(v2);
		vars.add(merged);
		
		for(int i=0; i < states.length; i++) {
			BlockState state = states[i];
			if(state == null) {
				continue;
			}
			
			updateState(state.getEntry(), v1, v2, merged);
			updateState(state.getExit(), v1, v2, merged);
		}
		
		return merged;
	}

	private void updateState(List<LocalVar>[] list, LocalVar v1, LocalVar v2, LocalVar var) {
		for(int k=0; k < list.length; k++) {
			List<LocalVar> l2 = list[k];
			if(l2 != null) {
				if(l2.contains(v1) || l2.contains(v2)) {
					l2.remove(v1);
					l2.remove(v2);
					l2.add(var);
				}
			}
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for(Entry<Integer, List<LocalVar>> e : entrySet()) {
			sb.append("@").append(e.getKey()).append(" (").append(e.getValue().size()).append(")\n");
			sb.append(LocalVar.toString(e.getValue()));
		}
		return sb.toString();
	}
}