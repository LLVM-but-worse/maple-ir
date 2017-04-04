package org.mapleir.deob.intraproc;

import java.util.HashSet;
import java.util.Set;

import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ArithmeticExpr.Operator;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.stmt.MonitorStmt;
import org.mapleir.ir.code.stmt.MonitorStmt.MonitorMode;
import org.mapleir.ir.code.stmt.ThrowStmt;
import org.objectweb.asm.Type;

public class DumbExceptionAnalysis implements ExceptionAnalysis, Opcode {

	@Override
	public Set<Type> getPossibleUserThrowables(CodeUnit u) {
		Set<Type> set = new HashSet<>();
		
		if(u.isFlagSet(CodeUnit.FLAG_STMT)) {
			Stmt s = (Stmt) u;
			canThrowStmt(s, set);
			
			for(Expr e : s.enumerateOnlyChildren()) {
				canThrowExpr(e, set);
			}
		} else {
			for(Expr e : ((Expr) u).enumerateWithSelf()) {
				canThrowExpr(e, set);
			}
		}
		
		return set;
	}

	private void canThrowStmt(Stmt u, Set<Type> set) {
		switch(u.getOpcode()) {
			case FIELD_STORE:
				set.add(INCOMPATIBLE_CLASS_CHANGE_ERROR);
				set.add(ILLEGAL_ACCESS_ERROR);
				break;
			case ARRAY_STORE:
				set.add(NULL_POINTER_EXCEPTION);
				set.add(INDEX_OUT_OF_BOUNDS_EXCEPTION);
				break;
			case RETURN:
				set.add(ILLEGAL_MONITOR_STATE_EXCEPTION);
				break;
			case THROW: {
				ThrowStmt thr = (ThrowStmt) u;
				Expr e = thr.getExpression();
				
				if(e.getOpcode() == Opcode.CONST_LOAD) {
					ConstantExpr c = (ConstantExpr) e;
					if(c.getConstant() == null) {
						set.add(NULL_POINTER_EXCEPTION);
					} else {
						throw new IllegalStateException(String.format("%s", thr));
					}
				} else {
					set.add(e.getType());
				}
				set.add(ILLEGAL_MONITOR_STATE_EXCEPTION);
				
				break;
			}
			case MONITOR: {
				set.add(NULL_POINTER_EXCEPTION);
				if(((MonitorStmt) u).getMode() == MonitorMode.EXIT) {
					set.add(ILLEGAL_MONITOR_STATE_EXCEPTION);
				}
				break;
			}
			/* nothing */
			case POP:
			case COND_JUMP:
			case LOCAL_STORE:
			case PHI_STORE:
			case NOP:
			case UNCOND_JUMP:
			case SWITCH_JUMP:
				break;
				
			default:
				throw new UnsupportedOperationException(String.format("%s: %s", Opcode.opname(u.getOpcode()), u));
		}
	}

	private void canThrowExpr(Expr u, Set<Type> set) {
		switch(u.getOpcode()) {
			case ARRAY_LOAD:
				set.add(NULL_POINTER_EXCEPTION);
				set.add(INDEX_OUT_OF_BOUNDS_EXCEPTION);
				break;
			case NEW_ARRAY:
				set.add(NEGATIVE_ARRAY_SIZE_EXCEPTION);
				set.add(ILLEGAL_ACCESS_ERROR);
				break;
			case ARRAY_LEN:
				set.add(NULL_POINTER_EXCEPTION);
				break;
			case CAST:
				set.add(NULL_POINTER_EXCEPTION);
				set.add(CLASS_CAST_EXCEPTION);
				break;
			case INSTANCEOF:
				set.add(CLASS_CAST_EXCEPTION);
				break;
			case FIELD_LOAD:{
				// FIXME: depends on the lookup method
				// and field access
				set.add(INCOMPATIBLE_CLASS_CHANGE_ERROR);
				set.add(NULL_POINTER_EXCEPTION);
				break;
			}
			case ARITHMETIC: {
				ArithmeticExpr ar = (ArithmeticExpr) u;
				Operator op = ar.getOperator();
				
				if(op == Operator.DIV || op == Operator.REM) {
					Type t = ar.getType();
					
					if(t == Type.INT_TYPE || t == Type.LONG_TYPE) {
						set.add(ARITHMETIC_EXCEPTION);
					}
				}
				break;
			}
			case DYNAMIC_INVOKE:
				throw new UnsupportedOperationException(u.toString());
			case INVOKE:
				set.add(ANY);
				
				set.add(ERROR);
				set.add(RUNTIME_EXCEPTION);
				
				set.add(NULL_POINTER_EXCEPTION);
				set.add(INCOMPATIBLE_CLASS_CHANGE_ERROR);
				set.add(ABSTRACT_METHOD_ERROR);
				set.add(UNSATISFIED_LINK_ERROR);
				set.add(ILLEGAL_ACCESS_ERROR);
				set.add(WRONG_METHOD_TYPE_EXCEPTION);
				break;
			case UNINIT_OBJ:
				set.add(INSTANTIATION_ERROR);
				break;
			case INIT_OBJ:
				set.add(ANY);
				
				set.add(ERROR);
				set.add(RUNTIME_EXCEPTION);
				
				set.add(INSTANTIATION_ERROR);
				
				set.add(NULL_POINTER_EXCEPTION);
				set.add(INCOMPATIBLE_CLASS_CHANGE_ERROR);
				set.add(ABSTRACT_METHOD_ERROR);
				set.add(UNSATISFIED_LINK_ERROR);
				set.add(ILLEGAL_ACCESS_ERROR);
				set.add(WRONG_METHOD_TYPE_EXCEPTION);
				break;
				
			case COMPARE:
			case NEGATE:
			case PHI:
			case EPHI:
			case LOCAL_LOAD:
			case CONST_LOAD:
			case CATCH:
				break;
				
			default:
				throw new UnsupportedOperationException(String.format("%s: %s", Opcode.opname(u.getOpcode()), u));
		}
	}

	@Override
	public Set<Type> getForcedThrowables(CodeUnit u) {
		throw new UnsupportedOperationException("TODO");
	}
}