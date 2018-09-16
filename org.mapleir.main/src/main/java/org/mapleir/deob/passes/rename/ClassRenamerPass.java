package org.mapleir.deob.passes.rename;

import java.util.*;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.IPass;
import org.mapleir.deob.PassContext;
import org.mapleir.deob.PassResult;
import org.mapleir.deob.util.RenamingHeuristic;
import org.mapleir.deob.util.RenamingUtil;
import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.*;
import org.mapleir.ir.code.expr.invoke.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.mapleir.ir.code.stmt.FieldStoreStmt;
import org.mapleir.ir.code.stmt.ReturnStmt;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.stdlib.collections.CollectionUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class ClassRenamerPass implements IPass {
	
	private final RenamingHeuristic heuristic;
	
	public ClassRenamerPass(RenamingHeuristic heuristic) {
		this.heuristic = heuristic;
	}
	
	private final Map<String, String> remapping = new HashMap<>();
	
	public boolean wasRemapped(String name) {
		return remapping.containsKey(name);
	}
	
	public String getRemappedName(String name) {
		return remapping.getOrDefault(name, name);
	}
	
	/*private String getClassName(String name) {
		int i = name.lastIndexOf('/');
		if(i == -1) {
			return name;
		} else {
			return name.substring(i + 1, name.length());
		}
	}*/
	
	@Override
	public PassResult accept(PassContext pcxt) {
		AnalysisContext cxt = pcxt.getAnalysis();
		ApplicationClassSource source = cxt.getApplication();
		Collection<ClassNode> classes = CollectionUtils.collate(source.iterator());

//		int min = RenamingUtil.computeMinimum(classes.size());
		int n = RenamingUtil.numeric("aaa");
		
		int step = 27;
		
		/* if(n > min) {
			step = Math.floorDiv(n, min);
			System.out.println("min: " + min);
			System.out.println("act: " + n);
			System.out.println("posstep: " + step);
			
			int j = n;
			for(int i=0; i < n; i++) {
				System.out.println("gen: " + j);
				j += step;
			}
		} */
		
		for(ClassNode cn : classes) {
			String className = RenamingUtil.getClassName(cn.name);
			if (!heuristic.shouldRename(className, cn.access)) {
				System.out.println("Heuristic bypass " + cn.name);
			}
			String newName = heuristic.shouldRename(className, cn.access) ? RenamingUtil.createName(n) : className;
			String s = RenamingUtil.getPackage(cn.name) + newName;
			n += step;
			remapping.put(cn.name, s);
//			 System.out.println(cn.name + " -> " + s);
			cn.name = s;
		}
		
		for(ClassNode cn : classes) {
			cn.superName = remapping.getOrDefault(cn.superName, cn.superName);
			
			{
				List<String> ifaces = new ArrayList<>();
				for(int i=0; i < cn.interfaces.size(); i++) {
					String s = cn.interfaces.get(i);
					ifaces.add(remapping.getOrDefault(s, s));
				}
				cn.interfaces = ifaces;
			}
			
			unsupported(cn.signature);
			// unsupported(cn.sourceFile);
			// unsupported(cn.sourceDebug);
			cn.outerClass = remapping.getOrDefault(cn.outerClass, cn.outerClass);
//			unsupported(cn.outerMethod);
//			unsupported(cn.outerMethodDesc);

			unsupported(cn.visibleAnnotations);
			unsupported(cn.invisibleAnnotations);
			unsupported(cn.visibleTypeAnnotations);
			unsupported(cn.invisibleTypeAnnotations);

			unsupported(cn.attrs);
			unsupported(cn.innerClasses);
			
			for(FieldNode f : cn.fields) {
				unsupported(cn.signature);
				
				{
					Type type = Type.getType(f.desc);
					String newType = resolveType(type, remapping);
					
					if(newType != null) {
						f.desc = newType;
					}
				}
				
				unsupported(f.visibleAnnotations);
				unsupported(f.invisibleAnnotations);
				unsupported(f.visibleTypeAnnotations);
				unsupported(f.invisibleTypeAnnotations);
				unsupported(f.attrs);
			}
			
			for(MethodNode m : cn.methods) {
				m.desc = resolveMethod(m.desc, remapping);
				
				unsupported(m.signature);
				
				{
					List<String> exceptions = new ArrayList<>();
					for(int i=0; i < m.exceptions.size(); i++) {
						String s = m.exceptions.get(i);
						exceptions.add(remapping.getOrDefault(s, s));
					}
					m.exceptions = exceptions;
				}
				
				unsupported(m.parameters);
				unsupported(m.visibleAnnotations);
				unsupported(m.invisibleAnnotations);
				unsupported(m.visibleTypeAnnotations);
				unsupported(m.invisibleTypeAnnotations);
				unsupported(m.attrs);
				unsupported(m.annotationDefault);
				unsupported(m.visibleParameterAnnotations);
				unsupported(m.invisibleParameterAnnotations);
				
				for(TryCatchBlockNode tcbn : m.tryCatchBlocks) {
					tcbn.type = remapping.getOrDefault(tcbn.type, tcbn.type);
				}

				ControlFlowGraph cfg = cxt.getIRCache().getFor(m);
				
				for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
					Set<Type> newTypeSet = new HashSet<>();
					for(Type t : er.getTypes()) {
						// FIXME:
						String s = t.getInternalName();
						if(remapping.containsKey(s)) {
							newTypeSet.add(Type.getType("L" + remapping.get(s) + ";"));
						} else {
							newTypeSet.add(t);
						}
					}
					er.setTypes(newTypeSet);
				}

				if(m.localVariables != null) {
					m.localVariables.clear();
					for(LocalVariableNode lvn : m.localVariables) {
						String newDesc = resolveType(Type.getType(lvn.desc), remapping);
						if(newDesc != null) {
							lvn.desc = newDesc;
						}
						
						unsupported(lvn.signature);
					}
				}
				
				unsupported(m.visibleLocalVariableAnnotations);
				unsupported(m.invisibleLocalVariableAnnotations);
				
				for(BasicBlock b : cfg.vertices()) {
					for(Stmt stmt : b) {
						
						if(stmt.getOpcode() == Opcode.FIELD_STORE) {
							FieldStoreStmt fs = (FieldStoreStmt) stmt;
							String owner = fs.getOwner();
							fs.setOwner(remapping.getOrDefault(owner, owner));
							
							{
								Type type = Type.getType(fs.getDesc());
								String newType = resolveType(type, remapping);
								
								if(newType != null) {
									fs.setDesc(newType);
								}
							}
						} else if(stmt.getOpcode() == Opcode.RETURN) {
							ReturnStmt ret = (ReturnStmt) stmt;
							String newType = resolveType(ret.getType(), remapping);
							
							if(newType != null) {
								ret.setType(Type.getType(newType));
							}
						} else if(stmt instanceof AbstractCopyStmt) {
							AbstractCopyStmt copy = (AbstractCopyStmt) stmt;
							
							VarExpr v = copy.getVariable();
							
							String newType = resolveType(v.getType(), remapping);
							if(newType != null) {
								v.setType(Type.getType(newType));
							}
						}
						
						for(Expr e : stmt.enumerateOnlyChildren()) {
							if(e.getOpcode() == Opcode.CAST) {
								CastExpr cast = (CastExpr) e;
								String newType = resolveType(cast.getType(), remapping);
								
								if(newType != null) {
									cast.setType(Type.getType(newType));
								}
							} else if(e.getOpcode() == Opcode.CATCH) {
								CaughtExceptionExpr caught = (CaughtExceptionExpr) e;
								String newType = resolveType(caught.getType(), remapping);

								if(newType != null) {
									caught.setType(Type.getType(newType));
								}
							} else if(e.getOpcode() == Opcode.INVOKE) {
								InvocationExpr invoke = (InvocationExpr) e;
								if (invoke.isDynamic())
									throw new UnsupportedOperationException();
								
								invoke.setOwner(remapping.getOrDefault(invoke.getOwner(), invoke.getOwner()));
								invoke.setDesc(resolveMethod(invoke.getDesc(), remapping));
							} else if(e.getOpcode() == Opcode.FIELD_LOAD) {
								FieldLoadExpr fl = (FieldLoadExpr) e;
								
								fl.setOwner(remapping.getOrDefault(fl.getOwner(), fl.getOwner()));

								String newType = resolveType(fl.getType(), remapping);
								if(newType != null) {
									fl.setDesc(newType);
								}
							} else if(e.getOpcode() == Opcode.INIT_OBJ) {
								InitialisedObjectExpr init = (InitialisedObjectExpr) e;
								
								init.setOwner(remapping.getOrDefault(init.getOwner(), init.getOwner()));
								init.setDesc(resolveMethod(init.getDesc(), remapping));
							} else if(e.getOpcode() == Opcode.INSTANCEOF) {
								InstanceofExpr inst = (InstanceofExpr) e;
								
								String newType = resolveType(inst.getCheckType(), remapping);
								if(newType != null) {
									inst.setCheckType(Type.getType(newType));
								}
							} else if(e.getOpcode() == Opcode.NEW_ARRAY) {
								NewArrayExpr na = (NewArrayExpr) e;
								
								String newType = resolveType(na.getType(), remapping);
								if(newType != null) {
									na.setType(Type.getType(newType));
								}
							} else if(e.getOpcode() == Opcode.ALLOC_OBJ) {
								AllocObjectExpr uninit = (AllocObjectExpr) e;
								
								String newType = resolveType(uninit.getType(), remapping);
								if(newType != null) {
									uninit.setType(Type.getType(newType));
								}
							} else if(e.getOpcode() == Opcode.LOCAL_LOAD) {
								VarExpr v = (VarExpr) e;
								
								String newType = resolveType(v.getType(), remapping);
								if(newType != null) {
									v.setType(Type.getType(newType));
								}
							} else if(e.getOpcode() == Opcode.CONST_LOAD) {
								ConstantExpr c = (ConstantExpr) e;
								
								Object cst = c.getConstant();
								if(cst instanceof Type) {
									Type t = (Type) cst;
									
									if(t.getSort() == Type.OBJECT) {
										String newType = resolveType(t, remapping);
										if(newType != null) {
											c.setConstant(Type.getType(newType));
										}
									} else {
										throw new UnsupportedOperationException(String.format("Unsupported ctype %s (%d)", t, t.getSort()));
									}
								}
							}
						}
					}
				}
			}
		}
		
		source.rebuildTable();

		return PassResult.with(pcxt, this).finished().make();
	}
	
	private String resolveMethod(String desc, Map<String, String> remapping) {
		Type[] args = Type.getArgumentTypes(desc);
		
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		
		for(int i=0; i < args.length; i++) {
			Type arg = args[i];
			String newArg = resolveType(arg, remapping);
			
			if(newArg == null) {
				newArg = arg.getDescriptor();
			}
			
			sb.append(newArg);
		}
		
		sb.append(")");
		
		Type ret = Type.getReturnType(desc);
		
		String newRet = null;
		if(!ret.getDescriptor().equals("V")) {
			newRet = resolveType(ret, remapping);
		}

		if(newRet == null) {
			newRet = ret.getDescriptor();
		}
		
		sb.append(newRet);
		
		return sb.toString();
	}
	
	private String resolveType(Type t, Map<String, String> remapping) {
		if(t.getSort() == Type.ARRAY) {
			Type elementType = t.getElementType();
			
			if(elementType.getSort() == Type.OBJECT) {
				String internalName = elementType.getInternalName();
				if(remapping.containsKey(internalName)) {
					String newInternalName = remapping.get(internalName);
					String newDescriptor = makeArrayDescriptor(newInternalName, t.getDimensions());
					return newDescriptor;
				}
			} else {
				// primitive, don't do anything.
			}
		} else if(t.getSort() == Type.OBJECT) {
			String internalName = t.getInternalName();
			if(remapping.containsKey(internalName)) {
				String newInternalName = remapping.get(internalName);
				return "L" + newInternalName + ";";
			}
		} else {
			// primitive, don't do anything.
		}
		
		return null;
	}
	
	private String makeArrayDescriptor(String className, int dims) {
		StringBuilder sb = new StringBuilder();
		for(int i=0; i < dims; i++) {
			sb.append('[');
		}
		return sb.append("L").append(className).append(";").toString();
	}
	
	private void unsupported(Object o) {
		boolean col = o instanceof Collection;
		boolean array = (o != null && o.getClass().isArray());
		
		if((col && ((Collection<?>) o).size() > 0) || (array && ((Object[]) o).length > 0) || (!col && !array && o != null)) {
			throw new UnsupportedOperationException(array ? Arrays.toString((Object[]) o) : o.toString());
		}
	}
}
