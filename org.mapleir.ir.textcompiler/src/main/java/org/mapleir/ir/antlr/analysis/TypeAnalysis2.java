package org.mapleir.ir.antlr.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.InvocationResolver;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ArrayLoadExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.FieldLoadExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.ArrayStoreStmt;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.impl.VersionedLocal;
import org.objectweb.asm.Type;

public class TypeAnalysis2 {
    
	public static void run(ApplicationClassSource source, ControlFlowGraph cfg,
			InvocationResolver resolver,
			Map<VersionedLocal, Type> initialFacts) {
		TypeAnalysis2 typeAnalysis = new TypeAnalysis2(source, cfg, resolver,
				initialFacts);
		typeAnalysis.processWorklist();
	}

    private final Function<Expr, LocalType> reprocessExpr = e -> processExpr(e);
    private final ApplicationClassSource source;
    private final ControlFlowGraph cfg;
    private final InvocationResolver resolver;
    private final Map<Object, LocalType> elements;
    private final LinkedList<Stmt> worklist;
    private final Set<Stmt> visited;
    
    private Stmt stmt;
    private boolean change;
    private boolean requeue;
    
    private TypeAnalysis2(ApplicationClassSource source, ControlFlowGraph cfg, InvocationResolver resolver, Map<?, Type> initialFacts) {
        this.source = source;
        this.cfg = cfg;
        this.resolver = resolver;
        elements = new HashMap<>();
        worklist = new LinkedList<>();
        
        visited = new HashSet<>();
        
        for(Entry<?, Type> e : initialFacts.entrySet()) {
            elements.put(e.getKey(), newType(e.getValue()));
        }
        
        BasicBlock firstBlock = cfg.getEntries().iterator().next();
        worklist.add(firstBlock.get(0));
    }

    private LocalType newType(Type t) {
    	LocalType lt = new LocalType();
    	lt.isFixed = true;
    	lt.type = t;
    	return lt;
    }

    private void processWorklist() {
        while(!worklist.isEmpty()) {
            stmt = worklist.pop();
            change = false;
            processStmt();
            
            /* if any part of the statement is invalid
             * i.e. UNKNOWN, throw the entire stmt away. */
            
            boolean invalid = false;
            for(Expr e : stmt.enumerateOnlyChildren()) {
                Object o = _getTypeObj(e);
                System.out.println("     " + e + " == " + getType(o));
                if(getType(o).isUnknown) {
                    invalid = true;
                }
            }
            

            /* the dst local is not included in enumerateOnlyChildren */
            if(stmt instanceof AbstractCopyStmt) {
            	AbstractCopyStmt copy = (AbstractCopyStmt) stmt;
            	if(getType(_getTypeObj(copy.getVariable())).isUnknown) {
            		invalid = true;
            	}
            }
            
            System.out.println("  " + invalid);
            if(invalid) {
                invalidateStmt(stmt);
            } else {
                if(change || !visited.contains(stmt)) {
                    queueSuccessors(stmt);
                }
                if(requeue) {
                    enqueue(stmt);
                }
            }
            visited.add(stmt);
        }
    }
    
    private void queueSuccessors(Stmt stmt) {
        BasicBlock block = stmt.getBlock();
        int idx = block.indexOf(stmt);
        
        if(idx == (block.size() - 1)) { 
            for(FlowEdge<BasicBlock> e : cfg.getEdges(block)) {
                BasicBlock succBlock = e.dst();
                enqueue(succBlock.get(0));
            }
        } else {
            enqueue(block.get(idx + 1));
        }
    }
    
    private void enqueue(Stmt stmt) {
        if(worklist.isEmpty() || worklist.getLast() != stmt) {
        	System.out.println("   queue: " + stmt);
        	worklist.addLast(stmt);
        }
    }
    
    private Object _getTypeObj(Expr e) {
        if(e.getOpcode() == Opcode.LOCAL_LOAD) {
            VarExpr vE = (VarExpr) e;
            return vE.getLocal();
        } else {
            return e;
        }
    }
    
    private void requeue() {
        requeue = true;
    }
    
