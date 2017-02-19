package org.mapleir.deobimpl2;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.deobimpl2.util.RenamingUtil;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.FieldLoadExpr;
import org.mapleir.ir.code.stmt.FieldStoreStmt;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.klass.library.ApplicationClassSource;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class FieldRenamerPass implements IPass {

	@Override
	public boolean isSingletonPass() {
		return false;
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {		
		Map<FieldNode, String> remapped = new HashMap<>();

//		int totalFields = 0;
		
//		int i = RenamingUtil.computeMinimum(totalFields);
		
		ApplicationClassSource source = cxt.getApplication();
		
		int i = RenamingUtil.numeric("aaaaa");
		
		for(ClassNode cn : source.iterate()) {
//			totalFields += cn.fields.size();
			for(FieldNode fn : cn.fields) {
				remapped.put(fn, RenamingUtil.createName(i++));
			}
		}
		
		for(ClassNode cn : source.iterate()) {
			for(MethodNode m : cn.methods) {
				ControlFlowGraph cfg = cxt.getIR(m);
				
				for(BasicBlock b : cfg.vertices()) {
					for(Stmt stmt : b) {
						
						if(stmt.getOpcode() == Opcode.FIELD_STORE) {
							FieldStoreStmt fs = (FieldStoreStmt) stmt;
							
							FieldNode f = findField(source, fs.getOwner(), fs.getName(), fs.getDesc(), fs.getInstanceExpression() == null);
							
							if(f != null) {
								if(remapped.containsKey(f)) {
									fs.setName(remapped.get(f));
								} else if(mustMark(source, f.owner.name)) {
									System.err.println("  no remap for " + f + ", owner: " + f.owner.name);
								}
							} else {
								if(mustMark(source, fs.getOwner())) {
									System.err.println("  can't resolve field(set): " + fs.getOwner() + "." + fs.getName() + " " + fs.getDesc() + ", " + (fs.getInstanceExpression() == null));
								}
							}
						}
						
						for(Expr e : stmt.enumerateOnlyChildren()) {
							if(e.getOpcode() == Opcode.FIELD_LOAD) {
								FieldLoadExpr fl = (FieldLoadExpr) e;
								
								FieldNode f = findField(source, fl.getOwner(), fl.getName(), fl.getDesc(), fl.getInstanceExpression() == null);
								
								if(f != null) {
									if(remapped.containsKey(f)) {
										fl.setName(remapped.get(f));
									} else if(mustMark(source, f.owner.name)) {
										System.err.println("  no remap for " + f + ", owner: " + f.owner.name);
									}
								} else {
									if(mustMark(source, fl.getOwner())) {
										System.err.println("  can't resolve field(get): " + fl.getOwner() + "." + fl.getName() + " " + fl.getDesc() + ", " + (fl.getInstanceExpression() == null));
									}
								}
							}
						}
					}
				}
			}
		}
		
		for(Entry<FieldNode, String> e : remapped.entrySet()) {
			e.getKey().name = e.getValue();
		}
		
		System.out.printf("  Renamed %d fields.%n", remapped.size());
		
		return remapped.size();
	}
	
	private boolean mustMark(ApplicationClassSource source, String owner) {
		ClassNode cn = source.findClassNode(owner);
		return cn == null || !source.isLibraryClass(owner);
	}
	
	private FieldNode findField(ApplicationClassSource source, String owner, String name, String desc, boolean isStatic) {
		if(isStatic) {
			return findStaticField(source, owner, name, desc);
		} else {
			return findVirtualField(source, owner, name, desc);
		}
	}
	
	private FieldNode findStaticField(ApplicationClassSource source, String owner, String name, String desc) {
		ClassNode cn0 = source.findClassNode(owner);
		if(cn0 == null) {
			return null;
		}
		
		Set<ClassNode> cset = source.getStructures().getAllBranches(cn0, true);
		
		for(ClassNode cn : cset) {
			for(FieldNode f : cn.fields) {
				if(Modifier.isStatic(f.access) && f.name.equals(name) && f.desc.equals(desc)) {
					return f;
				}
			}
		}
		
		return null;
	}
	
	private FieldNode findVirtualField(ApplicationClassSource source, String owner, String name, String desc) {
		ClassNode cn = source.findClassNode(owner);
		
		if(cn != null) {
			do {
				for(FieldNode f : cn.fields) {
					if(!Modifier.isStatic(f.access) && f.name.equals(name) && f.desc.equals(desc)) {
						return f;
					}
				}
				
				cn = source.findClassNode(cn.superName);
			} while(cn != null);
		}
		
		return null;
	}
}