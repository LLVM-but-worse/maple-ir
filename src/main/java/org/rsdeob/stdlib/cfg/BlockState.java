package org.rsdeob.stdlib.cfg;

import java.util.ArrayList;
import java.util.List;

public class BlockState {
	
	private final BasicBlock block;
	private List<LocalVar>[] entry;
	private List<LocalVar>[] exit;

	@SuppressWarnings("unchecked")
	public BlockState(BasicBlock block) {
		this.block = block;
		entry = (List<LocalVar>[]) new List<?>[2];
		init();
		initBlock();
	}

	@SuppressWarnings("unchecked")
	public BlockState(BasicBlock block, BlockState parent) {
		this.block = block;
		entry = (List<LocalVar>[]) new List<?>[parent.exit.length];
		
		for(int i=0; i < parent.exit.length; i++) {
			List<LocalVar> exit = parent.exit[i];
			if(exit != null) {
				entry[i] = new ArrayList<LocalVar>(exit);
			}
		}
		init();
		initBlock();
	}
	
	private void initBlock() {
		if(block.getState() == null) {
			block.setState(this);
		} else {
			System.err.println("already assigned " + block);
			throw new IllegalStateException();
		}
	}

	public BasicBlock getBlock() {
		return block;
	}

	public List<LocalVar>[] getEntry() {
		return entry;
	}

	public List<LocalVar>[] getExit() {
		return exit;
	}

	@SuppressWarnings("unchecked")
	public void init() {
		exit = (List<LocalVar>[]) new List<?>[entry.length];
		for(int i=0; i < entry.length; i++) {
			List<LocalVar> entries = entry[i];
			if(entries != null) {
				exit[i] = new ArrayList<>(entries);
			}
		}
	}
	
	public void addEntry(LocalVar var) {
		expand(var.getLocalIndex() + 1);
		
		if(entry[var.getLocalIndex()] == null) {
			entry[var.getLocalIndex()] = new ArrayList<>();
		}
		
		if(!entry[var.getLocalIndex()].contains(var)) {
			entry[var.getLocalIndex()].add(var);
		}
	}
	
	public LocalVar getExit(int index) {
		expand(index + 1);
		if(exit[index] == null || exit[index].size() != 1) {
			return null;
		}
		for(LocalVar var : exit[index]) {
			return var;
		}
		return null;
	}
	
	public void set(LocalVar var) {
		expand(var.getLocalIndex() + 1);
		if(exit[var.getLocalIndex()] == null) {
			exit[var.getLocalIndex()] = new ArrayList<>();
		}
		exit[var.getLocalIndex()].clear();
		exit[var.getLocalIndex()].add(var);
	}
	
	public void merge(VarVersionsMap map, int index) {
		expand(index + 1);
		if(exit[index] == null || exit[index].size() < 2) {
			return;
		} else {
			LocalVar[] vars = exit[index].toArray(new LocalVar[0]);
			for(int i=1; i < vars.length; i++) {
				vars[0] = map.updateMerge(vars[0], vars[i]);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void expand(int req) {
		int newSize = Math.min(entry.length, exit.length);
		while(req > newSize) {
			newSize *= 2;
		}
		if(entry.length != newSize) {
			List<LocalVar>[] newArray = (List<LocalVar>[]) new List<?>[newSize];
			System.arraycopy(entry, 0, newArray, 0, entry.length);
			entry = newArray;
		}
		if(exit.length != newSize) {
			List<LocalVar>[] newArray = (List<LocalVar>[]) new List<?>[newSize];
			System.arraycopy(exit, 0, newArray, 0, exit.length);
			exit = newArray;
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("state @ #").append(block.getId()).append("\n");
		sb.append(" entry: \n");
		for(int i=0; i < entry.length; i++) {
			List<LocalVar> vars = entry[i];
			if(vars != null) {
				sb.append(LocalVar.toString(vars));
			}
		}
		sb.append(" exit: \n");
		for(int i=0; i < exit.length; i++) {
			List<LocalVar> vars = exit[i];
			if(vars != null) {
				sb.append(LocalVar.toString(vars));
			}
		}
		return sb.toString();
	}
}