    private void requeueIfNotFixed(Object o) {
        LocalType t = getType(o);
        if(!t.isFixed) {
            requeue();
        }
    }
    
    private void invalidateStmt(Stmt stmt) {
        for(Expr e : stmt.enumerateOnlyChildren()) {
            set(_getTypeObj(e), LocalType.UNKNOWN);
        }
    }
    
    private void processStmt() {
        int opcode = stmt.getOpcode();
        
        if(opcode == Opcode.LOCAL_STORE || opcode == Opcode.PHI_STORE) {
            AbstractCopyStmt copy = (AbstractCopyStmt) stmt;
            VersionedLocal vl = (VersionedLocal) copy.getVariable().getLocal();

            if(copy.isSynthetic()) {
                /* synthetic copies (see parameter copies) should be provided
                 * as input facts. if we don't already have a type for it, we
                 * set it to unknown. */
                // should already be set as an input fact
                LocalType currentT = getType(vl);
                if(!currentT.hasType()) {
                    set(vl, LocalType.UNKNOWN);
                }
            } else {

//            	System.out.println("TypeAnalysis2.processStmt()");
                LocalType newT = processExpr(copy.getExpression());
                System.out.println("        " + vl + " ===== " + newT);
                /* for varexprs we store the type under the Local instance, not
                 * the expr. */
                set(vl, newT);
            }
        } else if(opcode == Opcode.ARRAY_STORE) {
            ArrayStoreStmt ars = (ArrayStoreStmt) stmt;
            boolean haveArrayType = expectType(ars.getArrayExpression(),
                    t -> t.getSort() == Type.ARRAY, reprocessExpr);
            // failable
            expectType(ars.getIndexExpression(), t -> t.getSort() == Type.INT, reprocessExpr);

            if (haveArrayType) {
                Type arrayType = getType(ars.getArrayExpression()).type;
                System.out.println(arrayType);
            }
        }
    }
    

    private LocalType processExpr(Expr e) {
    	Object o = _getTypeObj(e);
    	LocalType t = getType(o);
    	
    	if(t.isFixed) {
    		return t;
    	} else {
    		t = _processExpr(e);
    		/* technically allowing the caller to set the type for an expr
    		 * would be better (and therefore we do this also) but for var
    		 * expr's, the type is mapped under the local instance instead of
    		 * the expr instance. getting the type for an expr should not fail
    		 * because of this, however, instead it will fail when we check
    		 * for invalid states in the main worklist loop, as the copy stmt
    		 * dst will claim the type instead. */
    		set(_getTypeObj(e), t);
    		return t;
    	}
    }
    
    private LocalType _processExpr(Expr e) {
        if(e.getOpcode() == Opcode.LOCAL_LOAD) {
			VersionedLocal dst = (VersionedLocal) ((VarExpr) e).getLocal();
			return getType(dst);
        } else if(e.getOpcode() == Opcode.CONST_LOAD) {
        	ConstantExpr ce = (ConstantExpr) e;
        	return newType(ce.getType());
        } else if(e.getOpcode() == Opcode.ARRAY_LOAD) {
        	ArrayLoadExpr ale = (ArrayLoadExpr) e;
        	
        	Expr arrayExpr = ale.getArrayExpression();
            boolean haveArrayType = expectType(arrayExpr,
                    t -> t.getSort() == Type.ARRAY, reprocessExpr);
            // failable
            expectType(ale.getIndexExpression(), t -> t.getSort() == Type.INT, reprocessExpr);

            if (haveArrayType) {
                Type arrayType = getType(_getTypeObj(arrayExpr)).type;
                return newType(arrayType.getElementType());
            } else {
            	return LocalType.UNKNOWN;
            }
        } else if(e.getOpcode() == Opcode.FIELD_LOAD) {
            FieldLoadExpr fle = (FieldLoadExpr) e;
            /* desc is not set for field codeunits */
            System.out.println(fle);
            if(fle.isStatic()) {
                
            } else {
                if(expectType(fle.getInstanceExpression(), t -> !isPrimitive(t), reprocessExpr)) {
                    Type t = getType(fle.getInstanceExpression()).type;
                    
                    if(t.getSort() == Type.ARRAY) {
                        if(fle.getName().equals("length")) {
                            // TODO: change to arrlen expr
                        } else {
                            invalidateStmt(stmt);
                        }
                    } else {
                        /* object */
                        
                    }
                }
            }
        } else if(e.getOpcode() == Opcode.ARITHMETIC) {
        	ArithmeticExpr ae = (ArithmeticExpr) e;
			if (!expectType(ae.getLeft(), t -> isPrimitive(t), reprocessExpr)
					|| !expectType(ae.getRight(), t -> isPrimitive(t),
							reprocessExpr)) {
				return LocalType.UNKNOWN;
			} else {
				Type tl = getType(_getTypeObj(ae.getLeft())).type,
						tr = getType(_getTypeObj(ae.getRight())).type;
				return newType(ArithmeticExpr.getType(tl, tr, ae.getOperator()));
			}
        }
        
        throw new UnsupportedOperationException(String.valueOf(e));
    }
    
