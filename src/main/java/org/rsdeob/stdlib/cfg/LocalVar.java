package org.rsdeob.stdlib.cfg;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.rsdeob.stdlib.cfg.stat.Statement;

public class LocalVar {
	
	private final int localIndex;
	private final int size;
	private final boolean argument;
	private final Set<Statement> definitions;
	private final Set<Statement> usages;
	
	public LocalVar(int localIndex, int size) {
		this(localIndex, size, false);
	}
	
	public LocalVar(int localIndex, int size, boolean argument) {
		this.localIndex = localIndex;
		this.size = size;
		this.argument = argument;
		definitions = new HashSet<>();
		usages = new HashSet<>();
	}

	public int getLocalIndex() {
		return localIndex;
	}
	
	public int getSize() {
		return size;
	}
	
	public boolean isArgument() {
		return argument;
	}

	public Set<Statement> getDefinitions() {
		return definitions;
	}

	public Set<Statement> getUsages() {
		return usages;
	}

	@Override
	public String toString() {
		return "LocalVar [localIndex=" + localIndex + ", size=" + size + ", argument=" + argument + ", definitions=" + definitions + ", usages=" + usages + "]";
	}
	
	public static String toString(List<LocalVar> vars) {
		StringBuilder sb = new StringBuilder();
		for(LocalVar var : vars) {
			sb.append("  idx=").append(var.getLocalIndex()).append(", width=").append(var.getSize());
			sb.append(", arg=").append(var.isArgument()).append("\n");
			
			for(Statement u : var.getUsages()) {
				sb.append("   u:").append(u).append(" (").append(u.hashCode()).append(")").append("\n");
			}
			for(Statement d : var.getDefinitions()) {
				sb.append("   d:").append(d).append(" (").append(d.hashCode()).append(")").append("\n");
			}
		}
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof LocalVar) {
			return ((LocalVar) o).localIndex == localIndex;
		} else {
			return false;
		}
	}
}