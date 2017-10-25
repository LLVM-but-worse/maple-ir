package org.mapleir.ir.antlr.scope;

import java.util.HashMap;
import java.util.Map;

import org.mapleir.ir.antlr.model.UpdateableLocalsPool;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.locals.Local;
import org.objectweb.asm.tree.LabelNode;

public class CodeScope extends Scope {

	private final ControlFlowGraph cfg;
	private final UpdateableLocalsPool localPool;
	private final Map<String, Local> localMapping;
	private final Map<String, BasicBlock> blockMapping;
	
	public CodeScope(MethodScope parent) {
		super(parent.driver, parent);
	
		localPool = new UpdateableLocalsPool(0);
		cfg = new ControlFlowGraph(localPool);
		localMapping = new HashMap<>();
		blockMapping = new HashMap<>();
	}
	
	public BasicBlock findBlock(String displayName) {
		return blockMapping.get(displayName);
	}
	
	public BasicBlock createBlock(String displayName) {
		BasicBlock block = new BasicBlock(cfg, blockMapping.size() + 1, new LabelNode()) {
			@Override
			public String getDisplayName() {
				return displayName;
			}
			
			@Override
			public int hashCode() {
				return getNumericId();
			}
		};
		
		blockMapping.put(displayName, block);
		cfg.addVertex(block);
		
		return block;
	}
	
	public void mapLocal(String identifier, Local l) {
		localMapping.put(identifier, l);
	}
	
	public boolean isLocalMapped(String identifier) {
		return localMapping.containsKey(identifier);
	}
	
	public Local findLocal(String identifier) {
		return localMapping.get(identifier);
	}
	
	public Local getOrFindLocal(String identifier) {
		if (localMapping.containsKey(identifier)) {
			return localMapping.get(identifier);
		} else {
			Local l = localPool.getNextFreeLocal(false);
			l = localPool.getLatestVersion(l);
			localMapping.put(identifier, l);
			return l;
		}
	}

	public ControlFlowGraph getCfg() {
		return cfg;
	}

	public UpdateableLocalsPool getLocalPool() {
		return localPool;
	}

	@Override
	public MethodScope getParent() {
		return (MethodScope) parent;
	}
}
