package org.mapleir.deob.passes.rename;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.InvocationResolver;
import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.IPass;
import org.mapleir.deob.util.RenamingUtil;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.FieldLoadExpr;
import org.mapleir.ir.code.stmt.FieldStoreStmt;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class FieldRenamerPass implements IPass {

	@Override
	public boolean isQuantisedPass() {
		return false;
	}
	
	@Override
	public int accept(AnalysisContext cxt, IPass prev, List<IPass> completed) {		
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
		
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		for(ClassNode cn : source.iterate()) {
			for(MethodNode m : cn.methods) {
				ControlFlowGraph cfg = cxt.getIRCache().getFor(m);
				
				for(BasicBlock b : cfg.vertices()) {
					for(Stmt stmt : b) {
						
						if(stmt.getOpcode() == Opcode.FIELD_STORE) {
							FieldStoreStmt fs = (FieldStoreStmt) stmt;
							
							FieldNode f = resolver.findField(fs.getOwner(), fs.getName(), fs.getDesc(), fs.getInstanceExpression() == null);
							
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
								
								FieldNode f = resolver.findField(fl.getOwner(), fl.getName(), fl.getDesc(), fl.getInstanceExpression() == null);
								
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
}