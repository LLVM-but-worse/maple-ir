package org.mapleir.deob.interproc.cxtsenscg;

import java.lang.reflect.Modifier;

import org.mapleir.deob.interproc.geompa.*;
import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArrayLoadExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.FieldLoadExpr;
import org.mapleir.ir.code.expr.NewArrayExpr;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.ir.code.stmt.*;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.VersionedLocal;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class MethodPAGNodeFactory implements Opcode {

	private final MethodPAG mpag;
	private final MethodNode method;
	
	public MethodPAGNodeFactory(MethodPAG mpag) {
		this.mpag = mpag;
		method = mpag.getMethod();
	
		init();
	}
	
	private void init() {
		if(!Modifier.isStatic(method.access)) {
			caseThis();
		}
		
		Type[] args = Type.getArgumentTypes(method.desc);
		for(int i=0; i < args.length; i++) {
			Type arg = args[i];
			
			if(isRefLikeType(arg)) {
				caseParm(i, arg);
			}
		}
		
		Type returnType = Type.getReturnType(method.desc);
		if(isRefLikeType(returnType)) {
			caseRet(returnType);
		}
	}
	
	private boolean isRefLikeType(Type t) {
		return t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY;
	}
	
	private PointsToNode vis(Expr e) {
		return vis(e, false);
	}
	
	private PointsToNode vis(Expr e, boolean lhs /*for VarExpr*/) {
		
		return null;
	}
	
	public void handleStmt(Stmt stmt) {
		/* for certain instructions we cannot create nodes,
		 * i.e. invokes, so when we return null for a vis
		 * call, we have a problem, because we link null
		 * in the PAG. instead we have to do some hackery.
		 * we create a VarNode for the problematic expression
		 * and */
		int op = stmt.getOpcode();
		if(op == LOCAL_STORE || op == PHI_STORE) {
			AbstractCopyStmt copyStmt = (AbstractCopyStmt) stmt;
			
			PointsToNode dst;
			PointsToNode src;
			
			if(copyStmt.isSynthetic()) {
				/* for synth copies we mark the lhs as a local
				 * and the rhs as a this/parm node */
				
				int off = Modifier.isStatic(method.access) ? 0 : -1;
				
				dst = caseLocal(copyStmt.getVariable());
				
				VarExpr srcVE = (VarExpr) copyStmt.getExpression();
				VersionedLocal srcVL = (VersionedLocal) srcVE.getLocal();
				if(srcVL.getIndex() == 0) {
					src = caseThis();
				} else {
					// soot starts counting parms at 0 (differentiates this)
					src = caseParm(srcVL.getIndex() + off, srcVE.getType());
				}
			} else {
				dst = vis(copyStmt.getVariable());
				src = vis(copyStmt.getExpression());
			}
			mpag.addInternalEdge(src, dst);
		} else if(op == ARRAY_STORE) {
			ArrayStoreStmt as = (ArrayStoreStmt) stmt;
			PointsToNode dst = caseArrayStore(as);
			PointsToNode src = vis(as.getValueExpression());
			mpag.addInternalEdge(src, dst);
		} else if(op == FIELD_STORE) {
			FieldStoreStmt fs = (FieldStoreStmt) stmt;
			PointsToNode dst = caseFieldStore(fs);
			PointsToNode src = vis(fs.getValueExpression());
			mpag.addInternalEdge(src, dst);
		} else if(op == COND_JUMP) {
			ConditionalJumpStmt cond = (ConditionalJumpStmt) stmt;
			vis(cond.getLeft());
			vis(cond.getRight());
		} else if(op == RETURN) {
			ReturnStmt ret = (ReturnStmt) stmt;
			Type t = ret.getType();
			
			if(isRefLikeType(t)) {
				mpag.addInternalEdge(vis(ret.getExpression()), caseRet(t));
			}
		} else if(op == THROW) {
			ThrowStmt thr = (ThrowStmt) stmt;
			PointsToNode n = vis(thr.getExpression());
			mpag.addOutEdge(n, caseThrow());
		} else if(op == NOP || op == UNCOND_JUMP) {
			// nothing
		} else if(op == MONITOR) {
			MonitorStmt mon = (MonitorStmt) stmt;
			vis(mon.getExpression());
		} else if(op == POP) {
			PopStmt pop = (PopStmt) stmt;
			vis(pop.getExpression());
		} else if(op == SWITCH_JUMP) {
			SwitchStmt sw = (SwitchStmt) stmt;
			vis(sw.getExpression());
		} else {
			throw new UnsupportedOperationException(String.format("%s (%s)", stmt, stmt.getClass()));
		}
	}
	
	private boolean isNewInstance(Invocation inv) {
		return !inv.isStatic() && inv.getOwner().equals("java/lang/Class") && inv.getName().equals("newInstance");
	}
	
	public PointsToNode caseThis() {
		VarNode ret = mpag.getPAG().makeLocalVarNode(new Pair<>(method, PointsToAnalysis.THIS_NODE), Type.getType(method.owner.name), method);
		ret.setInterProcTarget();
		return ret;
	}
	
	public PointsToNode caseParm(int i, Type t) {
		VarNode ret = mpag.getPAG().makeLocalVarNode(new Pair<>(method, new Integer(i)), t, method);
		ret.setInterProcTarget();
		return ret;
	}
	
	public PointsToNode caseRet(Type t) {
		VarNode ret = mpag.getPAG().makeLocalVarNode(Parm.v(method, PointsToAnalysis.RETURN_NODE), t, method);
		ret.setInterProcSource();
		return ret;
	}
	
	public PointsToNode caseVarNode(VarNode vn) {
		return mpag.getPAG().makeLocalVarNode(vn, vn.getType(), method);
	}
	
	public PointsToNode casePhi(PhiExpr e) {
		Pair<Expr, String> phiPair = new Pair<>(e, PointsToAnalysis.PHI_NODE);
		PointsToNode phiNode = mpag.getPAG().makeLocalVarNode(phiPair, e.getType(), method);
		for(Expr v : e.getArguments().values()) {
			PointsToNode n = vis(v);
			mpag.addInternalEdge(n, phiNode);
		}
		return phiNode;
	}
	
	public PointsToNode caseArray(VarNode base) {
		return mpag.getPAG().makeFieldRefNode(base, ArrayElement.INSTANCE);
	}
	
	public PointsToNode caseArrayLoad(ArrayLoadExpr ale) {
		VarNode base = (VarNode) vis(ale.getArrayExpression());
		return caseArray(base);
	}
	
	public PointsToNode caseArrayStore(ArrayStoreStmt as) {
		VarNode base = (VarNode) vis(as.getArrayExpression());
		return caseArray(base);
	}
	
	public PointsToNode caseLocal(VarExpr ve) {
		return mpag.getPAG().makeLocalVarNode(ve.getLocal(), ve.getType(), method);
	}
	
	public PointsToNode caseField(String owner, String name, String desc, VarNode base) {
		PointsToNode res;
		FieldNode f = mpag.getPAG().getAnalysisContext().getInvocationResolver().findField(owner, name, desc, base == null);
		
		if(base != null) {
			// addDereference(base)
			res = mpag.getPAG().makeFieldRefNode((VarNode) base, mpag.getPAG().sparkFieldFinder.create(f));
		} else {
			res = mpag.getPAG().makeGlobalVarNode(f, Type.getType(f.desc));
		}
		return res;
	}
	
	public PointsToNode caseFieldStore(FieldStoreStmt fs) {
		return caseField(fs.getOwner(), fs.getName(), fs.getDesc(), null);
	}
	
	public PointsToNode caseFieldLoad(FieldLoadExpr fle) {
		// b1 dead
//		if (pag.getOpts().field_based() || pag.getOpts().vta()) {
//			setResult(mpag.getPAG().makeGlobalVarNode(ifr.getField(), ifr.getField().getType()));
//		} else {
//			setResult(mpag.getPAG().makeLocalFieldRefNode(ifr.getBase(), ifr.getBase().getType(), ifr.getField(), method));
//		}
		PointsToNode base = vis(fle.getInstanceExpression());
		return caseField(fle.getOwner(), fle.getName(), fle.getDesc(), (VarNode) base);
	}
	
	public PointsToNode caseNew(Expr e, Type t) {
		if(PAG.MERGE_STRINGBUILDERS && isStringBuffer(t)) {
			return mpag.getPAG().makeAllocNode(t, t, null);
		} else {
			return mpag.getPAG().makeAllocNode(e, t, method);
		}
	}
	
	private boolean isStringBuffer(Type t) {
		String desc = t.getDescriptor();
		return desc.equals("Ljava/lang/StringBuffer;") || desc.equals("Ljava/lang/StringBuilder;");
	}
	
	public PointsToNode caseNewArray(NewArrayExpr nae) {
		int dims = nae.getDimensions();
		if(dims > 1) {
			Type type = nae.getType();
			AllocNode prevAn = mpag.getPAG().makeAllocNode(new Pair<Expr, Integer>(nae, new Integer(dims)), type, method);
			VarNode prevVn = mpag.getPAG().makeLocalVarNode(prevAn, prevAn.getType(), method);
			
			mpag.addInternalEdge(prevAn, prevVn);
			
			PointsToNode res = prevAn;
			
			while(true) {
				// reduce array by dims by 1 [[I -> [I
				String typeDesc = type.getDescriptor().substring(1);
				type = Type.getType(typeDesc);
				dims--;
				
				if(type.getSort() != Type.ARRAY) {
					break;
				}
				
				AllocNode an = mpag.getPAG().makeAllocNode(new Pair<Expr, Integer>(nae, new Integer(dims)), type, method);
				VarNode vn = mpag.getPAG().makeLocalVarNode(an, type, method);
				
				mpag.addInternalEdge(an, vn);
				mpag.addInternalEdge(vn, mpag.getPAG().makeFieldRefNode(prevVn, ArrayElement.INSTANCE));
				prevAn = an;
				prevVn = vn;
				
			}
			
			return res;
		} else {
			return mpag.getPAG().makeAllocNode(nae, nae.getType(), method);
		}
	}
	
	public PointsToNode caseConstLoad(ConstantExpr ce) {
		Object cst = ce.getConstant();
		
		if(cst instanceof String) {
			String val = (String) cst;
			boolean isKlass = mpag.getPAG().getAnalysisContext().getApplication().contains(val);
			
			AllocNode an;
			
			if(PAG.STRING_CONSTANTS || isKlass || (val.length() > 0 && val.charAt(0) == '[')) {
				an = mpag.getPAG().makeStringConstantNode(val);
			} else {
				an = mpag.getPAG().makeAllocNode(PointsToAnalysis.STRING_NODE, TypeUtils.STRING, null);
			}
			
			VarNode vn = mpag.getPAG().makeGlobalVarNode(an, TypeUtils.STRING);
			mpag.getPAG().addEdge(an, vn);
			return vn;
		} else if(cst instanceof Type) {
			Type t = (Type) cst;
			AllocNode an = mpag.getPAG().makeClassConstantNode(new ClassConstant(t.getDescriptor()));
			VarNode vn = mpag.getPAG().makeGlobalVarNode(an, TypeUtils.CLASS);
			mpag.getPAG().addEdge(an, vn);
			return vn;
		} else {
			return null;
		}
	}
	
	public PointsToNode caseThrow() {
		VarNode ret = mpag.getPAG().makeGlobalVarNode(PointsToAnalysis.EXCEPTION_NODE, TypeUtils.THROWABLE);
		ret.setInterProcTarget();
		ret.setInterProcSource();
		return ret;
	}
}