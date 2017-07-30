package org.mapleir.deob.interproc.geompa;

import org.mapleir.context.AnalysisContext;
import org.mapleir.ir.TypeUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class MethodNodeFactory {
	protected PAG pag;
	protected MethodPAG mpag;
	protected MapleMethod method;
	protected AnalysisContext cxt;
	protected ClientAccessibilityOracle accessibilityOracle;
	
	public MethodNodeFactory(PAG pag, MethodPAG mpag) {
		this.pag = pag;
		this.mpag = mpag;
		setCurrentMethod(mpag.getMethod());
	}
	
	private void setCurrentMethod(MapleMethod m) {
		method = m;
		if ((m.getMethodNode().access & Opcodes.ACC_STATIC) == 0) {
			ClassNode c = m.getMethodNode().owner;
			if (c == null) {
				throw new RuntimeException("Method " + m + " has no declaring class");
			}
			caseThis();
		}
		Type[] params = Type.getArgumentTypes(m.getMethodNode().desc);
		for (int i = 0 ; i < params.length; i++) {
			if (TypeUtils.isRefLikeType(params[i])) {
				caseParm(i);
			}
		}
		Type retType = Type.getReturnType(m.getMethodNode().desc);
		if (TypeUtils.isRefLikeType(retType)) {
			caseRet();
		}
	}
	
}