    private <T> boolean expectType(T o, Predicate<Type> predicate, Function<T, LocalType> failFunction) {
        LocalType localT = getType(o);
        if(localT.hasType()) {
            Type t = localT.type;
            if(predicate.test(t)) {
                /* all ok */
                return true;
            } else {
                /* an actual 'fail' i.e. the type isn't as expected,
                 * this is not as severe as being unfixed (i.e. todo) */
                invalidateStmt(stmt);
            }
        } else if(!localT.isFixed) {
            /* unresolved so far, but resolveable.*/
            if(failFunction != null) {
                LocalType newT = failFunction.apply(o);
                set(o, newT);
                /* retry */
                return expectType(o, predicate, null);
            }
            requeue();
        } else {
            /* can be fixed but no type (UNKNOWN), in this
             * case no point requeueing it. */
        }
        
        /* either invalidated, requeue or UNKNOWN */
        return false;
    }
    
    private LocalType processVarExpr(VarExpr v) {
        VersionedLocal dst = (VersionedLocal) v.getLocal();
        return getType(dst);
    }
    
    private static boolean isPrimitive(Type t) {
        int sort = t.getSort();
        return sort != Type.OBJECT && sort != Type.ARRAY;
    }
    
    private LocalType merge(LocalType t1, LocalType t2) {
        throw new UnsupportedOperationException();
    }
    
    private void set(Object o, LocalType t) {
    	if(o instanceof VarExpr) {
    		throw new IllegalStateException(String.format("%s=%s", o, t));
    	}
        LocalType prev = getType(o);
        
        if(prev.isTodo) {
            elements.put(o, t);
        } else if(!prev.isUnknown) {
            if(prev.isFixed) {
                t = LocalType.UNKNOWN; // invalidate(conflict)
            } else {
                t = merge(prev, t);
            }
            elements.put(o, t);
        }
        
        if(!t.equals(prev)) {
            change = true;
        }
    }
    
    public LocalType getType(Object o) {
    	if(o instanceof VarExpr) {
    		throw new IllegalStateException(String.valueOf(o));
    	}
        if(elements.containsKey(o)) {
            return elements.get(o);
        } else {
            elements.put(o, LocalType.TODO);
            return LocalType.TODO;
        }
    }
    
    private static class LocalType {
        static final LocalType UNKNOWN = new LocalType(true, false);
        static final LocalType TODO = new LocalType(false, true);
        
        private boolean isUnknown;
        private boolean isTodo;
        private boolean isFixed;
        private Type type;
        
        private LocalType(boolean isUnknown, boolean isTodo) {
            isUnknown = isUnknown;
            isTodo = isTodo;
            isFixed = isUnknown; // if it's unknown, we can't change the type
            
            if(isUnknown == isTodo) {
                throw new IllegalStateException();
            }
        }
        
        public boolean hasType() {
            return isFixed && type != null;
        }
        
        public LocalType() {
            
        }
        
        @Override
        public String toString() {
            return "LocalType [isTop=" + isUnknown + ", isBottom=" + isTodo + ", type=" + type + "]";
        }
    }
}