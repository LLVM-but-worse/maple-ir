package org.mapleir.deobimpl2;

import org.mapleir.deobimpl2.util.RenamingUtil;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.FieldLoadExpr;
import org.mapleir.ir.code.stmt.FieldStoreStmt;
import org.mapleir.state.IContext;
import org.mapleir.stdlib.application.ApplicationClassSource;
import org.mapleir.stdlib.deob.IPass;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
				ControlFlowGraph cfg = cxt.getCFGS().getIR(m);
				
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
	
	public FieldNode findStaticField(ApplicationClassSource app, String owner, String name, String desc) {
		Set<FieldNode> set = new HashSet<>();
		
		ClassNode cn = app.findClassNode(owner);
		
		/* we do this because static fields can be in
		 * interfaces. */
		if(cn != null) {
			Set<ClassNode> lvl = new HashSet<>();
			lvl.add(cn);
			for(;;) {
				if(lvl.size() == 0) {
					break;
				}
				
				Set<FieldNode> lvlSites = new HashSet<>();
				
				for(ClassNode c : lvl) {
					for(FieldNode f : c.fields) {
						if(Modifier.isStatic(f.access) && f.name.equals(name) && f.desc.equals(desc)) {
							lvlSites.add(f);
						}
					}
				}
				
				if(lvlSites.size() > 1) {
					System.out.printf("(warn) resolved %s.%s %s to %s.%n", owner, name, desc, lvlSites);
				}
				
				if(lvlSites.size() > 0) {
					set.addAll(lvlSites);
					break;
				}
				
				Set<ClassNode> newLvl = new HashSet<>();
				for(ClassNode c : lvl) {
					ClassNode sup = app.findClassNode(c.superName);
					if(sup != null) {
						newLvl.add(sup);
					}
					
					for(String iface : c.interfaces) {
						ClassNode ifaceN = app.findClassNode(iface);
						
						if(ifaceN != null) {
							newLvl.add(ifaceN);
						}
					}
				}
				
				lvl.clear();
				lvl = newLvl;
			}
		}
		
		if(set.size() > 1) {
			throw new UnsupportedOperationException(String.format("multi dispatch?: %s.%s %s results:%s", owner, name, desc, set));
		} else if(set.size() == 1) {
			return set.iterator().next();
		} else {
			return null;
		}
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