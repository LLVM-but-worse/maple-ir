package org.mapleir.ir.antlr.analysis;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
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
import org.mapleir.ir.code.expr.FieldLoadExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.ArrayStoreStmt;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.impl.VersionedLocal;
import org.objectweb.asm.Type;


public class TypeAnalysis2 {
    
    public static void run(ApplicationClassSource source, ControlFlowGraph cfg, InvocationResolver resolver, Map<VersionedLocal, Type> initialFacts) {
        TypeAnalysis2 typeAnalysis = new TypeAnalysis2(source, cfg, resolver, initialFacts);
        typeAnalysis.processWorklist();
    }

    private final Function<Expr, LocalType> reprocessExpr = e -> processExpr(e);
    private final ApplicationClassSource source;
    private final ControlFlowGraph cfg;
    private final InvocationResolver resolver;
    private final Map<Object, LocalType> elements;
    private final LinkedList<Stmt> worklist;
    
    private Stmt stmt;
    private boolean change;
    private boolean requeue;
    
    private TypeAnalysis2(ApplicationClassSource source, ControlFlowGraph cfg, InvocationResolver resolver, Map<?, Type> initialFacts) {
        this.source = source;
        this.cfg = cfg;
        this.resolver = resolver;
        elements = new HashMap<>();
        worklist = new LinkedList<>();
        
        for(Entry<?, Type> e : initialFacts.entrySet()) {
            LocalType t = new LocalType();
            t.type = e.getValue();
            t.isFixed = true;
            elements.put(e.getKey(), t);
        }
        
        BasicBlock firstBlock = cfg.getEntries().iterator().next();
        worklist.add(firstBlock.get(0));
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
                if(getType(o).isUnknown) {
                    invalid = true;
                }
            }
            
            if(invalid) {
                invalidateStmt(stmt);
            } else {
                if(change) {
                    queueSuccessors(stmt);
                }
                if(requeue) {
                    enqueue(stmt);
                }
            }
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
        worklist.addLast(stmt);
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
            set(e, LocalType.UNKNOWN);
        }
    }
    
    private void processStmt() {
        int opcode = stmt.getOpcode();
        
        if(opcode == Opcode.LOCAL_STORE || opcode == Opcode.PHI_STORE) {
            AbstractCopyStmt copy = (AbstractCopyStmt) stmt;
            VersionedLocal vl = (VersionedLocal) copy.getVariable().getLocal();
            if(!copy.isSynthetic()) {
                // should already be set as an input fact
                LocalType currentT = getType(vl);
                if(!currentT.hasType()) {
                    set(vl, LocalType.UNKNOWN);
                }
            } else {
                LocalType newT = processExpr(copy.getExpression());
                set(vl, newT);
            }
        } else if(opcode == Opcode.ARRAY_STORE) {
            ArrayStoreStmt ars = (ArrayStoreStmt) stmt;
            boolean haveArrayType = expectType(ars.getArrayExpression(),
                    t -> t.getSort() == Type.ARRAY, reprocessExpr);
            expectType(ars.getIndexExpression(), t -> t.getSort() == Type.INT, reprocessExpr);

            if (haveArrayType) {
                Type arrayType = getType(ars.getArrayExpression()).type;
                System.out.println(arrayType);
            }
        }
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
    
    private LocalType processExpr(Expr e) {
        if(e.getOpcode() == Opcode.LOCAL_LOAD) {
            return processVarExpr((VarExpr) e);
        } else if(e.getOpcode() == Opcode.FIELD_LOAD) {
            FieldLoadExpr fle = (FieldLoadExpr) e;
            /* desc is not set for field codeunits */
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
        }
    }
    
    private static boolean isPrimitive(Type t) {
        int sort = t.getSort();
        return sort != Type.OBJECT && sort != Type.ARRAY;
    }
    
    private LocalType merge(LocalType t1, LocalType t2) {
        throw new UnsupportedOperationException();
    }
    
    private void set(Object o, LocalType t) {
        LocalType prev = getType(o);
        
        if(prev.isTodo) {
            elements.put(o, t);
        } else if(!prev.isUnknown) {
            if(prev.isFixed) {
                t = LocalType.UNKNOWN; // invalidate/conflict
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
            this.isUnknown = isUnknown;
            this.isTodo = isTodo;
            this.isFixed = isUnknown; // if it's unknown, we can't change the type
            
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