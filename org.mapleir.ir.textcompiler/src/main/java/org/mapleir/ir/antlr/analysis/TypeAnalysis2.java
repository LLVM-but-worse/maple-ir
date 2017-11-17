package org.mapleir.ir.antlr.analysis;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.impl.VersionedLocal;
import org.objectweb.asm.Type;

public class TypeAnalysis2 {

    public static void run(ControlFlowGraph cfg, Map<VersionedLocal, Type> initialFacts) {
        TypeAnalysis2 typeAnalysis = new TypeAnalysis2(cfg, initialFacts);
        typeAnalysis.processWorklist();
    }
    
    private final ControlFlowGraph cfg;
    private final Map<Object, LocalType> elements;
    private final LinkedList<Stmt> worklist;
    
    private Stmt stmt;
    private boolean change;
    
    private TypeAnalysis2(ControlFlowGraph cfg, Map<?, Type> initialFacts) {
        this.cfg = cfg;
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
        }
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
    
    private void processStmt() {
        int opcode = stmt.getOpcode();
        
        if(opcode == Opcode.LOCAL_STORE || opcode == Opcode.PHI_STORE) {
            AbstractCopyStmt copy = (AbstractCopyStmt) stmt;
            
            if(!copy.isSynthetic()) {
                LocalType newT = processExpr(copy.getExpression());
                set(copy.getVariable().getLocal(), newT);
            } else {
                // already set an input fact
            }
        }
    }
    
    private LocalType processVarExpr(VarExpr v) {
        VersionedLocal dst = (VersionedLocal) v.getLocal();
        return getType(dst);
    }
    
    private LocalType processExpr(Expr e) {
        
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
            this.isFixed = isUnknown;
            
            if(isUnknown == isTodo) {
                throw new IllegalStateException();
            }
        }
        
        public LocalType() {
            
        }
        
        @Override
        public String toString() {
            return "LocalType [isTop=" + isUnknown + ", isBottom=" + isTodo + ", type=" + type + "]";
        }
    }
}