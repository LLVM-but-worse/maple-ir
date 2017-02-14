package org.mapleir.deobimpl2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class ClassRenamerPass implements ICompilerPass {

	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		Collection<ClassNode> classes = cxt.getClassTree().getClasses().values();

		int n = RenamingUtil.computeMinimum(classes.size());
		Map<String, String> remapping = new HashMap<>();
		
		for(ClassNode cn : classes) {
			String s = RenamingUtil.createName(n++);
			remapping.put(cn.name, s);
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
			unsupported(cn.outerClass);
			unsupported(cn.outerMethod);
			unsupported(cn.outerMethodDesc);

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
				{
					Type[] args = Type.getArgumentTypes(m.desc);
					
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
					
					Type ret = Type.getReturnType(m.desc);
					
					String newRet = null;
					if(!ret.getDescriptor().equals("V")) {
						newRet = resolveType(ret, remapping);
					}

					if(newRet == null) {
						newRet = ret.getDescriptor();
					}
					
					sb.append(newRet);
					
					m.desc = sb.toString();
				}
				
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

				if(m.localVariables != null) {
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
			}
		}
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